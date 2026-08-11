const {
  RoomServiceClient, EgressClient, AccessToken, EncodedFileOutput, EncodedFileType, S3Upload,
  EgressStatus,} = require('livekit-server-sdk');
const Meeting = require('../models/Meeting');
const MeetingRecording = require('../models/MeetingRecording');
const wsManager = require('./wsManager');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

const warnLog = (...args) => {
  console.warn(...args);
};

// INITIALISATION DES CLIENTS
const getLivekitUrl = () => {
  const url = process.env.LIVEKIT_URL;
  if (!url) throw new Error('[LiveKit] LIVEKIT_URL manquante');
  return url.replace('wss://', 'https://').replace('ws://', 'http://');
};

const getRoomService = () =>
  new RoomServiceClient(
    getLivekitUrl(),
    process.env.LIVEKIT_API_KEY,
    process.env.LIVEKIT_API_SECRET
  );

const getEgressClient = () =>
  new EgressClient(
    getLivekitUrl(),
    process.env.LIVEKIT_API_KEY,
    process.env.LIVEKIT_API_SECRET
  );

// verif config
const validateConfig = () => {
  const required = ['LIVEKIT_URL', 'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET'];
  const missing = required.filter(k => !process.env[k]);
  if (missing.length > 0) {
    errorLog(`[LiveKit] Config manquante: ${missing.join(', ')}`);
    return false;
  }
  return true;
};

// ROOM OPERATIONS
async function createRoom(meetingId, meetingTitle) {
  if (!validateConfig()) throw new Error('LiveKit config incomplète');
  try {
    const roomService = getRoomService();
    const roomName = `meeting-${meetingId}`;
    const room = await roomService.createRoom({
      name: roomName,
      emptyTimeout: 15 * 60,
      maxParticipants: 20,
      metadata: JSON.stringify({ meetingTitle, meetingId }),
    });
    debugLog(`[LiveKit] Room créée: ${room.name} (sid: ${room.sid})`);
    return {
      id: room.name,
      sid: room.sid,
      name: room.name,
    };
  } catch (error) {
    errorLog('[LiveKit] Échec création room:', error.message);
    throw new Error('Failed to create video room');
  }
}

async function getRoom(roomName) {
  const roomService = getRoomService();
  const rooms = await roomService.listRooms([roomName]);
  return rooms[0] || null;
}

async function roomExists(roomName) {
  try {
    const roomService = getRoomService();
    const rooms = await roomService.listRooms([roomName]);
    return rooms && rooms.length > 0;
  } catch (error) {
    warnLog(`[LiveKit] roomExists check failed for ${roomName}: ${error.message}`);
    return false;
  }
}

async function disableRoom(roomName) {
  try {
    const roomService = getRoomService();
    await roomService.deleteRoom(roomName);
    debugLog(`[LiveKit] Room supprimée: ${roomName}`);
  } catch (error) {
    warnLog(`[LiveKit] disableRoom warning: ${error.message}`);
  }
}

async function getParticipants(roomName) {
  const roomService = getRoomService();
  const participants = await roomService.listParticipants(roomName);
  return participants || [];
}

// TOKEN OPERATIONS
async function generateToken(roomName, userId, userName, role = 'guest') {
  if (!validateConfig()) throw new Error('LiveKit config incomplète');

  const isHost = role === 'host';

  const at = new AccessToken(
    process.env.LIVEKIT_API_KEY,
    process.env.LIVEKIT_API_SECRET,
    {
      identity: userId.toString(),
      name: userName,
      ttl: '24h',
      metadata: JSON.stringify({ role, userId: userId.toString() }),
    }
  );

  at.addGrant({
    roomJoin: true,
    room: roomName,
    canPublish: true,
    canSubscribe: true,
    roomAdmin: isHost,
    canPublishData: true,
  });

  const token = await at.toJwt();
  debugLog(`[LiveKit] Token généré: user=${userId}, role=${role}, room=${roomName}`);
  return token;
}

// RECORDING OPERATIONS (EGRESS)
async function startRecording(roomName) {
  if (!validateConfig()) throw new Error('LiveKit config incomplète');

  const exists = await roomExists(roomName);
  if (!exists) {
    throw new Error(`[LiveKit] Room ${roomName} inexistante — impossible de démarrer le recording`);
  }

  const egressClient = getEgressClient();
  const filename = `recording-${roomName}-${Date.now()}.ogg`;
  const uploadEndpoint = process.env.RECORDING_UPLOAD_ENDPOINT ;

  debugLog(`[LiveKit] Démarrage recording pour room: ${roomName}`);
  debugLog(`[LiveKit] Fichier cible: ${filename}`);

  const fileOutput = new EncodedFileOutput({
    fileType: EncodedFileType.OGG,
    filepath: filename,
    disableDtx: false,
    output: {
      case: 's3',
      value: new S3Upload({
        accessKey: process.env.S3_ACCESS_KEY ,
        secret: process.env.S3_SECRET_KEY ,
        region: process.env.S3_REGION,
        bucket: process.env.S3_BUCKET || 'meetings',
        endpoint: uploadEndpoint,
        forcePathStyle: true,
      }),
    },
  });

  try {
    const info = await egressClient.startRoomCompositeEgress(
      roomName,
      { file: fileOutput },
      {
        layout: 'speaker',
        audioOnly: true,
      }
    );
    debugLog(`[LiveKit] Recording démarré: egressId=${info.egressId}, status=${info.status}`);

    return {
      egressId: info.egressId,
      filename,
      status: info.status,
    };
  } catch (err) {
    errorLog(`[LiveKit] Échec startRecording: ${err.message}`);
    errorLog(`[LiveKit] Code: ${err.code}, Metadata: ${JSON.stringify(err.metadata)}`);
    throw err;
  }
}

async function stopRecording(egressId, legacyEgressId) {
  if (!validateConfig()) throw new Error('LiveKit config incomplète');
  const targetEgressId = legacyEgressId || egressId;
  if (!targetEgressId) return null;

  const recording = await MeetingRecording.findOneAndUpdate(
    {
      recordingId: targetEgressId,
      recordingStatus: 'recording'
    },
    { $set: { recordingStatus: 'processing' } },
    { new: true }
  );
  if (!recording) {
    debugLog(`[LiveKit] stopRecording skip: egressId=${targetEgressId} déjà en cours d'arrêt ou introuvable`);
    return null;
  }
  try {
    const egressClient = getEgressClient();
    await egressClient.stopEgress(targetEgressId);
    debugLog(`[LiveKit] Recording arrêté: ${targetEgressId}`);
    // Déclencher la finalisation interne sans bloquer stopRecording
    setImmediate(async () => {
      try {
        const { finalizeEgress } = require('./recordingFinalizer');
        const rec = await MeetingRecording.findOne({ recordingId: targetEgressId });
        if (rec && rec.meetingId) {
          await finalizeEgress(targetEgressId, rec.meetingId.toString());
        } else {
          warnLog(`[LiveKit] finalizeEgress: meetingId introuvable pour egressId=${targetEgressId}`);
        }
      } catch (finErr) {
        errorLog(`[LiveKit] finalizeEgress erreur: ${finErr.message}`);
      }
    });
    return targetEgressId;
  } catch (err) {
    if (err.message && err.message.includes('EGRESS_COMPLETE')) {
      debugLog(`[LiveKit] Recording déjà terminé naturellement: ${targetEgressId}`);
      // Lancer quand même la finalisation car l'egress est terminé
      setImmediate(async () => {
        try {
          const { finalizeEgress } = require('./recordingFinalizer');
          const rec = await MeetingRecording.findOne({ recordingId: targetEgressId });
          if (rec && rec.meetingId) {
            await finalizeEgress(targetEgressId, rec.meetingId.toString());
          }
        } catch (finErr) {
          errorLog(`[LiveKit] finalizeEgress (already complete) erreur: ${finErr.message}`);
        }
      });
      return targetEgressId;
    }
    await MeetingRecording.updateOne(
      { recordingId: targetEgressId },
      { $set: { recordingStatus: 'recording' } }
    );

    errorLog(`[LiveKit] Échec stopRecording: ${err.message}`);
    throw err;
  }
}

async function checkAndStopRecording(meetingId, source) {
  const meeting = await Meeting.findById(meetingId);
  if (!meeting) return;

  const recording = await MeetingRecording.findOne({ meetingId });
  if (!recording) {
    debugLog(`[${source}] Aucun document recording pour ${meetingId} — skip`);
    return;
  }
  if (!recording.recordingId || recording.recordingStatus !== 'recording') {
    debugLog(`[${source}] Pas de recording actif pour ${meetingId} — skip`);
    return;
  }
  const remainingHosts = (meeting.joinedParticipants || []).find(
    p => p.role === 'host'
  );
  const remainingGuests = (meeting.joinedParticipants || []).filter(
    p => p.role === 'guest'
  );
  const shouldStop = !remainingHosts || remainingGuests.length === 0;
  if (!shouldStop) {
    debugLog(`[${source}] Recording continue:host présent, ${remainingGuests.length} guest(s)`);
    return;
  }
  debugLog(`[${source}] Conditions d'arrêt détectées → stopRecording(${recording.recordingId})`);
  const stopped = await stopRecording(recording.recordingId);

  if (stopped) {
    wsManager.broadcastToMeeting(meetingId.toString(), {
      event: 'recording_stopped',
      meetingId: meetingId.toString(),
    });
  }
}

async function getRecordingStatus(roomName) {
  if (!validateConfig()) throw new Error('LiveKit config incomplète');
  try {
    const egressClient = getEgressClient();
    const egresses = await egressClient.listEgress({ roomName });
    if (!egresses || egresses.length === 0) {
      return { isRecording: false, egresses: [] };
    }

    const activeEgress = egresses.find(e =>
      e.status === EgressStatus.EGRESS_ACTIVE || e.status === EgressStatus.EGRESS_STARTING
    );

    return {
      isRecording: !!activeEgress,
      egresses,
      activeEgressId: activeEgress?.egressId || null,
    };

  } catch (error) {
    errorLog('[LiveKit] Échec statut recording:', error.message);
    return { isRecording: false, error: error.message };
  }
}

module.exports = {
  createRoom,
  getRoom,
  roomExists,
  disableRoom,
  getParticipants,
  generateToken,
  startRecording,
  stopRecording,
  checkAndStopRecording,
  getRecordingStatus,
  validateConfig,
};