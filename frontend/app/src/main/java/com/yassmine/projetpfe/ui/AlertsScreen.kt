package com.yassmine.projetpfe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.NotificationDto
import com.yassmine.projetpfe.notifications.NotificationLocalization
import com.yassmine.projetpfe.ui.components.VisibleLazyColumnScrollbar
import com.yassmine.projetpfe.ui.theme.*
import com.yassmine.projetpfe.viewmodel.AppPreferencesViewModel
import com.yassmine.projetpfe.viewmodel.AlertsViewModel
import com.yassmine.projetpfe.viewmodel.SocialViewModel
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(
    onHomeClick: () -> Unit,
    onCreateClick: () -> Unit,
    onTasksClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNotificationClick: (NotificationDto) -> Unit,
    targetUserId: String? = null,
    targetNotificationId: String? = null,
    viewModel: AlertsViewModel = hiltViewModel(),
    appPreferencesViewModel: AppPreferencesViewModel = hiltViewModel(),
    socialViewModel: SocialViewModel = hiltViewModel()
) {
    val uiState     by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    val scope = rememberCoroutineScope()

    // Pagination
    val reachedBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom && uiState.hasMore && !uiState.isLoadingMore) {
            viewModel.loadMoreNotifications()
        }
    }

    val displayedNotifications = if (selectedTab == 1) {
        uiState.notifications.filter { !it.isRead }
    } else {
        uiState.notifications
    }
    val currentLang by appPreferencesViewModel.appLanguage.collectAsState()

    LaunchedEffect(targetNotificationId, targetUserId, uiState.notifications) {
        val socialTypes = setOf("friend_request", "friend_accepted", "friend_rejected")
        val target = when {
            !targetNotificationId.isNullOrBlank() -> {
                uiState.notifications.firstOrNull { it.id == targetNotificationId }
            }
            !targetUserId.isNullOrBlank() -> {
                uiState.notifications.firstOrNull {
                    it.type in socialTypes && it.data?.fromUserId == targetUserId
                }
            }
            else -> null
        }

        if (target != null && !target.isRead) {
            viewModel.markAsRead(target.id)
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavBar(
                selectedIndex = 3,
                onIndexSelected = { index ->
                    when (index) {
                        0 -> onHomeClick()
                        1 -> onCreateClick()
                        2 -> onTasksClick()
                        3 -> {}
                        4 -> onProfileClick()
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundLight)
        ) {
            // HEADER 
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(White)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(id = R.string.alerts_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        stringResource(id = R.string.alerts_unread_count, uiState.unreadCount),
                        fontSize = 13.sp,
                        color = TextGray
                    )
                }
                TextButton(onClick = { viewModel.markAllAsRead() }) {
                    Text(
                        stringResource(id = R.string.alerts_mark_all_read),
                        color = PrimaryBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // TABS 
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TabChip(stringResource(id = R.string.alerts_tab_all), selectedTab == 0, badge = null) { selectedTab = 0 }
                TabChip(stringResource(id = R.string.alerts_tab_unread), selectedTab == 1,
                    badge = if (uiState.unreadCount > 0) uiState.unreadCount else null
                ) { selectedTab = 1 }
            }

            Spacer(Modifier.height(16.dp))

            // LOADING 
            if (uiState.isLoading && uiState.notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
                return@Column
            }

            // ERREUR 
            val errorMsg = uiState.errorMessage
            if (errorMsg != null && uiState.notifications.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WifiOff, null, tint = TextLight, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(errorMsg, color = TextGray, fontSize = 14.sp)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.loadNotifications() }) {
                            Text(stringResource(id = R.string.common_retry), color = PrimaryBlue)
                        }
                    }
                }
                return@Column
            }

            // LISTE 
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (displayedNotifications.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Notifications, null, tint = TextLight, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text(stringResource(id = R.string.alerts_empty), color = TextGray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        items(displayedNotifications, key = { it.id }) { notification ->
                            val fromUserId = notification.data?.fromUserId
                            NotificationItem(
                                notification = notification,
                                currentLang = currentLang,
                                actionTaken = notification.actionTaken,
                                onClick = {
                                    viewModel.markAsRead(notification.id)
                                    onNotificationClick(notification)
                                },
                                onDelete = { viewModel.deleteNotification(notification.id) },
                                onAcceptRequest = if (
                                    notification.type == "friend_request" &&
                                    !fromUserId.isNullOrBlank()
                                ) {
                                    {
                                        socialViewModel.acceptRequest(fromUserId)
                                        viewModel.markNotifAction(notification.id, "accepted")
                                    }
                                } else null,
                                onRejectRequest = if (
                                    notification.type == "friend_request" &&
                                    !fromUserId.isNullOrBlank()
                                ) {
                                    {
                                        socialViewModel.rejectRequest(fromUserId)
                                        viewModel.markNotifAction(notification.id, "rejected")
                                    }
                                } else null
                            )
                        }
                        // Spinner pagination
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = PrimaryBlue,
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }
                }

                VisibleLazyColumnScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 8.dp)
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } }
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(id = R.string.common_scroll_to_top))
                    }
                }
            }
        }
    }
}

// Tab chip
@Composable
private fun RowScope.TabChip(
    label:    String,
    selected: Boolean,
    badge:    Int?,
    onClick:  () -> Unit
) {
    Surface(
        modifier = Modifier.weight(1f).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() },
        shape    = RoundedCornerShape(12.dp),
        color    = if (selected) White else Color.Transparent,
        border   = if (selected) null else
            androidx.compose.foundation.BorderStroke(1.dp, TextLight.copy(alpha = 0.3f))
    ) {
        Box(Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    label,
                    fontSize   = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (selected) PrimaryBlue else TextGray
                )
                if (badge != null) {
                    Box(
                        Modifier.size(20.dp).background(PrimaryBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(badge.toString(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = White)
                    }
                }
            }
        }
    }
}

// NotificationItem 
@Composable
fun NotificationItem(
    notification: NotificationDto,
    currentLang: String,
    actionTaken: String?,
    onClick:  () -> Unit,
    onDelete: () -> Unit,
    onAcceptRequest: (() -> Unit)? = null,
    onRejectRequest: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val (localTitle, localBody) = remember(notification.id, currentLang, notification.type, notification.data) {
        val dataMap = mutableMapOf<String, Any?>()
        dataMap["title"] = notification.title
        dataMap["message"] = notification.message
        notification.data?.meetingId?.let { dataMap["meetingId"] = it }
        notification.data?.meetingTitle?.let { dataMap["meetingTitle"] = it }
        notification.data?.startTime?.let {
            dataMap["startTime"] = it
            dataMap["start_time"] = it
            dataMap["meetingStartTime"] = it
        }
        notification.data?.fromUserName?.let { dataMap["fromUserName"] = it }
        notification.data?.organizerName?.let { dataMap["organizerName"] = it }

        val displayName = (dataMap["organizerName"] as? String)
            ?: (dataMap["fromUserName"] as? String)

        displayName?.let { dataMap["organizerName"] = it }

        NotificationLocalization.buildLocalizedNotification(
            context = context,
            type = notification.type,
            data = dataMap
        )
    }

    // admin_broadcast a un contenu libre
    // bypass total de la localisation
    val titleToShow = if (notification.type == "admin_broadcast") {
        notification.title.ifBlank { context.getString(R.string.notif_default_title) }
    } else when {
        localTitle.isNotBlank() -> localTitle
        notification.title.isNotBlank() -> notification.title
        else -> context.getString(R.string.notif_default_title)
    }
    val bodyToShow = if (notification.type == "admin_broadcast") {
        notification.message.ifBlank { context.getString(R.string.notif_generic_body) }
    } else when {
        localBody.isNotBlank() -> localBody
        notification.message.isNotBlank() -> notification.message
        else -> context.getString(R.string.notif_generic_body)
    }

    Surface(modifier = Modifier.fillMaxWidth().clickable { onClick() }, color = White) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val (bgColor, iconColor, icon) = when (notification.type) {
                "meeting_created", "meeting_updated" ->
                    Triple(Color(0xFFEFF6FF), PrimaryBlue, Icons.Default.CalendarMonth)
                "meeting_starting" ->
                    Triple(Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Default.Alarm)
                "meeting_cancelled" ->
                    Triple(Color(0xFFFEE2E2), Color(0xFFEF4444), Icons.Default.Cancel)
                "recording_started", "recording_ready" ->
                    Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), Icons.Default.FiberManualRecord)
                "friend_request" ->
                    Triple(Color(0xFFFFF7ED), Color(0xFFEA580C), Icons.Default.PersonAdd)
                "friend_accepted" ->
                    Triple(Color(0xFFECFDF3), Color(0xFF16A34A), Icons.Default.CheckCircle)
                "friend_rejected" ->
                    Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), Icons.Default.Cancel)
                else ->
                    Triple(Color(0xFFF3E8FF), Color(0xFF8B5CF6), Icons.Default.Notifications)
            }

            Box(
                Modifier.size(44.dp).background(bgColor, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(titleToShow, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Spacer(Modifier.height(4.dp))
                Text(bodyToShow, fontSize = 13.sp, color = TextGray, lineHeight = 18.sp)
                if (notification.type == "friend_request" && onAcceptRequest != null) {
                    Spacer(Modifier.height(8.dp))
                    when {
                        actionTaken == "accepted" -> {
                            Text(
                                stringResource(id = R.string.alerts_friend_accepted_label),
                                color = OnlineGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        actionTaken == "rejected" -> {
                            Text(
                                stringResource(id = R.string.alerts_friend_rejected_label),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        else -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onAcceptRequest,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(stringResource(id = R.string.alerts_accept), color = White, fontSize = 12.sp)
                                }

                                if (onRejectRequest != null) {
                                    OutlinedButton(
                                        onClick = onRejectRequest,
                                        shape = RoundedCornerShape(20.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(stringResource(id = R.string.alerts_reject), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(formatTimeAgo(notification.createdAt), fontSize = 11.sp, color = TextLight)
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!notification.isRead) {
                    Box(Modifier.size(10.dp).background(PrimaryBlue, CircleShape))
                } else {
                    Spacer(Modifier.size(10.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        stringResource(id = R.string.alerts_delete_notification),
                        tint = TextLight,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            modifier  = Modifier.padding(start = 74.dp),
            thickness = 1.dp,
            color     = TextLight.copy(alpha = 0.15f)
        )
    }
}

// Helper date
@Composable
private fun formatTimeAgo(isoDate: String): String {
    val diff = try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoDate)
        if (date == null) -1L else (System.currentTimeMillis() - date.time) / 1000L
    } catch (_: Exception) {
        -1L
    }

    if (diff < 0) {
        return stringResource(id = R.string.alerts_just_now)
    }

    return when {
        diff < 60 -> stringResource(id = R.string.alerts_just_now)
        diff < 3600 -> stringResource(id = R.string.alerts_minutes_ago, diff / 60)
        diff < 86400 -> stringResource(id = R.string.alerts_hours_ago, diff / 3600)
        diff < 172800 -> stringResource(id = R.string.alerts_yesterday)
        else -> stringResource(id = R.string.alerts_days_ago, diff / 86400)
    }
}