---
title: Flyway 迁移陷阱集
space: engineering
category: guide
tags: [Flyway, 数据库迁移, MySQL, collation, 版本号冲突, baseline-on-migrate, 回滚脚本]
sources:
  - backend/src/main/resources/db/migration-mysql/
  - backend/src/main/resources/application-mysql.yml
  - .wiki/pages/lessons-learned.md
  - .wiki/pages/production-deployment-lessons.md
backlinks:
  - _index
  - lessons-learned
  - production-deployment-lessons
  - deployment
created: 2026-07-10
updated: 2026-07-31
health_checked: 2026-07-31
---
# Flyway 迁移陷阱集

> 从 8 个工作区历史对话中提取的 Flyway 数据库迁移实战陷阱。
> 涵盖目录规范、版本号冲突、collation 陷阱、baseline-on-migrate、回滚脚本路径、checksum 等。
> 基础迁移规范见 CLAUDE.md §数据库迁移规范，本文只记录工程陷阱。

---

## 1. 迁移脚本目录：必须放在 migration-mysql/

### 1.1 陷阱

项目早期同时支持 PostgreSQL 和 MySQL，存在两套迁移目录：
- `db/migration/` — PostgreSQL（已废弃）
- `db/migration-mysql/` — MySQL（活跃使用）

开发者凭直觉把新迁移放在 `db/migration/`，导致 Flyway 不会执行该迁移（`application-mysql.yml` 配置的是 `db/migration-mysql/`）。

### 1.2 规范

- **活跃目录**：`backend/src/main/resources/db/migration-mysql/`
- **禁止使用**：`db/migration/`（已废弃）
- **回滚脚本**：`db/rollback/migration-mysql/U{version}__*.sql`（不是 `db/rollback/`）

### 1.3 验证

```bash
ls backend/src/main/resources/db/
# 期望：migration-mysql/  rollback/

grep "flyway.locations" backend/src/main/resources/application-mysql.yml
# 期望：classpath:db/migration-mysql
```

详见 [[lessons-learned]] §一。

---

## 2. 版本号冲突 — 必须用 new-migration.sh 预约

### 2.1 事故

两个 agent 同时开迁移时，各自 `ls | tail` 取版本号，结果撞号（如都取 V1081）。pre-push gate 在推送时才检测到冲突。

### 2.2 规范

```bash
# ✅ 正确：先预约版本号
bash scripts/next-migration-version.sh --reserve
# 输出：Next version: V1083, you can create with: bash scripts/new-migration.sh <描述>

# 然后创建迁移
bash scripts/new-migration.sh remove-task-executor-role
```

```bash
# ❌ 错误：手动 ls | tail 决定版本号
ls backend/src/main/resources/db/migration-mysql/ | tail
```

### 2.3 自动防冲突

- `sync-env.sh` 早操和 `pre-push-gate.sh` 会自动检测版本冲突
- 冲突在 pre-push 阶段会**强制 auto-fix**（V+1 递增重编号），禁止绕过
- 并行开发时，`new-migration.sh` 会各自从 remote 最新取版本号，不会撞号

### 2.4 教训

- **版本号必须从 remote 最新取**，不能只看本地
- **创建迁移前必须先 --reserve 预约**，防止并行开发撞号
- **不要绕过 pre-push gate 的 auto-fix**，它会在 rebase 时序导致撞号时自动重编号

---

## 3. collation 冲突 — 测试环境无法暴露

### 3.1 事故

生产环境 V1092 迁移执行 JOIN 时报：

```
Illegal mix of collations (utf8mb4_unicode_ci, IMPLICIT) and (utf8mb4_0900_ai_ci, IMPLICIT)
```

测试环境从未暴露此问题，因为测试环境的 `users` 表历史 collation 是 `utf8mb4_unicode_ci`，生产环境是 `utf8mb4_0900_ai_ci`。

### 3.2 根因

| 表 | 测试环境 collation | 生产环境 collation |
|----|-------------------|-------------------|
| `users` | utf8mb4_unicode_ci | utf8mb4_0900_ai_ci |
| `organization_departments` | utf8mb4_0900_ai_ci | utf8mb4_0900_ai_ci |

JOIN 时 collation 不一致就报错。测试环境因为历史包袱"恰好一致"，掩盖了问题。

### 3.3 修复

临时表必须显式指定 COLLATE：

```sql
CREATE TEMPORARY TABLE temp_users (
    id BIGINT,
    name VARCHAR(255)
) COLLATE utf8mb4_unicode_ci;
```

docker-compose.yml 已固化 `collation-server=utf8mb4_unicode_ci` 兼容历史数据。

### 3.4 教训

- **collation、字符集、时区、SQL mode 这些隐性配置差异，只有在生产环境才会暴露**
- **临时表必须显式指定 COLLATE 与关联表对齐**，不能依赖数据库默认值
- **测试环境的"历史包袱"会掩盖新部署的问题**
- **JOIN 涉及多表时，必须检查所有表的 collation 一致性**

---

## 4. baseline-on-migrate: true 覆盖生产配置

### 4.1 事故

`application-mysql.yml` 中 `flyway.baseline-on-migrate: true`，会覆盖 `application-prod.yml` 中的 `false` 设置，导致：
- 非空数据库启动时，Flyway 可能**静默跳过迁移脚本**
- 迁移未执行但启动成功，问题在运行时才暴露

### 4.2 修复

```yaml
# application-mysql.yml
spring:
  flyway:
    baseline-on-migrate: false  # 不要 true，否则非空数据库会跳过迁移
    validate-on-migrate: true
```

### 4.3 教训

- **profile 配置会覆盖主配置**，必须显式检查每个 profile 的 Flyway 配置
- **baseline-on-migrate: true 是危险配置**，只适合首次部署到已有数据库的场景
- **ddl-auto 也要检查**：`none` 会跳过 schema 验证，应设为 `validate`

---

## 5. ddl-auto: none vs validate

### 5.1 陷阱

`application-mysql.yml` 中 `jpa.hibernate.ddl-auto: none` 会让 JPA 跳过 schema 一致性验证。修改 schema 后如果 Flyway 没跑，JPA 不会报错，运行时才暴露。

### 5.2 规范

```yaml
# application-mysql.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate  # 不要 none，validate 会检查 schema 一致性
```

### 5.3 教训

- **ddl-auto: validate 是安全网**，能在启动时发现 schema 不一致
- **ddl-auto: none 只在生产极端情况下使用**（如 Flyway 已经验证过）
- **ddl-auto: update 禁止在生产使用**（会自动改 schema，绕过 Flyway）

---

## 6. 回滚脚本路径和 Header

### 6.1 事故

U1008 回滚放在 `db/rollback/` 而非 `db/rollback/migration-mysql/`，且缺少 source header，导致 `FlywayRollbackScriptCoverageTest` 报错。

### 6.2 规范

- **路径**：`db/rollback/migration-mysql/U{version}__*.sql`
- **Header 模板**：

```sql
-- Input: migration-mysql/V{version}__*.sql
-- Output: rollback script for mysql environments; ...
-- Pos: Flyway historical down migration coverage for 西域数智化投标管理平台.
```

### 6.3 验证

```bash
mvn test -Dtest=FlywayRollbackScriptCoverageTest
```

详见 [[lessons-learned]] §4.6。

---

## 7. Flyway checksum 校验失败

### 7.1 问题

rebase 后 Flyway 报 checksum 不匹配：

```
Migration mismatch for migration version V1081
→ Applied to database : 1234567890
→ Resolved locally    : 9876543210
```

### 7.2 处理

```bash
# 方式 1：清理 target 缓存（如因编译缓存导致）
rm -rf backend/target

# 方式 2：修复 checksum（如因 rebase 修改了已应用的迁移脚本内容，需评估风险）
mvn flyway:repair -Dflyway.url=... -Dflyway.user=...
```

### 7.3 教训

- **已应用的迁移脚本内容不可修改**（包括空格、注释），否则 checksum 失败
- **rebase 后 checksum 失败先清 target 缓存**，再考虑 repair
- **flyway:repair 要谨慎使用**，会更新 schema_history 表的 checksum

---

## 8. VARCHAR → ENUM 迁移必须包含数据清洗

### 8.1 事故

将某列从 VARCHAR 改为 ENUM 时，直接 ALTER TABLE 导致已有数据中不在 ENUM 范围内的值报 `Data truncated`。

### 8.2 正确流程

```sql
-- 1. 先清洗数据（把不在 ENUM 范围内的值改为合法值）
UPDATE tender SET status = 'DRAFT' WHERE status NOT IN ('DRAFT', 'BIDDING', 'CLOSED');

-- 2. 再 ALTER TABLE
ALTER TABLE tender MODIFY COLUMN status ENUM('DRAFT', 'BIDDING', 'CLOSED');
```

### 8.3 教训

- **类型变更迁移必须先清洗数据**，再改类型
- **ENUM 的值集必须覆盖现有数据**
- **迁移前先用 SELECT 检查是否有超范围数据**

---

## 9. MySQL 1093 子查询引用同表

### 9.1 问题

```sql
-- ❌ 错误（MySQL 1093: You can't specify target table 'tender' for update in FROM clause）
DELETE FROM tender WHERE id IN (
    SELECT id FROM tender WHERE status = 'DELETED'
);
```

### 9.2 解决

```sql
-- ✅ 正确：包一层 subquery
DELETE FROM tender WHERE id IN (
    SELECT id FROM (
        SELECT id FROM tender WHERE status = 'DELETED'
    ) AS tmp
);
```

### 9.3 教训

- **MySQL 不允许在 DELETE/UPDATE 的子查询中引用同表**
- **包一层 subquery 可以绕过此限制**

---

## 10. MySQL JSON_EXTRACT 空字符串异常

### 10.1 问题

```sql
-- ❌ 错误（JSON_EXTRACT 对空字符串返回 NULL，但后续操作报错）
SELECT JSON_EXTRACT(config, '$.key') FROM settings WHERE config = '';
-- Error: 3141 Invalid JSON text
```

### 10.2 解决

```sql
-- ✅ 正确：先检查是否为有效 JSON
SELECT JSON_EXTRACT(config, '$.key')
FROM settings
WHERE config != '' AND JSON_VALID(config);
```

### 10.3 教训

- **JSON_EXTRACT 前必须验证字段是有效 JSON**
- **空字符串和 NULL 都会导致 JSON 函数异常**
- **历史数据可能包含非 JSON 字符串**，迁移时要处理

---

## 11. INSERT IGNORE + NULL 唯一键不幂等（回滚脚本陷阱）

### 11.1 事故

U1182 回滚脚本使用 `INSERT IGNORE` 恢复种子数据，依赖 `uk_scope_org(scope, org_id)` 复合唯一键去重。但插入的 `org_id=NULL`，而 **MySQL InnoDB 对 NULL 不去重（NULL != NULL）**，唯一键冲突检测失效，重复执行回滚每次都会插入重复记录。

### 11.2 解决

```sql
-- ✅ 正确：INSERT 前显式 DELETE 清理 org_id IS NULL 的残留，再 INSERT
DELETE FROM form_definition_registry
WHERE scope IN ('knowledge.case', 'resource.expense') AND org_id IS NULL;

INSERT IGNORE INTO form_definition_registry(scope, scope_label, ...) VALUES (...);
```

### 11.3 教训

- **MySQL 复合唯一键中任一列为 NULL 时，该行不参与唯一性校验**（NULL != NULL），`INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` 的冲突检测都会失效
- **回滚脚本依赖唯一键保证幂等时，必须检查插入值是否含 NULL**；含 NULL 就先显式 DELETE 同范围记录再 INSERT
- **验证方法**：本地库连续执行回滚脚本 2 次，确认目标行数不增长
- 来源：PR !2229 google-code-review 独立核查发现（2026-07-31）

---

## 12. 迁移脚本命名规范

### 12.1 规范

- **基线版本**：`B{version}_*.sql`（如 `B73__full_schema_baseline.sql`）
- **增量版本**：`V{version}___{desc}.sql`（如 `V1081__remove_task_executor_role.sql`）
- **回滚版本**：`U{version}__{desc}.sql`（如 `U1081__remove_task_executor_role.sql`）

### 12.2 版本号

- 必须大于已有最大版本号
- **严禁手动猜测或 `ls | tail` 决定版本号**
- 必须使用 `bash scripts/new-migration.sh <描述>` 创建

---

## 13. 相关文档

- [[lessons-learned]] §一 §二 — 数据库迁移目录清理、CI 配置对齐
- [[production-deployment-lessons]] §1 — collation 冲突案例
- [[oss-organization-sync-playbook]] §6.3 — collation 陷阱
- [[deployment]] — 部署与上线
- CLAUDE.md §数据库迁移规范 — 基础规范

---

## 14. 变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 8 个工作区历史对话中提取 Flyway 迁移陷阱 |
| 2026-07-31 | 新增 §11 INSERT IGNORE + NULL 唯一键不幂等（U1182 回滚脚本事故，PR !2229）；原 §11~§13 顺延为 §12~§14 |
