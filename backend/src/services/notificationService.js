const Notification = require('../models/Notification');
const User = require('../models/User');
const isDevelopment = process.env.NODE_ENV !== 'production';

const errorLog = (...args) => {
  console.error(...args);
};

let wsManager = null;
const setWebSocketManager = (manager) => { wsManager = manager; };

const sendNotification = async ({ userId, type, title, message, data = {} }) => {
  try {
    if (data.meetingId && !data.actionUrl) {
      data.actionUrl = `/meetings/${data.meetingId}`;
    }
    const notif = await Notification.create({ userId, type, title, message, data, isDelivered: false });

    if (wsManager) {
      const delivered = wsManager.sendToUser(userId.toString(), {
        event: 'notification',
        notification: {
          id: notif._id.toString(),
          type,
          title,
          message,
          data,
          createdAt: notif.createdAt,
          payload: {
            meetingId: data.meetingId || null,
            meetingTitle: data.meetingTitle || null,
            startTime: data.startTime || null,
            actionUrl: data.actionUrl || null
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
    return notif;
  } catch (err) { errorLog('[Notif] sendNotification error:', err.message); }
};

const sendToMany = async (userIds, notifData) =>
  Promise.allSettled(userIds.map(uid => sendNotification({ userId: uid, ...notifData })));

// API publique
const notifyMeetingCreated = async (meeting, creatorName) => {
  try {
    const users = await User.find({ email: { $in: meeting.participants || [] } }).select('_id');
    if (!users.length) return;
    await sendToMany(users.map(u => u._id), {
      type: 'meeting_created', title: 'Nouvelle réunion',
      message: `${creatorName} vous invite à "${meeting.title}"`,
      data: {
        meetingId: meeting._id.toString(),
        meetingTitle: meeting.title,
        startTime: meeting.startTime,
        actionUrl: `/meetings/${meeting._id}`,
        organizerName: creatorName
      }
    });
  } catch (err) { errorLog('[Notif] notifyMeetingCreated:', err.message); }
};

const notifyMeetingStartingSoon = async (meeting) => {
  try {
    const emails = [...(meeting.participants || [])];
    const users = await User.find({
      $or: [{ _id: meeting.createdBy }, { email: { $in: emails } }]
    }).select('_id');
    await sendToMany(users.map(u => u._id), {
      type: 'meeting_starting', title: 'Réunion dans 5 minutes',
      message: `"${meeting.title}" commence bientôt`,
      data: {
        meetingId: meeting._id.toString(),
        meetingTitle: meeting.title,
        startTime: meeting.startTime,
        actionUrl: `/meetings/${meeting._id}`
      }
    });
  } catch (err) { errorLog('[Notif] notifyMeetingStartingSoon:', err.message); }
};

const notifyRecordingReady = async (meeting) => {
  try {
    const emails = [...(meeting.participants || [])];
    const users = await User.find({
      $or: [{ _id: meeting.createdBy }, { email: { $in: emails } }]
    }).select('_id');
    await sendToMany(users.map(u => u._id), {
      type: 'recording_ready', title: 'Enregistrement disponible',
      message: `L'enregistrement de "${meeting.title}" est prêt`,
      data: {
        meetingId: meeting._id.toString(),
        meetingTitle: meeting.title,
        actionUrl: `/meetings/${meeting._id}`
      }
    });
  } catch (err) { errorLog('[Notif] notifyRecordingReady:', err.message); }
};

const notifyMeetingCancelled = async (meeting, cancellerName) => {
  try {
    const users = await User.find({ email: { $in: meeting.participants || [] } }).select('_id');
    await sendToMany(users.map(u => u._id), {
      type: 'meeting_cancelled', title: 'Réunion annulée',
      message: `"${meeting.title}" a été annulée par ${cancellerName}`,
      data: {
        meetingId: meeting._id.toString(),
        meetingTitle: meeting.title,
        actionUrl: `/meetings/${meeting._id}`,
        organizerName: cancellerName
      }
    });
    if (wsManager) {
      wsManager.broadcastToMeeting(meeting._id.toString(), {
        event: 'meeting_status_changed',
        meetingId: meeting._id.toString(),
        status: 'cancelled'
      });
    }
  } catch (err) { errorLog('[Notif] notifyMeetingCancelled:', err.message); }
};

const notifyMeetingUpdated = async (meeting, updaterName) => {
  try {
    const users = await User.find({ email: { $in: meeting.participants || [] } }).select('_id');
    await sendToMany(users.map(u => u._id), {
      type: 'meeting_updated', title: 'Réunion modifiée',
      message: `"${meeting.title}" a été modifiée par ${updaterName}`,
      data: {
        meetingId: meeting._id.toString(),
        meetingTitle: meeting.title,
        actionUrl: `/meetings/${meeting._id}`,
        organizerName: updaterName
      }
    });
    if (wsManager) {
      wsManager.broadcastToMeeting(meeting._id.toString(), {
        event: 'meeting_status_changed',
        meetingId: meeting._id.toString(),
        status: meeting.status
      });
    }
  } catch (err) { errorLog('[Notif] notifyMeetingUpdated:', err.message); }
};

module.exports = {
  setWebSocketManager, sendNotification, sendToMany,
  notifyMeetingCreated, notifyMeetingStartingSoon,
  notifyRecordingReady,
  notifyMeetingCancelled, notifyMeetingUpdated,
};