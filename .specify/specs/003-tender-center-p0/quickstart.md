# Quickstart: 标讯中心 P0

Recommended implementation order (fastest → safe):

## Step 1: Role rename (trivial, 5 min)

Modify `RoleProfileCatalog.java` — change `sales` display name to "项目负责人".
Update `src/utils/permission.js` or i18n files for frontend display.
Fix e2e assertions.

## Step 2: V129 — SourceType expansion (30 min)

Write migration + rollback. Update `Tender.SourceType` inner enum.
Update `TenderCommandService.setSourceType()` per creation channel.
Update frontend filter options in `TenderSearchCard.vue`.
Remove duplicate `TenderSourceType.java`.

## Step 3: V130 + V132 — 3-section evaluation + assignment type (2-3 days)

Tables first → Entities + Repositories → Policy (pure core) → Service → Controller → Frontend form.

## Step 4: V131 — TenderSource persistence (1 day)

Table → Entity + Repository → Service (with encryption) → Controller → Frontend dialog.

## Step 5: Transfer flow (1 day)

V132 done in step 3. Service → Controller → TransferDialog.vue → TenderActionMenu.

## Step 6: E2E + regression (1 day)

New `tender-transfer-flow.spec.js`. Run full suite.

## Useful commands

```bash
# Check next migration version
ls backend/src/main/resources/db/migration-mysql/ | sort -V | tail -1

# Create migration
touch backend/src/main/resources/db/migration-mysql/V129__tender_source_type_expand.sql
touch backend/src/main/resources/db/rollback/migration-mysql/U129__tender_source_type_expand.sql

# Run architecture test
mvn -f backend/pom.xml test -Dtest=ArchitectureTest -q

# Run tender-specific tests
mvn -f backend/pom.xml test -Dtest="TenderCommandServiceTest,TenderEvaluationServiceTest"
```
