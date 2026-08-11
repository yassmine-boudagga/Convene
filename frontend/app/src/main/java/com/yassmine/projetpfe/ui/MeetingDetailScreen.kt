package com.yassmine.projetpfe.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.MeetingCreatorDto
import com.yassmine.projetpfe.data.api.MeetingDto
import com.yassmine.projetpfe.data.api.MeetingPermissionsDto
import com.yassmine.projetpfe.data.api.joinedPresenceTokenSet
import com.yassmine.projetpfe.data.api.participantPresenceCandidates
import com.yassmine.projetpfe.data.model.*
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.UserAvatar
import com.yassmine.projetpfe.ui.components.showSuccess
import com.yassmine.projetpfe.ui.theme.*
import com.yassmine.projetpfe.viewmodel.MeetingViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(
    meetingId: String,
    onBack: () -> Unit,
    onJoinMeeting: (String, String, String) -> Unit,
    onEditMeeting: (String) -> Unit = {},
    onSummaryClick: (String, String) -> Unit = { _, _ -> },
    meetingRefreshFlag: Boolean = false,
    onRefreshHandled: () -> Unit = {},
    viewModel: MeetingViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val selectedMeeting  by viewModel.selectedMeeting.collectAsState()
    val isLoading        by viewModel.isLoading.collectAsState()
    val currentUserId    by viewModel.currentUserId.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()
    val isSummaryAvailable by viewModel.isSummaryAvailable.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val recordingUrl by viewModel.recordingUrl.collectAsState()
    val recordingStatusResponse by viewModel.recordingStatus.collectAsState()
    val recordingStatus = recordingStatusResponse?.status
    val recordingLoading by viewModel.recordingLoading.collectAsState()
    val authToken by viewModel.authToken.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(meetingId, meetingRefreshFlag) {
        if (meetingRefreshFlag) {
            onRefreshHandled()
        }
        viewModel.loadMeeting(meetingId)
        viewModel.startMeetingDetailRealtime(meetingId)
    }

    // Charge le recording seulement quand le token est prêt (évite 401 sporadique)
    LaunchedEffect(meetingId, authToken) {
        if (authToken != null) {
            viewModel.loadRecordingInfo(meetingId)
        }
    }

    // Si la réunion est terminée et que le recording n'est pas
    // encore disponible au chargement, lancer un retry proactif
    LaunchedEffect(meetingId, selectedMeeting?.status) {
        if (selectedMeeting?.status == "finished" &&
            recordingUrl.isNullOrBlank()
        ) {
            viewModel.retryLoadRecordingInfoIfNeeded(meetingId)
        }
    }

    DisposableEffect(lifecycleOwner, meetingId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadMeeting(meetingId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(operationSuccess) {
        if (operationSuccess) {
            viewModel.resetOperationSuccess()
            onBack()
        }
    }

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSuccess(message)
        viewModel.clearSnackbarMessage()
    }

    MeetingDetailScreenContent(
        meeting       = selectedMeeting,
        isLoading     = isLoading,
        currentUserId = currentUserId,
        onBack        = onBack,
        onJoinMeeting = onJoinMeeting,
        onEditMeeting = { onEditMeeting(meetingId) },
        isSummaryAvailable = isSummaryAvailable,
        recordingUrl = recordingUrl,
        recordingStatus = recordingStatus,
        authToken = authToken,
        recordingLoading = recordingLoading,
        onSummaryClick = { title -> onSummaryClick(meetingId, title) },
        onCancel      = { viewModel.cancelMeeting(meetingId) },
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingDetailScreenContent(
    meeting: MeetingDto?,
    isLoading: Boolean,
    currentUserId: String?,
    onBack: () -> Unit,
    onJoinMeeting: (String, String, String) -> Unit,
    onEditMeeting: () -> Unit,
    isSummaryAvailable: Boolean,
    recordingUrl: String?,
    recordingStatus: String? = null,
    authToken: String?,
    recordingLoading: Boolean,
    onSummaryClick: (String) -> Unit,
    onCancel: () -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    if (meeting == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isLoading) CircularProgressIndicator(color = PrimaryBlue)
            else Text(stringResource(id = R.string.meeting_detail_not_found), color = TextGray)
        }
        return
    }

    val uiStatus = when (meeting.status) {
        "ongoing"  -> MeetingStatus.ONGOING
        "finished" -> MeetingStatus.FINISHED
        "archived" -> MeetingStatus.ARCHIVED
        else       -> MeetingStatus.UPCOMING
    }
    val isHost      = meeting.userRole == "host" || meeting.createdBy?.realId == currentUserId
    val permissions = meeting.permissions
    val canJoin     = (permissions?.canJoin == true) || meeting.status == "ongoing"
    val displayDate = formatMeetingDate(meeting.startTime)
    val displayTime = formatMeetingTime(meeting.startTime)
    val detailAccentColor = when (uiStatus) {
        MeetingStatus.UPCOMING -> PrimaryBlue
        MeetingStatus.ONGOING -> OnlineGreen
        MeetingStatus.FINISHED, MeetingStatus.ARCHIVED -> TextGray
    }
    val displayDuration = when {
        meeting.duration < 60 -> stringResource(
            id = R.string.meeting_detail_duration_minutes,
            meeting.duration
        )
        meeting.duration % 60 == 0 -> stringResource(
            id = R.string.meeting_detail_duration_hours,
            meeting.duration / 60
        )
        else -> stringResource(
            id = R.string.meeting_detail_duration_hours_minutes,
            meeting.duration / 60,
            meeting.duration % 60
        )
    }

    var showCancelDialog by remember { mutableStateOf(false) }
    var showRecordingSheet by remember { mutableStateOf(false) }

    // Dialogue annulation (SCHEDULED) soft-delete
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(stringResource(id = R.string.meeting_detail_cancel_title)) },
            text  = { Text(stringResource(id = R.string.meeting_detail_cancel_message)) },
            confirmButton = {
                TextButton(onClick = { showCancelDialog = false; onCancel() }) {
                    Text(stringResource(id = R.string.common_confirm), color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(stringResource(id = R.string.meeting_detail_back), color = TextGray)
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.meeting_detail_back),
                            tint = TextDark
                        )
                    }
                },
                actions = {
                    // UPCOMING : modifier + annuler
                    if (uiStatus == MeetingStatus.UPCOMING && isHost) {
                        IconButton(onClick = onEditMeeting) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.meeting_detail_edit),
                                tint = PrimaryBlue
                            )
                        }
                        IconButton(onClick = { showCancelDialog = true }) {
                            Icon(
                                Icons.Default.Cancel,
                                contentDescription = stringResource(id = R.string.meeting_detail_cancel),
                                tint = ErrorRed
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        },

        bottomBar = {
            when (uiStatus) {
                MeetingStatus.ONGOING -> {
                    if (canJoin) {
                        Surface(color = White, shadowElevation = 8.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                                Button(
                                    onClick  = { onJoinMeeting(meeting.realId, meeting.meetingType, meeting.title) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .shadow(4.dp, RoundedCornerShape(18.dp))
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            brush = Brush.horizontalGradient(
                                                listOf(
                                                    OnlineGreen,
                                                    OnlineGreen.copy(alpha = 0.8f)
                                                )
                                            )
                                        ),
                                    shape    = RoundedCornerShape(18.dp),
                                    colors   = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent,
                                        contentColor   = White
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Icon(Icons.Default.VideoCall, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (meeting.meetingType == "physical") {
                                            stringResource(id = R.string.meeting_detail_join_in_person)
                                        } else {
                                            stringResource(id = R.string.meeting_detail_join_meeting)
                                        },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                MeetingStatus.UPCOMING -> {
                    if (isHost) {
                        Surface(color = White, shadowElevation = 8.dp) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Modifier
                                OutlinedButton(
                                    onClick  = onEditMeeting,
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape    = RoundedCornerShape(18.dp),
                                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                                    border   = androidx.compose.foundation.BorderStroke(
                                        1.5.dp, PrimaryBlue.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(id = R.string.meeting_detail_edit),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Button(
                                    onClick  = { showCancelDialog = true },
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape    = RoundedCornerShape(18.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = White),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(id = R.string.meeting_detail_cancel),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                // FIX : PAST & ARCHIVED bottomBar vide, aucun bouton d'action destructrice
                MeetingStatus.FINISHED, MeetingStatus.ARCHIVED -> {}
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            //  HEADER
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = when (uiStatus) {
                                MeetingStatus.ONGOING  -> OnlineGreenBg
                                MeetingStatus.UPCOMING -> UpcomingBlueBg
                                MeetingStatus.FINISHED, MeetingStatus.ARCHIVED -> PastGrayBg
                            },
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (meeting.meetingType == "online") {
                            Icons.Default.VideoCall
                        } else {
                            Icons.Default.LocationOn
                        },
                        contentDescription = null,
                        tint = when (uiStatus) {
                            MeetingStatus.ONGOING  -> OnlineGreen
                            MeetingStatus.UPCOMING -> PrimaryBlue
                            MeetingStatus.FINISHED, MeetingStatus.ARCHIVED -> TextGray
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
                Column {
                    Text(meeting.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Spacer(Modifier.height(4.dp))
                    StatusBadge(status = uiStatus)
                    if (meeting.meetingType == "physical" && !meeting.location.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(
                                    text = meeting.location,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = UpcomingBlueBg,
                                disabledLabelColor = PrimaryBlue,
                                disabledLeadingIconContentColor = PrimaryBlue
                            )
                        )
                    }
                }
            }

            // HOST INFO (participants only)
            if (!isHost) {
                Card(
                    shape  = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = UpcomingBlueBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Person, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        Text(
                            stringResource(
                                id = R.string.meeting_detail_hosted_by,
                                meeting.createdBy?.name
                                    ?: stringResource(id = R.string.meeting_detail_unknown_user)
                            ),
                            color = PrimaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            //   DETAILS CARD  
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DetailRow(
                        icon = Icons.Default.CalendarMonth,
                        label = stringResource(id = R.string.meeting_detail_label_date),
                        value = displayDate,
                        iconColor = detailAccentColor
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    DetailRow(
                        icon = Icons.Default.AccessTime,
                        label = stringResource(id = R.string.meeting_detail_label_time),
                        value = displayTime,
                        iconColor = detailAccentColor
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    DetailRow(
                        icon = Icons.Default.Timer,
                        label = stringResource(id = R.string.meeting_detail_label_duration),
                        value = displayDuration,
                        iconColor = detailAccentColor
                    )
                    if (meeting.description?.isNotBlank() == true) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                        DetailRow(
                            icon = Icons.Default.Description,
                            label = stringResource(id = R.string.meeting_detail_label_description),
                            value = meeting.description,
                            iconColor = detailAccentColor
                        )
                    }
                }
            }

            ParticipantListSection(
                meeting = meeting,
                currentUserId = currentUserId,
                uiStatus = uiStatus
            )

            //   ONGOING INFO BANNER  
            if (uiStatus == MeetingStatus.ONGOING && isHost) {
                val pulseTransition = rememberInfiniteTransition(label = "hostPulse")
                val pulseScale by pulseTransition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 900),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "hostPulseScale"
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = OnlineGreenBg),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        OnlineGreen.copy(alpha = 0.45f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.RadioButtonChecked,
                            null,
                            tint = OnlineGreen,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                        )
                        Text(
                            stringResource(id = R.string.meeting_detail_ongoing_host_info),
                            color = OnlineGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            //   RECORDING  
            if (!meeting.recordingUrl.isNullOrBlank()) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = OnlineGreenBg)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FiberManualRecord, null, tint = OnlineGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(id = R.string.meeting_detail_recording_available),
                            color = OnlineGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (uiStatus == MeetingStatus.FINISHED || uiStatus == MeetingStatus.ARCHIVED) {
                if (recordingLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    when (meeting.aiStatus) {
                        "completed_empty" -> {
                            // Cas silencieux : pas de lecteur, juste le message
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.MicOff, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(id = R.string.meeting_detail_no_summary_silent_audio),
                                    fontSize = 15.sp
                                )
                            }
                        }

                        "failed" -> {
                            // Afficher le lecteur si disponible + bouton erreur
                            if (!recordingUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { showRecordingSheet = true },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(id = R.string.meeting_detail_listen_recording), fontSize = 15.sp)
                                }
                            } else if (recordingStatus == "failed") {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.MicOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                        Text(stringResource(id = R.string.meeting_detail_no_recording_silent), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    }
                                }
                            }
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    disabledContentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(id = R.string.meeting_detail_ai_analysis_failed), fontSize = 15.sp)
                            }
                        }

                        else -> {
                            // Tous les autres cas (completed, processing, not_started, null) :
                            // Le lecteur recording ET l'état summary ne s'affichent QUE si recordingUrl est disponible
                            if (!recordingUrl.isNullOrBlank()) {
                                // Lecteur recording
                                OutlinedButton(
                                    onClick = { showRecordingSheet = true },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(id = R.string.meeting_detail_listen_recording), fontSize = 15.sp)
                                }

                                // État summary conditionné à la présence du recording
                                when (meeting.aiStatus) {
                                    "completed" -> {
                                        Button(
                                            onClick = { onSummaryClick(meeting.title) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(54.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.colorScheme.secondary
                                                        )
                                                    )
                                                ),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color.Transparent,
                                                contentColor = White
                                            )
                                        ) {
                                            Icon(Icons.Default.Article, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                stringResource(id = R.string.meeting_detail_view_ai_summary),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    "processing" -> {
                                        Button(
                                            onClick = {},
                                            enabled = false,
                                            modifier = Modifier.fillMaxWidth().height(52.dp),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(8.dp))
                                            Text(stringResource(id = R.string.meeting_detail_summary_processing), fontSize = 15.sp)
                                        }
                                    }

                                    // not_started, null : recording accessible mais summary pas encore lancé
                                    // → n'afficher aucun bouton summary, le lecteur seul suffit
                                    else -> { /* rien */ }
                                }
                            } else {
                                // recordingUrl null : recording pas encore disponible
                                // Si recordingStatus == "failed" afficher le message d'erreur
                                if (recordingStatus == "failed") {
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        elevation = CardDefaults.cardElevation(0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.MicOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                            Text(stringResource(id = R.string.meeting_detail_no_recording_silent), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                        }
                                    }
                                }
                                // Sinon : recording en attente de disponibilité — ne rien afficher
                                // (pas de bouton summary, pas de lecteur, pas de message d'erreur prématuré)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // Bottom sheet recording - en dehors du Scaffold content
    if (showRecordingSheet && !recordingUrl.isNullOrBlank()) {
        com.yassmine.projetpfe.ui.components.RecordingBottomSheet(
            audioUrl = recordingUrl,
            token = authToken,
            onDismiss = { showRecordingSheet = false }
        )
    }
}

//   Helpers                      

private fun formatMeetingDate(isoString: String): String {
    return try {
        Instant.parse(isoString).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    } catch (_: Exception) { isoString.take(10) }
}

private fun formatMeetingTime(isoString: String): String {
    return try {
        Instant.parse(isoString).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { isoString.drop(11).take(5) }
}

//   Composables             

@Composable
fun StatusBadge(status: MeetingStatus) {
    val (bg, text, label) = when (status) {
        MeetingStatus.UPCOMING -> Triple(
            UpcomingBlueBg,
            PrimaryBlue,
            stringResource(id = R.string.meeting_detail_status_upcoming)
        )
        MeetingStatus.ONGOING  -> Triple(
            OnlineGreenBg,
            OnlineGreen,
            stringResource(id = R.string.meeting_detail_status_ongoing)
        )
        MeetingStatus.FINISHED -> Triple(
            PastGrayBg,
            TextGray,
            stringResource(id = R.string.meeting_detail_status_past)
        )
        MeetingStatus.ARCHIVED -> Triple(
            Color(0xFFF3E8FF),
            Color(0xFF7C3AED),
            stringResource(id = R.string.meeting_detail_status_archived)
        )
    }
    Surface(color = bg, shape = RoundedCornerShape(20.dp)) {
        Text(label, color = text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable
fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconColor: androidx.compose.ui.graphics.Color = PrimaryBlue
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(iconColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(18.dp)) }
            Text(label, color = TextGray, fontSize = 14.sp)
        }
        Text(value, color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ParticipantListSection(
    meeting: MeetingDto,
    currentUserId: String?,
    uiStatus: MeetingStatus
) {
    val organizer = meeting.createdBy
    val participantUsersByEmail = remember(meeting.participantUsers) {
        meeting.participantUsers
            .mapNotNull { user ->
                val email = user.email.trim().lowercase()
                if (email.isBlank()) null else email to user
            }
            .toMap()
    }
    val participantItems = remember(meeting.participants, meeting.participantUsers, organizer?.email) {
        val organizerEmail = organizer?.email?.trim()?.lowercase()
        val baseEmails = if (meeting.participants.isNotEmpty()) {
            meeting.participants
        } else {
            meeting.participantUsers.map { it.email }
        }

        baseEmails
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { email ->
                !organizerEmail.isNullOrBlank() && email.equals(organizerEmail, ignoreCase = true)
            }
            .distinctBy { it.lowercase() }
            .map { email ->
                val resolved = participantUsersByEmail[email.lowercase()]
                ParticipantListItem(
                    id = resolved?.id,
                    email = email,
                    profilePicture = resolved?.profilePicture,
                    displayName = resolved?.name?.takeIf { it.isNotBlank() }
                        ?: email
                )
            }
    }
    val joinedSet = remember(meeting.joinedParticipants) {
        meeting.joinedPresenceTokenSet()
    }
    val totalCount = participantItems.size + if (organizer != null) 1 else 0

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.People,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    stringResource(R.string.meeting_detail_label_participants),
                    color = TextGray,
                    fontSize = 15.sp
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "$totalCount",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            organizer?.let { host ->
                val isCurrentUser = host.realId == currentUserId || host.id == currentUserId || host.email == currentUserId
                val hasJoined = uiStatus == MeetingStatus.ONGOING &&
                    participantPresenceCandidates(host.realId, host.email.takeIf { it.isNotBlank() })
                        .any { it in joinedSet }

                ParticipantRow(
                    name = if (isCurrentUser) {
                        "${host.name} (${stringResource(R.string.meeting_detail_you)})"
                    } else {
                        host.name
                    },
                    profilePicture = host.profilePicture,
                    role = stringResource(R.string.meeting_detail_role_organizer),
                    joinStatus = if (uiStatus == MeetingStatus.ONGOING) hasJoined else null,
                    isCurrentUser = isCurrentUser
                )
            }

            participantItems.forEachIndexed { index, participant ->
                if (index > 0 || organizer != null) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }

                val hasJoined = uiStatus == MeetingStatus.ONGOING &&
                    participantPresenceCandidates(participant.id, participant.email.takeIf { it.isNotBlank() })
                        .any { it in joinedSet }
                val isCurrentUser = participant.id == currentUserId || participant.email == currentUserId

                ParticipantRow(
                    name = if (isCurrentUser) {
                        "${participant.displayName} (${stringResource(R.string.meeting_detail_you)})"
                    } else {
                        participant.displayName
                    },
                    profilePicture = participant.profilePicture,
                    role = stringResource(R.string.meeting_detail_role_participant),
                    joinStatus = if (uiStatus == MeetingStatus.ONGOING) hasJoined else null,
                    isCurrentUser = isCurrentUser
                )
            }
        }
    }
}

private data class ParticipantListItem(
    val id: String?,
    val email: String,
    val profilePicture: String?,
    val displayName: String
)

@Composable
fun ParticipantRow(
    name: String,
    profilePicture: String?,
    role: String,
    joinStatus: Boolean?,
    isCurrentUser: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UserAvatar(
            profilePicture = profilePicture,
            name = name,
            size = 38.dp
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                color = TextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(role, color = TextGray, fontSize = 12.sp)
        }

        joinStatus?.let { joined ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (joined) OnlineGreen else TextGray.copy(alpha = 0.5f),
                            CircleShape
                        )
                )
                Text(
                    text = if (joined) {
                        stringResource(R.string.meeting_detail_joined)
                    } else {
                        stringResource(R.string.meeting_detail_waiting)
                    },
                    color = if (joined) OnlineGreen else TextGray,
                    fontSize = 12.sp,
                    fontWeight = if (joined) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

//   Preview                      

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun MeetingDetailScreenPreview() {
    val sampleMeeting = MeetingDto(
        id = "meeting123", title = " Strategy Planning",
        description = "Discuss quarterly goals and roadmap",
        startTime = "2026-02-25T13:00:00.000Z", duration = 60,
        createdBy = MeetingCreatorDto(id = "user1", name = "Alice Martin", email = "alice@company.com"),
        participants = listOf("bob@company.com", "clara@company.com"),
        roomId = "room-abc-123", recordingId = null,
        status = "scheduled", joinedParticipants = emptyList(), notes = emptyList(),
        recordingStartedAt = null, recordingStoppedAt = null, recordingUrl = null,
        recordingDuration = null,
        createdAt = "2026-02-20T10:00:00Z", updatedAt = "2026-02-20T10:00:00Z",
        permissions = MeetingPermissionsDto(canJoin = false, canEdit = true, canCancel = true),
        userRole = "host"
    )
    ConveneTheme {
        MeetingDetailScreenContent(
            meeting = sampleMeeting, isLoading = false, currentUserId = "user1",
            onBack = {}, onJoinMeeting = { _, _, _ -> }, onEditMeeting = {},
            isSummaryAvailable = false,
            recordingUrl = null,
            authToken = null,
            recordingLoading = false,
            onSummaryClick = {},
            onCancel = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
