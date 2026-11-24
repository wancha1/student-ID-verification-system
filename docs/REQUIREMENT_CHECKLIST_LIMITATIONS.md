# Student requirement checklist — correctness and limitations

## Confirmed defects on PR #71 tip
- `StudentRequirement.kt` defaulted all seven Boolean requirement fields to `true`.
- `MainViewModel.requirementsList` inferred uniform and textbook completion from fee clearance.
- It inferred bus-pass clearance from day-scholar status.
- It marked sports kit, medical form, school ID and rules agreement complete without checking any evidence.
- The UI exposed a one-click "Clear All Items" action.
- Requirement overrides are held in an in-memory `MutableStateFlow<Map<String, StudentRequirement>>`; they are not currently persisted in Room or Supabase.

## Remediation in this branch
- All seven Boolean defaults are now `false`.
- New requirement rows no longer infer completion from fee status or boarding status.
- The bulk "Clear All Items" action and its ViewModel method have been removed.
- Regression tests assert new rows begin at 0% and cannot be fully cleared without explicit item updates.

## Remaining work
The Boolean model is only a fail-closed interim correction. It cannot distinguish:
- UNKNOWN — nobody has verified the item.
- REQUIRED — the item is required and remains outstanding.
- COMPLETE — verified complete.
- WAIVED — an authorized person waived the requirement.
- NOT_APPLICABLE — the requirement does not apply to this student.

Replace the booleans with an explicit enum/status model in a coordinated change across the model, UI, exports, Room persistence, Supabase schema, synchronization and audit events.

Requirement records also need:
- school and academic-year/term scope;
- actor who verified or changed the status;
- timestamp;
- evidence/reference where appropriate;
- correction reason;
- authorized waiver and not-applicable handling.

Do not claim requirements management is production-ready until requirement records persist across restart and synchronize across authorized devices.
