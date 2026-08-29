package com.yassmine.projetpfe.utils

import android.content.Context
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.local.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object BadgeLocalization {
    fun getLocalizedBadge(context: Context, badgeType: String?): Pair<String, String> {
        val savedLang = runBlocking {
            PreferencesManager(context.applicationContext)
                .getAppLanguage()
                .first()
        }

        val normalized = badgeType
            ?.trim()
            ?.lowercase()
            .orEmpty()

        val localizedCtx = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration).also {
                it.setLocale(java.util.Locale(savedLang.ifBlank { "fr" }))
            }
        )

        return when (normalized) {
            "organizer", "organisateur" -> Pair(
                localizedCtx.getString(R.string.badge_organizer_name),
                localizedCtx.getString(R.string.badge_organizer_desc)
            )

            "punctual", "ponctuel" -> Pair(
                localizedCtx.getString(R.string.badge_punctual_name),
                localizedCtx.getString(R.string.badge_punctual_desc)
            )

            "collaborator", "active_participant", "participant_actif" -> Pair(
                localizedCtx.getString(R.string.badge_active_participant_name),
                localizedCtx.getString(R.string.badge_active_participant_desc)
            )

            "networker" -> Pair(
                localizedCtx.getString(R.string.badge_networker_name),
                localizedCtx.getString(R.string.badge_networker_desc)
            )

            "early_bird", "matinal" -> Pair(
                localizedCtx.getString(R.string.badge_early_bird_name),
                localizedCtx.getString(R.string.badge_early_bird_desc)
            )

            "bilingual", "bilingue" -> Pair(
                localizedCtx.getString(R.string.badge_bilingual_name),
                localizedCtx.getString(R.string.badge_bilingual_desc)
            )

            "marathon" -> Pair(
                localizedCtx.getString(R.string.badge_marathon_name),
                localizedCtx.getString(R.string.badge_marathon_desc)
            )

            "efficient", "efficace" -> Pair(
                localizedCtx.getString(R.string.badge_efficient_name),
                localizedCtx.getString(R.string.badge_efficient_desc)
            )

            else -> Pair(
                badgeType ?: localizedCtx.getString(R.string.badge_unknown_name),
                localizedCtx.getString(R.string.badge_unknown_desc)
            )
        }
    }
}
