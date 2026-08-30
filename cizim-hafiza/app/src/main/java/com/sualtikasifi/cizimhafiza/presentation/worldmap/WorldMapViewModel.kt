package com.sualtikasifi.cizimhafiza.presentation.worldmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sualtikasifi.cizimhafiza.domain.model.LevelCatalog
import com.sualtikasifi.cizimhafiza.domain.model.World
import com.sualtikasifi.cizimhafiza.domain.repository.LevelProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

data class WorldCardState(
    val world: World,
    val unlocked: Boolean,
    val completedLevels: Int,
    val totalStars: Int
)

data class WorldMapUiState(val worlds: List<WorldCardState> = emptyList())

@HiltViewModel
class WorldMapViewModel @Inject constructor(
    levelProgressRepository: LevelProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorldMapUiState())
    val uiState: StateFlow<WorldMapUiState> = _uiState.asStateFlow()

    init {
        levelProgressRepository.observeAllProgress()
            .onEach { progress ->
                val completedCounts = progress
                    .groupingBy { it.worldId }
                    .eachCount()
                val worlds = World.entries.map { world ->
                    WorldCardState(
                        world = world,
                        unlocked = LevelCatalog.isWorldUnlocked(world.id, completedCounts),
                        completedLevels = completedCounts[world.id] ?: 0,
                        totalStars = progress.filter { it.worldId == world.id }.sumOf { it.bestStars }
                    )
                }
                _uiState.value = WorldMapUiState(worlds)
            }
            .launchIn(viewModelScope)
    }
}
