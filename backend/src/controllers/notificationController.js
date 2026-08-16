const Notification = require('../models/Notification');
const User = require('../models/User');
const { asyncHandler, successResponse, errorResponse } = require('../middleware/errorMiddleware');

const getNotifications = asyncHandler(async (req, res) => {
  const userId = req.userId;
  const { unread_only = 'false', limit = 30, page = 1 } = req.query;
  const parsedLimit = Math.min(parseInt(limit) || 30, 100); // cap à 100
  const parsedPage  = Math.max(parseInt(page)  || 1,  1);
  const skip        = (parsedPage - 1) * parsedLimit;

  const query = { userId };
  if (unread_only === 'true') query.isRead = false;

  const [notifications, total] = await Promise.all([
    Notification.find(query)
      .sort({ createdAt: -1 })
      .skip(skip)
      .limit(parsedLimit),
    Notification.countDocuments(query),
  ]);
  const unreadCount = await Notification.countDocuments({ userId, isRead: false });
  const hasMore     = skip + notifications.length < total;

  let enrichedNotifications = notifications;
  const hasFriendRequests = notifications.some((notif) => notif.type === 'friend_request');

  if (hasFriendRequests) {
    const currentUser = await User.findById(userId).select('friends friendRequests').lean();
    const friendIds = new Set((currentUser?.friends || []).map((id) => id.toString()));
    const pendingRequestFromIds = new Set(
      (currentUser?.friendRequests || [])
        .filter((request) => request?.status === 'pending' && request?.from)
        .map((request) => request.from.toString())
    );

    enrichedNotifications = notifications.map((notif) => {
      const plain = notif.toObject();

      if (plain.type !== 'friend_request') {
        return plain;
      }

      const fromUserId = plain.data?.fromUserId?.toString();
      if (!fromUserId) {
        plain.actionTaken = null;
        return plain;
      }

      if (friendIds.has(fromUserId)) {
        plain.actionTaken = 'accepted';
      } else if (!pendingRequestFromIds.has(fromUserId)) {
        plain.actionTaken = 'rejected';
      } else {
        plain.actionTaken = null;
      }

      return plain;
    });
  }

  const finalNotifications = enrichedNotifications.map((n) => {
    if (n && typeof n.toJSON === 'function') return n.toJSON();
    if (n && !n.id && n._id) {
      const copy = { ...n, id: n._id.toString() };
      delete copy._id;
      delete copy.__v;
      return copy;
    }
    return n;
  });

  const ids = finalNotifications
    .filter((n) => n && !n.isDelivered)
    .map((n) => (n.id != null ? String(n.id) : n._id && n._id.toString ? n._id.toString() : null))
    .filter(Boolean);
  if (ids.length) await Notification.updateMany({ _id: { $in: ids } }, { $set: { isDelivered: true, deliveredAt: new Date() } });

  return successResponse(res, { notifications: finalNotifications, unreadCount, hasMore, page: parsedPage, total }, 'Notifications retrieved');
});

const getUnreadCount = asyncHandler(async (req, res) => {
  const count = await Notification.countDocuments({ userId: req.userId, isRead: false });
  return successResponse(res, { count }, 'Count retrieved');
});

const markAsRead = asyncHandler(async (req, res) => {
  const n = await Notification.findOneAndUpdate(
    { _id: req.params.id, userId: req.userId },
    { $set: { isRead: true, readAt: new Date() } },
    { new: true }
  );
  if (!n) return errorResponse(res, 'Notification introuvable', 404);
  return successResponse(res, { notification: n }, 'Marked as read');
});

const markAllAsRead = asyncHandler(async (req, res) => {
  await Notification.updateMany({ userId: req.userId, isRead: false }, { $set: { isRead: true, readAt: new Date() } });
  return successResponse(res, null, 'All marked as read');
});

const deleteNotification = asyncHandler(async (req, res) => {
  await Notification.deleteOne({ _id: req.params.id, userId: req.userId });
  return successResponse(res, null, 'Deleted');
});

module.exports = { getNotifications, getUnreadCount, markAsRead, markAllAsRead, deleteNotification };