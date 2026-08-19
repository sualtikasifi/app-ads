package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.OnlinePlayer
import com.sualtikasifi.cizimhafiza.domain.model.Reaction
import com.sualtikasifi.cizimhafiza.presentation.common.PillShape
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite
import kotlinx.coroutines.delay

/** A short preset phrase paired with an emoji — kept to a fixed set (no free text) so there's nothing to moderate. */
data class PresetReaction(val emoji: String, val key: String, @StringRes val labelRes: Int)

val PRESET_REACTIONS = listOf(
    PresetReaction("😂", "funny", R.string.reaction_funny),
    PresetReaction("👏", "nice", R.string.reaction_nice),
    PresetReaction("😅", "hard", R.string.reaction_hard),
    PresetReaction("🔥", "fire", R.string.reaction_fire),
    PresetReaction("😱", "shock", R.string.reaction_shock),
    PresetReaction("👋", "hi", R.string.reaction_hi)
)

@StringRes
fun presetLabelRes(messageKey: String): Int? =
    PRESET_REACTIONS.find { it.key == messageKey }?.labelRes

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

/**
 * Shows the most recent reaction — including your own, so sending one
 * doesn't look like it silently failed — as a short-lived, hard-to-miss
 * pop-up. [players] (the room's current roster) is only used to label the
 * bubble with who sent it, since a room can have up to
 * [GameConstants.MAX_ROOM_SIZE] players and the emoji alone stops being
 * enough to tell who reacted once there's more than one other player.
 */
@Composable
fun ReactionOverlay(reactions: List<Reaction>, myUid: String?, players: List<OnlinePlayer>, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf<Reaction?>(null) }

    // Keyed on the reaction's own identity (who sent it + when), not just
    // list size — a plain size-based key can coincidentally repeat (e.g.
    // across a rematch's fresh reaction stream) and silently miss re-firing
    // the animation for a genuinely new reaction that happens to land the
    // list back at a previously-seen size.
    val latestKey = reactions.lastOrNull()?.let { it.uid to it.sentAtMillis }
    LaunchedEffect(latestKey) {
        val latest = reactions.lastOrNull() ?: return@LaunchedEffect
        visible = latest
        delay(2500)
        visible = null
    }

    AnimatedVisibility(
        visible = visible != null,
        enter = scaleIn(
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        ) + fadeIn(tween(150)),
        exit = scaleOut(animationSpec = tween(200)) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        val reaction = visible
        if (reaction != null) {
            Surface(
                shape = PillShape,
                color = CardWhite,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    val senderName = if (reaction.uid == myUid) {
                        stringResource(R.string.online_you_label, "")
                    } else {
                        players.find { it.uid == reaction.uid }?.displayName
                    }
                    if (!senderName.isNullOrBlank()) {
                        Text(
                            text = senderName.trim(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(text = reaction.emoji, fontSize = 36.sp)
                    val labelRes = presetLabelRes(reaction.messageKey)
                    if (labelRes != null) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
