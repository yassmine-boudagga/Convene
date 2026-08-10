package com.yassmine.projetpfe.data.repository

import android.content.Context
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.ForgotPasswordRequest
import com.yassmine.projetpfe.data.api.LoginRequest
import com.yassmine.projetpfe.data.api.LogoutRequest
import com.yassmine.projetpfe.data.api.RefreshTokenRequest
import com.yassmine.projetpfe.data.api.RegisterRequest
import com.yassmine.projetpfe.data.api.ResetPasswordRequest
import com.yassmine.projetpfe.data.local.PreferencesManager
import com.yassmine.projetpfe.notifications.NotificationBackgroundScheduler
import com.yassmine.projetpfe.data.repository.TokenPair
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.SharedFlow

@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager,
    private val authSessionManager: AuthSessionManager,
) {

    val sessionEvents: SharedFlow<AuthSessionEvent> = authSessionManager.sessionEvents

    suspend fun register(name: String, email: String, password: String): Result<Unit> {
        return try {
            val response = apiService.register(
                RegisterRequest(name, email, password)
            )

            authSessionManager.saveSession(
                accessToken = response.data.token,
                refreshToken = response.data.refreshToken,
                rememberMeEnabled = true,
            )
            preferencesManager.saveLoginTimestamp(System.currentTimeMillis())
            preferencesManager.saveUser(
                id = response.data.user.id,
                name = response.data.user.name,
                email = response.data.user.email
            )

            Result.success(Unit)

        } catch (e: HttpException) {
            val errorMessage = when (e.code()) {
                400 -> "Données invalides"
                409 -> "Cet email est déjà utilisé"
                500 -> "Erreur serveur"
                else -> "Erreur d'inscription"
            }
            Result.failure(Exception(errorMessage))

        } catch (_: IOException) {
            Result.failure(Exception("Vérifiez votre connexion Internet"))

        } catch (_: Exception) {
            Result.failure(Exception("Une erreur est survenue"))
        }
    }


    suspend fun login(email: String, password: String, rememberMe: Boolean): Result<Unit> {
        return try {
            val response = apiService.login(LoginRequest(email, password))

            authSessionManager.saveSession(
                accessToken = response.data.token,
                refreshToken = response.data.refreshToken,
                rememberMeEnabled = rememberMe,
            )
            preferencesManager.saveLoginTimestamp(System.currentTimeMillis())
            preferencesManager.saveUser(
                id = response.data.user.id,
                name = response.data.user.name,
                email = response.data.user.email
            )

            Result.success(Unit)

        } catch (e: HttpException) {
            val errorMessage = when (e.code()) {
                401 -> "Email ou mot de passe incorrect"
                403 -> "Accès refusé"
                500 -> "Erreur serveur"
                else -> "Erreur de connexion"
            }
            Result.failure(Exception(errorMessage))

        } catch (_: IOException) {
            Result.failure(Exception("Vérifiez votre connexion Internet"))

        } catch (_: Exception) {
            Result.failure(Exception("Une erreur est survenue"))
        }
    }

    suspend fun bootstrapSession(): Boolean {
        return try {
            authSessionManager.initializeFromStorage()
            val accessToken = authSessionManager.currentAccessToken() ?: return false

            if (authSessionManager.isJwtExpired(accessToken, skewSeconds = 60)) {
                val refreshed = refreshAccessTokenIfPossible()
                if (!refreshed) {
                    authSessionManager.emitSessionExpired()
                    return false
                }
            }

            authSessionManager.currentAccessToken() != null
        } catch (_: Exception) {
            authSessionManager.emitSessionExpired()
            false
        }
    }

    suspend fun refreshAccessTokenIfPossible(): Boolean {
        return authSessionManager.refreshIfNeeded { refreshToken ->
            try {
                val response = apiService.refreshAccessToken(RefreshTokenRequest(refreshToken))
                TokenPair(
                    accessToken = response.data.token,
                    refreshToken = response.data.refreshToken,
                )
            } catch (_: Exception) {
                null
            }
        }
    }


    suspend fun logout(context: Context): Result<Unit> {
        return try {
            apiService.logout(LogoutRequest(authSessionManager.currentRefreshToken()))
            authSessionManager.clearSession()
            preferencesManager.clearToken()
            preferencesManager.clearRefreshToken()
            preferencesManager.saveRememberMe(false)
            NotificationBackgroundScheduler.stop(context)
            Result.success(Unit)
        } catch (_: Exception) {
            authSessionManager.clearSession()
            preferencesManager.clearToken()
            preferencesManager.clearRefreshToken()
            preferencesManager.saveRememberMe(false)
            NotificationBackgroundScheduler.stop(context)
            Result.success(Unit)
        }
    }

    suspend fun forgotPassword(email: String): Result<Unit> {
        return try {
            val response = apiService.forgotPassword(ForgotPasswordRequest(email = email))
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Erreur lors de l'envoi. Réessayez."))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Impossible de contacter le serveur. Vérifiez votre connexion."))
        } catch (_: Exception) {
            Result.failure(Exception("Impossible de contacter le serveur. Vérifiez votre connexion."))
        }
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<String> {
        return try {
            val response = apiService.resetPassword(
                ResetPasswordRequest(
                    email = email,
                    code = code,
                    newPassword = newPassword
                )
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()?.message ?: "Mot de passe réinitialisé avec succès !")
            } else {
                val errorMessage = 
                try {
                    val body = response.errorBody()?.string()
                    org.json.JSONObject(body ?: "{}").optString(
                        "message",
                        "Code invalide ou expiré. Faites une nouvelle demande."
                    )
                } catch (_: Exception) {
                    "Code invalide ou expiré. Faites une nouvelle demande."
                }
                Result.failure(Exception(errorMessage))
            }
        } catch (_: IOException) {
            Result.failure(Exception("Impossible de contacter le serveur."))
        } catch (_: Exception) {
            Result.failure(Exception("Impossible de contacter le serveur."))
        }
    }
}