---
title: 数据字典补齐与生成器六类解析盲区（V1218 注释回填）
space: engineering
category: guide
tags: [db-schema, flyway, 数据字典, 注释, 动态SQL, V1218]
created: 2026-09-25
updated: 2026-09-25
health_checked: 2026-10-25
sources:
  - scripts/generate-db-schema.mjs
  - backend/src/main/resources/db/migration-mysql/V1218__backfill_table_column_comments.sql
  - docs/generated/db-schema.md
  - docs/lessons/lessons-learned.md
backlinks:
  - _index
---
# 数据字典补齐与生成器六类解析盲区（V1218 注释回填）

> **一句话**：`docs/generated/db-schema.md` 长期 151 表/1636 字段无注释且与真实 schema 漂移，
> 根因是 `generate-db-schema.mjs` 静态解析存在六类盲区。V1218 补齐全部注释（206 表/2372 列 100%），
> 生成器增强后与 `information_schema` 双向 diff 归零。

## 数据字典的三条纪律

- **真相链**：迁移 SQL → `npm run db:generate-schema` → `docs/generated/db-schema.md`（禁止手改）。
  字典"和库不一致"优先怀疑生成器解析盲区，而不是手改字典。
- **补注释的正路**：新增 V 迁移写 `ALTER TABLE ... COMMENT` / `MODIFY COLUMN ... COMMENT`，然后重新生成字典。
  MODIFY 的列定义必须逐列取自迁移链构建库的 `information_schema`（或 SHOW CREATE），严禁手写，防止意外改结构。
- **注释回填迁移的验证法**：前后 `SHOW CREATE TABLE` 去掉注释后逐字节 diff 必须为空；U 回滚做同样双向验证。

## 生成器六类解析盲区（已修复，写迁移时避开）

| 盲区 | 案例 | 后果 |
| --- | --- | --- |
| 头注释内含分号 | V1011 `-- Rename old table to deprecated; create ...` | 语句被切成残片，RENAME 丢失 → 新旧表名并存 |
| `CALL add_column_if_not_exists(...)` | V1042 等 80 处 | 动态加的列完全不可见 |
| `SELECT IF(count>0,'ALTER TABLE ...')` 预处理 | V1026 | 动态 ADD/DROP/MODIFY 不可见 |
| 存储过程体内嵌 `IF...THEN ALTER TABLE` | V1097/V1099/V1115 漂移修复 | 加删列不可见 |
| 一条 ALTER 多个 ADD COLUMN 只解析首个 | V1016 一次加几十列 | 其余列在字典中消失 |
| `RENAME TO` / `DROP TABLE` 不识别 | V1011 品牌授权表改名 | 幽灵表 + 新表无来源 |

生成器现已有：动态 SQL 展开（CALL 合成等价 DDL、字符串字面量 DDL 提取）、按行剥离行注释后切分、
多 ADD COLUMN 循环、RENAME/DROP TABLE/DROP COLUMN/CHANGE COLUMN 语义、binary/varbinary/blob 族类型。

## 验证口径（复用）

- 字典 ↔ 库双向 diff：字典列集 vs `information_schema` 列集，两个方向都应为 0。
- 本次基线：206 表 / 2372 列 100% 有注释；空表注释 = 0，空列注释 = 0。
- 注释取值统一规范见 PR 描述与 lessons §405；敏感点：注释字符串禁英文逗号/引号/分号（生成器按逗号切子句）。
