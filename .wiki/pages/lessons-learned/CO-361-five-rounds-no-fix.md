---
title: CO-361 五次修复不彻底的教训 — 反复追症状不追根因的代价
space: engineering
category: lesson
tags: [教训, CO-361, CO-373, 角色解析, 工程治理, 多Agent协作, pre-existing-error, 接口一致性]
sources:
  - Linear CO-361
  - Linear CO-373
  - PR #1153 (三态模型收口)
  - PR #1156 (跟进修复)
  - PR #1197 (任务可见性 + 项目访问权限)
  - PR #1218 (CANCELLED 同形缺口 + 状态归一)
  - PR #1223 (三态模型彻底收口)
  - PR #1245 (OSS 用户 roleCode 解析修正)
  - PR #1259 (统一服务层角色码解析入口)
  - CLAUDE.md §双远程仓库配置
backlinks:
  - _index
  - lessons-learned
  - architecture
created: 2026-06-28
updated: 2026-06-28
health_checked: 2026-06-28
---

# CO-361 五次修复不彻底的教训

> **本文件不是 CO-361 的处理记录，而是从 CO-361 五次失败修复中提炼出的工程教训。**
> 凡是看到"反复修了 N 次还修不好"的 issue，先看本文件。

---

## 一、CO-361 的真实时间线（教训背景）

| 时间 | 修复 PR | 改了啥 | 解决了 dom 反馈吗 |
|---|---|---|---|
| 06-26 06:44 | CO-361 创建 | 标题含糊："废弃 IN_PROGRESS + 项目负责人看板缺失任务 + 两入口一致" | — |
| 06-26 14:14 | **Zoey 在评论提了 3 个真实场景** | 项目负责人 403 / 投标负责人只能看自己 assignee / 执行人看不到自己 | 这才是真实需求 |
| 06-26 08:33 | #1153 / #1156 | 三态模型收口（IN_PROGRESS 下线） | ❌ 漏场景 |
| 06-27 04:25 | #1197 | 引入 `TaskVisibilityPolicy` 按角色分支 | ❌ 仍漏场景 |
| 06-27 04:35 | dom 反馈 | 管理员建任务、分配执行人 → 执行人看不到 | ❌ |
| 06-27 06:37 | #1218 | CANCELLED 同形缺口 + 状态归一收口 | ❌ |
| 06-27 09:42 | #1223 | **三态模型彻底收口**：DB ENUM / 枚举 / Service / 前端全链路下线 IN_PROGRESS、CANCELLED | ❌ |
| 06-27 12:45 | **dom 反馈：问题仍存在** | 任务执行人本身是项目参与人员 → 项目详情页看板看不到分配给自己的任务 | 这是 #1245 的修复目标 |
| 06-27 13:55 | **#1245** | `User.getRoleCode()` fallback 到 `"manager"` 是根因，TaskService / TaskBoardService 改用 `DataScopeConfigService.getRoleCode` | 局部修复 |
| **06-27 之后** | **CO-373 启动** | 系统里还有 27 处 `user.getRoleCode()` 直调都会触发同样雷 | — |
| 06-28 03:15 | #1259 | 引入 `EffectiveRoleResolver` 统一入口 + 收敛 13+ 处直调 | **这才系统性根治** |

**5 个 PR、跨度 2 天、用户反馈 3 次后才定位到根因。**

---

## 二、五次失败的根因：三条重复犯的错

### 错误 1：追症状不追根因

| 修复 PR | 看到的症状 | 实际的根因 |
|---|---|---|
| #1153 | "废弃 IN_PROGRESS" 标题字面 | OSS 用户 role_id=NULL 时 `User.getRoleCode()` 返回 `"manager"` |
| #1197 | "任务看板看不到任务" | 同上 |
| #1218 | "CANCELLED 任务消失" | 状态归一逻辑重复 + 根因同上 |
| #1223 | "IN_PROGRESS 还存在" | 状态枚举收口 + 根因同上 |

**教训**：当 issue 标题、用户反馈、PR description 都在说"任务看不到"时，要问的不是"任务看不到该怎么改"，而是"为什么看不到 —— 这条代码路径上有哪些权限判断？每个判断是怎么读 roleCode 的？"

### 错误 2：issue 评论里写了真需求，PR 都不读

**Zoey 在 06-26 14:14 评论里**清晰列出了三个真实业务场景：
- 项目负责人 403（场景 1）
- 投标负责人只能看自己 assignee（场景 2）
- 项目负责人和其他执行人只能看自己任务（场景 3）

但 **#1156 / #1197 / #1218 / #1223** 都没读这条评论，依然只盯着标题里的"废弃 IN_PROGRESS"。

**教训**：
- **issue 评论比 issue 标题更准确**。下次处理 issue 必须**先读完所有评论**再动手
- 在每个 PR description 里**显式引用**对应评论（"Fixes the scenarios described by @zoeyzhou364 at 14:14 in CO-361"）
- agent-start-task / who-touches 流程里加一条：**任务开始前必须读完 issue 所有评论**

### 错误 3：测试只测修过的场景，不测根因行为

#1245 自评 "54 个纯 mock 测试全绿"，但只验证了：
- 改过的 TaskService / TaskBoardService 行为正确
- 新增的 3 个 OSS roleCode 解析测试

**没验证**：
- `User.getRoleCode()` 实体 fallback 在其他场景里会不会引爆
- 系统里其他 26 处直调有没有同样的雷

直到 CO-373 才系统性地查了一遍，发现 27 处直调都会引爆同类问题。

**教训**：
- 修 bug 时必须**主动搜索根因的所有出现点**（grep 整个 codebase）
- 写测试必须**直接覆盖根因的行为**（这里是 `User.getRoleCode()` 在 OSS 用户上的行为），而不是只覆盖被改动的函数
- "测试通过" ≠ "问题被根除"。`User.getRoleCode()` 本身的行为没变，只是别处不再调它了 —— 这种"绕过雷"的修复不能让人安心

---

## 三、CO-373 是真正的根治：B5 + B6 工程防御

CO-361 / CO-373 教训的直接产物 —— **把根因变成立即可见、可拦截的工程约束**：

### B5: User.getRoleCode() 加 @Deprecated

```java
/**
 * @deprecated 业务权限判定请勿直接调用本方法。本方法在 OSS 同步用户
 * (role_id=NULL) 时 fallback 返回 "manager"，会导致业务权限判定误判
 * (CO-361 / CO-373 根因)。业务权限判定必须走下列统一入口之一：
 *   - EffectiveRoleResolver.resolveRoleCode(user)
 *   - DataScopeConfigService.getRoleCode(user)
 * 保留直调的合法场景（需在调用点加 // SAFE: 注释）：
 *   1. 登录响应装配 (AuthResponse)
 *   2. MDC 日志上下文 (TraceFilter)
 *   3. DataScopeConfigService.isLocalSystemAccount 内部判定
 *   4. DataScopeConfigService.getRoleCode 自身实现
 *   5. EffectiveRoleResolver 内部读取 entityRoleCode
 * 新增直调会被 scripts/check-rolecode-direct-calls.mjs pre-push 检查拦截。
 */
@Deprecated
public String getRoleCode() { ... }
```

### B6: scripts/check-rolecode-direct-calls.mjs（pre-push 拦截）

挂到 `scripts/pre-push-gate.sh` 第 9.5 节，挂到 `package.json` "check:rolecode-direct-calls"。

**扫描规则**：
1. 扫 `backend/src/main/java/**/*.java`
2. 检测 `user.getRoleCode()` / `currentUser.getRoleCode()` 等 User 类型 receiver 模式
3. 命中且不在豁免白名单 → pre-push gate fail
4. 豁免方式：调用点上方注释 `// SAFE: <理由>` 或 `// DEPRECATED: <理由>`
5. 文件白名单（仅 3 个）：User.java / EffectiveRoleResolver.java / DataScopeConfigService.java

**为什么用注释豁免而不是纯白名单文件**：
- 注释豁免**强制开发者写明理由**（"为什么这里可以保留直调"）
- 新文件加 SAFE 注释即可豁免，不需要 PR 改脚本白名单
- 纯白名单会让"为什么这个文件在白名单里"的审计变成考古

---

## 四、可复用的工程规则

### 规则 1：issue 评论先于 issue 标题

> 处理任何 issue 前，必须读完所有评论。

**怎么落地**：
- agent-start-task.sh 加一步："请贴出 issue 的最新 5 条评论"
- PR description 里**显式引用**对应评论（"Addresses @user 反馈的 N 场景"）

### 规则 2：根因暴露的代码路径必须全局收敛

> 发现根因在某段代码里 → 必须 grep 整个 codebase 找所有同类直调点 → 一次性收敛（不能打补丁）。

**怎么落地**：
- B6 那种 pre-push 拦截脚本：**只要根因出现在 X 函数 X 模式，全 codebase 扫**
- 拦截脚本比 PR review 更可靠 —— PR review 容易漏，脚本不会

### 规则 3：测试覆盖根因行为，不是修过的行为

> 修 bug 时写测试，必须直接测试根因行为（比如 `User.getRoleCode()` 在 OSS 用户上的行为），不能只测被改动的函数。

**怎么落地**：
- 写 bug fix PR 时强制写 1 个"根因行为测试"（不依赖被改动的函数）
- 这个测试放在 `*RootCauseTest.java` 里，独立于 fix 的具体函数

### 规则 4：mock 数据和真实 OSS 数据要分开测

> dom 12:45 的核心场景是 OSS 用户（role_id=NULL），但 e2e 用 `E2eDemoDataInitializer` 的本地 demo 账号 —— 本地账号根本不会触发 fallback 雷。

**怎么落地**：
- `E2eDemoDataInitializer` 增加 OSS 模拟用户 fixture（`external_org_source_app` 非空 + `roleProfile=null`）
- A1-A3 真复现必须在主工作区 trae 做，不能自动化
- e2e 覆盖"权限逻辑一致性"，A1-A3 覆盖"真 OSS fallback 行为"

### 规则 5：pre-existing error 也是 error

> #1259 commit 之后，本想干净 push，发现 lint 报 `jszip` 模块 unresolved。这是 origin/main 上 CO-378 引入的 pre-existing 问题。

**怎么处理**：
- 顺手修（如果改动很小）：本次任务就 fix，避免下次又卡
- 留作 follow-up（如果改动大）：创建子 issue 跟踪，不阻塞当前 push
- **绝不**用 `PRE_PUSH_GATE=0` 跳过（CLAUDE.md 明确禁止）

---

## 五、给后来者（agent 或人）的 checklist

处理"反复修不好的 bug"时：

- [ ] 读完 issue 所有评论，找到真正的需求描述（不只是标题）
- [ ] 列出所有相关 PR，对照 PR description 看都改了什么
- [ ] 用 git blame / git log 找每个 PR 的根因分析（不是"我猜是 X"，是"我验证过 X"）
- [ ] 写一个最小复现（dev 环境真复现 + 单测最小用例）
- [ ] grep 整个 codebase 找根因的所有出现点
- [ ] 收敛根因（迁移到统一入口 / 加 @Deprecated / 加拦截脚本）
- [ ] 写"根因行为测试"，不依赖被改动的函数
- [ ] 跑 pre-existing 错误的健康检查（lint / test / build / e2e）
- [ ] 跨入口 / 跨角色对比验证（如本例的 4×2 矩阵）
- [ ] 沉淀教训到本文件（不是处理记录，是可复用的工程规则）

---

## 六、相关 issue & PR

- **CO-361**：废弃 in-progress 项目负责人项目详情页任务看板缺失任务 两入口任务看板展示逻辑保持一致
  - Linear: https://linear.app/ericforai/issue/CO-361
- **CO-373**：统一服务层角色码解析入口，废弃直调 User.getRoleCode()
  - Linear: https://linear.app/ericforai/issue/CO-373
- **PR #1245**：OSS 用户 roleCode 解析修正（局部止血）
- **PR #1259**：EffectiveRoleResolver 统一入口（系统性根治）
- **commit f584ce58d**（minimax）：B5+B6 落地（@Deprecated + pre-push 拦截）
- **commit 4382cd519**（minimax）：A5 两入口一致性 e2e 回归测试