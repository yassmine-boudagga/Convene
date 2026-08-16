const express = require('express');
const router  = express.Router();
const ctrl    = require('../controllers/notificationController');
const { authenticate } = require('../middleware/authMiddleware');

router.use(authenticate);
router.get('/',             ctrl.getNotifications);
router.get('/unread-count', ctrl.getUnreadCount);
router.put('/read-all',     ctrl.markAllAsRead);
router.put('/:id/read',     ctrl.markAsRead);
router.delete('/:id',       ctrl.deleteNotification);

module.exports = router;