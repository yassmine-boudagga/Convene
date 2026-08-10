
const Meeting = require('../models/Meeting');
const { errorResponse } = require('./errorMiddleware');
const isDevelopment = process.env.NODE_ENV !== 'production';

const warnLog = (...args) => {
  console.warn(...args);
};

// Charge la réunion par ID et l'attache à req.meeting
const loadMeeting = async (req, res, next) => {
  try {
    const { id } = req.params;
    if (!id) {
      return errorResponse(res, 'Meeting ID requis', 400);
    }

    // Charge avec populate partiel puis hydrate joinedParticipants seulement si nécessaire
    let meeting;
    try {
      meeting = await Meeting.findById(id)
        .populate('createdBy', 'name email')
        .populate({ path: 'notes.userId', select: 'name email', strictPopulate: false });

      const shouldPopulateJoinedParticipants = (meeting?.joinedParticipants || []).some((p) => {
        if (!p?.userId) return false;
        if (typeof p.userId === 'string') return true;
        const hasHydratedFields = typeof p.userId === 'object' && (p.userId.name || p.userId.email);
        return !hasHydratedFields;
      });

      if (shouldPopulateJoinedParticipants) {
        await meeting.populate({ path: 'joinedParticipants.userId', select: 'name email', strictPopulate: false });
      }
    } catch (popErr) {
      warnLog(`[loadMeeting] Populate partiel pour meeting ${id}: ${popErr.message}`);
      meeting = await Meeting.findById(id);
    }

    if (!meeting) {
      return errorResponse(res, 'Réunion introuvable', 404);
    }

    // Filtrer les joinedParticipants avec userId null
    if (meeting.joinedParticipants && meeting.joinedParticipants.length > 0) {
      meeting.joinedParticipants = meeting.joinedParticipants.filter(p => p.userId != null);
    }

    req.meeting = meeting;
    next();
  } catch (error) {
    if (error.name === 'CastError') {
      return errorResponse(res, 'ID de réunion invalide', 400);
    }
    warnLog('[loadMeeting] Erreur:', error.message);
    return next({ status: 500, message: 'Erreur serveur lors du chargement de la réunion' });
  }
};

const checkMeetingStatus = (req, res, next) => {
  const { meeting } = req;

  if (meeting.status === 'finished') {
    return errorResponse(res, 'Cette réunion est terminée', 400);
  }
  if (meeting.status === 'cancelled') {
    return errorResponse(res, 'Cette réunion a été annulée', 400);
  }
  if (meeting.status === 'ongoing') {
    return next();
  }
  return errorResponse(res, 'La réunion n\'est pas encore disponible', 400);
};
module.exports = {
  loadMeeting,
  checkMeetingStatus
};