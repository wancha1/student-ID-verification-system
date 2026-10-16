# PR #7 — Audit and UI redesign notes

Date: 2026-10-16  
Branch: `fix/audit-findings-1-to-5`

## Scope

This follow-up addresses CI regressions after the Firebase staff-authentication migration, improves the staff login and app shell, and closes an authorization gap in emergency gate overrides.

## Changes made

### Authentication and authorization
- Staff login continues to use Firebase Authentication and the trusted `schools/ltc_school_01/staff/{uid}` roster document.
- The role lookup now explicitly uses Firestore `Source.SERVER`. A cached staff record must not be enough to establish the active role after sign-in.
- Explicit finance access is honored for both Administrator and Head Teacher accounts when the staff record grants it. Bursar access remains role-based.
- Emergency gate overrides require an authenticated Administrator or Head Teacher session before checking the supervisor PIN. Gate staff cannot authorize their own override using the PIN alone. The target must match the student identified by the current verified scan; inactive, revoked, replaced, or unknown cards cannot be overridden; the scan's actual active card is used in the audit record; and attribution comes from the authenticated session rather than a caller-supplied name.
- Role switching remains a sign-out/account-change flow; the UI does not grant a different role locally.

### CI and test reliability
- Added a test-only adapter for legacy PIN-based workflow tests. It lives in `src/test` and does not restore local PIN authentication to the production app.
- Outbox persistence tests now use a backend that intentionally rejects remote writes. This makes the tests verify durable pending/failed mutations without racing a successful background sync.
- Firestore emulator tests now use a dedicated named Firebase app, configure the emulator once, and use OkHttp for the emulator's owner-bypass PATCH request. Java's standard `HttpURLConnection` does not support PATCH directly.
- Added the OkHttp test dependency through the existing version catalog.

### UI/UX
- Redesigned staff login with a distinctive midnight-blue and champagne-gold visual identity, clearer hierarchy, a more prominent sign-in action, and a scrollable/IME-aware layout for small phones.
- Added specific guidance for inactive accounts and unsupported roles instead of returning only a generic sign-in failure.
- Refined the main app header with a school identity mark, role-specific department badge, stronger contrast, and a compact account-change control to reduce crowding on narrow screens.
- Reworked admin quick actions into two rows with larger touch targets and a clear section label, avoiding four cramped actions on small phones.

## Validation status

CI must be checked on the latest branch head before merging. Earlier CI confirmed the debug APK compiled and the Firestore rules tests passed, but unit tests still failed. The fixes above are intended to address those failures; do not treat the suite as passing until the latest Android unit-test job completes successfully.

## Remaining risks / deployment requirements

1. **Live Firebase setup remains external.** The actual LTC `google-services.json`, Email/Password enablement, named Firestore database, deployed rules, and trusted first-admin staff document must be configured for the real project.
2. **Offline re-unlock is not implemented.** First sign-in requires connectivity. This is not yet a fully offline-first authentication experience.
3. **Legacy `/users/{uid}` rules remain permissive for self-owned legacy data.** They allow a user to create their own legacy profile with a `GATE_OFFICER` role. This does not grant membership in `/schools/ltc_school_01/staff`, but the legacy path should be deprecated or explicitly migrated/isolated so old clients cannot confuse the two authorization models.
4. **Student read scope is broad.** Any active LTC staff role can read school-wide student records. This may be needed for gate, meals, and attendance workflows, but production privacy review should confirm that every role needs the full student document, particularly guardian/emergency contact details.
5. **Emergency override remains a local workflow.** It is now role-gated in the app and PIN-protected, but its authorization/audit event should eventually be verified server-side when connected, with explicit policy for offline override records and reconciliation.
6. **No real-device visual verification yet.** The layout compiles in CI, but it should be checked on a small Android phone with the keyboard open, larger font scaling, and both light/dark system settings before calling the redesign finished.

## Merge recommendation

Keep PR #7 open until the latest CI passes. Review the final diff and the remaining live-Firebase/offline-auth risks before considering a production release.


## Follow-up hardening (2026-10-16)

- Meal and requirements scans now call the repository's production QR verifier, so signature validity, validity dates, exact registered card identity, card lifecycle, and active student status are checked consistently before a department action.
- Meal records are loaded from Room and written before success is reported. The insert uses the existing unique (studentId, mealType, mealDate) index with an atomic IGNORE operation to prevent duplicate servings on repeated/concurrent scans. If local persistence is unavailable, serving fails closed.
- Exam-exception local records now carry Firebase UIDs for recommendation and approval. Database v8 migration adds UID columns; approval rejects missing requester identity and self-approval. Cloud writes include the UIDs, and Firestore rules enforce authenticated attribution and a one-way approval transition.
- The legacy `/users` namespace is denied all client access so self-created legacy role profiles cannot be confused with trusted school staff.
- **Student privacy migration is still a deployment blocker:** current student documents contain guardian/emergency-contact fields. Firestore rules cannot hide selected fields inside a document. Move those fields to a restricted `student_private/{studentId}` collection, migrate existing records, update sync/read paths, and verify role-based access before deploying.
- **Firebase/device verification remains external:** the repository still references the generated AI Studio/Oakridge Firestore database ID. Do not change it blindly. Confirm the actual LTC Firebase project and database with the project owner, install its local `google-services.json`, deploy rules to that database, and run emulator plus physical-device tests for offline recovery, revocation sync, small-screen layout, and restart persistence. These steps cannot be completed from source changes alone.
