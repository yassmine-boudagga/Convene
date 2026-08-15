package com.yassmine.projetpfe.viewmodel

import android.content.Context
import android.net.Uri
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.api.AIResultResponse
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.TaskResponse
import com.yassmine.projetpfe.notifications.NotificationWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class SummaryUiState {
    data object Loading : SummaryUiState()
    data object Processing : SummaryUiState()
    data object NotAvailable : SummaryUiState()
    data class Success(
        val aiResult: AIResultResponse,
        val tasks: List<TaskResponse>,
    ) : SummaryUiState()
    data class Empty(val reason: String) : SummaryUiState()
    data class Error(val message: String) : SummaryUiState()
}

@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val apiService: ApiService,
    private val wsClient: NotificationWebSocketClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val meetingId: String = savedStateHandle.get<String>("meetingId").orEmpty()

    private val _uiState = MutableStateFlow<SummaryUiState>(SummaryUiState.Loading)
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    private val _meetingTitle = MutableStateFlow(savedStateHandle.get<String>("meetingTitle").orEmpty())
    val meetingTitle: StateFlow<String> = _meetingTitle.asStateFlow()

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    sealed class ExportState {
        data object Idle : ExportState()
        data object Loading : ExportState()
        data class Success(val uri: Uri) : ExportState()
        data class Error(val message: String) : ExportState()
    }

    init {
        observeSummaryReadyEvents()
        loadSummary()
    }

    private fun observeSummaryReadyEvents() {
        viewModelScope.launch {
            wsClient.aiSummaryReadyEvents.collect { event ->
                if (event.meetingId == meetingId) {
                    onSummaryReady()
                }
            }
        }
    }

    fun loadSummary() {
        if (meetingId.isBlank()) {
            _uiState.value = SummaryUiState.Error("meetingId invalide")
            return
        }

        viewModelScope.launch {
            _uiState.value = SummaryUiState.Loading
            try {
                val aiResultDeferred = async { apiService.getMeetingAIResult(meetingId) }
                val tasksDeferred = async { apiService.getMeetingTasks(meetingId) }

                val aiResultResponse = aiResultDeferred.await()
                val tasksResponse = tasksDeferred.await()
                val aiResultData = aiResultResponse.body()?.data
                val tasksData = tasksResponse.body()?.data ?: emptyList()

                when {
                    aiResultResponse.isSuccessful && aiResultData != null -> {
                        // Vérifier si le résumé est vide (audio silencieux)
                        if (aiResultData.transcript?.rawText.isNullOrBlank()) {
                            _uiState.value = SummaryUiState.Empty("Aucune parole détectée dans l'enregistrement.")
                        } else {
                            _uiState.value = SummaryUiState.Success(
                                aiResult = aiResultData,
                                tasks = if (tasksResponse.isSuccessful) tasksData else emptyList()
                            )
                        }
                    }
                    aiResultResponse.code() == 404 -> {
                        _uiState.value = SummaryUiState.NotAvailable
                    }
                    aiResultResponse.code() == 202 -> {
                        _uiState.value = SummaryUiState.Processing
                    }
                    else -> {
                        _uiState.value = SummaryUiState.Error(
                            "Erreur ${aiResultResponse.code()}: " +
                                (aiResultResponse.body()?.message ?: "Erreur inconnue")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SummaryUiState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun onSummaryReady() {
        loadSummary()
    }

    fun exportToPdf(context: Context, meetingTitle: String = "Réunion") {
        val summary = (uiState.value as? SummaryUiState.Success)?.aiResult?.summary ?: return
        _exportState.value = ExportState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pdfDocument = android.graphics.pdf.PdfDocument()
                val pageW = 595
                val pageH = 842
                val margin = 50f

                var pageNum = 1

                var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create()
                var page = pdfDocument.startPage(pageInfo)
                var canvas = page.canvas
                var y = 60f

                val paintBody = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                }
                val paintTitle = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.BLACK
                    textSize = 20f
                    isFakeBoldText = true
                }
                val paintSection = android.graphics.Paint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.BLACK
                    textSize = 14f
                    isFakeBoldText = true
                }

                val usableWidth = (pageW - margin * 2).toInt()

                fun ensureSpace(needed: Float) {
                    if (y + needed > pageH - margin) {
                        pdfDocument.finishPage(page)
                        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageW, pageH, pageNum++).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = 60f
                    }
                }

                fun drawMultilineText(text: String, paint: android.graphics.Paint, indent: Float = 0f) {
                    val availW = usableWidth - indent.toInt()
                    val sl = StaticLayout.Builder
                        .obtain(
                            text,
                            0,
                            text.length,
                            TextPaint().apply { set(paint) },
                            availW
                        )
                        .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(4f, 1f)
                        .setIncludePad(false)
                        .build()

                    ensureSpace(sl.height.toFloat())
                    canvas.save()
                    canvas.translate(margin + indent, y)
                    sl.draw(canvas)
                    canvas.restore()
                    y += sl.height + 6f
                }

                ensureSpace(40f)
                canvas.drawText("Compte-rendu : $meetingTitle", margin, y, paintTitle)
                y += 35f

                ensureSpace(20f)
                canvas.drawText("Points clés :", margin, y, paintSection)
                y += 20f
                summary.keyPoints.forEach { drawMultilineText("• $it", paintBody, indent = 15f) }
                y += 8f

                ensureSpace(20f)
                canvas.drawText("Décisions :", margin, y, paintSection)
                y += 20f
                summary.decisions.forEach { drawMultilineText("• $it", paintBody, indent = 15f) }
                y += 8f

                ensureSpace(20f)
                canvas.drawText("Actions :", margin, y, paintSection)
                y += 20f
                summary.actionItems.forEach { drawMultilineText("• ${it.text}", paintBody, indent = 15f) }

                pdfDocument.finishPage(page)

                val safeNameRaw = meetingTitle
                    .replace(Regex("[^a-zA-Z0-9À-ÿ\\s_-]"), "")
                    .trim()
                    .replace(" ", "_")
                    .take(40)
                val safeName = safeNameRaw.ifBlank { "Reunion" }

                val file = java.io.File(
                    context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
                    "compte_rendu_${safeName}.pdf"
                )
                pdfDocument.writeTo(java.io.FileOutputStream(file))
                pdfDocument.close()

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )

                withContext(Dispatchers.Main) {
                    _exportState.value = ExportState.Success(uri)
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Compte-rendu : $meetingTitle")
                                putExtra(android.content.Intent.EXTRA_TITLE, "compte_rendu_${safeName}.pdf")
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            "Télécharger / Partager le PDF"
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SummaryVM", "Erreur export PDF", e)
                withContext(Dispatchers.Main) {
                    _exportState.value = ExportState.Error(e.message ?: "Erreur export")
                }
            }
        }
    }

    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }
}
