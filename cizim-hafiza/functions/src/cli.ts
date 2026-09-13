/**
 * Runs one of the league's scheduled tasks as a plain script — no Cloud
 * Functions runtime involved — so they can run from a GitHub Actions cron
 * instead. Cloud Functions require the Blaze plan regardless of how they're
 * deployed, which this project is deliberately staying off of; these three
 * tasks are all schedule-based (nothing here reacts to a live Firestore
 * write), so a cron script is a straight substitute; only the two
 * event-triggered functions in index.ts (onInviteCreated, clampImpossibleScores)
 * genuinely need Cloud Functions and stay undeployed until that changes.
 *
 * Authenticates the same way the rules/indexes deploy does: `admin.initializeApp()`
 * reads GOOGLE_APPLICATION_CREDENTIALS, which .github/workflows/league-scheduler.yml
 * points at a file written from the FIREBASE_SERVICE_ACCOUNT secret.
 *
 * Importing "./index" both defines the (undeployed) Cloud Functions and runs
 * its top-level `admin.initializeApp()` — safe here since a plain script
 * never touches the Cloud Functions runtime those definitions belong to.
 */
import {
  runBuildGlobalLeaderboard,
  runCleanupAbandonedRooms,
  runFinalizeLeaguePeriod,
} from "./index";

const tasks: Record<string, () => Promise<void>> = {
  "build-global-leaderboard": runBuildGlobalLeaderboard,
  "finalize-league-period": runFinalizeLeaguePeriod,
  "cleanup-abandoned-rooms": runCleanupAbandonedRooms,
};

const taskName = process.argv[2];
const run = tasks[taskName];

if (!run) {
  console.error(`Unknown task "${taskName}". Expected one of: ${Object.keys(tasks).join(", ")}`);
  process.exit(1);
}

run()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
