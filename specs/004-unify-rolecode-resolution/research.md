# Phase 0 Research: 统一服务层角色码解析入口

**Date**: 2026-06-27

本特性无 NEEDS CLARIFICATION 项（根因已在 CO-373 排查中确认）。研究聚焦于技术决策。

## R1: 纯核心与外壳的职责边界如何切分？

**Decision**: 纯核心 `EffectiveRolePolicy` 接收「缓存角色码（Optional<String>）」「实体角色码（String）」「是否 OSS 用户（boolean）」三个显式入参，返回「有效角色码（String，可空）」+「决策来源（枚举：CACHE_HIT / LOCAL_USER / CACHE_MISS_FAIL_CLOSED）」。外壳 `EffectiveRoleResolver` 负责从 `OssPermissionCache` 读缓存值、从 `User` 取实体角色码与 OSS 标识、调用纯核心、记录日志。

**Rationale**: 遵循 FP-Java Profile——纯核心不依赖 Spring/Redis，可独立单测；缓存 I/O 属于副作用，放外壳。这与 PR #1241 `resolveEffectiveRoleCode` 的内部逻辑一致，只是提炼为可复用组件。

**Alternatives considered**:
- 直接改 `User.getRoleCode()` 让它读缓存 → 否决：User 是 JPA 实体，注入 `OssPermissionCache` 会破坏 FP-Java「纯核心不依赖框架」底线，且使实体不可单测。
- 在每个 Guard/Service 里各自读缓存 → 否决：19 处重复，正是导致现状的根因，违反 DRY，且未来新增 Guard 仍会复发。

## R2: 决策来源枚举如何设计？

**Decision**: 使用 record 返回结构 `EffectiveRoleResult(String roleCode, Source source)`，其中 `Source` 是枚举 `CACHE_HIT` / `LOCAL_USER` / `CACHE_MISS_FAIL_CLOSED`。外壳根据 source 记录不同级别日志（CACHE_HIT→debug，LOCAL_USER→debug，CACHE_MISS_FAIL_CLOSED→warn）。

**Rationale**: FR-009 要求可观测性；枚举让日志清晰区分三种路径，且可被测试断言。

**Alternatives considered**:
- 只返回 roleCode 不返回 source → 否决：无法满足 FR-009 决策日志要求，线上排查时无法区分「缓存命中拿到正确角色」与「fail-closed 拿到空」。
- 用字符串标记 source → 否决：类型不安全，易拼写错误。

## R3: 空字符串缓存值如何处理？

**Decision**: 纯核心把空字符串与 `Optional.empty()` 等同处理，视为缓存未命中。外壳在传给纯核心前，将 `Optional.of("")` 归一化为 `Optional.empty()`。

**Rationale**: spec Edge Case 明确「缓存角色码为空字符串应视为未命中」；OssPermissionCache.getRoleCode 可能返回 `Optional.of("")`（见 AuthServiceTest:348 mock）。

**Alternatives considered**: 在纯核心内判断空字符串 → 否决：让纯核心处理空字符串归一化是关注点泄漏；归一化应在外壳层完成。

## R4: CurrentUserResolver 改造范围？

**Decision**: `CurrentUserResolver` 注入 `EffectiveRoleResolver`，`getCurrentRoleCode()` 改为调用 `effectiveRoleResolver.resolve(getCurrentUser())`。保留 `getCurrentUser()` 和 `requireCurrentUser()` 不变。新增便捷方法 `resolveEffectiveRoleCode(User)` 委托给 resolver。

**Rationale**: `CurrentUserResolver` 是「当前用户」中央解析器，是污染的扩散源之一（line 64 直调 `user.getRoleCode()`）。改它可让所有通过 resolver 获取角色码的下游一次性受益。但它不是唯一落点——许多 Guard 直接对 `User` 实体调 `getRoleCode()`，所以仍需逐个改 Guard。

**Alternatives considered**: 只改 CurrentUserResolver 不改 Guard → 否决：扫描显示 TaskPermissionGuard 等直接对 `currentUser.getRoleCode()` 调用，绕过 resolver，必须逐个改。

## R5: 既有 4 处走缓存实现如何收敛？

**Decision**: `ProjectDraftingService` 删除私有 `resolveEffectiveRoleCode` 方法，改注入 `EffectiveRoleResolver`。`DataScopeConfigService`、`UserDetailsServiceImpl`、`AuthService` 同样改用 `EffectiveRoleResolver`，删除各自的缓存读取重复代码。

**Rationale**: FR-005 要求消除重复实现。收敛后只有一个解析入口，未来维护只改一处。

**Alternatives considered**: 保留各自实现只加注释 → 否决：正是导致现状的根因，PR #1241 只补了 ProjectDraftingService 一处就复发。

## R6: 哪些 getRoleCode() 调用不需要改？

**Decision**: 以下场景的 `User.getRoleCode()` 调用保留不改：
1. **Assembler/Mapper 类**（审计日志项映射、看板快照、分析组装器、候选人列表组装等）：这些是数据快照/回显用途，记录「该用户在 DB 中的角色码」是合理的，不参与权限判定。
2. **TraceFilter**（line 82）：MDC 日志记录，记录实体角色码用于日志追踪即可。
3. **测试代码**：保持现状。
4. **User 实体的 getRoleCode() 方法本身**：保留（FR-010），只是不再被权限校验直调。

**Rationale**: 只有「参与权限判定」的调用才需要走统一入口。快照/回显用途记录 DB 角色码是正确语义。盲目全改会引入不必要的缓存读取消耗，且改变快照语义。

## R7: 前端回显修复方案？

**Decision**: `useInitiationStageActions.js:177` 的 `biddingAssistantName` truthy 判断，参照同文件 `biddingLeaderName` 的兜底逻辑（line 175 附近）补齐：当 `data.biddingAssistantId` 存在但 `data.biddingAssistantName` 为空时，用 `data.biddingAssistantId` 查询用户姓名补全，或显示占位文本。

**Rationale**: spec US4 要求回显一致。先读前端实际代码确认 `biddingLeaderName` 的兜底模式，再对齐应用。

**Alternatives considered**: 后端接口直接返回姓名 → 否决：根因是前端回显逻辑不健壮，且后端接口可能已返回 ID 对应姓名（需读代码确认）；即便后端已返回，前端也应有兜底，否则任何接口字段缺失都会复现。

## 总结

所有技术决策已明确，无未解决项。可进入 Phase 1 设计。
