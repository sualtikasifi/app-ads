package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.presentation.common.PillShape
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import kotlinx.coroutines.delay

/** A short preset phrase paired with an emoji — kept to a fixed set (no free text) so there's nothing to moderate. */
data class PresetReaction(val emoji: String, val key: String, val label: String)

val PRESET_REACTIONS = listOf(
    PresetReaction("😂", "funny", "Çok komik!"),
    PresetReaction("👏", "nice", "Harika!"),
    PresetReaction("😅", "hard", "Zordu bu!"),
    PresetReaction("🔥", "fire", "Ateş gibisin!"),
    PresetReaction("😱", "shock", "Vay canına!"),
    PresetReaction("👋", "hi", "Selam!")
)

fun presetLabel(messageKey: String): String =
    PRESET_REACTIONS.find { it.key == messageKey }?.label ?: ""

/** Row of tappable emoji chips that send a preset reaction. */
@Composable
fun ReactionSendRow(onSend: (emoji: String, messageKey: String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = androidx.compose.ui.Alignment.CenterHorizontally)
    ) {
        PRESET_REACTIONS.forEach { preset ->
            Surface(
                onClick = { onSend(preset.emoji, preset.key) },
                shape = CircleShape,
                color = CardWhite,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(text = preset.emoji, fontSize = 22.sp, modifier = Modifier.padding(10.dp))
            }
        }
    }
}

/** Shows the most recent reaction from the other player as a short-lived floating bubble. */
@Composable
fun ReactionOverlay(reactions: List<Reaction>, myUid: String?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf<Reaction?>(null) }

    LaunchedEffect(reactions.size) {
        val latest = reactions.lastOrNull() ?: return@LaunchedEffect
        if (latest.uid == myUid) return@LaunchedEffect
        visible = latest
        delay(2500)
        visible = null
    }

    AnimatedVisibility(
        visible = visible != null,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = slideOutVertically { it / 2 } + fadeOut(),
        modifier = modifier
    ) {
        val reaction = visible
        if (reaction != null) {
            Surface(shape = PillShape, color = CardWhite, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Text(
                    text = "${reaction.emoji} ${presetLabel(reaction.messageKey)}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}
