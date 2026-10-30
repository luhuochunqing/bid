---
title: AI 案例沉淀并发任务与大文件隔离契约
space: engineering
category: pitfalls
tags: [AI案例沉淀, 并发, 幂等, 临时文件, 后台任务]
sources:
  - backend/src/main/java/com/xiyu/bid/casework/application/CasePrecipitationTaskStateService.java
  - backend/src/main/java/com/xiyu/bid/casework/application/ProjectClosedEventListener.java
  - src/composables/useCasePrecipitationTask.js
backlinks:
  - docs/lessons/lessons-learned.md
  - .wiki/pages/case-precipitation-streaming.md
created: 2026-10-23
updated: 2026-10-30
health_checked: 2026-10-30
archive: false
---

# AI 案例沉淀并发任务与大文件隔离契约

## 根因

- 旧链路使用 `case-{projectId}` 作为 `.part` 文件所有权 ID。
- 同一项目被连续触发时，多次执行覆盖、读取和删除同一文件，完整 DOCX 会随机表现为 ZIP 损坏。
- POST 返回只代表异步事件已发布，不能作为任务完成证据。

## 强制契约

| 层 | 约束 |
|---|---|
| 幂等领取 | 事务内锁 `projects` 行；同一项目存在 PENDING/PROCESSING 时复用原任务。 |
| 执行隔离 | 每次新任务使用 UUID；下载、spill、解析、release 全程传递同一个 taskId。 |
| 自动触发 | 结果登记只发布事实；任务领取在 `AFTER_COMMIT` 监听器内执行，附加失败不得回滚结果登记。 |
| 最终围栏 | 只有活动任务可完成；超时旧任务被替换后，其迟到结果不得写入。 |
| 原子提交 | 案例保存与任务 COMPLETED 在同一事务；后续数据库失败必须回滚 COMPLETED。 |
| 错误分类 | 仅 `uk_source_proj_scoring_point` 冲突按幂等成功处理，其他完整性错误必须标记 FAILED。 |
| 前端状态 | POST 202 仅表示受理；页面轮询 GET 状态直到终态，刷新可恢复，卸载后不得复活轮询。 |

## 回归门禁

- 真实 MySQL：三线程领取同一 projectId 只产生一个任务。
- 真实 MySQL：旧任务超时替换后无法完成。
- 真实 MySQL：同事务后续约束失败时 COMPLETED 回滚，任务可转 FAILED。
- 真实 HTTP：三路同一大文件强制 spill，路径与 SHA-256 独立，单任务释放不影响其他文件。
- 前端：POST 后持续运行、刷新恢复、终态停止、在途请求卸载后不再调度。

## 副作用（Cross-Module Impact）

- 新增 `case_precipitation_task` 持久化审计表与 V/U 迁移。
- 手动触发与结果登记自动触发共用同一 projectId 门禁。
- 状态接口必须执行项目访问范围校验，不能暴露其他项目任务信息。

## 失败原因展示（2026-10-30）

- 结果登记后立即自动触发是业务契约；缺少评分项应 FAILED，不延迟触发、不静默跳过。
- `CasePrecipitationFailureMessages` 是纯转换规则：前置条件、下载、转换、AI 服务、配额等异常转为原因和下一步；不向用户透传原始响应/路径。
- `ProjectClosedEventListener` 将同一 reason 保存到任务并发送通知；前端通过最新任务 GET 恢复，结果登记与结项页面持续显示失败提示。
- 单文件配额拒绝携带实际字节数和本次配置上限，条件保持不变；不要建议用户对不可恢复的配额错误反复重试。
- 副作用：改变的是后续任务的错误文案；不回填历史通知/任务，不调整触发时机或文件配额。
