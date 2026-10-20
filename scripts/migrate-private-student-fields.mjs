/**
 * One-time migration of sensitive student fields out of schools/{schoolId}/students.
 *
 * Default is DRY RUN. Set --apply only after confirming the Firebase project/database,
 * backing up the database, and reviewing the dry-run count.
 *
 * Required environment:
 *   GOOGLE_CLOUD_PROJECT=<real Firebase project ID>
 *   FIRESTORE_DATABASE_ID=<actual named Firestore database ID>
 *   GOOGLE_APPLICATION_CREDENTIALS=<path to a trusted Admin SDK credential file>
 *
 * Run with a locally installed firebase-admin package:
 *   node scripts/migrate-private-student-fields.mjs
 *   node scripts/migrate-private-student-fields.mjs --apply
 */
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const projectId = process.env.GOOGLE_CLOUD_PROJECT;
const databaseId = process.env.FIRESTORE_DATABASE_ID;
const schoolId = "ltc_school_01";
const authorizedDatabaseId = "ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba";
const apply = process.argv.includes("--apply");
if (!projectId || !databaseId) {
  throw new Error("Set GOOGLE_CLOUD_PROJECT and FIRESTORE_DATABASE_ID after confirming the real LTC Firebase project.");
}
if (databaseId !== authorizedDatabaseId) {
  throw new Error("Refusing to migrate: FIRESTORE_DATABASE_ID does not match the owner-authorized LTC database.");
}

const app = initializeApp({ credential: applicationDefault(), projectId });
const db = getFirestore(app, databaseId);
const students = await db.collection("schools").doc(schoolId).collection("students").get();
const privateFields = ["guardianName", "guardianPhone", "emergencyContact", "notes"];
const targets = students.docs.filter((doc) => privateFields.some((field) => doc.get(field) !== undefined));
console.log(`Project=${projectId}; database=${databaseId}; school=${schoolId}`);
console.log(`Student documents=${students.size}; documents needing private-field migration=${targets.length}; mode=${apply ? "APPLY" : "DRY RUN"}`);
if (!apply) {
  console.log("No data changed. Review the count, back up the database, then rerun with --apply.");
  process.exit(0);
}

for (let offset = 0; offset < targets.length; offset += 200) {
  const chunk = targets.slice(offset, offset + 200);
  const batch = db.batch();
  const migratedAt = Date.now();
  for (const doc of chunk) {
    const data = doc.data();
    const privateRef = db.collection("schools").doc(schoolId).collection("student_private").doc(doc.id);
    const privateData = { studentId: doc.id, updatedAt: migratedAt };
    for (const field of privateFields) {
      if (data[field] !== undefined) privateData[field] = data[field];
    }
    batch.set(privateRef, privateData, { merge: true });
    const publicUpdate = { updatedAt: migratedAt };
    for (const field of privateFields) {
      if (data[field] !== undefined) publicUpdate[field] = FieldValue.delete();
    }
    batch.update(doc.ref, publicUpdate);
  }
  await batch.commit();
  console.log(`Migrated ${Math.min(offset + chunk.length, targets.length)} / ${targets.length}`);
}
console.log("Migration complete. Verify role-based access and client sync before deploying rules.");
