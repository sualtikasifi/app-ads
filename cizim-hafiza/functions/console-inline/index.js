// Plain-JS mirror of ../src/index.ts, kept only for deploying via the
// Google Cloud Console web UI's "Inline editor" (no local machine / Firebase
// CLI needed — see ../DEPLOY.md's "Bilgisayarsız / tarayıcıdan deploy"
// section). If ../src/index.ts ever changes, update this file to match.

const { onDocumentCreated } = require("firebase-functions/v2/firestore");
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
