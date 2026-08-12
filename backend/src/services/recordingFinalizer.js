const { EgressClient } = require('livekit-server-sdk');
const { S3Client, GetObjectCommand } = require('@aws-sdk/client-s3');
const { getSignedUrl } = require('@aws-sdk/s3-request-presigner');
const MeetingRecording = require('../models/MeetingRecording');
const Meeting = require('../models/Meeting');
const wsManager = require('./wsManager');
const notificationService = require('./notificationService');
const aiService = require('./aiService');
const { downloadRecording } = require('./Recordingdownloadservice');

const EGRESS_COMPLETE = 3;
const EGRESS_ABORTED = 5;
const POLL_INTERVAL_MS = 3000;
const MAX_ATTEMPTS = 10;
const inFlightFinalizations = new Set();
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

function getLivekitUrl() {
  const url = process.env.LIVEKIT_URL;
  if (!url) throw new Error('[Finalizer] LIVEKIT_URL manquante');
  return url.replace('wss://', 'https://').replace('ws://', 'http://');
}

function getEgressClient() {
  return new EgressClient(
    getLivekitUrl(),
    process.env.LIVEKIT_API_KEY,
    process.env.LIVEKIT_API_SECRET
  );
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function pollCompletedEgress(egressId, meetingId) {
  const egressClient = getEgressClient();
  const roomName = `meeting-${meetingId}`;
  let lastSeen = null;
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt += 1) {
    try {
      const egresses = await egressClient.listEgress({ roomName });
      const egressInfo = (egresses || []).find((e) => e.egressId === egressId);
      lastSeen = egressInfo || lastSeen;

      if (!egressInfo) {
        debugLog(`[Finalizer] Tentative ${attempt}/${MAX_ATTEMPTS}: egressId=${egressId} introuvable`);
      } else {
        debugLog(`[Finalizer] Tentative ${attempt}/${MAX_ATTEMPTS}: egressId=${egressId}, status=${egressInfo.status}`);
        if (egressInfo.status === EGRESS_COMPLETE || egressInfo.status === EGRESS_ABORTED) {
          return egressInfo;
        }
      }
    } catch (listErr) {
      errorLog(`[Finalizer] Tentative ${attempt}/${MAX_ATTEMPTS} listEgress erreur: ${listErr.message}`);
    }
    if (attempt < MAX_ATTEMPTS) {
      await sleep(POLL_INTERVAL_MS);
    }
  }
  errorLog(
    `[Finalizer] Egress non finalise apres ${MAX_ATTEMPTS} tentatives: egressId=${egressId}, status=${lastSeen ? lastSeen.status : 'not_found'}`
  );
  return null;
}

async function finalizeEgress(egressId, meetingId) {
  if (!egressId || !meetingId) {
    warnLog(`[Finalizer] finalizeEgress ignore: egressId=${egressId}, meetingId=${meetingId}`);
    return;
  }
  if (inFlightFinalizations.has(egressId)) {
    debugLog(`[Finalizer] Skip: egressId=${egressId} deja en cours de finalisation`);
    return;
  }
  inFlightFinalizations.add(egressId);
  try {
    const existing = await MeetingRecording.findOne({ recordingId: egressId });
    if (!existing || ['available', 'url_only', 'downloading'].includes(existing.recordingStatus)) {
      debugLog(`[Finalizer] Skip: egressId=${egressId} deja en statut ${existing?.recordingStatus}`);
      return;
    }
    const egressInfo = await pollCompletedEgress(egressId, meetingId);
    if (!egressInfo) {
      return;
    }
    const recQuery = {
      $or: [{ meetingId }, { recordingId: egressId }]
    };

    // cas ABORTED (status 5): dû à une absence de tracks audio
    if (egressInfo.status !== EGRESS_COMPLETE) {
      warnLog(`[Finalizer] Egress arrêté prématurément (status=${egressInfo.status}): egressId=${egressId}`);
      errorLog(`[Finalizer] Egress aborted (no_audio_tracks): egressId=${egressId}, meetingId=${meetingId}`);
      await MeetingRecording.findOneAndUpdate(
        recQuery,
        { $set: { recordingStatus: 'failed' } },
        { upsert: true, new: true, setDefaultsOnInsert: true }
      );
      // completed_empty pour l'IA
      await Meeting.findByIdAndUpdate(meetingId, { $set: { aiStatus: 'completed_empty' } });
      await wsManager.broadcastToMeeting(meetingId.toString(), {
        event: 'ai_summary_empty',
        meetingId: meetingId.toString(),
        reason: 'no_audio_tracks',
        timestamp: new Date().toISOString()
      });
      return;
    }
    if (egressInfo.error) {
      errorLog(`[Finalizer] Egress echoue: ${egressInfo.error}`);
      errorLog(`[Finalizer] Egress error: egressId=${egressId}, error=${egressInfo.error}`);
      await MeetingRecording.findOneAndUpdate(
        recQuery,
        { $set: { recordingStatus: 'failed' } },
        { upsert: true, new: true, setDefaultsOnInsert: true }
      );
      return;
    }

    // fileResults n'est pas disponible via listEgress dans LiveKit Cloud.
    // On lit le filename depuis MeetingRecording ou il a ete stocke lors du startRecording.
    const recDoc = await MeetingRecording.findOne({
      $or: [{ meetingId }, { recordingId: egressId }]
    });
    let filename = recDoc && recDoc.recordingFilename ? recDoc.recordingFilename : null;
    const fileResult = (egressInfo.fileResults && egressInfo.fileResults[0])
      || (egressInfo.file_results && egressInfo.file_results[0])
      || null;
    const streamResult = (egressInfo.stream_results && egressInfo.stream_results[0])
      || (egressInfo.streamResults && egressInfo.streamResults[0])
      || null;

    if (!filename) {
      filename = (fileResult && fileResult.filename)
        || (streamResult && streamResult.filename)
        || null;

      if (filename) {
        await MeetingRecording.findOneAndUpdate(
          recQuery,
          { $set: { recordingFilename: filename } },
          { upsert: true, new: true, setDefaultsOnInsert: true }
        );
      }
    }

    const duration = (egressInfo.endedAt && egressInfo.startedAt)
      ? Math.round((Number(egressInfo.endedAt) - Number(egressInfo.startedAt)) / 1_000_000_000)
      : 0;

    if (!filename) {
      errorLog(`[Finalizer] egressId=${egressId}: filename manquant`);
      errorLog(`[Finalizer] No filename available for egressId=${egressId} — recordingFilename, fileResults and stream_results all missing`);
      await MeetingRecording.findOneAndUpdate(
        recQuery,
        { $set: { recordingStatus: 'failed' } },
        { upsert: true, new: true, setDefaultsOnInsert: true }
      );
      return;
    }

    const s3Endpoint = process.env.S3_ENDPOINT;
    const s3Bucket = process.env.S3_BUCKET;
    const minioUrl = `${s3Endpoint}/${s3Bucket}/${filename}`;

    let downloadUrl = minioUrl;
    try {
      const s3Client = new S3Client({
        endpoint: s3Endpoint,
        region: process.env.S3_REGION,
        credentials: {
          accessKeyId: process.env.S3_ACCESS_KEY,
          secretAccessKey: process.env.S3_SECRET_KEY,
        },
        forcePathStyle: true,
      });
      const command = new GetObjectCommand({ Bucket: s3Bucket, Key: filename });
      downloadUrl = await getSignedUrl(s3Client, command, { expiresIn: 3600 });
      debugLog(`[Finalizer] Download URL préparée pour meetingId=${meetingId}, filename=${filename}`);
    } catch (presignErr) {
      errorLog(`[Finalizer] Presign echoue pour egressId=${egressId}: ${presignErr.message}`);
      warnLog('[Finalizer] Fallback vers URL directe MinIO');
    }

    await MeetingRecording.findOneAndUpdate(
      recQuery,
      {
        $set: {
          source: 'livekit',
          recordingId: egressId,
          recordingUrl: minioUrl,
          recordingDuration: duration,
          recordingStatus: 'downloading',
          recordingDownloadStatus: 'pending',
          recordingStoppedAt: new Date()
        }
      },
      { upsert: true, new: true, setDefaultsOnInsert: true }
    );

    await wsManager.broadcastToMeeting(meetingId.toString(), {
      event: 'recording_stopped',
      meetingId: meetingId.toString(),
      egressId
    });

    setImmediate(async () => {
      try {
        const localPath = await downloadRecording(downloadUrl, meetingId);
        const localUrl = `/api/meetings/${meetingId}/recording/file`;

        await MeetingRecording.findOneAndUpdate(
          recQuery,
          {
            $set: {
              recordingLocalPath: localPath,
              recordingDownloadStatus: 'completed',
              recordingStatus: 'available',
              recordingUrl: localUrl
            }
          },
          { upsert: true, new: true, setDefaultsOnInsert: true }
        );

        const meeting = await Meeting.findById(meetingId);
        if (meeting) {
          await notificationService.notifyRecordingReady(meeting);

          if (!localPath) {
            errorLog(`[Finalizer] Trigger IA annule pour ${meetingId}: recordingLocalPath manquant`);
            return;
          }

          try {
            const notes = (meeting.notes || []).map((n) => ({
              userId: n.userId ? n.userId.toString() : 'unknown',
              userName: n.userName || 'Participant',
              content: n.content,
              timestamp: n.timestamp || null
            }));

            await aiService.triggerPipeline(
              meetingId,
              localPath,
              notes,
              {
                title: meeting.title,
                description: meeting.description || '',
                participants: meeting.participants || [],
                duration: meeting.duration,
                meetingType: meeting.meetingType
              }
            );
            debugLog(`[Finalizer] Trigger IA envoye pour ${meetingId}`);
          } catch (aiErr) {
            errorLog(`[Finalizer] Trigger IA echoue pour ${meetingId}: ${aiErr.message}`);
          }
        }
      } catch (dlErr) {
        errorLog(`[Finalizer] Telechargement echoue: ${dlErr.message}`);
        errorLog(`[Finalizer] Download failed for meetingId=${meetingId}: ${dlErr.message}`);
        await MeetingRecording.findOneAndUpdate(
          recQuery,
          {
            $set: {
              recordingDownloadStatus: 'failed',
              recordingStatus: 'url_only'
            }
          },
          { upsert: true, new: true, setDefaultsOnInsert: true }
        );
      }
    });
  } catch (err) {
    errorLog(`[Finalizer] finalizeEgress erreur: ${err.message}`);
  } finally {
    inFlightFinalizations.delete(egressId);
  }
}
module.exports = { finalizeEgress };