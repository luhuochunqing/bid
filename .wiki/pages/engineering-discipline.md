---
title: 工程纪律手册 — 反复修复的根因、根治与预防
space: engineering
category: guide
tags: [工程纪律, 根因分析, bug修复, 反复修复, 开发规范, 经验积累, 预防, SOP]
sources:
  - .wiki/pages/lessons-learned.md
  - .wiki/pages/lessons-learned/CO-361-five-rounds-no-fix.md
  - docs/lessons/lessons-learned.md
  - .wiki/pages/spring-pitfalls.md
  - .wiki/pages/flyway-migration-pitfalls.md
backlinks:
  - _index
  - lessons-learned
  - lessons-learned/CO-361-five-rounds-no-fix
  - spring-pitfalls
  - flyway-migration-pitfalls
  - crm-integration-lessons
  - frontend-pitfalls
  - notification-system-pitfalls
  - ai-provider-configuration
created: 2026-07-10
updated: 2026-07-10
health_checked: 2026-07-10
---
# 工程纪律手册 — 反复修复的根因、根治与预防

> **本文件是"活的"工程纪律手册，每次发生反复修复案例时更新。**
> 目标：把"反复修了十几轮都修不好"变成"一轮定位根因，一轮根治，一轮验证"。
>
> 凡是遇到"修了又修还修不好"的 bug，先读本文件第一章。
> 凡是修完 bug，必须按第四章 SOP 沉淀经验。
> 凡是每周/每个 sprint 结束，按第六章更新本文件。

---

## 一、为什么 bug 会反复修不好？（7 大根因）

> 从 CO-361（5 次修复）、CO-280（3 次 PR）、SPRING_CONFIG_IMPORT 事故等案例提炼。

### 根因 1：追症状不追根因

**表现**：看到报错就改，不深挖"为什么会报错"。

**典型案例**：
- CO-361：4 个 PR 都在改"任务看板看不到任务"的症状，根因是 `User.getRoleCode()` 在 OSS 用户上 fallback 返回 `"manager"`
- CO-280：PR !886 回滚了 PR !884 的正确修复，因为误判根因

**为什么会导致反复修复**：
症状是根因的表象，根因会在**多个代码路径**上引爆。只修一个路径，其他路径还会爆。

**如何避免**：
- 遇到 bug 先问"5 个为什么"（见第三章）
- 用 `grep` 搜索根因在整个 codebase 的所有出现点
- 一次性收敛所有出现点，不能只修当前报错的那一个

---

### 根因 2：外部配置覆盖代码修复

**表现**：代码改对了，测试也过了，但生产环境还是旧行为。

**典型案例**：
- SPRING_CONFIG_IMPORT 事故：jar 内配置正确，但 `/etc/xiyu-bid/application-org-mappings.yml` 外部配置覆盖了 jar 内配置，导致代码修复无效
- 用户 06234 的角色解析：代码已修复 `User.getRoleCode()`，但服务器外部配置文件还写着 `bid-SystemAdmin`

**为什么会导致反复修复**：
"jar 内配置正确 ≠ 运行时配置正确"。代码修复在生产环境被外部配置静默覆盖，开发者以为修好了，但问题依旧。

**如何避免**：
- 部署后必须验证生产环境实际生效的配置（不是 jar 内的配置）
- 检查 `SPRING_CONFIG_IMPORT` 是否存在
- 用 `Actuator/env` 端点验证运行时配置
- 详见 [[spring-pitfalls]] §5

---

### 根因 3：环境差异掩盖问题

**表现**：测试环境通过，生产环境失败。

**典型案例**：
- collation 冲突：测试环境 `users` 表 collation 是 `utf8mb4_unicode_ci`，生产环境是 `utf8mb4_0900_ai_ci`，JOIN 时报错。测试环境"恰好一致"掩盖了问题。
- OSS 用户 role_id=NULL：e2e 用 `E2eDemoDataInitializer` 的本地 demo 账号，不会触发 OSS fallback 雷。

**为什么会导致反复修复**：
测试环境的"历史包袱"会掩盖新部署的问题。开发者以为"测试通过 = 生产没问题"，但环境差异导致问题只在生产暴露。

**如何避免**：
- 测试环境必须尽量模拟生产环境（collation、数据特征、外部系统调用）
- 跨系统 bug 必须用真实外部系统场景验证（不能只测同源访问）
- 生产部署前做"环境差异检查"（见第四章 SOP）
- 详见 [[flyway-migration-pitfalls]] §3

---

### 根因 4：测试只测修过的函数，不测根因行为

**表现**：测试全绿，但 bug 还在。

**典型案例**：
- CO-361：#1245 自评"54 个测试全绿"，但只验证了改过的 `TaskService` 行为，没验证 `User.getRoleCode()` 本身的行为
- 测试覆盖了"修过的路径"，但根因在其他路径上还会引爆

**为什么会导致反复修复**：
"测试通过" ≠ "问题被根除"。如果测试只测修过的函数，根因行为本身没被验证，其他调用路径还会爆。

**如何避免**：
- 写 bug fix PR 时必须写 1 个"根因行为测试"（独立于被改动的函数）
- 测试要覆盖根因的所有出现点（不只是当前报错的那一个）
- 测试用例要包含"应该失败"的场景（确认 bug 被修复）

---

### 根因 5：修 A 破 B（引入新 bug）

**表现**：修好一个 bug，但引入了新 bug。

**典型案例**：
- 修复 CRM 状态映射后，引入了 webhook 格式错误
- 修复 collation 冲突后，临时表 COLLATE 不匹配导致 JOIN 失败
- 前端修复路由组件后，E2E 选择器失效
- **AI fallback 双倍调用**（PR !1979 → !1982）：PR !1979 修复了 fallback 触发条件（让 `response_format`/`json_schema` 关键词匹配 BadRequest），但 fallback 机制每次都先尝试注定失败的 json_schema（10-25 秒），再 fallback 到 json_object（再 10-25 秒），导致 AI 解析耗时 22-50 秒、前端超时。根因是对 fallback 的性能假设有偏差——以为 fallback 是偶尔触发的异常路径，实际该 AI 网关每次都需要 fallback

**为什么会导致反复修复**：
修复一个 bug 可能影响其他代码路径，如果没有全面的回归测试，新 bug 会在后续暴露。

**如何避免**：
- 修复 bug 后必须跑全量回归测试（不只是修过的模块）
- 修复前评估影响范围（`grep` 所有调用点）
- 新增 `@Deprecated` 标记比直接删除更安全（不会立即破坏调用方）

---

### 根因 6：对框架/系统的理解有偏差

**表现**：以为框架会这样做，但实际不是。

**典型案例**：
- `@Transactional REQUIRES_NEW + try-catch`：以为内层事务独立，实际 rollback-only 标记会传播
- `@Async 自调用`：以为 `self.method()` 会异步执行，实际不经过代理
- `@ConditionalOnBean` 在 `@Service` 上：以为条件装配可靠，实际依赖 Bean 注册顺序
- `el-form-item :required`：以为只是显示星号，实际会注入校验规则覆盖自定义 rules

**为什么会导致反复修复**：
对框架的理解有偏差，导致"修复"方向错误。改了半天发现框架根本不是这么工作的。

**如何避免**：
- 遇到框架行为异常时，先查官方文档/源码，确认实际行为
- 不要凭直觉判断框架行为，要用最小复现验证
- 框架陷阱要沉淀到 wiki（见 [[spring-pitfalls]]、[[frontend-pitfalls]]）

---

### 根因 7：盲目相信"已修复"，没有验证

**表现**：代码改完就提交，没有在真实环境验证。

**典型案例**：
- SPRING_CONFIG_IMPORT 事故：代码合入 main 并打包部署，但没验证运行时配置是否生效
- CO-280：PR !886 只测同源场景就认为修复生效，CRM 实测才暴露真正根因

**为什么会导致反复修复**：
"代码改了" ≠ "问题解决了"。没有验证就认为修复完成，问题会在后续暴露，导致反复修复。

**如何避免**：
- 修复 bug 后必须在真实环境验证（至少在测试环境复现 bug 场景）
- 验证要覆盖"应该成功"和"应该失败"两种场景
- 验证结果要记录在 PR 描述中

---

## 二、反复修复的代价模型

```
第 1 次修复：1 小时（定位 + 改 + 测）
第 2 次修复：2 小时（发现没修好 + 重新定位 + 改 + 测）
第 3 次修复：4 小时（信心下降 + 重新定位 + 改 + 测）
...
第 N 次修复：2^N 小时（团队信任崩塌 + 反复排查）

vs.

根因分析 + 系统性根治：3 小时（5个为什么 + grep 全量收敛 + 根因测试）
```

**结论**：反复修复的代价是指数增长的。**第 2 次修复时就应该停下来做根因分析，而不是继续打补丁。**

---

## 三、根因分析框架："5 个为什么" + 全局收敛

### 3.1 5 个为什么（5 Whys）

遇到 bug 时，连续问 5 个"为什么"：

```
bug：任务看板看不到任务

为什么 1：为什么看不到？→ 权限校验返回 ROLE_NOT_AUTHORIZED
为什么 2：为什么返回 ROLE_NOT_AUTHORIZED？→ 用户角色被解析为 "manager"
为什么 3：为什么被解析为 "manager"？→ User.getRoleCode() 在 role_id=NULL 时 fallback 返回 "manager"
为什么 4：为什么 role_id=NULL？→ OSS 同步用户没有 role_id
为什么 5：为什么 OSS 用户会走到 User.getRoleCode()？→ 服务层直接调了 User.getRoleCode()，没有走 EffectiveRoleResolver

根因：User.getRoleCode() 在 OSS 用户上 fallback 返回 "manager"，且服务层有 27 处直调
```

### 3.2 全局收敛

找到根因后，**必须 grep 整个 codebase 找所有同类出现点**：

```bash
# 找所有调用点
grep -rn "user.getRoleCode()" backend/src/main/java/

# 一次性收敛所有调用点（迁移到统一入口）
# 不能只修当前报错的那一个
```

### 3.3 根因验证

确认根因后，**必须验证**：

```bash
# 1. 最小复现
# 在 dev 环境用 OSS 用户（role_id=NULL）触发 bug 场景

# 2. 根因测试
# 写一个直接测试根因行为的测试用例（不依赖被改动的函数）

# 3. 全局搜索
# grep 所有同类调用点，确认全部收敛
```

---

## 四、Bug 修复 SOP（标准流程）

### 4.1 定位阶段（不要急于改代码）

```
[ ] 1. 读完 issue 所有评论（评论比标题更准确）
[ ] 2. 列出所有相关 PR，对照 PR description 看都改了什么
[ ] 3. 用 git blame / git log 找每个 PR 的根因分析
[ ] 4. 在 dev 环境复现 bug
[ ] 5. 问"5 个为什么"定位根因
[ ] 6. grep 整个 codebase 找根因的所有出现点
```

### 4.2 修复阶段（一次性根治）

```
[ ] 7. 收敛根因（迁移到统一入口 / 加 @Deprecated / 加拦截脚本）
[ ] 8. 不要只修当前报错的路径，要修所有同类路径
[ ] 9. 评估影响范围（grep 所有调用点）
[ ] 10. 写"根因行为测试"（独立于被改动的函数）
[ ] 11. 跑全量回归测试（不只是修过的模块）
```

### 4.3 验证阶段（必须在真实环境验证）

```
[ ] 12. 在 dev 环境验证 bug 场景已修复
[ ] 13. 验证"应该成功"和"应该失败"两种场景
[ ] 14. 检查外部配置是否覆盖代码修复（SPRING_CONFIG_IMPORT 等）
[ ] 15. 跑 pre-existing 错误的健康检查（lint / test / build / e2e）
[ ] 16. 跨入口 / 跨角色对比验证
[ ] 17. 部署到测试环境再次验证
```

### 4.4 沉淀阶段（防止下次再犯）

```
[ ] 18. 沉淀教训到本文件（不是处理记录，是可复用的工程规则）
[ ] 19. 如果是框架陷阱，沉淀到对应 wiki 页面（spring-pitfalls / frontend-pitfalls 等）
[ ] 20. 如果是反复修复案例，沉淀到 lessons-learned/ 目录
[ ] 21. 加 pre-push 拦截脚本（如果根因可以通过代码模式检测）
[ ] 22. 更新本文件的"案例库"章节
```

---

## 五、开发规范（预防胜于治疗）

### 5.1 编码规范

| 规范 | 原因 | 参考 |
|------|------|------|
| 服务层禁止直调 `User.getRoleCode()` | OSS 用户会 fallback 到 "manager" | [[lessons-learned/CO-361-five-rounds-no-fix]] |
| `@Transactional` 不要和 try-catch 混用 | rollback-only 标记会传播 | [[spring-pitfalls]] §1 |
| `@Async` 方法不要自调用 | 自调用不经过代理，注解不生效 | [[spring-pitfalls]] §2 |
| `@Async` 方法操作数据库必须加 `@Transactional` | 异步线程不继承主线程的 Hibernate Session | [[spring-pitfalls]] §3 |
| 前端权限检查用 every（AND）而非 some（OR） | some 会让"有一个权限"就通过 | [[frontend-pitfalls]] §7 |
| 迁移脚本必须用 `new-migration.sh` 创建 | 手动取版本号会撞号 | [[flyway-migration-pitfalls]] §2 |
| 类文件不超过 300 行 | 遵守单一职责原则 | AGENTS.md |
| 外部服务异常必须保留原始 HTTP 状态码 | 502/503 比 500 更准确 | [[spring-pitfalls]] §9 |

### 5.2 测试规范

| 规范 | 原因 |
|------|------|
| 修 bug 必须写"根因行为测试" | 只测修过的函数 ≠ 问题被根除 |
| 测试用例要包含"应该失败"的场景 | 确认 bug 被修复 |
| 跨系统 bug 必须用真实外部系统场景验证 | 同源场景会掩盖问题 |
| E2E 选择器优先用 role > testid > label > text | text 会随 UI 变更失效 |

### 5.3 部署规范

| 规范 | 原因 | 参考 |
|------|------|------|
| 部署后必须验证运行时配置 | jar 内配置正确 ≠ 运行时配置正确 | [[spring-pitfalls]] §5 |
| 检查 `SPRING_CONFIG_IMPORT` 是否存在 | 外部配置会覆盖 jar 内配置 | [[spring-pitfalls]] §5 |
| 测试环境必须模拟生产环境 | 环境差异会掩盖问题 | [[flyway-migration-pitfalls]] §3 |
| 生产部署前做"环境差异检查" | collation / 字符集 / 时区 / SQL mode | [[flyway-migration-pitfalls]] §3 |

### 5.4 协作规范

| 规范 | 原因 |
|------|------|
| issue 评论比 issue 标题更准确 | 处理 issue 前必须读完所有评论 |
| PR description 要引用对应 issue 评论 | 显式关联需求和修复 |
| 回滚 PR 前必须确认根因 | 不能因为"看起来修了另一个问题"就回滚 |
| 每日 push WIP 分支 | 让其他 agent 看到你的意图 |

---

## 六、经验积累机制（自我进化）

### 6.1 反馈循环

```
发生 bug
  ↓
按 SOP 修复（第四章）
  ↓
沉淀教训（第 4.4 阶段）
  ↓
更新本文件（添加根因 / 规范 / 案例）
  ↓
加 pre-push 拦截脚本（如果根因可检测）
  ↓
下次遇到同类 bug → 直接查本文件 → 一轮修复
```

### 6.2 更新频率

| 频率 | 更新内容 |
|------|---------|
| 每次 bug 修复 | 沉淀教训到 4.4 阶段 |
| 每周/sprint 结束 | 更新本文件的"案例库"和"开发规范" |
| 每月 | 回顾本文件，提炼新的根因模式 |
| 每季度 | 评估本文件的有效性（反复修复次数是否减少） |

### 6.3 案例库索引

| 案例 | 反复次数 | 根因 | 参考 |
|------|---------|------|------|
| 投标专员保证金页面无数据 | 1 次 | 盲目相信"已修复" + 测试未暴露生产代码缺失 | PR !1971 未修改 MarginQueryRole，用户 10208 部署后仍无数据 |
| 跨部门协作人员首页 403 | 1 次 | 前端用 OR 语义把 task.review/task.assign 纳入告警可见条件 | Workbench.vue:155，本次修复 |
| CO-361 角色解析 | 5 次 | 追症状不追根因 + User.getRoleCode() fallback | [[lessons-learned/CO-361-five-rounds-no-fix]] |
| CO-280 下载 URL | 3 次 | 多根因同时存在 + 误回滚正确修复 | docs/lessons/lessons-learned.md §4 |
| SPRING_CONFIG_IMPORT | 2 次 | 外部配置覆盖 jar 内配置 | [[spring-pitfalls]] §5 |
| collation 冲突 | 1 次 | 测试环境掩盖生产问题 | [[flyway-migration-pitfalls]] §3 |
| CRM 商机状态映射 | 1 次 | 枚举值凭直觉不查文档 | [[crm-integration-lessons]] §1 |
| 通知 targetUrl 跳转失效 | 1 次 | 大小写不一致 | [[notification-system-pitfalls]] §1 |
| AI Provider 硬编码 | 1 次 | 未读取 activeProvider 配置 | [[ai-provider-configuration]] §1 |
| AI fallback 双倍调用 | 1 次 | 修 A 破 B：PR !1979 修 fallback 条件但引入每次双倍 AI 调用 | PR !1979 → !1982，OpenAiSdkStructuredOutputTransport |

### 6.4 pre-push 拦截脚本索引

| 脚本 | 检测什么 | 根因案例 |
|------|---------|---------|
| `scripts/check-rolecode-direct-calls.mjs` | 服务层直调 `User.getRoleCode()` | CO-361 |
| `scripts/pre-push-gate.sh` | 17 道门禁 | 多个案例 |
| `scripts/check-git-wrapper.sh` | `--no-verify` 绕过 | 工程纪律 |
| `scripts/next-migration-version.sh --reserve` | 迁移版本号冲突 | Flyway 撞号 |

---

## 七、给开发者（agent 或人）的 checklist

### 7.1 修 bug 前

```
[ ] 读完 issue 所有评论
[ ] 在 dev 环境复现 bug
[ ] 问"5 个为什么"定位根因
[ ] grep 整个 codebase 找根因的所有出现点
[ ] 查本文件第一章是否有同类根因
```

### 7.2 修 bug 时

```
[ ] 一次性收敛所有同类路径
[ ] 写"根因行为测试"
[ ] 跑全量回归测试
[ ] 评估影响范围
```

### 7.3 修 bug 后

```
[ ] 在真实环境验证
[ ] 检查外部配置是否覆盖
[ ] 沉淀教训到本文件
[ ] 加 pre-push 拦截脚本（如果根因可检测）
[ ] 更新案例库
```

### 7.4 反复修复时（第 2 次修同一个 bug）

```
[ ] 停下来！不要继续打补丁
[ ] 重新做根因分析（第三章）
[ ] 检查是否是外部配置覆盖（根因 2）
[ ] 检查是否是环境差异（根因 3）
[ ] 检查是否是对框架理解有偏差（根因 6）
[ ] 召集其他人/其他 agent 一起排查
```

---

## 八、相关文档

- [[lessons-learned]] — 工程经验总结（按 session 追加的事故记录）
- [[lessons-learned/CO-361-five-rounds-no-fix]] — CO-361 五次修复不彻底的教训
- [[spring-pitfalls]] — Spring Boot 陷阱集
- [[flyway-migration-pitfalls]] — Flyway 迁移陷阱集
- [[crm-integration-lessons]] — CRM 集成踩坑集
- [[frontend-pitfalls]] — 前端 Vue3 / Element Plus 陷阱集
- [[notification-system-pitfalls]] — 通知系统陷阱集
- [[ai-provider-configuration]] — AI Provider 配置与陷阱指南
- `docs/lessons/lessons-learned.md` — 通用工程教训与复盘
- RULES.md — 四阶段流程（plan → tdd → code-review → refactor-clean）
- RELIABILITY.md — 门禁体系、文件锁、PR 创建、回滚手册

---

## 九、变更记录

| 日期 | 变更内容 |
|------|---------|
| 2026-07-10 | 首次创建，从 CO-361、CO-280、SPRING_CONFIG_IMPORT 等案例提炼 7 大根因、SOP、规范 |
| 2026-07-10 | 新增"AI fallback 双倍调用"案例到根因 5 + 案例库索引（PR !1979 → !1982） |
