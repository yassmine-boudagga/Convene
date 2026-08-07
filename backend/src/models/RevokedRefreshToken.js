const mongoose = require('mongoose');

const revokedRefreshTokenSchema = new mongoose.Schema({
  tokenHash: {
    type: String,
    required: true,
    unique: true,
    index: true,
  },
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true,
  },
  reason: {
    type: String,
    enum: ['logout', 'rotated'],
    default: 'logout',
  },
  revokedAt: {
    type: Date,
    default: Date.now,
  },
  expiresAt: {
    type: Date,
    required: true,
    index: { expires: 0 },
  },
  newTokenHash: {
    type: String,
    default: null,
  },
  rotatedAt: {
    type: Date,
    default: null,
  },
}, {
  timestamps: true,
});

module.exports = mongoose.model('RevokedRefreshToken', revokedRefreshTokenSchema);
