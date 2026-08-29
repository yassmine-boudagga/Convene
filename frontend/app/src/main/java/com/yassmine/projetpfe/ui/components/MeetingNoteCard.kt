package com.yassmine.projetpfe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yassmine.projetpfe.data.api.MeetingNoteDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun MeetingNoteCard(
    note: MeetingNoteDto,
    modifier: Modifier = Modifier,
    fallbackTimestampMillis: Long? = null
) {
    val authorName = note.userId?.name?.takeIf { it.isNotBlank() } ?: "Inconnu"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UserAvatar(
                    profilePicture = note.userId?.profilePicture,
                    name = authorName,
                    size = 36.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = authorName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatMeetingNoteTimestamp(note.timestamp, fallbackTimestampMillis),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = note.content.ifBlank { "(Note vide)" },
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatMeetingNoteTimestamp(timestamp: String, fallbackMillis: Long?): String {
    if (timestamp.isNotBlank()) {
        val inputPatterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )

        inputPatterns.forEach { pattern ->
            try {
                val parser = SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsedDate = parser.parse(timestamp)
                if (parsedDate != null) {
                    val localFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
                        timeZone = TimeZone.getDefault()
                    }
                    return localFormat.format(parsedDate)
                }
            } catch (_: Exception) {
            }
        }

        return timestamp.takeIf { it.isNotBlank() } ?: "--:--:--"
    }

    val millis = fallbackMillis ?: System.currentTimeMillis()
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
