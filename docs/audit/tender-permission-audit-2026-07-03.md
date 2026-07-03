# 标讯中心 · 权限矩阵对照审计（2026-07-03）

> **权威基线**：飞书《标讯中心·权限矩阵》V1.0（revision 157，2026-05-14 定稿）
> 　https://my.feishu.cn/docx/Kq7NduknFoCTi3xyI0vcyJfZnce
> **审计日期**：2026-07-03
> **审计者**：zcode agent
> **方法**：逐功能点对照文档要求与当前代码实现；每个功能点配契约测试锁定，防止漂移
> **前置说明**：2026-06-17 的审计报告（`docs/exec-plans/active/tender-permission-matrix-audit-20260617.md`）已过时（其中部分 bug 已修复）；本审计以最新代码 + 最新文档为准

---

## 审计方法论

每个功能点产出 4 部分：

1. **文档要求**：飞书文档的权威定义（角色 × 数据范围 × 状态）
2. **代码实现**：Controller 注解 + Service 层校验
3. **差距判断**：匹配 / 角色多放 / 角色少放 / 数据范围错误 / 状态收口缺失
4. **契约测试**：基于文档断言的测试用例（锁定当前实现，防止未来漂移）

**核心原则**：即使代码当前正确，也要写契约测试——今天的"正确"可能在下次重构时被悄悄改坏。

---

## 2.1 标讯列表

### 2.1.1 查看列表

**文档要求**：

| 角色 | 数据范围 |
|---|---|
| 投标管理员（/bidAdmin） | ✅ 全量 |
| 投标组长（bid-TeamLeader） | ✅ 全量 |
| 投标项目负责人（bid-projectLeader） | ✅ 仅自己的（自己创建 + 被分配给自己） |
| 投标专员（bid-Team） | ✅ 仅分配给自己的 |

**代码实现**：

| 层 | 位置 | 实现 |
|---|---|---|
| Controller | `TenderController:52` 类级 | `hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM')` |
| Service | `TenderProjectAccessGuard.filterVisibleTenders:84` | admin access → 全量 |
| Service | `isSelfVisibleTender:62` | self 范围：自己创建/负责 OR 最新分配记录 assignee |

**差距判断**：

| 维度 | 结论 | 依据 |
|---|---|---|
| 角色覆盖 | ✅ 匹配 | bid-otherDept/bid-administration 在 ROLES_WITHOUT_LEGACY_ROLE_COMPAT 中不持 MANAGER，被类级注解正确拦截（文档要求"不涉及标讯"）|
| 投标管理员/组长全量 | ✅ 匹配 | `currentUserHasAdminAccess()` + dataScope=all |
| 项目负责人"仅自己的" | ✅ 匹配 | `isSelfOwnedTender`（creator/biddingPerson/projectManager）+ 分配记录 |
| 投标专员"仅分配" | ⚠️ **代码含"自己创建"** | 文档说"仅分配给自己的"，但代码 `isSelfVisibleTender` 也放行"自己创建的"（`bidTeamCanSeeSelfCreatedTender` 测试已锁定此行为）。**需业务确认**：投标专员自己录入的标讯，是否应该看到？（文档"仅分配"vs 代码"分配+创建"）|

**契约测试**：

| 测试 | 状态 |
|---|---|
| `TenderProjectAccessGuardVisibilityTest.bidTeamCanSeeTenderAssignedToThem` | ✅ 已存在 |
| `TenderProjectAccessGuardVisibilityTest.bidTeamCannotSeeTenderAssignedToOthers` | ✅ 已存在 |
| `TenderProjectAccessGuardVisibilityTest.bidTeamCanSeeSelfCreatedTender` | ⚠️ 已存在，但断言"可见"——**与文档"仅分配"可能冲突，待业务确认** |
| `TenderProjectAccessGuardVisibilityTest.bidTeamCannotSeeUnrelatedTender` | ✅ 已存在 |
| **缺失**：投标项目负责人数据范围测试 | ❌ 待补 |
| **缺失**：投标管理员/组长全量测试 | ❌ 待补 |
| **缺失**：跨部门协同/行政 403 测试 | ❌ 待补 |

---

### 2.1.2 搜索/筛选

**文档要求**：同"查看列表"（同一接口 `/api/tenders`，搜索参数）。

**代码实现**：`getAllTenders(@ModelAttribute TenderSearchCriteria criteria)` —— 搜索参数走同一接口，数据范围过滤一致。

**差距判断**：✅ 与 2.1.1 一致（同一端点 + 同一过滤逻辑）。

**契约测试**：与 2.1.1 共享（同一端点）。

---

### 2.1.3 导出

**文档要求**：各角色导出 = 其可见数据范围，不设单独权限点。

**代码实现**：❌ **无导出端点**。

**差距判断**：❌ **缺失**（功能未实现，非权限问题）。文档第 5 条业务规则"导出不单独设权限点"——未来实现导出时，复用列表的 dataScope 过滤即可。

**契约测试**：N/A（无端点可测）。

---

### 2.1.4 编辑（PUT /api/tenders/{id}）

**文档要求**：

| 角色 | 权限 |
|---|---|
| 投标管理员 | ✅ 全量·仅未立项状态 |
| 投标组长 | ✅ 全量·仅未立项状态 |
| 投标项目负责人 | ✅ 自己的·见状态表（跟踪中可编辑、已评估可编辑、投标中及之后不可） |
| 投标专员 | **—**（不可编辑） |

**代码实现**：

| 层 | 位置 | 实现 |
|---|---|---|
| Controller | `TenderController:105` 方法级 | `hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','SALES')` |
| Service | `TenderCommandAccessGuard` | 状态 + 角色细分校验（需详查） |

**差距判断**：

| 维度 | 结论 | 依据 |
|---|---|---|
| 角色覆盖 | ✅ 匹配 | 方法级不含 BID_TEAM（投标专员被拦，匹配文档"—"）；含 SALES/BID_PROJECTLEADER（项目负责人可编辑）|
| 投标专员被拒 | ✅ 匹配 | `TenderPermissionIntegrationTest.updateTender_byBidSpecialist_returnsForbidden` 已锁定 |
| 状态收口（管理员/组长仅未立项） | ❓ **待查 Service 层** | Controller 不校验状态，依赖 `TenderCommandAccessGuard`。需读 Service 确认是否真的限制"未立项" |
| 项目负责人状态递减 | ❓ **待查 Service 层** | 文档第三章定义的状态递减表，需 Service 层实现 |

**契约测试**：

| 测试 | 状态 |
|---|---|
| `TenderPermissionIntegrationTest.updateTender_byBidSpecialist_returnsForbidden` | ✅ 已存在 |
| `TenderPermissionIntegrationTest.updateTender_byAnonymous_returnsForbidden` | ✅ 已存在 |
| **缺失**：状态收口测试（管理员编辑已立项 → 403/拒绝） | ❌ 待 Service 层确认后补 |

---

### 2.1.5 删除（DELETE /api/tenders/{id}）

**文档要求**：

| 角色 | 权限 |
|---|---|
| 投标管理员 | ✅ 全量·仅未评估状态 |
| 投标组长 | ✅ 全量·仅未评估状态 |
| 投标项目负责人 | ✅ 自己创建的·仅未评估状态 |
| 投标专员 | **—** |

**代码实现**：

| 层 | 位置 | 实现 |
|---|---|---|
| Controller | `TenderController:123` 方法级 | `hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','SALES')` |
| Service | `TenderCommandAccessGuard` | 状态校验 |

**差距判断**：

| 维度 | 结论 |
|---|---|
| 角色覆盖 | ✅ 匹配（同编辑：含项目负责人，不含专员）|
| 状态收口（仅未评估） | ❓ **待查 Service 层**——审计报告曾指出"代码限制为 PENDING_ASSIGNMENT，比文档'未评估'更严"，需确认是否已修 |

**契约测试**：

| 测试 | 状态 |
|---|---|
| 投标专员删除 → 403 | ❌ **缺失**（编辑有测，删除没测）|
| 状态收口（已评估状态删除 → 拒绝） | ❌ 待 Service 层确认 |

---

### 2.1.6 分发/转派（POST /api/tenders/{id}/transfer）

**文档要求**：

| 角色 | 权限 |
|---|---|
| 投标管理员 | ✅ |
| 投标组长 | ✅ |
| 项目负责人 | — |
| 投标专员 | — |
| 业务规则第 9 条 | 任何状态可强行干预转派 |

**代码实现**：

| 层 | 位置 | 实现 |
|---|---|---|
| Controller | `TenderTransferController:44` 方法级 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN')` |

**差距判断**：

| 维度 | 结论 | 依据 |
|---|---|---|
| 角色覆盖 | ✅ 匹配 | 仅管理员/组长/投标管理员，不含项目负责人/专员 |
| 项目负责人/专员被拒 | ✅ 匹配 | `TenderPermissionIntegrationTest.transferTender_bySales_returnsForbidden` + `transferTender_byBidSpecialist_returnsForbidden` 已锁定 |
| 状态收口（任何状态可转派） | ❓ **待查 Service 层**——审计报告曾指出"代码限制为 TRACKING/EVALUATED"，与文档"任何状态"不符 |

**契约测试**：

| 测试 | 状态 |
|---|---|
| `transferTender_byBidSpecialist_returnsForbidden` | ✅ 已存在 |
| `transferTender_bySales_returnsForbidden` | ✅ 已存在 |
| `transferTender_byStaff_returnsForbidden` | ✅ 已存在（MANAGER 被拒，但 MANAGER 应该等于组长？需确认这个测试是否正确）|

---

## 2.1 审计小结

### 已正确锁定的功能点

- 投标专员数据范围（仅分配）—— Service 层单测 4 个用例
- 投标专员/匿名用户编辑被拒 —— Controller 层集成测试
- 转派角色限制 —— Controller 层集成测试

### 待补的契约测试（本轮目标）

1. **投标项目负责人数据范围测试**（仅自己的：创建 + 分配）
2. **投标管理员/组长全量测试**
3. **跨部门协同/行政 403 测试**（不涉及标讯）
4. **删除端点的角色测试**（投标专员删除 → 403）
5. **状态收口测试**（编辑/删除/转派的状态限制，需先确认 Service 层实现）

### 待业务确认

1. **投标专员"仅分配"vs"分配+创建"**——文档说"仅分配"，代码放行"自己创建的"。是文档需要更新，还是代码需要收紧？
2. **状态收口的具体实现**——文档"仅未评估"/"仅未立项"是否在 Service 层正确实现？

### 需深入 Service 层确认的（下一轮）

- `TenderCommandAccessGuard` 的编辑/删除状态校验
- `TenderTransferService` 的转派状态限制
- 文档第三章"投标项目负责人操作权限随状态递减"

---

*下一步：基于本审计补缺失的契约测试（待补 5 类），先用已知正确的部分，状态收口待 Service 层确认后补。*

---

## 三、投标项目负责人操作权限随标讯状态递减（状态收口审计）

> 文档第三章 + 第 2.1 编辑/删除/转派，结合代码 `TenderEditPermissionPolicy`（纯核心）+ `TenderTransferService`

### 3.1 编辑权限的状态收口

**文档要求**：
- 投标管理员/组长：仅**未立项**状态可编辑（待分配/跟踪中/已评估）
- 投标项目负责人：跟踪中 ✅ / 已评估 ✅ / 投标中及之后 ❌ / **待分配 ❌**

**代码实现**（`TenderEditPermissionPolicy.canEdit`）：

| 角色 | 代码允许编辑的状态 | 文档对照 |
|---|---|---|
| admin/bidAdmin/bid-TeamLeader | PENDING_ASSIGNMENT + TRACKING + EVALUATED | ✅ 匹配（"未立项"= 这三态）|
| bid-projectLeader（sales）| TRACKING/EVALUATED（需 creator 或 pm）；**PENDING_ASSIGNMENT（需 creator）** | ⚠️ **gap**：文档"待分配 ❌"，代码允许创建人编辑 |

**契约测试**（`TenderCommandAccessGuardTest`，新增 7 个锁定状态收口）：

| 测试 | 断言 | 锁定行为 |
|---|---|---|
| admin 编辑 BIDDING → 拒绝 | ✅ | 已立项不可编辑 |
| admin 编辑 WON → 拒绝 | ✅ | 已中标不可编辑 |
| admin 删除 EVALUATED → 拒绝 | ✅ | 已评估不可删除（文档第 5 条）|
| admin 删除 BIDDING → 拒绝 | ✅ | 已立项不可删除 |
| sales 编辑 EVALUATED（creator）→ 通过 | ✅ | 已评估可编辑（文档第三章）|
| sales 删除 EVALUATED（creator）→ 拒绝 | ✅ | 已评估不可删除 |
| sales 编辑 BIDDING（creator）→ 拒绝 | ✅ | 已立项不可编辑 |

**⚠️ 待业务确认 gap**：`updateTender_salesCreatorPendingAssignment_allows`（既有测试）锁定"sales 创建人在待分配状态可编辑"，与文档第三章"待分配 ❌"冲突。**不擅自改**——需业务方确认：项目负责人录入标讯后、分配前，能否修改？

### 3.2 删除权限的状态收口

**文档要求**：只有"未评估"状态可删除；已评估后不可删除（第 5 条业务规则）。

**代码实现**（`canDelete`）：
- admin/bidAdmin/bid-TeamLeader：DELETABLE = PENDING_ASSIGNMENT + TRACKING（**不含 EVALUATED**）
- sales：上述状态 + creator==userId

**判断**：✅ **匹配文档**。代码不含 EVALUATED 正确（文档第 5 条"已评估不可删除"）。06-17 旧审计报告说的"PENDING_ASSIGNMENT 限制过严"其实是对文档"未评估"的误读——文档明确"已评估不可删除"，代码正确。

### 3.3 转派/分发状态收口

**文档要求**（第 9 条）：投标管理员/组长**在任何状态**可强行干预转派。

**代码实现**（`TenderTransferService`）：`TRANSFERABLE_STATUSES = [TRACKING, EVALUATED]`——仅跟踪中/已评估可转派，其他状态抛"标讯状态已变更，无法转派"。

**判断**：⚠️ **代码比文档严格**。文档说"任何状态"，代码限制为 TRACKING/EVALUATED。

**契约测试**（`TenderTransferServiceTest`，新增 4 个锁定当前实现）：

| 测试 | 断言 |
|---|---|
| EVALUATED 可转派 | ✅ 通过 |
| PENDING_ASSIGNMENT 不可转派 → 抛异常 | ✅ |
| BIDDING 不可转派 → 抛异常 | ✅ |
| WON 不可转派 → 抛异常 | ✅ |

**⚠️ 待业务确认 gap**：文档"任何状态"vs 代码"TRACKING/EVALUATED"。**不擅自改**——需业务方确认：
- 是否允许在 PENDING_ASSIGNMENT 转派（分配前就指定负责人）？
- 是否允许在 BIDDING/WON/LOST 转派（已立项后换负责人）？
- 代码当前的限制可能是有意设计（防止已立项项目混乱），也可能是未实现完整

### 3.4 状态收口审计小结

**核心结论**：代码的状态收口实现**整体正确**，文档第三章的主要规则都已实现并有测试锁定。

**两类 gap（待业务确认，不擅自修改）**：
1. **sales 创建人在 PENDING_ASSIGNMENT 可编辑/删除**（文档"待分配 ❌"）——既有测试已锁定代码行为，需确认是文档滞后还是代码 bug
2. **转派限制为 TRACKING/EVALUATED**（文档"任何状态"）——需确认是文档理想化还是代码需放宽

**契约测试价值**：本次新增 11 个测试（编辑/删除 7 + 转派 4），把状态收口的当前实现锁定。任何未来重构若误放行已立项/已评估状态的编辑/删除/转派，测试会立即红。


---

## 2.2 标讯录入（审计）

### 文档要求（飞书 V1.0）

| 三级功能 | 投标管理员 | 投标组长 | 投标项目负责人 | 投标专员 | 投标系统管理员 |
|---|---|---|---|---|---|
| 手动录入 | ✅ | ✅ | ✅ | ✅ | ✅ |
| 粘贴识别（AI） | ✅ | ✅ | ✅ | ✅ | ✅ |
| 批量导入（Excel） | ✅ | ✅ | **—** | ✅ | ✅ |
| 下载模板 | ✅ 公开 | ✅ 公开 | **—** | ✅ 公开 | ✅ 公开 |

**关键约束**：批量导入 + 下载模板**不含投标项目负责人**。

### 端点对照

| 功能 | 端点 | Controller 注解 | 文档对照 |
|---|---|---|---|
| 手动录入 | POST `/api/tenders` | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM','SALES')` | ✅ 角色覆盖匹配；⚠️ 含冗余 `SALES`（幽灵项，无 ROLE_SALES，是 BID_PROJECTLEADER 的历史别名，无害）|
| 批量导入 | POST `/api/tenders/import` | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` | ✅ **匹配**（正确排除项目负责人）|
| 下载模板 | GET `/api/tenders/import-template` | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` | ✅ **匹配**（正确排除项目负责人）|
| 粘贴识别（AI） | ❌ 无独立端点 | — | ❓ 前端直接调 AI 服务，后端未暴露（与 06-17 审计结论一致）|

### 差距判断

| 维度 | 结论 |
|---|---|
| 手动录入角色覆盖 | ✅ 匹配（项目负责人可录入，匹配文档）|
| 批量导入排除项目负责人 | ✅ 匹配（文档明确"—"）|
| 下载模板排除项目负责人 | ✅ 匹配 |
| SALES 幽灵项 | ⚠️ 冗余但无害（系统无 ROLE_SALES，是历史角色改名残留。未来清理时可去掉，不影响权限）|
| 粘贴识别 | ❓ 未实现为独立端点 |

### 契约测试

**Controller 层集成测试**（`TenderPermissionIntegrationTest`，+4 = 16/16）：
- 项目负责人手动录入 → 非 403（放行）
- 项目负责人批量导入 → 403（排除）
- 项目负责人下载模板 → 403（排除）
- 投标专员批量导入 → 非 403（放行）

**反射型契约测试**（`TenderControllerPermissionTest`，+2 = 6/6）：
- `importTenders` 注解 == `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` + 显式不含 BID_PROJECTLEADER/SALES
- `downloadImportTemplate` 同上

### 2.2 小结

✅ 标讯录入的权限实现**匹配文档**，核心约束（批量导入/下载模板排除项目负责人）已正确实现并锁定。SALES 幽灵项是冗余无害残留，可在未来清理 hasAnyRole 时顺带去除。


---

## 2.3 标讯评估（审计）

### 文档要求（飞书 V1.0）

| 功能 | 投标管理员 | 投标组长 | 投标项目负责人 | 投标专员 |
|---|---|---|---|---|
| 填写评估表 | — | — | ✅ 被分配的标讯 | — |
| 提交评估（不可撤回） | — | — | ✅ 被分配的标讯 | — |
| └ 确认投标 | ✅ | ✅ | — | — |
| └ 放弃投标 | ✅ | ✅ | — | — |

**核心约束**：评估表仅"被分配的项目负责人"可填/提交；确认/放弃投标仅管理员/组长。

### 端点对照

| 文档功能 | 端点 | Controller 注解 | 文档对照 |
|---|---|---|---|
| 填写评估表（读）| GET `/{tenderId}/evaluation` | `isAuthenticated()` | ⚠️ Controller 不限角色，Service 层 `canFill` 校验"被分配的" |
| 填写评估表（存草稿）| PUT `/{tenderId}/evaluation` | `isAuthenticated()` | ⚠️ 同上；但文档第 1 条"不支持存草稿"——代码支持（V130 改动）|
| 提交评估 | POST `/{tenderId}/evaluation/submit` | `isAuthenticated()` | ⚠️ Controller 不限角色，Service 层校验 |
| **确认投标 路径A** | POST `/api/tenders/{id}/participate` | `ADMIN/BID_TEAMLEADER/BIDADMIN` | ✅ 匹配 |
| **放弃投标 路径A** | POST `/api/tenders/{id}/abandon` | `ADMIN/BID_TEAMLEADER/BIDADMIN` | ✅ 匹配 |
| **审核标讯 路径B** | POST `/{tenderId}/review` | `ADMIN`（仅）| ❌ **缺组长/bidAdmin** |
| **确认投标 路径B** | POST `/{tenderId}/bid` | `ADMIN/MANAGER` | ❌ **含 MANAGER（含 sales 项目负责人），不符合"仅管理员/组长"** |

### 关键发现：接口职责重叠（审计报告 §5.5）

**同一个业务（确认/放弃投标）有 4 个端点入口**：

```
路径 A（TenderController）         路径 B（TenderEvaluationController）
  participateBid  ──┐                reviewTender  ──┐
                    ├─ 都调 canDecide                 ├─ 都调 canDecide
  abandonBid      ──┘                proceedToBid  ──┘
```

**Service 层统一**：4 个端点都调 `TenderAssignmentPermissions.canDecide`（global access 或分配人）。

**Controller 注解不一致**（真实 gap）：

| 端点 | 注解 | 文档"确认投标" | 差异 |
|---|---|---|---|
| participateBid | ADMIN/BID_TEAMLEADER/BIDADMIN | 管理员/组长 | ✅ 匹配 |
| abandonBid | ADMIN/BID_TEAMLEADER/BIDADMIN | 管理员/组长 | ✅ 匹配 |
| reviewTender | **ADMIN（仅）** | 管理员/组长 | ❌ 缺组长 |
| proceedToBid | **ADMIN/MANAGER** | 管理员/组长 | ❌ 多放 MANAGER（含 sales）|

### 差距判断

| 维度 | 结论 |
|---|---|
| 评估表填写/提交（Service 层 canFill）| ✅ 由实例级分配校验，匹配文档"被分配的标讯" |
| 确认/放弃投标 Service 层（canDecide）| ✅ 统一逻辑（global access 或分配人）|
| **Controller 注解不一致** | ❌ **4 个端点权限不一**——reviewTender 过严（缺组长），proceedToBid 过宽（含 MANAGER/sales）|
| 存草稿 vs 文档"不支持草稿" | ⚠️ 代码支持草稿（V130 改动），文档第 1 条"不支持" |

### 契约测试

**Controller 层集成测试**（`TenderPermissionIntegrationTest`，+5 = 21/21）：
- participateBid：投标专员/项目负责人 → 403（文档：仅管理员/组长）
- abandonBid：投标专员 → 403
- **reviewTender：组长 → 403（⚠️ 锁定现状"注解过严"，待业务确认是否放宽到组长）**
- **proceedToBid：MANAGER（项目负责人）→ 非 403（⚠️ 锁定现状"注解过宽"，待业务确认是否收紧）**

### ✅ 业务确认结果（2026-07-03，答案：4=A，其余 B）

**Gap 4 已修复**：`reviewTender` 和 `proceedToBid` 注解统一为 `ADMIN/BID_TEAMLEADER/BIDADMIN`（与路径 A 一致）。

| 端点 | 修复前 | 修复后 |
|---|---|---|
| reviewTender | `ADMIN`（仅，缺组长）| `ADMIN/BID_TEAMLEADER/BIDADMIN` ✅ |
| proceedToBid | `ADMIN/MANAGER`（含 sales）| `ADMIN/BID_TEAMLEADER/BIDADMIN` ✅ |

契约测试同步更新（断言反转，锁定新行为）：
- `reviewTender_byBidTeamLeader_notForbidden`（组长放行，原"403"→"非 403"）
- `proceedToBid_byManager_returnsForbidden`（项目负责人拒绝，原"非 403"→"403"）

**Gap 1/2/3 答案都是 B（代码正确）**：代码行为是业务期望的，文档滞后。契约测试已锁定正确行为，文档后续更新即可。详见 `docs/audit/tender-permission-gaps-handoff.md`。

### 2.3 小结

评估表填写/提交的实例级权限（canFill）正确。确认/放弃投标的 Service 层（canDecide）统一正确。**Controller 注解不一致问题已按业务确认 A 修复**（4 端点统一为管理员+组长）。

