package com.sualtikasifi.cizimhafiza.presentation.online

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedIconButton
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.SocialButton
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground

@Composable
fun OnlineLobbyScreen(
    onBack: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onFriends: () -> Unit,
    onLeague: () -> Unit,
    viewModel: OnlineLobbyViewModel = hiltViewModel()
) {
    val pendingFriendRequests by viewModel.pendingFriendRequests.collectAsState()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            RaisedIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            // Teal disc, not orange: online is its own place in the app (see
            // the palette note in Color.kt). Ringed in white for the same
            // reason as the main menu's logo medallion — a crisp edge
            // against the textured collage background.
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.online_lobby_dino),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(0.78f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.online_lobby_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.online_lobby_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(34.dp))
            SocialButton(
                text = stringResource(R.string.online_create_room),
                onClick = onCreateRoom,
                icon = Icons.Filled.Add,
                height = 60.dp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                text = stringResource(R.string.online_join_room),
                onClick = onJoinRoom,
                icon = Icons.AutoMirrored.Filled.Login,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            // The count rides on the button rather than waiting inside the
            // Friends screen: a request that nobody knows to go and look at
            // is a request that never gets answered.
            Box {
                SecondaryButton(
                    text = stringResource(R.string.online_friends_entry),
                    onClick = onFriends,
                    icon = Icons.Filled.Group,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pendingFriendRequests > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 12.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = pendingFriendRequests.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryButton(
                text = stringResource(R.string.league_title),
                onClick = onLeague,
                icon = Icons.Filled.EmojiEvents,
                modifier = Modifier.fillMaxWidth()
            )
            }
        }
    }
}
