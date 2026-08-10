
const Meeting = require('../models/Meeting');
const MeetingRecording = require('../models/MeetingRecording');
const path = require('path');
const MeetingAIResult = require('../models/MeetingAIResult');
const Task = require('../models/Task');
const Notification = require('../models/Notification');
const User = require('../models/User');
const livekitService = require('../services/Livekitservice');
const notificationService = require('../services/notificationService');
const wsManager = require('../services/wsManager');
const { asyncHandler, successResponse, errorResponse } = require('../middleware/errorMiddleware');
const { checkAndGrantAchievements } = require('../services/achievementService');
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

const warnLog = (...args) => {
  console.warn(...args);
};

function checkPhysicalRecordingConditions(meeting) {
  if (meeting.meetingType !== 'physical') return false;
  if (!meeting.realMeetingStarted) return false;
  const hostId = extractId(meeting.createdBy);
  const joinedIds = extractJoinedUserIds(meeting.joinedParticipants);

  const isHostPresent = joinedIds.includes(hostId);
  const hasAtLeastOneGuest = joinedIds.filter(id => id !== hostId).length >= 1;
  return isHostPresent && hasAtLeastOneGuest;
}

function filterNullParticipants(meeting) {
  if (meeting?.joinedParticipants) {
    meeting.joinedParticipants = meeting.joinedParticipants.filter(p => p.userId != null);
  }
  return meeting;
}

function isHostRole(role) { return String(role || '').toLowerCase() === 'host'; }

function isGuestRole(role) { return String(role || '').toLowerCase() === 'guest'; }

function mergeAttendedByIds(existingAttendedBy = [], joinedParticipants = [], extraUserIds = []) {
  const existingIds = (existingAttendedBy || []).map((id) => id?.toString()).filter(Boolean);
  const joinedIds = extractJoinedUserIds(joinedParticipants);
  const extraIds = (extraUserIds || []).map((id) => id?.toString()).filter(Boolean);
  return [...new Set([...existingIds, ...joinedIds, ...extraIds])];
}

async function buildParticipantUsers(meeting) {
  const participantEmails = (meeting?.participants || [])
    .map((email) => String(email || '').trim().toLowerCase())
    .filter(Boolean);
  if (participantEmails.length === 0) {
    return [];
  }
  const users = await User.find({ email: { $in: participantEmails } })
    .select('_id name email profilePicture');
  const userByEmail = new Map(
    users.map((u) => [String(u.email || '').trim().toLowerCase(), u])
  );

  return participantEmails.map((email) => {
    const user = userByEmail.get(email);
    return {
      id: extractId(user) || null,
      email,
      name: user?.name || null,
      profilePicture: user?.profilePicture || null,
    };
  });
}

async function finishMeetingProperly(meeting, reason, broadcaster = wsManager, options = {}) {
  const meetingId = meeting._id.toString();
  const extraAttendedUserIds = Array.isArray(options.extraAttendedUserIds)
    ? options.extraAttendedUserIds
    : [];
  const triggeredByUserId = options.triggeredByUserId
    ? String(options.triggeredByUserId)
    : null;
  const attendedBy = mergeAttendedByIds(meeting.attendedBy, meeting.joinedParticipants, extraAttendedUserIds);

  try {
    const finishedMeeting = await Meeting.findOneAndUpdate(
      {
        _id: meeting._id,
        status: 'ongoing',
        realMeetingStarted: true,
      },
      {
        $set: {
          status: 'finished',
          activeEgressId: null,
          attendedBy,
          joinedParticipants: []
        }
      },
      { new: true }
    );

    if (!finishedMeeting) {
      const currentMeeting = await Meeting.findById(meeting._id).select('_id status realMeetingStarted');
      debugLog(`[Meeting] ${meetingId} finish skipped (already finished or not started)`);
      return currentMeeting;
    }

    const participantEmails = (meeting.participants || [])
      .map(email => String(email || '').trim().toLowerCase())
      .filter(Boolean);

    const guestUsers = participantEmails.length > 0
      ? await User.find({ email: { $in: participantEmails } }).select('_id')
      : [];

    const creatorId = extractId(meeting.createdBy);

    const joinedUserIds = extractJoinedUserIds(meeting.joinedParticipants);

    const uniqueUserIds = [...new Set([
      creatorId,
      ...guestUsers.map(u => u._id.toString()),
      ...joinedUserIds,
      ...extraAttendedUserIds.map((id) => id?.toString()).filter(Boolean)
    ].filter(Boolean))];

    const forceEndPayload = {
      event: 'meeting_force_end',
      meetingId,
      reason,
      countdown: 5,
      triggeredBy: triggeredByUserId,
    };

    let deliveredUsers = 0;
    uniqueUserIds.forEach(uid => {
      let delivered = false;
      if (broadcaster && typeof broadcaster.sendToUser === 'function') {
        delivered = broadcaster.sendToUser(uid, forceEndPayload);
      }
      if (delivered) deliveredUsers += 1;
    });
    debugLog(`[Meeting] ${meetingId} → finished (reason=${reason})`);
    debugLog(`[Meeting] meeting_force_end broadcast → ${deliveredUsers}/${uniqueUserIds.length} users | reason=${reason}`);

    // Laisse le temps au client de recevoir meeting_force_end avant la fermeture LiveKit.
    await new Promise(resolve => setTimeout(resolve, 300));

    if (meeting.roomId) {
      try {
        await livekitService.disableRoom(meeting.roomId);
        debugLog(`[Meeting] Room LiveKit supprimée: ${meeting.roomId}`);
      } catch (err) {
        warnLog(`[Meeting] disableRoom warning: ${err.message}`);
      }
    }
    return finishedMeeting;
  } catch (err) {
    errorLog(`[Meeting] finishMeetingProperly failed: ${err.message}`);
    throw err;
  }
}

// CREATE
const createMeeting = asyncHandler(async (req, res) => {
  const { title, description, startTime, duration, participants, meetingType, location } = req.body;
  const userId = req.userId;

  const requestedStart = new Date(startTime);
  if (isNaN(requestedStart.getTime())) {
    return errorResponse(res, 'Date de début invalide', 400);
  }
  if (requestedStart <= new Date()) {
    return errorResponse(res, 'La date et l\'heure de début doivent être dans le futur', 400);
  }

  const meeting = await Meeting.create({
    title,
    description,
    startTime,
    duration,
    meetingType,
    location: meetingType === 'physical' ? location : null,
    createdBy: userId,
    participants: participants || [],
    status: 'scheduled',
    realMeetingStarted: false,
  });

  debugLog(`[CreateMeeting] Réunion créée status=scheduled : ${meeting._id} — startTime=${meeting.startTime}`);
  try {
    await notificationService.notifyMeetingCreated(meeting, req.user.name);
  } catch (notifErr) {
    warnLog('[CreateMeeting] Notification error:', notifErr.message);
  }
  await meeting.populate('createdBy', 'name email profilePicture');
  // Fire-and-forget:check achievements for creator
  checkAndGrantAchievements(userId).catch(e => errorLog('[Achievement] createMeeting:', e.message));
  return successResponse(res, { meeting }, 'Réunion créée', 201);
});

// GET LIST
const getMeetings = asyncHandler(async (req, res) => {
  try {
    const userEmail = req.user.email.toLowerCase();
    const userId = req.userId;
    const { status, page = 1, limit = 20, all = 'false' } = req.query;
    const pageNumber = Math.max(parseInt(page, 10) || 1, 1);
    const limitNumber = Math.max(parseInt(limit, 10) || 20, 1);
    const fetchAll = all === 'true';
    const userFilter = { $or: [{ createdBy: userId }, { participants: userEmail }] };
    let query;

    if (status === 'archived') {
      query = { ...userFilter, status: 'archived' };
    } else if (status) {
      const safeStatus = status === 'cancelled' ? null : status;
      if (!safeStatus) {
        return successResponse(res, {
          meetings: [],
          pagination: { page: 1, limit: limitNumber, total: 0, pages: 0 }
        }, 'Meetings récupérés');
      }
      query = { ...userFilter, status: safeStatus };
    } else {
      query = { ...userFilter, status: { $nin: ['cancelled', 'archived'] } };
    }

    const meetingsQuery = Meeting.find(query)
      .populate('createdBy', 'name email profilePicture')
      .sort({ startTime: -1 });

    if (!fetchAll) {
      meetingsQuery.skip((pageNumber - 1) * limitNumber).limit(limitNumber);
    }

    const meetings = await meetingsQuery;

    const total = await Meeting.countDocuments(query);

    const meetingsWithPermissions = meetings.map(m => {
      filterNullParticipants(m);
      const obj = m.toJSON();
      obj.permissions = m.getActionPermissions(userId, userEmail);
      obj.userRole = m.getUserRole(userId, userEmail);
      return obj;
    });

    return successResponse(res, {
      meetings: meetingsWithPermissions,
      pagination: {
        page: pageNumber,
        limit: fetchAll ? total : limitNumber,
        total,
        pages: fetchAll ? (total > 0 ? 1 : 0) : (Math.ceil(total / limitNumber) || 0)
      }
    }, 'Meetings récupérés');

  } catch (err) {
    errorLog('[getMeetings] Erreur:', err.message, err.stack);
    return res.status(500).json({ success: false, message: 'Erreur serveur lors de la récupération des réunions' });
  }
});

// GET BY ID
const getMeetingById = asyncHandler(async (req, res) => {
  try {
    const meeting = req.meeting;
    const userEmail = req.user.email.toLowerCase();
    const userId = req.userId;

    filterNullParticipants(meeting);

    if (!meeting.hasAccess(userId, userEmail)) {
      return errorResponse(res, 'Accès non autorisé', 403);
    }

    await meeting.populate('createdBy', 'name email profilePicture');
    await meeting.populate('notes.userId', 'name email profilePicture');
    await meeting.populate('joinedParticipants.userId', '_id name email profilePicture');

    const meetingObject = meeting.toJSON
      ? meeting.toJSON()
      : meeting;

    if (Array.isArray(meetingObject.joinedParticipants)) {
      meetingObject.joinedParticipants = meetingObject.joinedParticipants
        .map((p) => {
          const hydratedUser = p?.userId && typeof p.userId === 'object' ? p.userId : null;
          const normalizedId = hydratedUser?._id
            ? hydratedUser._id.toString()
            : (p?.userId ? p.userId.toString() : null);
          if (!normalizedId) return null;

          return {
            id: normalizedId,
            email: hydratedUser?.email || null,
            name: hydratedUser?.name || null,
            profilePicture: hydratedUser?.profilePicture || null,
            role: p?.role || null,
            joinedAt: p?.joinedAt || null,
            lastSeen: p?.lastSeen || null,
          };
        })
        .filter(Boolean);
    }

    meetingObject.participantUsers = await buildParticipantUsers(meeting);

    return successResponse(res, {
      meeting: meetingObject,
      userRole: meeting.getUserRole(userId, userEmail),
      permissions: meeting.getActionPermissions(userId, userEmail)
    }, 'Réunion récupérée');

  } catch (err) {
    errorLog('[getMeetingById] Erreur:', err.message);
    return res.status(500).json({ success: false, message: 'Erreur serveur lors de la récupération de la réunion' });
  }
});

// UPDATE
const updateMeeting = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const { title, description, startTime, duration, participants, meetingType, location } = req.body;

  if (!meeting.isCreator(req.userId)) return errorResponse(res, 'Seul le créateur peut modifier', 403);
  if (meeting.status !== 'scheduled') return errorResponse(res, 'Impossible de modifier une réunion en cours ou terminée', 400);

  if (title) meeting.title = title;
  if (description !== undefined) meeting.description = description;
  if (startTime) meeting.startTime = startTime;
  if (duration) meeting.duration = duration;
  if (participants) meeting.participants = participants;
  if (meetingType) {
    meeting.meetingType = meetingType;
    if (meetingType === 'online') {
      meeting.location = null;
    }
  }
  if (location !== undefined) {
    meeting.location = meeting.meetingType === 'physical' ? location : null;
  }

  await meeting.save();
  await meeting.populate('createdBy', 'name email profilePicture');
  try {
    await notificationService.notifyMeetingUpdated(meeting, req.user.name);
  } catch (notifErr) {
    warnLog('[updateMeeting] Notification error:', notifErr.message);
  }
  return successResponse(res, { meeting }, 'Réunion mise à jour');
});

// CANCEL
const cancelMeeting = asyncHandler(async (req, res) => {
  const { meeting } = req;

  if (!meeting.isCreator(req.userId)) {
    return errorResponse(res, 'Seul le créateur peut annuler la réunion', 403);
  }

  if (meeting.status !== 'scheduled') {
    return errorResponse(res, 'Seule une réunion planifiée peut être annulée', 400);
  }

  meeting.status = 'cancelled';
  await meeting.save();

  debugLog(`[cancelMeeting] Réunion ${meeting._id} annulée par ${req.userId}`);

  if (meeting.roomId) {
    try { await livekitService.disableRoom(meeting.roomId); }
    catch (e) { warnLog('[cancelMeeting] disableRoom warning:', e.message); }
  }

  try {
    await notificationService.notifyMeetingCancelled(meeting, req.user.name);
  } catch (notifErr) {
    warnLog('[cancelMeeting] Notification error:', notifErr.message);
  }

  return successResponse(res, { message: 'Réunion annulée avec succès' }, 'Réunion annulée');
});

// JOIN
const joinMeeting = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const userEmail = req.user.email.toLowerCase();
  const userId = req.userId;

  if (!meeting.hasAccess(userId, userEmail)) {
    return errorResponse(res, 'Vous n\'êtes pas invité à cette réunion', 403);
  }

  if (meeting.status !== 'ongoing') {
    return errorResponse(res, 'La réunion n\'est pas encore disponible ou terminée', 400);
  }

  const roomName = `meeting-${meeting._id}`;
  if (!meeting.roomId) {
    try {
      const room = await livekitService.createRoom(meeting._id.toString(), meeting.title);
      meeting.roomId = room.id;
      await Meeting.updateOne({ _id: meeting._id }, { $set: { roomId: room.id } });
      debugLog(`[Join] Room LiveKit créée: ${room.id}`);
    } catch (roomErr) {
      errorLog('[Join] Échec création room LiveKit:', roomErr.message);
      return errorResponse(res, 'Impossible d\'initialiser la salle vidéo', 500);
    }
  } else {
    const exists = await livekitService.roomExists(meeting.roomId);
    if (!exists) {
      try {
        const room = await livekitService.createRoom(meeting._id.toString(), meeting.title);
        meeting.roomId = room.id;
        await Meeting.updateOne({ _id: meeting._id }, { $set: { roomId: room.id } });
        debugLog(`[Join] Room LiveKit recréée: ${room.id}`);
      } catch (roomErr) {
        errorLog('[Join] Échec recréation room LiveKit:', roomErr.message);
        return errorResponse(res, 'Impossible d\'initialiser la salle vidéo', 500);
      }
    }
  }

  const role = meeting.isCreator(userId) ? 'host' : 'guest';

  let isRecording = false;
  let activeEgressId = null;
  if (meeting.roomId) {
    try {
      const recordingStatus = await livekitService.getRecordingStatus(meeting.roomId);
      isRecording = !!recordingStatus?.isRecording;
      activeEgressId = recordingStatus?.activeEgressId || null;
    } catch (recordingErr) {
      warnLog(`[Join] getRecordingStatus warning: ${recordingErr.message}`);
    }
  }

  let token;
  try {
    token = await livekitService.generateToken(meeting.roomId, userId.toString(), req.user.name, role);
  } catch (tokenErr) {
    errorLog('[Join] Échec génération token:', tokenErr.message);
    return errorResponse(res, 'Impossible de générer le token d\'accès', 500);
  }

  await Meeting.updateOne(
    { _id: meeting._id },
    {
      $addToSet: {
        attendedBy: userId,
        joinedParticipants: {
          userId,
          role,
          joinedAt: new Date(),
          lastSeen: new Date(),
        }
      }
    }
  );

  setImmediate(async () => {  
    try {  
      const freshMeeting = await Meeting.findById(meeting._id)  
        .select('_id roomId realMeetingStarted joinedParticipants meetingType status');  
      if (  
        !freshMeeting ||  
        freshMeeting.status !== 'ongoing' ||  
        !freshMeeting.realMeetingStarted ||  
        !freshMeeting.roomId ||  
        freshMeeting.meetingType !== 'online'  
      ) return;  

      const stoppedRec = await MeetingRecording.findOne({  
        meetingId: freshMeeting._id,  
        manuallyStoppedAt: { $ne: null }  
      });  
      if (stoppedRec) return;  
 
      const currentRec = await MeetingRecording.findOne({ meetingId: freshMeeting._id });  
      if (currentRec?.recordingId && currentRec.recordingStatus === 'recording') return;  
 
      const participants = freshMeeting.joinedParticipants || [];  
      const hasHost = participants.some(p => p.role === 'host');  
      const hasGuest = participants.some(p => p.role === 'guest');  
      if (!hasHost || !hasGuest) return;  

      const resultStart = await livekitService.startRecording(freshMeeting.roomId);  
      await MeetingRecording.findOneAndUpdate(  
        { meetingId: freshMeeting._id },  
        {  
          $set: {  
            source: 'livekit',  
            recordingId: resultStart.egressId,  
            recordingFilename: resultStart.filename,  
            recordingStatus: 'recording',  
            recordingStartedAt: new Date()  
          }  
        },  
        { upsert: true, new: true, setDefaultsOnInsert: true }  
      );  
      wsManager.broadcastToMeeting(freshMeeting._id.toString(), {  
        event: 'recording_started',  
        meetingId: freshMeeting._id.toString(),  
        egressId: resultStart.egressId  
      });  
      debugLog(`[Join] Recording démarré pour meeting ${freshMeeting._id}`);  
    } catch (recErr) {   
      warnLog(`[Join] Recording immédiat échoué (Cron B reprendra): ${recErr.message}`);  
    }  
  });  

  const updatedMeeting = await Meeting.findById(meeting._id);
  const participantCount = updatedMeeting?.joinedParticipants?.length || 0;
  debugLog(`[Join] userId=${userId} rejoint roomName=${meeting.roomId}, participants actuels=${participantCount}`);

  // Fire-and-forget: check achievements for user who joined
  checkAndGrantAchievements(userId).catch(e => errorLog('[Achievement] joinMeeting:', e.message));

  return successResponse(res, {
    meeting: {
      id: meeting._id,
      title: meeting.title,
      status: meeting.status,
      realMeetingStarted: meeting.realMeetingStarted,
      roomId: meeting.roomId,
      participantCount,
      isRecording,
      activeEgressId,
    },
    token,
    livekitUrl: process.env.LIVEKIT_URL,
    roomName: meeting.roomId,
    role,
    role_display: role === 'host' ? 'hôte' : 'invité',
  }, 'Réunion rejointe');
});

const joinPhysicalMeeting = asyncHandler(async (req, res) => {
  try {
    const { meeting } = req;
    const userId = req.userId.toString();
    const userEmail = req.user.email.toLowerCase();

    if (meeting.meetingType !== 'physical') {
      return errorResponse(res, 'Cette route est réservée aux réunions présentielles', 400);
    }

    if (!meeting.hasAccess(userId, userEmail)) {
      return errorResponse(res, 'Vous n\'êtes pas invité à cette réunion', 403);
    }

    if (meeting.status !== 'ongoing') {
      return errorResponse(res, 'La réunion n\'est pas encore disponible ou est terminée', 400);
    }

    // Vérifier la fenêtre 
    const now = new Date();
    const startTime = new Date(meeting.startTime);
    const endTime = new Date(startTime.getTime() + meeting.duration * 60 * 1000);
    const fiveMinsBefore = new Date(startTime.getTime() - 5 * 60 * 1000);

    if (now < fiveMinsBefore) {
      return errorResponse(res, 'La réunion n\'est pas encore accessible (dans moins de 5 min avant le début)', 400);
    }
    if (now > endTime) {
      return errorResponse(res, 'La réunion est terminée', 400);
    }

    const alreadyJoined = (meeting.joinedParticipants || []).some((participant) => {
      return extractId(participant.userId) === userId;
    });

    if (alreadyJoined) {
      const existing = await Meeting.findById(meeting._id)
        .populate('joinedParticipants.userId', '_id name email profilePicture')
        .populate('createdBy', '_id name email profilePicture');

      const joinedParticipants = (existing?.joinedParticipants || [])
        .map((p) => {
          const hydratedUser = p.userId && typeof p.userId === 'object' ? p.userId : null;
          const id = hydratedUser?._id
            ? hydratedUser._id.toString()
            : (p.userId ? p.userId.toString() : null);

          if (!id) return null;

          return {
            id: id,
            name: hydratedUser?.name || null,
            email: hydratedUser?.email || null,
            profilePicture: hydratedUser?.profilePicture || null,
          };
        })
        .filter(Boolean);

      return successResponse(res, {
        joinedParticipants,
        status: existing?.status || meeting.status
      }, 'Already joined');
    }

    // Ajouter userId à joinedParticipants
    const role = meeting.isCreator(userId) ? 'host' : 'guest';

    await Meeting.updateOne(
      { _id: meeting._id },
      {
        $addToSet: {
          attendedBy: userId,
          joinedParticipants: {
            userId,
            role,
            joinedAt: new Date(),
            lastSeen: new Date(),
          }
        }
      }
    );

    // Recharger avec joinedParticipants peuplés
    const updated = await Meeting.findById(meeting._id)
      .populate('joinedParticipants.userId', '_id name email profilePicture')
      .populate('createdBy', '_id name email profilePicture');

    const meetingId = meeting._id.toString();

    // Broadcast participant_joined à tous
    const normalizedForWs = (updated.joinedParticipants || [])
      .map((p) => {
        const hydratedUser = p.userId && typeof p.userId === 'object' ? p.userId : null;
        const participantId = hydratedUser?._id
          ? hydratedUser._id.toString()
          : (p.userId ? p.userId.toString() : null);
        if (!participantId) return null;
        return {
          id: participantId,
          name: hydratedUser?.name || null,
          email: hydratedUser?.email || null,
          profilePicture: hydratedUser?.profilePicture || null,
        };
      })
      .filter(Boolean);

    wsManager.broadcastToMeeting(meetingId, {
      event: 'participant_joined',
      meetingId,
      userId,
      joinedParticipants: normalizedForWs
    });

    // Vérifier et démarrer le recording physical de manière atomique
    const shouldStartRecording = checkPhysicalRecordingConditions(updated);
    if (shouldStartRecording) {
      const existingRec = await MeetingRecording.findOne({
        meetingId: updated._id,
        manuallyStoppedAt: { $ne: null }
      });

      if (existingRec) {
        debugLog(`[join/physical] Skip: recording stoppé manuellement pour meeting ${meetingId}`);
      } else {
      try {
        const atomicResult = await MeetingRecording.findOneAndUpdate(
              {
                meetingId: updated._id
              },
              {
                $setOnInsert: {
                  source: 'physical_upload',
                  recordingStatus: 'recording',
                  recordingStartedAt: new Date()
                }
              },
              { upsert: true, new: false }
            ).catch(err => {
              if (err.code === 11000 || err.codeName === 'DuplicateKey') return null;
              throw err;
            });

            const wasAlreadyStarted = atomicResult &&
              ['recording', 'available', 'processing', 'downloading']
              .includes(atomicResult.recordingStatus);

            if (!wasAlreadyStarted) {
              wsManager.broadcastToMeeting(meetingId, {
                event: 'recording_started',
                meetingId
              });
          debugLog(`[join/physical] recording_started → meeting ${meetingId} (déclenché par join de ${userId})`);
        } else {
          debugLog(`[join/physical] Skip: recording déjà actif pour meeting ${meetingId} (${atomicResult?.recordingStatus})`);
        }
      } catch (recErr) {
        // Ne jamais bloquer le join pour une erreur de recording
        errorLog(`[join/physical] Erreur atomique recording: ${recErr.message}`);
      }
      }
    }

    // Fire-and-forget: check achievements for user who checked in
    checkAndGrantAchievements(userId).catch(e => errorLog('[Achievement] joinPhysical:', e.message));

    const joinedParticipants = (updated.joinedParticipants || [])
      .map((p) => {
        const hydratedUser = p.userId && typeof p.userId === 'object' ? p.userId : null;
        const id = hydratedUser?._id
          ? hydratedUser._id.toString()
          : (p.userId ? p.userId.toString() : null);

        if (!id) return null;

        return {
          id: id,
          name: hydratedUser?.name || null,
          email: hydratedUser?.email || null,
          profilePicture: hydratedUser?.profilePicture || null,
        };
      })
      .filter(Boolean);

    return successResponse(res, {
      joinedParticipants,
      status: updated.status
    }, 'Check-in présentiel enregistré');

  } catch (err) {
    errorLog('[join/physical] Erreur:', err.message);
    return errorResponse(res, err.message, 500);
  }
});

const leavePhysicalMeeting = asyncHandler(async (req, res) => {
  try {
    const { meeting } = req;
    const userId = req.userId.toString();

    if (meeting.meetingType !== 'physical') {
      return errorResponse(res, 'Cette route est réservée aux réunions présentielles', 400);
    }

    if (meeting.status === 'cancelled') {
      return errorResponse(res, 'La réunion n\'est plus disponible', 400);
    }

    if (meeting.status === 'finished') {
      return successResponse(res, {
        success: true,
        joinedParticipants: [],
        status: 'finished'
      }, 'Réunion déjà terminée');
    }

    // Retirer userId de joinedParticipants
    await Meeting.updateOne(
      { _id: meeting._id },
      { $pull: { joinedParticipants: { userId } } }
    );

    const updated = await Meeting.findById(meeting._id)
      .populate('createdBy', '_id name email profilePicture');

    // Broadcast participant_left
    wsManager.broadcastToMeeting(meeting._id.toString(), {
      event: 'participant_left',
      meetingId: meeting._id.toString(),
      userId
    });

    // Vérifier les conditions de fin
    const hostId = extractId(updated.createdBy);
    const remainingIds = extractJoinedUserIds(updated.joinedParticipants);
    const isHostLeaving = userId === hostId;
    const remainingGuests = remainingIds.filter(id => id !== hostId);

    let finishReason = null;
    if (remainingIds.length === 0) {
      finishReason = 'all_left';
    } else if (isHostLeaving) {
      finishReason = 'host_left';
    } else if (remainingGuests.length === 0) {
      finishReason = 'all_guests_left';
    }

    if (finishReason && updated.realMeetingStarted) {
      await finishMeetingProperly(updated, finishReason, wsManager, {
        extraAttendedUserIds: [userId.toString()],
        triggeredByUserId: userId.toString(),
      });
    }

    return successResponse(res, {
      success: true,
      message: 'Check-out présentiel enregistré'
    }, 'Check-out présentiel enregistré');

  } catch (err) {
    errorLog('[leave/physical] Erreur:', err.message);
    return errorResponse(res, err.message, 500);
  }
});

// LEAVE
const leaveMeeting = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const userId = req.userId;

  if (meeting.status === 'finished') {
    return successResponse(res, {
      status: 'finished'
    }, 'Réunion déjà terminée');
  }

  const participant = meeting.joinedParticipants?.find(p => {
    return extractId(p.userId) === userId.toString();
  });

  if (!participant) {
    return errorResponse(res, 'Vous n\'êtes pas dans cette réunion', 400);
  }

  await Meeting.updateOne(
    { _id: meeting._id },
    { $pull: { joinedParticipants: { userId } } }
  );

  // recording ne bloque jamais le flux principal leave
  try {
    const { checkAndStopRecording } = require('../services/Livekitservice');
    await checkAndStopRecording(meeting._id, 'Leave');
  } catch (err) {
    errorLog(`[Leave] Erreur checkAndStopRecording: ${err.message}`);
  }

  const updated = await Meeting.findById(meeting._id);
  const remaining = updated?.joinedParticipants || [];

  if (!updated) {
    return errorResponse(res, 'Réunion introuvable', 404);
  }

  if (!updated.realMeetingStarted) {
    debugLog(`[Leave] ${userId} quitte la pré-réunion ${meeting._id}, status reste ongoing`);
    _notifyParticipantLeft(meeting, userId, remaining);
    return successResponse(res, {
      meeting: {
        id: updated._id,
        status: updated.status,
        realMeetingStarted: false,
        participantCount: remaining.length,
      }
    }, 'Réunion quittée');
  }

  const remainingHosts = remaining.filter(p => isHostRole(p.role));
  const remainingGuests = remaining.filter(p => isGuestRole(p.role));
  const allLeft = remaining.length === 0;
  const noHost = remainingHosts.length === 0;
  const allGuestsLeftWithHostRemaining = remainingGuests.length === 0 && remainingHosts.length > 0;

  if (allGuestsLeftWithHostRemaining) {
    debugLog(`[Leave] Tous les guests partis, host seul → finish meeting ${meeting._id}`);
    _notifyParticipantLeft(meeting, userId, remaining);

    const finalMeeting = await finishMeetingProperly(updated, 'all_guests_left', wsManager, {
      extraAttendedUserIds: [userId.toString()],
      triggeredByUserId: userId.toString(),
    });
    return successResponse(res, {
      meeting: {
        id: finalMeeting?._id || meeting._id,
        title: finalMeeting?.title || meeting.title,
        status: 'finished',
        realMeetingStarted: true,
        participantCount: 0,
      },
      reason: 'all_guests_left'
    }, 'Réunion terminée: tous les guests ont quitté');
  }
  else if (allLeft || noHost) {
    const finishReason = allLeft ? 'all_left' : 'host_left';
    debugLog(`[Leave] Réunion ${meeting._id} → finished (reason=${finishReason})`);
    _notifyParticipantLeft(meeting, userId, remaining);

    const finalMeeting = await finishMeetingProperly(updated, finishReason, wsManager, {
      extraAttendedUserIds: [userId.toString()],
      triggeredByUserId: userId.toString(),
    });
    return successResponse(res, {
      meeting: {
        id: finalMeeting?._id || meeting._id,
        title: finalMeeting?.title || meeting.title,
        status: 'finished',
        realMeetingStarted: true,
        participantCount: 0,
      },
      reason: finishReason
    }, 'Réunion quittée');
  }

  const finalMeeting = await Meeting.findById(meeting._id).populate('createdBy', 'name email profilePicture');
  _notifyParticipantLeft(meeting, userId, remaining);

  return successResponse(res, {
    meeting: {
      id: finalMeeting._id,
      title: finalMeeting.title,
      status: finalMeeting.status,
      realMeetingStarted: finalMeeting.realMeetingStarted,
      participantCount: finalMeeting.joinedParticipants?.length || 0,
    }
  }, 'Réunion quittée');
});

function _notifyParticipantLeft(meeting, leavingUserId, remaining) {
  if (!wsManager) return;
  const meetingId = meeting._id.toString();
  const userId = leavingUserId.toString();
  const creatorId = extractId(meeting.createdBy);

  if (creatorId && creatorId !== userId) {
    wsManager.sendToUser(creatorId, { event: 'meeting_participant_left', meetingId, userId });
  }

  remaining.forEach(p => {
    const pId = extractId(p.userId);
    if (pId && pId !== userId) {
      wsManager.sendToUser(pId, { event: 'meeting_participant_left', meetingId, userId });
    }
  });
}

// HEARTBEAT
const heartbeat = asyncHandler(async (req, res) => {
  const { id } = req.params;
  const userId = req.userId;
  await Meeting.updateOne(
    { _id: id, 'joinedParticipants.userId': userId },
    { $set: { 'joinedParticipants.$.lastSeen': new Date() } }
  );
  return successResponse(res, null, 'ok');
});

// NOTES 
const addNote = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const { content } = req.body;
  const userId = req.userId;

  const hasJoined = meeting.joinedParticipants?.some(p => {
    return extractId(p.userId) === userId.toString();
  });
  if (!hasJoined) return errorResponse(res, 'Vous devez rejoindre la réunion d\'abord', 400);

  meeting.notes.push({ userId, content, timestamp: new Date() });
  await meeting.save();
  await meeting.populate('notes.userId', 'name email profilePicture');

  const newNote = meeting.notes[meeting.notes.length - 1];
  await wsManager.broadcastToMeeting(meeting._id.toString(), {
    event: 'note_added',
    meetingId: meeting._id.toString(),
    note: {
      id: newNote?._id?.toString(),
      content: newNote?.content || '',
      userId: extractId(newNote?.userId),
      userName: newNote?.userId?.name || req.user?.name || 'Inconnu',
      createdAt: newNote?.timestamp || new Date()
    }
  });

  // Fire-and-forget: check achievements for note author
  checkAndGrantAchievements(userId).catch(e => errorLog('[Achievement] addNote:', e.message));

  return successResponse(res, { note: newNote }, 'Note ajoutée');
});

const getNotes = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const userEmail = req.user.email.toLowerCase();
  const userId = req.userId;

  if (!meeting.hasAccess(userId, userEmail)) {
    return errorResponse(res, 'Accès non autorisé', 403);
  }

  await meeting.populate('notes.userId', 'name email profilePicture');
  return successResponse(res, { notes: meeting.notes }, 'Notes récupérées');
});

// TOKEN
const getToken = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const userEmail = req.user.email.toLowerCase();
  const userId = req.userId;

  if (!meeting.hasAccess(userId, userEmail)) return errorResponse(res, 'Accès non autorisé', 403);
  if (!meeting.roomId) return errorResponse(res, 'Salle non initialisée', 400);

  const role = meeting.isCreator(userId) ? 'host' : 'guest';
  const token = await livekitService.generateToken(meeting.roomId, userId.toString(), req.user.name, role);
  return successResponse(res, { token, livekitUrl: process.env.LIVEKIT_URL }, 'Token généré');
});

// Parse dueDateHint
function parseDueDateHint(hint, referenceDate = new Date()) {
  if (!hint || typeof hint !== 'string') return null;

  const normalizedHint = hint.toLowerCase().trim();
  const now = new Date(referenceDate);
  const base = new Date(referenceDate);
  base.setHours(0, 0, 0, 0);

  const hoursMatch = normalizedHint.match(/dans\s+(\d+)\s+heure/);
  if (hoursMatch) {
    const result = new Date(now);
    result.setHours(result.getHours() + parseInt(hoursMatch[1], 10));
    return result;
  }

  const daysMatch = normalizedHint.match(/dans\s+(\d+)\s+jour/);
  if (daysMatch) {
    const result = new Date(base);
    result.setDate(result.getDate() + parseInt(daysMatch[1], 10));
    return result;
  }

  const weeksMatch = normalizedHint.match(/dans\s+(\d+)\s+semaine/);
  if (weeksMatch) {
    const result = new Date(base);
    result.setDate(result.getDate() + parseInt(weeksMatch[1], 10) * 7);
    return result;
  }

  const monthsMatch = normalizedHint.match(/dans\s+(\d+)\s+mois/);
  if (monthsMatch) {
    const result = new Date(base);
    result.setMonth(result.getMonth() + parseInt(monthsMatch[1], 10));
    return result;
  }

  if (normalizedHint.includes('demain')) {
    const result = new Date(base);
    result.setDate(result.getDate() + 1);
    return result;
  }

  if (normalizedHint.includes('semaine prochaine')) {
    const result = new Date(base);
    result.setDate(result.getDate() + 7);
    return result;
  }

  if (normalizedHint.includes('mois prochain')) {
    const result = new Date(base);
    result.setMonth(result.getMonth() + 1);
    return result;
  }

  if (normalizedHint.includes('ce soir') || normalizedHint.includes("aujourd'hui")) {
    const result = new Date(now);
    result.setHours(20, 0, 0, 0);
    return result;
  }

  const explicitDate = new Date(hint);
  if (!Number.isNaN(explicitDate.getTime())) {
    return explicitDate;
  }

  return null;
}

const receiveAIResult = asyncHandler(async (req, res) => {
  const { id: meetingId } = req.params;
  const expectedSecret = process.env.AI_CALLBACK_SECRET;
  const callbackSecret = req.headers['x-ai-callback-secret'];
  const ip = (req.ip || req.socket?.remoteAddress || '').toString();
  const localIp = ip.includes('127.0.0.1') || ip.includes('::1');

  if (expectedSecret) {
    if (!callbackSecret || callbackSecret !== expectedSecret) {
      return errorResponse(res, 'Forbidden callback', 403);
    }
  } else if (!localIp) {
    return errorResponse(res, 'Forbidden callback', 403);
  }

  const meeting = await Meeting.findById(meetingId);
  if (!meeting) {
    return errorResponse(res, 'Réunion introuvable', 404);
  }

  const { transcript, summary, error, pipelineStatus } = req.body || {};
  const tasksToCreate = (summary?.actionItems || [])
    .filter(item => item?.text)
    .map(item => ({
      title: item.text,
      ownerHint: item.ownerHint || null,
      dueDateHint: item.dueDateHint || null,
      priority: item.priorityHint || 'medium'
    }));

  if (error) {
    meeting.aiStatus = 'failed';
    await meeting.save();
    return successResponse(res, { meetingId, aiStatus: meeting.aiStatus }, 'AI result received with error');
  }

  const transcriptPayload = (transcript && typeof transcript === 'object')
    ? transcript
    : { rawText: typeof transcript === 'string' ? transcript : '' };

  const durationCandidate = transcriptPayload.durationSeconds;
  const parsedDurationSeconds = durationCandidate === null || durationCandidate === undefined
    ? null
    : Number(durationCandidate);

  // Déterminer le aiStatus final basé sur pipelineStatus
  let finalAiStatus = 'completed';
  if (pipelineStatus === 'empty_audio' || (transcriptPayload && transcriptPayload.isEmpty === true)) {
    finalAiStatus = 'completed_empty';
  } else if (pipelineStatus === 'failed' || pipelineStatus === 'transcription_failed') {
    finalAiStatus = 'failed';
  }

  await MeetingAIResult.findOneAndUpdate(
    { meetingId },
    {
      $set: {
        transcript: {
          rawText: transcriptPayload.rawText || '',
          language: transcriptPayload.language || null,
          durationSeconds: Number.isFinite(parsedDurationSeconds) ? parsedDurationSeconds : null
        },
        summary: {
          keyPoints: summary?.keyPoints || [],
          decisions: summary?.decisions || [],
          actionItems: summary?.actionItems || []
        },
        pipelineStatus: pipelineStatus || 'completed',
        updatedAt: new Date()
      }
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );

  const participantEmails = new Set((meeting.participants || []).map((p) => String(p).trim().toLowerCase()));
  const hostUser = await User.findById(meeting.createdBy).select('email');
  if (hostUser?.email) {
    participantEmails.add(String(hostUser.email).trim().toLowerCase());
  }
  
  // Créer des tâches UNIQUEMENT si aiStatus = completed
  let createdCount = 0;
  const createdTasks = [];
  
  if (finalAiStatus === 'completed' && Array.isArray(tasksToCreate) && tasksToCreate.length > 0) {
    const ownerEmailsToResolve = [];
    for (const rawTask of tasksToCreate) {
      const ownerHint = rawTask?.ownerHint;
      if (!ownerHint) continue;
      const normalizedOwnerEmail = String(ownerHint).trim().toLowerCase();
      if (participantEmails.has(normalizedOwnerEmail)) {
        ownerEmailsToResolve.push(normalizedOwnerEmail);
      }
    }

    const uniqueOwnerEmails = [...new Set(ownerEmailsToResolve)];
    const resolvedUsers = uniqueOwnerEmails.length > 0
      ? await User.find({ email: { $in: uniqueOwnerEmails } }).select('_id email')
      : [];
    const assigneeByEmail = new Map(resolvedUsers.map((u) => [String(u.email).toLowerCase(), u._id]));

    for (const rawTask of tasksToCreate) {
      if (!rawTask?.title) continue;

      const normalizedOwnerEmail = rawTask?.ownerHint ? String(rawTask.ownerHint).trim().toLowerCase() : null;
      let assigneeId = normalizedOwnerEmail && participantEmails.has(normalizedOwnerEmail)
        ? (assigneeByEmail.get(normalizedOwnerEmail) || null)
        : null;

      if (!assigneeId) {
        // Fallback : assigner au createur
        assigneeId = meeting.createdBy;
      }

      const dueDate = parseDueDateHint(rawTask?.dueDateHint);

      const createdTask = await Task.create({
        title: rawTask.title,
        assigneeId,
        meetingId,
        priority: ['high', 'medium', 'low'].includes(rawTask.priority) ? rawTask.priority : 'medium',
        dueDate,
        source: 'ai_summary'
      });
      createdTasks.push(createdTask);
      createdCount += 1;
    }
  }

  // Mettre à jour aiStatus
  meeting.aiStatus = finalAiStatus;
  await meeting.save();

  const allEmails = [...(meeting.participants || [])];
  if (hostUser?.email && !allEmails.includes(hostUser.email)) {
    allEmails.push(hostUser.email);
  }

  const users = await User.find({ email: { $in: allEmails } }).select('_id email');

  const summaryNotifDocs = users.map(user => ({
    userId: user._id,
    type: 'ai_summary_ready',
    title: 'Résumé IA disponible',
    message: `Le résumé de la reunion "${meeting.title}" est prêt.`,
    data: {
      meetingId: meeting._id.toString(),
      meetingTitle: meeting.title,
      actionUrl: `/meetings/${meeting._id}`
    },
    isRead: false,
    isDelivered: false
  }));

  const summaryNotifs = summaryNotifDocs.length > 0
    ? await Notification.insertMany(summaryNotifDocs)
    : [];

  const deliveredSummaryIds = [];
  summaryNotifs.forEach(notif => {
    const delivered = wsManager.sendToUser(notif.userId.toString(), {
      event: 'notification',
      notification: {
        id: notif._id.toString(),
        type: notif.type,
        title: notif.title,
        message: notif.message,
        data: notif.data,
        createdAt: notif.createdAt,
        payload: {
          taskId: null,
          meetingId: notif.data?.meetingId || null,
          meetingTitle: notif.data?.meetingTitle || null,
          startTime: null,
          actionUrl: notif.data?.actionUrl || null
        }
      }
    });

    if (delivered) deliveredSummaryIds.push(notif._id);
  });

  if (deliveredSummaryIds.length > 0) {
    await Notification.updateMany(
      { _id: { $in: deliveredSummaryIds } },
      { $set: { isDelivered: true, deliveredAt: new Date() } }
    );
  }

  // Broadcast WebSocket selon le statut IA
  if (finalAiStatus === 'completed') {
    await wsManager.broadcastToMeeting(meeting._id.toString(), {
      event: 'ai_summary_ready',
      meetingId: meeting._id.toString(),
      timestamp: new Date()
    });
  } else if (finalAiStatus === 'completed_empty') {
    await wsManager.broadcastToMeeting(meeting._id.toString(), {
      event: 'ai_summary_empty',
      meetingId: meeting._id.toString(),
      reason: 'silent_audio',
      timestamp: new Date()
    });
  } else if (finalAiStatus === 'failed') {
    await wsManager.broadcastToMeeting(meeting._id.toString(), {
      event: 'ai_summary_failed',
      meetingId: meeting._id.toString(),
      reason: pipelineStatus,
      timestamp: new Date()
    });
  }

  const tasksWithAssignee = createdTasks.filter(t => t.assigneeId);
  const taskNotifDocs = tasksWithAssignee.map(task => ({
    userId: task.assigneeId,
    type: 'task_assigned',
    title: 'Nouvelle tache assignee',
    message: `Tache: "${task.title}"`,
    data: {
      taskId: task._id.toString(),
      meetingId: meeting._id.toString(),
      meetingTitle: meeting.title,
      actionUrl: `/meetings/${meeting._id}`
    },
    isRead: false,
    isDelivered: false
  }));

  const taskNotifs = taskNotifDocs.length > 0
    ? await Notification.insertMany(taskNotifDocs)
    : [];

  const deliveredTaskIds = [];
  taskNotifs.forEach(notif => {
    const delivered = wsManager.sendToUser(notif.userId.toString(), {
      event: 'notification',
      notification: {
        id: notif._id,
        type: notif.type,
        title: notif.title,
        message: notif.message,
        data: notif.data,
        createdAt: notif.createdAt,
        payload: {
          meetingId: notif.data?.meetingId || null,
          meetingTitle: notif.data?.meetingTitle || null,
          startTime: null,
          actionUrl: notif.data?.actionUrl || null
        }
      }
    });

    if (delivered) deliveredTaskIds.push(notif._id);
  });

  if (deliveredTaskIds.length > 0) {
    await Notification.updateMany(
      { _id: { $in: deliveredTaskIds } },
      { $set: { isDelivered: true, deliveredAt: new Date() } }
    );
  }

  await wsManager.broadcastToMeeting(meetingId.toString(), {
    event: 'ai_result_ready',
    meetingId: meetingId.toString(),
    hasTranscript: !!transcript,
    hasSummary: !!summary,
    taskCount: createdCount
  });

  return successResponse(res, {
    meetingId,
    aiStatus: meeting.aiStatus,
    taskCount: createdCount
  }, 'AI result saved');
});

const getAIStatus = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const userId = req.userId;
  const userEmail = req.user.email.toLowerCase();

  if (!meeting.hasAccess(userId, userEmail)) {
    return errorResponse(res, 'Accès non autorisé', 403);
  }

  return successResponse(res, {
    meetingId: meeting._id,
    aiStatus: meeting.aiStatus || 'idle'
  }, 'AI status récupéré');
});

const getMeetingAIResult = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const userId = req.userId;
  const userEmail = req.user.email.toLowerCase();

  if (!meeting.hasAccess(userId, userEmail)) {
    return errorResponse(res, 'Accès non autorisé', 403);
  }

  const aiResult = await MeetingAIResult.findOne({ meetingId: meeting._id });

  if (!aiResult) {
    return errorResponse(res, 'Aucun résumé disponible pour cette réunion', 404);
  }

  return successResponse(res, {
    meetingId: aiResult.meetingId,
    summary: aiResult.summary,
    transcript: aiResult.transcript,
    createdAt: aiResult.createdAt,
    updatedAt: aiResult.updatedAt
  }, 'AI result récupéré');
});

const getMeetingTasks = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const userId = req.userId;
  const userEmail = req.user.email.toLowerCase();

  if (!meeting.hasAccess(userId, userEmail)) {
    return errorResponse(res, 'Accès non autorisé', 403);
  }

  const tasks = await Task.find({ meetingId: meeting._id }).sort({ createdAt: 1 });

  return successResponse(res, tasks, 'Tâches récupérées');
});

const getRecordingInfo = async (req, res) => {
  try {
    const { meetingId } = req.params;
    const userId = req.userId;
    const userEmail = String(req.user?.email || '').toLowerCase();

    const meeting = await Meeting.findById(meetingId);
    if (!meeting) {
      return res.status(404).json({ success: false, message: 'Meeting not found' });
    }

    if (!meeting.hasAccess(userId, userEmail)) {
      return res.status(403).json({ success: false, message: 'Accès non autorisé' });
    }

    const recording = await MeetingRecording.findOne({
      meetingId,
      recordingStatus: 'available'
    });

    if (!recording) {
      return res.json({ success: true, data: null });
    }

    const baseUrl = (process.env.BASE_URL)
      .replace(/\/$/, '');

    let recordingUrl = null;

    if (recording.source === 'physical_upload' && recording.recordingLocalPath) {
      const rawPath = recording.recordingLocalPath;
      const filename = require('path').win32.basename(rawPath) || require('path').basename(rawPath);
      recordingUrl = `${baseUrl}/recordings/physical/${filename}`;

    } else if (recording.source === 'livekit' && recording.recordingLocalPath) {
      const rawPath = recording.recordingLocalPath;
      const filename = require('path').win32.basename(rawPath) || require('path').basename(rawPath);
      recordingUrl = `${baseUrl}/recordings/online/${filename}`;

    } else if (recording.recordingUrl && recording.recordingUrl.startsWith('http')) {
      recordingUrl = recording.recordingUrl;
    }

    if (!recordingUrl) {
      return res.json({ success: true, data: null });
    }

    const fallbackDuration = Number.isFinite(meeting.duration)
      ? Math.round(meeting.duration * 60)
      : null;
    const duration = recording.recordingDuration ?? (
      recording.source === 'physical_upload' ? fallbackDuration : null
    );

    return res.json({
      success: true,
      data: {
        recordingUrl,
        duration,
        source: recording.source
      }
    });
  } catch (err) {
    errorLog('[getRecordingInfo]', err);
    return res.status(500).json({ success: false, message: err.message });
  }
};

module.exports = {
  createMeeting, getMeetings, getMeetingById, updateMeeting,cancelMeeting,
  joinMeeting, joinPhysicalMeeting, leaveMeeting, leavePhysicalMeeting, heartbeat,
  addNote, getNotes, getToken,
  receiveAIResult,
  getMeetingAIResult,
  getMeetingTasks,
  getRecordingInfo,
  getAIStatus,
  finishMeetingProperly,checkPhysicalRecordingConditions
};