const mongoose = require('mongoose');

const taskSchema = new mongoose.Schema({
  title: {
    type: String,
    required: [true, 'Task title is required'],
    trim: true,
    maxlength: [300, 'Task title cannot exceed 300 characters']
  },
  assigneeId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    default: null,
    index: true
  },
  meetingId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Meeting',
    required: true,
  },
  priority: {
    type: String,
    enum: ['low', 'medium', 'high'],
    default: 'medium',
    index: true
  },
  dueDate: {
    type: Date,
    default: null,
    index: true
  },
  status: {
    type: String,
    enum: ['todo', 'completed', 'archived'],
    default: 'todo',
    index: true
  },
  completedAt: {
    type: Date,
    default: null
  },
  archivedAt: {
    type: Date,
    default: null
  },
  source: {
    type: String,
    enum: ['manual', 'ai_summary'],
    default: 'manual',
    index: true
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

taskSchema.index({ meetingId: 1, status: 1, priority: 1 });
taskSchema.index({ assigneeId: 1, status: 1 });
taskSchema.index({ meetingId: 1 });
taskSchema.index({ assigneeId: 1, dueDate: 1 });
taskSchema.index({ archivedAt: 1 });

module.exports = mongoose.model('Task', taskSchema);