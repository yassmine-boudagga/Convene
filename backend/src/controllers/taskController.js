const mongoose = require('mongoose');
const Task = require('../models/Task');
const Meeting = require('../models/Meeting');
const User = require('../models/User');
const { checkAndGrantAchievements } = require('../services/achievementService');
const { asyncHandler, successResponse, errorResponse } = require('../middleware/errorMiddleware');

async function canAccessMeeting(meeting, req) {
  const userEmail = req.user?.email ? req.user.email.toLowerCase() : '';
  return meeting.hasAccess(req.userId, userEmail);
}

async function getTaskAndValidateAccess(taskId, req) {
  if (!mongoose.Types.ObjectId.isValid(taskId)) {
    return { error: { message: 'ID de tâche invalide', status: 400 } };
  }

  const task = await Task.findById(taskId);
  if (!task) {
    return { error: { message: 'Tâche introuvable', status: 404 } };
  }

  const meeting = await Meeting.findById(task.meetingId);
  if (!meeting) {
    return { error: { message: 'Réunion liée introuvable', status: 404 } };
  }

  const isAssignee = task.assigneeId && task.assigneeId.toString() === req.userId.toString();
  const isCreator = meeting.isCreator(req.userId);

  if (!isAssignee && !isCreator) {
    return { error: { message: 'Accès non autorisé à cette tâche', status: 403 } };
  }

  return { task, meeting };
}

function normalizeTaskPayload(task) {
  const assigneeObj = task.assigneeId && task.assigneeId._id ? task.assigneeId : null;
  const meetingObj = task.meetingId && task.meetingId._id ? task.meetingId : null;

  return {
    id: task._id != null ? task._id.toString() : '',
    title: task.title,
    assigneeId: assigneeObj?._id ? assigneeObj._id.toString() : (task.assigneeId ? task.assigneeId.toString() : null),
    assigneeName: assigneeObj?.name || null,
    assigneeEmail: assigneeObj?.email || null,
    meetingId: meetingObj?._id ? meetingObj._id.toString() : (task.meetingId ? task.meetingId.toString() : null),
    meetingTitle: meetingObj?.title || null,
    status: task.status,
    priority: task.priority,
    dueDate: task.dueDate,
    completedAt: task.completedAt,
    archivedAt: task.archivedAt,
    source: task.source,
    createdAt: task.createdAt,
    updatedAt: task.updatedAt
  };
}

const getMyTasks = asyncHandler(async (req, res) => {
  const {
    status,
    priority,
    meetingId,
    archived,
    fromDate,
    toDate,
    page = 1,
    limit = 50
  } = req.query;

  const archivedFlag = String(archived || 'false').toLowerCase() === 'true';
  const pageNumber = Math.max(parseInt(page, 10) || 1, 1);
  const limitNumber = Math.max(parseInt(limit, 10) || 50, 1);
  const skip = (pageNumber - 1) * limitNumber;

  const match = {
    assigneeId: new mongoose.Types.ObjectId(req.userId)
  };

  if (archivedFlag) {
    match.status = 'archived';
  } else {
    match.status = { $ne: 'archived' };
    if (status) {
      match.status = status;
    }
  }

  if (priority) {
    match.priority = priority;
  }

  if (meetingId && mongoose.Types.ObjectId.isValid(meetingId)) {
    match.meetingId = new mongoose.Types.ObjectId(meetingId);
  }

  if (fromDate || toDate) {
    match.dueDate = {};
    if (fromDate) {
      match.dueDate.$gte = new Date(fromDate);
    }
    if (toDate) {
      const end = new Date(toDate);
      end.setHours(23, 59, 59, 999);
      match.dueDate.$lte = end;
    }
  }

  const [result] = await Task.aggregate([
    { $match: match },
    {// exclure tâches orphelines
      $lookup: {
        from: 'meetings', 
        localField: 'meetingId', 
        foreignField: '_id', 
        as: '_meetingExists' 
      } 
    }, 
    { 
      $match: {  
        '_meetingExists.0': { $exists: true }  
      }  
    },  
    {  
      $project: { _meetingExists: 0 }  
    },  
    {
      $addFields: {
        dueDateSortNull: {
          $cond: [{ $eq: ['$dueDate', null] }, 1, 0]
        }
      }
    },
    {
      $lookup: {
        from: 'meetings',
        localField: 'meetingId',
        foreignField: '_id',
        as: 'meeting'
      }
    },
    {
      $lookup: {
        from: 'users',
        localField: 'assigneeId',
        foreignField: '_id',
        as: 'assignee'
      }
    },
    {
      $unwind: {
        path: '$meeting',
        preserveNullAndEmptyArrays: true
      }
    },
    {
      $unwind: {
        path: '$assignee',
        preserveNullAndEmptyArrays: true
      }
    },
    {
      $sort: {
        dueDateSortNull: 1,
        dueDate: 1,
        createdAt: -1
      }
    },
    {
      $facet: {
        tasks: [
          { $skip: skip },
          { $limit: limitNumber },
          {
            $project: {
              _id: 0,
              id: { $toString: '$_id' },
              title: 1,
              assigneeId: {
                $ifNull: [{ $toString: '$assignee._id' }, { $toString: '$assigneeId' }]
              },
              assigneeName: '$assignee.name',
              assigneeEmail: '$assignee.email',
              meetingId: {
                $ifNull: [{ $toString: '$meeting._id' }, { $toString: '$meetingId' }]
              },
              meetingTitle: '$meeting.title',
              status: 1,
              priority: 1,
              dueDate: 1,
              completedAt: 1,
              archivedAt: 1,
              source: 1,
              createdAt: 1,
              updatedAt: 1
            }
          }
        ],
        meta: [{ $count: 'total' }]
      }
    }
  ]);

  const tasks = result?.tasks || [];
  const total = result?.meta?.[0]?.total || 0;
  const pages = total === 0 ? 0 : Math.ceil(total / limitNumber);

  return res.status(200).json({
    success: true,
    data: tasks,
    total,
    page: pageNumber,
    pages
  });
});

const getAllRelatedTasks = asyncHandler(async (req, res) => {
  const {
    status,
    priority,
    meetingId,
    archived,
    fromDate,
    toDate,
    page = 1,
    limit = 50
  } = req.query;

  const archivedFlag = String(archived || 'false').toLowerCase() === 'true';
  const pageNumber = Math.max(parseInt(page, 10) || 1, 1);
  const limitNumber = Math.max(parseInt(limit, 10) || 50, 1);
  const skip = (pageNumber - 1) * limitNumber;

  const user = await User.findById(req.userId).select('email');
  if (!user) {
    return errorResponse(res, 'Utilisateur introuvable', 404);
  }

  const meetings = await Meeting.find({
    $or: [
      { createdBy: req.userId },
      { participants: user.email }
    ],
    status: 'finished'
  }).select('_id');

  const meetingIds = meetings.map((meeting) => meeting._id);
  if (meetingIds.length === 0) {
    return successResponse(res, [], 'Tâches liées récupérées');
  }

  const query = {
    meetingId: { $in: meetingIds },
  };

  if (archivedFlag) {
    query.status = 'archived';
  } else {
    query.status = { $ne: 'archived' };
    if (status) {
      query.status = status;
    }
  }

  if (priority) {
    query.priority = priority;
  }

  if (meetingId && mongoose.Types.ObjectId.isValid(meetingId)) {
    query.meetingId = new mongoose.Types.ObjectId(meetingId);
  }

  if (fromDate || toDate) {
    query.dueDate = {};
    if (fromDate) {
      query.dueDate.$gte = new Date(fromDate);
    }
    if (toDate) {
      const end = new Date(toDate);
      end.setHours(23, 59, 59, 999);
      query.dueDate.$lte = end;
    }
  }

  const tasks = await Task.find(query)
    .populate('meetingId', 'title')
    .populate('assigneeId', 'name email')
    .sort({ dueDate: 1, createdAt: -1 })
    .skip(skip)
    .limit(limitNumber);

  const normalizedTasks = tasks.map(normalizeTaskPayload);

  return successResponse(res, normalizedTasks, 'Tâches liées récupérées');
});

const createTask = asyncHandler(async (req, res) => {
  const { title, meetingId, assigneeId, priority, dueDate } = req.body;

  if (!title || !meetingId) {
    return errorResponse(res, 'title et meetingId sont obligatoires', 400);
  }

  if (!mongoose.Types.ObjectId.isValid(meetingId)) {
    return errorResponse(res, 'meetingId invalide', 400);
  }

  const meeting = await Meeting.findById(meetingId);
  if (!meeting) {
    return errorResponse(res, 'Réunion introuvable', 404);
  }

  const hasAccess = await canAccessMeeting(meeting, req);
  if (!hasAccess) {
    return errorResponse(res, 'Accès non autorisé', 403);
  }

  const task = await Task.create({
    title,
    meetingId,
    assigneeId: assigneeId || req.userId,
    priority: priority || 'medium',
    dueDate: dueDate || null,
    source: 'manual'
  });

  const populatedTask = await Task.findById(task._id)
    .populate('meetingId', 'title')
    .populate('assigneeId', 'name email');

  return successResponse(res, normalizeTaskPayload(populatedTask), 'Tâche créée', 201);
});

const completeTask = asyncHandler(async (req, res) => {
  const access = await getTaskAndValidateAccess(req.params.id, req);
  if (access.error) {
    return errorResponse(res, access.error.message, access.error.status);
  }

  const { task } = access;
  if ( task.status === 'completed') {
    return errorResponse(res, 'Tâche déjà complétée', 400);
  }

  task.status = 'completed';
  task.completedAt = new Date();
  await task.save();

  const populatedTask = await Task.findById(task._id)
    .populate('meetingId', 'title')
    .populate('assigneeId', 'name email');

  checkAndGrantAchievements(task.assigneeId?.toString()).catch(console.error);

  return successResponse(res, normalizeTaskPayload(populatedTask), 'Tâche complétée');
});

async function archiveOldCompletedTasks() {
  const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
  const result = await Task.updateMany(
    {
      status: 'completed',
      completedAt: { $lt: sevenDaysAgo },
      archivedAt: null
    },
    {
      $set: {
        status: 'archived',
        archivedAt: new Date()
      }
    }
  );
  return result.modifiedCount;
}

module.exports = {
  createTask,
  getMyTasks,
  getAllRelatedTasks,
  completeTask,
  archiveOldCompletedTasks
};
