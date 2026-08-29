package com.yassmine.projetpfe.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Utilitaires de formatage de dates/heures pour l'app.
 *
 * PROBLÈME OBSERVÉ:
 *   - Liste (HomeScreen) affiche "22:00"
 *   - Détail (MeetingDetailScreen) affiche "23:00"
 *   - MongoDB: startTime = "2026-02-23T21:20:00.000+00:00" (UTC)
 *   - Attendu en UTC+1 (Tunis): 22:20
 */
object TimeUtils {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val localZone = ZoneId.systemDefault()

    /**
     * Parse n'importe quel format ISO8601 et retourne l'heure locale
     * Gère: "2026-02-23T21:20:00.000Z", "2026-02-23T22:20:00+01:00", etc.
     */
    fun formatTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "--:--"
        return try {
            val instant = parseToInstant(isoString)
            val zdt     = instant.atZone(localZone)
            zdt.format(timeFormatter)
        } catch (e: Exception) {
            // Fallback: essayer de parser le format "HH:mm" directement
            if (isoString.length == 5 && isoString.contains(":")) isoString
            else "--:--"
        }
    }

    fun formatDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "--/--/----"
        return try {
            val instant = parseToInstant(isoString)
            val zdt     = instant.atZone(localZone)
            zdt.format(dateFormatter)
        } catch (e: Exception) {
            "--/--/----"
        }
    }

    fun formatDateTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "--/--/---- --:--"
        return try {
            val instant = parseToInstant(isoString)
            val zdt     = instant.atZone(localZone)
            zdt.format(dateTimeFormatter)
        } catch (e: Exception) {
            "--/--/---- --:--"
        }
    }

    /**
     * Retourne "2026-02-23" pour la liste (format court utilisé dans HomeScreen cards)
     */
    fun formatDateShort(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "----"
        return try {
            val instant = parseToInstant(isoString)
            val zdt     = instant.atZone(localZone)
            // Format "yyyy-MM-dd" pour rester cohérent avec l'affichage actuel
            zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (e: Exception) {
            isoString.take(10)  // fallback: prendre les 10 premiers chars
        }
    }

    private fun parseToInstant(isoString: String): Instant {
        // Normaliser: remplacer "+00:00" par "Z" pour Instant.parse
        val normalized = isoString
            .replace("+00:00", "Z")
            .replace(" ", "T")   // "2026-02-23 21:20" → "2026-02-23T21:20"

        return try {
            Instant.parse(normalized)
        } catch (e: Exception) {
            // Essayer java.time.OffsetDateTime pour les formats avec offset
            val odt = java.time.OffsetDateTime.parse(isoString)
            odt.toInstant()
        }
    }
}