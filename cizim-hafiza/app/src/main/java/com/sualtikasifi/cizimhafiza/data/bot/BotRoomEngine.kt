package com.sualtikasifi.cizimhafiza.data.bot

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
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
        // Public: presentation layer needs this to recognize the bot player
        // and render BotMascot instead of a generic person icon (see
        // WaitingRoomScreen/OnlineResultScreen).
        const val BOT_UID = "karalak-bot"
        private const val BOT_DISPLAY_NAME = "Sude"
        private const val WORD_COUNT_TARGET = 10
        private const val WORD_COUNT_MIN = 3
        private const val POINTS_CORRECT = 5L
        private const val SPEED_BONUS_POINTS = 2L

        // --- Chat pacing (see BotChatBrain for the decision side) ---
        // How long one of Sude's characters lasts. Long enough that she's a
        // consistent person for a whole sitting, short enough that she's
        // someone else next time you come back — a character re-rolled every
        // single match would read as randomness, not personality.
        private const val PERSONALITY_TTL_MS = 20 * 60_000L
        // Nothing she says ever lands closer than this to her last message,
        // no matter how many things happen at once.
        private const val CHAT_COOLDOWN_MS = 30_000L
        private const val RECENT_KEYS_LIMIT = 8
        // Holds both "reply:<id>" claims and "count:<id>" message tallies, so
        // it needs room for a good few of each within one match.
        private const val HANDLED_LIMIT = 40
        private const val LOCAL_ATTEMPT_CACHE_LIMIT = 200
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val listenerStarted = AtomicBoolean(false)
    private val roomRef: DocumentReference get() = firestore.collection("rooms").document(ROOM_CODE)

    // Events this process already looked at. Purely a cost saver — the real,
    // cross-device guarantee is botChat.handled in Firestore — but the room
    // listener re-fires on every write, and without this each one would cost
    // a fresh read per already-settled event.
    private val locallyAttempted = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var idleChatterScheduledForSeed = -1L

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) auth.signInAnonymously().await()
    }

    /** Call before the first joinRoom() attempt on this code — creates the room if missing, repairs it if stuck. */
    suspend fun ensureBootstrapped() {
        ensureSignedIn()
        runMaintenance()
    }

    /** Starts the long-lived listeners that drive bot behavior, if this process hasn't already started them. */
    fun ensureRunning() {
        if (!listenerStarted.compareAndSet(false, true)) return
        scope.launch {
            ensureSignedIn()
            observeAndDrive()
        }
        scope.launch {
            ensureSignedIn()
            observeChat()
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
            "WAITING" -> handleWaiting(players)
            "PLAYING" -> handlePlaying(snapshot, players)
            "FINISHED" -> handleFinished(snapshot)
        }
    }

    // --- Waiting room: becomes "ready" itself after a random 2-8s delay
    // (not instantly, like a real person needing a moment), then waits for
    // every real player to actually tap "Hazır Ol" before starting —
    // matching a real friend's room instead of yanking everyone straight
    // into the match the instant they join. WaitingRoomViewModel.startGame()
    // is host-only and the bot IS the host, but has no device to tap
    // "Başlat" — this is what starts the match instead, once everyone (bot
    // included) has readied up. ---
    private suspend fun handleWaiting(players: Map<String, Map<String, Any?>>) {
        val realPlayers = players.filterKeys { it != BOT_UID }
        if (realPlayers.isEmpty()) return

        // Only genuinely fresh arrivals — without the age check, every player
        // already sitting in the lobby would get "greeted" again the moment
        // this listener attached (e.g. on app relaunch).
        val now = System.currentTimeMillis()
        realPlayers.forEach { (uid, data) ->
            if (data["left"] as? Boolean == true) return@forEach
            val joinedAt = (data["joinedAt"] as? Number)?.toLong() ?: return@forEach
            if (now - joinedAt > 60_000L) return@forEach
            scope.launch { runCatching { maybeChat(BotChatMoment.PLAYER_JOINED, "join:$uid:$joinedAt") } }
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
        val matchSeed = (snapshot.get("startedAt") as? Number)?.toLong() ?: 0L
        if (matchSeed != 0L) {
            scope.launch { runCatching { maybeChat(BotChatMoment.MATCH_START, "start:$matchSeed") } }
            scheduleMidMatchChatter(matchSeed)
        }

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
        // mid-round) and players who left mid-match are excluded here too —
        // otherwise the round could never reach FINISHED.
        val afterSubmit = roomRef.get().await()
        @Suppress("UNCHECKED_CAST")
        val afterPlayers = afterSubmit.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()
        val activePlayers = afterPlayers.filterValues {
            it["pendingNextRound"] as? Boolean != true && it["left"] as? Boolean != true
        }
        val allFinished = activePlayers.isNotEmpty() && activePlayers.values.all { it["finished"] as? Boolean == true }
        if (allFinished) {
            roomRef.update(mapOf("status" to "FINISHED", "finishedAt" to System.currentTimeMillis())).await()
        }
    }

    // --- Vote rematch — unless someone joined mid-round, in which case the
    // whole group goes straight back to the lobby instead (see
    // returnToWaitingKeepingPlayers). ---
    private suspend fun handleFinished(snapshot: DocumentSnapshot) {
        @Suppress("UNCHECKED_CAST")
        val players = snapshot.get("players") as? Map<String, Map<String, Any?>> ?: emptyMap()

        val matchSeed = (snapshot.get("startedAt") as? Number)?.toLong() ?: 0L
        if (matchSeed != 0L) {
            val moment = endOfMatchMoment(snapshot, players)
            scope.launch { runCatching { maybeChat(moment, "end:$matchSeed") } }
        }

        if (players.values.any { it["pendingNextRound"] as? Boolean == true }) {
            returnToWaitingKeepingPlayers()
            return
        }

        val rematchVotes = (snapshot.get("rematchVotes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        if (BOT_UID in rematchVotes) return // already handled this round

        delay(Random.nextLong(2_000, 5_001))
        roomRef.update("rematchVotes", FieldValue.arrayUnion(BOT_UID)).await()
    }

    // --- Chat ---
    //
    // Watches the reactions subcollection for anything a real player says and
    // gives Sude a chance to answer. Messages already in the subcollection
    // when this listener first attaches (a previous round, or an app
    // relaunch) must NOT trigger replies — only genuinely new ones.
    private suspend fun observeChat() {
        var isFirstSnapshot = true
        callbackFlow {
            val registration = roomRef.collection("reactions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    trySend(snapshot)
                }
            awaitClose { registration.remove() }
        }.catch { }.collect { snapshot ->
            if (snapshot == null) return@collect
            val skippingInitialLoad = isFirstSnapshot
            isFirstSnapshot = false
            if (skippingInitialLoad) return@collect

            snapshot.documentChanges
                .filter { it.type == DocumentChange.Type.ADDED && it.document.getString("uid") != BOT_UID }
                .forEach { change ->
                    val doc = change.document
                    // Launched, not awaited: maybeChat sleeps for a human-ish
                    // beat before answering, and blocking the listener that
                    // whole time would stall every later snapshot behind it.
                    scope.launch {
                        runCatching {
                            recordHumanMessage(doc.id)
                            maybeChat(
                                moment = BotChatMoment.DIRECT_REPLY,
                                eventKey = "reply:${doc.id}",
                                incomingKey = doc.getString("messageKey")
                            )
                        }
                    }
                }
        }
    }

    /**
     * The one place Sude decides to speak. Returns without a word far more
     * often than not — see BotChatBrain for why that's the point.
     *
     * Ordering matters here: the roll happens *before* the claim, and is
     * seeded so every device reaches the same verdict; the claim transaction
     * then just picks which device does the writing. Rolling inside the claim
     * instead would make her chattier the more devices were in the room.
     */
    private suspend fun maybeChat(
        moment: BotChatMoment,
        eventKey: String,
        incomingKey: String? = null
    ) {
        if (!markLocallyAttempted(eventKey)) return

        val snapshot = roomRef.get().await()
        if (!snapshot.exists()) return
        val matchSeed = (snapshot.get("startedAt") as? Number)?.toLong() ?: 0L
        val chat = snapshot.botChatState(matchSeed)
        if (eventKey in chat.handled) return

        val personality = ensurePersonality()
        if (chat.sentThisMatch >= personality.matchBudget) return

        val decision = BotChatBrain.decide(
            moment = moment,
            personality = personality,
            eventKey = eventKey,
            matchSeed = matchSeed,
            humanMessageCount = chat.humanMessageCount,
            recentKeys = chat.recentKeys,
            incomingKey = incomingKey
        ) ?: return

        // A follow-up costs a second message from the same budget, so it's
        // the first thing dropped when she's close to her limit.
        val followUp = decision.followUp?.takeIf { chat.sentThisMatch + 2 <= personality.matchBudget }
        val spokenKeys = listOfNotNull(decision.message.messageKey, followUp?.messageKey)
        if (!claimChatEvent(eventKey, matchSeed, personality.matchBudget, spokenKeys)) return

        delay(decision.delayMs)
        sendBotMessage(decision.message)
        if (followUp != null) {
            delay(decision.followUpDelayMs)
            sendBotMessage(followUp)
        }
    }

    /**
     * The one moment nothing in the room prompts — a stray thought partway
     * through a match. Deliberately the longest odds of any trigger, and the
     * only place Sude ever opens a conversation herself.
     *
     * Needs its own timer because the room listener is the only other clock
     * here, and nothing writes to the room mid-match.
     */
    private fun scheduleMidMatchChatter(matchSeed: Long) {
        if (idleChatterScheduledForSeed == matchSeed) return
        idleChatterScheduledForSeed = matchSeed
        scope.launch {
            delay(Random.nextLong(25_000, 70_001))
            runCatching { maybeChat(BotChatMoment.MID_MATCH, "idle:$matchSeed") }
        }
    }

    private suspend fun sendBotMessage(message: BotMessage) {
        roomRef.collection("reactions").add(
            mapOf(
                "uid" to BOT_UID,
                "emoji" to message.emoji,
                "messageKey" to message.messageKey,
                "sentAt" to System.currentTimeMillis()
            )
        ).await()
    }

    /** Everything the chat brain needs to know, with per-match counters zeroed when the match changes. */
    private data class BotChatState(
        val sentThisMatch: Int,
        val recentKeys: List<String>,
        val handled: List<String>,
        val humanMessageCount: Int
    )

    private fun DocumentSnapshot.botChatState(matchSeed: Long): BotChatState {
        @Suppress("UNCHECKED_CAST")
        val stored = get("botChat") as? Map<String, Any?> ?: emptyMap()
        val sameMatch = (stored["matchSeed"] as? Number)?.toLong() == matchSeed
        return BotChatState(
            sentThisMatch = if (sameMatch) (stored["sentThisMatch"] as? Number)?.toInt() ?: 0 else 0,
            recentKeys = (stored["recentKeys"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            handled = (stored["handled"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            humanMessageCount = if (sameMatch) (stored["humanMessageCount"] as? Number)?.toInt() ?: 0 else 0
        )
    }

    /**
     * Reads whoever Sude currently is, rolling a new character when the last
     * one has aged out (see PERSONALITY_TTL_MS). Two devices arriving at an
     * unset character can't disagree: the transaction retries the loser,
     * which then reads the winner's choice.
     */
    private suspend fun ensurePersonality(): BotPersonality =
        firestore.runTransaction<BotPersonality> { tx ->
            val snapshot = tx.get(roomRef)
            @Suppress("UNCHECKED_CAST")
            val stored = snapshot.get("botChat") as? Map<String, Any?> ?: emptyMap()
            val existing = BotPersonality.fromNameOrNull(stored["personality"] as? String)
            val setAt = (stored["personalitySetAt"] as? Number)?.toLong() ?: 0L
            val now = System.currentTimeMillis()
            if (existing != null && now - setAt < PERSONALITY_TTL_MS) {
                existing
            } else {
                val fresh = BotPersonality.weightedRandom(Random)
                tx.update(
                    roomRef,
                    mapOf(
                        "botChat.personality" to fresh.name,
                        "botChat.personalitySetAt" to now
                    )
                )
                fresh
            }
        }.await()

    /**
     * Claims the right to answer [eventKey] — exactly one device wins, and
     * only if the budget and cooldown still allow it. Re-checks both inside
     * the transaction because another device may have spoken while this one
     * was making up its mind.
     */
    private suspend fun claimChatEvent(
        eventKey: String,
        matchSeed: Long,
        budget: Int,
        spokenKeys: List<String>
    ): Boolean = firestore.runTransaction<Boolean> { tx ->
        val snapshot = tx.get(roomRef)
        @Suppress("UNCHECKED_CAST")
        val stored = snapshot.get("botChat") as? Map<String, Any?> ?: emptyMap()
        val handled = (stored["handled"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val sameMatch = (stored["matchSeed"] as? Number)?.toLong() == matchSeed
        val sent = if (sameMatch) (stored["sentThisMatch"] as? Number)?.toInt() ?: 0 else 0
        val lastSentAt = (stored["lastSentAt"] as? Number)?.toLong() ?: 0L
        val now = System.currentTimeMillis()

        if (eventKey in handled || sent >= budget || now - lastSentAt < CHAT_COOLDOWN_MS) {
            false
        } else {
            val recent = (stored["recentKeys"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            tx.update(
                roomRef,
                mapOf(
                    "botChat.handled" to (handled + eventKey).takeLast(HANDLED_LIMIT),
                    "botChat.matchSeed" to matchSeed,
                    "botChat.sentThisMatch" to sent + spokenKeys.size,
                    "botChat.lastSentAt" to now,
                    "botChat.recentKeys" to (recent + spokenKeys).takeLast(RECENT_KEYS_LIMIT),
                    "botChat.humanMessageCount" to
                        (if (sameMatch) (stored["humanMessageCount"] as? Number)?.toInt() ?: 0 else 0)
                )
            )
            true
        }
    }.await()

    /** Feeds the mirroring damper: a player who never chats gets a near-silent Sude. */
    private suspend fun recordHumanMessage(reactionId: String) {
        firestore.runTransaction<Unit> { tx ->
            val snapshot = tx.get(roomRef)
            @Suppress("UNCHECKED_CAST")
            val stored = snapshot.get("botChat") as? Map<String, Any?> ?: emptyMap()
            val handled = (stored["handled"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val countKey = "count:$reactionId"
            if (countKey in handled) return@runTransaction
            val matchSeed = (snapshot.get("startedAt") as? Number)?.toLong() ?: 0L
            val sameMatch = (stored["matchSeed"] as? Number)?.toLong() == matchSeed
            tx.update(
                roomRef,
                mapOf(
                    "botChat.handled" to (handled + countKey).takeLast(HANDLED_LIMIT),
                    "botChat.matchSeed" to matchSeed,
                    "botChat.humanMessageCount" to
                        (if (sameMatch) (stored["humanMessageCount"] as? Number)?.toInt() ?: 0 else 0) + 1,
                    "botChat.sentThisMatch" to
                        (if (sameMatch) (stored["sentThisMatch"] as? Number)?.toInt() ?: 0 else 0)
                )
            )
        }.await()
    }

    private fun markLocallyAttempted(eventKey: String): Boolean = synchronized(locallyAttempted) {
        if (locallyAttempted.size > LOCAL_ATTEMPT_CACHE_LIMIT) locallyAttempted.clear()
        locallyAttempted.add(eventKey)
    }

    /**
     * Which flavour of "the match just ended" this was. Every device reads
     * the same settled scores here, so they all classify it identically.
     */
    private fun endOfMatchMoment(
        snapshot: DocumentSnapshot,
        players: Map<String, Map<String, Any?>>
    ): BotChatMoment {
        val wordCount = (snapshot.get("wordCount") as? Number)?.toInt() ?: WORD_COUNT_TARGET
        val botScore = (players[BOT_UID]?.get("totalScore") as? Number)?.toInt() ?: 0
        val human = players
            .filterKeys { it != BOT_UID }
            .filterValues { it["left"] as? Boolean != true && it["pendingNextRound"] as? Boolean != true }
            .values
            .maxByOrNull { (it["totalScore"] as? Number)?.toInt() ?: 0 }
            ?: return BotChatMoment.MATCH_END_ROUTINE
        val humanScore = (human["totalScore"] as? Number)?.toInt() ?: 0
        val humanCorrect = (human["correctCount"] as? Number)?.toInt() ?: 0
        return when {
            // Within one word's worth of points — the rarest and most
            // remark-worthy way for a match to end.
            abs(botScore - humanScore) <= POINTS_CORRECT.toInt() -> BotChatMoment.MATCH_END_CLOSE
            wordCount > 0 && humanCorrect >= wordCount -> BotChatMoment.MATCH_END_HUMAN_GREAT
            humanScore > botScore -> BotChatMoment.MATCH_END_BOT_LOST
            wordCount > 0 && humanCorrect * 10 <= wordCount * 3 -> BotChatMoment.MATCH_END_HUMAN_STRUGGLED
            else -> BotChatMoment.MATCH_END_ROUTINE
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
                // A real player who quit (left=true) is dropped, not reset —
                // otherwise every rematch silently revives them as active
                // and the room waits on them forever (see
                // OnlineGameRepositoryImpl.returnToWaitingRoom for the same fix).
                val resetPlayers = playersMap
                    .filterNot { (uid, data) -> uid != BOT_UID && data["left"] as? Boolean == true }
                    .mapValues { (uid, data) ->
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
                "rematchVotes" to emptyList<String>()
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
