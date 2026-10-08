package com.pandagallery.app.ui.compression

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pandagallery.app.data.compression.CompressionQueueRepository
import com.pandagallery.app.domain.compression.CompressionOperationSummary
import com.pandagallery.app.domain.compression.CompressionStatistics
import com.pandagallery.app.domain.compression.CompressionTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompressionManagerViewModel @Inject constructor(
    private val repository: CompressionQueueRepository,
) : ViewModel() {
    val uiState: StateFlow<CompressionManagerUiState> = combine(
        repository.activeTasks,
        repository.historyAnalytics,
    ) { active, analytics ->
        CompressionManagerUiState(
            activeTasks = active,
            history = analytics.operations,
            historyTasks = analytics.tasks,
            statistics = analytics.statistics,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CompressionManagerUiState(),
    )

    fun pause(id: String) = viewModelScope.launch { repository.pause(id) }
    fun resume(id: String) = viewModelScope.launch { repository.resume(id) }
    fun retry(id: String) = viewModelScope.launch { repository.retry(id) }
    fun delete(id: String) = viewModelScope.launch { repository.delete(id) }
    fun pauseAll() = viewModelScope.launch { repository.pauseAll() }
    fun resumeAll() = viewModelScope.launch { repository.resumeAll() }
    fun cancelAllActive() = viewModelScope.launch { repository.cancelAllActive() }
    fun retryAllFailed() = viewModelScope.launch { repository.retryAllFailed() }
    fun clearHistory() = viewModelScope.launch { repository.clearHistory() }
}

data class CompressionManagerUiState(
    val activeTasks: List<CompressionTask> = emptyList(),
    val history: List<CompressionOperationSummary> = emptyList(),
    val historyTasks: List<CompressionTask> = emptyList(),
    val statistics: CompressionStatistics = CompressionStatistics(),
)
