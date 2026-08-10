const express = require('express');
const router = express.Router();
const meetingController = require('../controllers/meetingController');
const recordingController = require('../controllers/recordingController');
const { authenticate } = require('../middleware/authMiddleware');
const { validate } = require('../middleware/validationMiddleware');
const { loadMeeting, checkMeetingStatus } = require('../middleware/meetingMiddleware');

const isAdmin = (req, res, next) => {
	const userRole = String(req.user?.role || '').toLowerCase();
	const currentEmail = String(req.user?.email || '').toLowerCase();
	const allowedAdminEmails = String(process.env.ADMIN_EMAILS || '') 
		.split(',')
		.map((email) => email.trim().toLowerCase())
		.filter(Boolean);
	if (userRole === 'admin' || allowedAdminEmails.includes(currentEmail)) {
		return next();
	}
	return res.status(403).json({ success: false, message: 'Accès admin requis' });
};

router.post('/:id/ai-result', meetingController.receiveAIResult);

// All routes require authentication
router.use(authenticate);

// CRUD
router.post('/', validate('createMeeting'), meetingController.createMeeting);
router.get('/', meetingController.getMeetings);
router.get('/:id', loadMeeting, meetingController.getMeetingById);
router.put('/:id', loadMeeting, validate('updateMeeting'), meetingController.updateMeeting);
router.post('/:id/cancel', loadMeeting, meetingController.cancelMeeting);

router.post('/:id/join', loadMeeting, checkMeetingStatus, meetingController.joinMeeting);
router.post('/:id/join/physical', loadMeeting, meetingController.joinPhysicalMeeting);
router.post('/:id/leave', loadMeeting, meetingController.leaveMeeting);
router.post('/:id/leave/physical', loadMeeting, meetingController.leavePhysicalMeeting);

router.post('/:id/add-note', loadMeeting, validate('addNote'), meetingController.addNote);
router.get('/:id/notes', loadMeeting, meetingController.getNotes);

router.post('/:id/token', loadMeeting, meetingController.getToken);

router.post('/:id/stop-recording', loadMeeting, recordingController.stopRecording);
router.post('/:id/recording/upload', loadMeeting, recordingController.uploadPhysicalRecording);
router.get('/:id/recording-status', loadMeeting, recordingController.getRecordingStatus);
router.get('/:meetingId/recording', authenticate, meetingController.getRecordingInfo);
router.get('/:id/ai-result', loadMeeting, meetingController.getMeetingAIResult);
router.get('/:id/tasks', loadMeeting, meetingController.getMeetingTasks);
router.get('/:id/ai-status', loadMeeting, meetingController.getAIStatus);

router.post('/:id/heartbeat', meetingController.heartbeat);

module.exports = router;