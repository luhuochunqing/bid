# 非集成缺口补齐实施执行单

日期：2026-04-21  
基线文档：

- [需求完成度核查-非集成范围-2026-04-21.md](/Users/user/xiyu/xiyu-bid-poc/docs/需求完成度核查-非集成范围-2026-04-21.md)
- [2026-04-21-non-integration-gap-close-eng-review.md](/Users/user/xiyu/xiyu-bid-poc/docs/plans/2026-04-21-non-integration-gap-close-eng-review.md)

## 目标

把以下 4 项从“第一轮接线完成”推进到“具备稳定验收条件”：

1. 智能日程与预警
2. 标讯分发与指派跟进
3. 超前预测与市场洞察
4. AI 标书检查中的文本质量辅助

## 作业流程

本次按 Everything Claude Code 标准流程执行：

1. `plan`
2. `tdd`
3. `code-review`
4. `refactor-clean`

## 强制补项

1. Flyway migration
2. 读模型与主读链路一致性
3. Split-First 重构
4. 关键测试补齐

## 专家分工

### 专家 A：Alerts / Workbench

负责：

- 工作台切换到 `GET /api/workbench/schedule-overview`
- 告警状态迁移与查询 contract
- 工作台与告警相关测试

写集：

- `src/views/Dashboard/**`
- `src/api/modules/alerts.js`
- `backend/src/main/java/com/xiyu/bid/alerts/**`
- `backend/src/main/java/com/xiyu/bid/workbench/**`
- `backend/src/main/java/com/xiyu/bid/calendar/**`

### 专家 B：Tenders

负责：

- 前端 canonical status 映射
- 批量领取/指派/状态更新闭环
- 部分成功/失败测试

写集：

- `src/views/Bidding/List.vue`
- `src/views/Bidding/bidding-utils*`
- `src/stores/bidding*`
- `src/api/modules/tenders/**`
- `backend/src/main/java/com/xiyu/bid/batch/**`

### 专家 C：Customer Opportunity

负责：

- `CustomerOpportunityCenter.vue` Split-First 拆分
- convert-to-project 状态回写闭环
- 查询/转化/幂等等测试

写集：

- `src/views/Bidding/CustomerOpportunityCenter.vue`
- `src/views/Bidding/customer-opportunity/**`
- `src/api/modules/customerOpportunity.js`
- `backend/src/main/java/com/xiyu/bid/marketinsight/**`

### 专家 D：AI Quality / Migrations

负责：

- quality 新表 Flyway migration
- `useProjectDetailQualityCheck` 抽离
- AI 质量 contract/latest/adopt/ignore/empty-state 测试

写集：

- `src/composables/projectDetail/**`
- `src/api/modules/ai/**`
- `src/components/project/detail/**`
- `backend/src/main/java/com/xiyu/bid/projectquality/**`
- `backend/src/main/resources/db/migration-mysql/**`

## 架构约束

- 纯核心负责业务决策
- 应用服务只做编排
- 任何类不得同时承担规则计算、数据访问、DTO 转换、状态写入三类以上职责
- 单文件超过 300 行前必须拆分

## 验收口径

### 前端

- 页面和路由真实可达
- 页面调用的方法在 API 模块真实实现
- 不保留隐藏入口、假按钮、旧 demo 兜底

### API / Controller

- 返回结构与前端消费一致
- 不直出 entity
- 状态词、动作命名统一

### 测试

- AI 质量：run/latest/adopt/ignore/empty
- 商机：convert 回写与查询一致性
- 标讯：批量部分成功/失败与列表回刷
- 工作台/告警：状态迁移与主读链路

## 验证命令

```bash
cd /Users/user/xiyu/xiyu-bid-poc/.worktrees/integration
npm run test:unit
npm run build
cd backend
mvn -DskipTests compile
mvn test
```
