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
