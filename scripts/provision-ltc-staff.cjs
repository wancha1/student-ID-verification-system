#!/usr/bin/env node
"use strict";

/**
 * Creates one LTC staff profile for an existing Firebase Authentication user.
 *
 * Run only from a trusted workstation/server with Firebase Admin SDK Application
 * Default Credentials and explicit school-owner authorization. This script never
 * creates Auth users, overwrites existing staff profiles, or embeds credentials.
 *
 * Required environment:
 *   GOOGLE_CLOUD_PROJECT=gen-lang-client-0207053136
 *   FIRESTORE_DATABASE_ID=ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba
 *
 * Example:
 *   node scripts/provision-ltc-staff.cjs --uid UID --email EMAIL --name "Staff Name" --role ADMIN --confirm-authorization
 */
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

const PROJECT_ID = "gen-lang-client-0207053136";
const DATABASE_ID = "ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba";
const SCHOOL_ID = "ltc_school_01";
const ALLOWED_ROLES = new Set([
  "ADMIN", "HEAD_TEACHER", "BURSAR", "GATE_OFFICER", "SECURITY_HEAD",
  "MATRON_PATRON", "MEAL_STAFF", "DIRECTOR_OF_STUDIES", "TEACHER", "EXAM_STAFF"
]);

function argument(name) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : "";
}

async function main() {
  const projectId = (process.env.GOOGLE_CLOUD_PROJECT || "").trim();
  const databaseId = (process.env.FIRESTORE_DATABASE_ID || "").trim();
  const uid = argument("--uid").trim();
  const email = argument("--email").trim().toLowerCase();
  const name = argument("--name").trim();
  const role = argument("--role").trim().toUpperCase();
  const explicitlyConfirmed = process.argv.includes("--confirm-authorization");

  if (projectId !== PROJECT_ID || databaseId !== DATABASE_ID) {
    throw new Error("Refusing to run: set GOOGLE_CLOUD_PROJECT and FIRESTORE_DATABASE_ID to the exact authorized LTC resources.");
  }
  if (!uid || !email || !name || !ALLOWED_ROLES.has(role)) {
    throw new Error("Required arguments: --uid <Auth UID> --email <Auth email> --name <staff name> --role <supported role>.");
  }
  if (!explicitlyConfirmed) {
    throw new Error("No changes made. Obtain school-owner authorization, then include --confirm-authorization.");
  }

  const app = initializeApp({ credential: applicationDefault(), projectId });
  try {
    const auth = getAuth(app);
    const user = await auth.getUser(uid);
    if (!user.email || user.email.toLowerCase() !== email) {
      throw new Error("The supplied email does not match the existing Firebase Authentication user's email.");
    }
    if (user.disabled) {
      throw new Error("Refusing to provision a disabled Firebase Authentication account.");
    }

    const db = getFirestore(app, DATABASE_ID);
    const ref = db.collection("schools").doc(SCHOOL_ID).collection("staff").doc(uid);
    await ref.create({
      name,
      email: user.email,
      role,
      active: true,
      hasExplicitFinanceAccess: false
    });

    console.log(JSON.stringify({
      result: "CREATED",
      projectId: PROJECT_ID,
      databaseId: DATABASE_ID,
      schoolId: SCHOOL_ID,
      uid,
      email: user.email,
      role
    }, null, 2));
  } finally {
    await app.delete();
  }
}

main().catch((error) => {
  console.error(error.message || error);
  process.exitCode = 1;
});
