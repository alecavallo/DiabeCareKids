# Delta for pdf-export

## ADDED Requirements

### Requirement: On-Device Generation (INV-004)

The system MUST generate clinical PDF reports entirely on-device without relying on external servers or network connectivity.

#### Scenario: Generate valid PDF report offline
- GIVEN the user's device is entirely offline
- WHEN the guardian requests an export of meal records
- THEN the system generates a valid PDF document using native rendering capabilities
- AND no network requests are made during generation

### Requirement: Table Content Format

The PDF report MUST present exported records in a structured tabular format containing Date, Pre-Meal BG, 2h BG, and Real Carbs. Photo thumbnails MAY be omitted.

#### Scenario: Records map to required table columns
- GIVEN there are meal records with initial glucose, postprandial glucose, and real carbs
- WHEN the records are exported to PDF
- THEN each record appears as a single row
- AND the row columns accurately display Date, Pre-Meal BG, 2h BG, and Real Carbs

### Requirement: Date-Range Filtering

The system MUST only export meal records whose `fecha_hora_inicio` falls within the selected date range.

#### Scenario: Filter out out-of-range records
- GIVEN a selected date range of the last 7 days
- AND the database contains records from both the last 7 days and 30 days ago
- WHEN the report is generated
- THEN only records from the last 7 days are included in the PDF
- AND records older than 7 days are excluded

### Requirement: Chronological Ordering

The exported meal records MUST be ordered chronologically by `fecha_hora_inicio` in ascending order within the report.

#### Scenario: Records sorted ascending by date
- GIVEN multiple meal records within the selected date range
- WHEN the PDF report is generated
- THEN the rows in the table are sorted by `fecha_hora_inicio` from oldest to newest

### Requirement: Caching and Sharing Intent

The system MUST save the generated PDF to a local cache directory and automatically launch a system sharing intent (`ACTION_SEND`) via `FileProvider`.

#### Scenario: Export triggers share intent
- GIVEN a successfully generated PDF report
- WHEN the generation process completes
- THEN the file is saved to the application's local cache directory
- AND an `ACTION_SEND` intent is launched with the PDF URI attached

### Requirement: Constraint Enforcement

The system MUST NOT use custom fonts (system default only) and MUST NOT use third-party cloud/Firebase rendering services.

#### Scenario: Render with constraints applied
- GIVEN a request to generate a PDF report
- WHEN the PDF is rendered
- THEN it uses default system fonts
- AND it executes entirely on the client without cloud rendering
