## 1. Backend — DTO & API Extension
- [ ] 1.1 Read existing `InitiationDto`, `InitiationViewDto`, `ProjectDto` to understand current field set
- [ ] 1.2 Extend `InitiationDto` with PRD fields (基本信息 9 字段 + 投标信息 12 字段)
- [ ] 1.3 Add `CustomerInfoRow` DTO (15 fields) for table data
- [ ] 1.4 Extend `ProjectDto` / list query for 16 column projection
- [ ] 1.5 Add export endpoint `/api/projects/export` (Excel generation)
- [ ] 1.6 Write unit tests for new DTO validation

## 2. Backend — Flyway Migration
- [ ] 2.1 Create `V{next}__project_list_initiation_alignment.sql`
- [ ] 2.2 Add nullable columns to `project_initiation_details` for new fields
- [ ] 2.3 Add `customer_info_json` JSON column for 15×14 table
- [ ] 2.4 Rollback script in `db/rollback/`

## 3. Frontend — Project List Page
- [ ] 3.1 Refactor `src/views/Project/List.vue` table columns (5 → 16)
- [ ] 3.2 Add search/filter controls matching all 16 columns
- [ ] 3.3 Add export button with download handler
- [ ] 3.4 Set table min-width 1400px, enable horizontal scroll
- [ ] 3.5 Update `statusOptions` to match 6 lifecycle stages
- [ ] 3.6 Write component tests for new columns and export

## 4. Frontend — Initiation Stage Form
- [ ] 4.1 Rebuild `InitiationStage.vue` with 4-section layout
- [ ] 4.2 Implement 基本信息 3-per-row grid layout
- [ ] 4.3 Implement 投标信息 mixed layout (3-per-row + single-row)
- [ ] 4.4 Implement 客户信息 table (15 cols × 14 rows, horizontal scroll)
- [ ] 4.5 Add 招标文件 upload zone
- [ ] 4.6 Add AI风险评估 button (calls `/api/ai/risk-assessment`)
- [ ] 4.7 Wire form submission to existing `submitInitiation` API
- [ ] 4.8 Write component tests for all 4 sections

## 5. Integration & Verification
- [ ] 5.1 `npm run build` passes
- [ ] 5.2 `npm run test:unit` passes
- [ ] 5.3 `cd backend && mvn test -Dtest=ArchitectureTest` passes
- [ ] 5.4 `cd backend && mvn test` affected tests pass
- [ ] 5.5 Manual UI verification in browser
- [ ] 5.6 Update component header comments
