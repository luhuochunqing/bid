# Track: 标讯来源标签标记

## 需求概述
针对已生成的标讯，需要在标讯列表页展示当前标讯的来源：
- 人工录入的标讯展示标签为"人工录入"
- 拉取的标讯展示标签为"外部获取"
- **删除"内部标签"**
- 支持通过来源筛选标讯

---

## Phase 1: 数据层 - Tender 实体新增 sourceType 字段

- [x] **任务 1.1: 数据库迁移**
  - 新增 Flyway 迁移脚本，为 `tenders` 表添加 `source_type` 字段（ENUM: `MANUAL`, `EXTERNAL`）

- [x] **任务 1.2: Tender Entity 更新**
  - 在 `Tender.java` 中添加 `sourceType` 字段
  - 添加 `SourceType` 枚举 (MANUAL, EXTERNAL)

- [x] **任务 1.3: TenderDTO 更新**
  - 在 `TenderDTO.java` 中添加 `sourceType` 字段

- [x] **任务 1.4: Mapper 更新**
  - 在 `TenderMapper.java` 中映射 `sourceType` 字段

---

## Phase 2: 后端逻辑层 - 来源赋值

- [x] **任务 2.1: 人工录入来源标记**
  - 在 `TenderCommandService.create()` 方法中，创建标讯时默认设置 `sourceType = 'MANUAL'`

- [x] **任务 2.2: 外部拉取来源标记**
  - 在 `TenderSearchCriteria` 中添加 `sourceType` 筛选条件

- [x] **任务 2.3: 后端接口更新**
  - 更新 `/api/tenders` GET 接口支持按 `sourceType` 筛选
  - 更新 `TenderSpecification` 支持 sourceType 查询

---

## Phase 3: 前端展示层 - 标签渲染

- [x] **任务 3.1: helpers.js 更新**
  - 更新 `getSourceTagType()` 函数，删除 `internal` 映射
  - 更新 `getSourceText()` 函数，删除 `internal` 映射
  - 添加 `getSourceTypeTagType()` 函数
  - 添加 `getSourceTypeText()` 函数

- [x] **任务 3.2: TenderTable.vue 更新**
  - 修改现有来源展示为使用 `sourceType` 显示"人工录入"或"外部获取"

- [x] **任务 3.3: TenderMobileCards.vue 更新**
  - 同步更新移动端卡片展示

---

## Phase 4: 前端筛选层 - 来源筛选

- [x] **任务 4.1: constants.js 更新**
  - 更新 `SOURCE_OPTIONS`，删除 `internal` 选项
  - 添加 `SOURCE_TYPE_OPTIONS`（全部来源、人工录入、外部获取）

- [x] **任务 4.2: TenderSearchCard.vue 更新**
  - 将"来源"筛选从平台来源改为标讯来源类型
  - 支持按"人工录入"、"外部获取"筛选

- [x] **任务 4.3: useTenderListPage.js 更新**
  - 更新筛选逻辑，支持新的 `sourceType` 参数

---

## Phase 5: 测试验证

- [x] **任务 5.1: 后端测试**
  - 后端测试通过

- [x] **任务 5.2: 前端测试**
  - 更新 `helpers.spec.js` 测试新的 `sourceType` 映射
  - 更新 `constants.spec.js` 测试新的选项

- [x] **任务 5.3: E2E 验证**
  - `npm run build` 成功

---

## 验收标准

1. ✅ 人工录入的标讯在列表页展示"人工录入"标签
2. ✅ 外部拉取的标讯在列表页展示"外部获取"标签
3. ✅ 列表页不再显示"内部"标签
4. ✅ 支持按来源类型（人工录入/外部获取）筛选标讯
5. ✅ 移动端卡片同步显示正确的标签
6. ✅ 后端单元测试通过
7. ✅ 前端单元测试通过 (18 tests)
8. ✅ `npm run build` 成功
