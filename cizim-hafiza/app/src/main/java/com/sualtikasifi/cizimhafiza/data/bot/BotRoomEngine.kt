package com.sualtikasifi.cizimhafiza.data.bot

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.sualtikasifi.cizimhafiza.domain.model.DrawingStroke
import com.sualtikasifi.cizimhafiza.domain.model.ResultItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Drives every "bot" action in the permanent shared room 130246 (see the
 * "Bot Eğitim" main-menu entry). The bot has no device of its own — Cloud
 * Functions would be the natural place to simulate it, but that requires
 * Firebase's paid Blaze plan, which isn't an option here. Instead, whichever
 * real player's app happens to be around does this work locally, making the
 * exact same Firestore writes a server would have made — this is safe
 * because firestore.rules deliberately grants room 130246 (and only that
 * room) open read/write to any signed-in user, see the comment there.
 *
 * [ensureBootstrapped] and [ensureRunning] are cheap to call from every
 * screen that might touch this room — internally this only ever starts its
 * one long-lived listener once per app process (further calls are no-ops).
 * The tradeoff of having no always-on server: if every real player closes
 * the app mid-match, nothing finishes the bot's turn until someone opens
 * the app again — but the next person who tries to join runs
 * [ensureBootstrapped] first, which repairs a stuck room before their own
 * join, so the room never stays broken for long.
 */
@Singleton
class BotRoomEngine @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    companion object {
        const val ROOM_CODE = "130246"
        private const val BOT_UID = "karalak-bot"
        private const val BOT_DISPLAY_NAME = "Ayşe"
        private const val WORD_COUNT_TARGET = 10
        private const val WORD_COUNT_MIN = 3
        private const val POINTS_CORRECT = 5L
        private const val SPEED_BONUS_POINTS = 2L
        private val PRESET_REACTIONS = listOf(
            "😂" to "funny", "👏" to "nice", "😅" to "hard", "🔥" to "fire", "😱" to "shock", "👋" to "hi"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val listenerStarted = AtomicBoolean(false)
    private val roomRef: DocumentReference get() = firestore.collection("rooms").document(ROOM_CODE)

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    /** Call before the first joinRoom() attempt on this code — creates the room if missing, repairs it if stuck. */
    suspend fun ensureBootstrapped() {
        ensureSignedIn()
        runMaintenance()
    }

    /** Starts the long-lived listener that drives bot behavior, if this process hasn't already started one. */
    fun ensureRunning() {
        if (!listenerStarted.compareAndSet(false, true)) return
        scope.launch {
            ensureSignedIn()
            observeAndDrive()
        }
    }

    private suspend fun observeAndDrive() {
        callbackFlow {
            val registration = roomRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot)
            }
            awaitClose { registration.remove() }
        }.catch { }.collect { snapshot ->
            if (snapshot == null || !snapshot.exists()) return@collect
            runCatching { handleRoomChange(snapshot) }
        }
    }

    private suspend fun handleRoomChange(snapshot: DocumentSnapshot) {
        val status = snapshot.getString("status") ?: return
        @Suppress("UNCHECKED_CAST")
        val players = snapshot.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()

        when (status) {
            "WAITING" -> handleWaiting(players, snapshot.getBoolean("botGreeted") == true)
            "PLAYING" -> handlePlaying(snapshot, players)
            "FINISHED" -> handleFinished(snapshot)
        }
    }

    // --- Waiting room: greets a newly-joined real player with a wave, then
    // becomes "ready" itself after a random 2-8s delay (not instantly, like
    // a real person needing a moment), then waits for every real player to
    // actually tap "Hazır Ol" before starting — matching a real friend's
    // room instead of yanking everyone straight into the match the instant
    // they join. WaitingRoomViewModel.startGame() is host-only and the bot
    // IS the host, but has no device to tap "Başlat" — this is what starts
    // the match instead, once everyone (bot included) has readied up. ---
    private suspend fun handleWaiting(players: Map<String, Map<String, Any?>>, botGreeted: Boolean) {
        val realPlayers = players.filterKeys { it != BOT_UID }
        if (realPlayers.isEmpty()) return

        if (!botGreeted) {
            delay(Random.nextLong(1_500, 3_501))
            // Re-check: another device may have already sent this greeting
            // while this one was sleeping, or the room may have moved on.
            val fresh = roomRef.get().await()
            if (fresh.getString("status") == "WAITING" && fresh.getBoolean("botGreeted") != true) {
                roomRef.update("botGreeted", true).await()
                roomRef.collection("reactions").add(
                    mapOf("uid" to BOT_UID, "emoji" to "👋", "messageKey" to "hi", "sentAt" to System.currentTimeMillis())
                ).await()
            }
            return // the botGreeted write above re-triggers this listener anyway
        }

        val botReady = players[BOT_UID]?.get("ready") as? Boolean == true
        if (!botReady) {
            delay(Random.nextLong(2_000, 8_001))
            // Re-check: another device may have already marked the bot
            // ready while this one was sleeping, or the room may have moved
            // on (everyone left, room recycled, etc).
            val fresh = roomRef.get().await()
            if (fresh.getString("status") != "WAITING") return
            @Suppress("UNCHECKED_CAST")
            val freshPlayers = fresh.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
            if (freshPlayers.keys.none { it != BOT_UID }) return // everyone left while sleeping
            if (freshPlayers[BOT_UID]?.get("ready") as? Boolean != true) {
                roomRef.update("players.$BOT_UID.ready", true).await()
            }
            return // the ready write above re-triggers this listener anyway
        }

        if (!realPlayers.values.all { it["ready"] as? Boolean == true }) return

        // A short, randomized "starting the match" delay once everyone's
        // ready — an instant/mechanical start would be the first thing to
        // give the bot away.
        delay(Random.nextLong(3_000, 7_001))

        val trained = pickTrainedWords(WORD_COUNT_TARGET)
        if (trained.size < WORD_COUNT_MIN) return
        val wordIds = trained.mapNotNull { (it["wordId"] as? Number)?.toInt() }
        if (wordIds.size < WORD_COUNT_MIN) return

        // Transaction guard: only actually start if the room is still
        // WAITING and every real player is still ready by the time the
        // delay above elapses — closes the race where someone un-readies,
        // or a second real player joins not-yet-ready, while this device
        // was sleeping.
        firestore.runTransaction<Unit> { tx ->
            val fresh = tx.get(roomRef)
            if (fresh.exists() && fresh.getString("status") == "WAITING") {
                @Suppress("UNCHECKED_CAST")
                val freshPlayers = fresh.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
                val freshReal = freshPlayers.filterKeys { it != BOT_UID }
                if (freshReal.isNotEmpty() && freshReal.values.all { it["ready"] as? Boolean == true }) {
                    tx.update(
                        roomRef,
                        mapOf(
                            "status" to "PLAYING",
                            "wordIds" to wordIds.map { it.toLong() },
                            "startedAt" to System.currentTimeMillis()
                        )
                    )
                }
            }
        }.await()
    }

    // --- Submit the bot's own (pre-trained) drawing result ---
    private suspend fun handlePlaying(snapshot: DocumentSnapshot, players: Map<String, Map<String, Any?>>) {
        val botPlayer = players[BOT_UID] ?: return
        if (botPlayer["finished"] as? Boolean == true) return
        val wordIds = (snapshot.get("wordIds") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        if (wordIds.isEmpty()) return

        // Proportional to word count, mimicking real drawing+guessing time —
        // an instant result would be the same tell as an instant start.
        val delayMs = wordIds.sumOf { Random.nextLong(6_000, 12_001) }.coerceAtMost(240_000)
        delay(delayMs)

        // Re-check after the delay: another device may have already
        // submitted, or the room may have been reset/rematched meanwhile.
        val fresh = roomRef.get().await()
        if (!fresh.exists() || fresh.getString("status") != "PLAYING") return
        @Suppress("UNCHECKED_CAST")
        val freshPlayers = fresh.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
        if (freshPlayers[BOT_UID]?.get("finished") as? Boolean == true) return
        val freshWordIds = (fresh.get("wordIds") as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        if (freshWordIds != wordIds) return

        val trainedDocs = wordIds.mapNotNull { id ->
            firestore.collection("botTrainedWords").document(id.toString()).get().await().takeIf { it.exists() }
        }
        if (trainedDocs.isEmpty()) return

        // The bot always DRAWS its trained strokes (that part is always
        // "real"), but doesn't always GUESS its own drawing correctly —
        // same as a real player forgetting one of theirs. See
        // sampleWrongCount's distribution.
        val wrongCount = sampleWrongCount(trainedDocs.size)
        val wrongIndices = trainedDocs.indices.shuffled().take(wrongCount).toSet()
        val items = trainedDocs.mapIndexed { index, doc ->
            val word = doc.getString("word") ?: ""
            val strokesJson = doc.getString("strokesJson") ?: "[]"
            val strokes = runCatching { json.decodeFromString<List<DrawingStroke>>(strokesJson) }.getOrDefault(emptyList())
            ResultItem(word = word, isCorrect = index !in wrongIndices, strokes = strokes)
        }

        val correctItems = items.filter { it.isCorrect }
        // A little score variety (occasional speed bonus) so every bot
        // result doesn't look like the exact same round number.
        val totalScore = correctItems.size * POINTS_CORRECT + correctItems.count { Random.nextInt(100) < 40 } * SPEED_BONUS_POINTS
        val fastestCorrectMs = if (correctItems.isNotEmpty()) Random.nextLong(1_200, 3_501) else null

        roomRef.update(
            mapOf(
                "players.$BOT_UID.finished" to true,
                "players.$BOT_UID.totalScore" to totalScore,
                "players.$BOT_UID.correctCount" to correctItems.size.toLong(),
                "players.$BOT_UID.wrongCount" to (items.size - correctItems.size).toLong(),
                "players.$BOT_UID.fastestCorrectMs" to fastestCorrectMs
            )
        ).await()

        roomRef.collection("results").document(BOT_UID)
            .set(mapOf("itemsJson" to json.encodeToString(items)))
            .await()

        // Mirrors OnlineGameRepositoryImpl.submitResult's "last one to
        // finish flips the room" logic — re-read so this sees every real
        // player's own concurrent submission, not the stale snapshot from
        // before the delay above. pendingNextRound players (joined
        // mid-round) never submit a result for THIS round, so they're
        // excluded here too — otherwise the round could never reach
        // FINISHED once a late joiner was sitting in the lobby.
        val afterSubmit = roomRef.get().await()
        @Suppress("UNCHECKED_CAST")
        val afterPlayers = afterSubmit.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
        val activePlayers = afterPlayers.filterValues { it["pendingNextRound"] as? Boolean != true }
        val allFinished = activePlayers.isNotEmpty() && activePlayers.values.all { it["finished"] as? Boolean == true }
        if (allFinished) {
            roomRef.update(mapOf("status" to "FINISHED", "finishedAt" to System.currentTimeMillis())).await()
        }
    }

    // --- Vote rematch + send a few spaced-out emoji reactions — unless
    // someone joined mid-round, in which case the whole group goes straight
    // back to the lobby instead (see returnToWaitingKeepingPlayers). ---
    private suspend fun handleFinished(snapshot: DocumentSnapshot) {
        @Suppress("UNCHECKED_CAST")
        val players = snapshot.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
        if (players.values.any { it["pendingNextRound"] as? Boolean == true }) {
            returnToWaitingKeepingPlayers()
            return
        }

        val rematchVotes = (snapshot.get("rematchVotes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        if (BOT_UID in rematchVotes) return // already handled this round

        delay(Random.nextLong(2_000, 5_001))
        roomRef.update("rematchVotes", FieldValue.arrayUnion(BOT_UID)).await()

        repeat(Random.nextInt(2, 5)) {
            delay(Random.nextLong(1_500, 4_001))
            val (emoji, key) = PRESET_REACTIONS.random()
            roomRef.collection("reactions").add(
                mapOf("uid" to BOT_UID, "emoji" to emoji, "messageKey" to key, "sentAt" to System.currentTimeMillis())
            ).await()
        }
    }

    // %40 hepsini doğru bilir, %30 bir tanesini boş bırakır, %20 iki tanesini,
    // %10 üç tanesini — clamped to how many words are even in this match, so
    // a short match can't roll a wrong count larger than its own word count.
    private fun sampleWrongCount(wordCount: Int): Int {
        val roll = Random.nextInt(100)
        val target = when {
            roll < 40 -> 0
            roll < 70 -> 1
            roll < 90 -> 2
            else -> 3
        }
        return target.coerceAtMost(wordCount)
    }

    private suspend fun pickTrainedWords(count: Int): List<Map<String, Any?>> {
        val snapshot = firestore.collection("botTrainedWords").get().await()
        return snapshot.documents.mapNotNull { it.data }.shuffled().take(count)
    }

    private fun botPlayerMap() = mapOf(
        "displayName" to BOT_DISPLAY_NAME,
        "joinedAt" to System.currentTimeMillis(),
        // Starts NOT ready — handleWaiting flips this to true itself after
        // a random 2-8s delay once a real player has joined, instead of
        // looking instantly, suspiciously ready the moment the room exists.
        "ready" to false,
        "finished" to false,
        "left" to false,
        "totalScore" to 0L,
        "correctCount" to 0L,
        "wrongCount" to 0L,
        "fastestCorrectMs" to null,
        "pendingNextRound" to false
    )

    // Same shape as OnlineGameRepositoryImpl.playerMap()'s default (real
    // players start not-ready, unlike the bot) — used when resetting a real
    // player back to a fresh lobby state.
    private fun realPlayerMap(displayName: String) = mapOf(
        "displayName" to displayName,
        "joinedAt" to System.currentTimeMillis(),
        "ready" to false,
        "finished" to false,
        "left" to false,
        "totalScore" to 0L,
        "correctCount" to 0L,
        "wrongCount" to 0L,
        "fastestCorrectMs" to null,
        "pendingNextRound" to false
    )

    // No-vote-needed sibling of the rematch flow above, for when a real
    // player joined mid-round: everyone (finishers + the new joiner) is
    // reset to a fresh WAITING lobby together instead of an instant
    // rematch — mirrors OnlineGameRepositoryImpl.returnToWaitingRoom.
    private suspend fun returnToWaitingKeepingPlayers() {
        firestore.runTransaction<Unit> { tx ->
            val fresh = tx.get(roomRef)
            if (fresh.exists() && fresh.getString("status") == "FINISHED") {
                @Suppress("UNCHECKED_CAST")
                val playersMap = fresh.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
                val resetPlayers = playersMap.mapValues { (uid, data) ->
                    if (uid == BOT_UID) botPlayerMap() else realPlayerMap(data["displayName"] as? String ?: "")
                }
                tx.update(
                    roomRef,
                    mapOf(
                        "status" to "WAITING",
                        "wordIds" to emptyList<Long>(),
                        "players" to resetPlayers,
                        "rematchVotes" to emptyList<String>()
                    )
                )
            }
        }.await()
    }

    private suspend fun resetToWaiting() {
        roomRef.set(
            mapOf(
                "hostUid" to BOT_UID,
                "status" to "WAITING",
                "wordCount" to WORD_COUNT_TARGET.toLong(),
                "category" to null,
                "difficulty" to null,
                "mode" to "NORMAL",
                "wordIds" to emptyList<Long>(),
                "players" to mapOf(BOT_UID to botPlayerMap()),
                "rematchVotes" to emptyList<String>(),
                "botGreeted" to false
            )
        ).await()
    }

    // --- Room recycling: creates the room if it's ever missing, resets a
    // finished match that's sat around too long back to WAITING (real
    // players leaving a result screen never explicitly "closes" the room —
    // leaveRoom only flips a left flag), force-resets a match stuck in
    // PLAYING far longer than any real round should take, and prunes
    // players.size()==8 stragglers who technically "left" but were never
    // removed from the map (see OnlineGameRepositoryImpl.leaveRoom). Called
    // from ensureBootstrapped(), so the very next person to join repairs a
    // broken room before their own join — no background schedule needed. ---
    private suspend fun runMaintenance() {
        val snapshot = roomRef.get().await()
        if (!snapshot.exists()) {
            resetToWaiting()
            return
        }
        val status = snapshot.getString("status") ?: return
        val now = System.currentTimeMillis()

        when (status) {
            "FINISHED" -> {
                val finishedAt = snapshot.getLong("finishedAt") ?: 0L
                if (now - finishedAt > 3 * 60_000L) resetToWaiting()
            }
            "PLAYING" -> {
                val startedAt = snapshot.getLong("startedAt") ?: 0L
                if (now - startedAt > 15 * 60_000L) resetToWaiting()
            }
            "WAITING" -> {
                @Suppress("UNCHECKED_CAST")
                val players = snapshot.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
                val pruned = players.filterKeys { uid -> uid == BOT_UID || players[uid]?.get("left") != true }
                if (pruned.size != players.size) {
                    roomRef.update("players", pruned).await()
                }
            }
        }
    }
}
