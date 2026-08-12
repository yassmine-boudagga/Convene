const Meeting = require('../models/Meeting');
const MeetingRecording = require('../models/MeetingRecording');
const livekitService  = require('../services/Livekitservice');    
const notificationService = require('../services/notificationService');
const wsManager = require('../services/wsManager');
const aiService = require('../services/aiService');
const multer = require('multer');
const fs = require('fs');
const path = require('path');
const { asyncHandler, successResponse, errorResponse } = require('../middleware/errorMiddleware');
const { extractId } = require('../utils/idHelpers');
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

const PHYSICAL_RECORDINGS_DIR = path.resolve(__dirname, '../../recordings/physical');
if (!fs.existsSync(PHYSICAL_RECORDINGS_DIR)) {
  fs.mkdirSync(PHYSICAL_RECORDINGS_DIR, { recursive: true });
}

const allowedAudioExtensions = new Set(['.m4a', '.ogg', '.mp3']);

const physicalUpload = multer({
  storage: multer.diskStorage({
    destination: (req, file, cb) => cb(null, PHYSICAL_RECORDINGS_DIR),
    filename: (req, file, cb) => {
      const ext = path.extname(file.originalname || '').toLowerCase();
      const safeExt = allowedAudioExtensions.has(ext) ? ext : '.m4a';
      cb(null, `physical-${req.params.id}-${Date.now()}${safeExt}`);
    }
  }),
  limits: { fileSize: 500 * 1024 * 1024 },
  fileFilter: (req, file, cb) => {
    const ext = path.extname(file.originalname || '').toLowerCase();
    if (!file.mimetype?.startsWith('audio/')) {
      return cb(new Error('Seuls les fichiers audio sont autorisés'));
    }
    if (!allowedAudioExtensions.has(ext)) {
      return cb(new Error('Formats acceptés: .m4a, .ogg, .mp3'));
    }
    return cb(null, true);
  }
});

const runPhysicalUpload = (req, res) => new Promise((resolve, reject) => {
  physicalUpload.single('file')(req, res, (err) => {
    if (err) return reject(err);
    return resolve();
  });
});

// MANUAL STOP RECORDING
const stopRecording = asyncHandler(async (req, res, next) => {
  const { meeting } = req;
  const userId = req.userId;
  const creatorId = extractId(meeting.createdBy);

  if (creatorId !== userId.toString()) {
    return errorResponse(res, 'Seul le créateur peut stopper le recording', 403);
  }

  if (meeting.meetingType === 'online') {
    const recordingDoc = await MeetingRecording.findOne({
      meetingId: meeting._id,
      recordingStatus: 'recording'
    });

    if (!recordingDoc?.recordingId) {
      return errorResponse(res, 'Aucun enregistrement actif', 400);
    }

    if (!meeting.roomId) {
      return errorResponse(res, 'Room non initialisée', 400);
    }

    const stoppedAt = new Date();

    try {
      await livekitService.stopRecording(recordingDoc.recordingId);
    } catch (stopErr) {
      const msg = String(stopErr?.message || '').toLowerCase();
      if (msg.includes('object cannot be found') || msg.includes('not found')) {
        warnLog(`[stopRecording] egress non trouvé côté LiveKit (déjà terminé): ${stopErr.message}`);
        // Continuer:l'egress est déjà arrêté, on met à jour la DB quand même
      } else {
        throw stopErr;
      }
    }

    await MeetingRecording.updateOne(
      { meetingId: meeting._id },
      {
        $set: {
          recordingStatus: 'processing',
          recordingStoppedAt: stoppedAt,
          manuallyStoppedAt: stoppedAt,
          stoppedByHost: true
        }
      }
    );

    const wsManager = require('../services/wsManager');
    await wsManager.broadcastToMeeting(meeting._id.toString(), {
      event: 'recording_stopped',
      meetingId: meeting._id.toString(),
      stoppedByHost: true
    });

    const { finalizeEgress } = require('../services/recordingFinalizer');
    setImmediate(() => {
      finalizeEgress(recordingDoc.recordingId, meeting._id.toString()).catch(err => {
        errorLog(`[stopRecording] finalizeEgress erreur: ${err.message}`);
      });
    });

    return successResponse(res, {
      recordingId: recordingDoc.recordingId,
      stoppedAt,
      stoppedByHost: true,
      meetingStatus: 'ongoing'
    }, 'Recording stoppé. La réunion continue.');
  }

  if (meeting.meetingType === 'physical') {
    const recordingDoc = await MeetingRecording.findOne({
      meetingId: meeting._id,
      recordingStatus: 'recording'
    });

    if (!recordingDoc) {
      return errorResponse(res, 'Aucun enregistrement actif', 400);
    }

    const stoppedAt = new Date();

    await MeetingRecording.updateOne(
      { meetingId: meeting._id },
      {
        $set: {
          recordingStatus: 'processing',
          recordingStoppedAt: stoppedAt,
          manuallyStoppedAt: stoppedAt,
          stoppedByHost: true
        }
      }
    );

    const wsManager = require('../services/wsManager');
    await wsManager.broadcastToMeeting(meeting._id.toString(), {
      event: 'recording_stopped',
      meetingId: meeting._id.toString(),
      stoppedByHost: true
    });

    return successResponse(res, {
      stoppedByHost: true,
      meetingStatus: 'ongoing'
    }, 'Enregistrement stoppé. La réunion continue.');
  }

  return errorResponse(res, 'Type de réunion non supporté', 400);
});

const getRecordingStatus = asyncHandler(async (req, res, next) => {
  const { meeting } = req;

  if (meeting.meetingType === 'physical') {
    const recordingDoc = await MeetingRecording.findOne({ meetingId: meeting._id });
    return successResponse(res, {
      recordingId: recordingDoc?.recordingId || null,
      isRecording: recordingDoc?.recordingStatus === 'recording',
      startedAt: recordingDoc?.recordingStartedAt || null,
      stoppedAt: recordingDoc?.recordingStoppedAt || null,
      recordingUrl: recordingDoc?.recordingUrl || null,
      recordingDuration: recordingDoc?.recordingDuration || null,
      status: recordingDoc?.recordingStatus || 'none'
    }, 'Status retrieved');
  }

  if (!meeting.roomId) {
    return errorResponse(res, 'Room not initialized', 400);
  }

  let Status = null;
  const recordingDoc = await MeetingRecording.findOne({ meetingId: meeting._id });
  try {
    Status = await livekitService.getRecordingStatus(meeting.roomId);
  } catch (error) {
    debugLog('[Recording] Could not get status:', error.message);
  }

  return successResponse(res, {
    recordingId: recordingDoc?.recordingId || null,
    isRecording: recordingDoc?.recordingStatus === 'recording',
    startedAt: recordingDoc?.recordingStartedAt || null,
    stoppedAt: recordingDoc?.recordingStoppedAt || null,
    recordingUrl: recordingDoc?.recordingUrl || null,
    recordingDuration: recordingDoc?.recordingDuration || null,
    status: Status
  }, 'Status retrieved');
});

const uploadPhysicalRecording = asyncHandler(async (req, res) => {
  const { meeting } = req;
  const userId = req.userId;

  if (meeting.meetingType !== 'physical') {
    return errorResponse(res, 'Cette route est réservée aux réunions présentielles', 400);
  }

  if (!meeting.isCreator(userId)) {
    return errorResponse(res, 'Seul l\'organisateur peut uploader l\'audio', 403);
  }

  try {
    await runPhysicalUpload(req, res);
  } catch (uploadErr) {
    return errorResponse(res, uploadErr.message || 'Upload invalide', 400);
  }

  if (!req.file?.path) {
    return errorResponse(res, 'Fichier audio manquant', 400);
  }

  if (!req.file.size || req.file.size <= 0) {
    try {
      if (req.file.path && fs.existsSync(req.file.path)) {
        fs.unlinkSync(req.file.path);
      }
    } catch (_) {}
    return errorResponse(res, 'Fichier audio vide (0 octet) reçu', 400);
  }

  const savedFilePath = path.resolve(req.file.path);
  const clientDurationRaw = Number(req.body?.durationSeconds);
  const fallbackDuration = Number.isFinite(meeting.duration)
    ? Math.max(1, Math.round(Number(meeting.duration) * 60))
    : null;
  const normalizedDuration = Number.isFinite(clientDurationRaw) && clientDurationRaw > 0
    ? Math.round(clientDurationRaw)
    : fallbackDuration;

  await MeetingRecording.findOneAndUpdate(
    { meetingId: meeting._id },
    {
      $set: {
        source: 'physical_upload',
        recordingLocalPath: savedFilePath,
        recordingStatus: 'available',
        recordingDownloadStatus: 'completed',
        recordingStoppedAt: new Date(),
        recordingDuration: normalizedDuration
      }
    },
    { upsert: true, new: true, setDefaultsOnInsert: true }
  );

  const notes = (meeting.notes || []).map(n => ({
    userId: n.userId ? n.userId.toString() : 'unknown',
    userName: n.userName || 'Participant',
    content: n.content,
    timestamp: n.timestamp || null
  }));

  await aiService.triggerPipeline(
    meeting._id.toString(),
    savedFilePath,
    notes,
    {
      title: meeting.title,
      description: meeting.description || '',
      participants: meeting.participants || [],
      duration: meeting.duration,
      meetingType: meeting.meetingType,
      location: meeting.location || null,
      createdBy: extractId(meeting.createdBy)
    }
  );

  await Meeting.updateOne({ _id: meeting._id }, { $set: { aiStatus: 'processing' } });

  wsManager.broadcastToMeeting(meeting._id.toString(), {
    event: 'recording_available',
    meetingId: meeting._id.toString(),
    source: 'physical_upload'
  });

  try {
    await notificationService.notifyRecordingReady(meeting);
  } catch (notifErr) {
    errorLog('[uploadPhysicalRecording] Erreur notification recording:', notifErr.message);
  }

  return successResponse(res, {
    success: true,
    recordingPath: savedFilePath
  }, 'Recording présentiel uploadé');
});

module.exports = {
  stopRecording,
  getRecordingStatus,
  uploadPhysicalRecording
};