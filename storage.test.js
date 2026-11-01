const assert = require("node:assert");
const { initializeTestEnvironment, assertFails, assertSucceeds } =
  require("@firebase/rules-unit-testing");
const { ref, uploadBytes, getBytes, deleteObject } = require("firebase/storage");
const { test, before, after } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = "demo-ltc-storage";
const SCHOOL_ID = "ltc_school_01";
const PATH = `schools/${SCHOOL_ID}/students/STU-001/photo.jpg`;
const [host, portText] = (process.env.FIREBASE_STORAGE_EMULATOR_HOST || "127.0.0.1:9199").split(":");
let env;

const claims = (schoolId = SCHOOL_ID, role = "GATE_OFFICER", active = true) => ({
  ltcSchoolId: schoolId,
  ltcRole: role,
  ltcActive: active,
});

function storage(uid, customClaims) {
  return env.authenticatedContext(uid, customClaims).storage();
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    storage: {
      rules: fs.readFileSync("./storage.rules", "utf8"),
      host,
      port: Number(portText),
    },
  });
});

after(async () => {
  if (env) await env.cleanup();
});

test("active staff from the same school can read a student photo", async () => {
  const adminStorage = storage("admin", claims("ltc_school_01", "ADMIN"));
  await env.withSecurityRulesDisabled(async (context) => {
    await uploadBytes(ref(context.storage(), PATH), new Uint8Array([1, 2, 3]), {
      contentType: "image/jpeg",
    });
  });
  await assertSucceeds(getBytes(ref(storage("gate", claims()), PATH)));
  await assertSucceeds(getBytes(ref(storage("meal", claims("ltc_school_01", "MEAL_STAFF")), PATH)));
  await assertSucceeds(getBytes(ref(storage("head", claims("ltc_school_01", "HEAD_TEACHER")), PATH)));
  await assertSucceeds(getBytes(ref(adminStorage, PATH)));
});

test("wrong-school, inactive, missing-claim and unsupported-role users cannot read", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    await uploadBytes(ref(context.storage(), PATH), new Uint8Array([1, 2, 3]), {
      contentType: "image/jpeg",
    });
  });
  await assertFails(getBytes(ref(storage("other-school", claims("another_school")), PATH)));
  await assertFails(getBytes(ref(storage("inactive", claims(SCHOOL_ID, "ADMIN", false)), PATH)));
  await assertFails(getBytes(ref(storage("unclaimed", {}), PATH)));
  await assertFails(getBytes(ref(storage("legacy", claims(SCHOOL_ID, "BURSAR")), PATH)));
});

test("only active Admin and Head Teacher can upload or delete photos", async () => {
  const bytes = new Uint8Array([1, 2, 3, 4]);
  await assertSucceeds(uploadBytes(ref(storage("admin", claims(SCHOOL_ID, "ADMIN")), PATH), bytes, {
    contentType: "image/jpeg",
  }));
  await assertSucceeds(deleteObject(ref(storage("admin", claims(SCHOOL_ID, "ADMIN")), PATH)));
  await assertSucceeds(uploadBytes(ref(storage("head", claims(SCHOOL_ID, "HEAD_TEACHER")), PATH), bytes, {
    contentType: "image/webp",
  }));
  await assertFails(uploadBytes(ref(storage("gate", claims()), PATH), bytes, {
    contentType: "image/jpeg",
  }));
  await assertFails(uploadBytes(ref(storage("meal", claims(SCHOOL_ID, "MEAL_STAFF")), PATH), bytes, {
    contentType: "image/jpeg",
  }));
});

test("photo uploads reject unsupported MIME types and files over 5 MiB", async () => {
  const admin = storage("admin", claims(SCHOOL_ID, "ADMIN"));
  await assertFails(uploadBytes(ref(admin, PATH), new Uint8Array([1, 2]), {
    contentType: "application/pdf",
  }));
  await assertFails(uploadBytes(ref(admin, PATH), new Uint8Array(5 * 1024 * 1024 + 1), {
    contentType: "image/jpeg",
  }));
});

test("unscoped and alternate object paths are denied", async () => {
  const admin = storage("admin", claims(SCHOOL_ID, "ADMIN"));
  await assertFails(uploadBytes(ref(admin, "student_photos/photo.jpg"), new Uint8Array([1]), {
    contentType: "image/jpeg",
  }));
  await assertFails(uploadBytes(ref(admin, "schools/ltc_school_01/students/STU-001/private.txt"), new Uint8Array([1]), {
    contentType: "image/jpeg",
  }));
});
