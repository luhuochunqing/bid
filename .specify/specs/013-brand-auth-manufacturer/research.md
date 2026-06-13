# Research: 品牌授权 §4.6a

## Decision 1: Data Migration Strategy

**Decision**: Drop and recreate. New table `manufacturer_authorization` with 10 blueprint fields. Old `brand_authorization` table renamed via migration.

**Rationale**: Old schema (brandName, supplierName, scope, scopeDetail, authorizationDocUrl) is fundamentally incompatible with new 10-field model (一级产线, 品牌ID, 品牌, 进口/国产, 品牌原厂名称, etc.). Mapping is lossy — no product line, no import/domestic flag, no brand ID. Since there's no production data yet, a clean table replace is safer than a complex migration.

**Alternatives considered**:
- ALTER TABLE with column additions: rejected — old columns (supplierName→品牌原厂名称, scope→dropped) don't map cleanly, and the table name `brand_authorization` is too generic for the eventual dual-table model
- Side-by-side tables with old table deprecated: considered but adds complexity with no benefit since no production data exists

## Decision 2: Entity Architecture

**Decision**: Create new `ManufacturerAuthorization` domain record + `ManufacturerAuthorizationEntity` JPA entity in a new `manufacturer` subpackage under `brandauth`. Keep old code as-is and add `@Deprecated` annotations.

**Rationale**: The existing `BrandAuthorization` has a different field model. Attempting to refactor in-place would break existing API consumers. New package `brandauth.manufacturer` clearly separates old from new. Old code can be removed in a cleanup PR after §4.6b.

**New package structure**: `brandauth/manufacturer/domain/`, `brandauth/manufacturer/application/`, `brandauth/manufacturer/infrastructure/`

## Decision 3: File Attachment Strategy

**Decision**: Dedicated `brand_auth_attachment` table + upload endpoint at `/api/knowledge/brand-auth/attachments/upload`. Follow `PerformanceAttachmentEntity` pattern.

**Rationale**: Brand auth attachments have specific requirements (two types: 原厂授权附件 + 补充材料附件, multi-file, PDF/JPG/PNG only, 20MB max). A dedicated table allows type tagging and multi-file grouping. Avoids polluting the shared `project_documents` table with non-project data.

**Table**: `brand_auth_attachment` (id, authorization_id, attachment_type ENUM('AUTH_DOC','SUPPLEMENTARY'), file_name, file_url, file_size, file_type, created_at)

**Alternatives considered**:
- Reuse `project_documents` table with `linked_entity_type='BRAND_AUTH'`: rejected — that table is project-centric with project_id FK constraint
- Store files in a JSON column on the authorization table: rejected — MySQL JSON columns can't be indexed efficiently for file queries

## Decision 4: API Path Strategy

**Decision**: Refactor existing controller in-place. Update `BrandAuthorizationController` to handle new `ManufacturerAuthorization` DTOs. Map old endpoints to new model where possible.

**Rationale**: The existing controller at `/api/knowledge/brand-auth` is already wired to the frontend. Breaking the API contract would require coordinated frontend changes. Instead, update the DTO shape and keep the same endpoints.

**Endpoints**:
- `GET /api/knowledge/brand-auth` — list with new fields + filters
- `GET /api/knowledge/brand-auth/{id}` — detail with attachments
- `POST /api/knowledge/brand-auth` — create (JSON body, no files in this endpoint)
- `POST /api/knowledge/brand-auth/attachments/upload` — file upload (MultipartFile)
- `PUT /api/knowledge/brand-auth/{id}` — update
- `POST /api/knowledge/brand-auth/{id}/revoke` — 作废 with reason body
- `DELETE /api/knowledge/brand-auth/{id}` — removed (replaced by revoke)

## Decision 5: Operation Log Strategy

**Decision**: Reuse existing `AuditLog` infrastructure. No new table needed. Add a log query endpoint for brand auth.

**Rationale**: `@Auditable` annotation is already on the controller methods. The existing `audit_logs` table has `entityType`, `entityId`, `oldValue`, `newValue` fields that support diff-based logging. The `AuditLogService.logUpdate()` method accepts before/after values. We add a dedicated endpoint `GET /api/knowledge/brand-auth/{id}/logs` that queries audit_logs WHERE entity_type='BRAND_AUTH' AND entity_id={id}.

**Endpoints to add**: `GET /api/knowledge/brand-auth/{id}/logs` — returns paginated audit log entries for a specific authorization record.

## Decision 6: 代理商授权 Tab

**Decision**: Show tab with empty state placeholder. "代理商授权功能即将上线" message with illustration.

**Rationale**: Dual-tab layout is the primary navigation pattern per blueprint. Showing a placeholder establishes the mental model for users and avoids a jarring UI change when §4.6b adds the second tab. Hiding the tab entirely would require a follow-up UI change.

## Decision 7: 一级产线 Enum

**Decision**: Java enum `ProductLine` with 39 values. Stored as VARCHAR in database. Validated at application layer.

**Rationale**: The 39 items are a fixed business taxonomy that changes infrequently. A database table would add unnecessary complexity for v1. If the list needs CRUD management later, migrate to a table.

## Decision 8: Permission Keys

**Decision**: Add to `RoleProfileCatalog`:
- `knowledge-brand-auth.view` — view list/detail (bid_admin, bid_lead, bid_specialist)
- `knowledge-brand-auth.create` — create new (bid_admin, bid_lead, bid_specialist)  
- `knowledge-brand-auth.edit` — edit existing (bid_admin, bid_lead, bid_specialist)
- `knowledge-brand-auth.revoke` — 作废 (bid_admin, bid_lead only)

Follow the `bidding.manage/create/delete` pattern. Update `sidebar-menu.js` to keep `knowledge-brand-auth` as the top-level menu key. Add Flyway migration to seed these into existing role profiles.
