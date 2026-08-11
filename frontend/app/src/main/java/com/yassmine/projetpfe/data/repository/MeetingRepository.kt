package com.yassmine.projetpfe.data.repository

import android.util.Log
import com.google.gson.JsonSyntaxException
import com.yassmine.projetpfe.data.api.*
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getMeetings(
        status: String? = null,
        page: Int = 1,
        limit: Int = 20
    ): Result<MeetingListResponse> {
        return try {
            val response = apiService.getMeetings(status, page, limit, all = false)
            if (BuildConfig.DEBUG) Log.d("MeetingRepo", "getMeetings status=$status page=$page → ${response.data.meetings.size} items (total=${response.data.pagination?.total ?: "?"})")
            Result.success(response.data)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            Log.e("MeetingRepo", "HTTP ${e.code()}: $body")
            Result.failure(Exception("Erreur réseau ${e.code()}"))
        } catch (e: JsonSyntaxException) {
            Log.e("MeetingRepo", "JSON PARSE ERROR: ${e.message}", e)
            Result.success(MeetingListResponse(meetings = emptyList()))
        } catch (e: IOException) {
            Log.e("MeetingRepo", "IO: ${e.message}")
            Result.failure(Exception("Vérifiez votre connexion Internet"))
        } catch (e: Exception) {
            Log.e("MeetingRepo", "UNEXPECTED: ${e.message}", e)
            Result.failure(Exception("Une erreur est survenue: ${e.message}"))
        }
    }

    suspend fun createMeeting(request: CreateMeetingRequest): Result<MeetingDto> {
        return try {
            val response = apiService.createMeeting(request)
            val meeting = response.data.meeting
            if (meeting != null) {
                Result.success(meeting)
            } else {
                Result.failure(Exception("Réponse invalide du serveur"))
            }
        } catch (_: HttpException) {
            Result.failure(Exception("Erreur lors de la création"))
        } catch (_: IOException) {
            Result.failure(Exception("Vérifiez votre connexion Internet"))
        } catch (_: Exception) {
            Result.failure(Exception("Une erreur est survenue"))
        }
    }

    suspend fun getMeeting(id: String): Result<MeetingDto> {
        return try {
            val response = apiService.getMeeting(id)
            val meeting = response.data.meeting
            if (meeting == null) {
                Log.e("MeetingRepo", "getMeeting($id): meeting null dans la réponse")
                Result.failure(Exception("Réunion introuvable"))
            } else {
                if (BuildConfig.DEBUG) Log.d("MeetingRepo", "getMeeting($id): status=${meeting.status}")
                Result.success(meeting)
            }
        } catch (e: JsonSyntaxException) {
            Log.e("MeetingRepo", "JSON PARSE ERROR getMeeting($id): ${e.message}", e)
            Result.failure(Exception("Erreur de format de données"))
        } catch (e: Exception) {
            Log.e("MeetingRepo", "getMeeting($id) error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updateMeeting(id: String, request: UpdateMeetingRequest): Result<MeetingDto> {
        return try {
            val response = apiService.updateMeeting(id, request)
            val meeting = response.data.meeting
            if (meeting != null) {
                Result.success(meeting)
            } else {
                Result.failure(Exception("Réponse invalide du serveur"))
            }
        } catch (_: HttpException) {
            Result.failure(Exception("Erreur lors de la modification"))
        } catch (_: Exception) {
            Result.failure(Exception("Une erreur est survenue"))
        }
    }

    suspend fun cancelMeeting(id: String): Result<Unit> {
        return try {
            apiService.cancelMeeting(id)
            Result.success(Unit)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            Log.e("MeetingRepo", "cancelMeeting HTTP ${e.code()}: $body")
            Result.failure(Exception("Erreur lors de l'annulation"))
        } catch (_: IOException) {
            Result.failure(Exception("Vérifiez votre connexion Internet"))
        } catch (_: Exception) {
            Result.failure(Exception("Une erreur est survenue"))
        }
    }

    suspend fun joinMeeting(id: String): Result<JoinMeetingResponse> {
        return try {
            val response = apiService.joinMeeting(id)
            Result.success(response.data)
        } catch (e: HttpException) {
            val errorMessage = when (e.code()) {
                403  -> "Vous n'êtes pas autorisé à rejoindre cette réunion"
                400  -> "La réunion n'est pas disponible"
                else -> "Erreur lors de la connexion"
            }
            Result.failure(Exception(errorMessage))
        } catch (_: Exception) {
            Result.failure(Exception("Une erreur est survenue"))
        }
    }

    suspend fun leaveMeeting(id: String): Result<Unit> {
        return try {
            apiService.leaveMeeting(id)
            Result.success(Unit)
        } catch (e: HttpException) {
            if (e.code() == 400) {
                Log.w("MeetingRepo", "leaveMeeting 400 — déjà parti")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Erreur déconnexion ${e.code()}"))
            }
        } catch (e: Exception) {
            Log.w("MeetingRepo", "leaveMeeting: ${e.message}")
            Result.success(Unit)
        }
    }

    suspend fun sendHeartbeat(id: String) {
        try {
            apiService.sendHeartbeat(id)
        } catch (e: Exception) {
            Log.v("MeetingRepo", "heartbeat error: ${e.message}")
        }
    }

    suspend fun addNote(meetingId: String, content: String): Result<MeetingNoteDto> {
        return try {
            if (BuildConfig.DEBUG) Log.d("MeetingRepo", "addNote: meetingId=$meetingId")
            val response = apiService.addNote(meetingId, AddNoteRequest(content))
            Result.success(response.data.note)
        } catch (e: HttpException) {
            Log.e("MeetingRepo", "addNote HTTP ${e.code()}")
            Result.failure(Exception("Erreur serveur lors de l'ajout de la note"))
        } catch (e: Exception) {
            Log.e("MeetingRepo", "addNote error: ${e.message}", e)
            Result.failure(Exception("Erreur lors de l'ajout de la note"))
        }
    }

    suspend fun getNotes(meetingId: String): Result<List<MeetingNoteDto>> {
        return try {
            val response = apiService.getNotes(meetingId)
            if (BuildConfig.DEBUG) Log.d("MeetingRepo", "getNotes: ${response.data.notes.size} notes")
            Result.success(response.data.notes)
        } catch (_: Exception) {
            Result.failure(Exception("Erreur lors du chargement des notes"))
        }
    }

    suspend fun stopRecording(meetingId: String): Result<RecordingResponse> {
        return try {
            val response = apiService.stopRecording(meetingId)
            Result.success(response.data)
        } catch (e: HttpException) {
            val body = e.response()?.errorBody()?.string()
            Log.e("MeetingRepo", "stopRecording HTTP ${e.code()}: $body")
            Result.failure(Exception("Erreur arrêt enregistrement ${e.code()}"))
        } catch (e: IOException) {
            Log.e("MeetingRepo", "stopRecording IO: ${e.message}")
            Result.failure(Exception("Vérifiez votre connexion Internet"))
        } catch (e: Exception) {
            Log.e("MeetingRepo", "stopRecording error: ${e.message}", e)
            Result.failure(Exception("Erreur lors de l'arrêt de l'enregistrement"))
        }
    }
}