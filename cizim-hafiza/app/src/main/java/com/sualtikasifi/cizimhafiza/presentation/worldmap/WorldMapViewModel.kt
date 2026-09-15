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
    val totalStars: Int,
    /** The lowest-id unlocked world the player hasn't finished yet — "where you left off". */
    val isCurrent: Boolean
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
                // The first unlocked world still short of a full clear — a
                // player who has finished every unlocked world (right after
                // clearing one, before the next unlocks) gets no marker
                // rather than a wrong one pointing at a finished world.
                val currentWorldId = World.entries
                    .firstOrNull { world ->
                        LevelCatalog.isWorldUnlocked(world.id, completedCounts) &&
                            (completedCounts[world.id] ?: 0) < LevelCatalog.LEVELS_PER_WORLD
                    }
                    ?.id
                val worlds = World.entries.map { world ->
                    WorldCardState(
                        world = world,
                        unlocked = LevelCatalog.isWorldUnlocked(world.id, completedCounts),
                        completedLevels = completedCounts[world.id] ?: 0,
                        totalStars = progress.filter { it.worldId == world.id }.sumOf { it.bestStars },
                        isCurrent = world.id == currentWorldId
                    )
                }
                _uiState.value = WorldMapUiState(worlds)
            }
            .launchIn(viewModelScope)
    }
}
