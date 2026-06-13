# Research: 标讯中心 P0 阻塞项

## Decision 1: Evaluation customer info storage — EAV pattern

**Decision**: Use EAV (Entity-Attribute-Value) table for 13×14 matrix
**Rationale**: 13 roles × 14 info columns = 182 cells, not pre-definable as separate columns; EAV matches JPA entity-per-storage pattern already used in this project. Each cell = 1 row in `tender_evaluation_customer_info` with `(evaluation_id, role_key, info_key, value, value_type)`.
**Alternatives considered**: JSON column (loss of type safety, hard to query), 14 columns × 13 rows (182 columns total, anti-pattern).

## Decision 2: TenderSource API key encryption

**Decision**: Use project's existing AES encryption (same infrastructure as JWT secret)
**Rationale**: Avoid introducing new KMS infrastructure; symmetric AES with key derived from JWT_SECRET. Encrypt at Service layer, store as VARCHAR(512) in DB.
**Alternatives considered**: Vault/HashiCorp (overkill for single tenant config).

## Decision 3: EVALUATED_PENDING_REVIEW as virtual state

**Decision**: Not a new DB column for Tender status — use `requires_review BOOLEAN` flag on `tender_evaluations` table, plus `last_reviewed_by_id` and `last_reviewed_at` timestamps. The flag being TRUE means the evaluation has been re-edited and needs admin review before decision buttons become available.
**Rationale**: Avoids expanding Tender.Status enum (7 values across many switch statements) and keeps the review state scoped to the evaluation aggregate.

## Decision 4: Transfer vs Dispatch distinction

**Decision**: `TenderAssignmentRecord.type` ENUM with `DISPATCH` or `TRANSFER`. Transfer endpoint is `POST /api/tenders/{id}/transfer`, requires `bid_admin` or `bid_lead` role. Does NOT change tender status. Old `project_manager_id` is overwritten.
**Rationale**: Adding a type column is minimal schema change. The data scope guard (`@DataScope`) already reads `project_manager_id` from the tender row, so updating it on transfer automatically removes old owner's access.
