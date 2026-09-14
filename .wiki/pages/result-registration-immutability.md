---
title: 项目结果登记不可变链路与二次确认
space: engineering
category: module
tags: [结果确认, 竞争对手, 二次确认, 不可变性, 幂等, ResultConfirmStage]
created: 2026-08-27
updated: 2026-08-27
health_checked: 2026-09-14
sources:
  - src/views/Project/stages/ResultConfirmStage.vue
  - backend/src/main/java/com/xiyu/bid/project/service/ProjectResultRegistrationService.java
  - backend/src/main/java/com/xiyu/bid/project/core/ProjectFieldLockPolicy.java
  - docs/implementation-notes/competitor-confirm-lock.md
backlinks:
  - _index
---
# 项目结果登记不可变链路与二次确认

## 概览

项目中心 → 项目详情 → 结果确认阶段（`ResultConfirmStage.vue`）：登记动作**一次性、不可逆**。
2026-08-27 补齐 UI 层二次确认（登记前警示「登记后投标结果与竞争对手情况将被锁定存档、不可再修改」），
并在竞品卡片 header 加 `已登记 · 锁定不可修改` tag。数据层不可变性此前已闭环，本次纯 UX 收口。

## 三重不可变保障（单一真相：后端）

1. **幂等拒绝**：`ProjectResultRegistrationService.register()` 同 projectId 已存在 → `409 CONFLICT("项目结果已登记，不可重复提交")`；全仓不存在结果更新接口（PUT/PATCH 均无）。
2. **阶段字段锁**：CLOSED 阶段经 `ProjectFieldLockPolicy.assertWritable(stage,"result")` 拒写（423 LOCKED）。
3. **竞品子表仅随首次登记写入**：`ProjectResultCompetitor` 由 `persistCompetitors` 一次落库，无独立修改端点。

前端呼应：`registeredAt` 存在 → `resultDone=true` → 所有输入置灰、「添加一行/删除」隐藏、提交按钮变禁用态「已登记」。

## 二次确认弹窗设计决策

- **放位置**：所有前端必填校验通过之后、竞品名幂等 upsert 之后、调 API 之前——保证弹窗文案中的竞品家数是过滤空行后的真实数字，且表单填一半不被打断。
- **文案动态化**：有竞品 → 「已填写 N 家竞争对手情况」；零行 → 「尚未填写竞争对手情况」。警示范围覆盖结果+竞品整体（登记是原子事务，单独强调竞品会让用户误以为其他字段仍可改）。
- **取消静默返回**：不弹 info 提示，避免二次噪音。
- **测试锚点**：E2E 层无任何用例点击「登记结果」（`project-result-confirm-competitor-flow.spec.js` 仅增删行 + API 直调），UI 行为由 `ResultConfirmStage.spec.js` 单测兜底（mock `ElMessageBox.confirm`）。

## 陷阱：旧竞品录入旁路（死代码，勿误改）

`ProjectDetailResultDialogs.vue` 的「投标结果录入」弹窗 + `useProjectDetailProjectOps.js#handleSaveResult`
走 `projectStore.updateProject` 直写 `resultData.competitors`，**无锁定、可反复覆盖**。但其入口
`handleRecordResult` 已不被任何模板引用（不可达 UI）。改动竞品/结果逻辑时不要把它当第二真相源；
后续任务应清理。

## 关联

- [[competitor-library-governance]] — 竞品字典治理与输入即建（specs/055），本页上游依赖
