const assert = require("node:assert");
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-ltc-v1";
const SCHOOL_ID = "ltc_school_01";
const USERS = {
  admin: "admin_v1",
  head: "head_v1",
  gate: "gate_v1",
  meal: "meal_v1",
  removedRole: "bursar_legacy",
  inactive: "inactive_v1",
};

const [emulatorHost, emulatorPortStr] =
  (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("./firestore.rules", "utf8"),
      host: emulatorHost,
      port: parseInt(emulatorPortStr, 10),
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("schools").doc(SCHOOL_ID).set({
      studentPrivacyMigrationComplete: true,
    });
    const staff = db.collection("schools").doc(SCHOOL_ID).collection("staff");
    for (const [uid, role] of [
      [USERS.admin, "ADMIN"],
      [USERS.head, "HEAD_TEACHER"],
      [USERS.gate, "GATE_OFFICER"],
      [USERS.meal, "MEAL_STAFF"],
      [USERS.removedRole, "BURSAR"],
      [USERS.inactive, "GATE_OFFICER"],
    ]) {
      await staff.doc(uid).set({
        name: uid,
        email: uid + "@ltc.ac.ug",
        role,
        active: uid !== USERS.inactive,
      });
    }
  });
});

function schoolDb(uid) {
  return testEnv.authenticatedContext(uid).firestore()
    .collection("schools").doc(SCHOOL_ID);
}

function studentRecord(id = "STU-001") {
  return {
    id,
    studentNumber: "LTC-2026-0001",
    name: "Amina Ocen",
    firstName: "Amina",
    lastName: "Ocen",
    photoUrl: "",
    accessStatus: "APPROVED",
    gradeClass: "Senior 4",
    isDayScholar: true,
    dayScholarType: "DAY_SCHOLAR_WALK",
    transportRoute: "",
    gender: "Female",
    avatarColorSeed: 42,
    homeroomTeacher: "",
    academicYear: "2026",
    qrToken: "token-001",
    updatedAt: Date.now(),
    isDeleted: false,
  };
}

test("Version 1 accepts only the four approved staff roles", async () => {
  const admin = schoolDb(USERS.admin);
  const gate = schoolDb(USERS.gate);
  const meal = schoolDb(USERS.meal);
  const head = schoolDb(USERS.head);
  const removed = schoolDb(USERS.removedRole);
  const inactive = schoolDb(USERS.inactive);

  await assertSucceeds(admin.collection("students").get());
  await assertSucceeds(head.collection("students").get());
  await assertSucceeds(gate.collection("students").get());
  await assertSucceeds(meal.collection("students").get());
  await assertFails(removed.collection("students").get());
  await assertFails(inactive.collection("students").get());
});

test("Administrator registers students; Head Teacher and operators cannot", async () => {
  await assertSucceeds(
    schoolDb(USERS.admin).collection("students").doc("STU-001")
      .set(studentRecord())
  );
  await assertFails(
    schoolDb(USERS.head).collection("students").doc("STU-002")
      .set(studentRecord("STU-002"))
  );
  await assertFails(
    schoolDb(USERS.gate).collection("students").doc("STU-003")
      .set(studentRecord("STU-003"))
  );
  await assertFails(
    schoolDb(USERS.meal).collection("students").doc("STU-004")
      .set(studentRecord("STU-004"))
  );
});

test("Shared student records reject private fields", async () => {
  const invalid = studentRecord("STU-PRIVATE");
  invalid.guardianPhone = "+256700000001";
  await assertFails(
    schoolDb(USERS.admin).collection("students").doc("STU-PRIVATE").set(invalid)
  );
});

test("Only Admin and Head Teacher can access private student records", async () => {
  const privateData = {
    studentId: "STU-PRIVATE",
    guardianName: "Parent",
    guardianPhone: "+256700000001",
    emergencyContact: "+256770000001",
    notes: "",
    updatedAt: Date.now(),
  };
  await assertSucceeds(
    schoolDb(USERS.admin).collection("student_private").doc("STU-PRIVATE").set(privateData)
  );
  await assertSucceeds(
    schoolDb(USERS.head).collection("student_private").doc("STU-PRIVATE").get()
  );
  await assertFails(
    schoolDb(USERS.gate).collection("student_private").doc("STU-PRIVATE").get()
  );
  await assertFails(
    schoolDb(USERS.meal).collection("student_private").doc("STU-PRIVATE").get()
  );
});

test("Gate Officer can record gate logs but cannot record meals", async () => {
  const gate = schoolDb(USERS.gate);
  await assertSucceeds(
    gate.collection("gate_logs").doc("LOG-001").set({
      id: "LOG-001",
      studentNumber: "LTC-2026-0001",
      studentName: "Amina Ocen",
      decision: "APPROVED",
      gateLocation: "Main Gate",
      timestamp: Date.now(),
    })
  );
  await assertFails(
    gate.collection("meals").doc("MEAL-001").set({
      id: "MEAL-001",
      studentId: "STU-001",
      studentNumber: "LTC-2026-0001",
      studentName: "Amina Ocen",
      gradeClass: "Senior 4",
      mealType: "LUNCH",
      mealDate: "2026-10-31",
      status: "SERVED",
      notes: "",
      servingStation: "Main Hall",
      timestamp: Date.now(),
    })
  );
});

test("Meal Staff can record meals but cannot record gate logs", async () => {
  const meal = schoolDb(USERS.meal);
  await assertSucceeds(
    meal.collection("meals").doc("MEAL-001").set({
      id: "MEAL-001",
      studentId: "STU-001",
      studentNumber: "LTC-2026-0001",
      studentName: "Amina Ocen",
      gradeClass: "Senior 4",
      mealType: "LUNCH",
      mealDate: "2026-10-31",
      status: "SERVED",
      notes: "",
      servingStation: "Main Hall",
      timestamp: Date.now(),
    })
  );
  await assertFails(
    meal.collection("gate_logs").doc("LOG-002").set({
      id: "LOG-002",
      studentNumber: "LTC-2026-0001",
      studentName: "Amina Ocen",
      decision: "APPROVED",
      gateLocation: "Main Gate",
      timestamp: Date.now(),
    })
  );
});

test("Version 1 denies finance, attendance and examination collections", async () => {
  for (const uid of [USERS.admin, USERS.head, USERS.gate, USERS.meal]) {
    const school = schoolDb(uid);
    await assertFails(school.collection("attendance").get());
    await assertFails(school.collection("exam_exceptions").get());
  }
});

test("Gate and Meal staff cannot manage staff accounts", async () => {
  await assertFails(
    schoolDb(USERS.gate).collection("staff").doc("new_staff").set({
      name: "New Staff",
      email: "new@ltc.ac.ug",
      role: "ADMIN",
      active: true,
    })
  );
  await assertFails(
    schoolDb(USERS.meal).collection("staff").doc("new_staff").set({
      name: "New Staff",
      email: "new@ltc.ac.ug",
      role: "ADMIN",
      active: true,
    })
  );
});

test("Administrator can provision only the four Version 1 roles", async () => {
  const admin = schoolDb(USERS.admin).collection("staff");
  const profile = { name: "New Staff", email: "new@ltc.ac.ug", active: true };

  for (const role of ["ADMIN", "HEAD_TEACHER", "GATE_OFFICER", "MEAL_STAFF"]) {
    await assertSucceeds(admin.doc("new_" + role).set({ ...profile, role }));
  }
  await assertFails(admin.doc("new_BURSAR").set({ ...profile, role: "BURSAR" }));
  await assertFails(admin.doc("new_TEACHER").set({ ...profile, role: "TEACHER" }));
});

test("Administrators cannot modify their own staff profile", async () => {
  await assertFails(
    schoolDb(USERS.admin).collection("staff").doc(USERS.admin).update({ role: "GATE_OFFICER" })
  );
});

test("Only administrators can list staff profiles", async () => {
  await assertSucceeds(schoolDb(USERS.admin).collection("staff").get());
  await assertFails(schoolDb(USERS.head).collection("staff").get());
  await assertFails(schoolDb(USERS.gate).collection("staff").get());
  await assertFails(schoolDb(USERS.meal).collection("staff").get());
});
