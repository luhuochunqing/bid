# Capability: Tender Lifecycle Management

## Status Definitions
The system MUST support the following statuses for a Tender:
- `PENDING_ASSIGNMENT`: Newly created or imported tenders.
- `TRACKING`: Sales personnel are following up.
- `EVALUATED`: Bid/No-bid evaluation has been completed.
- `BIDDING`: Formal bidding process is underway.
- `WON`: Successfully won the bid.
- `LOST`: Bid lost to competitors.
- `ABANDONED`: Decided not to pursue.

## ADDED Requirements

### Requirement: Status Transition Validation (REQ-TL-001)
The system MUST enforce valid status transitions according to the defined lifecycle.

#### Scenario: Normal Flow
- Given a tender in `PENDING_ASSIGNMENT`
- When it is assigned to a user
- Then its status MUST change to `TRACKING`

#### Scenario: Evaluation Completion
- Given a tender in `TRACKING`
- When an evaluation form is submitted
- Then its status MUST change to `EVALUATED`

#### Scenario: Bidding Start
- Given a tender in `EVALUATED`
- When "Bid Now" is clicked
- Then its status MUST change to `BIDDING`

### Requirement: Abandonment Permission (REQ-TL-002)
Only authorized users (ADMIN/MANAGER or users with specific permission) SHALL be permitted to transition a tender to `ABANDONED`.

#### Scenario: Manager Abandons
- Given a manager user
- When they choose to abandon a tender
- Then the transition MUST be allowed

#### Scenario: Unauthorized Staff Abandons
- Given a staff user without abandon permission
- When they try to abandon a tender
- Then the system MUST reject the request
