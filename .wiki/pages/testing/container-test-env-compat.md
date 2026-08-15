---
title: 容器测试环境兼容（Testcontainers / Docker Desktop / MySQL sql_mode）
space: engineering
category: pitfalls
tags: [testing, testcontainers, docker, flyway, mysql, sql_mode, schema-validation]
sources:
  - backend/pom.xml
  - backend/src/test/java/com/xiyu/bid/support/AbstractMysqlIntegrationTest.java
  - backend/src/test/java/com/xiyu/bid/support/FlywayMysqlContainerTest.java
  - backend/src/main/resources/db/migration-mysql/V1186__fix_tender_event_logs_status_enum.sql
backlinks:
  - _index
  - testing/_index
created: 2026-08-15
updated: 2026-08-15
health_checked: 2026-08-15
---
# 容器测试环境兼容（Testcontainers / Docker Desktop / MySQL sql_mode）

> 容器契约测试（Flyway迁移回归 / schema validation）在真实 MySQL 上运行的兼容性坑集合。
> 2026-08-15 因部署后容器测试 SERVER 失败被触发，一次性修复三类问题。

## 1. Testcontainers 与 Docker Desktop 版本兼容

**症状**：容器测试启动报 `IllegalStateException: Could not find a valid Docker environment`，
或 docker-java 调用 Docker API 返回空 JSON + **Status 400**。

**根因**：Testcontainers 1.19.3 / 1.20.4 依赖的 docker-java 3.3.x 与 Docker Desktop 4.54 不兼容。

**修复**：`backend/pom.xml` `<testcontainers.version>` 升级到 **1.21.4**（docker-java 3.4.2）。

**副作用**：CI（GitHub Actions Linux Docker daemon）不受影响，但本地 macOS Docker Desktop 必须升级。

## 2. MySQL 容器 sql_mode 对齐（V1077 / V1092）

**症状**：迁移 V1077 报 `Error 1292: Incorrect datetime value: '0000-00-00 00:00:00'`；
V1092 临时表 JOIN 报 collation 冲突。

**根因**：Testcontainers 的 MySQL 8.0 容器默认 sql_mode 含 `NO_ZERO_DATE` / `NO_ZERO_IN_DATE`，
拒绝 V1077 使用的 `'0000-00-00'` 字面量；默认 collation 也与生产 `utf8mb4_unicode_ci` 不一致。

**修复**：所有容器契约测试的 MySQLContainer 统一加参数（对齐 `AbstractMysqlIntegrationTest`）：

```java
new MySQLContainer<>("mysql:8.0")
    .withCommand(
        "--character-set-server=utf8mb4",
        "--collation-server=utf8mb4_unicode_ci",
        "--sql-mode=ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION");
```

**涉及文件**：`FlywayMysqlContainerTest` / `V97` / `V1102` / `V1095` / `V117` / `BusinessTableCommentBackfillTest`。

## 3. JDBC 多语句支持（V1092）

**症状**：测试重放 V1092 报 `Error 1064 syntax error`。

**根因**：V1092 脚本含多条语句（START TRANSACTION + CREATE + ...），JDBC 默认不允许多语句。

**修复**：JDBC URL 追加 `allowMultiQueries=true`。**首个参数用 `'?'`，后续用 `'&'`**，
不能直接拼 `'&'`（否则 `&allowMultiQueries=true` 会被误当数据库名一部分）。

## 4. Hibernate schema-validation enum/VARCHAR 漂移

**症状**：`ddl-auto=validate` 报 `wrong column type: found [varchar], expecting [enum]`。

**两种修复模式**（按语义选其一）：
1. **改库**：新建迁移把列改成 ENUM（如 `V1186`：`tender_event_logs.status` VARCHAR(20)→ENUM('SENT','FAILED')）。
2. **改实体**：Hibernate 6 `@Enumerated(EnumType.STRING)` 默认期待 MySQL ENUM，若列是有意设计为 VARCHAR，
   需加 `@JdbcTypeCode(SqlTypes.VARCHAR)`（如 `WarehouseAttachmentEntity.type` 对齐 V1136 的 VARCHAR 定义）。

## 5. 构建缓存过期陷阱

**症状**：Flyway 报 `Unable to obtain inputstream for resource: db/migration-mysql/V1143...`，
但文件存在且被 git 跟踪。

**根因**：rebase 拉入新迁移后，`target/classes` 未重建，classpath 里的迁移列表与磁盘不一致。

**修复**：`mvn clean` 后重跑。**任何新增/修改迁移后，若容器测试报资源找不到，先 clean 再怀疑其他。**

## 6. 本地 vs CI 双路径（AbstractMysqlIntegrationTest）

`AbstractMysqlIntegrationTest` 按 `GITHUB_ACTIONS` 分流：
- **CI**（`GITHUB_ACTIONS=true`）：走 Testcontainers。
- **本地**（非 CI）：fallback 到手动容器 `localhost:13306/xiyu_bid_verify`（root/xiyu_test）。

本地未启动该容器时的失败（`Could not initialize class`）是**环境依赖**，非代码回归。
验证 Testcontainers 路径需显式 `GITHUB_ACTIONS=true mvn test`。

## 7. 打包被容器测试阻断时的处置 SOP（临时表残留）

**症状**：`package-release.sh` 打包时，容器契约测试 `FlywayMysqlContainerTest.v1092MergesUsersWhenTargetRoleAlreadyExists`
报 `Duplicate entry 'bidAdmin' for key 'tmp_role_mappings.PRIMARY'`。

**根因**：V1092 脚本用 `CREATE TEMPORARY TABLE IF NOT EXISTS tmp_role_mappings` 建临时表。
MySQL 临时表是**会话级**的，测试用例手动重放整份脚本时，若复用了 Context 启动时 Flyway
建过该临时表的连接池会话，`IF NOT EXISTS` 不会重建，第 2 次 INSERT 撞主键。

> **关键澄清**：这是**测试重放特有**的假阳性。生产/测试环境 Flyway 每个迁移版本只执行一次、
> 不会重放，所以生产部署**不会**触发该冲突。V1092 脚本受 Flyway checksum 保护，**禁止修改**。

**处置分层（按顺序判断，不要一上来绕门禁）**：

1. **判定是"测试重放假阳性"还是"真迁移/生产问题"**
   - 失败方法名含 `v1092` / 重放类，报错是 `Duplicate entry ... for key 'tmp_xxx.PRIMARY'`
     → 测试重放特有，生产不受影响。
   - 报错来自迁移本身（如 `Error 1292/1064/1267`）→ 可能反映生产风险，需当真问题处理。

2. **测试重放假阳性** → 按测试层修，**绝不碰迁移脚本**。推荐改为**独立连接重放**：
   直接从容器拿一条新连接（不经连接池），临时表与会话绑定、天然隔离，无需逐个 DROP：

   ```java
   try (Connection conn = MYSQL.createConnection("?allowMultiQueries=true");
        Statement stmt = conn.createStatement()) {
       stmt.execute(v1092Script);
   }
   ```

3. **临时绕过** → 仅当判定为纯测试问题且时间紧，才用逃生阀
   `XIYU_SKIP_CONTAINER_TEST=true` 跳过容器测试，但必须在 PR 注明"已绕过、待根治"并事后补修。

4. **长期根治** → 重放迁移脚本的测试统一走独立连接（见第 2 步），避免为每张临时表逐个堆 DROP。

## 副作用（Cross-Module Impact）

- **AbstractMysqlIntegrationTest** 的本地 fallback 依赖手动容器 `localhost:13306`，未启动即失败。
- **CrmTenderSubjectChecker** 依赖真实 CRM 外部服务，测试环境不可用时 `createTender` 相关测试报
  `招标主体校验服务暂不可用`（预存环境依赖，非本次回归）。
- 容器测试每次启动独立 MySQL 容器，耗时 30-40s/类，全量跑约 5-6 分钟。