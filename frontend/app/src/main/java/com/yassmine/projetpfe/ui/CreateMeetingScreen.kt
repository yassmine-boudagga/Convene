package com.yassmine.projetpfe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.CreateMeetingRequest
import com.yassmine.projetpfe.data.api.UserSearchResult
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.VisibleLazyColumnScrollbar
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.components.showSuccess
import com.yassmine.projetpfe.ui.theme.*
import com.yassmine.projetpfe.viewmodel.MeetingViewModel
import com.yassmine.projetpfe.viewmodel.SocialViewModel
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import androidx.compose.foundation.layout.imePadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMeetingScreen(
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onHomeClick: () -> Unit,
    onTasksClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: MeetingViewModel = hiltViewModel(),
    socialViewModel: SocialViewModel = hiltViewModel()
) {
    val operationSuccess by viewModel.operationSuccess.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    CreateMeetingScreenContent(
        operationSuccess = operationSuccess,
        isLoading = isLoading,
        error = error,
        onCreateMeeting = { request -> viewModel.createMeeting(request) },
        onResetSuccess = { viewModel.resetOperationSuccess() },
        onClearError = { viewModel.clearError() },
        onBackClick = onBackClick,
        onCreateClick = onCreateClick,
        onHomeClick = onHomeClick,
        onTasksClick = onTasksClick,
        onAlertsClick = onAlertsClick,
        onProfileClick = onProfileClick,
        socialViewModel = socialViewModel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateMeetingScreenContent(
    operationSuccess: Boolean,
    isLoading: Boolean,
    error: String?,
    onCreateMeeting: (CreateMeetingRequest) -> Unit,
    onResetSuccess: () -> Unit,
    onClearError: () -> Unit,
    onBackClick: () -> Unit,
    onCreateClick: () -> Unit,
    onHomeClick: () -> Unit,
    onTasksClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onProfileClick: () -> Unit,
    socialViewModel: SocialViewModel = hiltViewModel()
) {
    var meetingTitle by remember { mutableStateOf("") }
    var meetingType by remember { mutableStateOf("Online") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var duration by remember { mutableStateOf("") }
    var selectedParticipants by remember { mutableStateOf<List<UserSearchResult>>(emptyList()) }
    var location by remember { mutableStateOf("") }
    var agenda by remember { mutableStateOf("") }
    var showParticipantPicker by remember { mutableStateOf(false) }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDurationMenu by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                return utcTimeMillis >= todayStart
            }
        }
    )
    val timePickerState = rememberTimePickerState()

    val durationOptions = listOf(
        stringResource(id = R.string.create_meeting_duration_15),
        stringResource(id = R.string.create_meeting_duration_30),
        stringResource(id = R.string.create_meeting_duration_45),
        stringResource(id = R.string.create_meeting_duration_60),
        stringResource(id = R.string.create_meeting_duration_90),
        stringResource(id = R.string.create_meeting_duration_120),
        stringResource(id = R.string.create_meeting_duration_180)
    )
    val durationMinutes = mapOf(
        durationOptions[0] to 15,
        durationOptions[1] to 30,
        durationOptions[2] to 45,
        durationOptions[3] to 60,
        durationOptions[4] to 90,
        durationOptions[5] to 120,
        durationOptions[6] to 180
    )

    val dateFormatter = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateText = selectedDate?.let { dateFormatter.format(Date(it)) } ?: ""
    val timeText = selectedTime?.let { (h, m) -> String.format("%02d:%02d", h, m) } ?: ""

    val isFormValid =
        meetingTitle.isNotBlank() &&
                selectedDate != null &&
                selectedTime != null &&
                duration.isNotBlank() &&
                selectedParticipants.isNotEmpty() &&
                (meetingType == "Online" || location.isNotBlank())

    val snackbarHostState = remember { SnackbarHostState() }
    val startTimeFutureError = stringResource(id = R.string.create_meeting_error_start_time_future)

    LaunchedEffect(operationSuccess) {
        if (operationSuccess) {
            launch {
                snackbarHostState.showSuccess("Réunion créée avec succès")
            }
            onResetSuccess()
            onCreateClick()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showError(it)
            onClearError()
        }
    }

    LaunchedEffect(localValidationError) {
        localValidationError?.let {
            snackbarHostState.showError(it)
            localValidationError = null
        }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.create_meeting_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.create_meeting_back),
                            tint = TextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedIndex = 1,
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

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(color = BackgroundLight)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                //   MEETING TITLE  
                Column {
                    Row {
                        Text(
                            text = stringResource(id = R.string.create_meeting_field_title),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark
                        )
                        Text(text = " *", color = ErrorRed, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = meetingTitle,
                        onValueChange = { meetingTitle = it },
                        placeholder = {
                            Text(text = stringResource(id = R.string.create_meeting_title_placeholder), color = TextLight)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue.copy(alpha = 0.5f),
                            unfocusedBorderColor = TextLight.copy(alpha = 0.3f),
                            focusedContainerColor = White,
                            unfocusedContainerColor = White
                        )
                    )
                }

                //   MEETING TYPE  
                Column {
                    Row {
                        Text(
                            text = stringResource(id = R.string.create_meeting_field_type),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark
                        )
                        Text(text = " *", color = ErrorRed, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MeetingTypeOption(
                            label = stringResource(id = R.string.create_meeting_type_online),
                            icon = Icons.Default.VideoCall,
                            isSelected = meetingType == "Online",
                            onClick = { meetingType = "Online" },
                            modifier = Modifier.weight(1f)
                        )
                        MeetingTypeOption(
                            label = stringResource(id = R.string.create_meeting_type_physical),
                            icon = Icons.Default.LocationOn,
                            isSelected = meetingType == "Physical",
                            onClick = { meetingType = "Physical" },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                //   DATE & TIME  
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text(
                                text = stringResource(id = R.string.create_meeting_field_date),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark
                            )
                            Text(text = " *", color = ErrorRed, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = {},
                            placeholder = {
                                Text(text = stringResource(id = R.string.create_meeting_date_placeholder), color = TextLight)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = TextGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showDatePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = TextLight.copy(alpha = 0.3f),
                                disabledContainerColor = White,
                                disabledTextColor = TextDark,
                                disabledPlaceholderColor = TextLight,
                                disabledLeadingIconColor = TextGray
                            )
                        )
                    }

                    // Time
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text(
                                text = stringResource(id = R.string.create_meeting_field_time),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark
                            )
                            Text(text = " *", color = ErrorRed, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = timeText,
                            onValueChange = {},
                            placeholder = {
                                Text(text = stringResource(id = R.string.create_meeting_time_placeholder), color = TextLight)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = TextGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable (
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ){ showTimePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = TextLight.copy(alpha = 0.3f),
                                disabledContainerColor = White,
                                disabledTextColor = TextDark,
                                disabledPlaceholderColor = TextLight,
                                disabledLeadingIconColor = TextGray
                            )
                        )
                    }
                }

                //   DURATION  
                Column {
                    Row {
                        Text(
                            text = stringResource(id = R.string.create_meeting_field_duration),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark
                        )
                        Text(text = " *", color = ErrorRed, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedTextField(
                            value = duration,
                            onValueChange = {},
                            placeholder = {
                                Text(text = stringResource(id = R.string.create_meeting_duration_placeholder), color = TextLight)
                            },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextGray
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clickable (
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ){ showDurationMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = TextLight.copy(alpha = 0.3f),
                                disabledContainerColor = White,
                                disabledTextColor = TextDark,
                                disabledPlaceholderColor = TextLight,
                                disabledTrailingIconColor = TextGray
                            )
                        )

                        DropdownMenu(
                            expanded = showDurationMenu,
                            onDismissRequest = { showDurationMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            durationOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        duration = option
                                        showDurationMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                //   LOCATION  
                if (meetingType == "Physical") {
                    Column {
                        Row {
                            Text(
                                text = stringResource(id = R.string.create_meeting_field_location),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark
                            )
                            Text(text = " *", color = ErrorRed, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            placeholder = {
                                Text(text = stringResource(id = R.string.create_meeting_location_placeholder), color = TextLight)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = TextGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue.copy(alpha = 0.5f),
                                unfocusedBorderColor = TextLight.copy(alpha = 0.3f),
                                focusedContainerColor = White,
                                unfocusedContainerColor = White
                            )
                        )
                    }
                }

                //   PARTICIPANTS  
                Column {
                    Row {
                        Text(
                            text = stringResource(id = R.string.create_meeting_field_participants),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextDark
                        )
                        Text(text = " *", color = ErrorRed, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Chips of selected participants
                    if (selectedParticipants.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            items(selectedParticipants, key = { it.id }) { user ->
                                InputChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(user.name, fontSize = 12.sp) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(id = R.string.create_meeting_remove_participant),
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    selectedParticipants = selectedParticipants.filter { it.id != user.id }
                                                }
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = PrimaryBlue.copy(alpha = 0.12f),
                                        selectedLabelColor = PrimaryBlue
                                    )
                                )
                            }
                        }
                    }

                    // Add participant button
                    OutlinedButton(
                        onClick = { showParticipantPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(id = R.string.create_meeting_add_participant), fontSize = 13.sp)
                    }
                }

                //  AGENDA  
                Column {
                    Text(
                        text = stringResource(id = R.string.create_meeting_field_agenda_optional),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = agenda,
                        onValueChange = { agenda = it },
                        placeholder = {
                            Text(
                                text = stringResource(id = R.string.create_meeting_agenda_placeholder),
                                color = TextLight,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue.copy(alpha = 0.5f),
                            unfocusedBorderColor = TextLight.copy(alpha = 0.3f),
                            focusedContainerColor = White,
                            unfocusedContainerColor = White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                //  BOUTON CREATE
                Button(
                    onClick = {
                        // Validation locale prioritaire
                        if (meetingTitle.trim().length < 4) {
                            localValidationError = "Le titre doit contenir au moins 4 caractères"
                            return@Button
                        }

                        val dateMillis = selectedDate ?: return@Button
                        val time = selectedTime ?: return@Button
                        val durationMins = durationMinutes[duration] ?: 60

                        val cal = Calendar.getInstance().apply {
                            timeInMillis = dateMillis
                            set(Calendar.HOUR_OF_DAY, time.first)
                            set(Calendar.MINUTE, time.second)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        val localDateTime = cal.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()

                        val zonedDateTime = ZonedDateTime.of(
                            localDateTime,
                            ZoneId.systemDefault()
                        )

                        if (zonedDateTime.isBefore(java.time.ZonedDateTime.now())) {
                            localValidationError = startTimeFutureError
                            return@Button
                        }

                        val startTimeIso = zonedDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                        val participantList = selectedParticipants.map { it.email }

                        val request = CreateMeetingRequest(
                            title = meetingTitle.trim(),
                            description = agenda,
                            startTime = startTimeIso,
                            duration = durationMins,
                            meetingType = if (meetingType == "Physical") "physical" else "online",
                            location = if (meetingType == "Physical") location.trim() else null,
                            participants = participantList
                        )
                        onCreateMeeting(request)
                    },
                    enabled = isFormValid && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = White,
                        disabledContainerColor = PrimaryBlue.copy(alpha = 0.4f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(id = R.string.create_meeting_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            //   DATE PICKER DIALOG  
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedDate = datePickerState.selectedDateMillis
                            showDatePicker = false
                        }) {
                            Text(stringResource(id = R.string.common_ok), color = PrimaryBlue)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text(stringResource(id = R.string.common_cancel), color = TextGray)
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            //   TIME PICKER DIALOG  
            if (showTimePicker) {
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedTime = Pair(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) {
                            Text(stringResource(id = R.string.common_ok), color = PrimaryBlue)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(id = R.string.common_cancel), color = TextGray)
                        }
                    },
                    text = {
                        TimeInput(state = timePickerState)
                    }
                )
            }
            //   PARTICIPANT PICKER BOTTOM SHEET  
            if (showParticipantPicker) {
                ParticipantPickerSheet(
                    socialViewModel = socialViewModel,
                    selectedIds = selectedParticipants.map { it.id }.toSet(),
                    onAdd = { user ->
                        if (selectedParticipants.none { it.id == user.id }) {
                            selectedParticipants = selectedParticipants + user
                        }
                    },
                    onDismiss = { showParticipantPicker = false }
                )
            }
        }
    }
}

//  Participant Picker BottomSheet  
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantPickerSheet(
    socialViewModel: SocialViewModel,
    selectedIds: Set<String>,
    onAdd: (com.yassmine.projetpfe.data.api.UserSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    val friends by socialViewModel.friends.collectAsState()
    val isLoading by socialViewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val friendsFiltered = remember(friends, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) {
            friends
        } else {
            friends.filter { user ->
                user.name.contains(q, ignoreCase = true) ||
                    user.email.contains(q, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(Unit) { socialViewModel.loadFriends() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(id = R.string.create_meeting_add_participant),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        stringResource(id = R.string.create_meeting_search_friends_placeholder),
                        color = TextLight
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextGray) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue.copy(alpha = 0.5f),
                    unfocusedBorderColor = TextLight.copy(alpha = 0.3f),
                    focusedContainerColor = White,
                    unfocusedContainerColor = White
                )
            )

            Spacer(Modifier.height(10.dp))

            if (isLoading && friendsFiltered.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (friendsFiltered.isEmpty()) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.create_meeting_no_friends_match),
                                        color = TextGray,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        items(friendsFiltered, key = { it.id }) { user ->
                            val alreadySelected = user.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !alreadySelected) { onAdd(user) }
                                    .background(
                                        if (alreadySelected) PrimaryBlue.copy(alpha = 0.05f) else White,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                UserAvatar(profilePicture = user.profilePicture, name = user.name, size = 38)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = TextDark)
                                    Text(user.email, fontSize = 12.sp, color = TextGray)
                                }
                                if (alreadySelected) {
                                    Icon(Icons.Default.Check, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
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
        }
    }
}

// Option type de réunion
@Composable
fun MeetingTypeOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PrimaryBlue else TextLight.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) UpcomingBlueBg else White
    ) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(
                        width = 2.dp,
                        color = if (isSelected) PrimaryBlue else TextLight,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color = PrimaryBlue, shape = androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) PrimaryBlue else TextGray,
                modifier = Modifier.size(18.dp)
            )

            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) PrimaryBlue else TextDark
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CreateMeetingScreenPreview() {
    ConveneTheme {
        CreateMeetingScreenContent(
            operationSuccess = false,
            isLoading = false,
            error = null,
            onCreateMeeting = {},
            onResetSuccess = {},
            onClearError = {},
            onBackClick = {},
            onCreateClick = {},
            onHomeClick = {},
            onTasksClick = {},
            onAlertsClick = {},
            onProfileClick = {}
        )
    }
}
