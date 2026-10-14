const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read staff profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can create and read a least-privilege staff profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@oakridge.ac.ug",
      displayName: "Alice Staff",
      role: "GATE_OFFICER",
      createdAt: now,
      updatedAt: now,
    })
  );

  const doc = await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});


test("Authenticated user cannot assign themselves an elevated role", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@oakridge.ac.ug",
      displayName: "Alice Staff",
      role: "ADMIN",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Authenticated user cannot change their role after enrollment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@oakridge.ac.ug",
      displayName: "Alice Staff",
      role: "GATE_OFFICER",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "ADMIN",
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation: Bob cannot read Alice's student records", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const now = new Date();
    await context.firestore().collection("users").doc(ALICE_UID).collection("students").doc("stu_001").set({
      userId: ALICE_UID,
      studentNumber: "LTC-2026-0001",
      firstName: "Michael",
      lastName: "Kasule",
      gradeClass: "Senior 4-A",
      isDayScholar: true,
      feesStatus: "CLEARED",
      outstandingAmount: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_001").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("students").get());
});

test("Authenticated user: can create and list their own student records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("students").doc("stu_001").set({
      userId: ALICE_UID,
      studentNumber: "LTC-2026-0001",
      firstName: "Michael",
      lastName: "Kasule",
      gradeClass: "Senior 4-A",
      isDayScholar: true,
      feesStatus: "CLEARED",
      outstandingAmount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("students").get());
});

test("Scan logs: append-only, cannot be deleted or updated", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scan_logs").doc("log_001").set({
      userId: ALICE_UID,
      studentNumber: "LTC-2026-0001",
      studentName: "Michael Kasule",
      decision: "APPROVED",
      gatePoint: "Main Gate",
      scannedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("scan_logs").doc("log_001").update({
      decision: "DENIED",
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("scan_logs").doc("log_001").delete()
  );
});
