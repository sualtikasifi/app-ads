package com.sualtikasifi.cizimhafiza.presentation.bottraining

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.AppTextField
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * The passcode in front of Bot Eğitim.
 *
 * Bot training writes to shared Firestore data that every player's opponent
 * is built from, and the tile sits on the home screen of a shipping build —
 * so anything a curious player types in there lands in everyone's game. The
 * code is what keeps the tile from being wandered into.
 *
 * It is deliberately not a security control, and is not treated as one:
 * [ACCESS_CODE] is a constant in an APK anyone can unzip. It stops accidents
 * and casual poking, which is the entire threat here — the people meant to
 * get in are a handful of friends being told the number directly. The whole
 * feature comes out of the app once training is finished, gate included.
 *
 * The gate wraps the screen rather than living inside it so that
 * [BotTrainingViewModel] — and the Firestore reads its init kicks off — is
 * never constructed for someone who cannot get past this.
 */
@Composable
fun BotTrainingGate(
    onBack: () -> Unit,
    onWordReview: () -> Unit,
    onDifficultyReview: () -> Unit,
    viewModel: BotTrainingGateViewModel = hiltViewModel()
) {
    var unlocked by remember { mutableStateOf(viewModel.isUnlocked) }

    if (unlocked) {
        BotTrainingScreen(
            onBack = onBack,
            onWordReview = onWordReview,
            onDifficultyReview = onDifficultyReview
        )
        return
    }

    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val submit = {
        if (entered == ACCESS_CODE) {
            viewModel.unlock()
            unlocked = true
        } else {
            wrong = true
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            RaisedCard(
                corner = 28.dp,
                raise = 7.dp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🔒", style = MaterialTheme.typography.displaySmall)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.bot_training_locked_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.bot_training_locked_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    AppTextField(
                        value = entered,
                        onValueChange = { input ->
                            // Digits only, and no longer than the code itself:
                            // there is nothing else worth typing here, and a
                            // stray character would otherwise fail the compare
                            // invisibly.
                            entered = input.filter(Char::isDigit).take(ACCESS_CODE.length)
                            wrong = false
                        },
                        label = stringResource(R.string.bot_training_code_label),
                        centered = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 8.sp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (wrong) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.bot_training_code_wrong),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    PrimaryButton(
                        text = stringResource(R.string.bot_training_unlock),
                        onClick = submit,
                        enabled = entered.length == ACCESS_CODE.length,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            ScreenTopActions(
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopStart),
                title = stringResource(R.string.menu_bot_training)
            )
        }
    }
}

/** Shared with the handful of people training the bot; see [BotTrainingGate]. */
private const val ACCESS_CODE = "8991"

@HiltViewModel
class BotTrainingGateViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isUnlocked: Boolean get() = settingsRepository.botTrainingUnlocked

    fun unlock() {
        settingsRepository.botTrainingUnlocked = true
    }
}
