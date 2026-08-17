package com.yassmine.projetpfe.notifications

import android.content.Context
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.local.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object NotificationLocalization {
    fun buildLocalizedNotification(
        context: Context,
        type: String?,
        data: Map<String, Any?>
    ): Pair<String, String> {
        val savedLang = runBlocking {
            PreferencesManager(context.applicationContext)
                .getAppLanguage()
                .first()
                .lowercase()
        }.ifBlank { "fr" }
        val localizedCtx = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).also {
                it.setLocale(java.util.Locale(savedLang))
            }
        )

        val meetingTitle = (data["meetingTitle"] as? String)
            ?: (data["title"] as? String)
            ?: ""
        val organizer = (data["organizerName"] as? String)
            ?: (data["fromUserName"] as? String)
            ?: (data["fromName"] as? String)
            ?: localizedCtx.getString(R.string.notif_unknown_user)
        return when (type) {
            "meeting_created" -> Pair(
                localizedCtx.getString(R.string.notif_meeting_invite_title),
                localizedCtx.getString(R.string.notif_meeting_invite_body, organizer, meetingTitle)
            )

            "meeting_starting", "meeting_reminder" -> {
                Pair(
                    localizedCtx.getString(R.string.notif_reminder_title),
                    localizedCtx.getString(R.string.notif_reminder_body, meetingTitle, "5")
                )
            }

            "recording_ready" -> Pair(
                localizedCtx.getString(R.string.notif_recording_title),
                localizedCtx.getString(R.string.notif_recording_body, meetingTitle)
            )

            "ai_summary_ready", "ai_result_ready" -> Pair(
                localizedCtx.getString(R.string.notif_ai_summary_title),
                localizedCtx.getString(R.string.notif_ai_summary_body, meetingTitle)
            )

            "meeting_cancelled" -> Pair(
                localizedCtx.getString(R.string.notif_meeting_cancelled_title),
                localizedCtx.getString(R.string.notif_meeting_cancelled_body, meetingTitle)
            )

            "meeting_updated" -> Pair(
                localizedCtx.getString(R.string.notif_meeting_updated_title),
                localizedCtx.getString(R.string.notif_meeting_updated_body, meetingTitle)
            )

            "friend_request" -> Pair(
                localizedCtx.getString(R.string.notif_friend_request_title),
                localizedCtx.getString(R.string.notif_friend_request_body, organizer)
            )

            "friend_accepted" -> Pair(
                localizedCtx.getString(R.string.notif_friend_accepted_title),
                localizedCtx.getString(R.string.notif_friend_accepted_body, organizer)
            )

            "task_assigned" -> Pair(
                localizedCtx.getString(R.string.notif_task_assigned_title),
                localizedCtx.getString(R.string.notif_task_assigned_body, meetingTitle)
            )
            "friend_rejected" -> Pair(
                localizedCtx.getString(R.string.notif_friend_rejected_title),
                localizedCtx.getString(R.string.notif_friend_rejected_body, organizer)
            )
            "admin_broadcast" -> {
                val broadcastTitle = (data["title"] as? String)?.takeIf { it.isNotBlank() }
                    ?: localizedCtx.getString(R.string.notif_generic_title)
                val broadcastMessage = (data["message"] as? String)?.takeIf { it.isNotBlank() }
                    ?: localizedCtx.getString(R.string.notif_generic_body)
                Pair(broadcastTitle, broadcastMessage)
            }

            else -> Pair(
                localizedCtx.getString(R.string.notif_generic_title),
                localizedCtx.getString(R.string.notif_generic_body)
            )
        }
    }
}