const jwt = require('jsonwebtoken');
const crypto = require('crypto');
const bcryptjs = require('bcryptjs');
const User = require('../models/User');
const RevokedRefreshToken = require('../models/RevokedRefreshToken');
const wsManager = require('../services/wsManager');
const { sendPasswordResetEmail } = require('../services/emailService');
const { asyncHandler, successResponse, errorResponse } = require('../middleware/errorMiddleware');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

const GRACE_WINDOW_MS = 5000;
const generateToken = (userId) => {
  return jwt.sign(
    { id: userId },process.env.JWT_SECRET,{ expiresIn: process.env.JWT_EXPIRES_IN  }
  );
};

const generateRefreshToken = (userId) => {
  return jwt.sign(
    {
      id: userId,
      type: 'refresh',
      jti: crypto.randomBytes(16).toString('hex'),
    },
    process.env.JWT_SECRET,
    { expiresIn: process.env.JWT_REFRESH_EXPIRES_IN}
  );
};

const hashToken = (token) => crypto.createHash('sha256').update(String(token || '')).digest('hex');

const getTokenExpiryDate = (payload) => {
  if (payload?.exp) {
    return new Date(payload.exp * 1000);
  }
  return new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
};

function mapUserForAuthRest(userDoc) {
  if (!userDoc) return null;
  const u = userDoc.toObject ? userDoc.toObject() : userDoc;
  const idRaw = u._id ?? u.id;
  return {
    id: idRaw != null ? String(idRaw) : '',
    name: u.name,
    email: u.email,
    profilePicture: u.profilePicture || null,
    bio: u.bio || '',
    jobTitle: u.jobTitle || '',
    company: u.company || ''
  };
}

const register = asyncHandler(async (req, res, next) => {
  const { name, email, password } = req.body;

  const existingUser = await User.findByEmail(email);
  if (existingUser) {
    return errorResponse(res, 'Email already registered', 400);
  }

  const user = await User.create({
    name,
    email,
    password
  });

  const token = generateToken(user._id);
  const refreshToken = generateRefreshToken(user._id);
  return successResponse(res, {
    user: mapUserForAuthRest(user),
    token,
    refreshToken
  }, 'User registered successfully', 201);
});

const login = asyncHandler(async (req, res, next) => {
  const { email, password } = req.body;

  const user = await User.findOne({ email: email.toLowerCase() }).select('+password');
  
  if (!user) {
    return errorResponse(res, 'Invalid credentials', 401);
  }

  const isMatch = await user.comparePassword(password);
  if (!isMatch) {
    return errorResponse(res, 'Invalid credentials', 401);
  }
  const token = generateToken(user._id);
  const refreshToken = generateRefreshToken(user._id);
  return successResponse(res, {
    user: mapUserForAuthRest(user),
    token,
    refreshToken
  }, 'Login successful');
});

const getMe = asyncHandler(async (req, res, next) => {
  const user = await User.findById(req.userId);

  if (!user) {
    return errorResponse(res, 'User not found', 404);
  }
  return successResponse(res, { user: mapUserForAuthRest(user) }, 'User retrieved successfully');
});

const logout = asyncHandler(async (req, res, next) => {
  const { refreshToken: providedRefreshToken } = req.body || {};
  if (providedRefreshToken) {
    try {
      const payload = jwt.verify(providedRefreshToken,process.env.JWT_SECRET);

      if (
        payload?.id && String(payload.id) === String(req.userId) && payload.type === 'refresh'
      ) {
        const tokenHash = hashToken(providedRefreshToken);
        await RevokedRefreshToken.findOneAndUpdate(
          { tokenHash },
          {
            $setOnInsert: {
              tokenHash,
              userId: req.userId,
              reason: 'logout',
              revokedAt: new Date(),
              expiresAt: getTokenExpiryDate(payload),
            }
          },
          { upsert: true, setDefaultsOnInsert: true }
        );
      }
    } catch (_err) {}
  }
  wsManager.disconnectUser(req.userId);
  return successResponse(res, null, 'Logout successful');
});


const refreshToken = asyncHandler(async (req, res, next) => {
  const { refreshToken: providedRefreshToken } = req.body || {};
  if (!providedRefreshToken) {
    return errorResponse(res, 'refreshToken is required', 400);
  }
  let payload;
  try {
    payload = jwt.verify(
      providedRefreshToken,
      process.env.JWT_SECRET
    );
  } catch (_error) {
    return errorResponse(res, 'Invalid or expired refresh token', 401);
  }
  if (!payload?.id) {
    return errorResponse(res, 'Invalid refresh token payload', 401);
  }

  if (payload.type && payload.type !== 'refresh') {
    return errorResponse(res, 'Invalid token type for refresh', 401);
  }
  const user = await User.findById(payload.id).select('_id');
  if (!user) {
    return errorResponse(res, 'User not found', 404);
  }

  const tokenHash = hashToken(providedRefreshToken);
  const now = new Date();
  const insertResult = await RevokedRefreshToken.updateOne(
    { tokenHash },
    {
      $setOnInsert: {
        tokenHash,
        userId: user._id,
        reason: 'rotated',
        revokedAt: now,
        expiresAt: getTokenExpiryDate(payload)
      }
    },
    { upsert: true }
  );
  if (insertResult.upsertedCount === 1) {
    const token = generateToken(user._id);
    const rotatedRefreshToken = generateRefreshToken(user._id);
    const newTokenHashValue = hashToken(rotatedRefreshToken);

    await RevokedRefreshToken.updateOne(
      { tokenHash },
      { $set: { newTokenHash: newTokenHashValue, rotatedAt: now } }
    );

    return successResponse(
      res,
      { token, refreshToken: rotatedRefreshToken },
      'Token refreshed successfully'
    );
  }

  const existing = await RevokedRefreshToken.findOne({ tokenHash });
  if (
    existing &&
    existing.reason === 'rotated' &&
    existing.rotatedAt &&
    (Date.now() - existing.rotatedAt.getTime()) < GRACE_WINDOW_MS
  ) {
    const token = generateToken(user._id);
    const rotatedRefreshToken = generateRefreshToken(user._id);
    return successResponse(
      res,
      { token, refreshToken: rotatedRefreshToken },
      'Token refreshed successfully'
    );
  }

  return errorResponse(res, 'Refresh token has been revoked', 401);
});


const forgotPassword = asyncHandler(async (req, res, next) => {
  const { email } = req.body;

  if (!email || !email.includes('@')) {
    return errorResponse(res, 'Email invalide', 400);
  }
  const user = await User.findOne({email: email.toLowerCase().trim()}).select('+resetToken +resetTokenExpiry');

  if (user) {
    const resetCode = Math.floor(100000 + Math.random() * 900000).toString();
    const hashedCode = crypto.createHash('sha256').update(resetCode).digest('hex');
    user.resetToken = hashedCode;
    user.resetTokenExpiry = new Date(Date.now() + 60 * 60 * 1000);
    await user.save();
    try {
      await sendPasswordResetEmail(user.email, resetCode);
    } catch (emailErr) {
      // If email fails revoke the token
      user.resetToken = null;
      user.resetTokenExpiry = null;
      await user.save();
      errorLog('[Auth] Erreur envoi email:', emailErr.message);
      return errorResponse(res, 'Erreur lors de l\'envoi de l\'email. Vérifiez votre configuration Gmail.', 500);
    }
  }
  return successResponse(res, null, 'Si cet email est associé à un compte Convene, vous recevrez un code de réinitialisation.');
});

const resetPassword = asyncHandler(async (req, res, next) => {
  const { email, code, newPassword } = req.body;
  if (!email || !code || !newPassword) {
    return errorResponse(res, 'Email, code et nouveau mot de passe sont requis', 400);
  }
  if (code.length !== 6 || !/^\d{6}$/.test(code)) {
    return errorResponse(res, 'Le code doit contenir exactement 6 chiffres', 400);
  }
  if (newPassword.length < 6) {
    return errorResponse(res, 'Le mot de passe doit contenir au moins 6 caractères', 400);
  }
  // Hash the received code to compare with stored hash
  const hashedCode = crypto.createHash('sha256').update(code.trim()).digest('hex');
  const user = await User.findOne({
    email: email.toLowerCase().trim(),
    resetToken: hashedCode,
    resetTokenExpiry: { $gt: new Date() }
  }).select('+resetToken +resetTokenExpiry');
  if (!user) {
    return errorResponse(res, 'Code invalide ou expiré. Veuillez faire une nouvelle demande.', 400);
  }
  // Hash new password with bcrypt
  const saltRounds = 12;
  const hashedPassword = await bcryptjs.hash(newPassword, saltRounds);
  // Update password and clear token (single use)
  await User.updateOne(
    { _id: user._id },
    {
      $set: {
        password: hashedPassword,
        resetToken: null,
        resetTokenExpiry: null
      }
    }
  );
  debugLog(`[Auth] Mot de passe réinitialisé: ${user.email}`);
  return successResponse(res, null, 'Mot de passe réinitialisé avec succès. Vous pouvez maintenant vous connecter.');
});

module.exports = {
  register,
  login,
  getMe,
  logout,
  refreshToken,
  forgotPassword,
  resetPassword
};

