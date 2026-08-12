package com.yassmine.projetpfe.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.components.showSuccess
import androidx.navigation.NavController
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.MeetingDto
import com.yassmine.projetpfe.data.api.MeetingNoteDto
import com.yassmine.projetpfe.data.api.MeetingParticipantDto
import com.yassmine.projetpfe.data.api.joinedPresenceTokenSet
import com.yassmine.projetpfe.data.api.participantPresenceCandidates
import com.yassmine.projetpfe.ui.components.MeetingNoteCard
import com.yassmine.projetpfe.viewmodel.InPersonMeetingViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InPersonMeetingScreen(
    meetingId: String,
    navController: NavController,
    viewModel: InPersonMeetingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDuration.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()
    val meeting by viewModel.meeting.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val forceEndCountdown by viewModel.forceEndCountdown.collectAsState()
    val forceEndReason by viewModel.forceEndReason.collectAsState()
    val localNoteTimestamps by viewModel.localNoteTimestamps.collectAsState()
    val manualStopUsed by viewModel.manualStopUsed.collectAsState()
    val stopSuccessMessage by viewModel.stopSuccessMessage.collectAsState()
    val recordingStoppedByHost by viewModel.recordingStoppedByHost.collectAsState()

    val context = LocalContext.current
    val isHost = meeting?.createdBy?.realId == currentUserId
    val snackbarHostState = remember { SnackbarHostState() }
    var showLeaveDialog by remember { mutableStateOf(false) }

    BackHandler {
        showLeaveDialog = true
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.loadMeeting(meetingId)
            viewModel.joinMeeting(meetingId)
            viewModel.listenToWebSocket(meetingId)
        }
    }

    LaunchedEffect(meetingId) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            viewModel.loadMeeting(meetingId)
            viewModel.joinMeeting(meetingId)
            viewModel.listenToWebSocket(meetingId)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val showForceEndDialog = !forceEndReason.isNullOrBlank() && forceEndCountdown != null
    val suppressAutoLeaveNavigation =
        !forceEndReason.isNullOrBlank() || uploadState is InPersonMeetingViewModel.UploadState.Uploading

    LaunchedEffect(uploadState) {
        val errorState = uploadState as? InPersonMeetingViewModel.UploadState.Error
        if (errorState != null) {
            snackbarHostState.showError(errorState.message)
        }
    }

    LaunchedEffect(stopSuccessMessage) {
        val message = stopSuccessMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSuccess(message)
            viewModel.consumeStopSuccessMessage()
        }
    }

    LaunchedEffect(uiState, suppressAutoLeaveNavigation) {
        if (uiState is InPersonMeetingViewModel.InPersonUiState.Finished && !suppressAutoLeaveNavigation) {
            navController.navigate("meeting_detail/$meetingId") {
                popUpTo("in_person_meeting/$meetingId") { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val elapsedMeetingSeconds by produceState(initialValue = 0L, key1 = meeting?.startTime) {
        while (true) {
            value = meeting?.startTime?.let { start -> elapsedSinceMeetingStart(start) } ?: 0L
            delay(1000L)
        }
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(R.string.leave_meeting_title)) },
            text = {
                Text(
                    if (isRecording)
                        stringResource(R.string.auto_stop_send_hint)
                    else
                        stringResource(R.string.leave_meeting_confirm_message)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        viewModel.leaveMeeting(meetingId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text(stringResource(R.string.leave_label)) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text(stringResource(R.string.cancel_label)) }
            }
        )
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        meeting?.title ?: stringResource(R.string.in_person_meeting_default_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { showLeaveDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_label))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showLeaveDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) { Text(stringResource(R.string.leave_label)) }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            meeting?.let { mtg ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!mtg.location.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    mtg.location,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            val start = formatInPersonMeetingTime(mtg.startTime)
                            val end = formatInPersonMeetingEndTime(mtg.startTime, mtg.duration)
                            Text(
                                stringResource(R.string.in_person_time_range, start, end, mtg.duration),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Text(
                            text = stringResource(R.string.elapsed_time_format, formatDuration(elapsedMeetingSeconds)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            RecordingSection(
                isHost = isHost,
                isRecording = isRecording,
                duration = recordingDuration,
                uploadState = uploadState,
                stopDisabled = manualStopUsed,
                recordingStoppedByHost = recordingStoppedByHost,
                onStop = {
                    viewModel.stopAndUploadAndFinish(meetingId)
                }
            )

            ParticipantsSection(meeting = meeting)

            NotesSection(
                meetingId = meetingId,
                notes = meeting?.notes ?: emptyList(),
                viewModel = viewModel,
                localNoteTimestamps = localNoteTimestamps
            )

            if (uiState is InPersonMeetingViewModel.InPersonUiState.Loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            val errorState = uiState as? InPersonMeetingViewModel.InPersonUiState.Error
            if (errorState != null) {
                Text(
                    text = errorState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    if (showForceEndDialog) {
        val mappedReason = when (forceEndReason) {
            "host_left" -> stringResource(R.string.force_end_reason_host_left)
            "all_guests_left" -> stringResource(R.string.force_end_reason_all_left)
            "auto_finish" -> stringResource(R.string.force_end_reason_auto_finish)
            else -> stringResource(R.string.force_end_reason_default)
        }
        val countdownText = (forceEndCountdown ?: 0).coerceAtLeast(0)

        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.meeting_ended)) },
            text = {
                Text("$mappedReason\n\n${stringResource(R.string.force_end_closing_in_seconds, countdownText)}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.finishFromForceEnd()
                        navController.navigate("meeting_detail/$meetingId") {
                            popUpTo("in_person_meeting/$meetingId") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                ) {
                    Text(stringResource(R.string.leave_label))
                }
            }
        )
    }

    LaunchedEffect(forceEndCountdown, showForceEndDialog) {
        if (showForceEndDialog && (forceEndCountdown ?: 1) <= 0) {
            viewModel.finishFromForceEnd()
            navController.navigate("meeting_detail/$meetingId") {
                popUpTo("in_person_meeting/$meetingId") { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

@Composable
fun RecordingSection(
    isHost: Boolean,
    isRecording: Boolean,
    duration: Long,
    uploadState: InPersonMeetingViewModel.UploadState,
    stopDisabled: Boolean,
    recordingStoppedByHost: Boolean,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isRecording -> Color.Red.copy(alpha = 0.08f)
                uploadState is InPersonMeetingViewModel.UploadState.Success -> Color(0xFF4CAF50).copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                uploadState is InPersonMeetingViewModel.UploadState.Uploading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.uploading_recording))
                    }
                }

                uploadState is InPersonMeetingViewModel.UploadState.Success -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Text(
                            stringResource(R.string.recording_sent_ai_in_progress),
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                uploadState is InPersonMeetingViewModel.UploadState.Error -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            stringResource(R.string.recording_error_with_message, uploadState.message),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                isRecording -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BlinkingRedDot()
                            Text(
                                stringResource(R.string.recording_in_progress),
                                color = Color.Red,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            formatDuration(duration),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Red
                        )
                    }

                    if (isHost) {
                        Button(
                            onClick = onStop,
                            enabled = !stopDisabled,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (stopDisabled) Color(0xFF9E9E9E) else Color.Red,
                                disabledContainerColor = Color(0xFF9E9E9E)
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (stopDisabled) {
                                    stringResource(R.string.recording_stopped)
                                } else {
                                    stringResource(R.string.stop_and_send_ai)
                                }
                            )
                        }
                    }
                }

                recordingStoppedByHost && !isRecording -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.recording_stopped_by_host),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                else -> {
                    Text(
                        stringResource(R.string.waiting_auto_recording_start),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.auto_recording_start_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isHost) {
                        Button(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9E9E9E),
                                disabledContainerColor = Color(0xFF9E9E9E)
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.recording_stopped))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlinkingRedDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(Color.Red.copy(alpha = alpha), CircleShape)
    )
}

@Composable
fun ParticipantsSection(meeting: MeetingDto?) {
    if (meeting == null) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val guestParticipants = if (meeting.participantUsers.isNotEmpty()) {
                meeting.participantUsers
            } else {
                meeting.participants.map { email ->
                    MeetingParticipantDto(email = email)
                }
            }

            val joinedCount = meeting.joinedParticipants.size
            val totalCount = guestParticipants.size + 1

            val joinedTokenSet = meeting.joinedPresenceTokenSet()

            fun isPresent(id: String?, email: String?): Boolean {
                val candidates = participantPresenceCandidates(id, email)
                if (candidates.isEmpty()) return false
                return candidates.any { it in joinedTokenSet }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.participants_present_count, joinedCount, totalCount),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val hostPresent = isPresent(
                id = meeting.createdBy?.realId,
                email = meeting.createdBy?.email
            )
            ParticipantRow(
                name = stringResource(
                    R.string.host_name_with_role,
                    meeting.createdBy?.name ?: stringResource(R.string.unknown_user_label)
                ),
                present = hostPresent
            )

            guestParticipants.forEach { participant ->
                val guestName = participant.name?.takeIf { it.isNotBlank() }
                    ?: participant.email.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.guest_label)

                val guestPresent = isPresent(
                    id = participant.id,
                    email = participant.email.takeIf { it.isNotBlank() }
                )
                ParticipantRow(name = guestName, present = guestPresent)
            }
        }
    }
}

@Composable
fun ParticipantRow(name: String, present: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = if (present) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(name, style = MaterialTheme.typography.bodyMedium)
        }
        if (present) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.present_label),
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = stringResource(R.string.absent_label),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun NotesSection(
    meetingId: String,
    notes: List<MeetingNoteDto>,
    viewModel: InPersonMeetingViewModel,
    localNoteTimestamps: Map<String, Long>
) {
    var noteText by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.real_time_notes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                placeholder = { Text(stringResource(R.string.add_note_hint)) },
                maxLines = 3
            )

            Button(
                onClick = {
                    if (noteText.isNotBlank()) {
                        viewModel.addNote(meetingId, noteText)
                        noteText = ""
                    }
                },
                modifier = Modifier.align(Alignment.End),
                enabled = noteText.isNotBlank()
            ) {
                Text(stringResource(R.string.send_label))
            }

            if (notes.isNotEmpty()) {
                HorizontalDivider()
                notes.reversed().forEach { note ->
                    MeetingNoteCard(
                        note = note,
                        fallbackTimestampMillis = localNoteTimestamps[note.realId]
                    )
                }
            }
        }
    }
}

fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}

fun formatInPersonMeetingTime(startTime: String): String {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = sdfIn.parse(startTime)!!
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(date)
    } catch (e: Exception) {
        startTime
    }
}

fun formatInPersonMeetingEndTime(startTime: String, durationMinutes: Int): String {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = sdfIn.parse(startTime)!!
        val endDate = Date(date.time + durationMinutes * 60 * 1000L)
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getDefault()
        }.format(endDate)
    } catch (e: Exception) {
        ""
    }
}

private fun elapsedSinceMeetingStart(startTime: String): Long {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = sdfIn.parse(startTime) ?: return 0L
        val elapsedMillis = System.currentTimeMillis() - date.time
        if (elapsedMillis <= 0L) 0L else elapsedMillis / 1000L
    } catch (_: Exception) {
        0L
    }
}
