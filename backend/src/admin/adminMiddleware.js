const requireAdmin = (req, res, next) => {
  const adminEmails = String(process.env.ADMIN_EMAILS || '')
    .split(',')
    .map(email => email.trim().toLowerCase())
    .filter(Boolean);

  if (adminEmails.length === 0) {
    return res.status(403).json({ success: false, message: 'Aucun admin configuré' });
  }

  const currentEmail = String(req.user?.email || '').toLowerCase();
  if (!adminEmails.includes(currentEmail)) {
    return res.status(403).json({ success: false, message: 'Accès admin refusé' });
  }

  return next();
};

module.exports = { requireAdmin };
