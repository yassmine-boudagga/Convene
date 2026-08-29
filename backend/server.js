require('dotenv').config();

const http = require('http');
const app = require('./src/app');
const connectDB = require('./src/config/db');
const Meeting = require('./src/models/Meeting');
const MeetingRecording = require('./src/models/MeetingRecording');
const User = require('./src/models/User');
const notificationService = require('./src/services/notificationService');
const wsManager = require('./src/services/wsManager');
const livekitService = require('./src/services/Livekitservice');
const { archiveOldCompletedTasks } = require('./src/controllers/taskController');
const cron = require('node-cron');
const { extractId, extractJoinedUserIds } = require('./src/utils/idHelpers');
const PORT = process.env.PORT || 3000;
const logger = console;
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

const {
  checkPhysicalRecordingConditions
} = require('./src/controllers/meetingController');

async function broadcastMeetingStatusChanged(meeting) {
  const meetingId = meeting._id.toString();
  const payload = {
    event: 'meeting_status_changed',
    meetingId,
    status: meeting.status
  };
  await wsManager.broadcastToMeeting(meetingId, payload);

  const participantEmails = (meeting.participants || [])
    .map((email) => String(email || '').trim().toLowerCase())
    .filter(Boolean);

  const invitedUsers = participantEmails.length > 0
    ? await User.find({ email: { $in: participantEmails } }).select('_id')
    : [];

  const creatorId = extractId(meeting.createdBy);

  const targetUserIds = [...new Set([
    creatorId,
    ...invitedUsers.map((u) => u._id.toString())
  ].filter(Boolean))];

  targetUserIds.forEach((uid) => {
    wsManager.sendToUser(uid, payload);
  });
}
// CRON A : scheduled → ongoing + création room LiveKit
const cronScheduledToOngoing = () => {
  cron.schedule('*/30 * * * * *', async () => {
    try {
      const now = new Date();
      const in5min = new Date(now.getTime() + 5 * 60 * 1000);
      const meetings = await Meeting.find({
        status: 'scheduled',
        startTime: { $gte: now, $lte: in5min } 
      });

      for (const m of meetings) {
        try {
          m.status = 'ongoing';
          if (m.meetingType === 'online' && !m.roomId) {
            const room = await livekitService.createRoom(m._id.toString(), m.title);
            m.roomId = room.id;
          } else if (m.meetingType === 'online') {
            const exists = await livekitService.roomExists(m.roomId);
            if (!exists) {
              const room = await livekitService.createRoom(m._id.toString(), m.title);
              m.roomId = room.id;
            }
          }
          await m.save();
          await broadcastMeetingStatusChanged(m);
          debugLog(`[Cron A] ${m._id} scheduled → ongoing (startTime=${m.startTime})`);
        } catch (mErr) {
          errorLog(`[Cron A] Erreur pour meeting ${m._id}:`, mErr.message);
        }
      }
    } catch (err) { errorLog('[Cron A] Erreur globale:', err.message); }
  });
};

// CRON B — realMeetingStarted=true à partir de startTime exacte
const cronSetRealMeetingStarted = () => {
  cron.schedule('*/15 * * * * *', async () => {
    try {
      const now = new Date();
      const result = await Meeting.updateMany(
        {
          status: 'ongoing',
          realMeetingStarted: false,
          startTime: { $lte: now }  
        },
        { $set: { realMeetingStarted: true } }
      );
      if (result.modifiedCount > 0) {
        debugLog(`[Cron B] realMeetingStarted=true pour ${result.modifiedCount} meeting(s)`);
      }

      // LOGIQUE PHYSICAL
      const physicalMeetings = await Meeting.find({
        status: 'ongoing',
        meetingType: 'physical',
        realMeetingStarted: true
      }).populate('joinedParticipants.userId', '_id name email')
        .populate('createdBy', '_id name email')
        .select('_id createdBy meetingType realMeetingStarted joinedParticipants');

      for (const m of physicalMeetings) {
        const meetingId = m._id.toString();
        const shouldStartRecording = checkPhysicalRecordingConditions(m);
        if (shouldStartRecording) {
          const existingRec = await MeetingRecording.findOne({
            meetingId: m._id,
            manuallyStoppedAt: { $ne: null }
          });
          if (existingRec) {
            debugLog(`[Cron B] Skip physical ${meetingId}: recording stoppé manuellement`);
            continue;
          }
          try {
            const freshPhysical = await Meeting.findById(m._id)
              .select('status');
            if (!freshPhysical || freshPhysical.status !== 'ongoing') {
              debugLog(`[Cron B] Skip physical ${meetingId}: meeting no longer ongoing`);
              continue;
            }
            const atomicResult = await MeetingRecording.findOneAndUpdate(
              {
                meetingId: m._id
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
              debugLog(`[Cron B] recording_started → physical meeting ${meetingId}`);
            } else {
              debugLog(`[Cron B] Skip broadcast physical ${meetingId}: recording déjà actif (${atomicResult?.recordingStatus})`);
            }
          } catch (recErr) {
            errorLog(`[Cron B] Erreur atomique recording physical ${meetingId}: ${recErr.message}`);
          }
        }
      }

      // LOGIQUE Online 
      const meetings = await Meeting.find({
        status: 'ongoing',
        meetingType: 'online',
        realMeetingStarted: true,
      }).select('_id roomId realMeetingStarted joinedParticipants meetingType');

      for (const m of meetings) {
        const meetingId = m._id;
        const roomName = m.roomId;
        const existingRec = await MeetingRecording.findOne({
          meetingId,
          manuallyStoppedAt: { $ne: null }
        });
        if (existingRec) {
          debugLog(`[Cron B] Skip online ${meetingId}: recording stoppé manuellement`);
          continue;
        }

        const recordingDoc = await MeetingRecording.findOne({ meetingId });

        if (recordingDoc?.recordingId) {
          debugLog(`[Cron B] Skip ${meetingId}: recording déjà existant ${recordingDoc.recordingId} (status=${recordingDoc.recordingStatus})`);
          continue;
        }
        const hasHost = (m.joinedParticipants || []).some(
          p => p.role === 'host'
        );
        if (!hasHost) {
          debugLog(`[Cron B] Skip ${meetingId}: aucun host dans joinedParticipants`);
          continue;
        }
        const hasGuest = (m.joinedParticipants || []).some(
          p => p.role === 'guest'
        );
        if (!hasGuest) {
          debugLog(`[Cron B] Skip ${meetingId}: aucun guest dans joinedParticipants`);
          continue;
        }

        if (!roomName) {
          debugLog(`[Cron B] Skip ${meetingId}: roomName introuvable`);
          continue;
        }

        try {
          const freshMeeting = await Meeting.findById(m._id)
            .select('status roomId');
          if (!freshMeeting || freshMeeting.status !== 'ongoing') {
            debugLog(`[Cron B] Skip ${meetingId}: meeting no longer ongoing`);
            continue;
          }
          if (!freshMeeting.roomId) {
            debugLog(`[Cron B] Skip ${meetingId}: room no longer exists`);
            continue;
          }
          const resultStart = await livekitService.startRecording(roomName);

          await MeetingRecording.findOneAndUpdate(
            { meetingId },
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

          wsManager.broadcastToMeeting(meetingId.toString(), {
            event: 'recording_started',
            meetingId: meetingId.toString(),
            egressId: resultStart.egressId
          });

          debugLog(`[Cron B] Recording démarré pour ${meetingId}: ${resultStart.egressId}`);
        } catch (err) {
          errorLog(`[Cron B] Recording échoué pour ${meetingId}: ${err.message}`);
        }
      }
    } catch (err) { errorLog('[Cron B] Erreur:', err.message); }
  });
};

// CRON C — Auto-finish par durée max
const cronAutoFinishExpired = () => {
  cron.schedule('* * * * *', async () => {
    try {
      const now = new Date();
      // meetings scheduled dont startTime+duration est passée
      try { 
        const passedScheduled = await Meeting.find({  
          status: 'scheduled'  
        }).select('_id startTime duration');  
        for (const ms of passedScheduled) {  
          const endTimeMs = new Date(ms.startTime.getTime() + ms.duration * 60 * 1000);  
          if (now >= endTimeMs) {  
            await Meeting.updateOne(  
              { _id: ms._id, status: 'scheduled' },  
              { $set: { status: 'finished', joinedParticipants: [] } }  
            );  
            debugLog(`[Cron C] ${ms._id} scheduled → finished directement`);  
          }  
        }  
      } catch (passedErr) {  
        errorLog('[Cron C] Erreur traitement scheduled passés:', passedErr.message);  
      }  
      // meetings scheduled dont startTime+duration est passée
      const meetings = await Meeting.find({
        status: 'ongoing',
        realMeetingStarted: true
      }).select('_id startTime duration roomId meetingType joinedParticipants attendedBy');

      for (const m of meetings) {
        const endTime = new Date(m.startTime.getTime() + m.duration * 60 * 1000);
        if (now >= endTime) {
          const recordingDoc = await MeetingRecording.findOne({ meetingId: m._id });
          // Stopper le recording si actif : reunion en ligne 
          if (recordingDoc?.recordingId && recordingDoc.recordingStatus === 'recording') {
            try {
              const { stopRecording } = require('./src/services/Livekitservice');
              await stopRecording(recordingDoc.recordingId);
              wsManager.broadcastToMeeting(m._id.toString(), {
                event: 'recording_stopped',
                meetingId: m._id.toString()
              });
              debugLog('[Cron C] Recording arrêté: durée max atteinte');
            } catch (recErr) {
              errorLog(`[Cron C] Erreur arrêt recording: ${recErr.message}`);
            }
          }
           // Stopper le recording si actif : reunion presentielle
          if (m.meetingType === 'physical' && recordingDoc && recordingDoc.recordingStatus === 'recording'){
            try {
              await MeetingRecording.updateOne(
                {
                  meetingId: m._id,
                  recordingStatus: 'recording'
                },
                { 
                  $set: {
                    recordingStatus: 'processing',
                    recordingStoppedAt: new Date()
                  }
                }
              );
              wsManager.broadcastToMeeting(m._id.toString(), {
                event: 'recording_stopped',
                meetingId: m._id.toString()
              });
              debugLog('[Cron C] Recording physical marqué processing');

            } catch (recErr) {
              errorLog(`[Cron C] Erreur arrêt recording physical: ${recErr.message}`);
            }
          }
          const { finishMeetingProperly } = require('./src/controllers/meetingController');
          await finishMeetingProperly(m, 'auto_finish', wsManager);
          debugLog(`[Cron C] ${m._id} → finished (durée max atteinte: ${m.duration}min)`);
        }
      }      
    } catch (err) { errorLog('[Cron C] Erreur:', err.message); }
  });
};

// CRON RAPPELS:notifications 5min avant 
const scheduleMeetingReminders = () => {
  cron.schedule('* * * * *', async () => {
    try {
      const now = new Date();
      const in4min = new Date(now.getTime() + 4 * 60 * 1000);
      const in5min = new Date(now.getTime() + 5 * 60 * 1000);

      const meetings = await Meeting.find({
        startTime: { $gte: in4min, $lt: in5min },
        status: { $in: ['scheduled', 'ongoing'] },
        meetingType: { $in: ['online', 'physical'] },
        reminderSent: { $ne: true }
      }).populate('createdBy', 'name email');

      for (const m of meetings) {
        const lockedMeeting = await Meeting.findOneAndUpdate(
          { _id: m._id, reminderSent: { $ne: true } },
          { $set: { reminderSent: true } },
          { new: true }
        );

        if (!lockedMeeting) {
          continue;
        }

        await notificationService.notifyMeetingStartingSoon(lockedMeeting);
        debugLog(`[Cron Rappel] Rappel envoyé meetingId=${lockedMeeting._id}`);
      }
    } catch (err) { errorLog('[Cron Rappel] Erreur:', err.message); }
  });
};

// CRON PARTICIPANTS INACTIFS: nettoyage heartbeat (toutes les minutes)
const cleanupInactiveParticipants = () => {
  cron.schedule('* * * * *', async () => {
    try {
      const cutoff = new Date(Date.now() - 2 * 60 * 1000); //2 min sans heartbeat
      const meetings = await Meeting.find({
        status: 'ongoing',
        meetingType: { $in: ['online', 'physical'] },
        'joinedParticipants.0': { $exists: true }
      });

      for (const m of meetings) {
        const before = m.joinedParticipants.length;
        const previousJoinedUserIds = extractJoinedUserIds(m.joinedParticipants || []);
        m.joinedParticipants = m.joinedParticipants.filter(
          p => !p.lastSeen || p.lastSeen > cutoff
        );

        if (m.joinedParticipants.length !== before) {
          let forceEndByInactivity = false;

          if (m.meetingType === 'online' && m.realMeetingStarted) {
            try {
              const { checkAndStopRecording } = require('./src/services/Livekitservice');
              await checkAndStopRecording(m._id, 'CronInactifs');
            } catch (recErr) {
              errorLog(`[Cron Inactifs] checkAndStopRecording erreur: ${recErr.message}`);
            }
          }

          //finir la réunion si realMeetingStarted=true
          if (m.joinedParticipants.length === 0 && m.realMeetingStarted) {
            m.status = 'finished';
            m.attendedBy = [...new Set([
              ...(m.attendedBy || []).map((id) => id.toString()),
              ...previousJoinedUserIds
            ])];
            if (m.meetingType === 'physical') {
              forceEndByInactivity = true;
            }
            debugLog(`[Cron Inactifs] ${m._id} → finished (tous inactifs)`);
          } else if (m.joinedParticipants.length === 0) {
            debugLog(`[Cron Inactifs] ${m._id}: tous inactifs mais pré-réunion, status reste ongoing`);
          }
          await m.save();
          if (forceEndByInactivity) {
            const meetingId = m._id.toString();
            const payload = {
              event: 'meeting_force_end',
              meetingId,
              reason: 'inactivity_timeout'
            };

            wsManager.broadcastToMeeting(meetingId, payload);
            const participantIds = (m.attendedBy || [])
              .map((a) => (a?.userId ? a.userId.toString() : a?.toString?.()))
              .filter(Boolean);
            participantIds.forEach((uid) => {
              wsManager.sendToUser(uid, payload);
            });
          }
          debugLog(`[Cron Inactifs] Participants inactifs nettoyés: ${m._id}`);
        }
      }
    } catch (err) { errorLog('[Cron Inactifs] Erreur:', err.message); }
  });
};

// CRON TASKS:archivage des tâches completées > 7 jours
const cronArchiveCompletedTasks = () => {
  cron.schedule('0 0 * * *', async () => {
    try {
      const count = await archiveOldCompletedTasks();
      logger.info(`[Cron Tasks] ${count} tâches archivées automatiquement`);
    } catch (err) {
      logger.error('[Cron Tasks] Erreur archivage:', err.message);
    }
  });
};

// CRON meetings: archivage des réunions terminées
const cronArchiveOldFinishedMeetings = () => {
  cron.schedule('0 1 * * *', async () => {
    try {
      const result = await Meeting.cleanupOldMeetings();
      debugLog(`[Cron Archive Meetings] ${result.modifiedCount} réunion(s) archivée(s)`);
    } catch (err) {
      errorLog('[Cron Archive Meetings] Erreur:', err.message);
    }
  });
};

//Démarrage du serveur et connexion à la DB
const startServer = async () => {
  try {
    await connectDB();
    debugLog('[Database] MongoDB connecté');

    const server = http.createServer(app);
    wsManager.init(server);
    notificationService.setWebSocketManager(wsManager);

    cronScheduledToOngoing();
    cronSetRealMeetingStarted();
    cronAutoFinishExpired();
    scheduleMeetingReminders();
    cleanupInactiveParticipants();
    cronArchiveCompletedTasks();
    cronArchiveOldFinishedMeetings();

    server.listen(PORT, '0.0.0.0', () => {
      console.info(`
╔══════════════════════════════════════════════╗
║  Convene Backend — LiveKit + WebSocket       ║ 
╚══════════════════════════════════════════════╝`);
    });

  } catch (err) {
    errorLog('[Server] Échec démarrage:', err.message);
    process.exit(1);
  }
};
process.on('unhandledRejection', (err) => { errorLog('unhandledRejection:', err.message); process.exit(1); });
process.on('uncaughtException', (err) => { errorLog('uncaughtException:', err.message); process.exit(1); });
startServer();