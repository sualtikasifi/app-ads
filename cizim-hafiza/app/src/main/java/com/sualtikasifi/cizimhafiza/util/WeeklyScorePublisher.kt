package com.sualtikasifi.cizimhafiza.util

import android.util.Log
import com.sualtikasifi.cizimhafiza.domain.model.AvatarFrame
import com.sualtikasifi.cizimhafiza.domain.model.PlayerLevel
import com.sualtikasifi.cizimhafiza.domain.model.WeeklyLeague
import com.sualtikasifi.cizimhafiza.domain.repository.FriendRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps this device's weekly-league standing published on its public profile.
 *
 * The standing used to be written in exactly one place: the moment the player
 * opened the Weekly League screen. That is backwards. Your row in a friend's
 * table comes from *your* profile document, so a friend who plays all week
 * and never opens the standings has nothing to read — everyone else sees them
 * sitting on zero, no matter how much XP they earned. Which is precisely the
 * bug this exists to fix, and it hit the most casual players hardest, because
 * they are the least likely to open a leaderboard.
 *
 * Publishing follows the XP itself instead. [SettingsRepository.weeklyXp] is
 * a StateFlow, so this also fires once at app start with whatever the current
 * total is, which is what makes it self-healing: a write lost to a dead
 * connection is simply retried the next time the app opens.
 *
 * The two safeguards on cost:
 *  - [PUBLISH_DEBOUNCE_MS] collapses a whole match's worth of per-word XP into
 *    one write, landing a few seconds after the player stops earning.
 *  - a stored signature skips a write that would say exactly what the profile
 *    already says, so relaunching the app repeatedly costs nothing.
 */
@Singleton
class WeeklyScorePublisher @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val friendRepository: FriendRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    /** Safe to call repeatedly; only the first call subscribes. */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            settingsRepository.weeklyXp
                .debounce(PUBLISH_DEBOUNCE_MS)
                .map { xp -> snapshotFor(xp) }
                .distinctUntilChanged()
                .collect { snapshot -> publish(snapshot) }
        }
    }

    /**
     * Called when the standings are actually being looked at, so a table
     * opened seconds after a match does not show a stale row for yourself
     * while the debounce above is still counting down.
     */
    fun publishNow() {
        scope.launch { publish(snapshotFor(settingsRepository.weeklyXp.value)) }
    }

    private fun snapshotFor(weeklyXp: Int): Snapshot {
        val level = PlayerLevel.levelForXp(settingsRepository.lifetimeXp.value)
        return Snapshot(
            nickname = settingsRepository.nickname.value.trim().ifBlank { "Oyuncu" },
            weeklyXp = weeklyXp,
            weekId = WeeklyLeague.weekIdFor(LocalDate.now().toEpochDay()),
            level = level,
            frameId = AvatarFrame.resolve(settingsRepository.selectedAvatarFrameId.value, level).name
        )
    }

    private suspend fun publish(snapshot: Snapshot) {
        if (settingsRepository.publishedWeeklyScoreSignature == snapshot.signature) return
        runCatching {
            friendRepository.publishWeeklyScore(
                nickname = snapshot.nickname,
                weeklyXp = snapshot.weeklyXp,
                weekId = snapshot.weekId,
                level = snapshot.level,
                frameId = snapshot.frameId
            )
        }.onSuccess {
            // Only remembered once the write actually landed — a signature
            // stored on a failed publish would suppress the retry that is
            // the whole point of re-publishing at app start.
            settingsRepository.publishedWeeklyScoreSignature = snapshot.signature
        }.onFailure { Log.w(TAG, "Weekly score not published", it) }
    }

    private data class Snapshot(
        val nickname: String,
        val weeklyXp: Int,
        val weekId: Long,
        val level: Int,
        val frameId: String
    ) {
        val signature: String get() = "$weekId|$weeklyXp|$level|$frameId|$nickname"
    }

    private companion object {
        const val TAG = "WeeklyScorePublisher"

        /** Long enough to sit out a match's per-word XP, short enough to land before the app is closed. */
        const val PUBLISH_DEBOUNCE_MS = 8_000L
    }
}
