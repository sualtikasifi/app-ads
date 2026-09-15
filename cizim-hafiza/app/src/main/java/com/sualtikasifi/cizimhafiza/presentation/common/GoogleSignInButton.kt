package com.sualtikasifi.cizimhafiza.presentation.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sualtikasifi.cizimhafiza.R

/**
 * The Google-branded sign-in entry point — shared by [AccountScreen] and
 * [SignInPromptDialog] rather than duplicated per screen, since Google's
 * Sign-In branding guidelines fix its exact colours below; two copies would
 * only ever be a chance for them to quietly drift out of sync with each
 * other.
 *
 * Not a [PrimaryButton] with a label: Google's guidelines require their own
 * mark on the button that starts their flow, and a bare coloured pill
 * saying "Google ile Giriş Yap" meets neither the guideline nor a player's
 * expectation of what a Google sign-in looks like. Built on the app's own
 * [raisedSurface] so it still belongs to this app — the guidelines
 * constrain the logo and the wording, not the shape around them.
 */
@Composable
fun GoogleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .raisedSurface(
                face = GoogleButtonFace,
                edge = GoogleButtonEdge,
                corner = 29.dp,
                pressed = pressed,
                border = GoogleButtonBorder
            )
            .height(58.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = stringResource(R.string.account_sign_in_google),
                style = MaterialTheme.typography.titleMedium,
                color = GoogleButtonText,
                maxLines = 1
            )
        }
    }
}

// Fixed rather than theme-derived: these are Google's own button colours,
// and the whole point of the branding is that it looks the same in every
// app a player meets it in.
private val GoogleButtonFace = Color(0xFFFFFFFF)
private val GoogleButtonEdge = Color(0xFFDADCE0)
private val GoogleButtonBorder = Color(0xFF747775)
private val GoogleButtonText = Color(0xFF1F1F1F)
