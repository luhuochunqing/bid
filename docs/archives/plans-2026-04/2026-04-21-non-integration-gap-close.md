# 非集成缺口补齐实施计划

> **For Codex:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 补齐非集成范围内 4 个缺口项的前端入口、前端 API、后端 controller 与最小测试，使其达到可演示、可验收状态。

**Architecture:** 采用 `integration + expert worktrees` 的分支骨架，在集成工作区统一落地共享接线。后端遵守 FP-Java Profile，新增规则收敛到 core/policy，应用服务只做编排；前端把占位/断链逻辑拆成 feature 级 composable 和 API 模块，避免继续膨胀在超大页面中。

**Tech Stack:** Vue 3 + Pinia + Element Plus + Vitest；Spring Boot 3 + Spring Data JPA + JUnit/Mockito。

---

## 工作区与分支

- 集成工作区：`.worktrees/integration`
- 集成分支：`feat/non-integration-gap-close`
- 专家分支骨架：
  - `feat/alerts-closure`
  - `feat/tender-dispatch-closure`
  - `feat/market-insight-closure`
  - `feat/ai-writing-quality-closure`

## 范围冻结

只处理以下 4 项：

1. 智能日程与预警
2. 标讯分发与指派跟进
3. 超前预测与市场洞察
4. AI 标书检查中的文本质量辅助

明确不做：

- CRM、OA、组织架构、开放 API 等集成项
- 与 4 项无关的页面重设计、权限模型重构、数据库大迁移

## 子任务与合并顺序

1. `alerts`
   - 补路由入口、前端告警 API 语义、工作台日历跳转、后端 acknowledge/schedule overview
2. `tenders`
   - 补批量领取/指派/状态更新的前后端闭环
3. `insight`
   - 恢复客户商机中心真实入口与真实取数，拆解 market insight service
4. `ai-quality`
   - 补项目文本质量检查的前后端链路

共享接线统一在 `integration` 完成：

- `src/router/index.js`
- `src/components/layout/Sidebar.vue`
- 共享菜单/入口文案

## 验证命令

前端：

```bash
npm run test:unit
npm run build
```

后端：

```bash
mvn test
```

按功能补充定向测试：

- alerts 相关 vitest + spring tests
- tenders batch 相关 vitest + spring tests
- insight 相关 vitest + spring tests
- ai-quality 相关 vitest + spring tests

## 验收口径

- 页面入口真实可达，不再隐藏或被守卫拦截
- 页面调用的方法在 API 模块真实存在
- 后端 controller/服务成套，返回结构与前端一致
- 不重新引入 mock/demoPersistence/双模式兜底
- 没有新增超 300 行的大文件；新增类不混装规则计算、数据访问、DTO 转换、状态写入三类以上职责
