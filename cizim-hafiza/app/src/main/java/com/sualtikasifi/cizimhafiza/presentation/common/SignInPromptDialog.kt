package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import com.sualtikasifi.cizimhafiza.presentation.account.AccountViewModel
import com.sualtikasifi.cizimhafiza.util.asString

/**
 * Shown once, right after a new player's very first finished match (see
 * util/PostMatchPrompts.shouldShowSignIn) — before there is much progress
 * to lose, but while "you could lose this" is still a persuasive thing to
 * say, rather than waiting until there is a lot on the line and the player
 * has already gotten used to not being asked.
 *
 * Reuses [AccountViewModel] rather than talking to AuthRepository directly:
 * signIn()'s linkWithCredential path already keeps this device's progress
 * exactly as it is (see AccountViewModel's own doc), so there is no separate
 * "migrate progress" step to build here — signing in through the normal
 * account flow already does it. This dialog is just an earlier, friendlier
 * front door to that same flow, not a second implementation of it.
 */
@Composable
fun SignInPromptDialog(onDismiss: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // The moment sign-in actually lands, this has done its job — no reason
    // to keep sitting over the result screen waiting for a manual close.
    LaunchedEffect(uiState.authState) {
        if (uiState.authState is AuthState.Linked) onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(tween(150)),
            exit = fadeOut(tween(120))
        ) {
            RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "🎉☁️🏆", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.sign_in_prompt_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.sign_in_prompt_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    if (uiState.isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    } else {
                        GoogleSignInButton(onClick = viewModel::signIn, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        SecondaryButton(
                            text = stringResource(R.string.sign_in_prompt_later_button),
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    uiState.errorMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = message.asString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
