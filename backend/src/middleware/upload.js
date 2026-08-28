const multer = require('multer');
const path = require('path');
const fs = require('fs');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const uploadDir = path.join(__dirname, '../../uploads/avatars');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir, { recursive: true });
}

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    debugLog('[Multer] destination called, file:', file?.originalname, 'mimetype:', file?.mimetype);
    cb(null, uploadDir);
  },
  filename: (req, file, cb) => {
    debugLog('[Multer] filename called for userId:', req.userId);
    const ext = path.extname(file.originalname);
    cb(null, `avatar_${req.userId}_${Date.now()}${ext}`);
  }
});

const fileFilter = (req, file, cb) => {
  debugLog('[Multer] fileFilter mimetype:', file?.mimetype, 'originalname:', file?.originalname);
  const allowed = ['image/jpeg', 'image/png', 'image/webp'];
  const accepted = allowed.includes(file.mimetype) || String(file?.mimetype || '').startsWith('image/');
  cb(null, accepted);
};

module.exports = multer({
  storage,
  fileFilter,
  limits: { fileSize: 5 * 1024 * 1024 }
});
