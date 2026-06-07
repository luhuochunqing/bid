# Data Model: 标讯中心 P0

## TenderEvaluation (existing, modified)

Table: `tender_evaluations`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK | existing |
| tender_id | BIGINT FK→tenders | existing, one-to-one |
| status | ENUM(DRAFT, SUBMITTED) | existing |
| version | INT | existing, optimistic lock |
| requires_review | BOOLEAN DEFAULT FALSE | **NEW** — TRUE after re-edit in EVALUATED state |
| last_reviewed_by_id | VARCHAR(32) | **NEW** — who last approved |
| last_reviewed_at | DATETIME | **NEW** — when last approved |
| evaluation_round | INT DEFAULT 1 | **NEW** — increment on each re-edit cycle |

Relations:
- `tender_evaluation_basics` (one-to-one, 8 fields)
- `tender_evaluation_customer_info` (one-to-many, EAV rows)
- `tender_evaluation_recommendation` (one-to-one, 2 fields)

## TenderEvaluationBasic (NEW)

Table: `tender_evaluation_basics`

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT PK | auto_increment |
| evaluation_id | BIGINT FK | unique, one-to-one |
| shortlisted_count | INT | ≥0 |
| annual_procurement_amount | DECIMAL(15,2) | 万元 |
| unfavorable_items | TEXT | ≤5000 chars |
| risk_assessment | TEXT | ≤5000 chars |
| risk_mitigation_plan | TEXT | ≤5000 chars |
| process_knowledge | TEXT | ≤5000 chars |
| support_notes | TEXT | ≤5000 chars |
| project_plan_gap | TEXT | ≤5000 chars |

## TenderEvaluationCustomerInfo (NEW)

Table: `tender_evaluation_customer_info`

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT PK | auto_increment |
| evaluation_id | BIGINT FK | NOT NULL |
| role_key | VARCHAR(32) | NOT NULL; one of 13 roles |
| info_key | VARCHAR(32) | NOT NULL; one of 14 info dimensions |
| value | VARCHAR(500) | cell value |
| value_type | ENUM('TEXT','DROPDOWN') | not null |

Unique key: `(evaluation_id, role_key, info_key)`

## TenderEvaluationRecommendation (NEW)

Table: `tender_evaluation_recommendation`

| Column | Type | Constraints |
|--------|------|-------------|
| evaluation_id | BIGINT PK FK | one-to-one |
| should_bid | BOOLEAN | NOT NULL |
| reason | TEXT | nullable; required if should_bid=false |

## TenderAssignmentRecord (existing, modified)

Table: `tender_assignment_records`

| Column | Type | Notes |
|--------|------|-------|
| ...existing columns... | | |
| type | ENUM('DISPATCH','TRANSFER') | **NEW column**; DEFAULT 'DISPATCH' |

## TenderSourceConfig (NEW)

Table: `tender_source_configs`

| Column | Type | Constraints |
|--------|------|-------------|
| id | BIGINT PK | always 1 (singleton) |
| platforms_json | JSON | array of selected platforms |
| api_endpoint | VARCHAR(500) | nullable |
| api_key_encrypted | VARCHAR(512) | nullable, AES encrypted |
| keywords | VARCHAR(500) | comma-separated |
| regions_json | JSON | array of selected regions |
| business_units_json | JSON | array |
| budget_min | DECIMAL(15,2) | default 0 |
| budget_max | DECIMAL(15,2) | default 1000 |
| auto_sync | BOOLEAN | default false |
| sync_interval_minutes | INT | default 1440 |
| auto_dedupe | BOOLEAN | default true |
| updated_by | VARCHAR(32) | user who last modified |
| updated_at | DATETIME | auto-updated |

## Tender.SourceType (modified enum)

| Old | New | Mapping |
|-----|-----|---------|
| MANUAL | MANUAL_SINGLE | single manual entry |
| EXTERNAL | EXTERNAL_PLATFORM | third-party pull |
| — | CRM_OPPORTUNITY | CRM opportunity push |
| — | BULK_IMPORT | Excel batch import |

## Tender.Status (existing, no change)

7 states unchanged: PENDING_ASSIGNMENT, TRACKING, EVALUATED, BIDDING, WON, LOST, ABANDONED

## EVALUATED_PENDING_REVIEW (virtual state)

Not a new Status enum value. Detected by: `Tender.status == EVALUATED AND TenderEvaluation.requires_review == true`.
When `requires_review` is true:
- "立即投标" and "放弃投标" buttons are disabled in UI
- Top action bar shows "需审核" label
- Admin/lead sees "确认审核" button
- After review: `requires_review = false`, buttons re-enable
