# Scholink roster import

The Administrator can import a UTF-8 CSV file or an Excel workbook in modern `.xlsx` format from the Student Directory quick-access area.

## Required columns

These columns must be present and non-empty for each imported student:

- `admission_number`
- `first_name`
- `last_name`
- `class_name`

## Supported 13-column template

| Column | Import behaviour |
|---|---|
| admission_number | Maps to the student's unique admission/student number. |
| first_name | Maps to the first name. |
| last_name | Maps to the last name. |
| other_names | Included in the displayed first-name value and retained in private roster metadata. |
| gender | Maps to the gender field. |
| date_of_birth | Retained in private roster metadata; not yet a typed student field. |
| class_name | Combined with stream to form the class label. |
| stream | Combined with class_name to form the class label. |
| student_phone | Retained in private roster metadata; not yet a typed student field. |
| guardian_name | Maps to guardian name. |
| guardian_phone | Maps to guardian phone. |
| email | Retained in private roster metadata; not yet a typed student field. |
| status | Retained as the source roster status only. It is never interpreted as gate approval, fee clearance, or card status. |

Unknown columns are also retained in the private roster metadata. This is intentional: importing a roster must not silently discard source information.

## Import safeguards

- The importer previews valid rows and row-level errors before committing.
- Admission numbers are normalized to uppercase.
- Duplicate admission numbers within the same file are rejected with a row error.
- Admission numbers already in the local student registry are skipped; imports never silently overwrite an existing student.
- Import does not issue or sign ID cards.
- Imported status values do not grant gate access.
- The 13-column template does not contain fee clearance, boarding/day-scholar status, or transport assignment. Existing app defaults apply to those fields until authorized staff verify them; do not treat those defaults as authoritative financial or transport data.
- Unsupported and unknown source values are preserved in the student's private Notes metadata with the `ROSTER_IMPORT_METADATA_V1` marker. Notes are synchronized through the existing private student profile path.
- Maximum input file size is 20 MB and maximum data-row count is 10,000.

## Spreadsheet compatibility

Only modern `.xlsx` workbooks are supported. Legacy binary Excel `.xls` files and macro-enabled `.xlsm` workbooks are not accepted. The importer reads the first worksheet. Save the roster as `.xlsx` or export it as UTF-8 CSV before importing.
