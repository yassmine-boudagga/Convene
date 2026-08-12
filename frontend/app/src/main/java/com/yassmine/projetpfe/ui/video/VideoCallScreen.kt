package com.yassmine.projetpfe.ui.video

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.ui.components.UserAvatar
import com.yassmine.projetpfe.ui.theme.ConveneTheme
import com.yassmine.projetpfe.viewmodel.VideoCallState
import com.yassmine.projetpfe.viewmodel.VideoCallViewModel
import com.yassmine.projetpfe.viewmodel.VideoParticipant
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.delay
import livekit.org.webrtc.RendererCommon
import livekit.org.webrtc.SurfaceViewRenderer

@Composable
fun VideoCallScreen(
    meetingId: String,
    onLeave: () -> Unit,
    onForceEndNavigate: (String) -> Unit,
    onNotesClick: () -> Unit,
    viewModel: VideoCallViewModel = hiltViewModel()
) {
    val context      = LocalContext.current
    val callState    by viewModel.callState.collectAsState()
    val participants by viewModel.participants.collectAsState()
    val isMuted      by viewModel.isMuted.collectAsState()
    val isCameraOff  by viewModel.isCameraOff.collectAsState()
    val isRecording  by viewModel.isRecording.collectAsState()
    val isRecordingActive by viewModel.isRecordingActive.collectAsState()
    val isStoppingRecording by viewModel.isStoppingRecording.collectAsState()
    val isHost by viewModel.isHost.collectAsState()
    val room         by viewModel.room.collectAsState()
    val intentionalDisconnect by viewModel.intentionalDisconnect.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    var showParticipants by remember { mutableStateOf(false) }
    var hasJoined        by remember { mutableStateOf(false) }
    var showForceEndDialog by remember { mutableStateOf(false) }
    var showLeaveConfirmDialog by remember { mutableStateOf(false) }
    var forceEndReason by remember { mutableStateOf("") }
    var forceEndCountdown by remember { mutableIntStateOf(5) }
    var suppressAutoLeaveNavigation by remember { mutableStateOf(false) }

    val latestOnLeave by rememberUpdatedState(onLeave)
    val latestOnForceEndNavigate by rememberUpdatedState(onForceEndNavigate)
    val latestParticipants by rememberUpdatedState(participants)
    val latestIntentionalDisconnect by rememberUpdatedState(intentionalDisconnect)

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val permissionsAlreadyGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!hasJoined) {
            hasJoined = true
            viewModel.joinMeeting(meetingId)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasJoined) {
            if (permissionsAlreadyGranted) {
                hasJoined = true
                viewModel.joinMeeting(meetingId)
            } else {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                )
            }
        }
    }

    BackHandler(enabled = callState is VideoCallState.Connected) {
        showLeaveConfirmDialog = true
    }

    LaunchedEffect(callState, suppressAutoLeaveNavigation, showForceEndDialog, connectionError) {
        if (callState is VideoCallState.Disconnected && !suppressAutoLeaveNavigation && !showForceEndDialog && !connectionError) {
            latestOnLeave()
        }
    }

    val onConfirmLeave = {
        showLeaveConfirmDialog = false
        viewModel.markIntentionalDisconnect()
        viewModel.leaveMeeting()
        latestOnLeave()
    }

    LaunchedEffect(Unit) {
        viewModel.forceEndEvent.collect { event ->
            val localParticipant = latestParticipants.firstOrNull { it.isLocal }
            val localUserId = localParticipant?.id.orEmpty()
            val localRole = localParticipant?.role?.trim()?.lowercase().orEmpty()

            val triggeredByCurrentUser =
                event.triggeredBy?.trim()?.equals(localUserId, ignoreCase = true) == true

            val normalizedReason = event.reason.trim().lowercase()
            val shouldSkipDialogByHeuristic = event.triggeredBy.isNullOrBlank() && (
                (normalizedReason == "host_left" && localRole == "host") ||
                    (normalizedReason == "all_guests_left" && localRole == "guest" && latestIntentionalDisconnect)
            )

            if (triggeredByCurrentUser || shouldSkipDialogByHeuristic) {
                viewModel.disconnectAndNavigate()
                latestOnForceEndNavigate(meetingId)
                return@collect
            }

            suppressAutoLeaveNavigation = true
            forceEndReason = when (event.reason.trim().lowercase()) {
                "host_left" -> context.getString(R.string.force_end_reason_host_left)
                "all_guests_left" -> context.getString(R.string.force_end_reason_all_left)
                "auto_finish" -> context.getString(R.string.force_end_reason_auto_finish)
                else -> context.getString(R.string.force_end_reason_default)
            }
            forceEndCountdown = event.countdown
            showForceEndDialog = true

            repeat(event.countdown) {
                delay(1000)
                forceEndCountdown = (forceEndCountdown - 1).coerceAtLeast(0)
            }

            viewModel.disconnectAndNavigate()
            showForceEndDialog = false
            latestOnForceEndNavigate(meetingId)
        }
    }

    VideoCallScreenContent(
        callState                = callState,
        participants             = participants,
        room                     = room,
        isMuted                  = isMuted,
        isCameraOff              = isCameraOff,
        isRecording              = isRecording,
        isHost                   = isHost,
        isRecordingActive        = isRecordingActive,
        isStoppingRecording      = isStoppingRecording,
        showParticipants         = showParticipants,
        onShowParticipantsChange = { showParticipants = it },
        onToggleMute             = { viewModel.toggleMute() },
        onToggleCamera           = { viewModel.toggleCamera() },
        onSwitchCamera           = { viewModel.switchCamera() },
        onStopRecording          = { viewModel.stopRecording(meetingId) },
        showLeaveConfirmDialog   = showLeaveConfirmDialog,
        onRequestLeave           = { showLeaveConfirmDialog = true },
        onDismissLeave           = { showLeaveConfirmDialog = false },
        onConfirmLeave           = onConfirmLeave,
        onNotesClick             = onNotesClick,
        intentionalDisconnect    = intentionalDisconnect,
        connectionError          = connectionError,
        onRetry = {
            hasJoined = false
            viewModel.resetIntentionalDisconnect()
            viewModel.joinMeeting(meetingId)
            hasJoined = true
        },
        showForceEndDialog       = showForceEndDialog,
        forceEndReason           = forceEndReason,
        forceEndCountdown        = forceEndCountdown,
        onForceEndConfirm        = {
            viewModel.disconnectAndNavigate()
            showForceEndDialog = false
            latestOnForceEndNavigate(meetingId)
        },
    )
}

@Composable
private fun VideoCallScreenContent(
    callState: VideoCallState,
    participants: List<VideoParticipant>,
    room: Room?,
    isMuted: Boolean,
    isCameraOff: Boolean,
    isRecording: Boolean,
    isHost: Boolean,
    isRecordingActive: Boolean,
    isStoppingRecording: Boolean,
    showParticipants: Boolean,
    onShowParticipantsChange: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onStopRecording: () -> Unit,
    showLeaveConfirmDialog: Boolean,
    onRequestLeave: () -> Unit,
    onDismissLeave: () -> Unit,
    onConfirmLeave: () -> Unit,
    onNotesClick: () -> Unit,
    intentionalDisconnect: Boolean,
    connectionError: Boolean,
    showForceEndDialog: Boolean,
    forceEndReason: String,
    forceEndCountdown: Int,
    onForceEndConfirm: () -> Unit,
    onRetry: () -> Unit
) {
    val showConnectionLostScreen = connectionError &&
        !intentionalDisconnect &&
        !showForceEndDialog

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF1A1A2E))
        .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        when {
            showConnectionLostScreen -> {
                ErrorScreen(
                    message = stringResource(R.string.connection_lost_check_network),
                    onRetry = onRetry,
                    onLeave = onConfirmLeave
                )
            }
            callState is VideoCallState.Connecting -> ConnectingScreen()
            callState is VideoCallState.Error      -> {
                if (!intentionalDisconnect) {
                    ErrorScreen((callState as VideoCallState.Error).message, onRetry, onConfirmLeave)
                }
            }
            callState is VideoCallState.Connected  -> ConnectedCallContent(
                participants        = participants,
                room                = room,
                isMuted             = isMuted,
                isCameraOff         = isCameraOff,
                onToggleMute        = onToggleMute,
                onToggleCamera      = onToggleCamera,
                onSwitchCamera      = onSwitchCamera,
                isHost              = isHost,
                isRecordingActive   = isRecordingActive,
                isStoppingRecording = isStoppingRecording,
                onStopRecording     = onStopRecording,
                onRequestLeave      = onRequestLeave,
                onNotesClick        = onNotesClick,
                onParticipantsClick = { onShowParticipantsChange(true) }
            )
            else -> {
                if (!intentionalDisconnect) {
                    ConnectingScreen()
                }
            }
        }

        if (isRecording) {
            RecordingIndicator(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp)
            )
        }

        if (showParticipants) {
            ParticipantsSheet(participants) { onShowParticipantsChange(false) }
        }

        if (showLeaveConfirmDialog) {
            AlertDialog(
                onDismissRequest = onDismissLeave,
                title = { Text(stringResource(R.string.leave_meeting_title)) },
                text = { Text(stringResource(R.string.leave_meeting_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = onConfirmLeave,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))
                    ) {
                        Text(stringResource(R.string.leave_label))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissLeave) {
                        Text(stringResource(R.string.cancel_label))
                    }
                }
            )
        }

        if (showForceEndDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.meeting_ended)) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(forceEndReason)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.redirecting_in_seconds, forceEndCountdown),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onForceEndConfirm) {
                        Text(stringResource(R.string.leave_label))
                    }
                }
            )
        }
    }
}

@Composable
private fun ConnectingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.connecting_to_meeting), color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onLeave: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFEF5350), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, color = Color.White, textAlign = TextAlign.Center)
            if (message.contains("réseau", ignoreCase = true) || message.contains("WiFi", ignoreCase = true)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.wifi_tip_stable_connection),
                    color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onLeave, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Text(stringResource(R.string.leave_label))
                }
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                    Text(stringResource(R.string.retry_label))
                }
            }
        }
    }
}

@Composable
private fun ConnectedCallContent(
    participants: List<VideoParticipant>,
    room: Room?,
    isMuted: Boolean,
    isCameraOff: Boolean,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    isHost: Boolean,
    isRecordingActive: Boolean,
    isStoppingRecording: Boolean,
    onStopRecording: () -> Unit,
    onRequestLeave: () -> Unit,
    onNotesClick: () -> Unit,
    onParticipantsClick: () -> Unit
) {
    var localRendererEpoch by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                participants.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.waiting_for_participants), color = Color.White)
                    }
                }
                participants.size == 1 -> {
                    ParticipantTile(
                        participant = participants[0],
                        room        = room,
                        rendererEpoch = if (participants[0].isLocal) localRendererEpoch else 0,
                        modifier    = Modifier.fillMaxSize()
                    )
                }
                participants.size == 2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        ParticipantTile(
                            participant = participants[0],
                            room = room,
                            rendererEpoch = if (participants[0].isLocal) localRendererEpoch else 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        HorizontalDivider(
                            thickness = 1.dp,
                            color = Color.Black.copy(alpha = 0.3f)
                        )

                        ParticipantTile(
                            participant = participants[1],
                            room = room,
                            rendererEpoch = if (participants[1].isLocal) localRendererEpoch else 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp),
                        contentPadding        = PaddingValues(4.dp)
                    ) {
                        items(participants, key = { it.id }) { participant ->
                            ParticipantTile(
                                participant = participant,
                                room        = room,
                                rendererEpoch = if (participant.isLocal) localRendererEpoch else 0,
                                modifier    = Modifier.fillMaxWidth().aspectRatio(0.75f)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${participants.size} participant(s)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xEE16213E)
                            )
                        )
                    )
                    .navigationBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.3f))
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ControlButton(Icons.Default.People, stringResource(R.string.participants_label), onParticipantsClick)
                    ControlButton(Icons.AutoMirrored.Filled.Notes, stringResource(R.string.notes_label), onNotesClick)
                    ControlButton(Icons.Default.Cameraswitch, stringResource(R.string.camera_label)) {
                        onSwitchCamera()
                        localRendererEpoch += 1
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    CallControlButton(
                        icon     = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label    = if (isMuted) stringResource(R.string.enable_label) else stringResource(R.string.mute_label),
                        isActive = !isMuted,
                        onClick  = onToggleMute
                    )
                    FloatingActionButton(
                        onClick        = onRequestLeave,
                        containerColor = Color(0xFFEF5350),
                        shape          = CircleShape,
                        elevation      = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                        modifier       = Modifier.size(60.dp)
                    ) {
                        Icon(Icons.Default.CallEnd, stringResource(R.string.leave_label), tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    CallControlButton(
                        icon     = if (isCameraOff) Icons.Default.VideocamOff else Icons.Default.Videocam,
                        label    = if (isCameraOff) stringResource(R.string.enable_label) else stringResource(R.string.disable_label),
                        isActive = !isCameraOff,
                        onClick  = onToggleCamera
                    )
                }

                if (isHost) {
                    val stopButtonEnabled = isRecordingActive && !isStoppingRecording
                    val stopButtonContainerColor = if (stopButtonEnabled) {
                        Color(0xFFEF5350)
                    } else {
                        Color(0xFF5A5A6A)
                    }
                    val stopButtonText = when {
                        isStoppingRecording -> stringResource(R.string.stopping_recording)
                        isRecordingActive -> stringResource(R.string.stop_recording)
                        else -> stringResource(R.string.recording_not_active)
                    }

                    Button(
                        onClick = onStopRecording,
                        enabled = stopButtonEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = stopButtonContainerColor,
                            disabledContainerColor = Color(0xFF5A5A6A),
                            disabledContentColor = Color.White.copy(alpha = 0.8f)
                        )
                    ) {
                        Text(text = stopButtonText)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
                         
// ParticipantTile                       
@Composable
private fun ParticipantTile(
    participant: VideoParticipant,
    room: Room?,
    rendererEpoch: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D1B2A))) {
        val videoTrack = participant.videoTrack

        if (room != null && videoTrack != null && !participant.isCameraOff) {
            key(participant.id, rendererEpoch) {
                LiveKitVideoTrackRenderer(
                    room = room,
                    videoTrack = videoTrack,
                    mirror = participant.isLocal,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(12.dp)
            ) {
                Text(
                    text     = if (participant.isLocal) stringResource(R.string.me_name_format, participant.name) else participant.name,
                    color    = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        UserAvatar(
                            profilePicture = participant.profilePicture,
                            name = participant.name,
                            size = 64.dp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (participant.isLocal) stringResource(R.string.me_name_format, participant.name) else participant.name,
                        color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = 6.dp, start = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (participant.isMuted) {
                Surface(color = Color.Black.copy(alpha = 0.7f), shape = CircleShape, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MicOff, null, tint = Color(0xFFEF5350), modifier = Modifier.size(15.dp))
                    }
                }
            }
            if (participant.isCameraOff) {
                Surface(color = Color.Black.copy(alpha = 0.7f), shape = CircleShape, modifier = Modifier.size(24.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.VideocamOff, null, tint = Color(0xFFEF5350), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }

        if (participant.role == "host") {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
                color = Color(0xFFFFB300).copy(alpha = 0.9f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(stringResource(R.string.host_label), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LiveKitVideoTrackRenderer(
    room: Room,
    videoTrack: VideoTrack,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val renderer = remember(room, context) {
        SurfaceViewRenderer(context).apply {
            room.initVideoRenderer(this)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    DisposableEffect(renderer) {
        onDispose {
            renderer.release()
        }
    }

    DisposableEffect(videoTrack, renderer) {
        videoTrack.addRenderer(renderer)
        onDispose {
            videoTrack.removeRenderer(renderer)
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier,
        update = {
            it.setMirror(mirror)
        }
    )
}
                        
// Control Buttons                          
@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(width = 60.dp, height = 56.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, label, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() }
    ) {
        Surface(
            color = if (isActive) Color(0xFF2D2D44) else Color(0xFFEF5350).copy(alpha = 0.25f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(width = 60.dp, height = 56.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun RecordingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val alpha by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label         = "rec_alpha"
    )
    Surface(
        color = Color(0xCC000000),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(Color.Red.copy(alpha = alpha), CircleShape)
            )
            Text(
                stringResource(R.string.rec_label),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                stringResource(R.string.live_label),
                color = Color(0xFFFF5252).copy(alpha = alpha),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsSheet(participants: List<VideoParticipant>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF16213E)) {
        Column(Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.People, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    stringResource(R.string.participants_sheet_count, participants.size),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            participants.forEach { participant ->
                val displayName = if (participant.isLocal) {
                    stringResource(R.string.me_name_format, participant.name)
                } else {
                    participant.name
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserAvatar(
                        profilePicture = participant.profilePicture,
                        name = participant.name,
                        size = 44.dp
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            displayName,
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (participant.role.equals("host", ignoreCase = true)) {
                                stringResource(R.string.host_label)
                            } else {
                                stringResource(R.string.guest_label)
                            },
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (participant.isMuted) {
                            Surface(
                                color = Color(0xFFEF5350).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.MicOff,
                                    null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.padding(4.dp).size(16.dp)
                                )
                            }
                        }
                        if (participant.isCameraOff) {
                            Surface(
                                color = Color(0xFFEF5350).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.VideocamOff,
                                    null,
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.padding(4.dp).size(16.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun VideoCallConnectedPreview() {
    val sampleParticipants = listOf(
        VideoParticipant("1", "Alice Martin", true,  false, false, "host",  null),
        VideoParticipant("2", "Bob Dupont",   false, true,  false, "guest", null)
    )
    ConveneTheme {
        VideoCallScreenContent(
            callState                = VideoCallState.Connected,
            participants             = sampleParticipants,
            room                     = null,
            isMuted                  = false,
            isCameraOff              = false,
            isRecording              = true,
            isHost                   = true,
            isRecordingActive        = true,
            isStoppingRecording      = false,
            showParticipants         = false,
            onShowParticipantsChange = {},
            onToggleMute             = {},
            onToggleCamera           = {},
            onSwitchCamera           = {},
            onStopRecording          = {},
            showLeaveConfirmDialog   = false,
            onRequestLeave           = {},
            onDismissLeave           = {},
            onConfirmLeave           = {},
            onNotesClick             = {},
            intentionalDisconnect    = false,
            connectionError          = false,
            showForceEndDialog       = false,
            forceEndReason           = "",
            forceEndCountdown        = 0,
            onForceEndConfirm        = {},
            onRetry                  = {}
        )
    }
}
