package com.yassmine.projetpfe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.CreateTaskRequest
import com.yassmine.projetpfe.data.local.PreferencesManager
import com.yassmine.projetpfe.data.api.TaskResponse
import com.yassmine.projetpfe.data.repository.TaskFilterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TasksFilter {
    MY,
    ALL,
}

@HiltViewModel
class TaskViewModel @Inject constructor(
    private val apiService: ApiService,
    private val preferencesManager: PreferencesManager,
    private val taskFilterRepository: TaskFilterRepository,
) : ViewModel() {

    data class MeetingSummary(val id: String, val title: String)

    private val _tasks = MutableStateFlow<List<TaskResponse>>(emptyList())
    val tasks: StateFlow<List<TaskResponse>> = _tasks.asStateFlow()

    private val _relatedTasks = MutableStateFlow<List<TaskResponse>>(emptyList())
    val relatedTasks: StateFlow<List<TaskResponse>> = _relatedTasks.asStateFlow()

    private val _myTodoTasks = MutableStateFlow<List<TaskResponse>>(emptyList())
    val myTodoTasks: StateFlow<List<TaskResponse>> = _myTodoTasks.asStateFlow()

    private val _myCompletedTasks = MutableStateFlow<List<TaskResponse>>(emptyList())
    val myCompletedTasks: StateFlow<List<TaskResponse>> = _myCompletedTasks.asStateFlow()

    private val _myArchivedTasks = MutableStateFlow<List<TaskResponse>>(emptyList())
    val myArchivedTasks: StateFlow<List<TaskResponse>> = _myArchivedTasks.asStateFlow()

    private val _relatedTodoTasks = MutableStateFlow<List<TaskResponse>>(emptyList())
    val relatedTodoTasks: StateFlow<List<TaskResponse>> = _relatedTodoTasks.asStateFlow()

    private val _relatedCompletedTasks = MutableStateFlow<List<TaskResponse>>(emptyList())
    val relatedCompletedTasks: StateFlow<List<TaskResponse>> = _relatedCompletedTasks.asStateFlow()

    private val _relatedArchivedTasks = MutableStateFlow<List<TaskResponse>>(emptyList())
    val relatedArchivedTasks: StateFlow<List<TaskResponse>> = _relatedArchivedTasks.asStateFlow()

    private val _todoCount = MutableStateFlow(0)
    val todoCount: StateFlow<Int> = _todoCount.asStateFlow()

    private val _completedCount = MutableStateFlow(0)
    val completedCount: StateFlow<Int> = _completedCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    private val _hasMore = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isSwitchingFilter = MutableStateFlow(false)
    val isSwitchingFilter: StateFlow<Boolean> = _isSwitchingFilter.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    private val _activeFilter = MutableStateFlow(TasksFilter.MY)
    val activeFilter: StateFlow<TasksFilter> = _activeFilter.asStateFlow()

    private val _meetingsList = MutableStateFlow<List<MeetingSummary>>(emptyList())
    val meetingsList: StateFlow<List<MeetingSummary>> = _meetingsList.asStateFlow()

    private val _activeMeetingId = MutableStateFlow<String?>(null)
    val activeMeetingId: StateFlow<String?> = _activeMeetingId.asStateFlow()

    private val _activeStatus = MutableStateFlow<String?>(null)
    val activeStatus: StateFlow<String?> = _activeStatus.asStateFlow()

    private val _activeToDate = MutableStateFlow<String?>(null)
    val activeToDate: StateFlow<String?> = _activeToDate.asStateFlow()

    private var currentUserId: String? = null
    private var currentUserName: String? = null

    private val myBaseCacheByArchived = mutableMapOf<Boolean, List<TaskResponse>>()
    private val relatedCacheByArchived = mutableMapOf<Boolean, List<TaskResponse>>()

    init {
        viewModelScope.launch {
            preferencesManager.getUserId().collect { currentUserId = it }
        }
        viewModelScope.launch {
            preferencesManager.getUserName().collect { currentUserName = it }
        }

        loadMyTasks(force = true)
        loadRelatedTasks(force = true, showLoading = false)
        loadMeetingsList()
    }

    private fun applyMyTasks(tasks: List<TaskResponse>) {
        val normalized = tasks.map(::normalizeTaskResponse)
        _tasks.value = normalized
        _myTodoTasks.value = normalized.filter { it.status == "todo" }
        _myCompletedTasks.value = normalized.filter { it.status == "completed" }
        _myArchivedTasks.value = normalized.filter { it.status == "archived" }
        _todoCount.value = _myTodoTasks.value.size
        _completedCount.value = _myCompletedTasks.value.size
    }

    private fun applyRelatedTasks(tasks: List<TaskResponse>) {
        val normalized = tasks.map(::normalizeTaskResponse)
        _relatedTasks.value = normalized
        _relatedTodoTasks.value = normalized.filter { it.status == "todo" }
        _relatedCompletedTasks.value = normalized.filter { it.status == "completed" }
        _relatedArchivedTasks.value = normalized.filter { it.status == "archived" }
    }

    private fun shouldUseBaseCache(
        meetingId: String?,
        status: String?,
        toDate: String?,
    ): Boolean {
        return meetingId.isNullOrBlank() && status.isNullOrBlank() && toDate.isNullOrBlank()
    }

    private fun endpointForFilter(filter: TasksFilter): String {
        return if (filter == TasksFilter.MY) {
            TaskFilterRepository.ENDPOINT_MY
        } else {
            TaskFilterRepository.ENDPOINT_ALL
        }
    }

    private fun applyTasksForFilter(filter: TasksFilter, tasks: List<TaskResponse>) {
        if (filter == TasksFilter.MY) {
            applyMyTasks(tasks)
        } else {
            applyRelatedTasks(tasks)
        }
    }

    private fun cacheForFilter(filter: TasksFilter): MutableMap<Boolean, List<TaskResponse>> {
        return if (filter == TasksFilter.MY) {
            myBaseCacheByArchived
        } else {
            relatedCacheByArchived
        }
    }

    private fun shouldShowInMyList(task: TaskResponse): Boolean {
        val requestedArchived = _showArchived.value
        val status = normalizeTaskStatus(task.status)
        if (requestedArchived && status != "archived") return false
        if (!requestedArchived && status == "archived") return false

        val activeStatus = _activeStatus.value?.let(::normalizeTaskStatus)
        if (!activeStatus.isNullOrBlank() && status != activeStatus) return false

        val activeMeetingId = _activeMeetingId.value
        if (!activeMeetingId.isNullOrBlank() && task.meetingId != activeMeetingId) return false

        return true
    }

    private fun buildCreatedTask(
        serverTask: TaskResponse?,
        title: String,
        meetingId: String,
        priority: String,
        dueDate: String?,
    ): TaskResponse {
        val now = Instant.now().toString()
        val meetingTitle = _meetingsList.value.firstOrNull { it.id == meetingId }?.title

        val fallback = TaskResponse(
            id = "local_${System.currentTimeMillis()}",
            title = title,
            assigneeId = currentUserId,
            assigneeName = currentUserName,
            assigneeEmail = null,
            meetingId = meetingId,
            meetingTitle = meetingTitle,
            status = "todo",
            priority = priority,
            dueDate = dueDate,
            completedAt = null,
            archivedAt = null,
            source = "manual",
            createdAt = now,
            updatedAt = now,
        )

        val merged = if (serverTask == null) {
            fallback
        } else {
            fallback.copy(
                id = serverTask.id.takeIf { it.isNotBlank() } ?: fallback.id,
                title = serverTask.title.ifBlank { fallback.title },
                assigneeId = serverTask.assigneeId ?: fallback.assigneeId,
                assigneeName = serverTask.assigneeName ?: fallback.assigneeName,
                assigneeEmail = serverTask.assigneeEmail ?: fallback.assigneeEmail,
                meetingId = serverTask.meetingId ?: fallback.meetingId,
                meetingTitle = serverTask.meetingTitle ?: fallback.meetingTitle,
                status = normalizeTaskStatus(serverTask.status).ifBlank { fallback.status },
                priority = serverTask.priority.ifBlank { fallback.priority },
                dueDate = serverTask.dueDate ?: fallback.dueDate,
                completedAt = serverTask.completedAt ?: fallback.completedAt,
                archivedAt = serverTask.archivedAt ?: fallback.archivedAt,
                source = serverTask.source ?: fallback.source,
                createdAt = serverTask.createdAt ?: fallback.createdAt,
                updatedAt = serverTask.updatedAt ?: fallback.updatedAt,
            )
        }

        if (merged.assigneeName.isNullOrBlank() && merged.assigneeId == currentUserId) {
            return merged.copy(assigneeName = currentUserName)
        }

        return merged
    }

    fun loadMyTasks(
        meetingId: String? = _activeMeetingId.value,
        status: String? = _activeStatus.value,
        toDate: String? = _activeToDate.value,
        archived: Boolean = _showArchived.value,
        force: Boolean = false,
        showLoading: Boolean = true,
        switching: Boolean = false,
    ) {
        loadTasksForFilter(
            filter = TasksFilter.MY,
            meetingId = meetingId,
            status = status,
            toDate = toDate,
            archived = archived,
            force = force,
            showLoading = showLoading,
            switching = switching,
            page = 1,
        )
    }

    fun loadRelatedTasks(
        meetingId: String? = _activeMeetingId.value,
        status: String? = _activeStatus.value,
        toDate: String? = _activeToDate.value,
        archived: Boolean = _showArchived.value,
        force: Boolean = false,
        showLoading: Boolean = false,
        switching: Boolean = false,
    ) {
        loadTasksForFilter(
            filter = TasksFilter.ALL,
            meetingId = meetingId,
            status = status,
            toDate = toDate,
            archived = archived,
            force = force,
            showLoading = showLoading,
            switching = switching,
            page = 1,
        )
    }

    fun loadMoreTasks() {
        if (_activeFilter.value != TasksFilter.MY) return
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        val nextPage = _currentPage.value + 1
        loadTasksForFilter(
            filter = TasksFilter.MY,
            meetingId = _activeMeetingId.value,
            status = _activeStatus.value,
            toDate = _activeToDate.value,
            archived = _showArchived.value,
            force = true,
            showLoading = false,
            switching = false,
            page = nextPage,
        )
    }

    private fun loadTasksForFilter(
        filter: TasksFilter,
        meetingId: String?,
        status: String?,
        toDate: String?,
        archived: Boolean,
        force: Boolean,
        showLoading: Boolean,
        switching: Boolean,
        page: Int = 1,
    ) {
        val normalizedStatus = status?.let(::normalizeTaskStatus)
        val useBaseCache = shouldUseBaseCache(meetingId, normalizedStatus, toDate)
        val cache = cacheForFilter(filter)

        if (page == 1) {
            _currentPage.value = 1
        }

        if (page == 1 && !force && useBaseCache) {
            val cached = cache[archived]
            if (cached != null) {
                applyTasksForFilter(filter, cached)
                _activeMeetingId.value = meetingId
                _activeStatus.value = normalizedStatus
                _activeToDate.value = toDate
                _hasMore.value = false
                if (switching) {
                    _isSwitchingFilter.value = false
                }
                return
            }
        }

        _activeMeetingId.value = meetingId
        _activeStatus.value = normalizedStatus
        _activeToDate.value = toDate

        if (switching) {
            _isSwitchingFilter.value = true
        }

        viewModelScope.launch {
            if (page > 1) {
                _isLoadingMore.value = true
            } else if (showLoading) {
                _isLoading.value = true
            }

            if (filter == TasksFilter.MY) {
                _error.value = null
            }

            val endpoint = endpointForFilter(filter)

            try {
                val result = taskFilterRepository.fetchTasks(
                    endpoint = endpoint,
                    meetingId = meetingId,
                    status = normalizedStatus,
                    toDate = toDate,
                    archived = archived,
                    page = page,
                )

                result
                    .onSuccess { fetchResult ->
                        val loaded = fetchResult.tasks.map(::normalizeTaskResponse)
                        _hasMore.value = fetchResult.hasMore
                        if (page == 1) {
                            applyTasksForFilter(filter, loaded)
                            _currentPage.value = 1
                            if (useBaseCache) {
                                if (!fetchResult.hasMore) {
                                    cache[archived] = loaded
                                } else {
                                    cache.remove(archived)
                                }
                            }
                        } else {
                            _currentPage.value = page
                            val merged = if (filter == TasksFilter.MY) {
                                _tasks.value + loaded
                            } else {
                                _relatedTasks.value + loaded
                            }
                            applyTasksForFilter(filter, merged)
                            if (useBaseCache && !fetchResult.hasMore) {
                                cache[archived] = merged
                            }
                        }
                    }
                    .onFailure { error ->
                        if (filter == TasksFilter.MY) {
                            _error.value = error.message ?: "Erreur inconnue"
                        } else {
                            Log.e("TaskViewModel", "Error loading related tasks: ${error.message}")
                        }
                    }
            } finally {
                if (page > 1) {
                    _isLoadingMore.value = false
                } else if (showLoading) {
                    _isLoading.value = false
                }
                if (switching) {
                    _isSwitchingFilter.value = false
                }
            }
        }
    }

    fun completeTask(taskId: String) {
        if (taskId.isBlank()) return

        val previousTasks = _tasks.value
        val targetTask = previousTasks.firstOrNull { it.taskId == taskId } ?: return
        val optimisticTask = targetTask.copy(
            status = "completed",
            completedAt = Instant.now().toString(),
        )

        applyMyTasks(
            previousTasks.map { task ->
                if (task.taskId == taskId) optimisticTask else task
            }
        )
        myBaseCacheByArchived.clear()

        viewModelScope.launch {
            try {
                val response = apiService.completeTask(taskId)
                if (response.isSuccessful) {
                    val serverTask = response.body()?.data?.let(::normalizeTaskResponse)
                    val merged = _tasks.value.map { task ->
                        if (task.taskId != taskId) {
                            task
                        } else if (serverTask != null) {
                            mergeResolvedTask(task, serverTask)
                        } else {
                            task.copy(status = "completed")
                        }
                    }
                    applyMyTasks(merged)
                } else {
                    applyMyTasks(previousTasks)
                    _error.value = "Erreur ${response.code()}"
                }
            } catch (e: Exception) {
                applyMyTasks(previousTasks)
                _error.value = e.message ?: "Erreur inconnue"
            }
        }
    }

    fun createTask(
        title: String,
        meetingId: String,
        priority: String = "medium",
        dueDate: String? = null,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            try {
                val response = apiService.createTask(
                    CreateTaskRequest(
                        title = title,
                        meetingId = meetingId,
                        priority = priority,
                        dueDate = dueDate
                    )
                )
                if (response.isSuccessful) {
                    val newTask = buildCreatedTask(
                        serverTask = response.body()?.data,
                        title = title,
                        meetingId = meetingId,
                        priority = priority,
                        dueDate = dueDate,
                    )

                    val belongsToCurrentUser =
                        currentUserId.isNullOrBlank() || newTask.assigneeId == currentUserId

                    if (belongsToCurrentUser && shouldShowInMyList(newTask)) {
                        applyMyTasks(
                            listOf(newTask) + _tasks.value.filterNot { it.taskId == newTask.taskId }
                        )
                    }

                    myBaseCacheByArchived.clear()
                    onResult(true)
                } else {
                    _error.value = "Erreur ${response.code()}"
                    onResult(false)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur inconnue"
                onResult(false)
            }
        }
    }

    fun loadMeetingsList() {
        viewModelScope.launch {
            try {
                val response = apiService.getMeetings(status = null, all = true)
                val meetings = response.data.meetings.map { meeting ->
                    MeetingSummary(
                        id = meeting.realId,
                        title = meeting.title
                    )
                }
                _meetingsList.value = meetings.distinctBy { it.id }
            } catch (_: Exception) {
            }
        }
    }

    fun toggleArchived() {
        val newValue = !_showArchived.value
        _showArchived.value = newValue

        if (_activeFilter.value == TasksFilter.ALL) {
            relatedCacheByArchived.clear()
            loadRelatedTasks(
                meetingId = _activeMeetingId.value,
                status = _activeStatus.value,
                toDate = _activeToDate.value,
                archived = newValue,
                force = true,
                showLoading = true,
            )
        } else {
            myBaseCacheByArchived.clear()
            loadMyTasks(
                meetingId = _activeMeetingId.value,
                status = _activeStatus.value,
                toDate = _activeToDate.value,
                archived = newValue,
                force = true,
                showLoading = true,
            )
        }
    }

    fun setActiveFilter(filter: TasksFilter) {
        if (_activeFilter.value == filter) return

        _isSwitchingFilter.value = true
        _activeFilter.value = filter
        if (filter == TasksFilter.ALL) {
            val canUseBaseCache = shouldUseBaseCache(
                _activeMeetingId.value,
                _activeStatus.value,
                _activeToDate.value,
            )
            val cached = if (canUseBaseCache) relatedCacheByArchived[_showArchived.value] else null

            loadRelatedTasks(
                meetingId = _activeMeetingId.value,
                status = _activeStatus.value,
                toDate = _activeToDate.value,
                archived = _showArchived.value,
                force = cached == null,
                showLoading = false,
                switching = true,
            )
        } else {
            val canUseBaseCache = shouldUseBaseCache(
                _activeMeetingId.value,
                _activeStatus.value,
                _activeToDate.value,
            )
            val cached = if (canUseBaseCache) myBaseCacheByArchived[_showArchived.value] else null

            loadMyTasks(
                meetingId = _activeMeetingId.value,
                status = _activeStatus.value,
                toDate = _activeToDate.value,
                archived = _showArchived.value,
                force = cached == null,
                showLoading = false,
                switching = true,
            )
        }
    }

    fun applyFilters(
        meetingId: String?,
        status: String?,
        toDate: String?,
    ) {
        if (_activeFilter.value == TasksFilter.ALL) {
            loadRelatedTasks(
                meetingId = meetingId,
                status = status,
                toDate = toDate,
                archived = _showArchived.value,
                force = true,
                showLoading = true,
            )
        } else {
            loadMyTasks(
                meetingId = meetingId,
                status = status,
                toDate = toDate,
                archived = _showArchived.value,
                force = true,
                showLoading = true,
            )
        }
    }

    fun resetFilters() {
        applyFilters(
            meetingId = null,
            status = null,
            toDate = null,
        )
    }
}
