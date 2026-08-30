package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R

/** Sude's (the bot player's) visual expression — derived by callers from
 * existing room/reaction state, never stored in Firestore (see BotMascot.kt
 * usage in WaitingRoomScreen/OnlineResultScreen). */
enum class BotMascotPose { IDLE, WAVE, HAPPY, THINKING, SAD }

@Composable
fun BotMascot(pose: BotMascotPose, modifier: Modifier = Modifier) {
    val drawableRes = when (pose) {
        BotMascotPose.IDLE -> R.drawable.online_lobby_dino
        BotMascotPose.WAVE -> R.drawable.sude_wave
        BotMascotPose.HAPPY -> R.drawable.sude_happy
        BotMascotPose.THINKING -> R.drawable.sude_thinking
        BotMascotPose.SAD -> R.drawable.sude_sad
    }
    Crossfade(targetState = drawableRes, animationSpec = tween(220), label = "bot-mascot-pose") { res ->
        Image(
            painter = painterResource(res),
            contentDescription = null,
            modifier = modifier.size(40.dp)
        )
    }
}
