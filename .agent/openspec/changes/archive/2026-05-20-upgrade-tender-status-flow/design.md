# Design: Tender Status Lifecycle Upgrade

## 1. Architecture Overview
The solution follows the **FP-Java Profile**. Status transitions are handled by a Pure Core policy (`TenderStatusTransitionPolicy`), while side effects (database updates, notifications) are orchestrated by Application Services.

## 2. Domain Model Changes

### Tender Status (Enum)
- `PENDING_ASSIGNMENT` (待分配): Initial state for all new tenders.
- `TRACKING` (跟踪中): Transitioned after a lead is assigned or claimed.
- `EVALUATED` (已评估): Transitioned after a `ScoreAnalysis` is completed for the tender.
- `BIDDING` (投标中): Transitioned when "Bid Now" is clicked.
- `WON` (已中标): Outcome of a bid.
- `LOST` (未中标): Outcome of a bid.
- `ABANDONED` (已放弃): Terminated state.

### ScoreAnalysis Entity
- New field `tender_id` (Long, nullable).
- When a `ScoreAnalysis` with `tender_id` is saved, the corresponding `Tender` transitions to `EVALUATED`.

## 3. Transition Rules Matrix

| From \ To | PENDING_ASSIGNMENT | TRACKING | EVALUATED | BIDDING | WON | LOST | ABANDONED |
|-----------|--------------------|----------|-----------|---------|-----|------|-----------|
| PENDING_ASSIGNMENT | - | Assign/Claim | - | - | - | - | Abandon |
| TRACKING | Revert | - | Evaluate | - | - | - | Abandon |
| EVALUATED | - | - | - | Bid Now | - | - | Abandon |
| BIDDING | - | - | - | - | Won | Lost | Abandon |
| WON/LOST/ABANDONED | - | - | - | - | - | - | - |

## 4. Permission Logic
- **Abandon Permission**: Should be restricted to `ADMIN` or `MANAGER` by default, or configurable via a feature flag/permission bit. For now, we will implement it as a role-based check in the Application Service.

## 5. UI Components
- **TenderTable**: Status column using new labels.
- **TenderActionMenu**: Dynamic buttons based on current state.
    - `TRACKING` state -> "立即评估" button.
    - `EVALUATED` state -> "立即投标" button.
    - `BIDDING` state -> "登记结果" (leads to Won/Lost/Abandoned).
