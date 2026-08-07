const mongoose = require('mongoose');
const isDevelopment = process.env.NODE_ENV !== 'production';

const debugLog = (...args) => {
  if (isDevelopment) {
    console.debug(...args);
  }
};

const errorLog = (...args) => {
  console.error(...args);
};

const connectDB = async () => {
  const uri = process.env.MONGODB_URI;

  if (!uri) {
    errorLog('[Database] MONGODB_URI manquante dans les variables d\'environnement');
    process.exit(1);
  }

  try {
    const conn = await mongoose.connect(uri);

    mongoose.connection.on('error', (err) => {
      errorLog('[Database] Erreur de connexion MongoDB :', err.message);
    });
    // Reconnexion
    let reconnectAttempts = 0;

    mongoose.connection.once('disconnected', function handleDisconnect() {
      debugLog('[Database] MongoDB déconnecté');

      if (reconnectAttempts >= 5) {
        errorLog('[Database] Nombre maximum de tentatives atteint. Abandon.');
        return;
      }

      const delay = Math.pow(2, reconnectAttempts) * 1000;
      debugLog(`[Database] Reconnexion dans ${delay / 1000}s (tentative ${reconnectAttempts + 1}/5)...`);

      setTimeout(async () => {
        try {
          await mongoose.connect(uri);
          debugLog('[Database] Reconnexion réussie');
          reconnectAttempts = 0;
          mongoose.connection.once('disconnected', handleDisconnect);
        } catch (err) {
          reconnectAttempts++;
          errorLog('[Database] Reconnexion échouée :', err.message);
          handleDisconnect();
        }
      }, delay);
    });
    // Graceful shutdown
    process.on('SIGINT', async () => {
      try {
        await mongoose.connection.close();
        debugLog('[Database] Connexion MongoDB fermée proprement');
      } catch (err) {
        errorLog('[Database] Erreur lors de la fermeture :', err.message);
      } finally {
        process.exit(0);
      }
    });

    return conn;

  } catch (error) {
    errorLog('[Database] Échec de connexion MongoDB :', error.message);
    process.exit(1);
  }
};

module.exports = connectDB;