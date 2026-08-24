package com.yassmine.projetpfe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.UserAvatar
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.components.showSuccess
import com.yassmine.projetpfe.ui.navigation.Screen
import com.yassmine.projetpfe.ui.theme.BackgroundLight
import com.yassmine.projetpfe.ui.theme.CardBackground
import com.yassmine.projetpfe.ui.theme.ConvenePrimary
import com.yassmine.projetpfe.ui.theme.ConvenePrimaryContainer
import com.yassmine.projetpfe.ui.theme.ErrorRed
import com.yassmine.projetpfe.ui.theme.OnlineGreen
import com.yassmine.projetpfe.ui.theme.SurfaceContainer
import com.yassmine.projetpfe.ui.theme.SurfaceContainerLow
import com.yassmine.projetpfe.ui.theme.TextDark
import com.yassmine.projetpfe.ui.theme.TextGray
import com.yassmine.projetpfe.ui.theme.TextLight
import com.yassmine.projetpfe.ui.theme.WarmNeutral
import com.yassmine.projetpfe.viewmodel.FriendInvitation
import com.yassmine.projetpfe.viewmodel.FriendsViewModel
import com.yassmine.projetpfe.viewmodel.UserSummary
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    navController: NavHostController,
    viewModel: FriendsViewModel = hiltViewModel()
) {
    val friends by viewModel.friends.collectAsState()
    val receivedInvitations by viewModel.receivedInvitations.collectAsState()
    val sentInvitations by viewModel.sentInvitations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val error by viewModel.error.collectAsState()
    val success by viewModel.success.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry) {
        if (navBackStackEntry?.destination?.route == Screen.Friends.route) {
            viewModel.refreshAll()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showError(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(success) {
        success?.let {
            snackbarHostState.showSuccess(it)
            viewModel.clearSuccess()
        }
    }

    val normalizedQuery = searchQuery.trim()
    val friendMatches: (UserSummary) -> Boolean = { user ->
        normalizedQuery.isBlank() ||
            user.name.contains(normalizedQuery, ignoreCase = true) ||
            user.email.contains(normalizedQuery, ignoreCase = true) ||
            (user.jobTitle?.contains(normalizedQuery, ignoreCase = true) == true) ||
            (user.company?.contains(normalizedQuery, ignoreCase = true) == true)
    }

    val filteredFriends = friends.filter(friendMatches)
    val filteredReceived = receivedInvitations.filter { invitation -> friendMatches(invitation.user) }
    val filteredSent = sentInvitations.filter { invitation -> friendMatches(invitation.user) }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BackgroundLight)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            FriendsTopBar(
                onBack = onBack,
                onSearchUsers = { navController.navigate(Screen.SearchUsers.route) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            FriendsPillTabs(
                selectedTab = selectedTab,
                friendsCount = friends.size,
                receivedCount = receivedInvitations.size,
                onSelectTab = { selectedTab = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            FriendsSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> FriendsListTab(
                        friends = filteredFriends,
                        onRemoveFriend = { user -> viewModel.removeFriend(user.id) },
                        onCardClick = { userId -> navController.navigate("public_profile/$userId") }
                    )

                    1 -> ReceivedInvitationsTab(
                        invitations = filteredReceived,
                        onAccept = { id -> viewModel.acceptInvitation(id) },
                        onDecline = { id -> viewModel.declineInvitation(id) },
                        onCardClick = { userId -> navController.navigate("public_profile/$userId") }
                    )

                    else -> SentInvitationsTab(
                        invitations = filteredSent,
                        onCancel = { id -> viewModel.cancelInvitation(id) },
                        onCardClick = { userId -> navController.navigate("public_profile/$userId") }
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendsTopBar(
    onBack: () -> Unit,
    onSearchUsers: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.friends_back_cd),
                    tint = TextDark
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = stringResource(id = R.string.friends_title),
                    color = TextDark,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(id = R.string.friends_subtitle),
                    color = TextGray,
                    fontSize = 12.sp
                )
            }
        }

        FloatingActionButton(
            onClick = onSearchUsers,
            shape = CircleShape,
            containerColor = ConvenePrimary,
            contentColor = CardBackground,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(id = R.string.friends_add_cd)
            )
        }
    }
}

@Composable
private fun FriendsPillTabs(
    selectedTab: Int,
    friendsCount: Int,
    receivedCount: Int,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        stringResource(id = R.string.friends_tab_friends, friendsCount),
        stringResource(id = R.string.friends_tab_received),
        stringResource(id = R.string.friends_tab_sent)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainer, RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) {
                            Modifier
                                .shadow(6.dp, RoundedCornerShape(12.dp), clip = false)
                                .background(CardBackground)
                        } else {
                            Modifier.background(SurfaceContainer)
                        }
                    )
                    .padding(vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) CardBackground else SurfaceContainer)
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) WarmNeutral.copy(alpha = 0.25f) else SurfaceContainer,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelectTab(index) }
                    .padding(horizontal = 10.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) CardBackground else SurfaceContainer)
                        .padding(vertical = 2.dp)
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) TextDark else TextGray,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (index == 1 && receivedCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ErrorRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = receivedCount.toString(),
                                color = CardBackground,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, WarmNeutral, RoundedCornerShape(14.dp)),
        placeholder = {
            Text(
                text = stringResource(id = R.string.friends_search_placeholder),
                color = TextLight,
                fontSize = 13.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextGray
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CardBackground,
            unfocusedContainerColor = CardBackground,
            disabledContainerColor = CardBackground,
            focusedIndicatorColor = CardBackground,
            unfocusedIndicatorColor = CardBackground,
            disabledIndicatorColor = CardBackground,
            focusedTextColor = TextDark,
            unfocusedTextColor = TextDark,
            cursorColor = ConvenePrimary
        )
    )
}

@Composable
private fun FriendsListTab(
    friends: List<UserSummary>,
    onRemoveFriend: (UserSummary) -> Unit,
    onCardClick: (String) -> Unit
) {
    if (friends.isEmpty()) {
        EmptyFriendsState(
            icon = Icons.Default.PersonSearch,
            message = stringResource(id = R.string.friends_empty_no_friends)
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(id = R.string.friends_count, friends.size).uppercase(Locale.getDefault()),
            color = TextLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(friends, key = { it.id }) { friend ->
                FriendCard(
                    friend = friend,
                    onRemove = { onRemoveFriend(friend) },
                    onCardClick = onCardClick
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun FriendCard(
    friend: UserSummary,
    onRemove: () -> Unit,
    onCardClick: (String) -> Unit
) {
    var showRemoveDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCardClick(friend.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, WarmNeutral.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UserAvatar(
                profilePicture = friend.profilePicture,
                name = friend.name,
                size = 42.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = friend.name,
                    color = TextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = friend.jobTitle ?: friend.company ?: friend.email,
                    color = TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = { showRemoveDialog = true },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WarmNeutral),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfaceContainerLow,
                    contentColor = TextGray
                )
            ) {
                Text(
                    text = stringResource(id = R.string.friends_remove_button),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (showRemoveDialog) {
            AlertDialog(
                onDismissRequest = { showRemoveDialog = false },
                title = { Text(stringResource(id = R.string.friends_remove_dialog_title, friend.name)) },
                text = { Text(stringResource(id = R.string.friends_remove_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onRemove()
                            showRemoveDialog = false
                        }
                    ) {
                        Text(text = stringResource(id = R.string.friends_remove_confirm), color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveDialog = false }) {
                        Text(text = stringResource(id = R.string.common_cancel))
                    }
                },
                containerColor = CardBackground,
                titleContentColor = TextDark,
                textContentColor = TextGray
            )
        }
    }
}

@Composable
private fun ReceivedInvitationsTab(
    invitations: List<FriendInvitation>,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onCardClick: (String) -> Unit
) {
    if (invitations.isEmpty()) {
        EmptyFriendsState(
            icon = Icons.Default.MailOutline,
            message = stringResource(id = R.string.friends_empty_received)
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(id = R.string.friends_received_count, invitations.size),
            color = TextLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(invitations, key = { it.invitationId }) { invitation ->
                InvitationCard(
                    invitation = invitation,
                    onAccept = { onAccept(invitation.invitationId) },
                    onDecline = { onDecline(invitation.invitationId) },
                    onCardClick = onCardClick
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun InvitationCard(
    invitation: FriendInvitation,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCardClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCardClick(invitation.user.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, WarmNeutral.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UserAvatar(
                    profilePicture = invitation.user.profilePicture,
                    name = invitation.user.name,
                    size = 42.dp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = invitation.user.name,
                        color = TextDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = invitation.user.jobTitle ?: invitation.user.company ?: invitation.user.email,
                        color = TextGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formatRelativeTime(invitation.createdAt),
                    color = TextLight,
                    fontSize = 11.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConvenePrimary,
                        contentColor = CardBackground
                    )
                ) {
                    Text(stringResource(id = R.string.friends_accept), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, WarmNeutral),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = SurfaceContainerLow,
                        contentColor = TextGray
                    )
                ) {
                    Text(stringResource(id = R.string.friends_decline), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SentInvitationsTab(
    invitations: List<FriendInvitation>,
    onCancel: (String) -> Unit,
    onCardClick: (String) -> Unit
) {
    if (invitations.isEmpty()) {
        EmptyFriendsState(
            icon = Icons.Default.HourglassTop,
            message = stringResource(id = R.string.friends_empty_sent)
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(id = R.string.friends_sent_count, invitations.size),
            color = TextLight,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(invitations, key = { it.invitationId }) { invitation ->
                SentInvitationCard(
                    invitation = invitation,
                    onCancel = { onCancel(invitation.invitationId) },
                    onCardClick = onCardClick
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SentInvitationCard(
    invitation: FriendInvitation,
    onCancel: () -> Unit,
    onCardClick: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCardClick(invitation.user.id) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, WarmNeutral.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            UserAvatar(
                profilePicture = invitation.user.profilePicture,
                name = invitation.user.name,
                size = 42.dp
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = invitation.user.name,
                    color = TextDark,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = invitation.user.jobTitle ?: invitation.user.company ?: invitation.user.email,
                    color = TextGray,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(ConvenePrimaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.friends_pending),
                        color = ConvenePrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.25f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardBackground,
                        contentColor = ErrorRed
                    )
                ) {
                    Text(stringResource(id = R.string.common_cancel), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EmptyFriendsState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(SurfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextGray,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            color = TextGray,
            fontSize = 14.sp,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun formatRelativeTime(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return ""

    val now = Instant.now()
    val createdAt = runCatching { Instant.parse(isoDate) }.getOrNull() ?: return ""
    val minutes = ChronoUnit.MINUTES.between(createdAt, now)

    return when {
        minutes < 1 -> stringResource(id = R.string.friends_just_now)
        minutes < 60 -> stringResource(id = R.string.friends_minutes_ago, minutes)
        minutes < 1440 -> stringResource(id = R.string.friends_hours_ago, minutes / 60)
        else -> {
            val localDate = createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
            stringResource(id = R.string.friends_date_format, localDate.dayOfMonth, localDate.monthValue)
        }
    }
}
