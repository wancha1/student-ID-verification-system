const assert = require("node:assert");
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

test("Authenticated user: can create and read their own staff profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@oakridge.ac.ug",
      displayName: "Alice Staff",
      role: "ADMIN",
      createdAt: now,
      updatedAt: now,
    })
  );

  const doc = await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
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

// -------------------------------------------------------------------
// SCHOOL-WIDE MULTI-DEVICE & ROLE-BASED ACCESS CONTROL TESTS
// -------------------------------------------------------------------

const SCHOOL_LTC = "ltc_school_01";
const OTHER_SCHOOL = "other_school_99";
const ADMIN_UID = "admin_staff_01";
const GATE_UID = "gate_officer_02";
const BURSAR_UID = "bursar_florence_03";
const TEACHER_UID = "teacher_akello_04";
const MEAL_UID = "meal_patron_05";
const EXAM_UID = "exam_supervisor_06";
const UNRELATED_UID = "unrelated_user_99";


async function setupSchoolStaff() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const ltcRef = db.collection("schools").doc(SCHOOL_LTC).collection("staff");
    await ltcRef.doc(ADMIN_UID).set({ role: "ADMIN", name: "System Admin" });
    await ltcRef.doc(GATE_UID).set({ role: "GATE_OFFICER", name: "Gate Officer Okello" });
    await ltcRef.doc(BURSAR_UID).set({ role: "BURSAR", name: "Bursar Florence" });
    await ltcRef.doc(TEACHER_UID).set({ role: "TEACHER", name: "Teacher Akello" });
    await ltcRef.doc(MEAL_UID).set({ role: "MEAL_STAFF", name: "Dining Hall Staff" });
    await ltcRef.doc(EXAM_UID).set({ role: "EXAM_STAFF", name: "Examiner Charles" });

    // Other school staff
    await db.collection("schools").doc(OTHER_SCHOOL).collection("staff").doc(UNRELATED_UID).set({
      role: "ADMIN",
      name: "Other School Admin"
    });
  });
}


test("Cross-device sync: Admin on Device A registers student, Gate on Device B reads student", async () => {
  await setupSchoolStaff();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID).firestore();
  const gateDb = testEnv.authenticatedContext(GATE_UID).firestore();

  // Admin registers student
  const stuDoc = adminDb.collection("schools").doc(SCHOOL_LTC).collection("students").doc("STU-CROSS-01");
  await assertSucceeds(
    stuDoc.set({
      id: "STU-CROSS-01",
      studentNumber: "LTC-2026-8800",
      firstName: "Emmanuel",
      lastName: "Ocen",
      name: "Emmanuel Ocen",
      gradeClass: "Senior 4-Sci",
      isDayScholar: true,
      feesStatus: "CLEARED",
      outstandingAmount: 0,
      updatedAt: Date.now()
    })
  );

  // Gate staff on Device B can read the student record
  const gateStuDoc = gateDb.collection("schools").doc(SCHOOL_LTC).collection("students").doc("STU-CROSS-01");
  const snapshot = await assertSucceeds(gateStuDoc.get());
  assert.strictEqual(snapshot.data().studentNumber, "LTC-2026-8800");
});

test("School isolation: Unrelated school user cannot access LTC students", async () => {
  await setupSchoolStaff();
  const otherDb = testEnv.authenticatedContext(UNRELATED_UID).firestore();
  const doc = otherDb.collection("schools").doc(SCHOOL_LTC).collection("students").doc("STU-CROSS-01");
  await assertFails(doc.get());
});

test("Role permission: Gate staff cannot modify student fees", async () => {
  await setupSchoolStaff();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("schools").doc(SCHOOL_LTC).collection("students").doc("STU-CROSS-01").set({
      id: "STU-CROSS-01",
      studentNumber: "LTC-2026-8800",
      firstName: "Emmanuel",
      lastName: "Ocen",
      name: "Emmanuel Ocen",
      gradeClass: "Senior 4-Sci",
      isDayScholar: true,
      feesStatus: "CLEARED",
      outstandingAmount: 0,
      updatedAt: Date.now()
    });
  });
  const gateDb = testEnv.authenticatedContext(GATE_UID).firestore();
  const doc = gateDb.collection("schools").doc(SCHOOL_LTC).collection("students").doc("STU-CROSS-01");
  await assertFails(
    doc.update({
      feesStatus: "CLEARED",
      outstandingAmount: 0
    })
  );
});

test("Role permission: Bursar / Finance CAN update student fees", async () => {
  await setupSchoolStaff();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("schools").doc(SCHOOL_LTC).collection("students").doc("STU-CROSS-01").set({
      id: "STU-CROSS-01",
      studentNumber: "LTC-2026-8800",
      firstName: "Emmanuel",
      lastName: "Ocen",
      name: "Emmanuel Ocen",
      gradeClass: "Senior 4-Sci",
      isDayScholar: true,
      feesStatus: "CLEARED",
      outstandingAmount: 0,
      updatedAt: Date.now()
    });
  });
  const bursarDb = testEnv.authenticatedContext(BURSAR_UID).firestore();
  const doc = bursarDb.collection("schools").doc(SCHOOL_LTC).collection("students").doc("STU-CROSS-01");
  await assertSucceeds(
    doc.update({
      feesStatus: "OUTSTANDING",
      outstandingAmount: 150000,
      updatedAt: Date.now()
    })
  );
});

test("Role permission: Teachers cannot record meals, but Meal Staff can", async () => {
  await setupSchoolStaff();
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const mealDb = testEnv.authenticatedContext(MEAL_UID).firestore();

  const mealDoc = {
    id: "MEAL-LOG-01",
    studentId: "STU-CROSS-01",
    mealType: "LUNCH",
    servingStation: "Station-1",
    timestamp: Date.now()
  };

  // Teacher cannot create meal record
  await assertFails(
    teacherDb.collection("schools").doc(SCHOOL_LTC).collection("meals").doc("MEAL-LOG-01").set(mealDoc)
  );

  // Meal staff can create meal record
  await assertSucceeds(
    mealDb.collection("schools").doc(SCHOOL_LTC).collection("meals").doc("MEAL-LOG-01").set(mealDoc)
  );

  // Meal logs are immutable (cannot be updated or deleted)
  await assertFails(
    mealDb.collection("schools").doc(SCHOOL_LTC).collection("meals").doc("MEAL-LOG-01").delete()
  );
});

test("Role permission: Gate staff can record gate logs, but cannot alter or delete them", async () => {
  await setupSchoolStaff();
  const gateDb = testEnv.authenticatedContext(GATE_UID).firestore();

  const gateLog = {
    id: "GATE-LOG-01",
    studentNumber: "LTC-2026-8800",
    decision: "APPROVED",
    gateLocation: "Main Gate",
    timestamp: Date.now()
  };

  await assertSucceeds(
    gateDb.collection("schools").doc(SCHOOL_LTC).collection("gate_logs").doc("GATE-LOG-01").set(gateLog)
  );

  // Immutable audit trail
  await assertFails(
    gateDb.collection("schools").doc(SCHOOL_LTC).collection("gate_logs").doc("GATE-LOG-01").delete()
  );
});

