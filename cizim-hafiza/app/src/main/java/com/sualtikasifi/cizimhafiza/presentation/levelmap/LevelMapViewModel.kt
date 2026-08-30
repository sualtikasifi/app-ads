package com.sualtikasifi.cizimhafiza.presentation.levelmap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.LevelCatalog
import com.sualtikasifi.cizimhafiza.domain.model.World
import com.sualtikasifi.cizimhafiza.domain.repository.LevelProgressRepository
import com.sualtikasifi.cizimhafiza.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class LevelNodeState(
    val levelIndex: Int,
    val unlocked: Boolean,
    val stars: Int,
    val isNext: Boolean
)

data class LevelMapUiState(
    val world: World? = null,
    val levels: List<LevelNodeState> = emptyList()
)

@HiltViewModel
class LevelMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    levelProgressRepository: LevelProgressRepository
) : ViewModel() {

    private val worldId: Int = savedStateHandle.get<Int>(Screen.ArgWorldId) ?: 1
    private val world = World.forId(worldId)

    private val _uiState = MutableStateFlow(LevelMapUiState(world = world))
    val uiState: StateFlow<LevelMapUiState> = _uiState.asStateFlow()

    init {
        levelProgressRepository.observeWorldProgress(worldId)
            .onEach { progress ->
                val starsByLevel = progress.associate { it.levelIndex to it.bestStars }
                var nextAssigned = false
                val levels = (1..LevelCatalog.LEVELS_PER_WORLD).map { levelIndex ->
                    val unlocked = levelIndex == 1 || starsByLevel.containsKey(levelIndex - 1)
                    val isNext = unlocked && !starsByLevel.containsKey(levelIndex) && !nextAssigned
                    if (isNext) nextAssigned = true
                    LevelNodeState(
                        levelIndex = levelIndex,
                        unlocked = unlocked,
                        stars = starsByLevel[levelIndex] ?: 0,
                        isNext = isNext
                    )
                }
                _uiState.value = LevelMapUiState(world = world, levels = levels)
            }
            .launchIn(viewModelScope)
    }
}
