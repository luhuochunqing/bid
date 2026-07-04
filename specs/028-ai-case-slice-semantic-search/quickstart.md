# Quickstart: AI 案例切片语义检索

## Prerequisites

1. 已在服务器上执行 `scripts/slice_all_v9.py` 并生成 `/tmp/winbid_slices/project_*.jsonl`。
2. MySQL 数据库可访问，`Flyway` 迁移能正常执行。
3. 已配置 embedding 服务的 API key（通义千问 / OpenAI / 豆包）。
4. （可选）本地具备 Docker 环境，以便 `*MysqlIntegrationTest` 通过 Testcontainers 运行；若缺失，相关集成测试会报错，但不影响功能代码。

## Local Development Steps

### 1. Run Flyway Migration

```bash
cd /Users/user/xiyu/worktrees/trae/backend
XIYU_DEV_CONFIRMED=1 ./start.sh
# 或使用 mvn 启动，确保 Flyway 自动执行 V1135__create_bid_case_slice.sql
```

### 2. Import Slice Metadata

```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--import-case-slices=true"
```

或直接用 JAR：

```bash
java -jar backend/target/*.jar --import-case-slices=true
```

### 3. Generate Embeddings

启动后调用批量向量化接口（或等待自动任务）：

```bash
curl -X POST "http://127.0.0.1:18089/api/case-slices/admin/batch-embed?batchSize=100" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

重复调用直到 `remaining` 为 0。

### 4. Test Recommendation

```bash
curl "http://127.0.0.1:18089/api/case-slices/recommend/by-query?query=技术方案&topK=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

或绑定评分项：

```bash
curl "http://127.0.0.1:18089/api/case-slices/recommend?projectId=1&scoringItemId=42&topK=10" \
  -H "Authorization: Bearer $JWT_TOKEN"
```

## Verification Checklist

- [x] `bid_case_slice` 表已创建
- [x] 批量导入后可写入 8144 条切片记录
- [x] 批量向量化接口可对未处理切片生成 embedding
- [x] `/api/case-slices/recommend/by-query` 返回语义相关的结果
- [x] `/api/case-slices/recommend` 对有效 projectId + scoringItemId 返回结果
- [x] 无管理员权限访问 `POST /api/case-slices/admin/batch-embed` 返回 403

## Cross-Cutting Gates Results

> 以下命令在当前分支 `agent/claude/ai-case-slice-semantic-search` 执行并记录。

| Gate | Command | Result |
|---|---|---|
| ArchitectureTest | `cd backend && mvn test -Dtest=ArchitectureTest` | 通过 |
| FPJavaArchitectureTest + MaintainabilityArchitectureTest | `cd backend && mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest` | 通过 |
| Line Budgets | `npm run check:line-budgets` | 通过（guarded_changes=28） |
| Feature Tests | `cd backend && mvn test -Dtest=BidCaseSlice*Test,CosineSimilarityPolicyTest,BidCaseSliceMatchPolicyTest,BatchEmbeddingServiceTest,CaseSliceJsonlImporterTest,QwenEmbeddingClientTest,OpenAiCompatibleClientEmbeddingTest,BidCaseSliceArchitectureTest` | 通过 |
| Backend Full Test Suite | `cd backend && mvn test` | 见下方说明 |

### Backend Full Test Suite 说明

- 执行结果：`Tests run: 5719, Failures: 0, Errors: 30, Skipped: 20`。
- 本功能相关测试全部通过；30 个 Errors 全部来自 **需要 Docker 的 Testcontainers MySQL 集成测试**，与本功能无直接关联。
- 受环境影响的测试类包括：
  - `PlatformAccountBorrowServiceMysqlIntegrationTest`
  - `EffectiveRoleResolverMysqlIntegrationTest`
  - `FlywayMysqlContainerTest`
  - `TenderCommandServiceMysqlIntegrationTest`
- 根因：当前环境未检测到可用 Docker，`flyway-mysql` profile 无法启动 MySQL container，导致 ApplicationContext 加载失败。
- 建议在具备 Docker 的环境（CI / 本地启动 Docker Desktop）重新执行 `mvn test` 以获取完整绿色基线。

## Notable Deviations / Decisions

1. **纯核心策略的测试可替换性**
   - `BidCaseSliceRecommendAppService` 未将 `BidCaseSliceMatchPolicy` 声明为 Spring Bean，而是直接 `new BidCaseSliceMatchPolicy()` 实例化，保持纯核心无 Spring 依赖。
   - 为单元测试提供包级可见的 `setMatchPolicy(...)` 方法，测试通过 Mockito 替换策略，避免 Spring 上下文污染。

2. **ArchUnit 控制器的豁免范围**
   - `BidCaseSliceArchitectureTest` 中“控制器仅依赖应用/服务层”的规则限定为 `haveSimpleNameContaining("BidCaseSlice")`，避免对历史 controller 产生误报。

3. **应用层基础设施依赖的既有豁免**
   - 规则 `application 包不直接依赖 infrastructure` 对历史应用服务（归档导出、案例 CRUD/搜索/知识库等）维持豁免；本次新增类（`BatchEmbeddingService`、`BidCaseSliceRecommendAppService` 等）遵循新规。

## Locks

- 任务开始时通过 `npm run agent:lock-acquire` 获取的以下锁，在任务完成后已释放：
  - `backend/src/main/resources/db/migration-mysql/V1135__create_bid_case_slice.sql`
  - `backend/src/main/java/com/xiyu/bid/ai/client`
  - `backend/src/main/java/com/xiyu/bid/casework`
