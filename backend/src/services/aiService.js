const axios = require('axios');
const Meeting = require('../models/Meeting');
const User = require('../models/User');
const AI_SERVICE_URL = process.env.AI_SERVICE_URL;
const BACKEND_URL = process.env.SERVER_URL || `http://localhost:${process.env.PORT}`;
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

const RETRY_DELAYS_MS = [2000, 5000, 10000];
function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
function buildAiProcessUrl() {
  // Force IPv4 localhost
  return `${AI_SERVICE_URL.replace('localhost', '127.0.0.1')}/process`;
}
function buildCallbackUrl(meetingId) {
  const backendBaseUrl = BACKEND_URL.replace('localhost', '127.0.0.1').replace(/\/$/, '');
  return `${backendBaseUrl}/api/meetings/${meetingId}/ai-result`;
}
function sanitizeNotes(notes = []) {
  return (notes || []).map((n) => ({
    userId: n?.userId ? String(n.userId) : 'unknown',
    userName: n?.userName ? String(n.userName) : 'Participant',
    content: n?.content ? String(n.content) : '',
    timestamp: n?.timestamp || null
  })).filter((n) => n.content.length > 0);
}
function normalizeEmailList(emails = []) {
  return [...new Set((emails || [])
    .map((email) => String(email || '').trim().toLowerCase())
    .filter(Boolean))];
}
function mergeParticipantEmails(guestEmails = [], hostEmail = null) {
  const normalizedGuests = normalizeEmailList(guestEmails);
  const normalizedHost = hostEmail ? String(hostEmail).trim().toLowerCase() : null;
  if (!normalizedHost) {
    return normalizedGuests;
  }
  return [normalizedHost, ...normalizedGuests.filter((email) => email !== normalizedHost)];
}

async function triggerPipeline(meetingId, audioLocalPath, notes = [], meetingMeta = {}) {
  const callbackUrl = buildCallbackUrl(meetingId);
  const processUrl = buildAiProcessUrl();
  try {
      const hostUserId = meetingMeta?.createdBy || (await Meeting.findById(meetingId).select('createdBy'))?.createdBy;
      if (hostUserId) {
        const hostUser = await User.findById(hostUserId).select('email');
        hostEmail = hostUser?.email ? String(hostUser.email).trim().toLowerCase() : null;
      }

    const allParticipantEmails = mergeParticipantEmails(meetingMeta?.participants, hostEmail);
    const sanitizedNotes = sanitizeNotes(notes);
    const normalizedMeta = {
      ...meetingMeta,
      participants: allParticipantEmails
    };

    const payload = {
      meetingId: meetingId.toString(),
      meetingTitle: meetingMeta?.title || 'Untitled meeting',
      audioPath: audioLocalPath,
      notes: sanitizedNotes,
      participants: allParticipantEmails,
      meetingDurationMinutes: Number.isFinite(Number(meetingMeta?.duration)) ? Number(meetingMeta.duration) : null,
      meta: normalizedMeta,
      callbackUrl
    };

    const headers = {};
    if (process.env.AI_CALLBACK_SECRET) {
      headers['x-ai-callback-secret'] = process.env.AI_CALLBACK_SECRET;
    }
    let response = null;
    let lastError = null;

    for (let attempt = 1; attempt <= 3; attempt += 1) {
      try {
        debugLog(`[AI] Trigger attempt ${attempt}/3 for meeting ${meetingId}`);
        response = await axios.post(processUrl, payload, {
          timeout: 5 * 60 * 1000,
          headers
        });
        break;
      } catch (err) {
        lastError = err;
        errorLog(`[AI] Trigger attempt ${attempt}/3 failed for meeting ${meetingId}: ${err.message}`);
        if (attempt < 3) {
          await sleep(RETRY_DELAYS_MS[attempt - 1]);
        }
      }
    }

    if (!response) {
      throw lastError || new Error('AI trigger failed after retries');
    }

    await Meeting.updateOne({ _id: meetingId }, { $set: { aiStatus: 'processing' } });

    debugLog(`[AI] Pipeline trigger OK for meeting ${meetingId} (status=${response.status})`);
    return response.data;
  } catch (error) {
    errorLog(`[AI] Pipeline trigger failed for meeting ${meetingId}: ${error.message}`);
    try {
      await Meeting.updateOne({ _id: meetingId }, { $set: { aiStatus: 'failed' } });
    } catch (markErr) {
      errorLog(`[AI] Failed to mark aiStatus=failed: ${markErr.message}`);
    }
    throw error;
  }
}
module.exports = {
  triggerPipeline
};
