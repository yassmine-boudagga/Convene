const WebSocket = require('ws');
const jwt = require('jsonwebtoken');
const { extractId, extractJoinedUserIds } = require('../utils/idHelpers');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

class WebSocketManager {
  constructor() {
    this.connections = new Map(); 
    this._logoutBlacklist = new Map();
    this._BLACKLIST_TTL_MS = 10000;
    this._blacklistCleanupInterval = null;
    this.meetingRooms = new Map();
    this.wss = null;
    this._appPingInterval = null;
  }

  init(server) {
    this.wss = new WebSocket.Server({
      server,
      path: '/ws',
      perMessageDeflate: false,
    });
    this.wss.on('connection', (ws) => {
      ws.userId  = null;
      ws.isAlive = true;
      ws.lastPongAt = Date.now();
      const authTimeout = setTimeout(() => {
        if (!ws.userId && ws.readyState === WebSocket.OPEN) {
          ws.close(1008, 'auth_timeout');
        }
      }, 10000);
      ws._authTimeout = authTimeout;

      ws.on('message', (raw) => this._handleMessage(ws, raw));
      ws.on('close', (code, reason) => {
        clearTimeout(ws._authTimeout);
        this._handleClose(ws, code, reason);
      });
      ws.on('error', (err) => {
        clearTimeout(ws._authTimeout);
        errorLog('[WS] error:', err.message);
      });
      ws.on('pong', () => {
        ws.isAlive = true;
        ws.lastPongAt = Date.now();
      });
    });
    // Heartbeat bas niveau pour reseaux mobiles instables
    this._pingInterval = setInterval(() => this._pingAll(), 30000);

    if (this._appPingInterval) {
      clearInterval(this._appPingInterval);
    }
    // Heartbeat applicatif periodique pour detection client-side
    this._appPingInterval = setInterval(() => this._sendAppPings(), 25000);

    if (this._blacklistCleanupInterval) {
      clearInterval(this._blacklistCleanupInterval);
    }
    this._blacklistCleanupInterval = setInterval(() => {
      const now = Date.now();
      for (const [key, expiry] of this._logoutBlacklist.entries()) {
        if (now >= expiry) {
          this._logoutBlacklist.delete(key);
        }
      }
    }, 60000);
    debugLog('[WS] Serveur WebSocket démarré');
    return this;
  }

  _handleMessage(ws, raw) {
    let msg;
    try { msg = JSON.parse(raw.toString()); } catch { return; }
    if (msg.event === 'auth')  this._authenticate(ws, msg.token);
    if (msg.event === 'ping')  { ws.isAlive = true; this._send(ws, { event: 'pong' }); }
    if (msg.event === 'join_meeting' && msg.meetingId) {
      if (!ws.userId) {
        return;
      } 
      this.registerClientInRoom(msg.meetingId.toString(), ws.userId.toString(), ws);
    }
    if (msg.event === 'leave_meeting' && msg.meetingId) {
      if (!ws.userId) {
        return;
      }
      this.unregisterClientFromRoom(msg.meetingId.toString(), ws.userId.toString(), ws);
    }
  }

  registerClientInRoom(meetingId, userId, ws) {
    if (!meetingId || !userId || !ws) return;

    if (!this.meetingRooms.has(meetingId)) {
      this.meetingRooms.set(meetingId, new Map());
    }

    const room = this.meetingRooms.get(meetingId);
    const existing = room.get(userId);
    if (existing && existing !== ws && existing.readyState === WebSocket.OPEN) {
      try { existing.terminate(); } catch (_) {}
    }

    room.set(userId, ws);
    ws._meetingId = meetingId;
    ws._userId = userId;
  }

  unregisterClientFromRoom(meetingId, userId, ws = null) {
    if (!meetingId || !userId) return;
    const room = this.meetingRooms.get(meetingId);
    if (!room) return;
    const current = room.get(userId);
    if (!ws || current === ws) {
      room.delete(userId);
    }
    if (room.size === 0) {
      this.meetingRooms.delete(meetingId);
    }
  }

  _authenticate(ws, token) {
    try {
      if (!ws || ws.readyState !== WebSocket.OPEN) {
        return;
      }
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      const userId =decoded.id.toString();

      const blacklistExpiry = this._logoutBlacklist.get(userId);
      if (blacklistExpiry) {
        if (Date.now() < blacklistExpiry) {
          debugLog(`[WS] Auth rejetée — userId=${userId} en blacklist post-logout`);
          this._send(ws, { event: 'auth_error', message: 'recently_logged_out' });
          ws.close(1000, 'recently_logged_out');
          return;
        }
        this._logoutBlacklist.delete(userId);
      }
      // socket deja authentifiee, ignorer les re-auth sur la meme connexion.
      if (ws.userId && ws.userId.toString() === userId) {
        return;
      }

      const existingConns = this.connections.get(userId);
      if (existingConns && existingConns.size > 0) {
        // Nettoyer les sockets mortes avant de décider s'il existe une autre connexion active.
        existingConns.forEach(existingWs => {
          if (!existingWs || existingWs.readyState === WebSocket.CLOSED || existingWs.readyState === WebSocket.CLOSING) {
            existingConns.delete(existingWs);
          }
        });
        if (!existingConns.size) {
          this.connections.delete(userId);
        }
      }

      const remainingConns = this.connections.get(userId);
      if (remainingConns && remainingConns.size > 0 && !remainingConns.has(ws)) {
        try { ws.close(1000, 'duplicate_auth'); } catch (_) {}
        return;
      }
      // apres auth
      if (ws._authTimeout) {
        clearTimeout(ws._authTimeout);
        ws._authTimeout = null;
      }
      ws.userId = userId;
      if (!this.connections.has(userId)) this.connections.set(userId, new Set());
      this.connections.get(userId).add(ws);
      this._send(ws, { event: 'auth_success', userId });
      this._deliverPending(userId);
      debugLog(`[WS] Auth OK: userId=${userId}, total connectés=${this.connections.size}`);
    } catch (err) {
      this._send(ws, { event: 'auth_error', message: 'Token invalide' });
      ws.close(1008, 'invalid_token');
    }
  }

  _serializeNotif(n) {
    const raw = n.toObject ? n.toObject() : n;
    return {
      id:          (raw._id || raw.id || '').toString(),
      type:        raw.type        || '',
      title:       raw.title       || '',
      message:     raw.message     || '',
      isRead:      raw.isRead      ?? false,
      isDelivered: raw.isDelivered ?? false,
      createdAt:   raw.createdAt   ? raw.createdAt.toISOString?.() ?? raw.createdAt : '',
      updatedAt:   raw.updatedAt   ? raw.updatedAt.toISOString?.() ?? raw.updatedAt : null,
      actionTaken: raw.actionTaken ?? null,
      data:        raw.data        ?? null,
    };
  }

  async _deliverPending(userId) {
    try {
      const Notification = require('../models/Notification');
      const pending = await Notification.find({ userId, isDelivered: false })
        .sort({ createdAt: -1 }).limit(20);
      if (!pending.length) return;
      const delivered = this.sendToUser(userId, {
        event: 'pending_notifications',
        notifications: pending.map(n => this._serializeNotif(n))
      });
      if (delivered) {
        await Notification.updateMany(
          { _id: { $in: pending.map(n => n._id) } },
          { $set: { isDelivered: true, deliveredAt: new Date() } }
        );
      }
    } catch (err) { errorLog('[WS] _deliverPending:', err.message); }
  }

  sendToUser(userId, data) {
    const conns = this.connections.get(userId.toString());
    if (!conns || !conns.size) return false;
    const payload = JSON.stringify(data);
    let delivered = false;
    conns.forEach(ws => {
      if (ws.readyState === WebSocket.OPEN) { ws.send(payload); delivered = true; }
    });
    return delivered;
  }

  disconnectUser(userId) {
    const key = userId?.toString();
    if (!key) return 0;
    const conns = this.connections.get(key);
    let closedCount = 0;
    if (conns && conns.size) {
      debugLog(`[WS] disconnectUser: fermeture forcée userId=${key}`);
      conns.forEach(ws => {
        if (!ws) return;
        try {
          if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
            ws.close(1000, 'logout');
            closedCount += 1;
          }
        } catch (_) {}
      });
      this.connections.delete(key);
    }

    this._logoutBlacklist.set(key, Date.now() + this._BLACKLIST_TTL_MS);
    debugLog(`[WS] Blacklist logout: userId=${key} pendant ${this._BLACKLIST_TTL_MS}ms`);
    return closedCount;
  }

  async broadcastToMeeting(meetingId, payload) {
    if (!meetingId || !payload) return 0;
    try {
      const sockets = new Set();
      // S1:meetingRooms
      const room = this.meetingRooms.get(meetingId.toString());
      if (room) {
        room.forEach(ws => {
          if (ws && ws.readyState === WebSocket.OPEN) sockets.add(ws);
        });
      }
      // S2:MongoDB joinedParticipants
      try {
        const Meeting = require('../models/Meeting');
        const meeting = await Meeting.findById(meetingId).select('createdBy joinedParticipants');
        if (meeting) {
          const userIds = new Set();
          if (meeting.createdBy) {
            const cId = extractId(meeting.createdBy);
            if (cId) userIds.add(cId);
          }
          extractJoinedUserIds(meeting.joinedParticipants || []).forEach(uid => {
            userIds.add(uid);
          });
          userIds.forEach(uid => {
            const conns = this.connections.get(uid.toString());
            if (!conns || !conns.size) return;
            conns.forEach(ws => {
              if (ws.readyState === WebSocket.OPEN) sockets.add(ws);
            });
          });
        }
      } catch (err) {
        errorLog('[WS] broadcastToMeeting: erreur lecture MongoDB:', err.message);
      }

      if (sockets.size === 0) return 0;

      const serialized = JSON.stringify(payload);
      let deliveredCount = 0;
      sockets.forEach(ws => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(serialized);
          deliveredCount += 1;
        }
      });

      return deliveredCount;
    } catch (err) {
      errorLog('[WS] broadcastToMeeting error:', err.message);
      return 0;
    }
  }

  _send(ws, data) {
    if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(data));
  }

  async _handleClose(ws, code = 1005, reason = '') {
    const userId = ws.userId;
    const reasonText = Buffer.isBuffer(reason) ? reason.toString('utf8') : String(reason || '');
    const isPongTimeout = ws._pongTimeoutClose === true;
    if (isPongTimeout) {
      debugLog(`[WS] Close socket userId=${userId} code=${code} reason=pong_timeout`);
    }
    debugLog(`[WS] Close socket userId=${userId} code=${code} reason=${reasonText}`);

    if (ws._meetingId && ws._userId) {
      this.unregisterClientFromRoom(ws._meetingId, ws._userId, ws);
    }
    
    if (userId) {
      const key = userId.toString();
      const conns = this.connections.get(key);
      if (conns) {
        conns.delete(ws);
      }

      const remainingConns = this.connections.get(key);
      if (!remainingConns || !remainingConns.size) {
        this.connections.delete(key);
      }
      if (this.connections.has(key)) {
        return;
      }
      
      // CLEANUP pour réunions physiques: supprimer user de joinedParticipants
      try {
        const Meeting = require('../models/Meeting');
        const User = require('../models/User');
        const meetings = await Meeting.find({
          meetingType: 'physical',
          status:'ongoing',
          'joinedParticipants.userId': key
        }).select('_id joinedParticipants createdBy realMeetingStarted participants attendedBy');
        
        for (const meeting of meetings) {
          const updatedMeeting = await Meeting.findByIdAndUpdate(
            meeting._id,
            { $pull: { joinedParticipants: { userId } } },
            { new: true }
          ).populate('joinedParticipants.userId', '_id name email')
           .populate('createdBy', '_id name email');

          const meetingId = meeting._id.toString();
          await this.broadcastToMeeting(meetingId, {
            event: 'participant_left',
            meetingId,
            userId: key,
          });

          const hostId = extractId(updatedMeeting?.createdBy);
          const remainingIds = extractJoinedUserIds(updatedMeeting?.joinedParticipants || []);
          const isHostLeaving = key === hostId;
          const remainingGuests = remainingIds.filter(id => id !== hostId);

          let finishReason = null;
          if (remainingIds.length === 0) {
            finishReason = 'all_left';
          } else if (isHostLeaving) {
            finishReason = 'host_left';
          } else if (remainingGuests.length === 0) {
            finishReason = 'all_guests_left';
          }

          if (finishReason && updatedMeeting?.realMeetingStarted) {
            const { finishMeetingProperly } = require('../controllers/meetingController');
            await finishMeetingProperly(updatedMeeting, finishReason, this, {
              extraAttendedUserIds: [key],
              triggeredByUserId: key,
            });

            const meetingId = meeting._id.toString();
            await this.broadcastToMeeting(meetingId, {
              event: 'meeting_force_end',
              meetingId,
              reason: finishReason,
              countdown: 5,
              triggeredBy: key
            });
          }
        debugLog(`[WS] participant_left → meeting ${meetingId}, userId=${key}`);
        }
      } catch (err) {
        errorLog('[WS] Cleanup error:', err.message);
      }
    }
  }

  _pingAll() {
    if (!this.wss) return;
    const now = Date.now();
    const graceMs = 120000;

    this.wss.clients.forEach(ws => {
      if (!ws.lastPongAt) {
        ws.lastPongAt = now;
      }

      if ((now - ws.lastPongAt) > graceMs) {
        ws._pongTimeoutClose = true;
        this._handleClose(ws, 1006, 'pong_timeout');
        ws.terminate();
        return;
      }
      ws.isAlive = false;
      ws.ping();
    });
  }

  _sendAppPings() {
    const payload = JSON.stringify({ event: 'ping' });

    this.connections.forEach((conns) => {
      conns.forEach((ws) => {
        if (ws && ws.readyState === WebSocket.OPEN && ws.userId) {
          ws.send(payload);
        }
      });
    });
  }
  broadcastAll(payload) {
    const serialized = JSON.stringify(payload);
    let deliveredCount = 0;
    this.connections.forEach((conns) => {
      conns.forEach((ws) => {
        if (ws && ws.readyState === WebSocket.OPEN) {
          ws.send(serialized);
          deliveredCount += 1;
        }
      });
    });
    return deliveredCount;
  }
  get connectedUsersCount() { return this.connections.size; }
}
module.exports = new WebSocketManager();