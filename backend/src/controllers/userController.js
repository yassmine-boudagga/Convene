

const User = require('../models/User');
const Meeting = require('../models/Meeting');
const Task = require('../models/Task');
const Notification = require('../models/Notification');
const { asyncHandler, successResponse, errorResponse } = require('../middleware/errorMiddleware');
const wsManager = require('../services/wsManager');
const { ACHIEVEMENTS, getProgress, checkAndGrantAchievements } = require('../services/achievementService');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

const ACHIEVEMENT_TARGETS = {
  organizer: 10,
  punctual: 10,
  collaborator: 20,
  bilingual: 2,
  marathon: 1,
  efficient: 10
};
// Helpers
function getFriendStatus(currentUser, targetUser) {
  const currentIdStr = currentUser._id.toString();
  const targetIdStr = targetUser._id.toString();

  // Check if already friends
  const alreadyFriends =
    (currentUser.friends && currentUser.friends.some(f => f.toString() === targetIdStr)) ||
    (targetUser.friends && targetUser.friends.some(f => f.toString() === currentIdStr));

  if (alreadyFriends) return 'friends';

  // Current user sent a request to target (pending in target's friendRequests)
  const sentByMe =
    targetUser.friendRequests &&
    targetUser.friendRequests.some(
      r => r.from.toString() === currentIdStr
    );
  if (sentByMe) return 'pending_sent';

  // Target sent a request to current user (pending in currentUser's friendRequests)
  const sentByTarget =
    currentUser.friendRequests &&
    currentUser.friendRequests.some(
      r => r.from.toString() === targetIdStr
    );
  if (sentByTarget) return 'pending_received';

  return 'none';
}

const searchUsers = asyncHandler(async (req, res) => {
  const { q } = req.query;
  if (!q || q.trim().length < 1) {
    return successResponse(res, { users: [] }, 'No query provided');
  }

  const regex = new RegExp(q.trim(), 'i');
  const users = await User.find({
    _id: { $ne: req.userId },
    $or: [{ name: regex }, { email: regex }]
  })
    .select('_id name email profilePicture bio jobTitle company friends friendRequests')
    .limit(30);

  const currentUser = req.user;
  const currentUserFull = await User.findById(req.userId).select('friends friendRequests');

  const results = users.map(u => ({
    id: u._id.toString(),
    name: u.name,
    email: u.email,
    profilePicture: u.profilePicture,
    bio: u.bio,
    jobTitle: u.jobTitle,
    company: u.company,
    friendStatus: getFriendStatus(currentUserFull, u)
  }));

  return successResponse(res, { users: results }, 'Search results');
});

const sendFriendRequest = asyncHandler(async (req, res) => {
  const targetId = req.params.id;
  const currentUserId = req.userId.toString();

  if (targetId === currentUserId) {
    return errorResponse(res, 'Cannot send friend request to yourself', 400);
  }

  const targetUser = await User.findById(targetId).select('_id name friends friendRequests');
  if (!targetUser) return errorResponse(res, 'User not found', 404);

  // Check already friends
  if (targetUser.friends && targetUser.friends.some(f => f.toString() === currentUserId)) {
    return errorResponse(res, 'Already friends', 400);
  }

  // Check pending request already sent
  const alreadyPending = targetUser.friendRequests &&
    targetUser.friendRequests.some(
      r => r.from.toString() === currentUserId
    );
  if (alreadyPending) {
    return errorResponse(res, 'Friend request already sent', 400);
  }

  // Add request to target's friendRequests
  await User.findByIdAndUpdate(targetId, {
    $push: {
      friendRequests: { from: req.userId, createdAt: new Date() }
    }
  });

  const notif = await Notification.create({
    userId: targetId,
    type: 'friend_request',
    title: 'Nouvelle demande d\'ami',
    message: `${req.user.name} vous a envoye une demande d'ami`,
    data: {
      fromUserId: req.user._id.toString(),
      fromUserName: req.user.name
    },
    isRead: false,
    isDelivered: false
  });

  const delivered = wsManager.sendToUser(targetId.toString(), {
    event: 'notification',
    notification: {
      id: notif._id.toString(),
      type: notif.type,
      title: notif.title,
      message: notif.message,
      data: notif.data,
      createdAt: notif.createdAt
    }
  });
  if (delivered) {
    await Notification.updateOne(
      { _id: notif._id },
      { $set: { isDelivered: true, deliveredAt: new Date() } }
    );
  }

  // WS notification to target user
  wsManager.sendToUser(targetId.toString(), {
    event: 'friend_request_received',
    fromUserId: currentUserId,
    fromName: req.user.name
  });

  return successResponse(res, null, 'Friend request sent', 201);
});

const acceptFriendRequest = asyncHandler(async (req, res) => {
  const fromUserId = req.params.id;
  const currentUserId = req.userId.toString();

  // Verify request exists in currentUser's friendRequests
  const currentUser = await User.findById(currentUserId).select('friendRequests friends');
  if (!currentUser) return errorResponse(res, 'User not found', 404);

  const request = currentUser.friendRequests &&
    currentUser.friendRequests.find(
      r => r.from.toString() === fromUserId
    );

  if (!request) return errorResponse(res, 'Friend request not found', 404);

  // Remove the request from currentUser and add mutual friends
  await User.findByIdAndUpdate(currentUserId, {
    $pull: { friendRequests: { from: fromUserId } },
    $addToSet: { friends: fromUserId }
  });

  await User.findByIdAndUpdate(fromUserId, {
    $addToSet: { friends: currentUserId }
  });

  const notif = await Notification.create({
    userId: fromUserId,
    type: 'friend_accepted',
    title: 'Demande d\'ami acceptee',
    message: `${req.user.name} a accepte votre demande d'ami`,
    data: {
      fromUserId: req.user._id.toString(),
      fromUserName: req.user.name
    },
    isRead: false,
    isDelivered: false
  });

  const delivered = wsManager.sendToUser(fromUserId.toString(), {
    event: 'notification',
    notification: {
      id: notif._id.toString(),
      type: notif.type,
      title: notif.title,
      message: notif.message,
      data: notif.data,
      createdAt: notif.createdAt
    }
  });
  if (delivered) {
    await Notification.updateOne(
      { _id: notif._id },
      { $set: { isDelivered: true, deliveredAt: new Date() } }
    );
  }

  // WS notification to initiator
  wsManager.sendToUser(fromUserId.toString(), {
    event: 'friend_request_accepted',
    byUserId: currentUserId,
    byName: req.user.name
  });

  return successResponse(res, null, 'Friend request accepted');
});
// (reject or cancel)
const rejectOrCancelFriendRequest = asyncHandler(async (req, res) => {
  const otherId = req.params.id;
  const currentUserId = req.userId.toString();

  const [currentUser, targetUser] = await Promise.all([
    User.findById(currentUserId).select('_id name friendRequests'),
    User.findById(otherId).select('_id friendRequests')
  ]);

  if (!currentUser) return errorResponse(res, 'User not found', 404);
  if (!targetUser) return errorResponse(res, 'User not found', 404);

  const isRejectingReceived = (currentUser.friendRequests || []).some(
    r => r.from.toString() === otherId
  );

  const isCancellingSent = (targetUser.friendRequests || []).some(
    r => r.from.toString() === currentUserId
  );

  if (!isRejectingReceived && !isCancellingSent) {
    return errorResponse(res, 'Friend request not found', 404);
  }

  if (isRejectingReceived) {
    await User.findByIdAndUpdate(currentUserId, {
      $pull: { friendRequests: { from: otherId } }
    });

    const notif = await Notification.create({
      userId: otherId,
      type: 'friend_rejected',
      title: 'Demande d\'ami refusee',
      message: `${currentUser.name} a refuse votre demande d'ami`,
      data: {
        fromUserId: currentUser._id.toString(),
        fromUserName: currentUser.name
      },
      isRead: false,
      isDelivered: false
    });

    const delivered = wsManager.sendToUser(otherId.toString(), {
      event: 'notification',
      notification: {
        id: notif._id.toString(),
        type: 'friend_rejected',
        title: notif.title,
        message: notif.message,
        isRead: false,
        isDelivered: false,
        createdAt: notif.createdAt,
        data: {
          fromUserId: currentUser._id.toString(),
          fromUserName: currentUser.name
        }
      }
    });

    if (delivered) {
      await Notification.updateOne(
        { _id: notif._id },
        { $set: { isDelivered: true, deliveredAt: new Date() } }
      );
    }
  }

  if (isCancellingSent) {
    await User.findByIdAndUpdate(otherId, {
      $pull: { friendRequests: { from: currentUserId } }
    });
  }

  return successResponse(res, null, 'Friend request removed');
});

// DELETE /api/users/:id/friend
const removeFriend = asyncHandler(async (req, res) => {
  const targetId = req.params.id;
  const currentUserId = req.userId.toString();

  if (targetId === currentUserId) {
    return errorResponse(res, 'Cannot remove yourself from friends', 400);
  }

  const targetExists = await User.exists({ _id: targetId });
  if (!targetExists) return errorResponse(res, 'User not found', 404);

  await User.updateOne(
    { _id: req.user._id },
    { $pull: { friends: targetId } }
  );

  await User.updateOne(
    { _id: targetId },
    { $pull: { friends: req.user._id } }
  );

  return successResponse(res, null, 'Amitie supprimee');
});

// GET /api/users/me/friends
const getMyFriends = asyncHandler(async (req, res) => {
  const user = await User.findById(req.userId)
    .populate('friends', '_id name email profilePicture bio jobTitle company');

  if (!user) return errorResponse(res, 'User not found', 404);

  const friends = (user.friends || []).map(f => ({
    id: f._id.toString(),
    name: f.name,
    email: f.email,
    profilePicture: f.profilePicture,
    bio: f.bio,
    jobTitle: f.jobTitle,
    company: f.company,
    friendStatus: 'friends'
  }));

  return successResponse(res, { friends }, 'Friends list');
});

const getPublicProfile = asyncHandler(async (req, res) => {
  const targetId = req.params.id;
  const currentUserId = req.userId.toString();

  const [targetUser, currentUser] = await Promise.all([
    User.findById(targetId).select('_id name email profilePicture bio jobTitle company friends friendRequests achievements'),
    User.findById(currentUserId).select('friends friendRequests')
  ]);

  if (!targetUser) return errorResponse(res, 'User not found', 404);

  // Meeting stats + notes count
  const noteMeetings = await Meeting.find({ 'notes.userId': targetId }).select('notes');
  const notesAdded = noteMeetings.reduce((sum, m) =>
    sum + (m.notes || []).filter(n => n.userId && n.userId.toString() === targetId.toString()).length, 0);

  const [meetingsOrganized, meetingsAttended, tasksCompleted] = await Promise.all([
    Meeting.countDocuments({ createdBy: targetId }),
    Meeting.countDocuments({ attendedBy: targetId, status: 'finished' }),
    Task.countDocuments({ assigneeId: targetId, status: 'completed' })
  ]);

  const friendStatus = targetId === currentUserId
    ? 'self'
    : getFriendStatus(currentUser, targetUser);

  const now = new Date();
  const currentAchievements = (targetUser.achievements || []).filter(
    a => a.month === now.getMonth() && a.year === now.getFullYear()
  );
  const earnedMap = new Map(currentAchievements.map(a => [a.id, a]));
  const progress = await getProgress(targetId.toString());

  const achievements = Object.entries(ACHIEVEMENTS)
    .filter(([id]) => {
      const current = progress[id] ?? 0;
      const target = ACHIEVEMENT_TARGETS[id] ?? 0;
      return earnedMap.has(id) || current >= target;
    })
    .map(([id, def]) => {
      const earned = earnedMap.get(id);
      return {
        id,
        name: def.name,
        unlockedAt: earned ? earned.unlockedAt : null
      };
    });

  return successResponse(res, {
    profile: {
      id: targetUser._id.toString(),
      name: targetUser.name,
      email: targetUser.email,
      profilePicture: targetUser.profilePicture,
      bio: targetUser.bio,
      jobTitle: targetUser.jobTitle,
      company: targetUser.company,
      friendStatus,
      meetingsOrganized,
      meetingsAttended,
      notesAdded,
      tasksCompleted,
      achievements
    }
  }, 'Public profile');
});

// GET /api/users/me/stats
const getMyStats = asyncHandler(async (req, res) => {
  const userId = req.userId;

  const user = await User.findById(userId).select('achievements email');
  if (!user) return errorResponse(res, 'User not found', 404);

  // Count notes added by user across all meetings
  const noteMeetings = await Meeting.find({ 'notes.userId': userId }).select('notes');
  const notesAdded = noteMeetings.reduce((sum, m) =>
    sum + (m.notes || []).filter(n => n.userId && n.userId.toString() === userId.toString()).length, 0);

  const [meetingsOrganized, meetingsAttended, tasksCompleted] = await Promise.all([
    Meeting.countDocuments({ createdBy: userId }),
    Meeting.countDocuments({ attendedBy: userId, status: 'finished' }),
    Task.countDocuments({ assigneeId: userId, status: 'completed' })
  ]);

  const now = new Date();
  const currentAchievements = (user.achievements || []).filter(
    a => a.month === now.getMonth() && a.year === now.getFullYear()
  );
  const unlockedIds = new Set(currentAchievements.map(a => a.id));
  const earnedMap = new Map(currentAchievements.map(a => [a.id, a]));
  const progress = await getProgress(userId.toString());

  let shouldRetroGrant = false;

  const allAchievements = Object.entries(ACHIEVEMENTS).map(([id, def]) => {
    const current = progress[id] ?? 0;
    const target = ACHIEVEMENT_TARGETS[id] ?? 0;
    const unlockedByProgress = current >= target;
    const unlocked = unlockedIds.has(id) || unlockedByProgress;
    if (unlockedByProgress && !unlockedIds.has(id)) {
      shouldRetroGrant = true;
    }

    const earned = earnedMap.get(id);
    return {
      id,
      name: def.name,
      unlocked,
      unlockedAt: earned ? earned.unlockedAt : null,
      current,
      target
    };
  });

  if (shouldRetroGrant) {
    checkAndGrantAchievements(userId.toString()).catch((e) => {
      errorLog('[Achievement] retro-grant /me/stats:', e.message);
    });
  }

  return successResponse(res, {
    stats: {
      meetingsOrganized,
      meetingsAttended,
      notesAdded,
      tasksCompleted,
      achievements: allAchievements
    }
  }, 'My stats');
});

// GET /api/users/me/profile
const getMyProfile = asyncHandler(async (req, res) => {
  const userId = req.userId;
  const user = await User.findById(userId)
    .select('_id name email profilePicture bio jobTitle company friends friendRequests achievements createdAt');

  if (!user) return errorResponse(res, 'User not found', 404);

  // Count notes added by user across all meetings
  const noteMeetings = await Meeting.find({ 'notes.userId': userId }).select('notes');
  const notesAdded = noteMeetings.reduce((sum, m) =>
    sum + (m.notes || []).filter(n => n.userId && n.userId.toString() === userId.toString()).length, 0);

  const [meetingsOrganized, meetingsAttended, tasksCompleted] = await Promise.all([
    Meeting.countDocuments({ createdBy: userId }),
    Meeting.countDocuments({ attendedBy: userId, status: 'finished' }),
    Task.countDocuments({ assigneeId: userId, status: 'completed' })
  ]);

  // Full achievement catalog with unlocked state
  const earnedMap = new Map((user.achievements || []).map(a => [a.id, a]));
  const allAchievements = Object.entries(ACHIEVEMENTS).map(([id, def]) => {
    const earned = earnedMap.get(id);
    return {
      id,
      name: def.name,
      unlocked: !!earned,
      unlockedAt: earned ? earned.unlockedAt : null
    };
  });

  return successResponse(res, {
    profile: {
      id: user._id.toString(),
      name: user.name,
      email: user.email,
      profilePicture: user.profilePicture,
      bio: user.bio,
      jobTitle: user.jobTitle,
      company: user.company,
      friendsCount: (user.friends || []).length,
      pendingRequestsCount: (user.friendRequests || []).length,
      friendStatus: 'self',
      meetingsOrganized,
      meetingsAttended,
      notesAdded,
      tasksCompleted,
      achievements: allAchievements,
      createdAt: user.createdAt
    }
  }, 'My profile');
});

// PUT /api/users/me/profile
const updateMyProfile = asyncHandler(async (req, res) => {
  const ALLOWED_FIELDS = ['name', 'bio', 'jobTitle', 'company', 'profilePicture'];
  const updates = {};

  for (const field of ALLOWED_FIELDS) {
    if (req.body[field] !== undefined) {
      updates[field] = req.body[field];
    }
  }

  if (Object.keys(updates).length === 0) {
    return errorResponse(res, 'No valid fields to update', 400);
  }

  const updated = await User.findByIdAndUpdate(
    req.userId,
    { $set: updates },
    { new: true, runValidators: true }
  ).select('_id name email profilePicture bio jobTitle company');

  if (!updated) return errorResponse(res, 'User not found', 404);

  return successResponse(res, {
    profile: {
      id: updated._id.toString(),
      name: updated.name,
      email: updated.email,
      profilePicture: updated.profilePicture || null,
      bio: updated.bio || '',
      jobTitle: updated.jobTitle || '',
      company: updated.company || ''
    }
  }, 'Profile updated');
});

const uploadAvatar = async (req, res) => {
  try {
    debugLog('[uploadAvatar] content-type:', req.headers['content-type']);
    debugLog('[uploadAvatar] req.file:', req.file);

    if (!req.file) {
      return res.status(400).json({ message: 'Aucun fichier fourni' });
    }

    const userId = req.userId;
    const avatarUrl = `/uploads/avatars/${req.file.filename}`;
    await User.findByIdAndUpdate(userId, { profilePicture: avatarUrl });

    return successResponse(res, { avatarUrl }, 'Avatar mis à jour');
  } catch (err) {
    return res.status(500).json({ message: err.message });
  }
};

const deleteAvatar = async (req, res) => {
  try {
    const userId = req.userId;
    const user = await User.findById(userId).select('profilePicture');

    if (!user) {
      return res.status(404).json({ message: 'Utilisateur non trouvé' });
    }

    if (user.profilePicture && user.profilePicture.startsWith('/uploads/')) {
      const fs = require('fs');
      const path = require('path');
      const relativePath = user.profilePicture.replace(/^\/+/, '');
      const filePath = path.join(__dirname, '../../', relativePath);

      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
      }
    }

    await User.findByIdAndUpdate(userId, { profilePicture: null });
    return successResponse(res, null, 'Photo supprimée');
  } catch (err) {
    return res.status(500).json({ message: err.message });
  }
};

// GET /api/users/me/friend-requests
const getPendingFriendRequests = asyncHandler(async (req, res) => {
  const user = await User.findById(req.userId)
    .populate('friendRequests.from', '_id name email profilePicture jobTitle');

  if (!user) return errorResponse(res, 'User not found', 404);

  const pending = (user.friendRequests || [])
    .map(r => ({
      from: {
        id: r.from._id.toString(),
        name: r.from.name,
        email: r.from.email,
        profilePicture: r.from.profilePicture,
        jobTitle: r.from.jobTitle
      },
      createdAt: r.createdAt
    }));

  return successResponse(res, { requests: pending }, 'Pending friend requests');
});

// GET /api/users/friend-requests/sent
const getSentFriendRequests = asyncHandler(async (req, res) => {
  const currentUserId = req.userId.toString();

  const users = await User.find({
    friendRequests: {
      $elemMatch: { from: req.userId }
    }
  }).select('_id name email profilePicture jobTitle friendRequests');

  const requests = users.map(user => {
    const pendingRequest = (user.friendRequests || []).find(
      r => r.from.toString() === currentUserId
    );
    return {
      to: {
        id: user._id.toString(),
        name: user.name,
        email: user.email,
        profilePicture: user.profilePicture,
        jobTitle: user.jobTitle
      },
      createdAt: pendingRequest?.createdAt || null
    };
  });

  return successResponse(res, { requests }, 'Sent friend requests');
});

module.exports = {
  searchUsers,
  sendFriendRequest,
  acceptFriendRequest,
  rejectOrCancelFriendRequest,
  removeFriend,
  getMyFriends,
  getPublicProfile,
  getMyProfile,
  getMyStats,
  updateMyProfile,
  getPendingFriendRequests,
  getSentFriendRequests,
  uploadAvatar,
  deleteAvatar
};
