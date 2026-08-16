package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A [ScrollState] pre-jumped to its bottom before [isReady] turns true, so a
 * "start at the bottom, climb up" map screen (WorldMapScreen/LevelMapScreen)
 * never flashes its top content for a frame before snapping down — the
 * caller should hide its scrollable content (e.g. Modifier.alpha/graphicsLayer)
 * until [isReady]. The 500ms timeout is a safety net in case content turns
 * out short enough that maxValue never becomes positive; without it the
 * content would stay hidden forever.
 */
@Composable
fun rememberBottomAlignedScrollState(): Pair<ScrollState, Boolean> {
    val scrollState = rememberScrollState()
    var isReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withTimeoutOrNull(500) {
            snapshotFlow { scrollState.maxValue }.filter { it > 0 }.first()
        }
        if (scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
        isReady = true
    }
    return scrollState to isReady
}
