// Handles email sending via Gmail SMTP (using App Password)
const nodemailer = require('nodemailer');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

// Create transporter with Gmail SMTP
const transporter = nodemailer.createTransport({
  service: 'gmail',
  auth: {
    user: process.env.GMAIL_USER,
    pass: process.env.GMAIL_APP_PASSWORD
  }
});

async function sendPasswordResetEmail(toEmail, resetCode) {
  const mailOptions = {
    from: `"Convene" <${process.env.GMAIL_USER}>`,
    to: toEmail,
    subject: 'Votre code de réinitialisation Convene',
    html: `
      <!DOCTYPE html>
      <html>
      <body style="font-family: Arial, sans-serif; max-width: 500px;
                   margin: 0 auto; padding: 20px; color: #333;">
        <div style="text-align: center; margin-bottom: 30px;">
          <h1 style="color: #1a1a2e; font-size: 24px; margin: 0;">
            🎯 Convene
          </h1>
        </div>
        <h2 style="font-size: 20px; margin-bottom: 8px;">
          Réinitialisation de mot de passe
        </h2>
        <p style="color: #666; margin-bottom: 24px;">
          Vous avez demandé la réinitialisation de votre mot de passe.
          Voici votre code de vérification :
        </p>
        <div style="background: #f8f9fa; border-radius: 12px;padding: 24px; text-align: center; margin-bottom: 24px;">
          <span style="font-size: 36px; font-weight: bold;letter-spacing: 12px; color: #1a1a2e;font-family: 'Courier New', monospace;">
            ${resetCode}
          </span>
        </div>
        <p style="color: #666; font-size: 14px;">
          ⏱️ Ce code expire dans <strong>1 heure</strong>.
        </p>
        <p style="color: #666; font-size: 14px;">
          🔒 Si vous n'avez pas demandé cette réinitialisation,
          ignorez cet email. Votre mot de passe reste inchangé.
        </p>
        <hr style="border: none; border-top: 1px solid #eee; margin: 24px 0;">
        <p style="color: #aaa; font-size: 12px; text-align: center;">
          Cet email a été envoyé automatiquement par Convene.
          Ne pas répondre à cet email.
        </p>
      </body>
      </html>
    `
  };
  await transporter.sendMail(mailOptions);
  debugLog(`[Email] Code de réinitialisation envoyé à ${toEmail}`);
}
module.exports = {
  sendPasswordResetEmail
};
