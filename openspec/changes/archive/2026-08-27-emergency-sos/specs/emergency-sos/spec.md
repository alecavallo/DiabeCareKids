# Delta for emergency-sos

## ADDED Requirements

### Requirement: REQ-SOS-001 (Continuous 3.0s Hold Activation)

The system MUST trigger an emergency alert only when the SOS interaction is continuously held for exactly 3.0 seconds (INV-003). If the hold is released before 3.0 seconds, the system MUST reset the progress to 0% and MUST NOT trigger an alert or create an emergency record. The system SHOULD trigger haptic feedback upon successful activation.

#### Scenario: Valid 3.0s activation
- GIVEN the user initiates a hold on the SOS button
- WHEN the hold is maintained continuously for 3.0 seconds
- THEN the system transitions to the triggered state
- AND the system provides haptic feedback

#### Scenario: Early-release cancellation
- GIVEN the user is holding the SOS button
- AND the hold duration is 2.5 seconds
- WHEN the user releases the hold
- THEN the system resets progress to 0%
- AND no emergency alert is triggered
- AND no emergency record is created

#### Scenario: Release at exactly boundary
- GIVEN the user is holding the SOS button
- WHEN the hold duration reaches exactly 3.0 seconds
- THEN the system immediately triggers the emergency state regardless of subsequent release

### Requirement: REQ-SOS-002 (Emergency Record Creation)

Upon a successful SOS trigger, the system MUST create an `Emergencia` record with a unique identifier, the patient's ID and name, the current timestamp, and set the status to "ACTIVA".

#### Scenario: SOS triggers record creation
- GIVEN a successful 3.0s SOS activation has occurred
- WHEN the system processes the trigger
- THEN an `Emergencia` record is created in the `EmergenciaStore`
- AND the record's state is strictly set to "ACTIVA"

### Requirement: REQ-SOS-003 (Location Capture)

The system MUST attempt to capture the user's current GPS coordinates (latitude, longitude, accuracy) via a `LocationProvider` when an SOS is triggered. If location permissions are denied or coordinates are unavailable, the system MUST gracefully handle the absence by creating the emergency record with null location data.

#### Scenario: Location available on trigger
- GIVEN location permissions are granted and GPS is available
- WHEN the SOS alert is triggered
- THEN the `Emergencia` record includes the latitude, longitude, and accuracy in meters

#### Scenario: Location unavailable on trigger
- GIVEN location permissions are denied or GPS is unavailable
- WHEN the SOS alert is triggered
- THEN the system creates the `Emergencia` record with null coordinates
- AND the system does not crash or block the alert

### Requirement: REQ-SOS-004 (Guardian Notification)

The system MUST dispatch a high-priority notification to all associated guardians immediately after the emergency record is created, using the `GuardianNotifier` abstraction. 

#### Scenario: Notifier invoked on trigger
- GIVEN a valid SOS alert has been triggered
- AND the `Emergencia` record has been successfully built
- WHEN the notification step executes
- THEN the `GuardianNotifier` is invoked to dispatch alerts to all guardians