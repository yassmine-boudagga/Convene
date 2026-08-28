const express = require('express');
const router = express.Router();
const { authenticate } = require('../middleware/authMiddleware');
const { requireAdmin } = require('./adminMiddleware');
const adminController = require('./adminController');

router.use(authenticate, requireAdmin);

router.get('/stats', adminController.getStats);
router.get('/stats/period', adminController.getStatsByPeriod);

router.get('/users', adminController.getUsers);
router.get('/users/:id', adminController.getUserById);
router.put('/users/:id', adminController.updateUser);
router.delete('/users/:id', adminController.deleteUser);

router.get('/meetings', adminController.getMeetings);
router.get('/meetings/:id/detail', adminController.getMeetingDetail);
router.put('/meetings/:id', adminController.updateMeeting);
router.delete('/meetings/:id', adminController.deleteMeetingAdmin);
router.post('/meetings/:id/force-end', adminController.forceEndMeeting);

router.get('/recordings', adminController.getRecordings);
router.delete('/recordings/:meetingId', adminController.deleteRecording);

router.get('/tasks', adminController.getTasks);
router.put('/tasks/:id', adminController.updateTask);
router.delete('/tasks/:id', adminController.deleteTask);

router.get('/notifications', adminController.getNotifications);
router.delete('/notifications/:id', adminController.deleteNotification);

router.get('/ai-results', adminController.getAIResults);
router.delete('/ai-results/:meetingId', adminController.deleteAIResult);

router.post('/broadcast', adminController.broadcastGlobal);
router.get('/maintenance/failed-ai-meetings', adminController.getFailedAIMeetings);
router.post('/maintenance/reset-ai-meeting/:meetingId', adminController.resetSingleAIMeeting);
router.delete('/maintenance/reset-ai-meeting/:meetingId/cancel', adminController.cancelScheduledAIMeeting);
router.get('/system/status', adminController.getSystemStatus);

module.exports = router;
