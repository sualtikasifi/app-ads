package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.domain.model.MatchInvite
import com.sualtikasifi.cizimhafiza.presentation.theme.CardWhite

/**
 * App-wide "X invited you!" banner — shown floating above whatever screen is
 * currently on top (see its usage in CizimHafizaNavGraph), so an invite is
 * never missed just because the player isn't on the Friends screen.
 */
@Composable
fun IncomingInviteBanner(
    invite: MatchInvite?,
    isResponding: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = invite != null,
        enter = slideInVertically(animationSpec = tween(280)) { -it } + fadeIn(tween(280)),
        exit = slideOutVertically(animationSpec = tween(220)) { -it } + fadeOut(tween(220)),
        modifier = modifier.statusBarsPadding().padding(16.dp)
    ) {
        if (invite != null) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.friends_incoming_invite, invite.fromNickname),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
                    if (isResponding) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Card(
                                onClick = onAccept,
                                shape = PillShape,
                                colors = CardDefaults.cardColors(containerColor = CardWhite, contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = stringResource(R.string.friends_accept_invite),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                )
                            }
                            Card(
                                onClick = onDecline,
                                shape = PillShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                            ) {
                                Text(
                                    text = stringResource(R.string.friends_decline_invite),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
