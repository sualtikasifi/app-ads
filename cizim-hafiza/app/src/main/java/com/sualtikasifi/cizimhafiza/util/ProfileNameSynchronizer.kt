package com.sualtikasifi.cizimhafiza.util

import com.sualtikasifi.cizimhafiza.domain.repository.AuthRepository
import com.sualtikasifi.cizimhafiza.domain.repository.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gives a signed-in player the name their Google account already has,
 * whenever they have not chosen one themselves.
 *
 * The name shown to other players (lobby, league table, recorded rounds)
 * used to be set in exactly one place — the field on Oda Kur — so a player
 * who never opened online mode appeared to everyone as "Oyuncu", and a
 * player who signed into a different account kept the previous one's name.
 * Both are the same missing rule: a nickname belongs to an ACCOUNT, and an
 * account that has not been given one should fall back to the name Google
 * already knows.
 *
 * Only ever fills a blank, and only for an account that has never named
 * itself (see [SettingsRepository.hasChosenNickname]). Both halves matter.
 * Filling any blank at any moment meant a player CLEARING the field to type
 * a new name had the old one put straight back, before the first new
 * character arrived — the field appeared to refuse to be emptied. A name
 * the player has deleted is a decision, not an omission.
 *
 * Watches the nickname as well as the auth state on purpose: signing into
 * an existing account clears the nickname and then restores that account's
 * own (see BackupRepository.switchToAccount), which lands well after the
 * auth state has already settled. Reacting to auth alone would look at the
 * outgoing player's name and conclude there was nothing to do.
 */
@Singleton
class ProfileNameSynchronizer @Inject constructor(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    /** Safe to call repeatedly; only the first call subscribes. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(authRepository.authState, settingsRepository.nickname) { authState, nickname ->
                authState to nickname
            }.collect { (authState, nickname) ->
                if (nickname.isNotBlank() || settingsRepository.hasChosenNickname) return@collect
                val displayName = (authState as? AuthState.Linked)?.displayName?.trim()
                if (!displayName.isNullOrBlank()) settingsRepository.setNickname(displayName)
            }
        }
    }
}
