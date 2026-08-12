const express = require('express');
const router = express.Router();
const { authenticate } = require('../middleware/authMiddleware');
const {
  getMyTasks,
  getAllRelatedTasks,
  createTask,
  completeTask,
} = require('../controllers/taskController');

router.get('/me', authenticate, getMyTasks);
router.get('/related', authenticate, getAllRelatedTasks);
router.post('/', authenticate, createTask);
router.patch('/:id/complete', authenticate, completeTask);

module.exports = router;
