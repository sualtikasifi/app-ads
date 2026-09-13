package com.sualtikasifi.cizimhafiza.presentation.reports

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
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.AppTextField
import com.sualtikasifi.cizimhafiza.presentation.common.DEVELOPER_ACCESS_CODE
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.ScreenTopActions
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground

/**
 * The passcode in front of the report inbox.
 *
 * Reports carry other players' drawings and the uid of whoever was reported,
 * which is nobody else's business — and unlike Bot Eğitim this screen has to
 * stay reachable in a shipping build, since reports arrive while the game is
 * live. The tap-the-version door that leads here (see SettingsScreen) hides
 * it; this stops whoever stumbles through that door anyway.
 *
 * Deliberately NOT remembered between visits, unlike Bot Eğitim's gate: that
 * one is unlocked once by someone about to spend hours training, while this
 * is opened for a minute now and then, and a phone that has been unlocked
 * once should not hand the inbox to whoever picks it up next.
 *
 * Wraps the screen rather than living inside it so [DrawingReportsViewModel]
 * — and the Firestore read its init kicks off — is never constructed for
 * somebody who cannot get past this.
 */
@Composable
fun DrawingReportsGate(onBack: () -> Unit) {
    var unlocked by remember { mutableStateOf(false) }

    if (unlocked) {
        DrawingReportsScreen(onBack = onBack)
        return
    }

    var entered by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val submit = {
        if (entered == DEVELOPER_ACCESS_CODE) {
            unlocked = true
        } else {
            wrong = true
            entered = ""
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .screenBackground()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.reports_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        AppTextField(
                            value = entered,
                            onValueChange = { input ->
                                entered = input.filter(Char::isDigit).take(DEVELOPER_ACCESS_CODE.length)
                                wrong = false
                            },
                            label = stringResource(R.string.reports_gate_label),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            textStyle = MaterialTheme.typography.headlineSmall.copy(
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (wrong) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.reports_gate_wrong),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryButton(
                            text = stringResource(R.string.reports_gate_enter),
                            onClick = submit,
                            enabled = entered.length == DEVELOPER_ACCESS_CODE.length,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            ScreenTopActions(
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopStart),
                title = stringResource(R.string.reports_title)
            )
        }
    }
}
