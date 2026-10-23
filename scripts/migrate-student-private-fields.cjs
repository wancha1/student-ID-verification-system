#!/usr/bin/env node
"use strict";

/**
 * One-time migration of sensitive student fields out of the shared roster.
 *
 * Safety: dry-run by default. Supply --apply only after confirming the exact LTC
 * Google Cloud project and named Firestore database. Uses Application Default
 * Credentials; never embed a service-account key in this repository.
 *
 * Required environment:
 *   GOOGLE_CLOUD_PROJECT=<verified LTC Firebase project ID>
 *   FIRESTORE_DATABASE_ID=<verified Firestore database ID>
 *
 * Install firebase-admin in a trusted administrator workstation environment.
 */
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

const SCHOOL_ID = "ltc_school_01";
const AUTHORIZED_PROJECT_ID = "gen-lang-client-0207053136";
const AUTHORIZED_DATABASE_ID =
  "ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba";
const PRIVATE_FIELDS = ["guardianName", "guardianPhone", "emergencyContact", "notes"];
const applyChanges = process.argv.includes("--apply");

async function main() {
  const projectId = (process.env.GOOGLE_CLOUD_PROJECT || "").trim();
  const databaseId = (process.env.FIRESTORE_DATABASE_ID || "").trim();
  if (!projectId || !databaseId) {
    throw new Error(
      "Set GOOGLE_CLOUD_PROJECT and FIRESTORE_DATABASE_ID to the verified LTC project/database."
    );
  }
  if (projectId !== AUTHORIZED_PROJECT_ID || databaseId !== AUTHORIZED_DATABASE_ID) {
    throw new Error(
      "Refusing to run: GOOGLE_CLOUD_PROJECT and FIRESTORE_DATABASE_ID must both match the owner-authorized LTC resources."
    );
  }

  const app = initializeApp({ credential: applicationDefault(), projectId });
  try {
    const db = getFirestore(app, databaseId);
    const studentsRef = db.collection("schools").doc(SCHOOL_ID).collection("students");
    const snapshot = await studentsRef.get();

    let affected = 0;
    let batch = db.batch();
    let writes = 0;
    for (const doc of snapshot.docs) {
      const source = doc.data();
      const privateData = {};
      for (const field of PRIVATE_FIELDS) {
        if (Object.prototype.hasOwnProperty.call(source, field)) {
          privateData[field] = source[field];
        }
      }
      if (Object.keys(privateData).length === 0) continue;

      affected += 1;
      if (!applyChanges) continue;

      privateData.studentId = doc.id;
      privateData.updatedAt = source.updatedAt || Date.now();
      const privateRef = db.collection("schools").doc(SCHOOL_ID)
        .collection("student_private").doc(doc.id);
      batch.set(privateRef, privateData, { merge: true });

      const removal = {};
      for (const field of PRIVATE_FIELDS) {
        if (Object.prototype.hasOwnProperty.call(source, field)) {
          removal[field] = FieldValue.delete();
        }
      }
      batch.update(doc.ref, removal);
      writes += 2;

      // Two writes per student; stay below Firestore's 500-write batch limit.
      if (writes >= 400) {
        await batch.commit();
        batch = db.batch();
        writes = 0;
      }
    }

    if (applyChanges && writes > 0) await batch.commit();
    if (applyChanges) {
      // The flag is the release gate used by Firestore rules to allow school-wide
      // roster queries. Set it only after verifying no shared record retains private data.
      const verification = await studentsRef.get();
      const remainingPrivateRecords = verification.docs.filter((doc) =>
        PRIVATE_FIELDS.some((field) => Object.prototype.hasOwnProperty.call(doc.data(), field))
      );
      if (remainingPrivateRecords.length > 0) {
        throw new Error(
          `Migration verification failed: ${remainingPrivateRecords.length} shared student record(s) still contain private fields. Roster access remains blocked.`
        );
      }
      await db.collection("schools").doc(SCHOOL_ID).set({
        studentPrivacyMigrationComplete: true,
        privacyMigrationCompletedAt: FieldValue.serverTimestamp()
      }, { merge: true });
    }

    console.log(JSON.stringify({
      mode: applyChanges ? "APPLIED" : "DRY_RUN",
      projectId,
      databaseId,
      studentsScanned: snapshot.size,
      studentRecordsWithPrivateFields: affected,
      message: applyChanges
        ? "Migration finished. Verify the result before deploying restrictive rules."
        : "No data changed. Review the count, then rerun with --apply to migrate."
    }, null, 2));
  } finally {
    await app.delete();
  }
}

main().catch((error) => {
  console.error(error.message || error);
  process.exitCode = 1;
});
