const mongoose = require('mongoose');

const notificationSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
  type: {
    type: String, required: true,
    enum: [
      'meeting_created',
      'meeting_starting',
      'meeting_cancelled',
      'meeting_updated',
      'recording_ready',
      'recording_started',
      'ai_summary_ready',
      'task_assigned',
      'friend_request',
      'friend_accepted',
      'friend_rejected',
      'admin_broadcast',
    ]
  },
  title: { type: String, required: true },
  message: { type: String, required: true },
  data: {
    taskId: { type: String, default: null },
    meetingId: { type: String, default: null },
    meetingTitle: { type: String, default: null },
    startTime: { type: Date, default: null },
    actionUrl: { type: String, default: null },
    organizerName: { type: String, default: null },
    fromUserId: { type: String, default: null },
    fromUserName: { type: String, default: null }
  },
  isRead: { type: Boolean, default: false, index: true },
  readAt: { type: Date },
  isDelivered: { type: Boolean, default: false },
  deliveredAt: { type: Date },
  expiresAt: {
    type: Date,
    default: () => new Date(Date.now() + 7 * 24 * 60 * 60 * 1000),
    index: { expireAfterSeconds: 0 }
  },
}, {
  timestamps: true,
  toJSON: {
    transform: function(doc, ret) {
      ret.id = ret._id.toString();
      delete ret._id;
      delete ret.__v;
      return ret;
    }
  }
});
notificationSchema.index({ userId: 1, isRead: 1, createdAt: -1 });

module.exports = mongoose.model('Notification', notificationSchema);