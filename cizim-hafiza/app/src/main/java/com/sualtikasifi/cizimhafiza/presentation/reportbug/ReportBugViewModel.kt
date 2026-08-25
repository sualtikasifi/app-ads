package com.sualtikasifi.cizimhafiza.presentation.reportbug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.repository.BugReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportBugUiState(
    val description: String = "",
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ReportBugViewModel @Inject constructor(
    private val repository: BugReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportBugUiState())
    val uiState: StateFlow<ReportBugUiState> = _uiState.asStateFlow()

    fun onDescriptionChanged(text: String) {
        _uiState.update { it.copy(description = text, errorMessage = null) }
    }

    fun submit() {
        val description = _uiState.value.description.trim()
        if (description.isEmpty() || _uiState.value.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.submitReport(description)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
                }
                .onFailure {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = "Gönderilemedi, tekrar dene") }
                }
        }
    }
}
