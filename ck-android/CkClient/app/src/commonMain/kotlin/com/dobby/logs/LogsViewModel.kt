package com.dobby.logs

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LogsViewModel(
    private val logsRepository: LogsRepository,
    private val copyLogsInteractor: CopyLogsInteractor
): ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())

    val uiState: StateFlow<LogsUiState> = _uiState

    init {
        val logsState = LogsUiState(logsRepository.readLogs())
        _uiState.tryEmit(logsState)
    }

    fun clearLogs() {
        logsRepository.clearLogs()
        _uiState.tryEmit(LogsUiState())
    }

    fun copyLogsToClipBoard() {
        copyLogsInteractor.copy(_uiState.value.logMessages)
    }
}
