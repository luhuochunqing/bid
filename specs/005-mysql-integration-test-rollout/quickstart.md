# Quickstart: 运行 MySQL 集成测试

**Feature**: 005-mysql-integration-test-rollout
**Date**: 2026-06-30

## 前置条件

### 1. 本地环境（macOS）

需要本地 MySQL 8.0 容器在 13306 端口运行（基类 `AbstractMysqlIntegrationTest` fallback 路径）。

```bash
# 启动一次性 MySQL 容器（如未存在）
docker run -d --name xiyu-mysql-test -p 13306:3306 \
  -e MYSQL_ROOT_PASSWORD=xiyu_test \
  -e MYSQL_DATABASE=xiyu_bid_verify \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci \
  --sql-mode=ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION

# 验证容器运行
docker ps | grep xiyu-mysql-test
```

**注意**: 基类 `adjustLocalSessionSettings()` 会自动 `SET GLOBAL sql_mode` + `ALTER DATABASE ... COLLATE`，无需手动调整。

### 2. CI 环境（GitHub Actions）

无需任何配置——基类 `AbstractMysqlIntegrationTest` 检测 `GITHUB_ACTIONS=true` 环境变量，自动启动 Testcontainers MySQL 8.0 容器。

## 运行集成测试

### 仅运行本 feature 新增的集成测试

```bash
cd /Users/user/xiyu/worktrees/codex/backend

# EffectiveRoleResolver 集成测试
mvn test -Dtest=EffectiveRoleResolverMysqlIntegrationTest

# TenderCommandService 集成测试
mvn test -Dtest=TenderCommandServiceMysqlIntegrationTest

# 两个一起跑
mvn test -Dtest=EffectiveRoleResolverMysqlIntegrationTest,TenderCommandServiceMysqlIntegrationTest
```

### 运行所有 MySQL 集成测试（含范本）

```bash
cd /Users/user/xiyu/worktrees/codex/backend

# 所有 *MysqlIntegrationTest 类
mvn test -Dtest='*MysqlIntegrationTest'

# 含契约测试
mvn test -Dtest='*MysqlContainerTest,*MysqlIntegrationTest,*ContractTest'
```

### 运行被测类的全部测试（unit + integration）

```bash
cd /Users/user/xiyu/worktrees/codex/backend

# EffectiveRoleResolver 全部测试
mvn test -Dtest='EffectiveRoleResolver*'

# TenderCommandService 全部测试
mvn test -Dtest='TenderCommandService*'
```

### 完整 `mvn test`（含 ArchitectureTest + 全部 unit + 全部 integration）

```bash
cd /Users/user/xiyu/worktrees/codex/backend
mvn test
```

> 预计耗时：本地约 3-5 分钟（含 Spring 上下文启动 + Flyway 全迁移链 + 全部测试）。

## 常见问题

### Q1: 本地无 Docker，集成测试会怎样？

**A**: 测试 fail-fast。基类 `AbstractMysqlIntegrationTest` 不带 `disabledWithoutDocker=true`，无 Docker 时 `adjustLocalSessionSettings()` 抛 `IllegalStateException`，提示"无法连接本地 MySQL 容器"。

**解决**: 安装 Docker Desktop 或启动本地 MySQL 容器（见前置条件）。

### Q2: Flyway 迁移失败（checksum 不匹配）

**A**: 通常因本地 `xiyu_bid_verify` 库有残留的 failed migration 记录。

**解决**:
```bash
# 清理 flyway_schema_history 表
docker exec -it xiyu-mysql-test mysql -uroot -pxiyu_test -e \
  "DELETE FROM xiyu_bid_verify.flyway_schema_history WHERE success=0;"

# 或彻底重建数据库
docker exec -it xiyu-mysql-test mysql -uroot -pxiyu_test -e \
  "DROP DATABASE xiyu_bid_verify; CREATE DATABASE xiyu_bid_verify CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

### Q3: 测试数据残留导致后续测试失败

**A**: 集成测试用 `@BeforeEach` + `JdbcTemplate.update("DELETE ... WHERE id IN (9001..9099)")` 清理，正常情况不会残留。如手动调试插入了非约定 ID 的数据，可手动清理：

```bash
docker exec -it xiyu-mysql-test mysql -uroot -pxiyu_test xiyu_bid_verify -e "
  DELETE FROM tender_assignment_records WHERE tender_id IN (SELECT id FROM tenders WHERE creator_id BETWEEN 9001 AND 9099);
  DELETE FROM tender_attachments WHERE tender_id IN (SELECT id FROM tenders WHERE creator_id BETWEEN 9001 AND 9099);
  DELETE FROM tenders WHERE creator_id BETWEEN 9001 AND 9099;
  DELETE FROM users WHERE id BETWEEN 9001 AND 9099;
"
```

### Q4: Spring 上下文启动失败（Bean 冲突）

**A**: 集成测试用 `@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")` 允许 Bean 覆盖（`@Primary` 测试配置生效）。如启动失败，检查：
- `EffectiveRoleResolverMysqlIntegrationTestConfig` 是否正确标注 `@Primary`
- `NoOpPasswordEncryptionTestConfig` 是否已 `@Import`
- 是否有其他 `@TestConfiguration` 冲突

### Q5: CI 上 Testcontainers 启动超时

**A**: `testcontainers.properties` 已禁用 Ryuk resource reaper（GitHub Actions 兼容）。如仍超时，检查 GitHub Actions runner 的 Docker daemon 状态，或临时跳过：

```bash
# 仅本地用，CI 不应跳过
mvn test -Dtest='EffectiveRoleResolverTest,TenderCommandServiceTest' -Dexclude.tests='*MysqlIntegrationTest'
```

## 验证清单

完成本 feature 后，运行以下命令验证全部通过：

```bash
cd /Users/user/xiyu/worktrees/codex

# 1. 后端全量测试（含新集成测试 + 既有 unit test + ArchitectureTest）
cd backend && mvn test

# 2. 前端构建（确保无副作用）
cd .. && npm run build

# 3. Git 状态确认（仅新增测试类 + spec 文档）
git status
```

预期结果：
- `mvn test` 全绿（既有测试零回归 + 新增集成测试通过）
- `npm run build` 通过
- `git status` 显示新增文件：
  - `backend/src/test/java/com/xiyu/bid/security/EffectiveRoleResolverMysqlIntegrationTest.java`
  - `backend/src/test/java/com/xiyu/bid/security/EffectiveRoleResolverMysqlIntegrationTestConfig.java`
  - `backend/src/test/java/com/xiyu/bid/tender/service/TenderCommandServiceMysqlIntegrationTest.java`
  - `specs/005-mysql-integration-test-rollout/` 下所有文档
