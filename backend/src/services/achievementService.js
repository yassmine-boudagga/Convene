'use strict';
const User = require('../models/User');
const Meeting = require('../models/Meeting');
const Task = require('../models/Task');
const MeetingAIResult = require('../models/MeetingAIResult');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

const TARGETS = {
  organizer: 10, punctual: 10, collaborator: 20, bilingual: 2, marathon: 1, efficient: 10
};

function getCurrentMonthStart() {
  const now = new Date();
  return new Date(now.getFullYear(), now.getMonth(), 1);
}

function toIdString(value) {
  return value && value.toString ? value.toString() : String(value || '');
}

async function countPunctualMeetingsThisMonth(userId, monthStart) {
  const userIdStr = toIdString(userId);

  const meetings = await Meeting.find({
    status: { $in: ['ongoing', 'finished'] },
    startTime: { $gte: monthStart },
    'joinedParticipants.userId': userId
  }).select('startTime joinedParticipants');

  let punctual = 0;
  meetings.forEach((meeting) => {
    const participant = (meeting.joinedParticipants || []).find(
      (p) => p.userId && toIdString(p.userId) === userIdStr
    );

    if (!participant?.joinedAt) return;
    const maxOnTime = new Date(
      new Date(meeting.startTime).getTime() + 5 * 60000
    );
    if (new Date(participant.joinedAt) <= maxOnTime) {
      punctual += 1;
    }
  });

  return punctual;
}

async function countCollaboratorNotesThisMonth(userId, monthStart) {
  const userIdStr = toIdString(userId);
  const meetings = await Meeting.find({
    'notes.userId': userId,
    'notes.timestamp': { $gte: monthStart }
  }).select('notes');

  return meetings.reduce((sum, meeting) => {
    const notes = (meeting.notes || []).filter((note) => {
      if (!note.userId || toIdString(note.userId) !== userIdStr) return false;
      if (!note.timestamp) return false;
      return new Date(note.timestamp) >= monthStart;
    });
    return sum + notes.length;
  }, 0);
}

async function countBilingualLanguagesThisMonth(userId, monthStart) {
  const meetings = await Meeting.find({
    createdAt: { $gte: monthStart },
    $or: [{ createdBy: userId }, { attendedBy: userId }]
  }).select('_id');

  const meetingIds = meetings.map((m) => m._id);
  if (meetingIds.length === 0) return 0;

  const aiResults = await MeetingAIResult.find({
    meetingId: { $in: meetingIds },
    'transcript.language': { $exists: true, $ne: null }
  }).select('transcript.language');

  const langs = new Set(
    aiResults.map((result) => result.transcript?.language).filter(Boolean)
  );

  return langs.size;
}

async function countMarathonMeetingsThisMonth(userId, monthStart) {
  return Meeting.countDocuments({
    createdAt: { $gte: monthStart },
    duration: { $gte: 60 },
    $or: [{ createdBy: userId }, { attendedBy: userId }]
  });
}

async function countEfficientTasksThisMonth(userId, monthStart) {
  const completedTasks = await Task.find({
    assigneeId: userId,
    status: 'completed',
    $or: [
      { completedAt: { $gte: monthStart } },
      { completedAt: null, updatedAt: { $gte: monthStart } }
    ]
  }).select('dueDate completedAt updatedAt');

  return completedTasks.filter((task) => {
    const deadline = task.dueDate;
    if (!deadline) return false;

    const completedAt = task.completedAt || task.updatedAt;
    if (!completedAt) return false;

    return new Date(completedAt) <= new Date(deadline);
  }).length;
}

async function getProgress(userId) {
  const monthStart = getCurrentMonthStart();

  const [organizer, punctual, collaborator, bilingual, marathon, efficient] = await Promise.all([
    Meeting.countDocuments({ createdBy: userId, createdAt: { $gte: monthStart } }),
    countPunctualMeetingsThisMonth(userId, monthStart),
    countCollaboratorNotesThisMonth(userId, monthStart),
    countBilingualLanguagesThisMonth(userId, monthStart),
    countMarathonMeetingsThisMonth(userId, monthStart),
    countEfficientTasksThisMonth(userId, monthStart)
  ]);

  return { organizer, punctual, collaborator, bilingual, marathon, efficient };
}

const ACHIEVEMENTS = {
  organizer: {
    name: 'Organisateur',
    check: async (userId) => {
      const progress = await getProgress(userId);
      return progress.organizer >= TARGETS.organizer;
    }
  },
  punctual: {
    name: 'Ponctuel',
    check: async (userId) => {
      const progress = await getProgress(userId);
      return progress.punctual >= TARGETS.punctual;
    }
  },
  collaborator: {
    name: 'Collaborateur',
    check: async (userId) => {
      const progress = await getProgress(userId);
      return progress.collaborator >= TARGETS.collaborator;
    }
  },
  bilingual: {
    name: 'Bilingue',
    check: async (userId) => {
      const progress = await getProgress(userId);
      return progress.bilingual >= TARGETS.bilingual;
    }
  },
  marathon: {
    name: 'Marathon',
    check: async (userId) => {
      const progress = await getProgress(userId);
      return progress.marathon >= TARGETS.marathon;
    }
  },
  efficient: {
    name: 'Efficace',
    check: async (userId) => {
      const progress = await getProgress(userId);
      return progress.efficient >= TARGETS.efficient;
    }
  }
};

//Vérifie et attribue les achievements non encore débloqués pour un user.
async function checkAndGrantAchievements(userId) {
  try {
    const user = await User.findById(userId).select('achievements');
    if (!user) return [];

    const now = new Date();
    const currentMonth = now.getMonth() + 1;
    const currentYear = now.getFullYear();

    const existingIds = (user.achievements || [])
      .filter(a => a.month === currentMonth && a.year === currentYear)
      .map(a => a.id);

    const progress = await getProgress(userId);
    const newAchievements = [];

    for (const [id, def] of Object.entries(ACHIEVEMENTS)) {
      if (existingIds.includes(id)) continue;

      const current = progress[id] || 0;
      const target = TARGETS[id] || Number.MAX_SAFE_INTEGER;
      if (current >= target) {
        newAchievements.push({
          id,
          month: currentMonth,
          year: currentYear
        });
      }
    }
    if (newAchievements.length > 0) {
      await User.findByIdAndUpdate(userId, {
        $push: { achievements: { $each: newAchievements } }
      });
      debugLog(`[Achievement] ${newAchievements.length} nouveau(x) badge(s) pour userId=${userId}:`,
        newAchievements.map(a => a.id).join(', '));
    }
    return newAchievements;
  } catch (err) {
    errorLog('[Achievement] checkAndGrantAchievements erreur:', err.message);
    return [];
  }
}
module.exports = { checkAndGrantAchievements, getProgress, ACHIEVEMENTS };
