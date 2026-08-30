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

    // users/{uid}/private/device, not users/{uid} itself: the parent profile
    // document is readable by any signed-in player (that is how a friend list
    // resolves nicknames), so the push token — which identifies a specific
    // physical device — is kept in the owner-only private/ subcollection.
    // This function runs with admin credentials and bypasses rules, so the
    // move costs delivery nothing. Falls back to the old location so a device
    // that has not opened the app since the move still receives invites.
    const db = admin.firestore();
    const privateDoc = await db
      .collection("users").doc(uid)
      .collection("private").doc("device")
      .get();
    let fcmToken: string | undefined = privateDoc.get("fcmToken");
    if (!fcmToken) {
      const legacyDoc = await db.collection("users").doc(uid).get();
      fcmToken = legacyDoc.get("fcmToken");
    }
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

/**
 * Clamps an impossible score the moment it is written.
 *
 * Scores are computed on the player's own device and written straight into
 * `rooms/{code}.players.{uid}.totalScore`. firestore.rules can stop a player
 * rewriting *someone else's* row, but it cannot check arithmetic — nothing
 * there prevents a modified client from claiming 9999 for its own. This is
 * the arithmetic check: the maximum a round can possibly be worth is one
 * correct answer per word, each with the speed bonus, so anything above that
 * ceiling is rejected and pulled back down to it.
 *
 * Deliberately a clamp rather than a ban. The honest failure modes here (an
 * older client, a rounding difference, a rule tweaked in the app but not
 * here) should degrade to "your score was capped", never to a locked
 * account — and a cheater capped to the same ceiling as everyone else has
 * nothing left to gain.
 *
 * Keep POINTS_CORRECT / SPEED_BONUS_POINTS in sync with GameConstants.kt.
 */
const POINTS_CORRECT = 5;
const SPEED_BONUS_POINTS = 2;
const MAX_POINTS_PER_WORD = POINTS_CORRECT + SPEED_BONUS_POINTS;

export const clampImpossibleScores = onDocumentWritten(
  "rooms/{roomCode}",
  async (event) => {
    const after = event.data?.after;
    if (!after?.exists) return;

    const players = (after.get("players") ?? {}) as Record<string, Record<string, unknown>>;
    const wordIds = (after.get("wordIds") ?? []) as unknown[];
    // Before a round starts there is no word list to bound the score by, and
    // the scores are all zero anyway.
    if (wordIds.length === 0) return;

    const ceiling = wordIds.length * MAX_POINTS_PER_WORD;
    const corrections: Record<string, number> = {};

    for (const [uid, data] of Object.entries(players)) {
      const claimed = typeof data.totalScore === "number" ? data.totalScore : 0;
      if (claimed > ceiling || claimed < 0) {
        corrections[`players.${uid}.totalScore`] = Math.min(Math.max(claimed, 0), ceiling);
        logger.warn(
          `Room ${event.params.roomCode}: ${uid} claimed ${claimed}, ceiling is ${ceiling} — clamping`
        );
      }
    }

    if (Object.keys(corrections).length > 0) {
      // This write re-triggers this same function; the second pass finds
      // every score already within the ceiling and writes nothing, so the
      // recursion terminates after exactly one extra invocation.
      await after.ref.update(corrections);
    }
  }
);

/**
 * Deletes abandoned rooms and their subcollections once a day.
 *
 * Nothing in the app ever removes a room: firestore.rules denies delete
 * outright, and a player leaving only flips a `left` flag. Every room ever
 * created — plus its `results` documents, which carry the full stroke data
 * for every drawing, and its `reactions` log — therefore accumulates
 * forever, and the Firestore bill grows with it for storage nobody can
 * reach any more.
 *
 * Room 130246 is the permanent bot room and is explicitly never collected.
 */
export const cleanupAbandonedRooms = onSchedule(
  { schedule: "every day 04:00", timeZone: "Europe/Istanbul" },
  async () => {
    const BOT_ROOM = "130246";
    const MAX_AGE_MS = 24 * 60 * 60 * 1000;
    const cutoff = Date.now() - MAX_AGE_MS;
    const db = admin.firestore();

    const rooms = await db.collection("rooms").get();
    let deleted = 0;

    for (const room of rooms.docs) {
      if (room.id === BOT_ROOM) continue;

      // startedAt is only set once a match begins, so fall back to
      // createdAt for a lobby nobody ever played in.
      const lastActivity =
        (room.get("startedAt") as number | undefined) ??
        (room.get("createdAt") as number | undefined) ??
        0;
      if (lastActivity > cutoff) continue;

      // recursiveDelete removes the document together with its results/ and
      // reactions/ subcollections, which a plain delete() would orphan.
      await db.recursiveDelete(room.ref);
      deleted++;
    }

    logger.info(`Cleanup: removed ${deleted} abandoned room(s) of ${rooms.size}`);
  }
);
