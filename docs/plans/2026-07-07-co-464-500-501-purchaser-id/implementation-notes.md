# 实施笔记 — CO-464 + CO-500 + CO-501

> 任务：Tender 新增 purchaserId + 关联商机两步校验
> 分支：`agent/zcode/co-464-500-501-purchaser-id`
> 起始：2026-07-07

## 决策与权衡

### 1. purchaserId 类型选 Long
- Tender 实体现有 ID 类字段（projectId、projectManagerId 等）统一用 Long
- CRM 返回的 data 是 integer，但投标系统存储统一用 Long，避免将来 ID 越界

### 2. 拆纯核心 TenderSubjectConsistencyPolicy
- 第二步本地校验有 4 种业务分支（空值/匹配group/匹配subject/都不匹配）
- 满足 RULES.md §2.1 "复杂业务规则提炼到纯核心"的阈值
- 受 FPJavaArchitectureTest 门禁保护

### 3. 事务边界拆分（CO-325 防护）
- linkCrmOpportunity 原本是类级 @Transactional
- CRM HTTP 校验放事务外（persistCrmLink 用方法级 @Transactional）
- persistCrmLink 必须是包级方法（非 private），否则 Spring AOP 自调用失效

### 4. CheckResult 而非 Optional<Long>
- CrmTenderSubjectChecker 返回 CheckResult 对象
- 业务错误（NOT_IN_CRM/NOT_IN_GROUP）作为值返回，调用方决定如何抛
- 仅 CRM 不可用抛 BusinessException

## 走过的弯路（教训）

见 plan v4 第十节。

## ⚠️ 未联调风险声明（提交时已知）

**本次提交的代码接口未做真实联调验证**，以下风险需在主工作区（trae）联调时确认：

### 未验证项
1. **CRM `check-tender-subject` 接口真实响应**
   - `code` 字段是否真的为字符串 "0"/"1"（`CrmResponseHandler.parse` 用 `asInt(-1)` 兼容，但未实测）
   - `data` 字段通过时是否真的返回招标主体 ID（飞书文档示例有 `data: false` 和 `data: 0` 两种，实际行为未验证）
   - `msg` 字段区分两种子错误的真实文本（当前用关键字"不存在"/"不属于"/"集团"匹配，可能不准）
   - lessons §12 警告"接口文档曾写错"，**必须用真实响应校准**

2. **Flyway V1146 迁移**
   - SQL 语法在 MySQL 8.0 上未实测
   - U1146 回滚脚本未实测

3. **端到端业务流**
   - 前端 `useCrmOpportunitySelector.js` 的 `chance.groupName` / `chance.tenderSubject` 字段是否真的存在于商机 VO 响应中（代码读到的 `CustomerChanceVO.java` 有这俩字段，但前端拿到的真实 JSON 未验证）
   - 关联商机的完整链路（前端选商机 → 透传字段 → 后端两步校验 → 落库 purchaserId）未端到端跑通

### 已验证项（单元测试层面）
- 见下方"验证结果汇总"，所有 mock 测试全绿
- 但 mock 测试的 CRM 响应是我**根据文档推测的格式**，不是真实响应

### 联调时必须做的事
1. 在主工作区启动后端 + 前端
2. 跟 CRM 同事确认 `check-tender-subject` 接口可用
3. 用真实标讯 + 真实商机调 `PATCH /api/tenders/{id}/crm-opportunity`
4. 抓真实 CRM 响应，校准 `CrmTenderSubjectChecker.interpret()` 的 `msg` 匹配规则
5. 验证 Flyway V1146 迁移成功 + U1146 回滚成功
6. 前端真实操作一次"关联商机"，验证 purchaserId 落库

## 待确认事项

- [ ] CRM msg 字段匹配规则联调时完善
- [ ] 前端 emit('linked') 所有消费方定位

## 实施过程中遇到的问题与决策

### 1. Java record 静态工厂方法命名冲突
- 问题：`record Result(boolean allowed, ...)` 内的 `static Result allowed()` 与 record 自动生成的 `allowed()` accessor 冲突，编译报错
- 解决：改名为 `Result.ok()`，避免与 record component 同名
- 文件：`TenderSubjectConsistencyPolicy.java`

### 2. 现有测试的构造函数需同步更新
- `TenderCommandService` 构造函数加了 `CrmTenderSubjectChecker` 依赖
- 受影响测试：`TenderCommandServiceTest`、`TenderCommandServiceLinkCrmOpportunityDedupTest`
- 两个测试都加了 `@Mock CrmTenderSubjectChecker` 字段

### 3. TenderCrmLinkIntegrationTest 需 MockBean + 字段补全
- CO-329 的 `@SpringBootTest` 测试假设关联商机能直接成功
- CO-501 新增的 CRM 校验 + 本地一致性校验让它失败
- 解决：
  - `@MockBean CrmTenderSubjectChecker` 替换真实 HTTP 调用
  - tender 加 `purchaserName("测试招标主体")`
  - TenderCrmLinkRequest 加 `chanceGroupName/chanceTenderSubject` 匹配

### 4. MySQL 集成测试在本工作区跑不通（环境问题，非回归）
- `TenderCommandServiceMysqlIntegrationTest` 需要真实 MySQL 容器
- 本工作区（zcode）Docker Desktop 未运行，7 个测试 errors
- 这些在 main 基线上也是同样失败，**不是本次改动引起的回归**
- 联调阶段在主工作区（trae）验证

### 5. Flyway 迁移测试同理（Docker 不可用）
- `FlywayMysqlContainerTest` 失败原因：Could not find a valid Docker environment
- V1146 + U1146 SQL 已写好，主工作区联调时验证

## 验证结果汇总

| 验证项 | 结果 |
|---|---|
| TenderSubjectConsistencyPolicyTest（纯核心单测） | ✅ 7 测试全绿 |
| CrmTenderSubjectCheckerTest（CRM 调用单测） | ✅ 10 测试全绿 |
| TenderMapperTest（映射器测试，含新 purchaserId 断言） | ✅ 11 测试全绿 |
| TenderCommandServiceLinkCrmOpportunityDedupTest（编排测试） | ✅ 7 测试全绿 |
| TenderCommandServiceTest（已有测试） | ✅ 17 测试全绿 |
| TenderCrmLinkIntegrationTest（CO-329 集成测试，已适配） | ✅ 1 测试全绿 |
| FPJavaArchitectureTest（纯核心合规） | ✅ 8 测试全绿 |
| ArchitectureTest（架构边界） | ✅ 28 测试全绿 |
| 前端 line-budgets | ✅ passed |
| 前端 front-data-boundaries | ✅ passed |
| 前端 doc-governance | ✅ passed（112 目录） |
| tender + crm 模块全量 | 611 测试，0 Failures，7 Errors（全是 MySQL 集成测试环境问题） |
| FlywayMysqlContainerTest | ❌ Docker 不可用（环境问题，非代码问题） |

## 最终文件清单（实际改动）

### 后端（11 文件）
1. `backend/src/main/resources/db/migration-mysql/V1146__add_tender_purchaser_id.sql`（新建）
2. `backend/src/main/resources/db/rollback/migration-mysql/U1146__add_tender_purchaser_id.sql`（新建）
3. `backend/src/main/java/com/xiyu/bid/entity/Tender.java`（+purchaserId 字段）
4. `backend/src/main/java/com/xiyu/bid/tender/dto/TenderRequest.java`（+purchaserId 字段）
5. `backend/src/main/java/com/xiyu/bid/tender/dto/TenderDTO.java`（+purchaserId 字段）
6. `backend/src/main/java/com/xiyu/bid/tender/dto/TenderCrmLinkRequest.java`（+chanceGroupName/chanceTenderSubject）
7. `backend/src/main/java/com/xiyu/bid/tender/service/TenderMapper.java`（4 处映射）
8. `backend/src/main/java/com/xiyu/bid/tender/service/TenderCommandService.java`（linkCrmOpportunity 拆事务 + 两步校验）
9. `backend/src/main/java/com/xiyu/bid/tender/controller/TenderController.java`（改用 req 重载）
10. `backend/src/main/java/com/xiyu/bid/crm/config/CrmProperties.java`（+checkTenderSubjectPath）
11. `backend/src/main/java/com/xiyu/bid/crm/application/CrmTenderSubjectChecker.java`（新建）
12. `backend/src/main/java/com/xiyu/bid/tender/core/TenderSubjectConsistencyPolicy.java`（新建纯核心）

### 前端（2 文件）
13. `src/views/Bidding/detail/components/useCrmOpportunitySelector.js`（emit +2 字段）
14. `src/views/Bidding/detail/DetailPage.vue`（onCrmOpportunityLinked 透传字段）

### 测试（5 文件）
15. `TenderSubjectConsistencyPolicyTest.java`（新建）
16. `CrmTenderSubjectCheckerTest.java`（新建）
17. `TenderMapperTest.java`（+4 个映射断言方法）
18. `TenderCommandServiceLinkCrmOpportunityDedupTest.java`（重写，含 CO-501 场景）
19. `TenderCommandServiceTest.java`（构造函数 +1 mock）
20. `TenderCrmLinkIntegrationTest.java`（@MockBean + 字段补全）

### 文档（1 文件）
21. `docs/plans/2026-07-07-co-464-500-501-purchaser-id/implementation-notes.md`（本文件）
