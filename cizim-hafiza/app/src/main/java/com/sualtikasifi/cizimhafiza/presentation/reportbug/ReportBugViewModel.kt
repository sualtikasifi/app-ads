package com.sualtikasifi.cizimhafiza.presentation.reportbug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.BugReport
import com.sualtikasifi.cizimhafiza.domain.model.BugReportCategory
import com.sualtikasifi.cizimhafiza.domain.repository.BugReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.util.UiText

data class ReportBugUiState(
    val category: BugReportCategory = BugReportCategory.SUGGESTION,
    val description: String = "",
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: UiText? = null
)

@HiltViewModel
class ReportBugViewModel @Inject constructor(
    private val repository: BugReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportBugUiState())
    val uiState: StateFlow<ReportBugUiState> = _uiState.asStateFlow()

    // Past submissions with any developer reply attached — see
    // BugReportRepository.observeMyReports. Previously reports were
    // strictly write-only: send it, get a "thanks" toast, and never hear
    // anything again, which reads as shouting into a void.
    val myReports: StateFlow<List<BugReport>> = repository.observeMyReports()
        .catch { } // a listener retry (see firestoreFlow) is invisible here; the last good list just stays on screen
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())

    fun onDescriptionChanged(text: String) {
        _uiState.update { it.copy(description = text, errorMessage = null) }
    }

    fun onCategorySelected(category: BugReportCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun submit() {
        val state = _uiState.value
        val description = state.description.trim()
        if (description.isEmpty() || state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            repository.submitReport(description, state.category)
                .onSuccess {
                    _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
                }
                .onFailure {
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = UiText.of(R.string.error_report_send_failed)) }
                }
        }
    }
}
