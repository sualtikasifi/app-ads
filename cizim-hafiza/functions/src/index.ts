import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { logger } from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

/**
 * Fires whenever a friend match invite is written to
 * users/{uid}/invites/{inviteId} (see FriendRepositoryImpl.sendMatchInvite
 * in the Android app) and pushes a notification to the recipient's device —
 * this is what lets an invite reach someone whose app isn't currently open
 * (FriendInviteMessagingService.kt only fires from a live app process; this
 * function is what wakes it up).
 *
 * The message payload is data-only (no top-level `notification` field) on
 * purpose: a `notification` payload gets displayed directly by the OS when
 * the app is backgrounded/killed, bypassing FriendInviteMessagingService
 * entirely — which means no custom channel, no localized text (this
 * function has no idea what language the recipient's device is in), and no
 * tap-through PendingIntent to open the app. A data-only payload always
 * hands control to onMessageReceived, which builds everything from the
 * recipient's own local string resources.
 */
export const onInviteCreated = onDocumentCreated(
  "users/{uid}/invites/{inviteId}",
  async (event) => {
    const invite = event.data?.data();
    if (!invite) return;

    const { uid, inviteId } = event.params;
    const fromNickname: string | undefined = invite.fromNickname;
    const roomCode: string | undefined = invite.roomCode;
    if (!fromNickname || !roomCode) {
      logger.warn(`Invite ${inviteId} missing fromNickname/roomCode, skipping push`);
      return;
    }

    const recipient = await admin.firestore().collection("users").doc(uid).get();
    const fcmToken: string | undefined = recipient.get("fcmToken");
    if (!fcmToken) {
      // Recipient has never opened notifications on this device (or is on
      // an old install from before push was added) — not an error, they'll
      // still see the invite via the in-app banner next time they open the
      // app, same as before this feature existed.
      return;
    }

    try {
      await admin.messaging().send({
        token: fcmToken,
        data: {
          inviteId,
          roomCode,
          fromNickname,
        },
        android: {
          priority: "high",
        },
      });
    } catch (error) {
      // A stale/uninstalled-app token is expected over time, not a bug —
      // just log it rather than retrying (the invite itself already exists
      // in Firestore regardless, so nothing is lost).
      logger.warn(`Failed to send invite push for ${inviteId}:`, error);
    }
  }
);

// ---------------------------------------------------------------------------
// Bot opponent (room 130246) — see PLAY_STORE.md / the "Bot Eğitim" main-menu
// entry for the feature. The bot has no device, so every Firestore write
// attributed to it happens here via the Admin SDK (which bypasses
// firestore.rules) instead of from any app-side code. See
// OnlineGameRepositoryImpl.kt for the exact rooms/{roomCode} data shape this
// mirrors.
// ---------------------------------------------------------------------------

const BOT_ROOM_CODE = "130246";
const BOT_UID = "karalak-bot";
const BOT_DISPLAY_NAME = "Ayşe";
// Keep in sync with GameConstants.kt (both are indie-app-scale constants,
// not read from a shared source).
const BOT_WORD_COUNT_TARGET = 10;
const BOT_WORD_COUNT_MIN = 3;
const POINTS_CORRECT = 5;
const SPEED_BONUS_POINTS = 2;
// Mirrors ReactionBar.kt's PRESET_REACTIONS — kept in sync manually since
// Cloud Functions can't import Android/Compose code.
const PRESET_REACTIONS = [
  { emoji: "😂", key: "funny" },
  { emoji: "👏", key: "nice" },
  { emoji: "😅", key: "hard" },
  { emoji: "🔥", key: "fire" },
  { emoji: "😱", key: "shock" },
  { emoji: "👋", key: "hi" },
];

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function randomInt(minInclusive: number, maxInclusive: number): number {
  return Math.floor(Math.random() * (maxInclusive - minInclusive + 1)) + minInclusive;
}

function botPlayerMap() {
  return {
    displayName: BOT_DISPLAY_NAME,
    joinedAt: Date.now(),
    ready: true,
    finished: false,
    left: false,
    totalScore: 0,
    correctCount: 0,
    wrongCount: 0,
    fastestCorrectMs: null,
  };
}

/** Picks up to `count` random trained words, reading straight from botTrainedWords (see the "Bot Eğitim" screen / BotTrainingRepositoryImpl.kt). */
async function pickTrainedWords(count: number): Promise<admin.firestore.QueryDocumentSnapshot[]> {
  const snapshot = await admin.firestore().collection("botTrainedWords").get();
  const docs = snapshot.docs;
  for (let i = docs.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [docs[i], docs[j]] = [docs[j], docs[i]];
  }
  return docs.slice(0, count);
}

/** Resets room 130246 back to a fresh, joinable WAITING state with only the bot present. */
async function resetBotRoomToWaiting(roomRef: admin.firestore.DocumentReference) {
  await roomRef.set(
    {
      hostUid: BOT_UID,
      status: "WAITING",
      wordCount: BOT_WORD_COUNT_TARGET,
      category: null,
      difficulty: null,
      mode: "NORMAL",
      wordIds: [],
      players: { [BOT_UID]: botPlayerMap() },
      rematchVotes: [],
      startingLockedAt: admin.firestore.FieldValue.delete(),
      startedAt: admin.firestore.FieldValue.delete(),
      finishedAt: admin.firestore.FieldValue.delete(),
    },
    { merge: false }
  );
}

/**
 * Every write to rooms/130246 re-runs this — it drives all three bot
 * "decisions" that would otherwise need a real device: starting the match
 * (WaitingRoomViewModel.startGame is host-only, and the bot IS the host but
 * has no client to tap the button), submitting the bot's own drawing result,
 * and voting rematch + sending emoji reactions once the match finishes.
 * Each branch re-checks the room's current state before writing anything,
 * so a retrigger caused by this same function's own writes is a safe no-op.
 */
export const onBotRoomWrite = onDocumentWritten(
  { document: `rooms/${BOT_ROOM_CODE}`, timeoutSeconds: 300 },
  async (event) => {
    const after = event.data?.after;
    if (!after || !after.exists) return;
    const room = after.data();
    if (!room) return;
    const roomRef = after.ref;
    const players: Record<string, any> = room.players ?? {};

    // --- Branch 1: auto-start once a real player has joined a waiting room ---
    if (room.status === "WAITING") {
      const realPlayerCount = Object.keys(players).filter((uid) => uid !== BOT_UID).length;
      if (realPlayerCount > 0) {
        // A short, randomized "the host is getting ready" delay — an
        // instant/mechanical start would be the first thing to give the bot
        // away.
        await sleep(randomInt(3000, 6000));

        const trained = await pickTrainedWords(BOT_WORD_COUNT_TARGET);
        if (trained.length < BOT_WORD_COUNT_MIN) {
          logger.warn(`Bot room: only ${trained.length} trained words available, need ${BOT_WORD_COUNT_MIN} to start`);
          return;
        }
        const wordIds = trained.map((doc) => doc.get("wordId"));

        // Transaction guard: only actually start if the room is still
        // WAITING by the time the delay above elapses — closes the race
        // where a second real player's join re-triggers this function while
        // the first invocation is still sleeping.
        await admin.firestore().runTransaction(async (tx) => {
          const fresh = await tx.get(roomRef);
          if (!fresh.exists || fresh.get("status") !== "WAITING") return;
          tx.update(roomRef, {
            status: "PLAYING",
            wordIds,
            startedAt: Date.now(),
          });
        });
      }
      return;
    }

    // --- Branch 2: submit the bot's own (pre-trained) drawing result ---
    if (room.status === "PLAYING") {
      const botPlayer = players[BOT_UID];
      const wordIds: number[] = room.wordIds ?? [];
      if (!botPlayer || botPlayer.finished || wordIds.length === 0) return;

      // Proportional to word count, mimicking real drawing+guessing time —
      // an instant result would be the same tell as an instant start.
      const delayMs = wordIds.reduce((total: number) => total + randomInt(6000, 12000), 0);
      await sleep(Math.min(delayMs, 240_000));

      // Re-check after the delay: another invocation may have already
      // submitted (e.g. this function retriggered itself), or the room may
      // have been reset by maintainBotRoom in the meantime.
      const fresh = await roomRef.get();
      const freshRoom = fresh.data();
      if (!freshRoom || freshRoom.status !== "PLAYING" || freshRoom.players?.[BOT_UID]?.finished) return;
      if (JSON.stringify(freshRoom.wordIds) !== JSON.stringify(wordIds)) return;

      const trainedDocs = await Promise.all(
        wordIds.map((id) => admin.firestore().collection("botTrainedWords").doc(String(id)).get())
      );
      const items = trainedDocs
        .filter((doc) => doc.exists)
        .map((doc) => {
          const data = doc.data()!;
          return {
            word: data.word,
            isCorrect: true,
            strokes: JSON.parse(data.strokesJson),
          };
        });
      if (items.length === 0) return;

      // A little score variety (occasional speed bonus) so every bot result
      // doesn't look like the exact same round number.
      const totalScore = items.length * POINTS_CORRECT + items.filter(() => Math.random() < 0.4).length * SPEED_BONUS_POINTS;
      const fastestCorrectMs = randomInt(1200, 3500);

      await roomRef.update({
        [`players.${BOT_UID}.finished`]: true,
        [`players.${BOT_UID}.totalScore`]: totalScore,
        [`players.${BOT_UID}.correctCount`]: items.length,
        [`players.${BOT_UID}.wrongCount`]: 0,
        [`players.${BOT_UID}.fastestCorrectMs`]: fastestCorrectMs,
      });

      await roomRef.collection("results").doc(BOT_UID).set({ itemsJson: JSON.stringify(items) });

      // Mirrors OnlineGameRepositoryImpl.submitResult's "last one to finish
      // flips the room" logic — re-read so we see every real player's own
      // concurrent submission, not just the stale snapshot from before we
      // slept.
      const afterSubmit = await roomRef.get();
      const afterPlayers: Record<string, any> = afterSubmit.data()?.players ?? {};
      const allFinished = Object.values(afterPlayers).length > 0 && Object.values(afterPlayers).every((p: any) => p.finished);
      if (allFinished) {
        await roomRef.update({ status: "FINISHED", finishedAt: Date.now() });
      }
      return;
    }

    // --- Branch 3: vote rematch + send a few spaced-out emoji reactions ---
    if (room.status === "FINISHED") {
      const rematchVotes: string[] = room.rematchVotes ?? [];
      if (rematchVotes.includes(BOT_UID)) return; // already handled this round

      await sleep(randomInt(2000, 5000));
      await roomRef.update({ rematchVotes: admin.firestore.FieldValue.arrayUnion(BOT_UID) });

      const reactionCount = randomInt(2, 4);
      for (let i = 0; i < reactionCount; i++) {
        await sleep(randomInt(1500, 4000));
        const reaction = PRESET_REACTIONS[randomInt(0, PRESET_REACTIONS.length - 1)];
        await roomRef.collection("reactions").add({
          uid: BOT_UID,
          emoji: reaction.emoji,
          messageKey: reaction.key,
          sentAt: Date.now(),
        });
      }
    }
  }
);

/**
 * Keeps room 130246 usable forever without any manual Firestore Console
 * step: creates it if it's ever missing, recycles it back to WAITING once a
 * finished match has sat around too long (real players leaving a result
 * screen never explicitly "closes" the room — see leaveRoom's comment in
 * OnlineGameRepositoryImpl.kt, it only flips a left flag), and force-resets
 * a match that's been stuck in PLAYING far longer than any real round should
 * take (e.g. a player's app crashed mid-match). Without this sweep the room
 * would eventually fill up with players.size() == 8 stragglers who never
 * technically left, and joinRoom would start rejecting everyone.
 */
export const maintainBotRoom = onSchedule("every 2 minutes", async () => {
  const roomRef = admin.firestore().collection("rooms").doc(BOT_ROOM_CODE);
  const snapshot = await roomRef.get();

  if (!snapshot.exists) {
    await resetBotRoomToWaiting(roomRef);
    return;
  }

  const room = snapshot.data()!;
  const now = Date.now();

  if (room.status === "FINISHED") {
    const finishedAt: number = room.finishedAt ?? 0;
    if (now - finishedAt > 3 * 60 * 1000) {
      await resetBotRoomToWaiting(roomRef);
    }
    return;
  }

  if (room.status === "PLAYING") {
    const startedAt: number = room.startedAt ?? 0;
    if (now - startedAt > 15 * 60 * 1000) {
      await resetBotRoomToWaiting(roomRef);
    }
    return;
  }

  if (room.status === "WAITING") {
    const players: Record<string, any> = room.players ?? {};
    const pruned = Object.fromEntries(
      Object.entries(players).filter(([uid, data]) => uid === BOT_UID || !(data as any).left)
    );
    if (Object.keys(pruned).length !== Object.keys(players).length) {
      await roomRef.update({ players: pruned });
    }
  }
});
