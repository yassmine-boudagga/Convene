const express = require('express');
const router = express.Router();
const authController = require('../controllers/authController');
const { authenticate } = require('../middleware/authMiddleware');
const { validate } = require('../middleware/validationMiddleware');

router.post('/register', validate('register'), authController.register);
router.post('/login', validate('login'), authController.login);
router.get('/me', authenticate, authController.getMe);
router.post('/logout', authenticate, authController.logout);
router.post('/refresh', authController.refreshToken);
router.post('/forgot-password', authController.forgotPassword);
router.post('/reset-password', authController.resetPassword);

module.exports = router;
