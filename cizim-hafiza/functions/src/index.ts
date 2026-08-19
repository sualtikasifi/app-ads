import { onDocumentCreated } from "firebase-functions/v2/firestore";
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
