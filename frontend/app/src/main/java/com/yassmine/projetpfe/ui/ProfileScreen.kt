package com.yassmine.projetpfe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yassmine.projetpfe.BuildConfig
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.Achievement
import com.yassmine.projetpfe.data.api.AchievementMeta
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.LanguageToggle
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.components.showSuccess
import com.yassmine.projetpfe.ui.theme.BackgroundLight
import com.yassmine.projetpfe.ui.theme.CardBackground
import com.yassmine.projetpfe.ui.theme.TextDark
import com.yassmine.projetpfe.ui.theme.TextGray
import com.yassmine.projetpfe.ui.theme.White
import com.yassmine.projetpfe.ui.theme.WarmNeutral
import com.yassmine.projetpfe.utils.BadgeLocalization
import com.yassmine.projetpfe.viewmodel.AppPreferencesViewModel
import com.yassmine.projetpfe.viewmodel.ProfileViewModel
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ProfileScreen(
    onEditProfile: () -> Unit = {},
    onHomeClick: () -> Unit,
    onCreateClick: () -> Unit,
    onTasksClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onFriendsClick: () -> Unit = {},
    onLogout: () -> Unit,
    currentLanguage: String = "fr",
    onLanguageChange: (String) -> Unit = {},
    profileViewModel: ProfileViewModel = hiltViewModel(),
    appPreferencesViewModel: AppPreferencesViewModel = hiltViewModel()
) {
    val localAppLanguage by appPreferencesViewModel.appLanguage.collectAsState()

    val userName by profileViewModel.userName.collectAsState()
    val userEmail by profileViewModel.userEmail.collectAsState()
    val myProfile by profileViewModel.myProfile.collectAsState()
    val myStats by profileViewModel.myStats.collectAsState()
    val isLoading by profileViewModel.isLoading.collectAsState()
    val error by profileViewModel.error.collectAsState()
    val updateSuccess by profileViewModel.updateSuccess.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAvatarDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showError(it); profileViewModel.clearError() }
    }
    LaunchedEffect(updateSuccess) {
        updateSuccess?.let { snackbarHostState.showSuccess(it); profileViewModel.clearUpdateSuccess() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                profileViewModel.loadMyProfile()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val defaultUser = stringResource(id = R.string.profile_default_user)
    val displayName = myProfile?.name ?: userName ?: defaultUser
    val displayEmail = myProfile?.email ?: userEmail ?: ""
    val displayBio = myProfile?.bio
    val displayJobTitle = myProfile?.jobTitle
    val displayCompany = myProfile?.company
    val friendsCount = myProfile?.friendsCount ?: 0

    val initials = if (displayName.isNotBlank()) {
        displayName.split(" ")
            .mapNotNull { it.firstOrNull() }
            .take(2)
            .joinToString("")
            .uppercase()
    } else "?"

    val context = LocalContext.current
    val avatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            profileViewModel.uploadAvatar(context, uri)
        }
    }

    val profilePicture = myProfile?.profilePicture
    val avatarUrl = buildAvatarUrl(profilePicture)

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavBar(
                selectedIndex = 4,
                onIndexSelected = { index ->
                    when (index) {
                        0 -> onHomeClick()
                        1 -> onCreateClick()
                        2 -> onTasksClick()
                        3 -> onAlertsClick()
                        4 -> {}
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(color = BackgroundLight)
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LanguageToggle(
                            currentLanguage = currentLanguage,
                            onLanguageChange = onLanguageChange
                        )

                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = stringResource(id = R.string.profile_logout_title),
                                tint = White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(top = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { avatarLauncher.launch("image/*") },
                                    onLongClick = { showAvatarDialog = true }
                                ),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            if (!avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(avatarUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = stringResource(id = R.string.profile_avatar_title),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, White.copy(alpha = 0.5f), CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = White
                                    )
                                }
                            }

                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .align(Alignment.Center),
                                    color = White,
                                    strokeWidth = 3.dp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { avatarLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = stringResource(id = R.string.profile_change_photo),
                                    tint = White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Text(
                            text = displayName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = White
                        )

                        if (!displayJobTitle.isNullOrBlank()) {
                            Text(
                                text = displayJobTitle,
                                fontSize = 13.sp,
                                color = White.copy(alpha = 0.85f)
                            )
                        }

                        if (!displayCompany.isNullOrBlank()) {
                            Text(
                                text = displayCompany,
                                fontSize = 12.sp,
                                color = White.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                onEditProfile()
                                showEditDialog = true
                            },
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = White.copy(alpha = 0.08f),
                                contentColor = White
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                White.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            key(localAppLanguage) {
                                Text(
                                    text = stringResource(id = R.string.profile_edit),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = White
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!displayBio.isNullOrBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = White),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(id = R.string.profile_about),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextGray
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(displayBio, fontSize = 14.sp, color = TextDark)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(text = displayEmail, fontSize = 13.sp, color = TextGray)
                }

                Spacer(Modifier.height(16.dp))

                val stats = myStats
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            icon = Icons.Default.CalendarMonth,
                            label = stringResource(id = R.string.profile_stat_organized),
                            value = stats?.meetingsOrganized?.toString() ?: "-",
                            iconColor = MaterialTheme.colorScheme.primary,
                            iconBgColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.People,
                            label = stringResource(id = R.string.profile_stat_attended),
                            value = stats?.meetingsAttended?.toString() ?: "-",
                            iconColor = MaterialTheme.colorScheme.secondary,
                            iconBgColor = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            icon = Icons.Default.Notes,
                            label = stringResource(id = R.string.profile_stat_notes),
                            value = stats?.notesAdded?.toString() ?: "-",
                            iconColor = MaterialTheme.colorScheme.tertiary,
                            iconBgColor = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = Icons.Default.CheckCircle,
                            label = stringResource(id = R.string.profile_stat_tasks),
                            value = stats?.tasksCompleted?.toString() ?: "-",
                            iconColor = MaterialTheme.colorScheme.error,
                            iconBgColor = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onFriendsClick() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        WarmNeutral.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = stringResource(id = R.string.profile_friends_title),
                                    color = TextDark,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(id = R.string.profile_friends_count, friendsCount),
                                    color = TextGray,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextGray
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(id = R.string.profile_badges_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    val achievements = stats?.achievements ?: emptyList()

                    when {
                        achievements.isEmpty() && isLoading -> {
                            repeat(2) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(88.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    elevation = CardDefaults.cardElevation(0.dp)
                                ) {}
                                Spacer(Modifier.height(10.dp))
                            }
                        }

                        achievements.isEmpty() -> {
                            AchievementEmptyState()
                        }

                        else -> {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(items = achievements, key = { it.id }) { achievement ->
                                    AchievementListCard(
                                        achievement = achievement,
                                        currentLang = localAppLanguage
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = {
                        Text(
                            text = stringResource(id = R.string.profile_logout_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = { Text(stringResource(id = R.string.profile_logout_message)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showLogoutDialog = false
                                profileViewModel.logout()
                                onLogout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(id = R.string.profile_logout_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text(stringResource(id = R.string.common_cancel))
                        }
                    }
                )
            }

            if (showAvatarDialog) {
                Dialog(
                    onDismissRequest = { showAvatarDialog = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(id = R.string.profile_avatar_title),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                if (!avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(avatarUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = stringResource(id = R.string.profile_avatar_title),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(200.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(200.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.tertiary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            fontSize = 64.sp,
                                            color = White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        showAvatarDialog = false
                                        avatarLauncher.launch("image/*")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(id = R.string.profile_change_photo))
                                }

                                Spacer(Modifier.height(8.dp))

                                if (!profilePicture.isNullOrBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            showAvatarDialog = false
                                            profileViewModel.deleteAvatar()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text(stringResource(id = R.string.profile_remove_photo))
                                    }

                                    Spacer(Modifier.height(8.dp))
                                }

                                TextButton(
                                    onClick = { showAvatarDialog = false },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                ) {
                                    Text(stringResource(id = R.string.common_close))
                                }
                            }
                        }
                    }
                }
            }

            if (showEditDialog) {
                EditProfileDialog(
                    initialName = myProfile?.name ?: userName ?: "",
                    initialBio = myProfile?.bio ?: "",
                    initialJobTitle = myProfile?.jobTitle ?: "",
                    initialCompany = myProfile?.company ?: "",
                    isSaving = isLoading,
                    onDismiss = { showEditDialog = false },
                    onSave = { name, bio, jobTitle, company ->
                        profileViewModel.updateProfile(
                            name = name.ifBlank { null },
                            bio = bio,
                            jobTitle = jobTitle,
                            company = company
                        )
                        showEditDialog = false
                    }
                )
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    initialName: String,
    initialBio: String,
    initialJobTitle: String,
    initialCompany: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, bio: String, jobTitle: String, company: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var bio by remember { mutableStateOf(initialBio) }
    var jobTitle by remember { mutableStateOf(initialJobTitle) }
    var company by remember { mutableStateOf(initialCompany) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(id = R.string.profile_edit_dialog_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(id = R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text(stringResource(id = R.string.profile_bio_label)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = jobTitle,
                    onValueChange = { jobTitle = it },
                    label = { Text(stringResource(id = R.string.profile_job_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text(stringResource(id = R.string.profile_company_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(id = R.string.profile_avatar_hint),
                    fontSize = 12.sp,
                    color = TextGray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, bio, jobTitle, company) },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(id = R.string.common_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun AchievementListCard(
    achievement: Achievement,
    currentLang: String,
) {
    val context = LocalContext.current
    val badgeKey = achievement.id.ifBlank { achievement.name }
    val localizedBadge = remember(badgeKey, currentLang) {
        BadgeLocalization.getLocalizedBadge(context, badgeKey)
    }

    val meta = AchievementMeta.map[achievement.id]
    val isUnlocked = achievement.unlocked || achievement.current >= achievement.target
    val emoji = if (isUnlocked) (meta?.emoji ?: "🏅") else "🔒"
    val title = localizedBadge.first
    val description = localizedBadge.second

    val statusText = if (isUnlocked) {
        stringResource(id = R.string.badge_unlocked)
    } else {
        stringResource(id = R.string.badge_locked)
    }

    Card(
        modifier = Modifier
            .width(286.dp)
            .height(92.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isUnlocked) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextDark,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = TextGray,
                    maxLines = 2
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isUnlocked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        TextGray
                    }
                )
            }
        }
    }
}

@Composable
private fun AchievementEmptyState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.profile_badges_empty_title),
                fontWeight = FontWeight.SemiBold,
                color = TextDark
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(id = R.string.profile_badges_empty_subtitle),
                fontSize = 12.sp,
                color = TextGray
            )
        }
    }
}

@Composable
fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    iconColor: androidx.compose.ui.graphics.Color,
    iconBgColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color = iconBgColor, shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = label,
                fontSize = 11.sp,
                color = TextGray,
                lineHeight = 15.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
        }
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
