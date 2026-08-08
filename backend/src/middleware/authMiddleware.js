
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const isDevelopment = process.env.NODE_ENV !== 'production';

const errorLog = (...args) => {
  console.error(...args);
};

const authenticate = async (req, res, next) => {
  try {
    // Get token from header
    const authHeader = req.headers.authorization;
    if (!authHeader) {
      return res.status(401).json({
        success: false,
        message: 'No authorization token provided'
      });
    }
    // Check if it's a Bearer token
    const parts = authHeader.split(' ');
    if (parts.length !== 2 || parts[0] !== 'Bearer') {
      return res.status(401).json({
        success: false,
        message: 'Invalid authorization header format. Use: Bearer <token>'
      });
    }

    const token = parts[1];
    // Verify token
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    const user = await User.findById(decoded.id);
    if (!user) {
      return res.status(401).json({
        success: false,
        message: 'User not found'
      });
    }
    // Attach user to request
    req.user = user;
    req.userId = user._id;
    next();
  } catch (error) {
    // Handle specific JWT errors
    if (error.name === 'JsonWebTokenError') {
      return res.status(401).json({
        success: false,
        message: 'Invalid token'
      });
    }
    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({
        success: false,
        message: 'Token expired'
      });
    }

    errorLog('Authentication error:', error);
    return res.status(500).json({
      success: false,
      message: 'Authentication failed'
    });
  }
};

module.exports = {
  authenticate
};
