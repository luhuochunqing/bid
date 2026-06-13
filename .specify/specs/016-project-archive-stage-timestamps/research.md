# Research: 项目档案时间戳补全

**Feature**: specs/016-project-archive-stage-timestamps/spec.md

## Research Questions

本功能范围明确，无需额外研究。以下问题已在代码审查中确认。

### Q1: Project 实体当前是否有阶段时间戳字段？

**Status**: ✅ 已确认

**Finding**:
- `Project.java` 当前仅有 `createdAt`、`updatedAt` 两个审计字段
- 无 `initiatedAt`、`evaluatingAt`、`closedAt` 字段
- 需要新增 3 个 `LocalDateTime` 字段

**References**:
- `backend/src/main/java/com/xiyu/bid/entity/Project.java:166-173`

### Q2: ProjectStageTransitionPolicy 是否需要改造？

**Status**: ✅ 无需改造

**Finding**:
- `ProjectStageTransitionPolicy` 是纯核心（core 包），仅做合法性校验
- 时间戳填充属于副作用，应在 Application Service 层（ProjectStageService）处理
- 符合 FP-Java 架构：纯核心不处理时间/副作用

**References**:
- `backend/src/main/java/com/xiyu/bid/project/core/ProjectStageTransitionPolicy.java`

### Q3: ProjectArchiveDetailService 当前时间戳来源？

**Status**: ⚠️ 需改造

**Finding**:
- 当前代码第 42 行：`initiatedAt = tender.getCreatedAt()`（降级获取）
- 当前代码第 97 行：`bidSubmissionAt = null`（硬编码 null）
- 当前代码第 99 行：`closedAt = null`（硬编码 null）
- 改造后应从 `Project` 实体直接读取这 3 个字段

**References**:
- `backend/src/main/java/com/xiyu/bid/casework/application/ProjectArchiveDetailService.java:42-99`

### Q4: ArchiveDetailDrawer.vue 前端是否需要改造？

**Status**: ✅ 无需改造

**Finding**:
- 模板第 19-22 行已正确引用 `fullDetail?.initiatedAt`、`fullDetail?.bidSubmissionAt`、`fullDetail?.closedAt`
- `formatDateTime()` 工具函数已处理 null 值显示 "-"
- 前端无需修改

**References**:
- `src/views/Knowledge/views/components/ArchiveDetailDrawer.vue:19-22`

### Q5: 状态倒退时时间戳如何处理？

**Status**: ✅ 已确认

**Finding**:
- spec §Edge Cases 明确：时间戳保留首次进入该阶段的记录
- 代码实现：在填充时间戳前检查 `if (field == null)` 再赋值
- 不会在状态倒退时被清除

## Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| 时间戳填充时机 | Application Service 层 | 符合 FP-Java，Pure Core 不处理副作用 |
| 时间戳字段位置 | Project Entity | 档案时间戳属于项目生命周期数据 |
| 历史数据处理 | 回填 createdAt | 保持数据完整性，提供近似值 |
| 状态倒退处理 | 保留首次进入时间 | spec 明确要求，不可篡改性 |
