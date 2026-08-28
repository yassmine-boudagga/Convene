const User = require('../models/User');
const Meeting = require('../models/Meeting');
const MeetingRecording = require('../models/MeetingRecording');
const MeetingAIResult = require('../models/MeetingAIResult');
const Task = require('../models/Task');
const Notification = require('../models/Notification');
const wsManager = require('../services/wsManager');
const livekitService = require('../services/Livekitservice');
const RevokedRefreshToken = require('../models/RevokedRefreshToken');
const fs = require('fs');
const path = require('path');
const { asyncHandler, successResponse, errorResponse } = require('../middleware/errorMiddleware');
const { extractId, extractJoinedUserIds } = require('../utils/idHelpers');
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

const MAX_LIMIT = 100;
const scheduledAIJobs = new Map(); // meetingId(string) → timeoutId

function escapeRegex(value) {
  return String(value || '').replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function parsePagination(query) {
  const pageNumber = Math.max(parseInt(query.page, 10) || 1, 1);
  const limitRaw = parseInt(query.limit, 10) || 20;
  const limitNumber = Math.min(Math.max(limitRaw, 1), MAX_LIMIT);
  const skip = (pageNumber - 1) * limitNumber;
  return { pageNumber, limitNumber, skip };
}

function buildPagination(total, pageNumber, limitNumber) {
  const pages = total === 0 ? 0 : Math.ceil(total / limitNumber);
  return { total, page: pageNumber, limit: limitNumber, pages };
}

function isValidObjectId(value) {
  return /^[a-fA-F0-9]{24}$/.test(String(value || ''));
}

const getStats = asyncHandler(async (req, res) => {
  try {
    const now = new Date();
    const firstDayOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);

    const [
      usersTotal, usersNewThisMonth,
      meetingsTotal, meetingsScheduled, meetingsOngoing, meetingsFinished,
      meetingsCancelled, meetingsArchived, meetingsOnline, meetingsPhysical,
      recordingsTotal, recordingsRecording, recordingsProcessing,
      recordingsDownloading, recordingsAvailable, recordingsFailed, recordingsLivekit,
      recordingsPhysical, tasksTotal, tasksTodo, tasksCompleted, tasksArchived,
      tasksManual, tasksAI, notificationsTotal, notificationsUnread,
      aiResultsTotal, aiCompleted, aiEmptyAudio, aiFailed, aiTranscriptionFailed
    ] = await Promise.all([
      User.countDocuments(),
      User.countDocuments({ createdAt: { $gte: firstDayOfMonth } }),
      Meeting.countDocuments(),
      Meeting.countDocuments({ status: 'scheduled' }),
      Meeting.countDocuments({ status: 'ongoing' }),
      Meeting.countDocuments({ status: 'finished' }),
      Meeting.countDocuments({ status: 'cancelled' }),
      Meeting.countDocuments({ status: 'archived' }),
      Meeting.countDocuments({ meetingType: 'online' }),
      Meeting.countDocuments({ meetingType: 'physical' }),
      Meeting.countDocuments({ status: 'ongoing' }),
      MeetingRecording.countDocuments(),
      MeetingRecording.countDocuments({ recordingStatus: 'recording' }),
      MeetingRecording.countDocuments({ recordingStatus: 'processing' }),
      MeetingRecording.countDocuments({ recordingStatus: 'downloading' }),
      MeetingRecording.countDocuments({ recordingStatus: 'available' }),
      MeetingRecording.countDocuments({ recordingStatus: 'failed' }),
      MeetingRecording.countDocuments({ source: 'livekit' }),
      MeetingRecording.countDocuments({ source: 'physical_upload' }),
      Task.countDocuments(),
      Task.countDocuments({ status: 'todo' }),
      Task.countDocuments({ status: 'completed' }),
      Task.countDocuments({ status: 'archived' }),
      Task.countDocuments({ source: 'manual' }),
      Task.countDocuments({ source: 'ai_summary' }),
      Notification.countDocuments(),
      Notification.countDocuments({ isRead: false }),
      MeetingAIResult.countDocuments(),
      MeetingAIResult.countDocuments({ pipelineStatus: 'completed' }),
      MeetingAIResult.countDocuments({ pipelineStatus: 'empty_audio' }),
      MeetingAIResult.countDocuments({ pipelineStatus: 'failed' }),
      MeetingAIResult.countDocuments({ pipelineStatus: 'transcription_failed' })
    ]);

    return res.status(200).json({
      success: true,
      data: {
        users: { total: usersTotal, newThisMonth: usersNewThisMonth },
        meetings: {
          total: meetingsTotal,
          byStatus: { scheduled: meetingsScheduled, ongoing: meetingsOngoing, finished: meetingsFinished, cancelled: meetingsCancelled, archived: meetingsArchived },
          byType: { online: meetingsOnline, physical: meetingsPhysical },
          activeLive: meetingsOngoing
        },
        recordings: {
          total: recordingsTotal,
          byStatus: { recording: recordingsRecording, processing: recordingsProcessing, downloading: recordingsDownloading, available: recordingsAvailable, failed: recordingsFailed },
          bySource: { livekit: recordingsLivekit, physical_upload: recordingsPhysical }
        },
        tasks: {
          total: tasksTotal,
          byStatus: { todo: tasksTodo, completed: tasksCompleted, archived: tasksArchived },
          bySource: { manual: tasksManual, ai_summary: tasksAI }
        },
        notifications: { total: notificationsTotal, unread: notificationsUnread },
        aiResults: {
          total: aiResultsTotal,
          byPipelineStatus: { completed: aiCompleted, empty_audio: aiEmptyAudio, failed: aiFailed, transcription_failed: aiTranscriptionFailed }
        },
        system: { nodeVersion: process.version, uptime: Math.round(process.uptime()), env: process.env.NODE_ENV || 'development' }
      }
    });
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getUsers = asyncHandler(async (req, res) => {
  try {
    const { pageNumber, limitNumber, skip } = parsePagination(req.query);
    const search = String(req.query.search || '').trim();
    const filter = {};
    if (search) {
      const regex = new RegExp(escapeRegex(search), 'i');
      filter.$or = [{ name: regex }, { email: regex }];
    }
    const [users, total] = await Promise.all([
      User.find(filter).select('-password -resetToken -resetTokenExpiry').sort({ createdAt: -1 }).skip(skip).limit(limitNumber).lean(),
      User.countDocuments(filter)
    ]);
    return res.status(200).json({ success: true, data: users, pagination: buildPagination(total, pageNumber, limitNumber) });
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getUserById = asyncHandler(async (req, res) => {
  try {
    const user = await User.findById(req.params.id).select('-password -resetToken -resetTokenExpiry');
    if (!user) return errorResponse(res, 'Utilisateur introuvable', 404);
    return successResponse(res, user, 'Utilisateur récupéré');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const updateUser = asyncHandler(async (req, res) => {
  try {
    const allowedFields = ['name', 'email', 'bio', 'jobTitle', 'company'];
    const updates = {};
    allowedFields.forEach((field) => { if (req.body[field] !== undefined) updates[field] = req.body[field]; });
    const user = await User.findByIdAndUpdate(req.params.id, updates, { new: true, runValidators: true }).select('-password -resetToken -resetTokenExpiry');
    if (!user) return errorResponse(res, 'Utilisateur introuvable', 404);
    return successResponse(res, user, 'Utilisateur mis à jour');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const deleteUser = asyncHandler(async (req, res) => {
  try {
    const user = await User.findById(req.params.id).select('email');
    if (!user) return errorResponse(res, 'Utilisateur introuvable', 404);
    const adminEmails = String(process.env.ADMIN_EMAILS || '').split(',').map((e) => e.trim().toLowerCase()).filter(Boolean);
    const userEmail = String(user.email || '').trim().toLowerCase();
    if (adminEmails.includes(userEmail)) return errorResponse(res, 'Suppression interdite pour un admin', 403);
    const requesterId = req.userId || req.user?._id;
    if (requesterId && requesterId.toString() === user._id.toString()) return errorResponse(res, 'Impossible de supprimer votre propre compte', 403);
    await Notification.deleteMany({ userId: user._id });
    await Task.updateMany({ assigneeId: user._id }, { $set: { assigneeId: null } });
    await Meeting.updateMany({ createdBy: user._id }, { $set: { status: 'cancelled' } });
    await RevokedRefreshToken.deleteMany({ userId: user._id });
    await User.deleteOne({ _id: user._id });
    return successResponse(res, { deletedId: user._id.toString() }, 'Utilisateur supprimé');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getMeetings = asyncHandler(async (req, res) => {
  try {
    const { pageNumber, limitNumber, skip } = parsePagination(req.query);
    const status = String(req.query.status || '').trim();
    const type = String(req.query.type || '').trim();
    const search = String(req.query.search || '').trim();
    const fromDate = req.query.fromDate;
    const toDate = req.query.toDate;

    const filter = {};
    if (status) filter.status = status;
    if (type) filter.meetingType = type;
    if (search) filter.title = new RegExp(escapeRegex(search), 'i');
    if (fromDate) filter.startTime = { ...filter.startTime, $gte: new Date(fromDate) };
    if (toDate) filter.startTime = { ...filter.startTime, $lte: new Date(toDate + 'T23:59:59') };

    const [meetings, total] = await Promise.all([
      Meeting.find(filter).populate('createdBy', 'name email').sort({ createdAt: -1 }).skip(skip).limit(limitNumber).lean(),
      Meeting.countDocuments(filter)
    ]);
    return res.status(200).json({ success: true, data: meetings, pagination: buildPagination(total, pageNumber, limitNumber) });
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getMeetingDetail = asyncHandler(async (req, res) => {
  try {
    const { id } = req.params;
    if (!isValidObjectId(id)) return errorResponse(res, 'ID invalide', 400);

    const [meeting, recording, aiResult, tasks] = await Promise.all([
      Meeting.findById(id)
        .populate('createdBy', 'name email profilePicture')
        .populate('joinedParticipants.userId', 'name email profilePicture')
        .populate('attendedBy', 'name email')
        .populate('notes.userId', 'name email'),
      MeetingRecording.findOne({ meetingId: id }).lean(),
      MeetingAIResult.findOne({ meetingId: id })
        .select('pipelineStatus transcript.durationSeconds summary.keyPoints summary.actionItems')
        .lean(),
      Task.find({ meetingId: id }).populate('assigneeId', 'name email').lean()
    ]);

    if (!meeting) return errorResponse(res, 'Réunion introuvable', 404);

    return successResponse(res, { meeting, recording, aiResult, tasks }, 'Détails récupérés');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const updateMeeting = asyncHandler(async (req, res) => {
  try {
    const allowedFields = ['title', 'description', 'startTime', 'duration', 'location', 'status'];
    const updates = {};
    allowedFields.forEach((field) => { if (req.body[field] !== undefined) updates[field] = req.body[field]; });
    const meeting = await Meeting.findByIdAndUpdate(req.params.id, updates, { new: true, runValidators: true });
    if (!meeting) return errorResponse(res, 'Réunion introuvable', 404);
    return successResponse(res, meeting, 'Réunion mise à jour');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const deleteMeetingAdmin = asyncHandler(async (req, res) => {
  const { id } = req.params;
  if (!isValidObjectId(id)) return errorResponse(res, 'ID de réunion invalide', 400);
  const meeting = await Meeting.findById(id);
  if (!meeting) return errorResponse(res, 'Réunion introuvable', 404);

  const recording = await MeetingRecording.findOne({ meetingId: id });
  if (recording?.recordingLocalPath) {
    try {
      if (fs.existsSync(recording.recordingLocalPath)) {
        fs.unlinkSync(recording.recordingLocalPath);
        debugLog(`[Admin] Fichier recording supprimé: ${recording.recordingLocalPath}`);
      }
    } catch (fileErr) {
      warnLog(`[Admin] Impossible de supprimer le fichier recording: ${fileErr.message}`);
    }
  }

  const [recResult, aiResult, taskResult, notifResult] = await Promise.all([
    MeetingRecording.deleteMany({ meetingId: id }),
    MeetingAIResult.deleteMany({ meetingId: id }),
    Task.deleteMany({ meetingId: id }),
    Notification.deleteMany({ 'data.meetingId': id }),
  ]);
  await Meeting.deleteOne({ _id: id });
  debugLog(`[Admin] Meeting ${id} supprimé avec cascade — recordings:${recResult.deletedCount} ai:${aiResult.deletedCount} tasks:${taskResult.deletedCount} notifs:${notifResult.deletedCount}`);

  return successResponse(res, {
    deletedId: id,
    cascade: { recordings: recResult.deletedCount, aiResults: aiResult.deletedCount, tasks: taskResult.deletedCount, notifications: notifResult.deletedCount }
  }, 'Réunion et données associées supprimées');
});

const forceEndMeeting = asyncHandler(async (req, res) => {
  try {
    const meeting = await Meeting.findById(req.params.id);
    if (!meeting) return errorResponse(res, 'Réunion introuvable', 404);
    if (['finished', 'cancelled', 'archived'].includes(meeting.status)) return errorResponse(res, `Réunion déjà terminée (status: ${meeting.status})`, 400);

    const attendedIds = (meeting.attendedBy || []).map(id => id?.toString()).filter(Boolean);
    const joinedIds = extractJoinedUserIds(meeting.joinedParticipants || []);
    const attendedBy = [...new Set([...attendedIds, ...joinedIds])];

    const updatedMeeting = await Meeting.findOneAndUpdate({ _id: meeting._id }, { $set: { status: 'finished', joinedParticipants: [], attendedBy } }, { new: true });
    wsManager.broadcastToMeeting(meeting._id.toString(), { event: 'meeting_force_end', meetingId: meeting._id.toString(), reason: 'admin_force_end', countdown: 0, triggeredBy: req.userId });
    // Arrêter le recording si actif
    const MeetingRecording = require('../models/MeetingRecording');
    await MeetingRecording.findOneAndUpdate(
      { meetingId: meeting._id, recordingStatus: 'recording' },
      { $set: { recordingStatus: 'failed' } }
    );
    if (meeting.roomId) {
      try { await livekitService.disableRoom(meeting.roomId); } catch (err) { warnLog(`[Admin] disableRoom warning: ${err.message}`); }
    }
    return successResponse(res, updatedMeeting, 'Réunion terminée');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getRecordings = asyncHandler(async (req, res) => {
  try {
    const { pageNumber, limitNumber, skip } = parsePagination(req.query);
    const status = String(req.query.status || '').trim();
    const source = String(req.query.source || '').trim();
    const fromDate = req.query.fromDate;
    const toDate = req.query.toDate;
    const filter = {};
    if (status) filter.recordingStatus = status;
    if (source) filter.source = source;
    if (fromDate) filter.recordingStartedAt = { ...filter.recordingStartedAt, $gte: new Date(fromDate) };
    if (toDate) filter.recordingStartedAt = { ...filter.recordingStartedAt, $lte: new Date(toDate + 'T23:59:59') };
    const [recordings, total] = await Promise.all([
      MeetingRecording.find(filter).populate('meetingId', 'title').sort({ createdAt: -1 }).skip(skip).limit(limitNumber).lean(),
      MeetingRecording.countDocuments(filter)
    ]);
    return res.status(200).json({ success: true, data: recordings, pagination: buildPagination(total, pageNumber, limitNumber) });
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const deleteRecording = asyncHandler(async (req, res) => {
  try {
    const recording = await MeetingRecording.findOne({ meetingId: req.params.meetingId });
    if (!recording) return errorResponse(res, 'Recording introuvable', 404);
    if (recording.recordingLocalPath) {
      const filePath = path.normalize(recording.recordingLocalPath);
      if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
    }
    await MeetingRecording.deleteOne({ meetingId: req.params.meetingId });
    return successResponse(res, { deletedMeetingId: req.params.meetingId }, 'Recording supprimé');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getTasks = asyncHandler(async (req, res) => {
  try {
    const { pageNumber, limitNumber, skip } = parsePagination(req.query);
    const status = String(req.query.status || '').trim();
    const source = String(req.query.source || '').trim();
    const fromDate = req.query.fromDate;
    const toDate = req.query.toDate;
    const filter = {};
    if (status) filter.status = status;
    if (source) filter.source = source;
    if (fromDate) filter.createdAt = { ...filter.createdAt, $gte: new Date(fromDate) };
    if (toDate) filter.createdAt = { ...filter.createdAt, $lte: new Date(toDate + 'T23:59:59') };
    const [tasks, total] = await Promise.all([
      Task.find(filter).populate('meetingId', 'title').populate('assigneeId', 'name email').sort({ createdAt: -1 }).skip(skip).limit(limitNumber).lean(),
      Task.countDocuments(filter)
    ]);
    return res.status(200).json({ success: true, data: tasks, pagination: buildPagination(total, pageNumber, limitNumber) });
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const updateTask = asyncHandler(async (req, res) => {
  try {
    const allowedFields = ['title', 'priority', 'dueDate', 'status'];
    const updates = {};
    allowedFields.forEach((field) => { if (req.body[field] !== undefined) updates[field] = req.body[field]; });
    const task = await Task.findByIdAndUpdate(req.params.id, updates, { new: true, runValidators: true });
    if (!task) return errorResponse(res, 'Tâche introuvable', 404);
    return successResponse(res, task, 'Tâche mise à jour');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const deleteTask = asyncHandler(async (req, res) => {
  try {
    const task = await Task.findByIdAndDelete(req.params.id);
    if (!task) return errorResponse(res, 'Tâche introuvable', 404);
    return successResponse(res, { deletedId: req.params.id }, 'Tâche supprimée');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getNotifications = asyncHandler(async (req, res) => {
  try {
    const { pageNumber, limitNumber, skip } = parsePagination(req.query);
    const type = String(req.query.type || '').trim();
    const userId = String(req.query.userId || '').trim();
    const fromDate = req.query.fromDate;
    const toDate = req.query.toDate;
    const filter = {};
    if (type) filter.type = type;
    if (userId && isValidObjectId(userId)) filter.userId = userId;
    if (fromDate) filter.createdAt = { ...filter.createdAt, $gte: new Date(fromDate) };
    if (toDate) filter.createdAt = { ...filter.createdAt, $lte: new Date(toDate + 'T23:59:59') };
    const [notifications, total] = await Promise.all([
      Notification.find(filter).populate('userId', 'name email').sort({ createdAt: -1 }).skip(skip).limit(limitNumber).lean(),
      Notification.countDocuments(filter)
    ]);
    return res.status(200).json({ success: true, data: notifications, pagination: buildPagination(total, pageNumber, limitNumber) });
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const deleteNotification = asyncHandler(async (req, res) => {
  try {
    const notification = await Notification.findByIdAndDelete(req.params.id);
    if (!notification) return errorResponse(res, 'Notification introuvable', 404);
    return successResponse(res, { deletedId: req.params.id }, 'Notification supprimée');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getAIResults = asyncHandler(async (req, res) => {
  try {
    const { pageNumber, limitNumber, skip } = parsePagination(req.query);
    const status = String(req.query.status || '').trim();
    const fromDate = req.query.fromDate;
    const toDate = req.query.toDate;
    const filter = {};
    if (status) filter.pipelineStatus = status;
    if (fromDate) filter.createdAt = { ...filter.createdAt, $gte: new Date(fromDate) };
    if (toDate) filter.createdAt = { ...filter.createdAt, $lte: new Date(toDate + 'T23:59:59') };
    const [results, total] = await Promise.all([
      MeetingAIResult.find(filter).populate('meetingId', 'title meetingType').sort({ createdAt: -1 }).skip(skip).limit(limitNumber).lean(),
      MeetingAIResult.countDocuments(filter)
    ]);
    return res.status(200).json({ success: true, data: results, pagination: buildPagination(total, pageNumber, limitNumber) });
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const deleteAIResult = asyncHandler(async (req, res) => {
  try {
    const result = await MeetingAIResult.findOneAndDelete({ meetingId: req.params.meetingId });
    if (!result) return errorResponse(res, 'Résultat IA introuvable', 404);
    return successResponse(res, { deletedMeetingId: req.params.meetingId }, 'Résultat IA supprimé');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getSystemStatus = asyncHandler(async (req, res) => {
  try {
    const [users, meetings, recordings, tasks, notifications, aiResults] = await Promise.all([
      User.countDocuments(), Meeting.countDocuments(), MeetingRecording.countDocuments(),
      Task.countDocuments(), Notification.countDocuments(), MeetingAIResult.countDocuments()
    ]);
    return res.status(200).json({
      success: true,
      data: {
        collections: { users, meetings, recordings, tasks, notifications, aiResults },
        process: { nodeVersion: process.version, uptime: Math.round(process.uptime()), pid: process.pid, memoryMB: Math.round(process.memoryUsage().heapUsed / 1024 / 1024) },
        env: process.env.NODE_ENV || 'development'
      }
    });
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const broadcastGlobal = asyncHandler(async (req, res) => {
  try {
    const { title, message, type } = req.body;
    if (!title || !message || !type) return errorResponse(res, 'Titre, message et type requis', 400);
    const allowedTypes = ['info', 'warning', 'maintenance'];
    if (!allowedTypes.includes(type)) return errorResponse(res, 'Type invalide (info|warning|maintenance)', 400);

    const users = await User.find({}, '_id').lean();

    const notifications = users.map(u => ({
      userId: u._id,
      type: 'admin_broadcast',
      title,
      message,
      data: { broadcastType: type },
      isRead: false,
      isDelivered: false,
      expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
    }));
    await Notification.insertMany(notifications);

    const wsDelivered = wsManager.broadcastAll({
      event: 'notification',
      notification: { type: 'admin_broadcast', title, message, data: { broadcastType: type }, createdAt: new Date() }
    });

    debugLog(`[Admin] Broadcast global: ${users.length} utilisateurs, ${wsDelivered} livrés en temps réel`);
    return successResponse(res, { recipientCount: users.length, wsDelivered }, 'Broadcast envoyé');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const getStatsByPeriod = asyncHandler(async (req, res) => {
  try {
    const { period = 'week', from, to } = req.query;
    let startDate;
    const endDate = to ? new Date(to + 'T23:59:59') : new Date();

    if (period === 'week') {
      startDate = new Date(); startDate.setDate(startDate.getDate() - 7);
    } else if (period === 'month') {
      startDate = new Date(); startDate.setMonth(startDate.getMonth() - 1);
    } else if (period === 'custom' && from) {
      startDate = new Date(from);
    } else {
      startDate = new Date(); startDate.setDate(startDate.getDate() - 7);
    }

    const filter = { createdAt: { $gte: startDate, $lte: endDate } };
    const meetingFilter = { createdAt: { $gte: startDate, $lte: endDate } };

    const [newUsers, meetings, recordings, tasks] = await Promise.all([
      User.countDocuments(filter),
      Meeting.find(meetingFilter).select('status meetingType'),
      MeetingRecording.countDocuments(filter),
      Task.countDocuments(filter)
    ]);

    const meetingsByStatus = {};
    meetings.forEach(m => { meetingsByStatus[m.status] = (meetingsByStatus[m.status] || 0) + 1; });
    const meetingsByType = { online: 0, physical: 0 };
    meetings.forEach(m => { meetingsByType[m.meetingType] = (meetingsByType[m.meetingType] || 0) + 1; });

    return successResponse(res, {
      period, range: { start: startDate, end: endDate },
      users: { new: newUsers },
      meetings: { total: meetings.length, byStatus: meetingsByStatus, byType: meetingsByType },
      recordings: { total: recordings },
      tasks: { total: tasks }
    }, 'Statistiques récupérées');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});


const getFailedAIMeetings = asyncHandler(async (req, res) => {
  try {
    // Fetch all failed meetings (lean for performance)
    const failedMeetings = await Meeting.find({ aiStatus: 'failed' })
      .select('_id title startTime duration participants createdBy notes')
      .lean();

    const reprocessable = [];

    for (const meeting of failedMeetings) {
      if (scheduledAIJobs.has(meeting._id.toString())) continue;

      const recording = await MeetingRecording.findOne({ meetingId: meeting._id })
        .select('recordingStatus recordingLocalPath')
        .lean();

      if (
        recording &&
        recording.recordingStatus === 'available' &&
        recording.recordingLocalPath &&
        fs.existsSync(recording.recordingLocalPath)
      ) {
        reprocessable.push({
          _id:              meeting._id,
          title:            meeting.title,
          startTime:        meeting.startTime,
          recordingLocalPath: recording.recordingLocalPath
        });
      }
    }

    return successResponse(res, reprocessable, 'Réunions IA failed récupérées');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const resetSingleAIMeeting = asyncHandler(async (req, res) => {
  try {
    const { meetingId } = req.params;
    const { scheduledAt } = req.body; // ISO string or undefined

    if (!isValidObjectId(meetingId)) {
      return errorResponse(res, 'ID de réunion invalide', 400);
    }

    //  1. Validate scheduledAt if provided
    const MAX_DELAY_MS = 24 * 60 * 60 * 1000; // 24 hours
    let delayMs = 0;
    let scheduledDate = null;

    if (scheduledAt) {
      scheduledDate = new Date(scheduledAt);
      if (isNaN(scheduledDate.getTime())) {
        return errorResponse(res, 'scheduledAt invalide (format ISO attendu)', 400);
      }
      delayMs = scheduledDate.getTime() - Date.now();
      if (delayMs < 0) delayMs = 0; // past date → immediate
      if (delayMs > MAX_DELAY_MS) {
        return errorResponse(res, 'scheduledAt ne peut pas dépasser 24h dans le futur', 400);
      }
    }

    //  2. Verify meeting state RIGHT NOW 
    const meeting = await Meeting.findById(meetingId)
      .select('_id title aiStatus duration participants createdBy notes')
      .lean();

    if (!meeting) return errorResponse(res, 'Réunion introuvable', 404);
    if (meeting.aiStatus === 'processing') {
      return errorResponse(res, 'Pipeline IA déjà en cours pour cette réunion', 409);
    }
    if (meeting.aiStatus !== 'failed') {
      return errorResponse(res, `Statut IA actuel: ${meeting.aiStatus}. Seules les réunions "failed" peuvent être réinitialisées`, 400);
    }

    //  3. Verify recording RIGHT NOW 
    const recording = await MeetingRecording.findOne({ meetingId })
      .select('recordingStatus recordingLocalPath')
      .lean();

    if (!recording) {
      return errorResponse(res, 'Aucun enregistrement trouvé pour cette réunion', 404);
    }
    if (recording.recordingStatus !== 'available') {
      return errorResponse(res, `Enregistrement non disponible (statut: ${recording.recordingStatus})`, 400);
    }
    if (!recording.recordingLocalPath || !fs.existsSync(recording.recordingLocalPath)) {
      return errorResponse(res, 'Fichier audio introuvable sur le serveur', 404);
    }

    //  4. Cancel any existing scheduled job for this meeting 
    const meetingIdStr = meetingId.toString();
    if (scheduledAIJobs.has(meetingIdStr)) {
      clearTimeout(scheduledAIJobs.get(meetingIdStr));
      scheduledAIJobs.delete(meetingIdStr);
      debugLog(`[Admin] Ancien job annulé pour meeting ${meetingIdStr}`);
    }

    //  5. Capture values for the async job (avoid closure issues) 
    const audioLocalPath = recording.recordingLocalPath;
    const meetingMeta = {
      title:        meeting.title,
      duration:     meeting.duration,
      participants: meeting.participants || [],
      createdBy:    meeting.createdBy
    };
    const notes = meeting.notes || [];

    //  6. Schedule or fire immediately 
    const executeJob = async () => {
      scheduledAIJobs.delete(meetingIdStr);
      debugLog(`[Admin] Executing AI reset job for meeting ${meetingIdStr} (${meeting.title})`);

      try {
        // Re-verify state at execution time (server restart protection)
        const freshMeeting = await Meeting.findById(meetingIdStr).select('aiStatus').lean();
        if (!freshMeeting) {
          warnLog(`[Admin] Meeting ${meetingIdStr} introuvable au moment de l'exécution — job annulé`);
          return;
        }
        if (freshMeeting.aiStatus === 'processing') {
          warnLog(`[Admin] Meeting ${meetingIdStr} déjà en processing au moment de l'exécution — job annulé`);
          return;
        }

        // Re-verify file still exists
        if (!fs.existsSync(audioLocalPath)) {
          warnLog(`[Admin] Fichier audio disparu pour meeting ${meetingIdStr} — job annulé`);
          await Meeting.updateOne({ _id: meetingIdStr }, { $set: { aiStatus: 'failed' } });
          return;
        }

        // Reset status + clean old AI result
        await Meeting.updateOne(
          { _id: meetingIdStr, aiStatus: { $in: ['failed', 'not_started'] } },
          { $set: { aiStatus: 'not_started' } }
        );
        await MeetingAIResult.deleteOne({ meetingId: meetingIdStr });

        // Trigger pipeline (aiService handles retries + status updates)
        const { triggerPipeline } = require('../services/aiService');
        await triggerPipeline(meetingIdStr, audioLocalPath, notes, meetingMeta);

        debugLog(`[Admin] AI pipeline re-triggered successfully for meeting ${meetingIdStr}`);
      } catch (jobErr) {
        errorLog(`[Admin] AI reset job failed for meeting ${meetingIdStr}: ${jobErr.message}`);
        // triggerPipeline already sets aiStatus:'failed' on error — no need to do it here
      }
    };

    if (delayMs > 0) {
      const timeoutId = setTimeout(executeJob, delayMs);
      scheduledAIJobs.set(meetingIdStr, timeoutId);
      const scheduledLabel = scheduledDate.toLocaleString('fr-FR');
      debugLog(`[Admin] AI reset job programmé pour meeting ${meetingIdStr} à ${scheduledLabel} (dans ${Math.round(delayMs / 60000)} min)`);

      return successResponse(res, {
        meetingId:    meetingIdStr,
        title:        meeting.title,
        scheduledAt:  scheduledDate.toISOString(),
        delayMinutes: Math.round(delayMs / 60000),
        status:       'scheduled'
      }, `Pipeline IA programmé pour ${scheduledLabel}`);
    } else {
      // Immediate: fire & forget
      setImmediate(executeJob);
      return successResponse(res, {
        meetingId: meetingIdStr,
        title:     meeting.title,
        status:    'triggered'
      }, 'Pipeline IA déclenché immédiatement');
    }
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

const cancelScheduledAIMeeting = asyncHandler(async (req, res) => {
  try {
    const { meetingId } = req.params;
    if (!isValidObjectId(meetingId)) return errorResponse(res, 'ID invalide', 400);

    const meetingIdStr = meetingId.toString();
    if (!scheduledAIJobs.has(meetingIdStr)) {
      return errorResponse(res, 'Aucun job programmé pour cette réunion', 404);
    }

    clearTimeout(scheduledAIJobs.get(meetingIdStr));
    scheduledAIJobs.delete(meetingIdStr);
    debugLog(`[Admin] Job annulé manuellement pour meeting ${meetingIdStr}`);

    return successResponse(res, { meetingId: meetingIdStr }, 'Job annulé');
  } catch (err) {
    return errorResponse(res, err.message || 'Erreur serveur', 500);
  }
});

module.exports = {
  getStats, getSystemStatus,
  getUsers, getUserById, updateUser, deleteUser,
  getMeetings, getMeetingDetail, updateMeeting, deleteMeetingAdmin, forceEndMeeting,
  getRecordings, deleteRecording,
  getTasks, updateTask, deleteTask,
  getNotifications, deleteNotification,
  getAIResults, deleteAIResult,
  broadcastGlobal, getStatsByPeriod,
  getFailedAIMeetings, resetSingleAIMeeting, cancelScheduledAIMeeting
};