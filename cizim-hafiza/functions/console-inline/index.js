// Plain-JS mirror of ../src/index.ts, kept only for deploying via the
// Google Cloud Console web UI's "Inline editor" (no local machine / Firebase
// CLI needed — see ../DEPLOY.md's "Bilgisayarsız / tarayıcıdan deploy"
// section). If ../src/index.ts ever changes, update this file to match.

const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { logger } = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.onInviteCreated = onDocumentCreated(
  "users/{uid}/invites/{inviteId}",
  async (event) => {
    const invite = event.data ? event.data.data() : null;
    if (!invite) return;

    const { uid, inviteId } = event.params;
    const fromNickname = invite.fromNickname;
    const roomCode = invite.roomCode;
    if (!fromNickname || !roomCode) {
      logger.warn(`Invite ${inviteId} missing fromNickname/roomCode, skipping push`);
      return;
    }

    const recipient = await admin.firestore().collection("users").doc(uid).get();
    const fcmToken = recipient.get("fcmToken");
    if (!fcmToken) return;

    try {
      await admin.messaging().send({
        token: fcmToken,
        data: { inviteId, roomCode, fromNickname },
        android: { priority: "high" },
      });
    } catch (error) {
      logger.warn(`Failed to send invite push for ${inviteId}:`, error);
    }
  }
);

// ---------------------------------------------------------------------------
// Bot opponent (room 130246) — see ../src/index.ts for the full comments.
// ---------------------------------------------------------------------------

const BOT_ROOM_CODE = "130246";
const BOT_UID = "karalak-bot";
const BOT_DISPLAY_NAME = "Ayşe";
const BOT_WORD_COUNT_TARGET = 10;
const BOT_WORD_COUNT_MIN = 3;
const POINTS_CORRECT = 5;
const SPEED_BONUS_POINTS = 2;
const PRESET_REACTIONS = [
  { emoji: "😂", key: "funny" },
  { emoji: "👏", key: "nice" },
  { emoji: "😅", key: "hard" },
  { emoji: "🔥", key: "fire" },
  { emoji: "😱", key: "shock" },
  { emoji: "👋", key: "hi" },
];

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function randomInt(minInclusive, maxInclusive) {
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

async function pickTrainedWords(count) {
  const snapshot = await admin.firestore().collection("botTrainedWords").get();
  const docs = snapshot.docs;
  for (let i = docs.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [docs[i], docs[j]] = [docs[j], docs[i]];
  }
  return docs.slice(0, count);
}

async function resetBotRoomToWaiting(roomRef) {
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

exports.onBotRoomWrite = onDocumentWritten(
  { document: `rooms/${BOT_ROOM_CODE}`, timeoutSeconds: 300 },
  async (event) => {
    const after = event.data ? event.data.after : null;
    if (!after || !after.exists) return;
    const room = after.data();
    if (!room) return;
    const roomRef = after.ref;
    const players = room.players || {};

    if (room.status === "WAITING") {
      const realPlayerCount = Object.keys(players).filter((uid) => uid !== BOT_UID).length;
      if (realPlayerCount > 0) {
        await sleep(randomInt(3000, 6000));

        const trained = await pickTrainedWords(BOT_WORD_COUNT_TARGET);
        if (trained.length < BOT_WORD_COUNT_MIN) {
          logger.warn(`Bot room: only ${trained.length} trained words available, need ${BOT_WORD_COUNT_MIN} to start`);
          return;
        }
        const wordIds = trained.map((doc) => doc.get("wordId"));

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

    if (room.status === "PLAYING") {
      const botPlayer = players[BOT_UID];
      const wordIds = room.wordIds || [];
      if (!botPlayer || botPlayer.finished || wordIds.length === 0) return;

      const delayMs = wordIds.reduce((total) => total + randomInt(6000, 12000), 0);
      await sleep(Math.min(delayMs, 240000));

      const fresh = await roomRef.get();
      const freshRoom = fresh.data();
      if (!freshRoom || freshRoom.status !== "PLAYING" || (freshRoom.players && freshRoom.players[BOT_UID] && freshRoom.players[BOT_UID].finished)) return;
      if (JSON.stringify(freshRoom.wordIds) !== JSON.stringify(wordIds)) return;

      const trainedDocs = await Promise.all(
        wordIds.map((id) => admin.firestore().collection("botTrainedWords").doc(String(id)).get())
      );
      const items = trainedDocs
        .filter((doc) => doc.exists)
        .map((doc) => {
          const data = doc.data();
          return {
            word: data.word,
            isCorrect: true,
            strokes: JSON.parse(data.strokesJson),
          };
        });
      if (items.length === 0) return;

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

      const afterSubmit = await roomRef.get();
      const afterPlayers = (afterSubmit.data() && afterSubmit.data().players) || {};
      const values = Object.values(afterPlayers);
      const allFinished = values.length > 0 && values.every((p) => p.finished);
      if (allFinished) {
        await roomRef.update({ status: "FINISHED", finishedAt: Date.now() });
      }
      return;
    }

    if (room.status === "FINISHED") {
      const rematchVotes = room.rematchVotes || [];
      if (rematchVotes.includes(BOT_UID)) return;

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

exports.maintainBotRoom = onSchedule("every 2 minutes", async () => {
  const roomRef = admin.firestore().collection("rooms").doc(BOT_ROOM_CODE);
  const snapshot = await roomRef.get();

  if (!snapshot.exists) {
    await resetBotRoomToWaiting(roomRef);
    return;
  }

  const room = snapshot.data();
  const now = Date.now();

  if (room.status === "FINISHED") {
    const finishedAt = room.finishedAt || 0;
    if (now - finishedAt > 3 * 60 * 1000) {
      await resetBotRoomToWaiting(roomRef);
    }
    return;
  }

  if (room.status === "PLAYING") {
    const startedAt = room.startedAt || 0;
    if (now - startedAt > 15 * 60 * 1000) {
      await resetBotRoomToWaiting(roomRef);
    }
    return;
  }

  if (room.status === "WAITING") {
    const players = room.players || {};
    const pruned = Object.fromEntries(
      Object.entries(players).filter(([uid, data]) => uid === BOT_UID || !data.left)
    );
    if (Object.keys(pruned).length !== Object.keys(players).length) {
      await roomRef.update({ players: pruned });
    }
  }
});
