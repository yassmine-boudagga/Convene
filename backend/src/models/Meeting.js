const mongoose = require('mongoose');
const { extractId } = require('../utils/idHelpers');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const noteSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  content: {
    type: String,
    required: [true, 'Note content is required'],
    maxlength: [5000, 'Note cannot exceed 5000 characters']
  },
  timestamp: {
    type: Date,
    default: Date.now
  }
}, {
  _id: true,
  toJSON: {
    transform: function (doc, ret) {
      ret.id = ret._id.toString();
      delete ret._id;
      return ret;
    }
  }
});

const joinedParticipantSchema = new mongoose.Schema({
  userId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  role: {
    type: String,
    enum: ['host', 'guest'],
    default: 'guest'
  },
  joinedAt: {
    type: Date,
    default: Date.now
  },
  lastSeen: {
    type: Date,
    default: Date.now
  },
}, {
  _id: false
});

const meetingSchema = new mongoose.Schema({
  title: {
    type: String,
    required: [true, 'Meeting title is required'],
    trim: true,
    maxlength: [200, 'Title cannot exceed 200 characters']
  },
  description: {
    type: String,
    trim: true,
    maxlength: [2000, 'Description cannot exceed 2000 characters'],
    default: ''
  },
  startTime: {
    type: Date,
    required: [true, 'Meeting start time is required'],
    index: true
  },
  duration: {
    type: Number,
    required: [true, 'Meeting duration is required'],
    min: [1, 'Duration must be at least 1 minute'],
    max: [480, 'Duration cannot exceed 480 minutes (8 hours)']
  },
  meetingType: {
    type: String,
    enum: ['online', 'physical'],
    default: 'online',
    index: true
  },
  location: {
    type: String,
    trim: true,
    maxlength: [300, 'Location cannot exceed 300 characters'],
    default: null
  },
  aiStatus: {
    type: String,
    enum: ['not_started', 'processing', 'completed', 'completed_empty', 'failed'],
    default: 'not_started',
    index: true
  },
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true
  },
  participants: [{
    type: String,
    lowercase: true,
    trim: true,
    match: [/^\w+([.-]?\w+)*@\w+([.-]?\w+)*(\.\w{2,3})+$/, 'Please provide valid participant emails']
  }],
  roomId: {
    type: String,
    default: null,
    description: 'LiveKit room ID for video conferencing'
  },
  status: {
    type: String,
    enum: ['scheduled', 'ongoing', 'finished', 'cancelled', 'archived'],
    default: 'scheduled',
    index: true
  },

  joinedParticipants: [joinedParticipantSchema],
  attendedBy: [{
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User'
  }],
  notes: [noteSchema],
  reminderSent: {
    type: Boolean,
    default: false
  },
  realMeetingStarted: {
    type: Boolean,
    default: false,
    index: true
  },
}, {
  timestamps: true,
  toJSON: {
    virtuals: true,
    transform: function (doc, ret) {
      ret.id = ret._id.toString();
      delete ret._id;
      delete ret.__v;
      return ret;
    }
  }
});

meetingSchema.index({ createdBy: 1, status: 1 });
meetingSchema.index({ startTime: 1, status: 1 });
meetingSchema.index({ roomId: 1 });
meetingSchema.index({ meetingType: 1, status: 1, startTime: 1 });


meetingSchema.statics.cleanupOldMeetings = async function () {
  const oneMonthAgo = new Date();
  oneMonthAgo.setMonth(oneMonthAgo.getMonth() - 1);

  const result = await this.updateMany(
    {
      status: 'finished',
      updatedAt: { $lte: oneMonthAgo }
    },
    { $set: { status: 'archived' } }
  );

  debugLog(`[cleanupOldMeetings] ${result.modifiedCount} réunion(s) archivée(s)`);
  return { modifiedCount: result.modifiedCount };
};


meetingSchema.methods.isCreator = function (userId) {
  const creatorId = extractId(this.createdBy);
  return creatorId === userId.toString();
};

meetingSchema.methods.getUserRole = function (userId, userEmail) {
  if (this.isCreator(userId)) { return 'host';}
  if (userEmail && this.participants.includes(userEmail.toLowerCase())) { return 'guest';}
  return null;
};

meetingSchema.methods.hasAccess = function (userId, userEmail) {
  if (this.isCreator(userId)) return true;
  if (userEmail && this.participants.includes(userEmail.toLowerCase())) return true;

  const hasJoined = this.joinedParticipants?.some(p => {
    return extractId(p.userId) === userId.toString();
  });
  if (hasJoined) return true;
  return false;
};

meetingSchema.methods.getActionPermissions = function (userId, userEmail) {
  const role = this.getUserRole(userId, userEmail);
  const permissions = {
    canJoin: false,
    canEdit: false,
    canCancel: false
  };
  if (!role) {
    return permissions;
  }

  const now = new Date();
  const fiveMinutesBeforeStart = new Date(this.startTime.getTime() - 5 * 60 * 1000);
  const meetingEnd = new Date(this.startTime.getTime() + this.duration * 60 * 1000);

  const canJoinTime = now >= fiveMinutesBeforeStart && now <= meetingEnd && this.status !== 'finished';

  switch (this.status) {
    case 'scheduled':
      if (role === 'host') {
        permissions.canEdit = true;
        permissions.canCancel = true;
        permissions.canJoin = canJoinTime;
      } else if (role === 'guest') {
        permissions.canJoin = canJoinTime;
      }
      break;
    case 'ongoing':
      permissions.canJoin = true;
      break;
    case 'finished':
      break;
  }
  return permissions;
};

// Pre-save: normalize participants, location
meetingSchema.pre('save', function (next) {
  if (this.meetingType === 'online') {
    this.location = null;
  }

  if (this.meetingType === 'physical' && (!this.location || !this.location.trim())) {
    return next(new Error('Location is required for physical meetings'));
  }

  if (this.participants && this.participants.length > 0) {
    this.participants = [...new Set(this.participants.map(p => p.toLowerCase()))];
  }
  next();
});
module.exports = mongoose.model('Meeting', meetingSchema);