package com.sualtikasifi.cizimhafiza.presentation.mainmenu

import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.LevelProgressState
import com.sualtikasifi.cizimhafiza.domain.model.PenSkin
import com.sualtikasifi.cizimhafiza.util.DailyChallengeRepository
import com.sualtikasifi.cizimhafiza.util.DailyChallengeState
import com.sualtikasifi.cizimhafiza.util.SettingsRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Activity
import com.sualtikasifi.cizimhafiza.ads.AdManager
import com.sualtikasifi.cizimhafiza.ads.RewardedOutcome
import com.sualtikasifi.cizimhafiza.data.local.dao.AchievementDao
import com.sualtikasifi.cizimhafiza.domain.model.Penalty
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import com.sualtikasifi.cizimhafiza.domain.repository.PenaltyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One avatar frame paired with whether the current level has unlocked it and whether it's the active pick. */
data class AvatarFrameUiItem(val frame: AvatarFrame, val unlocked: Boolean, val selected: Boolean)

data class PenSkinUiItem(val skin: PenSkin, val unlocked: Boolean, val selected: Boolean)

/** What a finished rewarded streak action should confirm on screen. */
enum class StreakToast { Rescued }

@HiltViewModel
class MainMenuViewModel @Inject constructor(
    achievementDao: AchievementDao,
    friendRepository: FriendRepository,
    private val dailyChallengeRepository: DailyChallengeRepository,
    private val settingsRepository: SettingsRepository,
    private val penaltyRepository: PenaltyRepository,
    private val adManager: AdManager
) : ViewModel() {

    private val _penaltyWarning = MutableStateFlow<Penalty?>(null)

    /**
     * The rejected round the player is about to be told about, or null.
     *
     * Applied from the main menu because that is where every session starts:
     * somebody whose round was rejected overnight finds out the moment they
     * open the game, rather than whenever they next happen to visit some
     * particular screen.
     */
    val penaltyWarning: StateFlow<Penalty?> = _penaltyWarning.asStateFlow()

    init {
        viewModelScope.launch {
            // Silent and empty on every ordinary launch, and its failures are
            // swallowed inside the repository: a network hiccup must never
            // stand between a player and their main menu.
            _penaltyWarning.value = penaltyRepository.applyOutstanding().firstOrNull()
        }
    }

    fun dismissPenaltyWarning() { _penaltyWarning.value = null }

    val dailyState: StateFlow<DailyChallengeState> = dailyChallengeRepository.state

    /**
     * A one-shot confirmation for the two rewarded streak actions below, so
     * the menu can say the ad actually paid out. Null once shown.
     */
    private val _streakToast = MutableStateFlow<StreakToast?>(null)
    val streakToast: StateFlow<StreakToast?> = _streakToast.asStateFlow()

    fun consumeStreakToast() { _streakToast.value = null }

    /** Dismisses the rescue offer for this app session without spending it. */
    fun dismissRescuePrompt() = dailyChallengeRepository.dismissRescuePrompt()

    /**
     * Keeps a lapsed streak alive in exchange for a watched ad.
     *
     * The banked "streak freeze" this replaced was spent silently and shown
     * nowhere, so players only ever learned it existed by noticing a streak
     * that should have broken hadn't. A single deliberate offer, made at the
     * moment of the break, is both clearer and honest about its price.
     */
    fun rescueStreak(activity: Activity) {
        if (dailyChallengeRepository.state.value.rescuableStreak <= 0) return
        adManager.maybeShowRewarded(activity) { outcome ->
            val earned = outcome == RewardedOutcome.EARNED
            // The rescue dialog stays open on an unavailable ad, so the
            // player can simply tap again — no separate notice needed.
            if (earned && dailyChallengeRepository.rescueStreak()) {
                _streakToast.value = StreakToast.Rescued
            }
        }
    }

    /** The player's own badge, shown on the menu so the level is always in sight. */
    val levelProgress: StateFlow<LevelProgressState> = settingsRepository.lifetimeXp
        .map { LevelProgressState.forXp(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LevelProgressState.forXp(0)
        )

    /** The player's own chosen ring (see AvatarFrame.resolve) for the menu's badge. */
    val selectedFrame: StateFlow<AvatarFrame> = combine(
        settingsRepository.selectedAvatarFrameId,
        settingsRepository.lifetimeXp
    ) { selectedId, xp -> AvatarFrame.resolve(selectedId, LevelProgressState.forXp(xp).level) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AvatarFrame.DEFAULT
        )

    /** The full catalog for the frame picker sheet, each paired with whether it's unlocked/currently worn. */
    val avatarFrameItems: StateFlow<List<AvatarFrameUiItem>> = combine(
        settingsRepository.selectedAvatarFrameId,
        levelProgress
    ) { selectedId, progress ->
        val resolved = AvatarFrame.resolve(selectedId, progress.level)
        AvatarFrame.entries.map { AvatarFrameUiItem(it, unlocked = progress.level >= it.unlockLevel, selected = it == resolved) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AvatarFrame.entries.map { AvatarFrameUiItem(it, unlocked = it == AvatarFrame.DEFAULT, selected = it == AvatarFrame.DEFAULT) }
    )

    /** Only ever called for a frame [AvatarFrameUiItem.unlocked] — see MainMenuScreen's picker sheet. */
    fun selectAvatarFrame(frame: AvatarFrame) = settingsRepository.setSelectedAvatarFrame(frame)

    /** The pen catalog, same shape as [avatarFrameItems] — see domain.model.PenSkin. */
    val penSkinItems: StateFlow<List<PenSkinUiItem>> = combine(
        settingsRepository.selectedPenSkinId,
        levelProgress
    ) { selectedId, progress ->
        val resolved = PenSkin.resolve(selectedId, progress.level)
        PenSkin.entries.map { PenSkinUiItem(it, unlocked = progress.level >= it.unlockLevel, selected = it == resolved) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PenSkin.entries.map { PenSkinUiItem(it, unlocked = it == PenSkin.DEFAULT, selected = it == PenSkin.DEFAULT) }
    )

    /** Only ever called for a pen [PenSkinUiItem.unlocked]. */
    fun selectPenSkin(skin: PenSkin) = settingsRepository.setSelectedPenSkin(skin)

    /**
     * Re-reads the daily state whenever the menu comes back into view — the
     * app can sit in the background across midnight, at which point a
     * "already done today" card is stale and today's challenge is waiting.
     */
    fun refreshDaily() = dailyChallengeRepository.refresh()

    // Drives the small badge on the "İstatistikler" tile — cleared the next
    // time StatisticsScreen opens (see StatisticsViewModel.markAllSeen).
    val hasUnseenAchievement: StateFlow<Boolean> = achievementDao.observeUnseenCount()
        .map { it > 0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    /**
     * How many friend requests are waiting, for the badge on the Arkadaşlar
     * tile.
     *
     * This count used to live on the online lobby's own button — one screen
     * deeper than the menu. A request only ever appears inside Arkadaşlarım,
     * so a badge you have to already be halfway there to see cannot do the
     * one job it exists for: telling somebody a request arrived. On the menu
     * it is in front of them every time they open the app.
     *
     * An empty collection costs no document reads to watch, and a failure
     * shows no badge rather than an error — a home screen should not grow
     * one over a number this small.
     */
    val pendingFriendRequests: StateFlow<Int> = friendRepository.observeFriendRequests()
        .map { it.size }
        .catch { emit(0) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )
}
