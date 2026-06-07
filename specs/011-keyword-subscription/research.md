# Research: 标讯关键词订阅

## Unknowns Resolved

### 1. 关键词匹配策略

**Decision**: 基于 SQL LIKE 模糊匹配（标讯标题 + 内容字段）

**Rationale**: 
- 初期用户量和关键词数量较少，LIKE 查询性能足够
- 实现简单，无需引入 Elasticsearch 等外部依赖
- AND 逻辑：`title LIKE '%关键词1%' AND title LIKE '%关键词2%'`
- OR 逻辑：`title LIKE '%关键词1%' OR title LIKE '%关键词2%'`
- 范围扩展到标讯的 `title` 和 `content` 两个字段

**Alternatives considered**:
- Elasticsearch 全文搜索：引入运维复杂度，当前规模不需要
- MySQL FULLTEXT 索引：需要额外配置，匹配行为不符合中文分词需求

### 2. 站内通知系统对接

**Decision**: 扩展现有的通知表，增加 `keyword_match_result_id` 关联字段

**Rationale**:
- 项目已有通知系统（`sys_notification` 表及相关 Service）
- 本功能只需在通知中关联匹配结果 ID，前端据此跳转到匹配结果列表
- 通知标题固定模板："【关键词订阅】您的订阅规则「{规则名称}」匹配到 {N} 条新标讯"

**Alternatives considered**:
- 新建独立通知表：造成数据冗余和通知管理分散

### 3. 定时任务执行方式

**Decision**: 使用 Spring `@Scheduled(cron = "0 0 2 * * ?")` 每天凌晨 2 点执行

**Rationale**:
- 项目现有 Spring Boot 基础设施，`@Scheduled` 无需额外配置
- 可配置 cron 表达式，支持运维调整执行时间
- 失败自动重试：下次执行周期自动重试（不单独实现重试逻辑）

**Alternatives considered**:
- Quartz：功能更强但引入不必要的复杂度
- 手动触发 + 分布式锁：当前单节点部署不需要

### 4. 数据保留策略

**Decision**: 匹配历史保留 90 天，定时清理

**Rationale**:
- 匹配结果数据随时间线性增长，需要清理
- 用单独的清理任务每天凌晨执行：DELETE 90 天前的 match_result 及关联 match_item

**Alternatives considered**:
- 永久保留：数据库膨胀不可控
- 按条数限制：不如时间策略直观

### 5. 并发与事务处理

**Decision**: 匹配任务在单个事务中完成
- 查询所有活跃规则 → 逐个规则匹配 → 批量写入匹配结果
- 每个规则的匹配结果在一个事务内写入
- 匹配完成后批量创建通知

**Rationale**:
- 每天一次的批量任务，非高并发场景
- 单个事务确保完整性：全部匹配完成才写入通知
- 如果中间失败，已写入的匹配结果保留（下次匹配继续匹配新标讯）
