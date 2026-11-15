# LTC Student Access — School Pilot Onboarding

## Current deployment boundary

The connected production Supabase project is configured for **Lira Town College**, school tenant `ltc_school_01`. The Android authenticator currently uses this school ID in its source code. This build is therefore an LTC deployment, not yet a generic multi-school installer.

Do not reuse the LTC tenant ID, accounts, student data, or signing identity as a shortcut for another school. A second school requires a distinct tenant configuration, separate authorized staff memberships, verified RLS isolation, and a deployment-specific release.

## Before the pilot

- [ ] Obtain written approval from the school leadership and identify the accountable data owner.
- [ ] Agree which student fields are necessary and who is permitted to view each field.
- [ ] Confirm the school has authority to process student records and guardian contact details.
- [ ] Agree retention, correction, export, incident-reporting, and account-revocation procedures.
- [ ] Prepare a test roster using synthetic data before importing real student data.
- [ ] Verify that the school's Android devices meet the app's minimum Android 8.0 requirement.
- [ ] Assign one accountable Admin and one backup Admin.

## Staff accounts

LTC V1's primary staff interfaces are:
- **ADMIN** — student registry, card lifecycle, staff administration and system configuration.
- **HEAD_TEACHER** — leadership oversight and approved review functions.
- **GATE_OFFICER** — gate verification and gate activity.
- **MEAL_STAFF** — meal verification and serving records.

Create staff identities in Supabase Auth, then create the corresponding row in `public.staff_memberships` using the authenticated user's UUID, school ID `ltc_school_01`, full name, email, role, and active status. Do not let users self-assign a role. The first Admin must be provisioned through a trusted owner-controlled process; the mobile app must not bootstrap its own administrator.

The live database currently contains four active LTC memberships, one for each primary V1 role. Verify the intended account owner and email with school leadership before issuing credentials. Never publish staff passwords in deployment documents.

## Student roster and cards

A blank CSV header template is maintained at [docs/templates/ltc-student-roster-template.csv](templates/ltc-student-roster-template.csv). It is a column guide, not an importer or an authorization to upload records.

- Import only an official roster approved by the school's accountable data owner.
- Preserve student numbers exactly as issued by the school; do not convert them to numbers or remove leading zeroes.
- Required source fields: `student_number`, `first_name`, `last_name`, `grade_class`, and `access_status`.
- Use `is_day_scholar` as `true` or `false`; use `fee_status` only as `CLEARED` or `OUTSTANDING`.
- `class_name`, `stream`, `day_scholar_type`, `transport_route`, `gender`, `homeroom_teacher`, and `academic_year` are optional source fields.
- The importer must derive the full display name and generate internal IDs, school ID, timestamps, QR credentials, and card records; do not invent these values in the spreadsheet.
- Validate missing required values, duplicate student numbers within the school, invalid booleans/statuses, and malformed rows before any write.
- Review a validation report and obtain a second-person approval before committing a live import. Imports should be idempotent and should never silently overwrite a different student's record.
- Keep guardian and emergency-contact details in the restricted private student profile, not in the general roster CSV.
- Issue a unique card for each student. Verify QR signature, card lifecycle status, and student mapping before printing.
- Test lost-card revocation and replacement before handing cards to students.
- Do not use real student records in screenshots, sample files, or support tickets.

The current production database was configured as a clean start; no student roster has been migrated from Firebase. The live students table currently has zero records. No production roster import has been performed. The template does not mean that an end-to-end CSV importer has been implemented or tested; that remains a release acceptance item.

## Device setup

- Install the production-signed APK obtained from the Production APK workflow.
- Sign in with the staff account assigned to that physical device.
- Grant camera permission only to roles/devices that need scanning.
- Sync the approved roster before operating in offline mode.
- Record the device custodian, device identifier, school location, and support contact.
- Enable device screen lock and keep devices physically secured.

## Pilot day procedure

1. Admin verifies the roster and active cards.
2. Gate Officer tests entry/exit scans using approved test cards.
3. Meal Staff tests one serving and duplicate-serving prevention.
4. Head Teacher reviews the operational summary.
5. The pilot lead checks sync status and pending offline events.
6. The school signs off only after the physical-device acceptance checklist passes.

## Support and incident handling

- First contact: the school's designated Admin.
- Escalation: the product owner/developer.
- For a lost device, revoke the staff membership/session and secure the device before issuing a replacement.
- For suspected data exposure, suspend affected accounts, preserve audit information, notify the school's accountable data owner, and follow the school's incident process.
- Do not share service-role keys, signing keys, or student exports in support chats.

## Go-live gate

Do not onboard a second school from the current LTC-specific APK. First externalize the school ID and backend configuration, create a distinct tenant, test cross-school denial, and generate a school-specific production build.
