package com.yassmine.projetpfe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.TaskResponse
import com.yassmine.projetpfe.ui.theme.BackgroundLight
import com.yassmine.projetpfe.ui.theme.ConvenePrimary
import com.yassmine.projetpfe.ui.theme.ErrorRed
import com.yassmine.projetpfe.ui.theme.ConveneTheme
import com.yassmine.projetpfe.ui.theme.OnlineGreen
import com.yassmine.projetpfe.ui.theme.PrimaryBlue
import com.yassmine.projetpfe.ui.theme.PurpleAccent
import com.yassmine.projetpfe.ui.theme.TextGray
import com.yassmine.projetpfe.ui.theme.White
import com.yassmine.projetpfe.ui.components.VisibleLazyColumnScrollbar
import com.yassmine.projetpfe.viewmodel.TasksFilter
import com.yassmine.projetpfe.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onBackClick: () -> Unit,
    onCreateTaskClick: () -> Unit,
    onTasksClick: () -> Unit,
    onHomeClick: () -> Unit,
    onCreateClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val viewModel: TaskViewModel = hiltViewModel()
    val myTodoTasks by viewModel.myTodoTasks.collectAsState()
    val myDoneTasks by viewModel.myCompletedTasks.collectAsState()
    val myArchivedTasks by viewModel.myArchivedTasks.collectAsState()
    val relatedTodoTasks by viewModel.relatedTodoTasks.collectAsState()
    val relatedDoneTasks by viewModel.relatedCompletedTasks.collectAsState()
    val relatedArchivedTasks by viewModel.relatedArchivedTasks.collectAsState()
    val todoCount by viewModel.todoCount.collectAsState()
    val meetings by viewModel.meetingsList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val isSwitchingFilter by viewModel.isSwitchingFilter.collectAsState()
    val error by viewModel.error.collectAsState()
    val showArchived by viewModel.showArchived.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val activeMeetingId by viewModel.activeMeetingId.collectAsState()
    val activeStatus by viewModel.activeStatus.collectAsState()
    val activeToDate by viewModel.activeToDate.collectAsState()

    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var archivedExpanded by rememberSaveable { mutableStateOf(true) }
    var taskToComplete by remember { mutableStateOf<TaskResponse?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }

    var filterMeetingId by remember { mutableStateOf<String?>(activeMeetingId) }
    var filterMeetingTitle by remember { mutableStateOf<String?>(null) }
    var filterStatus by remember { mutableStateOf<String?>(activeStatus) }
    var filterBeforeDate by remember { mutableStateOf<String?>(activeToDate) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    LaunchedEffect(listState, hasMore, isLoadingMore) {
        snapshotFlow { listState.layoutInfo }
            .collect { layoutInfo ->
                val totalItems = layoutInfo.totalItemsCount
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (totalItems > 0 && lastVisible >= totalItems - 3 && hasMore && !isLoadingMore) {
                    viewModel.loadMoreTasks()
                }
            }
    }
    val scope = rememberCoroutineScope()

    val activeFiltersCount = listOfNotNull(activeMeetingId, activeStatus, activeToDate).size
    val pendingCount = todoCount
    val overdueTasks = myTodoTasks.filter { task -> task.dueDate != null && isTaskOverdue(task.dueDate) }

    BackHandler(enabled = showFilterSheet) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    BackHandler(enabled = showCreateSheet) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    if (taskToComplete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { taskToComplete = null },
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(stringResource(id = R.string.tasks_confirm_complete_title)) },
            text = {
                Text(
                    stringResource(
                        id = R.string.tasks_confirm_complete_message,
                        taskToComplete?.title.orEmpty()
                    )
                )
            },
            confirmButton = {
                Button(onClick = {
                    val task = taskToComplete
                    if (task != null) {
                        viewModel.completeTask(task.taskId)
                    }
                    taskToComplete = null
                }) {
                    Text(stringResource(id = R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToComplete = null }) {
                    Text(stringResource(id = R.string.common_cancel))
                }
            }
        )
    }

    if (showFilterSheet) {
        FilterTasksSheet(
            meetings = meetings,
            filterMeetingId = filterMeetingId,
            filterMeetingTitle = filterMeetingTitle,
            filterStatus = filterStatus,
            filterBeforeDate = filterBeforeDate,
            onMeetingChange = { id, title ->
                filterMeetingId = id
                filterMeetingTitle = title
            },
            onStatusChange = { filterStatus = it },
            onBeforeDateChange = { filterBeforeDate = it },
            onReset = {
                filterMeetingId = null
                filterMeetingTitle = null
                filterStatus = null
                filterBeforeDate = null
                viewModel.resetFilters()
                showFilterSheet = false
            },
            onApply = {
                viewModel.applyFilters(filterMeetingId, filterStatus, filterBeforeDate)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (showCreateSheet) {
        CreateTaskSheet(
            meetings = meetings,
            onDismiss = { showCreateSheet = false },
            onCreate = { title, meetingId, priority, dueDate ->
                viewModel.createTask(
                    title = title,
                    meetingId = meetingId,
                    priority = priority,
                    dueDate = dueDate,
                    onResult = { success ->
                        if (success) {
                            showCreateSheet = false
                            scope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    }
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(id = R.string.tasks_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (pendingCount > 0) {
                            val pendingText = if (pendingCount == 1) {
                                stringResource(id = R.string.tasks_pending_one)
                            } else {
                                stringResource(id = R.string.tasks_pending_many, pendingCount)
                            }
                            Text(
                                text = pendingText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (activeFiltersCount > 0) {
                                Badge { Text(activeFiltersCount.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = {
                            filterMeetingId = activeMeetingId
                            filterMeetingTitle = meetings.firstOrNull { it.id == activeMeetingId }?.title
                            filterStatus = activeStatus
                            filterBeforeDate = activeToDate
                            showFilterSheet = true
                        }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = stringResource(id = R.string.tasks_filters)
                            )
                        }
                    }
                    IconButton(onClick = {
                        onCreateTaskClick()
                        showCreateSheet = true
                    }) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = stringResource(id = R.string.tasks_create),
                                tint = White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedIndex = 2,
                onIndexSelected = { index ->
                    when (index) {
                        0 -> onHomeClick()
                        1 -> onCreateClick()
                        2 -> onTasksClick()
                        3 -> onAlertsClick()
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
            if (overdueTasks.isNotEmpty() && activeFilter == TasksFilter.MY && !showArchived) {
                val firstOverdue = overdueTasks.first()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (overdueTasks.size == 1) {
                                    stringResource(id = R.string.tasks_overdue_one)
                                } else {
                                    stringResource(id = R.string.tasks_overdue_many, overdueTasks.size)
                                },
                                color = ErrorRed,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(firstOverdue.title, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TabButton(
                        text = stringResource(id = R.string.tasks_tab_my),
                        selected = activeFilter == TasksFilter.MY,
                        onClick = { viewModel.setActiveFilter(TasksFilter.MY) }
                    )
                    TabButton(
                        text = stringResource(id = R.string.tasks_tab_all),
                        selected = activeFilter == TasksFilter.ALL,
                        onClick = { viewModel.setActiveFilter(TasksFilter.ALL) }
                    )
                }

                TextButton(onClick = { viewModel.toggleArchived() }) {
                    Text(
                        if (showArchived) {
                            stringResource(id = R.string.tasks_hide_archived)
                        } else {
                            stringResource(id = R.string.tasks_show_archived)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.animation.AnimatedVisibility(
                visible = isSwitchingFilter,
                enter = androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(delayMillis = 120)
                ),
                exit = androidx.compose.animation.fadeOut()
            ) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val currentTodo = if (activeFilter == TasksFilter.MY) myTodoTasks else relatedTodoTasks
            val currentDone = if (activeFilter == TasksFilter.MY) myDoneTasks else relatedDoneTasks
            val currentArchived = if (activeFilter == TasksFilter.MY) myArchivedTasks else relatedArchivedTasks
            val isReadOnlyTab = activeFilter == TasksFilter.ALL
            val assigneeFallback = if (activeFilter == TasksFilter.MY) {
                stringResource(id = R.string.tasks_assignee_me)
            } else {
                stringResource(id = R.string.tasks_assignee_assigned)
            }

            val hasVisibleTasks = if (showArchived) {
                currentArchived.isNotEmpty()
            } else {
                currentTodo.isNotEmpty() || currentDone.isNotEmpty()
            }

            if (isLoading && !isSwitchingFilter && !hasVisibleTasks) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (!hasVisibleTasks && !isSwitchingFilter) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(id = R.string.tasks_empty), color = TextGray)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            if (!showArchived) {
                                if (currentTodo.isNotEmpty()) {
                                    item(key = "header_todo") {
                                        Text(
                                            text = stringResource(id = R.string.tasks_header_todo, currentTodo.size),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.8.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                                        )
                                    }

                                    items(currentTodo, key = { "todo_${it.taskId}" }) { task ->
                                        TaskCard(
                                            task = task,
                                            readOnly = isReadOnlyTab,
                                            assigneeFallbackLabel = assigneeFallback,
                                            onCompleteRequest = { taskToComplete = it }
                                        )
                                    }
                                }

                                if (currentDone.isNotEmpty()) {
                                    item(key = "header_done") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { completedExpanded = !completedExpanded }
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = stringResource(id = R.string.tasks_header_done, currentDone.size),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    letterSpacing = 0.8.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                imageVector = if (completedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = if (completedExpanded) {
                                                    stringResource(id = R.string.common_collapse)
                                                } else {
                                                    stringResource(id = R.string.common_expand)
                                                },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    }

                                    if (completedExpanded) {
                                        items(currentDone, key = { "done_${it.taskId}" }) { task ->
                                            TaskCard(
                                                task = task,
                                                readOnly = true,
                                                assigneeFallbackLabel = assigneeFallback,
                                                onCompleteRequest = {}
                                            )
                                        }
                                    }
                                }
                            } else {
                                item(key = "header_archived") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { archivedExpanded = !archivedExpanded }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.tasks_header_archived, currentArchived.size),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 0.8.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = if (archivedExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (archivedExpanded) {
                                                stringResource(id = R.string.common_collapse)
                                            } else {
                                                stringResource(id = R.string.common_expand)
                                            },
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }

                                if (archivedExpanded) {
                                    items(currentArchived, key = { "arch_${it.taskId}" }) { task ->
                                        TaskCard(
                                            task = task,
                                            readOnly = true,
                                            assigneeFallbackLabel = assigneeFallback,
                                            onCompleteRequest = {}
                                        )
                                    }
                                }
                            }

                            item(key = "tasks_loading_more_footer") {
                                if (isLoadingMore) {
                                    val loadingLabel = stringResource(id = R.string.tasks_loading_more)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                            .semantics { contentDescription = loadingLabel },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = ConvenePrimary,
                                            strokeWidth = 3.dp
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

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollToTop,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            SmallFloatingActionButton(
                                onClick = { scope.launch { listState.animateScrollToItem(0) } }
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowUp,
                                    contentDescription = stringResource(id = R.string.common_scroll_to_top)
                                )
                            }
                        }
                    }
                }
            }

            if (!error.isNullOrBlank()) {
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) White else Color.Transparent,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, TextGray.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) PrimaryBlue else TextGray,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun TaskCard(
    task: TaskResponse,
    readOnly: Boolean,
    assigneeFallbackLabel: String,
    onCompleteRequest: (TaskResponse) -> Unit
) {
    val isDone = task.status == "completed"
    val isArchived = task.status == "archived"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                isDone -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = OnlineGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }

                isArchived -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = TextGray.copy(alpha = 0.65f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                readOnly -> {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(2.dp, TextGray.copy(alpha = 0.4f), CircleShape)
                            .clip(CircleShape)
                    )
                }

                else -> {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clip(CircleShape)
                            .clickable { onCompleteRequest(task) }
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (isDone || isArchived) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (task.source == "ai_summary") {
                        Surface(
                            color = PurpleAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.tasks_ai_label),
                                color = PurpleAccent,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (readOnly) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(id = R.string.tasks_read_only),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextGray
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = task.meetingTitle ?: stringResource(id = R.string.tasks_meeting_fallback),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("•", color = TextGray)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Person, contentDescription = null, tint = TextGray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = task.assigneeName?.takeIf { it.isNotBlank() } ?: assigneeFallbackLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    task.dueDate?.let { date ->
                        val overdue = isTaskOverdue(date) && !isDone
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = if (overdue) ErrorRed else TextGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = formatDate(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) ErrorRed else TextGray
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    PriorityBadge(priority = task.priority)
                }
            }
        }
    }
}

@Composable
private fun PriorityBadge(priority: String) {
    val normalized = priority.lowercase()
    val (textColor, bgColor, label) = when (normalized) {
        "high" -> Triple(
            ErrorRed,
            ErrorRed.copy(alpha = 0.12f),
            stringResource(id = R.string.tasks_priority_high)
        )
        "medium" -> Triple(
            Color(0xFFD97706),
            Color(0xFFD97706).copy(alpha = 0.12f),
            stringResource(id = R.string.tasks_priority_medium)
        )
        else -> Triple(
            OnlineGreen,
            OnlineGreen.copy(alpha = 0.12f),
            stringResource(id = R.string.tasks_priority_low)
        )
    }

    Surface(shape = RoundedCornerShape(12.dp), color = bgColor) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingPickerField(
    label: String,
    selectedMeetingTitle: String?,
    meetings: List<TaskViewModel.MeetingSummary>,
    onMeetingSelected: (TaskViewModel.MeetingSummary?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Champ déclencheur (readOnly)
    OutlinedTextField(
        value = selectedMeetingTitle ?: stringResource(id = R.string.tasks_all_meetings),
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        modifier = modifier.fillMaxWidth(),
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedMeetingTitle != null) {
                    IconButton(
                        onClick = {
                            onMeetingSelected(null)
                            searchQuery = ""
                        }
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(id = R.string.common_clear),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                IconButton(onClick = { showPicker = true }) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(id = R.string.common_open))
                }
            }
        }
    )

    // Dialog de sélection (taille contrôlée)
    if (showPicker) {
        AlertDialog(
            onDismissRequest = {
                showPicker = false
                searchQuery = ""
            },
            title = { Text(label, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Champ de recherche
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(id = R.string.tasks_search_meeting_placeholder)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )

                    HorizontalDivider()

                    // Réunions filtrées
                    val filtered = remember(searchQuery, meetings) {
                        if (searchQuery.isBlank()) meetings
                        else meetings.filter { m ->
                            m.title.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    val meetingListState = rememberLazyListState()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        LazyColumn(
                            state = meetingListState,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Option "Toutes les réunions" en tête
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            stringResource(id = R.string.tasks_all_meetings),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (selectedMeetingTitle == null)
                                                MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    leadingContent = {
                                        if (selectedMeetingTitle == null) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Spacer(Modifier.size(16.dp))
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onMeetingSelected(null)
                                            searchQuery = ""
                                            showPicker = false
                                        }
                                )
                                HorizontalDivider()
                            }

                            if (filtered.isEmpty()) {
                                item {
                                    Text(
                                        stringResource(id = R.string.tasks_no_meeting_found),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else {
                                items(filtered, key = { it.id }) { meeting ->
                                    val isSelected = selectedMeetingTitle == meeting.title
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                meeting.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        leadingContent = {
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Spacer(Modifier.size(16.dp))
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onMeetingSelected(meeting)
                                                searchQuery = ""
                                                showPicker = false
                                            }
                                    )
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant
                                            .copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }

                        VisibleLazyColumnScrollbar(
                            listState = meetingListState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPicker = false
                    searchQuery = ""
                }) {
                    Text(stringResource(id = R.string.common_close))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterTasksSheet(
    meetings: List<TaskViewModel.MeetingSummary>,
    filterMeetingId: String?,
    filterMeetingTitle: String?,
    filterStatus: String?,
    filterBeforeDate: String?,
    onMeetingChange: (String?, String?) -> Unit,
    onStatusChange: (String?) -> Unit,
    onBeforeDateChange: (String?) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    // Calculer minuit du jour courant en UTC millis
    val todayMillis = remember {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }

    // Objet SelectableDates qui bloque le passé
    val futureSelectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            return utcTimeMillis >= todayMillis
        }
        override fun isSelectableYear(year: Int): Boolean {
            return year >= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(stringResource(id = R.string.tasks_filter_title), style = MaterialTheme.typography.titleLarge)

            Text(
                stringResource(id = R.string.tasks_filter_meeting),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MeetingPickerField(
                label = stringResource(id = R.string.tasks_filter_meeting),
                selectedMeetingTitle = filterMeetingTitle,
                meetings = meetings,
                onMeetingSelected = { meeting ->
                    onMeetingChange(meeting?.id, meeting?.title)
                }
            )

            Text(
                stringResource(id = R.string.tasks_filter_status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    null to stringResource(id = R.string.tasks_status_all),
                    "todo" to stringResource(id = R.string.tasks_status_todo),
                    "completed" to stringResource(id = R.string.tasks_status_completed)
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = filterStatus == value,
                        onClick = { onStatusChange(value) },
                        label = { Text(label) }
                    )
                }
            }

            Text(
                stringResource(id = R.string.tasks_filter_deadline_before),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = filterBeforeDate ?: stringResource(id = R.string.tasks_no_limit),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        if (filterBeforeDate != null) {
                            IconButton(onClick = { onBeforeDateChange(null) }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        }
                    }
                }
            )

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(selectableDates = futureSelectableDates)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                onBeforeDateChange(sdf.format(Date(millis)))
                            }
                            showDatePicker = false
                        }) {
                            Text(stringResource(id = R.string.common_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(id = R.string.common_cancel))
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(id = R.string.common_reset))
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(id = R.string.common_apply))
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateTaskSheet(
    meetings: List<TaskViewModel.MeetingSummary>,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var selectedMeetingId by remember { mutableStateOf<String?>(null) }
    var selectedMeetingTitle by remember { mutableStateOf<String?>(null) }
    var priority by remember { mutableStateOf("medium") }
    var showDatePicker by remember { mutableStateOf(false) }

    // Calculer minuit du jour courant en UTC millis
    val todayMillis = remember {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        cal.timeInMillis
    }

    // Objet SelectableDates qui bloque le passé
    val futureSelectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            return utcTimeMillis >= todayMillis
        }
        override fun isSelectableYear(year: Int): Boolean {
            return year >= java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(id = R.string.tasks_new_title), style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(id = R.string.tasks_title_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(stringResource(id = R.string.tasks_related_meeting), style = MaterialTheme.typography.labelMedium)
            MeetingPickerField(
                label = stringResource(id = R.string.tasks_filter_meeting),
                selectedMeetingTitle = selectedMeetingTitle,
                meetings = meetings,
                onMeetingSelected = { meeting ->
                    selectedMeetingId = meeting?.id
                    selectedMeetingTitle = meeting?.title
                }
            )

            Text(stringResource(id = R.string.tasks_priority_label), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "high" to stringResource(id = R.string.tasks_priority_high),
                    "medium" to stringResource(id = R.string.tasks_priority_medium),
                    "low" to stringResource(id = R.string.tasks_priority_low)
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = priority == value,
                        onClick = { priority = value },
                        label = { Text(label) }
                    )
                }
            }

            Text(stringResource(id = R.string.tasks_due_date_label), style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = dueDate,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(id = R.string.tasks_due_date_label)) },
                placeholder = { Text(stringResource(id = R.string.tasks_select_date)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        if (dueDate.isNotEmpty()) {
                            IconButton(onClick = { dueDate = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        }
                    }
                }
            )

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(selectableDates = futureSelectableDates)
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                dueDate = sdf.format(Date(millis))
                            }
                            showDatePicker = false
                        }) {
                            Text(stringResource(id = R.string.common_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(id = R.string.common_cancel))
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Button(
                onClick = {
                    val meetingId = selectedMeetingId ?: return@Button
                    if (title.isBlank()) return@Button
                    onCreate(
                        title.trim(),
                        meetingId,
                        priority,
                        dueDate.ifBlank { null }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(id = R.string.tasks_create))
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun isTaskOverdue(date: String): Boolean {
    val parsed = parseDate(date) ?: return false
    return parsed < System.currentTimeMillis()
}

private fun formatDate(date: String): String {
    val parsed = parseDate(date) ?: return date
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(parsed))
}

private fun parseDate(value: String): Long? {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd"
    )

    for (pattern in patterns) {
        val formatter = SimpleDateFormat(pattern, Locale.US)
        formatter.isLenient = true
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        val date = runCatching { formatter.parse(value) }.getOrNull()
        if (date != null) {
            return date.time
        }
    }

    return null
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TasksScreenPreview() {
    ConveneTheme {
        TasksScreen(
            onBackClick = {},
            onCreateTaskClick = {},
            onHomeClick = {},
            onCreateClick = {},
            onAlertsClick = {},
            onProfileClick = {},
            onTasksClick = {}
        )
    }
}

