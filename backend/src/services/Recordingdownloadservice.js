// Recording Download Service
const { S3Client, GetObjectCommand } = require('@aws-sdk/client-s3');
const fs = require('fs');
const path = require('path');
const MeetingRecording = require('../models/MeetingRecording'); 
const RECORDINGS_DIR = path.join(__dirname, '../../recordings');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

if (!fs.existsSync(RECORDINGS_DIR)) {
  fs.mkdirSync(RECORDINGS_DIR, { recursive: true });
  debugLog(`[RecordingDownload] Dossier créé: ${RECORDINGS_DIR}`);
}
// Télécharge un recording depuis une URL
async function downloadRecording(presignedUrl, meetingId) {
  if (!presignedUrl) {
    throw new Error('URL de recording manquante');
  }
  await MeetingRecording.findOneAndUpdate(
    { meetingId },
    { $set: { recordingDownloadStatus: 'downloading' } },
    { upsert: true, new: true }
  );

  const timestamp = Date.now();
  const filename = `meeting-${meetingId}-${timestamp}.ogg`;
  const filepath = path.join(RECORDINGS_DIR, filename);

  debugLog(`[RecordingDownload] Téléchargement: meetingId=${meetingId}, filename=${filename}`);
  debugLog(`[RecordingDownload] Destination: ${filepath}`);

  try {
    // Extract the S3 key from the presigned URL or reconstruct from meetingId
    const urlObj = new URL(presignedUrl);
    // pathParts: ['', 'meetings', 'recording-...ogg']
    const pathParts = urlObj.pathname.split('/').filter(Boolean);
    const bucket = pathParts[0]; // 'meetings'
    const key = pathParts.slice(1).join('/'); // 'recording-...-ts.ogg'

    const s3Client = new S3Client({
      endpoint: process.env.S3_ENDPOINT,
      region: process.env.S3_REGION,
      credentials: {
        accessKeyId: process.env.S3_ACCESS_KEY,
        secretAccessKey: process.env.S3_SECRET_KEY,
      },
      forcePathStyle: true,
    });

    const command = new GetObjectCommand({
      Bucket: bucket || process.env.S3_BUCKET ,
      Key: key,
    });

    const s3Response = await s3Client.send(command);

    const chunks = [];
    for await (const chunk of s3Response.Body) {
      chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
    }
    let buffer = Buffer.concat(chunks);

    // Strip aws-chunked encoding prefix if present.
    // MinIO stores LiveKit egress files with aws-chunked markers
    const oggSMarker = Buffer.from('4f676753', 'hex'); // 'OggS'
    const oggSIndex = buffer.indexOf(oggSMarker);
    if (oggSIndex > 0) {
      debugLog(`[RecordingDownload] Stripping ${oggSIndex} bytes of aws-chunked prefix before OggS`);
      buffer = buffer.slice(oggSIndex);
    }

    fs.writeFileSync(filepath, buffer);

    const stats = fs.statSync(filepath);
    const sizeMB = (stats.size / (1024 * 1024)).toFixed(2);
    debugLog(`[RecordingDownload] Téléchargé: ${filename} (${sizeMB} MB)`);

    await MeetingRecording.findOneAndUpdate(
      { meetingId },
      {
        $set: {
          recordingLocalPath: filepath,
          recordingDownloadStatus: 'completed',
          recordingStatus: 'available'
        }
      },
      { upsert: true, new: true }
    );

    return filepath;

  } catch (error) {
    errorLog('[RecordingDownload] Erreur:', error.message);

    if (fs.existsSync(filepath)) {
      fs.unlinkSync(filepath);
    }
    errorLog(`[RecordingDownload] Download failed for meetingId=${meetingId}: ${error.message}`);
    await MeetingRecording.findOneAndUpdate(
      { meetingId },
      {
        $set: {
          recordingDownloadStatus: 'failed',
          recordingStatus: 'failed'
        }
      },
      { upsert: true, new: true }
    );

    throw error;
  }
}

module.exports = {
  downloadRecording,
  RECORDINGS_DIR
};