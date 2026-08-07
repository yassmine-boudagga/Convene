const mongoose = require('mongoose');

const actionItemSchema = new mongoose.Schema({
  text: {
    type: String,
    required: true,
    trim: true,
    maxlength: [1000, 'Action item cannot exceed 1000 characters']
  },
  ownerHint: {
    type: String,
    trim: true,
    maxlength: [200, 'Owner hint cannot exceed 200 characters'],
    default: null
  },
  dueDateHint: {
    type: String,
    trim: true,
    maxlength: [100, 'Due date hint cannot exceed 100 characters'],
    default: null
  }
}, { _id: false });

const meetingAIResultSchema = new mongoose.Schema({
  meetingId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Meeting',
    required: true,
    unique: true,
  },
  transcript: {
    rawText: {
      type: String,
      default: null
    },
    language: {
      type: String,
      default: null
    },
    durationSeconds: {
      type: Number,
      default: null
    }
  },
  summary: {
    keyPoints: [{
      type: String,
      trim: true,
      maxlength: [1000, 'Key point cannot exceed 1000 characters']
    }],
    decisions: [{
      type: String,
      trim: true,
      maxlength: [1000, 'Decision cannot exceed 1000 characters']
    }],
    actionItems: [actionItemSchema]
  },
  pipelineStatus: {
    type: String,
    enum: ['completed', 'empty_audio', 'failed', 'transcription_failed'],
    default: 'completed'
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

module.exports = mongoose.model('MeetingAIResult', meetingAIResultSchema);