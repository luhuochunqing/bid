---
title: Hibernate STRING 枚举 vs varchar 列校验陷阱
space: engineering
category: pitfall
tags: [hibernate, jpa, enum, flyway, schema-validation, mysql]
sources:
  - backend/src/main/java/com/xiyu/bid/competitionintel/entity/Competitor.java
  - backend/src/main/resources/db/migration-mysql/V1202__competitor_status_unique_seed.sql
backlinks:
  - .wiki/pages/_index.md
created: 2026-08-26
updated: 2026-08-26
health_checked: 2026-09-03
---

# Hibernate STRING 枚举 vs varchar 列校验陷阱

> Hibernate 6.x MySQL 方言对 `@Enumerated(EnumType.STRING)` 默认期望**原生 ENUM 列**（`enum('active','disabled')`）。若 Flyway 建的是 `VARCHAR(n)` 且实体未显式声明 `columnDefinition`，`ddl-auto: validate` 下启动即炸：`Schema-validation: wrong column type encountered ... found [varchar], but expecting [enum]`。

## 冲突链路（spec 055 竞品状态实例）

```
V1202: ALTER TABLE competitors ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
Competitor.status: @Enumerated(EnumType.STRING) @Column(length = 20)   ← 缺 columnDefinition
Hibernate 6 方言:  期望 enum('active','disabled')
prod / e2e / flyway-mysql profile: ddl-auto: validate → 后端启动 crash-loop
```

## 修复

改实体对齐 DB 真相（已发布 Flyway 迁移不可变，禁止回改或追加修正列类型迁移）：

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
private CompetitorStatus status = CompetitorStatus.ACTIVE;
```

## 陷阱特征

- 编译通过、dev（ddl-auto=update）/ H2 环境全绿，只在真实 MySQL + validate 暴露
- 打包门禁 `FlywayMysqlContainerTest`（Testcontainers 真实 MySQL 全量回放 B73~最新 + validate）可精确拦截
- 报错中枚举值以小写渲染是方言细节，核心矛盾是 varchar vs 原生 enum 列类型

## 防护建议

1. **不要跳过容器测试**：`XIYU_SKIP_CONTAINER_TEST=true` 会放弃这道防线（同 [[tenderevent-sdk-bean-conflict]] 教训）
2. **新增 STRING 枚举字段二选一并全库统一**：① DDL 用原生 `ENUM(...)` 对齐 Hibernate 默认；② DDL 用 `VARCHAR(n)` + 实体显式 `columnDefinition = "varchar(n)"`
3. **Hibernate 5→6 存量排查**：grep `@Enumerated(EnumType.STRING)`，逐个核对底层列类型
