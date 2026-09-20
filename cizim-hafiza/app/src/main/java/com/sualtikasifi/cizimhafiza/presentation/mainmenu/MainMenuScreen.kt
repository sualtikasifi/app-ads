package com.sualtikasifi.cizimhafiza.presentation.mainmenu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import com.sualtikasifi.cizimhafiza.util.DailyChallengeState
import kotlinx.coroutines.delay
import com.sualtikasifi.cizimhafiza.R
import com.sualtikasifi.cizimhafiza.presentation.common.IconWell
import com.sualtikasifi.cizimhafiza.presentation.common.SecondaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.PrimaryButton
import com.sualtikasifi.cizimhafiza.presentation.common.RaisedCard
import com.sualtikasifi.cizimhafiza.presentation.common.TintedBadge
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.Chest
import com.sualtikasifi.cizimhafiza.domain.model.ChestSlots
import com.sualtikasifi.cizimhafiza.domain.model.Moderation
import com.sualtikasifi.cizimhafiza.domain.model.Penalty
import com.sualtikasifi.cizimhafiza.domain.model.DailyChallenge
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.domain.model.XpAwards
import com.sualtikasifi.cizimhafiza.domain.model.LevelTier
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import com.sualtikasifi.cizimhafiza.presentation.chests.ChestsViewModel
import com.sualtikasifi.cizimhafiza.presentation.common.LevelAvatar
import com.sualtikasifi.cizimhafiza.presentation.common.artRes
import com.sualtikasifi.cizimhafiza.presentation.common.labelRes
import com.sualtikasifi.cizimhafiza.presentation.common.screenBackground
import com.sualtikasifi.cizimhafiza.presentation.theme.AppTheme
import com.sualtikasifi.cizimhafiza.util.GameConstants

/** The one gap used between every major section of the menu, so the page reads as evenly spaced top to bottom. */
private val SECTION_GAP = 9.dp

/** The daily challenge card once today's is done — see [DailyChallengeCard]. */
private val DailyDoneGreen = Color(0xFFD9EFDC)

@Composable
fun MainMenuScreen(
    onPlay: () -> Unit,
    onQuickMatch: () -> Unit,
    onPlayOnline: () -> Unit,
    onLevels: () -> Unit,
    onAchievements: () -> Unit,
    onFriends: () -> Unit,
    onSettings: () -> Unit,
    onDailyChallenge: () -> Unit,
    onChests: () -> Unit,
    viewModel: MainMenuViewModel = hiltViewModel()
) {
    val hasUnseenAchievement by viewModel.hasUnseenAchievement.collectAsState()
    val pendingFriendRequests by viewModel.pendingFriendRequests.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    val dailyState by viewModel.dailyState.collectAsState()
    val penaltyWarning by viewModel.penaltyWarning.collectAsState()
    val levelProgress by viewModel.levelProgress.collectAsState()
    val selectedFrame by viewModel.selectedFrame.collectAsState()
    val avatarFrameItems by viewModel.avatarFrameItems.collectAsState()
    var framePickerOpen by remember { mutableStateOf(false) }
    var rankLadderOpen by remember { mutableStateOf(false) }
    val streakToast by viewModel.streakToast.collectAsState()
    val referralRewardXp by viewModel.referralRewardXp.collectAsState()
    // The system back gesture on the menu used to close the app outright,
    // with no way to take it back — easy to trigger by accident mid-swipe
    // and, on a game, more destructive than it looks.
    var exitPromptOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = !exitPromptOpen) { exitPromptOpen = true }
    val context = LocalContext.current
    val activity = context as? Activity

    // The app can sit in the background across midnight; without this the
    // menu would still be showing "done for today" on a day whose challenge
    // is actually waiting to be played.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshDaily()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A fixed screen, not a scrolling one: the menu is the app's home base,
    // opened dozens of times a session, and every scroll gesture on it is a
    // small tax on getting to "Oyna". Fitting the daily-challenge card and
    // level badge in without scrolling meant trimming sizes throughout
    // rather than letting any one element claim its old, roomier size.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .screenBackground()
                .padding(padding)
                .padding(horizontal = 22.dp)
                .padding(top = 20.dp, bottom = 12.dp)
        ) {
            // Centred as one block when it fits, scrollable when it does
            // not. It used to be neither: a weight(1f) column simply clipped
            // whatever ran past the bottom, so on a shorter phone — or once
            // the level card grew a rank pill and the daily card grew a
            // streak flame — the last row of tiles was cut in half with no
            // way to reach it. heightIn(min = maxHeight) keeps the centred
            // look on a roomy screen and lets the same content scroll on a
            // cramped one, rather than trading one for the other.
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo medallion + mascot: the mark on a tinted disc, ringed
                // in white so its edge stays crisp against the textured
                // collage background, with the pencil mascot alongside for
                // personality — the same character the user's own redesign
                // put front and center on the home screen.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(R.drawable.karalak_logo_mark),
                            contentDescription = null,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.mascot_pencil_wink),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(SECTION_GAP))

                // A proper card — the same colored-container treatment as
                // DailyChallengeCard below it — rather than the badge just
                // floating loose on the page: this is the player's own
                // chosen ring (see StatisticsScreen's picker), the thing all
                // the frame-unlock and sparkle work is actually for, and it
                // deserves to look like a deliberate piece of the menu, not
                // a sticker.
                ProfileHeader(
                    nickname = nickname,
                    progress = levelProgress,
                    frame = selectedFrame,
                    onFrameClick = { framePickerOpen = true },
                    onLevelClick = { rankLadderOpen = true },
                    onSettingsClick = onSettings
                )

                Spacer(modifier = Modifier.height(SECTION_GAP))

                DailyChallengeCard(state = dailyState, onPlay = onDailyChallenge)

                Spacer(modifier = Modifier.height(SECTION_GAP))

                // Three equal cards rather than one headline button plus a
                // pair of smaller tiles: all three ways to start a match are
                // real choices a player makes every session, not one
                // "the" mode with two lesser alternatives underneath it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ModeCard(
                        imageRes = R.drawable.icon_mode_quickmatch,
                        label = stringResource(R.string.quick_match_title),
                        subtitle = stringResource(R.string.mode_card_quickmatch_subtitle),
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        onClick = onQuickMatch,
                        modifier = Modifier.weight(1f)
                    )
                    ModeCard(
                        imageRes = R.drawable.icon_mode_playfriend,
                        label = stringResource(R.string.menu_play_online),
                        subtitle = stringResource(R.string.mode_card_playfriend_subtitle),
                        container = MaterialTheme.colorScheme.secondary,
                        content = MaterialTheme.colorScheme.onSecondary,
                        onClick = onPlayOnline,
                        modifier = Modifier.weight(1f)
                    )
                    ModeCard(
                        imageRes = R.drawable.icon_mode_offline,
                        label = stringResource(R.string.menu_play),
                        subtitle = stringResource(R.string.mode_card_offline_subtitle),
                        container = AppTheme.tokens.success,
                        content = Color.White,
                        onClick = onPlay,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(SECTION_GAP))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuTile(
                        icon = Icons.Filled.Map,
                        label = stringResource(R.string.menu_levels),
                        subtitle = stringResource(R.string.menu_levels_subtitle),
                        tint = AppTheme.tokens.success,
                        container = Color(0xFFD9EFDC),
                        onClick = onLevels,
                        modifier = Modifier.weight(1f)
                    )
                    MenuTile(
                        imageRes = R.drawable.icon_achievements,
                        label = stringResource(R.string.menu_achievements),
                        subtitle = stringResource(R.string.menu_achievements_subtitle),
                        container = Color(0xFFF8EBD0),
                        onClick = onAchievements,
                        showBadge = hasUnseenAchievement,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(SECTION_GAP))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MenuTile(
                        icon = Icons.Filled.Group,
                        label = stringResource(R.string.menu_friends),
                        subtitle = stringResource(R.string.menu_friends_subtitle),
                        tint = Color(0xFF7B68C4),
                        container = Color(0xFFE7E3F7),
                        onClick = onFriends,
                        // The count, not just a dot: "3 people are waiting"
                        // is a different message from "something changed",
                        // and this is the one badge on the menu that asks
                        // the player to go and do something for somebody.
                        badgeCount = pendingFriendRequests,
                        modifier = Modifier.weight(1f)
                    )
                    MenuTile(
                        icon = Icons.Filled.Settings,
                        label = stringResource(R.string.menu_settings),
                        subtitle = stringResource(R.string.menu_settings_subtitle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onSettings,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(SECTION_GAP))

                // The chest economy's own showcase, not a small tile pointing
                // at it — this is the new system the redesign centers on, so
                // it gets the same real estate the user's own mockup gave it:
                // live art, a live countdown per slot, right on the menu.
                ChestsShowcaseSection(onChests = onChests)
            }
            }
        }

        if (exitPromptOpen) {
            AlertDialog(
                onDismissRequest = { exitPromptOpen = false },
                title = { Text(stringResource(R.string.exit_confirm_title)) },
                text = { Text(stringResource(R.string.exit_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = { exitPromptOpen = false; activity?.finish() }) {
                        Text(stringResource(R.string.exit_confirm_yes))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { exitPromptOpen = false }) {
                        Text(stringResource(R.string.exit_confirm_no))
                    }
                }
            )
        }

        // The streak the player just lost, offered back for an ad. Shown the
        // moment the menu opens, because that is exactly when they find out
        // it broke — see DailyChallengeRepository.repairStreak.
        // Gated on ads: the rescue IS watching an ad, so with ads off the
        // streak simply breaks rather than opening a dialog whose only
        // button cannot work.
        if (dailyState.rescuableStreak > 0 && GameConstants.ADMOB_ENABLED) {
            StreakRescueDialog(
                lostStreak = dailyState.rescuableStreak,
                onRescue = { activity?.let(viewModel::rescueStreak) },
                onDismiss = viewModel::dismissRescuePrompt
            )
        }

        streakToast?.let { toast ->
            LaunchedEffect(toast) {
                delay(2_500)
                viewModel.consumeStreakToast()
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                TintedBadge(
                    text = stringResource(
                        when (toast) {
                            StreakToast.Rescued -> R.string.streak_rescue_done
                        }
                    ),
                    container = MaterialTheme.colorScheme.surface,
                    content = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (referralRewardXp > 0) {
            LaunchedEffect(referralRewardXp) {
                delay(3_500)
                viewModel.consumeReferralRewardNotice()
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                TintedBadge(
                    text = stringResource(R.string.referral_reward_earned_format, referralRewardXp),
                    container = MaterialTheme.colorScheme.surface,
                    content = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (rankLadderOpen) {
            RankLadderSheet(progress = levelProgress, onDismiss = { rankLadderOpen = false })
        }

        if (framePickerOpen) {
            AvatarFramePickerSheet(
                items = avatarFrameItems,
                onSelect = { viewModel.selectAvatarFrame(it); framePickerOpen = false },
                onDismiss = { framePickerOpen = false }
            )
        }

    }
    penaltyWarning?.let { penalty ->
        PenaltyDialog(penalty = penalty, onDismiss = viewModel::dismissPenaltyWarning)
    }
}

/**
 * What the player is told when a round of theirs was rejected in review.
 *
 * Says the number out loud rather than letting the XP quietly differ from
 * what they remember: a penalty nobody notices deters nobody, and a level
 * that dropped without explanation reads as a bug.
 */
@Composable
private fun PenaltyDialog(penalty: Penalty, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        RaisedCard(corner = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconWell(icon = Icons.Filled.Gavel)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.penalty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.penalty_body, penalty.xpRevoked),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                // What happens NEXT is the part that changes behaviour. A
                // penalty that only reports what was taken reads as a fine;
                // saying how close the lockout is turns it into a warning,
                // which is the point.
                Text(
                    text = if (penalty.lockedUntilMillis > 0L) {
                        stringResource(R.string.penalty_locked, penalty.strike)
                    } else {
                        stringResource(
                            R.string.penalty_strikes,
                            penalty.strike,
                            // Distance to the NEXT multiple, not to three: the
                            // count is lifetime and every third offence costs
                            // a day, so offence 4 is two away from a lockout,
                            // not "already past it".
                            Moderation.STRIKES_BEFORE_LOCKOUT -
                                penalty.strike % Moderation.STRIKES_BEFORE_LOCKOUT
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                PrimaryButton(
                    text = stringResource(R.string.penalty_understood),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Either a tintable glyph ([icon], on a colored [IconWell]) or a full-color
 * illustration ([imageRes]) — never both. The illustrated set (achievements,
 * the mode cards) already carries its own color and container shape, so
 * putting it inside another tinted circle would double up on both.
 */
/**
 * A list-style row rather than a centered icon-over-label tile: a small
 * colored icon well on the left, title + one-line subtitle stacked next to
 * it, a chevron on the right — matching the user's own redesign, and reading
 * as "tap to go somewhere" more clearly than a square button did.
 */
@Composable
private fun MenuTile(
    label: String,
    subtitle: String,
    container: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    imageRes: Int? = null,
    modifier: Modifier = Modifier,
    showBadge: Boolean = false,
    badgeCount: Int = 0
) {
    Box(modifier = modifier) {
        RaisedCard(
            onClick = onClick,
            corner = 20.dp,
            face = AppTheme.tokens.cardWarm,
            edge = AppTheme.tokens.edge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (imageRes != null) {
                    Image(
                        painter = painterResource(imageRes),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                    )
                } else if (icon != null) {
                    IconWell(icon = icon, tint = tint, container = container, size = 34.dp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // A new, not-yet-viewed achievement (see MainMenuViewModel) —
        // cleared the next time StatisticsScreen opens.
        if (showBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(12.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
            )
        }
        // A number instead, where there is one worth reading — see the
        // Arkadaşlar tile.
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onError
                )
            }
        }
    }
}

/**
 * One of the three equal ways to start a match — bigger than [MenuTile] and
 * carrying a subtitle, since these are the headline choice the old single
 * "Hızlı Eşleş" button used to claim alone.
 */
@Composable
private fun ModeCard(
    imageRes: Int,
    label: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RaisedCard(
        onClick = onClick,
        corner = 22.dp,
        face = container,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = content,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

/**
 * The chest economy's own section on the menu — not a tile pointing at it.
 * Reads its own [ChestsViewModel] (a separate instance from the one
 * ChestsScreen creates when the player actually navigates there; both read
 * the same SettingsRepository-backed state, so they never disagree) so the
 * countdown on each slot ticks live without any plumbing through
 * MainMenuViewModel.
 */
@Composable
private fun ChestsShowcaseSection(onChests: () -> Unit, viewModel: ChestsViewModel = hiltViewModel()) {
    val slots by viewModel.chestSlots.collectAsState()
    val now by viewModel.nowMillis.collectAsState()
    RaisedCard(
        onClick = onChests,
        corner = 22.dp,
        face = Color(0xFFF3E1BC),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = null,
                    tint = AppTheme.tokens.gold,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.menu_chests),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (0 until ChestSlots.SLOT_COUNT).forEach { index ->
                    ChestPreviewCard(chest = slots.getOrNull(index), nowMillis = now, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChestPreviewCard(chest: Chest?, nowMillis: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (chest != null) {
            Image(
                painter = painterResource(chest.tier.artRes()),
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    chest.isReady(nowMillis) -> stringResource(R.string.chests_open_button)
                    chest.unlockStartedAtMillis != null -> {
                        val remaining = chest.unlockStartedAtMillis + chest.tier.unlockDurationMillis - nowMillis
                        val totalMinutes = (remaining / 60_000).coerceAtLeast(0)
                        val hours = totalMinutes / 60
                        val minutes = totalMinutes % 60
                        if (hours > 0) {
                            stringResource(R.string.chests_remaining_hours_minutes, hours, minutes)
                        } else {
                            stringResource(R.string.chests_remaining_minutes, minutes)
                        }
                    }
                    else -> stringResource(chest.tier.labelRes())
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        } else {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.chests_slot_empty),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * The home screen's profile strip — one compact row, nothing else.
 *
 * Deliberately minimal per spec: avatar (tap to change the frame) on the
 * left, nickname + level + a slim progress sliver in the middle (tap to see
 * the rank ladder), a settings icon on the right. No rank pill, no pen chip,
 * no league chip, no gold — those either moved elsewhere (league stays
 * reachable from the online lobby) or were dropped from the header outright.
 */
@Composable
private fun ProfileHeader(
    nickname: String,
    progress: LevelProgressState,
    frame: AvatarFrame,
    onFrameClick: () -> Unit,
    onLevelClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    RaisedCard(corner = 22.dp, face = MaterialTheme.colorScheme.primaryContainer, raise = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                LevelAvatar(
                    level = progress.level,
                    frame = frame,
                    size = 52.dp,
                    modifier = Modifier.clickable(onClick = onFrameClick)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                        .clickable(onClick = onFrameClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.avatar_frame_change_cd),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onLevelClick)
            ) {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.avatar_frame_locked_level, progress.level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Recomposed fresh every time the main menu is navigated back
                // to (the destination is disposed while a game is on
                // screen), so this fill-from-zero plays every return trip.
                val animatedFraction = remember { Animatable(0f) }
                LaunchedEffect(progress.progressFraction) {
                    animatedFraction.animateTo(
                        targetValue = progress.progressFraction,
                        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
                    )
                }
                LinearProgressIndicator(
                    progress = { animatedFraction.value },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.menu_settings),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * A grid of every [AvatarFrame] the player has unlocked so far (plus locked
 * ones ahead, dimmed with the level that opens them), tapping an unlocked
 * one picks it — see MainMenuViewModel.selectAvatarFrame. No level-number
 * face is drawn on the swatches (unlike [LevelAvatar]): this is about
 * choosing the ring, not restating the player's level eleven times over.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AvatarFramePickerSheet(
    items: List<AvatarFrameUiItem>,
    onSelect: (AvatarFrame) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.avatar_frame_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.height(420.dp)
            ) {
                gridItems(items, key = { it.frame.name }) { item ->
                    AvatarFrameSwatch(item = item, onClick = { if (item.unlocked) onSelect(item.frame) })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AvatarFrameSwatch(item: AvatarFrameUiItem, onClick: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (item.selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.unlocked, onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(96.dp).padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(item.frame.drawableRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(if (item.unlocked) 1f else 0.35f)
            )
            if (!item.unlocked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (item.frame.isLeagueReward) {
                            stringResource(R.string.cosmetic_locked_league)
                        } else {
                            stringResource(R.string.avatar_frame_locked_level, item.frame.unlockLevel)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * The menu's first call to action: today's challenge, or — once it's done —
 * the streak it just extended.
 *
 * Deliberately shows the streak in both states. Before playing it's what's
 * at stake; after playing it's the reward, and seeing it tick up is most of
 * the reason to come back tomorrow.
 */
@Composable
private fun DailyChallengeCard(state: DailyChallengeState, onPlay: () -> Unit) {
    val available = state.isAvailableToday
    val todayResult = state.todayResult

    // Breathes while there is still something to do, and stops the moment
    // there isn't. A card that pulses forever is wallpaper; one that pulses
    // only when it is asking for something reads as the app tapping the
    // player on the shoulder — and the streak it protects is the thing most
    // worth coming back for.
    val restColor = MaterialTheme.colorScheme.primaryContainer
    val peakColor = lerp(restColor, MaterialTheme.colorScheme.primary, 0.22f)
    val transition = rememberInfiniteTransition(label = "dailyPulse")
    // Held as State and read inside a draw lambda rather than unwrapped with
    // `by` here. Read at this level it changes sixty times a second, and every
    // one of those changes recomposed the whole card — title, subtitle,
    // countdown, flame — for as long as the menu was on screen. That cost was
    // being paid straight through every navigation animation, which is what
    // made leaving the menu stutter.
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Slow on purpose — roughly a resting breath. Anything quicker
            // stops being an invitation and starts being an alarm.
            animation = tween(1_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dailyPulseFraction"
    )
    val pulseShape = RoundedCornerShape(22.dp)

    RaisedCard(
        corner = 22.dp,
        // Done is a light green, not the neutral surface it used to be: the
        // player has finished the one daily thing, and the card should look
        // finished rather than merely inactive.
        face = if (available) restColor else DailyDoneGreen,
        raise = 7.dp,
        onClick = if (available) onPlay else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        // The breath itself: the peak colour laid over the resting face at an
        // animated alpha. First child, so it draws under the row rather than
        // over it, and sized to the card without taking part in measuring it.
        if (available) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = pulse.value }
                    .background(peakColor, pulseShape)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (available) "🎯" else "✅",
                style = MaterialTheme.typography.headlineSmall
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.daily_challenge_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (available) {
                        stringResource(R.string.daily_challenge_ready, DailyChallenge.WORD_COUNT)
                    } else {
                        stringResource(
                            R.string.daily_challenge_done,
                            todayResult?.correctCount ?: 0,
                            DailyChallenge.WORD_COUNT
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!available) {
                    Text(
                        text = stringResource(R.string.daily_challenge_resets_in, midnightCountdownText()),
                        style = MaterialTheme.typography.labelSmall,
                        // No extra alpha on top of the muted color: this was
                        // the faintest text on the home screen.
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.currentStreak > 0) {
                StreakFlame(multiplier = XpAwards.dailyStreakMultiplier(state.currentStreak))
            }
        }
    }
}

/**
 * The streak, and what it is worth, in one mark.
 *
 * The multiplier used to be a separate gold pill plus a "play tomorrow for
 * Nx" line beside the card's title — two extra pieces of text restating a
 * number that was already on screen right next to them. It belongs on the
 * flame: the streak count and the multiplier are the same number until the
 * cap, so showing them apart made the card look busier than it is.
 *
 * The flame breathes rather than sitting still. It is the one thing on the
 * home screen that represents something at risk of being lost, and a static
 * emoji reads as a label; a moving one reads as alive.
 */
@Composable
private fun StreakFlame(multiplier: Int) {
    val transition = rememberInfiniteTransition(label = "streakFlame")
    // Same reason as the card's pulse: kept as State and read in draw lambdas,
    // so the flame animates without recomposing anything.
    val scale = transition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(880, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streakFlameScale"
    )
    val glow = transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(880, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "streakFlameGlow"
    )
    val gold = AppTheme.tokens.gold
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .drawBehind { drawCircle(color = gold, alpha = glow.value) }
            )
            Text(
                text = "🔥",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
            )
        }
        // Only the multiplier. Below the cap the streak count and the
        // multiplier are the same number, so printing both stacked them into
        // "3" over "3x XP" — the same fact twice, the top line adding
        // nothing. What the player is actually protecting is the multiplier.
        Text(
            text = stringResource(R.string.daily_challenge_multiplier_badge, multiplier),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * "HH:MM:SS" until local midnight, ticking every second. Local time, not
 * UTC: the daily challenge itself resets on [java.time.LocalDate]'s day
 * boundary (see DailyChallenge/DailyChallengeRepository), which is the
 * device's local calendar day — the countdown has to agree with the exact
 * moment the card it's showing will actually flip to "ready" again.
 */
@Composable
private fun midnightCountdownText(): String {
    var remaining by remember {
        mutableStateOf(java.time.Duration.between(java.time.LocalDateTime.now(), nextMidnight()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            remaining = java.time.Duration.between(java.time.LocalDateTime.now(), nextMidnight())
        }
    }
    val total = remaining.seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun nextMidnight(): java.time.LocalDateTime =
    java.time.LocalDate.now().plusDays(1).atStartOfDay()

/**
 * The one deliberate way back from a broken streak.
 *
 * Deliberately a modal: someone who has just lost a 60-day streak will not
 * go looking for a button, and the offer expires within a couple of days
 * (see DailyChallengeRepository.MAX_RESCUE_GAP_DAYS).
 *
 * The action is a full button carrying a play icon and the word "ad",
 * because it opens a rewarded video. AdMob's policies require the reward
 * and the fact that an ad is coming to be stated before the tap, and a bare
 * line of tappable text stated neither — it did not even look like a
 * control.
 */
@Composable
private fun StreakRescueDialog(lostStreak: Int, onRescue: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        RaisedCard(corner = 28.dp, raise = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🔥", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.streak_rescue_title, lostStreak),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.streak_rescue_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                PrimaryButton(
                    text = stringResource(R.string.streak_rescue_action),
                    onClick = onRescue,
                    icon = Icons.Filled.PlayCircle,
                    height = 54.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                SecondaryButton(
                    text = stringResource(R.string.streak_rescue_dismiss),
                    onClick = onDismiss,
                    height = 46.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Every rank, the level it opens at, and where the player currently stands.
 *
 * The card only ever showed the next rank and the XP left to it, which told
 * a player what was immediately ahead but nothing about the shape of the
 * climb — how many ranks exist, how far apart they are, what the top one is
 * called. A ladder someone can look at is what turns a number into a goal.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RankLadderSheet(progress: LevelProgressState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.rank_ladder_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.rank_ladder_subtitle,
                    stringResource(progress.tier.rank.nameRes)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            LevelTier.entries.forEach { tier ->
                val reached = progress.level >= tier.minLevel
                val isCurrent = tier == progress.tier
                val xpAway = (PlayerLevel.totalXpForLevel(tier.minLevel) - progress.totalXp).coerceAtLeast(0)
                RaisedCard(
                    corner = 18.dp,
                    face = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            // A rank still ahead is dimmed rather than hidden:
                            // the point of the list is seeing what is coming.
                            .alpha(if (reached) 1f else 0.55f)
                    ) {
                        Text(text = tier.rank.emoji, style = MaterialTheme.typography.titleLarge)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(tier.rank.nameRes),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.rank_ladder_unlock_level, tier.minLevel),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = when {
                                isCurrent -> stringResource(R.string.rank_ladder_current)
                                reached -> stringResource(R.string.rank_ladder_reached)
                                else -> stringResource(R.string.rank_ladder_remaining, xpAway)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
