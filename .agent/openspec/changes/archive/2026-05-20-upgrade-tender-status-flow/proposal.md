# Proposal: Upgrade Tender Status Flow

## 1. Problem Statement
The current tender status flow is too simple (PENDING, TRACKING, BIDDED, ABANDONED) and does not reflect the real-world business process of XiYu. The user requires a more detailed lifecycle including assignment, evaluation, and bidding results.

## 2. Proposed Changes
- Refactor `Tender.Status` to support the full lifecycle: `PENDING_ASSIGNMENT`, `TRACKING`, `EVALUATED`, `BIDDING`, `WON`, `LOST`, `ABANDONED`.
- Enhance `ScoreAnalysis` to support Tender evaluation before project creation.
- Implement strict status transition rules in `TenderStatusTransitionPolicy`.
- Add configurable "Abandon" permission.
- Update Frontend UI to reflect the new statuses and provide appropriate actions for each stage.

## 3. Impact
- **Database**: Migration required for `tenders` and `score_analyses` tables.
- **API**: Minor changes to Tender and ScoreAnalysis endpoints.
- **Business Logic**: Centralized in `TenderStatusTransitionPolicy`.

## 4. Verification Plan
- **Unit Tests**: Coverage for transition policy and permission checks.
- **Integration Tests**: Verify status flow from assignment to result.
- **E2E Tests**: Manual verification in the UI.
