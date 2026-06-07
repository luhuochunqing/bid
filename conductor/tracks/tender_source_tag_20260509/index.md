# Track: 标讯来源标签标记

## 任务概述
为标讯列表页添加来源标签显示，支持按"人工录入"/"外部获取"筛选，删除"内部标签"。

## 状态
- [x] 已完成

## 负责人
- @cursor (当前 Agent)

## 完成日期
- 2026-05-09

## 相关文件
- [plan.md](./plan.md)

## 实施总结

### Phase 1: 数据层 ✅
- [x] 1.1 数据库迁移脚本 (V108, V114)
- [x] 1.2 Tender Entity 更新 (sourceType + SourceType 枚举)
- [x] 1.3 TenderDTO 更新
- [x] 1.4 TenderMapper 更新

### Phase 2: 后端逻辑层 ✅
- [x] 2.1 人工录入来源标记 (TenderCommandService)
- [x] 2.2 TenderSearchCriteria 增加 sourceType
- [x] 2.3 TenderSpecification 支持 sourceType 筛选

### Phase 3: 前端展示层 ✅
- [x] 3.1 helpers.js 更新 (新增 getSourceTypeTagType, getSourceTypeText)
- [x] 3.2 TenderTable.vue 更新
- [x] 3.3 TenderMobileCards.vue 更新

### Phase 4: 前端筛选层 ✅
- [x] 4.1 constants.js 更新 (SOURCE_OPTIONS, SOURCE_TYPE_OPTIONS)
- [x] 4.2 TenderSearchCard.vue 更新
- [x] 4.3 useTenderListPage.js 更新

### Phase 5: 测试验证 ✅
- [x] 5.1 前端测试通过 (18 tests)
- [x] 5.2 npm run build 成功

## 验收标准

1. ✅ 人工录入的标讯在列表页展示"人工录入"标签
2. ✅ 外部拉取的标讯在列表页展示"外部获取"标签
3. ✅ 列表页不再显示"内部"标签
4. ✅ 支持按来源类型（人工录入/外部获取）筛选标讯
5. ✅ 移动端卡片同步显示正确的标签
6. ✅ npm run build 成功
7. ✅ 前端单元测试通过 (18 tests)

## 变更文件清单

### 后端 (Java)
- `backend/src/main/java/com/xiyu/bid/entity/Tender.java` - 添加 sourceType 字段和 SourceType 枚举
- `backend/src/main/java/com/xiyu/bid/tender/dto/TenderDTO.java` - 添加 sourceType 字段
- `backend/src/main/java/com/xiyu/bid/tender/dto/TenderRequest.java` - 添加 sourceType 字段
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderMapper.java` - 映射 sourceType
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java` - 设置默认 sourceType
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderSearchCriteria.java` - 添加 sourceType
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderSpecification.java` - 支持 sourceType 筛选
- `backend/src/main/resources/db/migration-mysql/V114__tender_source_type.sql` - MySQL 迁移
- `backend/src/main/resources/db/migration/V108__tender_source_type.sql` - PostgreSQL 迁移

### 前端 (Vue/JS)
- `src/views/Bidding/list/helpers.js` - 新增 sourceType 相关函数
- `src/views/Bidding/list/constants.js` - 更新 SOURCE_OPTIONS, SOURCE_TYPE_OPTIONS
- `src/views/Bidding/list/components/TenderTable.vue` - 使用 sourceType 标签
- `src/views/Bidding/list/components/TenderMobileCards.vue` - 使用 sourceType 标签
- `src/views/Bidding/list/components/TenderSearchCard.vue` - 使用 sourceType 筛选
- `src/views/Bidding/list/useTenderListPage.js` - 传递 sourceType 参数
- `src/views/Bidding/list/helpers.spec.js` - 新增测试用例

## 清理收口 (2026-05-10)

### 废弃目录清理
- [x] 删除废弃的 `db/migration/` 目录（PostgreSQL 迁移，75 个文件）
- [x] 保留活跃的 `db/migration-mysql/` 目录（MySQL 迁移）

### 文档更新
- [x] 更新 `CLAUDE.md` 数据库迁移规范
- [x] 创建 `.wiki/pages/lessons-learned.md` 工程经验总结

### CI 配置确认
- [x] 确认 CI 配置只测试 MySQL (`FlywayMysqlContainerTest`)
- [x] 确认无 PostgreSQL 相关测试引用

### 经验教训
1. **废弃即删除**：PostgreSQL 支持移除后应及时清理 `migration/` 目录
2. **CI 即技术栈声明**：CI 配置必须与项目实际技术栈严格一致
3. **配置与目录同步**：flyway.locations 配置变更后需验证对应目录状态
