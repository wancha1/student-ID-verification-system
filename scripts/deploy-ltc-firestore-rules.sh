#!/usr/bin/env bash
set -euo pipefail

# Deploys only the LTC Firestore rules target after verifying firebase.json.
# Requires the Firebase CLI to be installed and an authorized operator to be logged in.
EXPECTED_PROJECT="gen-lang-client-0207053136"
EXPECTED_DATABASE="ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba"

command -v firebase >/dev/null 2>&1 || {
  echo "Firebase CLI is required. Install it with: npm install -g firebase-tools" >&2
  exit 1
}
command -v node >/dev/null 2>&1 || {
  echo "Node.js is required to validate firebase.json." >&2
  exit 1
}
test -f firebase.json || {
  echo "Run this script from the repository root; firebase.json was not found." >&2
  exit 1
}
test -f firestore.rules || {
  echo "firestore.rules was not found in the repository root." >&2
  exit 1
}

node <<'NODE'
const fs = require("node:fs");
const config = JSON.parse(fs.readFileSync("firebase.json", "utf8"));
const targets = config.firestore;
const expectedDatabase = "ai-studio-android-oakridge-c82a4d82-3596-4f68-a7ca-a7d4c71aaaba";
if (!Array.isArray(targets) || targets.length !== 1) {
  console.error("Refusing deployment: firebase.json must contain exactly one Firestore target.");
  process.exit(1);
}
if (targets[0].database !== expectedDatabase || targets[0].rules !== "firestore.rules") {
  console.error("Refusing deployment: Firestore target or rules file does not match the authorized LTC configuration.");
  process.exit(1);
}
console.log("Verified one Firestore rules target:", targets[0].database);
NODE

echo "Project:   ${EXPECTED_PROJECT}"
echo "Database:  ${EXPECTED_DATABASE}"
echo "Rules:     firestore.rules"
echo "This will publish the repository's current rules to the configured database."
read -r -p "Type DEPLOY-LTC-RULES to continue: " confirmation
if [[ "$confirmation" != "DEPLOY-LTC-RULES" ]]; then
  echo "Cancelled; no deployment was attempted."
  exit 1
fi

firebase deploy --only firestore:rules --project "$EXPECTED_PROJECT"
