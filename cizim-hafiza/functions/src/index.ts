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
export async function runCleanupAbandonedRooms(): Promise<void> {
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

// Not currently deployed — see functions/DEPLOY.md. Cloud Functions require
// the Blaze plan regardless of how they're deployed, which this project is
// staying off of, so this schedule-based task instead runs as a plain script
// from a GitHub Actions cron (.github/workflows/league-scheduler.yml),
// calling runCleanupAbandonedRooms() directly with no Cloud Functions
// runtime involved. Kept here, wired up and ready, for the day this project
// does go Blaze — at which point delete the cron workflow and deploy this.
export const cleanupAbandonedRooms = onSchedule(
  { schedule: "every day 04:00", timeZone: "Europe/Istanbul" },
  runCleanupAbandonedRooms
);

// ---------------------------------------------------------------------------
// Weekly global league
// ---------------------------------------------------------------------------

/**
 * Calendar-month period id, matching LeaguePeriod.periodIdFor in the Android
 * app exactly: year*12 + (month - 1). The two MUST agree — a player's
 * profile is stamped with the app's id and this function filters on it.
 *
 * Evaluated in Istanbul, like every other schedule here. A player in another
 * timezone rolls over a few hours out of step with the table, which is a
 * cosmetic skew on a monthly number, and the only alternative — a per-player
 * month — cannot be aggregated at all.
 */
const LEAGUE_TIME_ZONE = "Europe/Istanbul";

interface IstanbulNow {
  year: number;
  month: number;   // 1-12
  day: number;
  hour: number;
  daysInMonth: number;
}

function istanbulNow(now: Date): IstanbulNow {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: LEAGUE_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    hourCycle: "h23",
  }).formatToParts(now);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value ?? 0);
  const year = get("year");
  const month = get("month");
  // Day 0 of the next month is the last day of this one.
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return { year, month, day: get("day"), hour: get("hour"), daysInMonth };
}

function periodIdFor(t: IstanbulNow): number {
  return t.year * 12 + (t.month - 1);
}

/**
 * The prize id for a period, derived rather than configured: the artwork is
 * stamped with its own month, so there is exactly one right answer and
 * nobody has to remember to set it. Matches AvatarFrame's
 * LEAGUE_CHAMPION_<year>_<month> constants and LeagueReward's FRAME: prefix.
 *
 * A month whose artwork this build of the app does not ship still gets an id
 * recorded; the app resolves it to nothing and shows no prize rather than
 * the wrong one, and a later update reveals it.
 */
function rewardIdFor(periodId: number): string {
  const year = Math.floor(periodId / 12);
  const month = (periodId % 12) + 1;
  return `FRAME:LEAGUE_CHAMPION_${year}_${String(month).padStart(2, "0")}`;
}

/** Entries in the published snapshot. A bot row carries no uid — see buildGlobalLeaderboard. */
interface LeagueRow {
  uid: string | null;
  nickname: string;
  periodXp: number;
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

/** Random XP a bot gains each time growth is applied — see [runBuildGlobalLeaderboard]. */
const BOT_GROWTH_MIN = 200;
const BOT_GROWTH_MAX = 1000;

/**
 * Minimum real time between two growth applications to the same bot. The
 * schedule this runs from fires every 6 hours; 5 gives headroom for a manual
 * or slightly-early re-run not to double a bot's growth, while never missing
 * a real 6-hour tick.
 */
const BOT_GROWTH_INTERVAL_MS = 5 * 60 * 60 * 1000;

interface BotState {
  nickname: string;
  periodXp: number;
  level: number;
}

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
 * **Bots accumulate, they are not recomputed from scratch.** Each bot's
 * identity (nickname, level) is deterministic from `periodId` and its own
 * index, exactly as before — the same bot never renames itself mid-month.
 * Its score, though, is carried forward from the previous snapshot and grows
 * by a random [BOT_GROWTH_MIN, BOT_GROWTH_MAX] every time this runs
 * (throttled by [BOT_GROWTH_INTERVAL_MS] so a manual or early re-run does not
 * double-grant growth). This is deliberately NOT capped below the real
 * podium the way an earlier version was: bots are meant to be real
 * competition — a player who stops playing gets overtaken, and the table
 * keeps moving even with no human activity at all. The actual prize is
 * unaffected either way: [runFinalizeLeaguePeriod] picks winners straight
 * from `users/`, never from this table, so a bot sitting in the visible top
 * three still cannot win anything.
 */
// Not currently deployed as a Cloud Function — see functions/DEPLOY.md and
// the note on runCleanupAbandonedRooms above. Runs from
// .github/workflows/league-scheduler.yml instead, on the same schedule.
export const buildGlobalLeaderboard = onSchedule(
  { schedule: "every 6 hours", timeZone: LEAGUE_TIME_ZONE },
  runBuildGlobalLeaderboard
);

export async function runBuildGlobalLeaderboard(): Promise<void> {
  const db = admin.firestore();
  const t = istanbulNow(new Date());
  const periodId = periodIdFor(t);

    const realSnapshot = await db
      .collection("users")
      .where("periodId", "==", periodId)
      .orderBy("periodXp", "desc")
      .limit(MAX_ENTRIES)
      .get();

    const real: LeagueRow[] = realSnapshot.docs.map((doc) => ({
      uid: doc.id,
      nickname: (doc.get("nickname") as string | undefined)?.trim() || "?",
      periodXp: (doc.get("periodXp") as number | undefined) ?? 0,
      level: (doc.get("level") as number | undefined) ?? 1,
      bot: false,
    }));

    // Carried INTO the snapshot rather than read separately by every client:
    // the reward of the week and last week's winners then cost nothing to
    // look at, because the table was going to be read anyway.
    const config = await db.doc("leaderboards/config").get();
    const previous = await db.doc("leaderboards/global").get();

    const previousPeriodId = previous.get("periodId") as number | undefined;
    const previousBots = (previous.get("bots") as BotState[] | undefined) ?? [];
    const previousBotsGrewAt = previous.get("botsGrewAt") as number | undefined;

    const samePeriod = previousPeriodId === periodId;
    const now = Date.now();
    // A new month starts every bot back at zero, same as a real player's own
    // periodXp — growth is then due immediately so the table is not all
    // zeroes right after the rollover.
    const growthDue =
      !samePeriod || previousBotsGrewAt === undefined || now - previousBotsGrewAt >= BOT_GROWTH_INTERVAL_MS;

    const bots: LeagueRow[] = [];
    const botStates: BotState[] = [];
    for (let i = 0; i < BOT_COUNT; i++) {
      const identity = seededRandom(periodId * 1_000 + i);
      const nickname = botNickname(identity);
      const level = Math.max(Math.round(2 + identity() * 60), 1);

      let periodXp = samePeriod ? previousBots[i]?.periodXp ?? 0 : 0;
      if (growthDue) {
        // Seeded by the growth tick rather than pure Math.random(): two
        // calls landing in the same throttle window (retries, a manual
        // re-run right after the scheduled one) compute the same increment
        // instead of each adding their own.
        const tick = Math.floor(now / BOT_GROWTH_INTERVAL_MS);
        const growth = seededRandom(tick * 104_729 + periodId * 97 + i);
        periodXp += BOT_GROWTH_MIN + Math.floor(growth() * (BOT_GROWTH_MAX - BOT_GROWTH_MIN + 1));
      }

      bots.push({ uid: null, nickname, periodXp, level, bot: true });
      botStates.push({ nickname, periodXp, level });
    }

    const entries = [...real, ...bots]
      .sort((a, b) => b.periodXp - a.periodXp || a.nickname.localeCompare(b.nickname))
      .slice(0, MAX_ENTRIES);

    await db.doc("leaderboards/global").set({
      periodId,
      generatedAt: now,
      daysRemaining: Math.max(t.daysInMonth - t.day, 0),
      // The month's own prize, unless the review panel has overridden it.
      rewardId: (config.get("rewardId") as string | undefined) ?? rewardIdFor(periodId),
      entries,
      bots: botStates,
      botsGrewAt: growthDue ? now : previousBotsGrewAt ?? now,
      // Written by finalizeLeaguePeriod; preserved here so a rebuild during
      // the week does not wipe the winners the app is still handing out.
      lastPeriod: previous.get("lastPeriod") ?? null,
    });

  logger.info(
    `League: ${real.length} real + ${bots.length} bot row(s) for period ${periodId}, growth ${growthDue ? "applied" : "skipped"}`
  );
}

/**
 * Closes the month that just ended and records its top three.
 *
 * Runs a few minutes after midnight on the first, while every profile still
 * carries LAST month's id and final score — a player's own device only resets
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
 *
 * Scheduled daily rather than for one exact moment on the 1st — the cron
 * this replaced fired weekly, a leftover from before the league moved to
 * calendar months, which meant it was closing "last month" fresh every
 * Monday instead of once at the actual boundary. A day is not a moment
 * either, and Firebase Scheduler's `timeZone` option (or, for the GitHub
 * Actions cron this currently runs from instead, no timezone support at
 * all) both make "the 1st in Istanbul" awkward to target exactly in UTC
 * cron fields. Running once a day and relying on the idempotency check
 * below sidesteps that entirely: [finishedPeriodId] is constant for every
 * day of a given month, so the write only actually happens once, on
 * whichever day this next runs on or after the real boundary.
 */
// Not currently deployed as a Cloud Function — see the note on
// runCleanupAbandonedRooms above. Runs from
// .github/workflows/league-scheduler.yml instead.
export const finalizeLeaguePeriod = onSchedule(
  { schedule: "every day 00:10", timeZone: LEAGUE_TIME_ZONE },
  runFinalizeLeaguePeriod
);

export async function runFinalizeLeaguePeriod(): Promise<void> {
  const db = admin.firestore();
  const finishedPeriodId = periodIdFor(istanbulNow(new Date())) - 1;

  const existing = await db.doc("leaderboards/global").get();
  if ((existing.get("lastPeriod") as { periodId?: number } | undefined)?.periodId === finishedPeriodId) {
    logger.info(`League: period ${finishedPeriodId} already finalized, skipping`);
    return;
  }

  const config = await db.doc("leaderboards/config").get();
    const rewardId = (config.get("rewardId") as string | undefined)
      ?? rewardIdFor(finishedPeriodId);

    const snapshot = await db
      .collection("users")
      .where("periodId", "==", finishedPeriodId)
      .orderBy("periodXp", "desc")
      .limit(3)
      .get();

    const winners = snapshot.docs
      .filter((doc) => ((doc.get("periodXp") as number | undefined) ?? 0) > 0)
      .map((doc, index) => ({
        uid: doc.id,
        nickname: (doc.get("nickname") as string | undefined)?.trim() || "?",
        periodXp: (doc.get("periodXp") as number | undefined) ?? 0,
        rank: index + 1,
      }));

    const batch = db.batch();
    for (const winner of winners) {
      batch.set(
        db.doc(`users/${winner.uid}/private/leagueAwards`),
        { [String(finishedPeriodId)]: { rank: winner.rank, rewardId, periodXp: winner.periodXp } },
        { merge: true }
      );
    }
    batch.set(
      db.doc("leaderboards/global"),
      { lastPeriod: { periodId: finishedPeriodId, rewardId, winners } },
      { merge: true }
    );
    await batch.commit();

  logger.info(`League: period ${finishedPeriodId} closed with ${winners.length} winner(s), prize ${rewardId}`);
}
