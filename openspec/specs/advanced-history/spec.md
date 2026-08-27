# Delta for Advanced History

## ADDED Requirements

### Requirement: Add Historical Past Record

The system MUST allow creating a meal record for a past date and time.
The system MUST set `es_registro_historico=true` for these records.
The system MUST omit photos for historical records (`foto_antes_url` and `foto_despues_url` MUST be null).

#### Scenario: Save past record without photos

- GIVEN the guardian is creating a past meal record
- WHEN they submit the form with a past date/time, meal type, initial BG, food name, carbs, and consumed percentage
- THEN the system saves the `RegistroComida`
- AND `es_registro_historico` is true
- AND `foto_antes_url` is null
- AND `foto_despues_url` is null

### Requirement: Timeline Ordering

The system MUST allow retrieval of all meal records to build a historical timeline.
The system MUST display the records ordered by their start time (`fecha_hora_inicio`) descending (newest first).

#### Scenario: Viewing timeline with multiple records

- GIVEN the persistence store contains multiple meal records with different start times
- WHEN the guardian accesses the Advanced View history timeline
- THEN the system displays the list of records
- AND the newest records appear at the top

### Requirement: Historical Record Editing and Recalculation

The system MUST allow guardians to edit existing historical records.
The system MUST recalculate the actual carbohydrates (`carbohidratos_reales`) using `CarbMath` when estimated carbs, consumed percentage, or 2-hour postprandial BG are modified.
The system MUST persist the updated record to the store.

#### Scenario: Edit changes carbs and recalculates reals

- GIVEN an existing historical record with 50g estimated carbs and 100% consumed (reals = 50g)
- WHEN the guardian edits the consumed percentage to 50%
- THEN the system recalculates `carbohidratos_reales` to 25g
- AND the updated record is persisted correctly

### Requirement: Manual Carb Entry Fallback

The system MUST permit manual entry of carbohydrate values when adding or editing a historical record.
The system MUST NOT require a nutrition API (USDA/Gemini) lookup for manual entries.

#### Scenario: Historical record created via manual carb entry

- GIVEN the guardian is logging a past meal
- WHEN they bypass the food lookup and manually enter "30g" of carbohydrates
- THEN the system accepts the manual value
- AND successfully creates the historical record with 30g estimated carbs
