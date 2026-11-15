#!/usr/bin/env node
"use strict";

/**
 * Reconciles one existing LTC staff profile with Firebase Auth custom claims.
 *
 * Safe by default: without --apply this script only prints the proposed change.
 * Run only from a trusted workstation/server with Firebase Admin SDK credentials.
 * It never creates users or staff documents and never changes Firestore profiles.
 *
 * Required environment:
 *   GOOGLE_CLOUD_PROJECT=gen-lang-client-0207053136
 *   FIRESTORE_DATABASE_ID=ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba
 *
 * Preview:
 *   node scripts/migrate-ltc-staff-claims.cjs --uid FIREBASE_AUTH_UID
 * Apply:
 *   node scripts/migrate-ltc-staff-claims.cjs --uid FIREBASE_AUTH_UID --apply --confirm-authorization
 */
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

const PROJECT_ID = "gen-lang-client-0207053136";
const DATABASE_ID = "ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba";
const SCHOOL_ID = "ltc_school_01";
const ALLOWED_ROLES = new Set([
  "ADMIN", "HEAD_TEACHER", "GATE_OFFICER", "MEAL_STAFF"
]);

function argument(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : "";
}

async function main() {
  const projectId = (process.env.GOOGLE_CLOUD_PROJECT || "").trim();
  const databaseId = (process.env.FIRESTORE_DATABASE_ID || "").trim();
  const uid = argument("--uid").trim();
  const apply = process.argv.includes("--apply");
  const confirmed = process.argv.includes("--confirm-authorization");

  if (projectId !== PROJECT_ID || databaseId !== DATABASE_ID) {
    throw new Error("Refusing to run: set GOOGLE_CLOUD_PROJECT and FIRESTORE_DATABASE_ID to the exact authorized LTC resources.");
  }
  if (!uid) {
    throw new Error("Required argument: --uid <existing Firebase Auth UID>.");
  }
  if (apply && !confirmed) {
    throw new Error("No changes made. Obtain school-owner authorization and include --confirm-authorization with --apply.");
  }

  const app = initializeApp({ credential: applicationDefault(), projectId });
  try {
    const auth = getAuth(app);
    const db = getFirestore(app, DATABASE_ID);
    const user = await auth.getUser(uid);
    const staffRef = db.collection("schools").doc(SCHOOL_ID)
      .collection("staff").doc(uid);
    const staffSnapshot = await staffRef.get();

    if (!staffSnapshot.exists) {
      throw new Error("No existing LTC staff document was found for this UID. Nothing was changed.");
    }

    const staff = staffSnapshot.data() || {};
    const role = String(staff.role || "").trim().toUpperCase();
    if (!ALLOWED_ROLES.has(role)) {
      throw new Error(`Unsupported or missing V1 staff role: ${role || "(empty)"}. Nothing was changed.`);
    }
    if (staff.email && user.email &&
        String(staff.email).trim().toLowerCase() !== user.email.toLowerCase()) {
      throw new Error("The Firestore staff email does not match the Firebase Authentication user's email. Resolve the identity mismatch manually.");
    }

    const active = staff.active === true && user.disabled !== true;
    const claims = {
      ...(user.customClaims || {}),
      ltcSchoolId: SCHOOL_ID,
      ltcRole: role,
      ltcActive: active
    };

    const result = {
      mode: apply ? "APPLY" : "DRY_RUN",
      projectId: PROJECT_ID,
      databaseId: DATABASE_ID,
      schoolId: SCHOOL_ID,
      uid,
      email: user.email || null,
      role,
      firestoreActive: staff.active === true,
      authDisabled: user.disabled === true,
      claimsToSet: {
        ltcSchoolId: claims.ltcSchoolId,
        ltcRole: claims.ltcRole,
        ltcActive: claims.ltcActive
      },
      unrelatedCustomClaimsPreserved: true
    };

    if (apply) {
      await auth.setCustomUserClaims(uid, claims);
      result.result = "UPDATED";
      result.nextStep = "Have the staff member sign out and sign in again, or force-refresh the ID token, before testing Storage access.";
    } else {
      result.result = "NO_CHANGES";
      result.nextStep = "Review the proposed claims. To apply, rerun with --apply --confirm-authorization.";
    }
    console.log(JSON.stringify(result, null, 2));
  } finally {
    await app.delete();
  }
}

main().catch((error) => {
  console.error(error.message || error);
  process.exitCode = 1;
});
