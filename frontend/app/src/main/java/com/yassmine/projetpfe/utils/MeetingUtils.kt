package com.yassmine.projetpfe.utils

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

fun getUserNoteColor(userId: String): Color {
    val palette = listOf(
        Color(0xFFE53935),
        Color(0xFF1E88E5),
        Color(0xFF43A047),
        Color(0xFFF4511E),
        Color(0xFF8E24AA),
        Color(0xFF00ACC1),
        Color(0xFFE91E63),
        Color(0xFF00897B),
        Color(0xFFFFB300),
        Color(0xFF6D4C41),
        Color(0xFF546E7A),
        Color(0xFF7CB342)
    )

    if (userId.isBlank()) return palette[1]
    val hash = abs(userId.hashCode())
    return palette[hash % palette.size]
}
