<!-- 一旦我所属的文件夹有所变化，请更新我。 -->
# 自动生成的事实真相 (docs/generated/)

> ⚠️ **禁止手动编辑本目录下的文件**。所有文件由脚本生成，确保 AI 看到的是代码库的**绝对真实现状**，而非过时的人工描述。

## 目录结构

| 文件 | 生成命令 | 来源 | 说明 |
|---|---|---|---|
| `db-schema.md` | `npm run db:generate-schema` | `backend/src/main/resources/db/migration-mysql/V*.sql` + `B*.sql` | 从 Flyway 迁移解析出的数据库表结构 |

## 原则

1. **机器维护**：任何结构性事实（表结构、API schema、路由清单等）都应脚本生成，不手写。
2. **可重跑**：脚本必须幂等，重跑覆盖同一文件。
3. **带戳**：每个生成文件顶部带 `<!-- AUTO-GENERATED ... -->` 头，声明生成时间与脚本来源。
4. **随源刷新**：源（如 Flyway 迁移、JPA 实体、OpenAPI）变更后，重跑对应生成命令。

## 何时重跑 db-schema.md

- 新增 / 修改 Flyway 迁移（V*.sql）
- 排查"表结构与代码假设不一致"问题时
- PR 涉及库表变更时（建议在 PR 里刷新本文件一并提交）

## 外部参考

- 迁移源目录：`backend/src/main/resources/db/migration-mysql/`
- 生成脚本：`scripts/generate-db-schema.mjs`
- 手写数据模型（合成视图）：`.wiki/pages/data-model.md`
- JPA 实体边界：`backend/src/main/java/com/xiyu/bid/entity/README.md`
