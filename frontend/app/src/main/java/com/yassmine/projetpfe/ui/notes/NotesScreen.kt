package com.yassmine.projetpfe.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.MeetingCreatorDto
import com.yassmine.projetpfe.data.api.MeetingNoteDto
import com.yassmine.projetpfe.ui.components.MeetingNoteCard
import com.yassmine.projetpfe.ui.components.VisibleLazyColumnScrollbar
import com.yassmine.projetpfe.ui.theme.ConveneTheme
import com.yassmine.projetpfe.ui.theme.PrimaryBlue
import com.yassmine.projetpfe.ui.theme.White
import com.yassmine.projetpfe.viewmodel.MeetingViewModel

// Couleurs locales
private val NoteBg        = Color(0xFFF4F7FB)
private val CardBg        = Color(0xFFFFFFFF)
private val TextPrimary   = Color(0xFF1A1F36)
private val TextSecondary = Color(0xFF8492A6)
private val InputBg       = Color(0xFFF0F4F8)
private val DividerColor  = Color(0xFFEDF1F7)

// Entry Point

@Composable
fun NotesScreen(
    meetingId: String,
    onBack: () -> Unit,
    viewModel: MeetingViewModel = hiltViewModel()
) {
    val notes by viewModel.notes.collectAsState()
    var noteText by remember { mutableStateOf("") }

    LaunchedEffect(meetingId) {
        viewModel.loadMeeting(meetingId)   // peuple _selectedMeeting
        viewModel.loadNotes(meetingId)
    }

    NotesScreenContent(
        notes = notes,
        noteText = noteText,
        onNoteTextChange = { noteText = it },
        onSendNote = {
            if (noteText.isNotBlank()) {
                viewModel.addNote(meetingId, noteText)
                noteText = ""
            }
        },
        onBack = onBack
    )
}

// Screen Content

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotesScreenContent(
    notes: List<MeetingNoteDto>,
    noteText: String,
    onNoteTextChange: (String) -> Unit,
    onSendNote: () -> Unit,
    onBack: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll vers le bas à chaque nouvelle note
    LaunchedEffect(notes.size) {
        if (notes.isNotEmpty()) {
            listState.animateScrollToItem(notes.size - 1)
        }
    }

    Scaffold(
        // PAS de imePadding ici le imePadding est sur la bottomBar uniquement
        containerColor = NoteBg,
        //Laisser le Scaffold utiliser les insets système par défaut

        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(id = R.string.notes_title_collaborative),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        if (notes.isNotEmpty()) {
                            Text(
                                text = if (notes.size > 1) {
                                    stringResource(id = R.string.notes_count_many, notes.size)
                                } else {
                                    stringResource(id = R.string.notes_count_one, notes.size)
                                },
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.notes_back),
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = White
                )
            )
        },

        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 12.dp, spotColor = Color(0x1A4F6AF5)),
                color = White,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()          
                        .imePadding()                    
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    //  Champ de saisie 
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = onNoteTextChange,
                        placeholder = {
                            Text(
                                stringResource(id = R.string.notes_placeholder),
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = PrimaryBlue,
                            unfocusedBorderColor    = DividerColor,
                            focusedContainerColor   = InputBg,
                            unfocusedContainerColor = InputBg,
                            cursorColor             = PrimaryBlue
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize   = 14.sp,
                            color      = TextPrimary,
                            lineHeight = 20.sp
                        )
                    )

                    //   Bouton envoi  
                    val canSend = noteText.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Bottom)
                            .background(
                                color = if (canSend) PrimaryBlue else Color(0xFFDDE4EF),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick  = onSendNote,
                            enabled  = canSend,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(id = R.string.notes_send),
                                tint               = if (canSend) White else Color(0xFFB0BAC5),
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->

        if (notes.isEmpty()) {
            // état vide
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                color = PrimaryBlue.copy(alpha = 0.08f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📝", fontSize = 30.sp)
                    }
                    Text(
                        text       = stringResource(id = R.string.notes_empty_title),
                        color      = TextPrimary,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text     = stringResource(id = R.string.notes_empty_subtitle),
                        color    = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            //   Liste des notes  
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state   = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        MeetingNoteCard(note = note)
                    }
                }

                VisibleLazyColumnScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NotesScreenWithNotesPreview() {
    val sampleNotes = listOf(
        MeetingNoteDto(id = "1",
            userId = MeetingCreatorDto(id = "u1", name = "Yassmine Boudagga", email = "y@example.com"),
            content = "cc",
            timestamp = "2026-02-25T20:07:00Z"),
        MeetingNoteDto(id = "2",
            userId = MeetingCreatorDto(id = "u1", name = "Yassmine Boudagga", email = "y@example.com"),
            content = "quel est le problem",
            timestamp = "2026-02-25T20:10:00Z"),
        MeetingNoteDto(id = "3",
            userId = MeetingCreatorDto(id = "u2", name = "Bob Dupont", email = "bob@example.com"),
            content = "N'oubliez pas de préparer le rapport pour vendredi !",
            timestamp = "2026-02-25T20:12:00Z")
    )
    ConveneTheme {
        NotesScreenContent(
            notes          = sampleNotes,
            noteText       = "",
            onNoteTextChange = {},
            onSendNote     = {},
            onBack         = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun NotesScreenEmptyPreview() {
    ConveneTheme {
        NotesScreenContent(
            notes          = emptyList(),
            noteText       = "",
            onNoteTextChange = {},
            onSendNote     = {},
            onBack         = {}
        )
    }
}
