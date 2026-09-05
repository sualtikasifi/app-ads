package com.sualtikasifi.cizimhafiza.presentation.quickmatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.GhostRun
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import com.sualtikasifi.cizimhafiza.domain.repository.GhostRunRepository
import com.sualtikasifi.cizimhafiza.domain.usecase.GetWordsByIdsUseCase
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the Hızlı Eşleş screen is doing right now.
 *
 * [Empty] and [Failed] are kept apart on purpose even though both end in
 * "no match". "Nobody has left a round behind yet" is a true and temporary
 * fact about a young game, and telling a player that is very different from
 * telling them something went wrong — the first invites them to go and play
 * a round themselves, the second invites them to try again.
 */
sealed interface QuickMatchState {
    data object Searching : QuickMatchState
    data class Found(val opponent: GhostRun) : QuickMatchState
    data object Empty : QuickMatchState
    data object Failed : QuickMatchState
}

@HiltViewModel
class QuickMatchViewModel @Inject constructor(
    private val ghostRunRepository: GhostRunRepository,
    private val getWordsByIdsUseCase: GetWordsByIdsUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<QuickMatchState>(QuickMatchState.Searching)
    val state: StateFlow<QuickMatchState> = _state.asStateFlow()

    init { search() }

    fun search() {
        _state.value = QuickMatchState.Searching
        viewModelScope.launch {
            val level = PlayerLevel.levelForXp(settingsRepository.lifetimeXp.value)
            repeat(MAX_ATTEMPTS) {
                val result = ghostRunRepository.findOpponent(level)
                val opponent = result.getOrElse {
                    _state.value = QuickMatchState.Failed
                    return@launch
                } ?: run {
                    _state.value = QuickMatchState.Empty
                    return@launch
                }
                if (isPlayable(opponent)) {
                    _state.value = QuickMatchState.Found(opponent)
                    return@launch
                }
            }
            // Every candidate the pool offered was unplayable here. Rare
            // enough to be worth no explanation of its own, and honest:
            // there was no match to be had.
            _state.value = QuickMatchState.Empty
        }
    }

    /**
     * Whether this device can actually deal the opponent's ten words.
     *
     * A word pool is versioned and a language's pool deliberately withholds
     * entries that do not translate, so a recorded round can name a word this
     * build has no copy of. Dealing nine words against an opponent's ten
     * would quietly rig the score, so such a candidate is skipped rather
     * than played — checked here, before the match is offered, because this
     * is the last point where trying somebody else is still free.
     */
    private suspend fun isPlayable(opponent: GhostRun): Boolean =
        runCatching { getWordsByIdsUseCase(opponent.wordIds).size == opponent.wordIds.size }
            .getOrDefault(false)

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
