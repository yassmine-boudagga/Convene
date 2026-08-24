//IMPORTANT: /me/* routes declared BEFORE /:id/* to avoid Express collision.

const express = require('express');
const router = express.Router();
const userController = require('../controllers/userController');
const { authenticate } = require('../middleware/authMiddleware');
const upload = require('../middleware/upload');

router.use(authenticate);

router.get('/search', userController.searchUsers); 
router.get('/me/profile', userController.getMyProfile);

router.put('/me/profile', userController.updateMyProfile);

router.get('/me/friends', userController.getMyFriends);

router.get('/me/friend-requests', userController.getPendingFriendRequests);

router.get('/friend-requests/sent', userController.getSentFriendRequests);

router.get('/me/stats', userController.getMyStats);

router.post('/me/avatar', upload.single('avatar'), userController.uploadAvatar);
router.delete('/me/avatar', userController.deleteAvatar);

router.get('/:id/profile', userController.getPublicProfile);

router.post('/:id/friend-request', userController.sendFriendRequest);

router.post('/:id/friend-request/accept', userController.acceptFriendRequest);

router.delete('/:id/friend-request', userController.rejectOrCancelFriendRequest);

router.delete('/:id/friend', userController.removeFriend);

module.exports = router;
