const express = require('express');
const helmet = require('helmet');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');
const fs = require('fs');
const path = require('path');
const { S3Client, PutObjectCommand } = require('@aws-sdk/client-s3');

const authRoutes = require('./routes/authRoutes');
const meetingRoutes = require('./routes/meetingRoutes');
const notificationRoutes = require('./routes/notificationRoutes');
const taskRoutes = require('./routes/taskRoutes');
const userRoutes = require('./routes/userRoutes');

const { notFoundHandler, errorHandler } = require('./middleware/errorMiddleware');
const { authenticate } = require('./middleware/authMiddleware');
const Meeting = require('./models/Meeting');
const MeetingRecording = require('./models/MeetingRecording');

const app = express();
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

app.get('/favicon.ico', (req, res) => res.status(204).end());

// statique du dashboard AVANT rate limiter pour ne pas throttler les assets
app.use('/admin', express.static(path.join(__dirname, '../public/admin')));

//TRUST PROXY
app.set('trust proxy', 1);
app.use(helmet());// Security middleware
// Rate limiting
const limiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 300,
  message: {
    success: false,
    message: 'Too many requests from this IP, please try again later'
  }
});
app.use('/api/', limiter);

const recordingUploadLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 500,
  message: { success: false, message: 'Too many recording uploads' }
});
app.use('/recording-upload', recordingUploadLimiter);



// Upload endpoint pour les enregistrements LiveKit
app.post('/recording-upload', (req, res) => {
  try {
    return res.status(405).json({ success: false, message: 'Use PUT /recording-upload/:bucket/:filename for S3-compatible uploads' });
  } catch (error) {
    return res.status(500).json({ success: false, message: error.message });
  }
});

app.head('/recording-upload/:bucket/:filename(*)', (req, res) => {
  try {
    debugLog(`[RecordingUpload] HEAD check: bucket=${req.params.bucket}, key=${req.params.filename}`);
    return res.status(200).end();
  } catch (error) {
    errorLog(`[RecordingUpload] Erreur HEAD: ${error.message}`);
    return res.status(500).end();
  }
});

app.get('/recording-upload/:bucket', (req, res) => {
  try {
    const bucket = (req.params.bucket || '').trim();
    debugLog(`[RecordingUpload] GET bucket check: bucket=${bucket}`);

    const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">\n  <Name>${bucket}</Name>\n  <Prefix></Prefix>\n  <KeyCount>0</KeyCount>\n  <MaxKeys>1000</MaxKeys>\n  <IsTruncated>false</IsTruncated>\n</ListBucketResult>`;

    res.set('Content-Type', 'application/xml');
    return res.status(200).send(xml);
  } catch (error) {
    errorLog(`[RecordingUpload] Erreur bucket check: ${error.message}`);
    return res.status(500).json({ success: false, message: error.message });
  }
});

app.put('/recording-upload/:bucket/:filename(*)', async (req, res) => {
  const chunks = [];

  req.on('data', chunk => {
    chunks.push(chunk);
  });

  req.on('end', async () => {
    try {
      const body = Buffer.concat(chunks);
      if (!Buffer.isBuffer(body) || body.length === 0) {
        return res.status(400).json({ success: false, message: 'No binary file received' });
      }

      const bucketFromPath = (req.params.bucket || '').trim();
      const keyFromPath = (req.params.filename || '').trim();

      const bucket = process.env.S3_BUCKET || bucketFromPath || 'meetings';
      let key = keyFromPath;

      if (!key) {
        const roomName = (req.query.roomName || req.headers['x-room-name'] || 'unknown-room').toString();
        key = `recording-${roomName}-${Date.now()}.ogg`;
      }

      debugLog(`[RecordingUpload] Reçu: method=${req.method}, bucket=${bucket}, key=${key}, bytes=${body.length}, encoding=${req.headers['content-encoding'] || 'none'}`);

      const minioClient = new S3Client({
        endpoint: process.env.S3_ENDPOINT,
        region: process.env.S3_REGION,
        credentials: {
          accessKeyId: process.env.S3_ACCESS_KEY,
          secretAccessKey: process.env.S3_SECRET_KEY,
        },
        forcePathStyle: true,
      });

      const upload = new PutObjectCommand({
        Bucket: bucket,
        Key: key,
        Body: body,
        ContentType: req.headers['content-type'] || 'application/octet-stream',
      });

      await minioClient.send(upload);

      debugLog(`[RecordingUpload] Stocké dans MinIO: ${process.env.S3_ENDPOINT}/${bucket}/${key}`);
      return res.status(200).json({ success: true, filename: key });
    } catch (error) {
      errorLog(`[RecordingUpload] Erreur stockage MinIO: ${error.message}`);
      return res.status(500).json({ success: false, message: error.message });
    }
  });

  req.on('error', err => {
    errorLog(`[RecordingUpload] Erreur stream brut: ${err.message}`);
    return res.status(500).json({ success: false, message: err.message });
  });
});

const jsonParser = express.json({ limit: '10mb' });
const urlencodedParser = express.urlencoded({ extended: true, limit: '10mb' });

function shouldBypassBodyParser(pathname) {
  if (!pathname) return false;
  return /^\/api\/meetings\/[^/]+\/recording\/upload$/.test(pathname)
    || pathname === '/api/users/me/avatar';
}

app.use((req, res, next) => {
  if (shouldBypassBodyParser(req.path)) {
    return next();
  }
  return jsonParser(req, res, next);
});

app.use((req, res, next) => {
  if (shouldBypassBodyParser(req.path)) {
    return next();
  }
  return urlencodedParser(req, res, next);
});

if (process.env.NODE_ENV !== 'production') {
  app.use(morgan('dev'));
} else {
  app.use(morgan('combined'));
}

app.get('/health', (req, res) => {
  res.status(200).json({
    success: true,
    message: 'Server is running',
    timestamp: new Date().toISOString()
  });
});

app.use('/uploads', express.static(path.join(__dirname, '../uploads')));
app.get('/recordings/physical/:filename', authenticate, async (req, res) => {
  try {
    const filename = req.params.filename;

    if (!filename || filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
      return res.status(400).json({ success: false, message: 'Nom de fichier invalide' });
    }

    const recording = await MeetingRecording.findOne({
      recordingLocalPath: { $regex: filename.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') }
    });

    if (!recording) {
      return res.status(404).json({ success: false, message: 'Recording introuvable' });
    }

    const meeting = await Meeting.findById(recording.meetingId);
    if (!meeting) {
      return res.status(404).json({ success: false, message: 'Réunion introuvable' });
    }

    const userEmail = req.user.email.toLowerCase();
    const userId = req.userId;

    if (!meeting.hasAccess(userId, userEmail)) {
      return res.status(403).json({ success: false, message: 'Accès non autorisé' });
    }

    const filePath = path.join(__dirname, '../recordings', 'physical', filename);

    if (!fs.existsSync(filePath)) {
      return res.status(404).json({ success: false, message: 'Fichier introuvable sur le serveur' });
    }

    const stat = fs.statSync(filePath);
    const ext = path.extname(filename).toLowerCase();
    const mimeMap = {
      '.ogg': 'audio/ogg',
      '.mp4': 'video/mp4',
      '.webm': 'video/webm',
      '.m4a': 'audio/mp4',
      '.mp3': 'audio/mpeg'
    };
    const contentType = mimeMap[ext] || 'application/octet-stream';

    const rangeHeader = req.headers.range;
    const start = rangeHeader ? parseInt(rangeHeader.replace(/bytes=/, '').split('-')[0], 10) : 0;
    const rawEnd = rangeHeader ? rangeHeader.replace(/bytes=/, '').split('-')[1] : null;
    const end = rawEnd ? parseInt(rawEnd, 10) : stat.size - 1;
    const chunkSize = end - start + 1;

    res.writeHead(206, {
      'Content-Range': `bytes ${start}-${end}/${stat.size}`,
      'Accept-Ranges': 'bytes',
      'Content-Length': chunkSize,
      'Content-Type': contentType
    });

    fs.createReadStream(filePath, { start, end }).pipe(res);
  } catch (err) {
    errorLog('[recordings/physical] Erreur:', err.message);
    return res.status(500).json({ success: false, message: err.message });
  }
});

// ONLINE RECORDING STREAMING 
app.get('/recordings/online/:filename', authenticate, async (req, res) => {
  try {
    const filename = req.params.filename;

    // Security: prevent path traversal
    if (filename.includes('..') || filename.includes('/') || filename.includes('\\')) {
      return res.status(400).json({ success: false, message: 'Invalid filename' });
    }

    // Vérifier que l'utilisateur a accès à ce recording
    const recordingDoc = await MeetingRecording.findOne({
      recordingLocalPath: { $regex: filename.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') }
    });
    if (!recordingDoc) {
      return res.status(404).json({ success: false, message: 'Recording introuvable' });
    }
    const meetingDoc = await Meeting.findById(recordingDoc.meetingId);
    if (!meetingDoc || !meetingDoc.hasAccess(req.userId, req.user.email.toLowerCase())) {
      return res.status(403).json({ success: false, message: 'Accès non autorisé' });
    }

    const filePath = path.join(__dirname, '../recordings', filename);

    if (!fs.existsSync(filePath)) {
      return res.status(404).json({ success: false, message: 'Recording not found' });
    }

    const stat = fs.statSync(filePath);
    const fileSize = stat.size;
    const ext = path.extname(filename).toLowerCase();

    const mimeMap = {
      '.ogg': 'audio/ogg',
      '.mp4': 'video/mp4',
      '.webm': 'video/webm',
      '.m4a': 'audio/mp4',
      '.mp3': 'audio/mpeg'
    };
    const contentType = mimeMap[ext] || 'application/octet-stream';

    const rangeHeader = req.headers.range;

    let start, end;

    if (rangeHeader) {
      const parts = rangeHeader.replace(/bytes=/, '').split('-');
      start = parseInt(parts[0], 10);
      end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;
    } else {
      start = 0;
      end = fileSize - 1;
    }

    // Clamp to valid bounds
    start = Math.max(0, start);
    end = Math.min(fileSize - 1, end);
    const chunkSize = end - start + 1;

    res.writeHead(206, {
      'Content-Range': `bytes ${start}-${end}/${fileSize}`,
      'Accept-Ranges': 'bytes',
      'Content-Length': chunkSize,
      'Content-Type': contentType,
    });

    fs.createReadStream(filePath, { start, end }).pipe(res);
  } catch (err) {
    errorLog('[recordings/online] Erreur:', err.message);
    return res.status(500).json({ success: false, message: err.message });
  }
});

app.use('/api/auth', authRoutes);
app.use('/api/meetings', meetingRoutes);
app.use('/api/notifications', notificationRoutes);
app.use('/api/tasks', taskRoutes);
app.use('/api/users', userRoutes);

app.get('/', (req, res) => {
  res.status(200).json({
    success: true,
    message: 'Convene API',
    version: '1.0.0'
  });
});

const adminRoutes = require('./admin/adminRoutes');
app.use('/api/admin', adminRoutes);

app.use(notFoundHandler);
app.use(errorHandler);

module.exports = app;