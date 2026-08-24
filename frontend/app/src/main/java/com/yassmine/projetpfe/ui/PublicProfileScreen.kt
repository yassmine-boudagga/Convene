package com.yassmine.projetpfe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yassmine.projetpfe.BuildConfig
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.AchievementMeta
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.components.showSuccess
import com.yassmine.projetpfe.ui.theme.*
import com.yassmine.projetpfe.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val profile by viewModel.viewedProfile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()
    var showRemoveFriendConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(userId) { viewModel.loadProfile(userId) }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showError(it); viewModel.clearError() }
    }
    LaunchedEffect(operationSuccess) {
        operationSuccess?.let { snackbarHostState.showSuccess(it); viewModel.clearSuccess() }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.public_profile_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.public_profile_back),
                            tint = TextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isLoading && profile == null) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 80.dp),
                    color = PrimaryBlue
                )
            } else if (profile != null) {
                val p = profile!!
                val initials = if (p.name.isNotBlank()) {
                    p.name.split(" ")
                        .mapNotNull { it.firstOrNull() }
                        .take(2)
                        .joinToString("")
                        .uppercase()
                } else "?"
                val avatarUrl = buildAvatarUrl(p.profilePicture)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    //  Avatar 
                    if (!avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(id = R.string.public_profile_photo_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                initials,
                                fontSize = 36.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    //  Name + jobTitle 
                    Text(p.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    if (!p.jobTitle.isNullOrBlank()) {
                        Text(p.jobTitle, fontSize = 14.sp, color = PrimaryBlue)
                    }
                    if (!p.company.isNullOrBlank()) {
                        Text(p.company, fontSize = 13.sp, color = TextGray)
                    }
                    if (p.email.isNotBlank()) {
                        Text(p.email, fontSize = 12.sp, color = TextGray)
                    }

                    //  Action button 
                    if (p.friendStatus != "self") {
                        if (p.friendStatus == "friends") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {},
                                    enabled = false,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        disabledContainerColor = OnlineGreen.copy(alpha = 0.2f),
                                        disabledContentColor = OnlineGreen
                                    )
                                ) {
                                    Text(stringResource(id = R.string.public_profile_friends))
                                }

                                OutlinedButton(
                                    onClick = { showRemoveFriendConfirm = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                                ) {
                                    Text(stringResource(id = R.string.public_profile_remove))
                                }
                            }

                            if (showRemoveFriendConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showRemoveFriendConfirm = false },
                                    title = { Text(stringResource(id = R.string.public_profile_remove_friend_title)) },
                                    text = {
                                        Text(
                                            stringResource(
                                                id = R.string.public_profile_remove_friend_message,
                                                p.name
                                            )
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showRemoveFriendConfirm = false
                                            viewModel.removeFriend(p.id)
                                        }) {
                                            Text(
                                                stringResource(id = R.string.public_profile_remove_friend_confirm),
                                                color = Color.Red
                                            )
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showRemoveFriendConfirm = false }) {
                                            Text(stringResource(id = R.string.common_cancel))
                                        }
                                    }
                                )
                            }
                        } else {
                            FriendActionButton(
                                friendStatus = p.friendStatus,
                                onClick = {
                                    when (p.friendStatus) {
                                        "none" -> viewModel.sendFriendRequest(p.id)
                                        "pending_sent" -> viewModel.cancelFriendRequest(p.id)
                                        "pending_received" -> viewModel.acceptRequest(p.id)
                                        else -> {}
                                    }
                                },
                                onRejectClick = {
                                    if (p.friendStatus == "pending_received") {
                                        viewModel.rejectRequest(p.id)
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = TextLight.copy(alpha = 0.3f))

                    //  Bio 
                    if (!p.bio.isNullOrBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = White),
                            elevation = CardDefaults.cardElevation(1.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(id = R.string.public_profile_about),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextGray
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(p.bio, fontSize = 14.sp, color = TextDark)
                            }
                        }
                    }

                    //  Stats 
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem(
                                label = stringResource(id = R.string.public_profile_stat_organized),
                                value = p.meetingsOrganized.toString(),
                                meetingsLabel = stringResource(id = R.string.public_profile_meetings)
                            )
                            StatItem(
                                label = stringResource(id = R.string.public_profile_stat_attended),
                                value = p.meetingsAttended.toString(),
                                meetingsLabel = stringResource(id = R.string.public_profile_meetings)
                            )
                        }
                    }

                    //  Badges 
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.public_profile_badges_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextDark
                            )

                            val unlockedAchievements = p.achievements.filter {
                                it.unlocked || it.current >= it.target || !it.unlockedAt.isNullOrBlank()
                            }

                            if (unlockedAchievements.isEmpty()) {
                                Text(
                                    text = stringResource(id = R.string.public_profile_no_badge_month),
                                    fontSize = 12.sp,
                                    color = TextGray
                                )
                            } else {
                                unlockedAchievements.chunked(2).forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        row.forEach { achievement ->
                                            val meta = AchievementMeta.map[achievement.id]
                                            Card(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(120.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(12.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(text = meta?.emoji ?: "🏅", fontSize = 24.sp)
                                                    Text(
                                                        text = achievement.name,
                                                        fontSize = 12.sp,
                                                        color = TextDark,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "✓",
                                                        color = Color(0xFF22C55E),
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        if (row.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (error != null) {
                Text(
                    stringResource(id = R.string.public_profile_not_found),
                    color = ErrorRed,
                    modifier = Modifier.padding(top = 80.dp)
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, meetingsLabel: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
        Text(meetingsLabel, fontSize = 10.sp, color = TextGray)
        Text(label, fontSize = 11.sp, color = TextDark, fontWeight = FontWeight.Medium)
    }
}

private fun buildAvatarUrl(path: String?): String? {
    val safePath = path?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (safePath.startsWith("http")) return safePath

    val baseHost = BuildConfig.BASE_URL
        .trimEnd('/')
        .removeSuffix("/api")
    return "${baseHost}/${safePath.trimStart('/')}"
}
