package com.yassmine.projetpfe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import com.yassmine.projetpfe.data.api.UserSearchResult
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.VisibleLazyColumnScrollbar
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.components.showSuccess
import com.yassmine.projetpfe.ui.theme.*
import com.yassmine.projetpfe.viewmodel.SocialViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchUsersScreen(
    onBack: () -> Unit,
    onUserClick: (String) -> Unit,
    viewModel: SocialViewModel = hiltViewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()

    val query by viewModel.searchQuery.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

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
                        stringResource(id = R.string.search_users_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.search_users_back),
                            tint = TextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(BackgroundLight)
        ) {
            //  Search Bar 
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                placeholder = {
                    Text(stringResource(id = R.string.search_users_placeholder), color = TextLight)
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue.copy(alpha = 0.5f),
                    unfocusedBorderColor = TextLight.copy(alpha = 0.3f),
                    focusedContainerColor = White,
                    unfocusedContainerColor = White
                )
            )

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.search_users_loading),
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            //  Results List 
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (query.isBlank() && searchResults.isNotEmpty() && !isLoading) {
                        item {
                            Text(
                                text = stringResource(id = R.string.search_users_suggested),
                                color = TextGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    if (searchResults.isEmpty() && query.isNotBlank() && !isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(id = R.string.search_users_no_results, query),
                                    color = TextGray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    items(searchResults, key = { it.id }) { user ->
                        UserSearchItem(
                            user = user,
                            onUserClick = { onUserClick(user.id) },
                            onActionClick = { handleFriendAction(user, viewModel) },
                            onRejectClick = { handleFriendRejectAction(user, viewModel) }
                        )
                    }
                }

                VisibleLazyColumnScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun UserSearchItem(
    user: UserSearchResult,
    onUserClick: () -> Unit,
    onActionClick: () -> Unit,
    onRejectClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUserClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar (initials)
            UserAvatar(name = user.name, profilePicture = user.profilePicture, size = 44)

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextDark)
                Text(user.email, fontSize = 12.sp, color = TextGray)
                if (!user.jobTitle.isNullOrBlank()) {
                    Text(user.jobTitle, fontSize = 12.sp, color = PrimaryBlue)
                }
            }

            // Action button
            FriendActionButton(
                friendStatus = user.friendStatus,
                onClick = onActionClick,
                onRejectClick = onRejectClick
            )
        }
    }
}

@Composable
internal fun UserAvatar(name: String, size: Int) {
    UserAvatar(name = name, profilePicture = null, size = size)
}

@Composable
internal fun UserAvatar(name: String, profilePicture: String?, size: Int) {
    val initials = name.split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
    val avatarUrl = buildAvatarUrl(path = profilePicture)

    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(id = R.string.search_users_profile_photo_desc),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                color = PrimaryBlue,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 3).sp
            )
        }
    }
}

@Composable
internal fun FriendActionButton(
    friendStatus: String,
    onClick: () -> Unit,
    onRejectClick: (() -> Unit)? = null
) {
    when (friendStatus) {
        "friends" -> OutlinedButton(
            onClick = {},
            enabled = false,
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text(stringResource(id = R.string.search_users_friends), fontSize = 12.sp) }

        "pending_sent" -> OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706)),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text(stringResource(id = R.string.search_users_cancel_request), fontSize = 12.sp) }

        "pending_received" -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(id = R.string.search_users_accept),
                    fontSize = 12.sp,
                    color = White
                )
            }

            OutlinedButton(
                onClick = { onRejectClick?.invoke() },
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    stringResource(id = R.string.search_users_reject),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        else -> Button(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                stringResource(id = R.string.search_users_add),
                fontSize = 12.sp,
                color = White
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

private fun handleFriendAction(user: UserSearchResult, viewModel: SocialViewModel) {
    when (user.friendStatus) {
        "none" -> viewModel.sendFriendRequest(user.id)
        "pending_sent" -> viewModel.cancelFriendRequest(user.id)
        "pending_received" -> viewModel.acceptRequest(user.id)
        else -> { /* friends — no action */ }
    }
}

private fun handleFriendRejectAction(user: UserSearchResult, viewModel: SocialViewModel) {
    if (user.friendStatus == "pending_received") {
        viewModel.rejectRequest(user.id)
    }
}
