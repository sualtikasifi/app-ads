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

// ---------------------------------------------------------------------------
// Weekly global league
// ---------------------------------------------------------------------------

/**
 * Monday-aligned week id, matching WeeklyLeague.weekIdFor in the Android app
 * exactly — epoch day 0 was a Thursday, so the +3 moves the bucket boundary
 * onto Monday. The two MUST agree: a player's profile is stamped with the
 * app's week id and this function filters on it.
 *
 * Evaluated in Istanbul, like every other schedule here. A player in another
 * timezone rolls over a few hours out of step with the table, which is a
 * cosmetic skew on a weekly number and the only alternative — a per-player
 * week — cannot be aggregated at all.
 */
const LEAGUE_TIME_ZONE = "Europe/Istanbul";

function istanbulParts(now: Date): { epochDay: number; hour: number } {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: LEAGUE_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0);
  const epochDay = Math.floor(
    Date.UTC(get("year"), get("month") - 1, get("day")) / 86_400_000
  );
  return { epochDay, hour: get("hour") };
}

function weekIdFor(epochDay: number): number {
  return Math.floor((epochDay + 3) / 7);
}

function weekStartEpochDay(weekId: number): number {
  return weekId * 7 - 3;
}

/** Entries in the published snapshot. A bot row carries no uid — see buildGlobalLeaderboard. */
interface LeagueRow {
  uid: string | null;
  nickname: string;
  weeklyXp: number;
  level: number;
  bot: boolean;
}

/**
 * Deterministic PRNG (mulberry32). Bots must produce the SAME identity and a
 * monotonically growing score on every rebuild — a bot whose name or score
 * jumped around between refreshes would be obvious within a day.
 */
function seededRandom(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const BOT_NAME_PREFIX = [
  "kalem", "boya", "fırça", "çizgi", "gölge", "eskiz", "silgi", "palet",
  "mürekkep", "tuval", "karakalem", "desen", "kontur", "leke", "perspektif",
];
const BOT_NAME_SUFFIX = [
  "usta", "avcı", "kaşif", "ustası", "delisi", "canavarı", "sever", "krali",
  "meraklısı", "yolcusu", "gezgini", "sihirbazı",
];

function botNickname(random: () => number): string {
  const prefix = BOT_NAME_PREFIX[Math.floor(random() * BOT_NAME_PREFIX.length)];
  const suffix = BOT_NAME_SUFFIX[Math.floor(random() * BOT_NAME_SUFFIX.length)];
  const number = Math.floor(random() * 90) + 10;
  return `${prefix}${suffix}${number}`;
}

/** How many filler rows the table carries, and the ceiling on the whole published list. */
const BOT_COUNT = 24;
const MAX_ENTRIES = 100;

/**
 * What bots aim for in a week when there is no real activity to scale
 * against at all — an empty table of zeroes is worse than no table.
 */
const FALLBACK_BOT_CEILING = 900;

/**
 * Publishes the whole global table as ONE document, every six hours.
 *
 * The alternative — every client querying users/ directly — costs one read
 * per listed player per viewer. At a hundred listed players and four opens a
 * day that is forty thousand reads a day for a hundred players, which is
 * most of the free daily quota spent on a single screen. This way a viewer
 * pays ONE read, and the hundred reads happen here, four times a day, no
 * matter how many people look.
 *
 * **Bots exist only in this document.** Nothing is ever written to users/ for
 * them: a fake profile there would surface in friend search, in duels and in
 * every other place that reads a real account, and would be discovered the
 * first time somebody tried to add one. They carry no uid for the same
 * reason — the app refuses to open a profile for a row without one.
 *
 * Bot scores are capped below the lowest real player still holding a podium
 * place, so a player who is in the real top three is also in the VISIBLE top
 * three. Without that the prize would go to someone the table shows in
 * seventh, which is incoherent, and the screen would read as twenty
 * strangers standing between you and a reward you cannot reach.
 */
export const buildGlobalLeaderboard = onSchedule(
  { schedule: "every 6 hours", timeZone: LEAGUE_TIME_ZONE },
  async () => {
    const db = admin.firestore();
    const { epochDay, hour } = istanbulParts(new Date());
    const weekId = weekIdFor(epochDay);

    const realSnapshot = await db
      .collection("users")
      .where("weekId", "==", weekId)
      .orderBy("weeklyXp", "desc")
      .limit(MAX_ENTRIES)
      .get();

    const real: LeagueRow[] = realSnapshot.docs.map((doc) => ({
      uid: doc.id,
      nickname: (doc.get("nickname") as string | undefined)?.trim() || "?",
      weeklyXp: (doc.get("weeklyXp") as number | undefined) ?? 0,
      level: (doc.get("level") as number | undefined) ?? 1,
      bot: false,
    }));

    const active = real.filter((row) => row.weeklyXp > 0);
    // Strictly below the lowest real player who is currently on the podium,
    // so no bot can displace one. With fewer than three active players there
    // is no podium to protect and the lowest active score serves instead.
    const podiumFloor =
      active.length >= 3 ? active[2].weeklyXp : active[active.length - 1]?.weeklyXp;
    const ceiling =
      podiumFloor !== undefined ? Math.max(podiumFloor - 1, 1) : FALLBACK_BOT_CEILING;

    // Where we are through the week, so a bot's score grows between refreshes
    // the way a player's does rather than appearing all at once on Monday.
    const daysIntoWeek = epochDay - weekStartEpochDay(weekId);
    const progress = Math.min((daysIntoWeek * 24 + hour + 1) / 168, 1);

    const bots: LeagueRow[] = [];
    for (let i = 0; i < BOT_COUNT; i++) {
      const random = seededRandom(weekId * 1_000 + i);
      const nickname = botNickname(random);
      // A spread rather than a straight line, so the table does not look
      // like a generated ladder: each bot takes a decreasing share of the
      // ceiling with its own jitter.
      const share = (1 - i / BOT_COUNT) * (0.55 + random() * 0.45);
      const target = Math.max(Math.round(ceiling * share), 1);
      bots.push({
        uid: null,
        nickname,
        weeklyXp: Math.max(Math.round(target * progress), 1),
        level: Math.max(Math.round(2 + random() * 60), 1),
        bot: true,
      });
    }

    const entries = [...real, ...bots]
      .sort((a, b) => b.weeklyXp - a.weeklyXp || a.nickname.localeCompare(b.nickname))
      .slice(0, MAX_ENTRIES);

    // Carried INTO the snapshot rather than read separately by every client:
    // the reward of the week and last week's winners then cost nothing to
    // look at, because the table was going to be read anyway.
    const config = await db.doc("leaderboards/config").get();
    const previous = await db.doc("leaderboards/global").get();

    await db.doc("leaderboards/global").set({
      weekId,
      generatedAt: Date.now(),
      daysRemaining: Math.max(weekStartEpochDay(weekId + 1) - epochDay, 0),
      rewardId: (config.get("rewardId") as string | undefined) ?? null,
      entries,
      // Written by finalizeWeeklyLeague; preserved here so a rebuild during
      // the week does not wipe the winners the app is still handing out.
      lastWeek: previous.get("lastWeek") ?? null,
    });

    logger.info(
      `League: ${real.length} real + ${bots.length} bot row(s) for week ${weekId}, ceiling ${ceiling}`
    );
  }
);

/**
 * Closes the week that just ended and records its top three.
 *
 * Runs a few minutes after midnight on Monday, while every profile still
 * carries LAST week's id and final score — a player's own device only resets
 * its weekly total the next time it is opened, which is exactly what makes
 * the final standings still readable here.
 *
 * **Bots cannot win.** Only real accounts are considered, so the prize always
 * reaches a person even in a week where filler rows sat high in the table.
 *
 * Winners are recorded twice on purpose: in the public snapshot, where the
 * app finds them for free on a screen it was already reading, and durably
 * under each winner's own profile, so somebody who does not open the app for
 * a fortnight still collects what they won.
 */
export const finalizeWeeklyLeague = onSchedule(
  { schedule: "5 0 * * 1", timeZone: LEAGUE_TIME_ZONE },
  async () => {
    const db = admin.firestore();
    const { epochDay } = istanbulParts(new Date());
    const finishedWeekId = weekIdFor(epochDay) - 1;

    const config = await db.doc("leaderboards/config").get();
    const rewardId = (config.get("rewardId") as string | undefined) ?? null;
    if (!rewardId) {
      logger.warn(`League: week ${finishedWeekId} closed with no reward configured`);
    }

    const snapshot = await db
      .collection("users")
      .where("weekId", "==", finishedWeekId)
      .orderBy("weeklyXp", "desc")
      .limit(3)
      .get();

    const winners = snapshot.docs
      .filter((doc) => ((doc.get("weeklyXp") as number | undefined) ?? 0) > 0)
      .map((doc, index) => ({
        uid: doc.id,
        nickname: (doc.get("nickname") as string | undefined)?.trim() || "?",
        weeklyXp: (doc.get("weeklyXp") as number | undefined) ?? 0,
        rank: index + 1,
      }));

    const batch = db.batch();
    for (const winner of winners) {
      batch.set(
        db.doc(`users/${winner.uid}/private/leagueAwards`),
        { [String(finishedWeekId)]: { rank: winner.rank, rewardId, weeklyXp: winner.weeklyXp } },
        { merge: true }
      );
    }
    batch.set(
      db.doc("leaderboards/global"),
      { lastWeek: { weekId: finishedWeekId, rewardId, winners } },
      { merge: true }
    );
    await batch.commit();

    logger.info(`League: week ${finishedWeekId} closed with ${winners.length} winner(s)`);
  }
);
