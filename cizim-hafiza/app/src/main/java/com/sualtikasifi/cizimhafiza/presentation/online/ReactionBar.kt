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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

/** A short preset emoji reaction — kept to a fixed set (no free text) so there's nothing to moderate. */
data class PresetReaction(val emoji: String, val key: String, @StringRes val labelRes: Int)

/** A short preset chat phrase — same "nothing to moderate" reasoning as [PresetReaction], just text instead of an emoji. */
data class PresetPhrase(val key: String, @StringRes val textRes: Int)

/** Kept as an "extra" quick-send row — [PRESET_PHRASES] below is the primary way to say something now. */
val PRESET_EMOJIS = listOf(
    PresetReaction("😂", "funny", R.string.reaction_funny),
    PresetReaction("👏", "nice", R.string.reaction_nice),
    PresetReaction("😅", "hard", R.string.reaction_hard),
    PresetReaction("🔥", "fire", R.string.reaction_fire),
    PresetReaction("😱", "shock", R.string.reaction_shock),
    PresetReaction("👋", "hi", R.string.reaction_hi)
)

val PRESET_PHRASES = listOf(
    PresetPhrase("chat_selam", R.string.chat_selam),
    PresetPhrase("chat_hazir_misin", R.string.chat_hazir_misin),
    PresetPhrase("chat_bol_sans", R.string.chat_bol_sans),
    PresetPhrase("chat_iyi_eglenceler", R.string.chat_iyi_eglenceler),
    PresetPhrase("chat_hos_geldin", R.string.chat_hos_geldin),
    PresetPhrase("chat_harikasin", R.string.chat_harikasin),
    PresetPhrase("chat_super_cizim", R.string.chat_super_cizim),
    PresetPhrase("chat_bildin", R.string.chat_bildin),
    PresetPhrase("chat_aynen_oyle", R.string.chat_aynen_oyle),
    PresetPhrase("chat_vay_canina", R.string.chat_vay_canina),
    PresetPhrase("chat_cok_iyiydi", R.string.chat_cok_iyiydi),
    PresetPhrase("chat_tam_isabet", R.string.chat_tam_isabet),
    PresetPhrase("chat_ne_ciziyorsun", R.string.chat_ne_ciziyorsun),
    PresetPhrase("chat_az_kaldi", R.string.chat_az_kaldi),
    PresetPhrase("chat_elinden_geliyor", R.string.chat_elinden_geliyor),
    PresetPhrase("chat_detaylara_bak_sen", R.string.chat_detaylara_bak_sen),
    PresetPhrase("chat_bilecegim", R.string.chat_bilecegim),
    PresetPhrase("chat_zor_bir_tane", R.string.chat_zor_bir_tane),
    PresetPhrase("chat_aklima_geldi", R.string.chat_aklima_geldi),
    PresetPhrase("chat_ipucu_ver", R.string.chat_ipucu_ver),
    PresetPhrase("chat_ne_bu_oyle", R.string.chat_ne_bu_oyle),
    PresetPhrase("chat_anlamadim_hic", R.string.chat_anlamadim_hic),
    PresetPhrase("chat_sanat_bu", R.string.chat_sanat_bu),
    PresetPhrase("chat_bir_daha_dene", R.string.chat_bir_daha_dene),
    PresetPhrase("chat_yakaladim_seni", R.string.chat_yakaladim_seni),
    PresetPhrase("chat_bu_turu_kazanacagim", R.string.chat_bu_turu_kazanacagim),
    PresetPhrase("chat_rovans_istiyorum", R.string.chat_rovans_istiyorum),
    PresetPhrase("chat_kafa_kafaya", R.string.chat_kafa_kafaya),
    PresetPhrase("chat_gorusuruz", R.string.chat_gorusuruz),
    PresetPhrase("chat_tekrar_oynayalim_mi", R.string.chat_tekrar_oynayalim_mi)
)

@StringRes
fun presetLabelRes(messageKey: String): Int? =
    PRESET_EMOJIS.find { it.key == messageKey }?.labelRes

@StringRes
fun presetPhraseTextRes(messageKey: String): Int? =
    PRESET_PHRASES.find { it.key == messageKey }?.textRes

/**
 * "Sohbet" button opening a scrollable sheet of [PRESET_PHRASES] (the
 * primary way to say something to the other player/players), plus a small
 * row of [PRESET_EMOJIS] underneath, explicitly labeled as an extra —
 * emoji-only reactions used to be the only option here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReactionSendRow(onSend: (emoji: String, messageKey: String) -> Unit, modifier: Modifier = Modifier) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = { sheetOpen = true },
            shape = PillShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = stringResource(R.string.reaction_chat_button),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.reaction_emoji_extra_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterHorizontally)
        ) {
            PRESET_EMOJIS.forEach { preset ->
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

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.reaction_chat_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(360.dp)
                ) {
                    items(PRESET_PHRASES, key = { it.key }) { phrase ->
                        Surface(
                            onClick = {
                                sheetOpen = false
                                // No emoji of its own — the phrase text (which
                                // may already contain one inline, e.g. "Selam! 👋")
                                // is the whole message. See ReactionOverlay's
                                // phrase branch for how this renders.
                                onSend("", phrase.key)
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(phrase.textRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
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
                    val phraseTextRes = presetPhraseTextRes(reaction.messageKey)
                    if (phraseTextRes != null) {
                        // A chat phrase (see PRESET_PHRASES): the text itself
                        // is the whole message, shown big instead of an emoji.
                        // Capped at 2 lines + a max width so a longer phrase
                        // still fits the fixed-height overlay slot both
                        // WaitingRoomScreen and OnlineResultScreen reserve for
                        // this composable.
                        Text(
                            text = stringResource(phraseTextRes),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.widthIn(max = 220.dp)
                        )
                    } else {
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
}
