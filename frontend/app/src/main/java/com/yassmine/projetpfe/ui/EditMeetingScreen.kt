package com.yassmine.projetpfe.ui


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.data.api.UpdateMeetingRequest
import com.yassmine.projetpfe.data.api.UserSearchResult
import com.yassmine.projetpfe.ui.components.AppSnackbarHost
import com.yassmine.projetpfe.ui.components.showError
import com.yassmine.projetpfe.ui.theme.*
import com.yassmine.projetpfe.viewmodel.MeetingViewModel
import com.yassmine.projetpfe.viewmodel.SocialViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMeetingScreen(
    meetingId: String,
    onBack: () -> Unit,
    onUpdateSuccess: () -> Unit,
    onHomeClick: () -> Unit,
    onTasksClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: MeetingViewModel = hiltViewModel(),
    socialViewModel: SocialViewModel = hiltViewModel()
) {
    val selectedMeeting by viewModel.selectedMeeting.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val operationSuccess by viewModel.operationSuccess.collectAsState()

    // Load meeting data on start
    LaunchedEffect(meetingId) {
        viewModel.getMeetingById(meetingId)
    }

    // Navigate back on success
    LaunchedEffect(operationSuccess) {
        if (operationSuccess) {
            viewModel.resetOperationSuccess()
            onUpdateSuccess()
        }
    }

    val meeting = selectedMeeting

    // Pre-fill state once meeting is loaded
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedParticipants by remember { mutableStateOf<List<UserSearchResult>>(emptyList()) }
    var showParticipantPicker by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var selectedTime by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDurationMenu by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }

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

    val durationOptions = listOf("15 mins", "30 mins", "45 mins", "1 hour", "1.5 hours", "2 hours", "3 hours")
    val durationMinutes = mapOf(
        "15 mins" to 15, "30 mins" to 30, "45 mins" to 45,
        "1 hour" to 60, "1.5 hours" to 90, "2 hours" to 120, "3 hours" to 180
    )
    val minutesToLabel = durationMinutes.entries.associate { (k, v) -> v to k }
    val friends by socialViewModel.friends.collectAsState()

    //  Pre-fill form from loaded meeting
    LaunchedEffect(meeting) {
        if (meeting != null && !isInitialized) {
            title = meeting.title
            description = meeting.description ?: ""
            duration = minutesToLabel[meeting.duration] ?: "${meeting.duration} mins"

            // Parse the UTC startTime and convert to local timezone for editing
            try {
                val instant = Instant.parse(meeting.startTime)
                val zdt = instant.atZone(ZoneId.systemDefault())
                selectedDate = zdt.toInstant().toEpochMilli()
                selectedTime = Pair(zdt.hour, zdt.minute)
            } catch (_: Exception) {
                // Fallback: leave empty
            }
            isInitialized = true
        }
    }

    LaunchedEffect(friends, meeting) {
        if (meeting != null && friends.isNotEmpty() && isInitialized) {
            val meetingEmails = meeting.participants.map { it.trim().lowercase() }.toSet()
            selectedParticipants = friends.filter {
                it.email.trim().lowercase() in meetingEmails
            }
        }
    }

    LaunchedEffect(Unit) {
        socialViewModel.loadFriends()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showError(it)
            viewModel.clearError()
        }
    }

    val dateFormatter = java.text.SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val dateText = selectedDate?.let { dateFormatter.format(Date(it)) } ?: ""
    val timeText = selectedTime?.let { (h, m) -> String.format(Locale.getDefault(), "%02d:%02d", h, m) } ?: ""

    val isFormValid = title.isNotBlank() && duration.isNotBlank()

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Meeting",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = TextDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = White)
            )
        },
        bottomBar = {
            BottomNavBar(
                selectedIndex = -1,
                onIndexSelected = { index ->
                    when (index) {
                        0 -> onHomeClick()
                        2 -> onTasksClick()
                        3 -> onAlertsClick()
                        4 -> onProfileClick()
                    }
                }
            )
        }
    ) { paddingValues ->

        if (meeting == null && isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
            return@Scaffold
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(BackgroundLight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                //  TITLE 
                Column {
                    Row {
                        Text("Meeting Title", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark)
                        Text(" *", color = ErrorRed, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("e.g.,Strategy Planning", color = TextLight) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
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

                //  DATE & TIME 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text("Date", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark)
                            Text(" *", color = ErrorRed, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = {},
                            placeholder = { Text("jj/mm/aaaa", color = TextLight) },
                            leadingIcon = {
                                Icon(Icons.Default.CalendarMonth, null, tint = TextGray, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).clickable { showDatePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = TextLight.copy(alpha = 0.3f),
                                disabledContainerColor = White,
                                disabledTextColor = TextDark,
                                disabledLeadingIconColor = TextGray
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row {
                            Text("Time", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark)
                            Text(" *", color = ErrorRed, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = timeText,
                            onValueChange = {},
                            placeholder = { Text("--:--", color = TextLight) },
                            leadingIcon = {
                                Icon(Icons.Default.AccessTime, null, tint = TextGray, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).clickable { showTimePicker = true },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = TextLight.copy(alpha = 0.3f),
                                disabledContainerColor = White,
                                disabledTextColor = TextDark,
                                disabledLeadingIconColor = TextGray
                            )
                        )
                    }
                }

                //  DURATION 
                Column {
                    Row {
                        Text("Duration", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark)
                        Text(" *", color = ErrorRed, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        OutlinedTextField(
                            value = duration,
                            onValueChange = {},
                            placeholder = { Text("Select duration", color = TextLight) },
                            trailingIcon = {
                                Icon(Icons.Default.KeyboardArrowDown, null, tint = TextGray)
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp).clickable { showDurationMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            readOnly = true,
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledBorderColor = TextLight.copy(alpha = 0.3f),
                                disabledContainerColor = White,
                                disabledTextColor = TextDark,
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
                                    onClick = { duration = option; showDurationMenu = false }
                                )
                            }
                        }
                    }
                }

                //  PARTICIPANTS 
                Column {
                    Text("Participants", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (selectedParticipants.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            items(selectedParticipants, key = { it.id }) { user ->
                                InputChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(user.name, fontSize = 12.sp) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Retirer",
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    selectedParticipants = selectedParticipants.filter {
                                                        it.id != user.id
                                                    }
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

                    OutlinedButton(
                        onClick = { showParticipantPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Ajouter un participant", fontSize = 13.sp)
                    }
                }

                //  DESCRIPTION 
                Column {
                    Text("Agenda (Optional)", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = {
                            Text("Enter meeting agenda and topics to discuss...", color = TextLight, fontSize = 13.sp)
                        },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
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

                //  SAVE BUTTON 
                Button(
                    onClick = {
                        val durationMins = durationMinutes[duration]

                        //  Build startTime with local timezone offset (same fix as CreateMeeting)
                        val startTimeIso: String? = if (selectedDate != null && selectedTime != null) {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = selectedDate!!
                                set(Calendar.HOUR_OF_DAY, selectedTime!!.first)
                                set(Calendar.MINUTE, selectedTime!!.second)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val localDateTime = cal.toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime()
                            val zdt = ZonedDateTime.of(localDateTime, ZoneId.systemDefault())
                            zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        } else null

                        val participantList = selectedParticipants.map { it.email }

                        val request = UpdateMeetingRequest(
                            title = title.takeIf { it.isNotBlank() },
                            description = description,
                            startTime = startTimeIso,
                            duration = durationMins,
                            participants = participantList.takeIf { it.isNotEmpty() }
                        )
                        viewModel.updateMeeting(meetingId, request)
                    },
                    enabled = isFormValid && !isLoading,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = White,
                        disabledContainerColor = PrimaryBlue.copy(alpha = 0.4f)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Changes", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

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

            //  DATE PICKER 
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedDate = datePickerState.selectedDateMillis
                            showDatePicker = false
                        }) { Text("OK", color = PrimaryBlue) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = TextGray) }
                    }
                ) { DatePicker(state = datePickerState) }
            }

            //  TIME PICKER 
            if (showTimePicker) {
                AlertDialog(
                    onDismissRequest = { showTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedTime = Pair(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) { Text("OK", color = PrimaryBlue) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = TextGray) }
                    },
                    text = { TimeInput(state = timePickerState) }
                )
            }
        }
    }
}