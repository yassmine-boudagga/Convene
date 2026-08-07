const mongoose = require('mongoose');

const meetingRecordingSchema = new mongoose.Schema({
  meetingId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Meeting',
    required: true,
    unique: true,
  },
  source: {
    type: String,
    enum: ['livekit', 'physical_upload'],
    required: true,
    default: 'livekit',
    index: true
  },
  recordingId: {
    type: String,
    default: null,
    index: true
  },
  recordingFilename: {
    type: String,
    default: null
  },
  recordingStatus: {
    type: String,
    enum: ['recording', 'processing', 'downloading', 'available', 'url_only', 'failed', null],
    default: null,
    index: true
  },
  recordingStartedAt: {
    type: Date,
    default: null
  },
  recordingStoppedAt: {
    type: Date,
    default: null
  },
  manuallyStoppedAt: {
    type: Date,
    default: null
  },
  stoppedByHost: {
    type: Boolean,
    default: false
  },
  recordingUrl: {
    type: String,
    default: null
  },
  recordingDuration: {
    type: Number,
    default: null
  },
  recordingLocalPath: {
    type: String,
    default: null
  },
  recordingDownloadStatus: {
    type: String,
    enum: ['pending', 'downloading', 'completed', 'failed', null],
    default: null
  }
}, {
  timestamps: true,
  toJSON: {
    transform: function (doc, ret) {
      ret.id = ret._id;
      delete ret._id;
      delete ret.__v;
      return ret;
    }
  }
});

meetingRecordingSchema.index({ meetingId: 1, source: 1 });
meetingRecordingSchema.index({ source: 1, recordingStatus: 1 });

module.exports = mongoose.model('MeetingRecording', meetingRecordingSchema);