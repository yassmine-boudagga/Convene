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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.model.*
import com.yassmine.projetpfe.ui.theme.*
import com.yassmine.projetpfe.viewmodel.MeetingViewModel
import com.yassmine.projetpfe.viewmodel.AppPreferencesViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.yassmine.projetpfe.ui.components.VisibleLazyColumnScrollbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//   Helpers timezone                   

private fun parseLocalDate(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zdt = instant.atZone(ZoneId.systemDefault())
        zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    } catch (_: Exception) { isoString.take(10) }
}

private fun parseLocalTime(isoString: String): String {
    return try {
        val instant = Instant.parse(isoString)
        val zdt = instant.atZone(ZoneId.systemDefault())
        zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) { isoString.drop(11).take(5) }
}

private fun getTodayLabel(prefix: String): String {
    val today     = LocalDate.now()
    val locale = Locale.getDefault()
    val dayOfWeek = today.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    val month     = today.month.getDisplayName(TextStyle.SHORT, locale)
    val day       = today.dayOfMonth
    return "$prefix, $dayOfWeek, $month $day"
}

//  écran principal 

@Composable
@OptIn(ExperimentalMaterialApi::class)
fun HomeScreen(
    onMeetingClick: (Meeting) -> Unit,
    onCreateClick: () -> Unit,
    onTasksClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit = {},
    onArchivedMeetingsClick: () -> Unit = {},
    viewModel: MeetingViewModel = hiltViewModel()
) {
    var selectedTab    by remember { mutableIntStateOf(0) }
    var searchText     by remember { mutableStateOf("") }
    var bottomNavIndex by remember { mutableIntStateOf(0) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    val meetings  by viewModel.meetings.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error     by viewModel.error.collectAsState()
    val hasMorePast by viewModel.hasMorePast.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()

    val activeTab = when (selectedTab) {
        0 -> "scheduled"
        1 -> "ongoing"
        2 -> "past"
        else -> "scheduled"
    }

    val apiStatus = when (activeTab) {
        "scheduled" -> "scheduled"
        "ongoing" -> "ongoing"
        "past" -> "finished"
        else -> null
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    val reachedBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 3
        }
    }
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isPullRefreshing,
        onRefresh = {
            isPullRefreshing = true
            viewModel.loadMeetings(apiStatus)
        }
    )

    LaunchedEffect(selectedTab) { viewModel.loadMeetings(apiStatus) }

    LaunchedEffect(apiStatus) {
        while (true) {
            delay(300_000L)
            viewModel.loadMeetings(apiStatus)
        }
    }

    DisposableEffect(lifecycleOwner, apiStatus) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadMeetings(apiStatus)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) isPullRefreshing = false
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom && activeTab == "past" && hasMorePast && !isLoadingMore) {
            viewModel.loadMorePastMeetings()
        }
    }

    val uiMeetings = meetings.map { dto ->
        val uiType = when (dto.meetingType.lowercase()) {
            "physical" -> MeetingType.PHYSICAL
            else -> MeetingType.ONLINE
        }

        Meeting(
            id           = dto.realId,
            title        = dto.title,
            date         = parseLocalDate(dto.startTime),
            time         = parseLocalTime(dto.startTime),
            participants = dto.joinedParticipants.size.takeIf { it > 0 } ?: dto.participants.size,
            duration     = "${dto.duration} mins",
            type         = uiType,
            status       = when (dto.status) {
                "ongoing"  -> MeetingStatus.ONGOING
                "finished" -> MeetingStatus.FINISHED
                else       -> MeetingStatus.UPCOMING
            },
            location = dto.location
        )
    }.filter { it.title.contains(searchText, ignoreCase = true) }

    Scaffold(
        bottomBar = {
            // onCreateClick est passé ici : le FAB central appelle onCreateClick()
            // Dans les autres screens (CreateMeeting, Tasks...) ce paramètre est optionnel = {}
            BottomNavBar(
                selectedIndex   = bottomNavIndex,
                onIndexSelected = { index ->
                    bottomNavIndex = index
                    when (index) {
                        2 -> onTasksClick()
                        3 -> onAlertsClick()
                        4 -> onProfileClick()
                    }
                },
                onCreateClick = onCreateClick
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(color = BackgroundLight)
        ) {
            HeaderSection(
                onCreateClick = onCreateClick,
                onSearchClick = onSearchClick
            )
            Spacer(modifier = Modifier.height(16.dp))
            SearchBar(
                value         = searchText,
                onValueChange = { searchText = it },
                modifier      = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            TabSection(
                selectedTab   = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier      = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (activeTab == "past") {
                TextButton(
                    onClick = onArchivedMeetingsClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Voir les archives",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
            ) {
                when {
                isLoading && uiMeetings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
                error != null -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: stringResource(id = R.string.common_error), color = ErrorRed, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadMeetings(apiStatus) }) { Text(stringResource(id = R.string.common_retry)) }
                    }
                }
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (uiMeetings.isEmpty()) item { EmptyState(tab = selectedTab) }
                        else items(uiMeetings) { meeting ->
                            MeetingCard(meeting = meeting, onClick = { onMeetingClick(meeting) })
                        }
                        if (activeTab == "past" && isLoadingMore) {
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

                    VisibleLazyColumnScrollbar(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(vertical = 8.dp)
                    )
                }
                }

                PullRefreshIndicator(
                    refreshing = isPullRefreshing,
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter),
                    contentColor = PrimaryBlue
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
//   Header                      
@Composable
fun HeaderSection(
    onCreateClick: () -> Unit,
    onSearchClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text(stringResource(id = R.string.home_meetings_title), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark)
            Text(getTodayLabel(prefix = stringResource(id = R.string.home_today_prefix)), fontSize = 13.sp, color = TextGray)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(color = Color.White, shape = CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSearchClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = stringResource(id = R.string.home_search_users), tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color = PrimaryBlue, shape = CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCreateClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

//   Search Bar                     
@Composable
fun SearchBar(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder  = { Text(stringResource(id = R.string.home_search_meetings_placeholder), color = TextLight) },
        leadingIcon  = { Icon(Icons.Default.Search, null, tint = TextLight, modifier = Modifier.size(20.dp)) },
        trailingIcon = { Icon(Icons.Default.FilterAlt, null, tint = TextGray, modifier = Modifier.size(20.dp)) },
        modifier  = modifier.fillMaxWidth().height(52.dp),
        shape     = RoundedCornerShape(14.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = PrimaryBlue.copy(alpha = 0.3f),
            unfocusedBorderColor    = TextLight.copy(alpha = 0.3f),
            focusedContainerColor   = Color.White,
            unfocusedContainerColor = Color.White
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = TextDark)
    )
}

//   Tabs                       

@Composable
fun TabSection(selectedTab: Int, onTabSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf(
        stringResource(id = R.string.home_tab_upcoming),
        stringResource(id = R.string.home_tab_ongoing),
        stringResource(id = R.string.home_tab_past)
    )
    Row(
        modifier = modifier.fillMaxWidth()
            .background(color = PastGrayBg, shape = RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tabs.forEachIndexed { index, label ->
            val isSelected = index == selectedTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onTabSelected(index) }
                    .background(if (isSelected) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label, fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) PrimaryBlue else TextGray
                )
            }
        }
    }
}

//   Meeting Card                    

@Composable
fun MeetingCard(meeting: Meeting, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { onClick() },
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            MeetingTypeIcon(type = meeting.type, status = meeting.status)
            Column(modifier = Modifier.weight(1f)) {
                Text(meeting.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    InfoItem(icon = Icons.Default.CalendarMonth, text = meeting.date)
                    InfoItem(icon = Icons.Default.AccessTime, text = meeting.time)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    InfoItem(
                        icon = Icons.Default.People,
                        text = stringResource(id = R.string.home_participants_count, meeting.participants)
                    )
                    Text("•", color = TextGray, fontSize = 12.sp)
                    Text(meeting.duration, fontSize = 12.sp, color = TextGray)
                }
                meeting.location?.let {
                    Spacer(Modifier.height(4.dp))
                    InfoItem(icon = Icons.Default.LocationOn, text = it, iconColor = Color(0xFFE11D48))
                }
            }
        }
    }
}

@Composable
fun MeetingTypeIcon(type: MeetingType, status: MeetingStatus) {
    val bgColor   = when (status) { MeetingStatus.ONGOING -> OnlineGreenBg; MeetingStatus.UPCOMING -> UpcomingBlueBg; else -> PastGrayBg }
    val iconColor = when (status) { MeetingStatus.ONGOING -> OnlineGreen;   MeetingStatus.UPCOMING -> PrimaryBlue;   else -> TextGray }
    val icon      = when (type)   { MeetingType.ONLINE -> Icons.Default.VideoCall; MeetingType.PHYSICAL -> Icons.Default.LocationOn }
    Box(modifier = Modifier.size(44.dp).background(bgColor, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun InfoItem(icon: ImageVector, text: String, iconColor: Color = TextGray) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, color = TextGray)
    }
}

@Composable
fun EmptyState(tab: Int) {
    val message = when (tab) {
        0 -> stringResource(id = R.string.home_empty_upcoming)
        1 -> stringResource(id = R.string.home_empty_ongoing)
        2 -> stringResource(id = R.string.home_empty_past)
        else -> ""
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.CalendarMonth, null, tint = TextLight, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, color = TextGray, fontSize = 14.sp)
    }
}

//
// EXPLICATION du paramètre onCreateClick:
// BottomNavBar est utilisé dans TOUS les screens (Home, Tasks, Profile, etc.)
// Seul HomeScreen a besoin que le FAB bleu central navigue vers CreateMeeting.
// Les autres screens n'ont pas de CreateClick â†’ onCreateClick = {} par défaut.
//
// Exemple :
//   HomeScreen  â†’ BottomNavBar(onCreateClick = { navController.navigate("create") })   FAB actif
//   TasksScreen â†’ BottomNavBar(...)   â† pas de onCreateClick, {} par défaut = FAB ne fait rien de spécial

private data class NavItem(val label: String, val iconSelected: ImageVector, val iconDefault: ImageVector)

@Composable
fun BottomNavBar(
    selectedIndex:   Int,
    onIndexSelected: (Int) -> Unit,
    onCreateClick:   () -> Unit = {},   // â† optionnel : seul HomeScreen le passe
    appPreferencesViewModel: AppPreferencesViewModel = hiltViewModel()
) {
    val currentLang by appPreferencesViewModel.appLanguage.collectAsState()

    key(currentLang) {
        val navItems = listOf(
            NavItem(stringResource(id = R.string.nav_home), Icons.Filled.Home, Icons.Outlined.Home),
            NavItem(stringResource(id = R.string.nav_tasks), Icons.Filled.CheckBox, Icons.Outlined.CheckBox),
            NavItem(stringResource(id = R.string.nav_alerts), Icons.Filled.Notifications, Icons.Outlined.Notifications),
            NavItem(stringResource(id = R.string.nav_profile), Icons.Filled.Person, Icons.Outlined.Person)
        )
        val logicalIndex = listOf(0, 2, 3, 4)

        //  FIX FAB COUPé :
        // Stratégie : la navbar a une hauteur fixe de 64dp (barre visible)
        // + navigationBarsPadding() en bas pour la barre système.
        // Le FAB dépasse de 28dp au-dessus â†’ on ajoute un paddingTop de 28dp à  la Surface
        // pour que le Scaffold reserve cet espace et ne le rogne pas.
        val fabOverhang = 28.dp

        Surface(
            modifier        = Modifier.fillMaxWidth(),
            color           = Color.White,
            shadowElevation = 0.dp,
            tonalElevation  = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()          // padding barre système Android (gestes/boutons)
            ) {
                // Espace réservé pour le FAB qui déborde au-dessus
                Spacer(modifier = Modifier.height(fabOverhang))

                // Séparateur fin
                HorizontalDivider(color = Color(0xFFEDF1F7), thickness = 1.dp)

                // Barre de navigation : hauteur 64dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    //   4 boutons autour du FAB central  
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navItems.take(2).forEachIndexed { i, item ->
                            NavItemButton(
                                item       = item,
                                isSelected = selectedIndex == logicalIndex[i],
                                onClick    = { onIndexSelected(logicalIndex[i]) },
                                modifier   = Modifier.weight(1f)
                            )
                        }
                        // Espace central pour le FAB
                        Spacer(modifier = Modifier.width(68.dp))
                        navItems.takeLast(2).forEachIndexed { i, item ->
                            NavItemButton(
                                item       = item,
                                isSelected = selectedIndex == logicalIndex[2 + i],
                                onClick    = { onIndexSelected(logicalIndex[2 + i]) },
                                modifier   = Modifier.weight(1f)
                            )
                        }
                    }

                    //   FAB Create  centré, dépasse au-dessus  
                    // offset(y = -fabOverhang) = remonte de 28dp au-dessus de la barre
                    // L'espace est réservé par le Spacer(fabOverhang) dans la Column parente
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .align(Alignment.TopCenter)
                            .offset(y = -fabOverhang)
                            .shadow(
                                elevation    = 8.dp,
                                shape        = CircleShape,
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                            .background(
                                brush = Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                ),
                                shape = CircleShape
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onIndexSelected(1)
                                onCreateClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.home_create_meeting), tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItemButton(item: NavItem, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else Color.Transparent
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = if (isSelected) item.iconSelected else item.iconDefault,
                contentDescription = item.label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier           = Modifier.size(22.dp)
            )
        }
        Text(
            text       = item.label,
            fontSize   = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    ConveneTheme {
        HomeScreen(
            onMeetingClick = {}, onCreateClick  = {},
            onTasksClick   = {}, onAlertsClick  = {}, onProfileClick = {},
            onArchivedMeetingsClick = {}
        )
    }
}
