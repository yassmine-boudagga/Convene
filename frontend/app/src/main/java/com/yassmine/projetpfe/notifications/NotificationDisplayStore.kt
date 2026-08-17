package com.yassmine.projetpfe.notifications

import android.content.Context

object NotificationDisplayStore {
    private const val PREFS_NAME = "meetflow_notification_store"
    private const val KEY_IDS = "displayed_ids"

    @Synchronized
    fun shouldDisplay(context: Context, notificationId: String): Boolean {
        if (notificationId.isBlank()) return true

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val displayed = prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()

        if (displayed.contains(notificationId)) {
            return false
        }

        displayed.add(notificationId)
        prefs.edit().putStringSet(KEY_IDS, displayed).apply()
        return true
    }

    @Synchronized
    fun markDisplayed(context: Context, notificationId: String) {
        if (notificationId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val displayed = prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        displayed.add(notificationId)
        prefs.edit().putStringSet(KEY_IDS, displayed).apply()
    }

    @Synchronized
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_IDS)
            .apply()
    }
}
