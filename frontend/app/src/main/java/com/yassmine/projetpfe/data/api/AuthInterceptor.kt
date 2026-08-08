package com.yassmine.projetpfe.data.api

import com.yassmine.projetpfe.BuildConfig
import com.yassmine.projetpfe.data.repository.AuthSessionManager
import com.yassmine.projetpfe.data.repository.TokenPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Interceptor
import okhttp3.Response
import org.json.JSONObject
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val authSessionManager: AuthSessionManager,
) : Interceptor {

    private val refreshClient: OkHttpClient = OkHttpClient.Builder().build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val path = originalRequest.url.encodedPath

        if (isAuthBypassPath(path)) {
            return chain.proceed(originalRequest) // envoi de requete 
        }

        var token = authSessionManager.currentAccessToken()
        if (!token.isNullOrBlank() && authSessionManager.isJwtExpired(token, skewSeconds = 60)) {
            val refreshed = runBlocking {
                authSessionManager.refreshIfNeeded { refreshToken ->
                    val refreshUrl = BuildConfig.BASE_URL.trimEnd('/') + "/auth/refresh"
                    val body = JSONObject()
                        .put("refreshToken", refreshToken)
                        .toString()
                        .toRequestBody("application/json".toMediaType())

                    val refreshRequest = Request.Builder()
                        .url(refreshUrl)
                        .post(body)
                        .build()

                    try {
                        val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                        if (!refreshResponse.isSuccessful) {
                            refreshResponse.close()
                            return@refreshIfNeeded null
                        }

                        val payload = refreshResponse.body?.string().orEmpty()
                        refreshResponse.close()

                        val root = JSONObject(payload)
                        val data = root.optJSONObject("data") ?: return@refreshIfNeeded null
                        val newAccessToken = data.optString("token", "")
                        val newRefreshToken = data.optString("refreshToken", "").ifBlank { null }

                        if (newAccessToken.isBlank()) {
                            return@refreshIfNeeded null
                        }

                        TokenPair(newAccessToken, newRefreshToken)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            if (!refreshed) {
                runBlocking(Dispatchers.IO) { authSessionManager.emitSessionExpired() }
            }
            token = authSessionManager.currentAccessToken()
        }

        val newRequest = if (!token.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(newRequest)
        if (response.code != 401 || newRequest.header("X-Auth-Retry") == "1") {
            return response
        }

        response.close()

        val refreshed = runBlocking {
            val latestToken = authSessionManager.currentAccessToken()
            if (!latestToken.isNullOrBlank() && latestToken != token) {
                true
            } else {
                authSessionManager.refreshIfNeeded { refreshToken ->
                    val refreshUrl = BuildConfig.BASE_URL.trimEnd('/') + "/auth/refresh"
                    val body = JSONObject()
                        .put("refreshToken", refreshToken)
                        .toString()
                        .toRequestBody("application/json".toMediaType())

                    val refreshRequest = Request.Builder()
                        .url(refreshUrl)
                        .post(body)
                        .build()

                    try {
                        val refreshResponse = refreshClient.newCall(refreshRequest).execute()
                        if (!refreshResponse.isSuccessful) {
                            refreshResponse.close()
                            return@refreshIfNeeded null
                        }

                        val payload = refreshResponse.body?.string().orEmpty()
                        refreshResponse.close()

                        val root = JSONObject(payload)
                        val data = root.optJSONObject("data") ?: return@refreshIfNeeded null
                        val newAccessToken = data.optString("token", "")
                        val newRefreshToken = data.optString("refreshToken", "").ifBlank { null }

                        if (newAccessToken.isBlank()) {
                            return@refreshIfNeeded null
                        }

                        TokenPair(newAccessToken, newRefreshToken)
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        }

        val retryToken = authSessionManager.currentAccessToken()
        if (!refreshed || retryToken.isNullOrBlank()) {
            runBlocking(Dispatchers.IO) { authSessionManager.emitSessionExpired() }
            return chain.proceed(originalRequest)
        }

        val retriedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $retryToken")
            .header("X-Auth-Retry", "1")
            .build()

        return chain.proceed(retriedRequest)
    }

    private fun isAuthBypassPath(path: String): Boolean {
        return path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/auth/forgot-password") ||
            path.contains("/auth/reset-password") ||
            path.contains("/auth/refresh")
    }
}

