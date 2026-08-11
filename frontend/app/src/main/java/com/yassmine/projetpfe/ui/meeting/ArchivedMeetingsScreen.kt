package com.yassmine.projetpfe.ui.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yassmine.projetpfe.data.model.Meeting
import com.yassmine.projetpfe.data.model.MeetingStatus
import com.yassmine.projetpfe.data.model.MeetingType
import com.yassmine.projetpfe.ui.MeetingCard
import com.yassmine.projetpfe.viewmodel.MeetingViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun parseArchivedDate(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zdt = instant.atZone(ZoneId.systemDefault())
        zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    } catch (_: Exception) {
        isoString.take(10)
    }
}

private fun parseArchivedTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zdt = instant.atZone(ZoneId.systemDefault())
        zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        isoString.drop(11).take(5)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedMeetingsScreen(
    onBack: () -> Unit,
    onMeetingClick: (String) -> Unit,
    viewModel: MeetingViewModel,
) {
    val archivedMeetings by viewModel.archivedMeetings.collectAsState()
    val isArchivedLoading by viewModel.isArchivedLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadArchivedMeetings()
    }

    val uiArchivedMeetings = archivedMeetings.map { dto ->
        val uiType = when (dto.meetingType.lowercase()) {
            "physical" -> MeetingType.PHYSICAL
            else -> MeetingType.ONLINE
        }

        Meeting(
            id = dto.realId,
            title = dto.title,
            date = parseArchivedDate(dto.startTime),
            time = parseArchivedTime(dto.startTime),
            participants = dto.joinedParticipants.size.takeIf { it > 0 } ?: dto.participants.size,
            duration = "${dto.duration} mins",
            type = uiType,
            status = MeetingStatus.FINISHED,
            location = dto.location
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réunions archivées") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        when {
            isArchivedLoading && uiArchivedMeetings.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiArchivedMeetings.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aucune réunion archivée")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(uiArchivedMeetings, key = { it.id }) { meeting ->
                        MeetingCard(
                            meeting = meeting,
                            onClick = { onMeetingClick(meeting.id) }
                        )
                    }
                }
            }
        }
    }
}
