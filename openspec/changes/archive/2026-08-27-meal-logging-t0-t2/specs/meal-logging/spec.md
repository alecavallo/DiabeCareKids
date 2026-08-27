# Delta for Meal Logging

## ADDED Requirements

### Requirement: REQ-MEAL-001 - Domain Models and Serialization

The system MUST define a `RegistroComida` model representing a meal log entry with fields: `tipo_comida` (enum), `glicemia_inicial`, `nombre_alimento`, `carbohidratos_estimados`, `fuente_carbohidratos`, `carbohidratos_reales`, `glicemia_postprandial_2h`, `porcentaje_consumido`, `es_registro_historico`, `foto_antes_url`, `foto_despues_url`, `creado_por_usuario_id`, and `ultima_modificacion`.
The system MUST support JSON serialization and deserialization for this model.

#### Scenario: Serialization round-trip
- GIVEN a populated `RegistroComida` object with all fields set
- WHEN the object is serialized to JSON and deserialized back to an object
- THEN the resulting object MUST be strictly equal to the original object

### Requirement: REQ-MEAL-002 - Hybrid Carb Resolution Engine

The system MUST resolve carbohydrate estimates using a fallback sequence: USDA database (primary), followed by AI estimation via Gemini (secondary), followed by manual input.
The system MUST tag AI-estimated results with "[AI Estimated]".
The system MUST allow the user to manually edit the carbohydrate value before persistence, regardless of the resolution source (INV-002).

#### Scenario: USDA resolution succeeds
- GIVEN a search query for a known food item
- WHEN the nutrition repository is queried
- THEN the system returns the exact carbohydrate value from the USDA database
- AND the value remains editable by the user

#### Scenario: USDA fails and AI fallback succeeds
- GIVEN a search query for an unknown or complex food item
- WHEN the USDA database returns a 404 or empty result
- THEN the system queries the Gemini AI fallback
- AND returns an estimated carbohydrate value tagged with "[AI Estimated]"
- AND the value remains editable by the user

#### Scenario: All automated sources fail
- GIVEN a search query where both USDA and Gemini fail or timeout
- WHEN the resolution sequence completes
- THEN the system prompts for manual carbohydrate input

### Requirement: REQ-MEAL-003 - T2 Real Carbohydrate Calculation

The system MUST calculate `carbohidratos_reales` based on the estimated carbohydrates and the percentage consumed: `carbohidratos_reales = carbohidratos_estimados * (porcentaje_consumido / 100)`.
The system MUST allow recording a 2-hour postprandial blood glucose (`glicemia_postprandial_2h`) during the T2 stage.

#### Scenario: 80% consumed calculation
- GIVEN a meal with `carbohidratos_estimados` of 50g
- WHEN the user logs a `porcentaje_consumido` of 80% during T2
- THEN the `carbohidratos_reales` MUST be calculated as 40g
- AND the postprandial blood glucose value is saved as editable

### Requirement: REQ-MEAL-004 - Optional Photos (INV-005)

The system MUST support optional before (`foto_antes_url`) and after (`foto_despues_url`) photos for meal logs.

#### Scenario: Saving T0 without photo
- GIVEN a new meal log at T0
- WHEN the user saves the log without attaching a photo
- THEN the log is successfully saved with `foto_antes_url` as null

#### Scenario: Saving T2 with photo
- GIVEN an existing meal log at T2
- WHEN the user attaches a post-meal photo and saves
- THEN the log is successfully saved with `foto_despues_url` populated

### Requirement: REQ-MEAL-005 - Persistence Abstraction

The system MUST abstract persistence behind a `PersistenceStore` interface in `commonMain`.
The system MUST provide an in-memory test double (fake) for this interface to allow offline CI testing without a real Firestore connection.

#### Scenario: Save and load via store
- GIVEN an in-memory `PersistenceStore` test double
- WHEN a `RegistroComida` is saved to the store
- THEN the exact same `RegistroComida` can be retrieved by its ID from the store

## MODIFIED Requirements

None.

## REMOVED Requirements

None.

## RENAMED Requirements

None.