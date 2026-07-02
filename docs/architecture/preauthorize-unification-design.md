# 设计文档：消除 @PreAuthorize hasAnyRole 双轨制技术债

> **文档类型**: 架构设计 RFC（完整审核版）
> **状态**: Draft — 待审核
> **起草**: 2026-07-02
> **作者**: zcode agent
> **关联**: Spec Kit `specs/024-preauthorize-unification/`、Constitution v1.3.0
> **审核方式**: 请从头到尾通读，重点审核第二部分（根因）和第三部分（方案），第四/五部分是落地细节

---

## 摘要（30 秒读完）

后端有 **176 处 `@PreAuthorize` 注解** + **2 处程序化鉴权**使用 `hasAnyRole`/`hasRole` 角色枚举式白名单（精确 grep 核实，见附录 B），与 `RoleProfileCatalog` 已定义的细粒度权限键形成**双轨制**。根因是 2026-06-16 的 `eb58f2817` 切断了三个角色的 legacy role 兼容（堵越权），但**没有同步迁移依赖这些 authority 的 176 处白名单**。从此系统分裂成"新模型给角色赋权 / 旧模型拒绝承认"的矛盾状态，导致 **20+ 个反复返工的 403 PR**（CO-362 → CO-466）。最近一次症状是 `bid-otherDept` 用户（工号 09118）2026-07-02 访问 `GET /api/task-extended-fields` 被 403。

**本方案分三层治理**：
1. **P1 立即修复**：删 `TaskExtendedFieldController` 的过度收紧注解（治本次故障）
2. **P2 架构守卫**：ArchitectureTest 新增规则禁止 `hasAnyRole`，配 176 处豁免清单 + 数量一致性自验（止血防复发）
3. **P3 分批迁移**：176 处按 7 个业务模块批次迁移到 `hasAuthority`，最终守卫升级为硬失败门禁（治本）

---

## 第一部分：背景与现状

### 1.1 系统的权限模型（两套并行的机制）

后端鉴权实际上有**两套不同的模型**同时存在：

| 模型 | 机制 | 真相来源 | 表达式 |
|---|---|---|---|
| **A. 角色枚举式白名单** | 列出允许的角色名，Spring Security 检查 `ROLE_*` authority | Controller 手工抄写 | `hasAnyRole('ADMIN','MANAGER',...)` |
| **B. 细粒度权限键** | 列出业务权限键，检查用户是否拥有该权限 | `RoleProfileCatalog` seed 统一定义 | `hasAuthority('task.handle.own')` |

**模型 B 才是正确的**——权限键在 `RoleProfileCatalog.SeedDefinition.menuPermissions` 中统一定义，每个角色映射到一组权限键。新增角色时只需在 seed 注册权限，Controller 注解**无需任何修改**。

**模型 A 是历史包袱**——它在每个 Controller 里手抄一份角色列表，与 `RoleProfileCatalog` 没有任何同步机制，必然漂移。

### 1.2 7 个系统角色（固定不变）

| roleCode | 角色名 | 业务定位 |
|---|---|---|
| `admin` | 管理员 | 系统全权限 |
| `/bidAdmin` | 投标管理员 | 复盘审核、结项闸门 |
| `bid-TeamLeader` | 投标组长 | 标书编制、评标推进 |
| `bid-projectLeader` | 投标项目负责人 | 立项发起、客户维护 |
| `bid-Team` | 投标专员 | 投标辅助、任务处理 |
| `bid-administration` | 行政人员 | 资质证书管理 |
| `bid-otherDept` | 跨部门协同人员 | 项目任务处理 |

**这 7 个角色在迁移期内不新增**（用户已确认）。

### 1.3 关键历史节点：eb58f2817（2026-06-16）

这次提交是当前所有问题的**分水岭**。

**背景**：跨部门协同人员（`bid-otherDept`）登录时经 legacy fallback 拿到 `ROLE_STAFF`，从而通过 100+ 处 `hasAnyRole(... 'STAFF' ...)` 越权访问标讯/项目/知识库（蓝图要求该角色仅处理任务）。

**修复**：引入 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 机制，对三个新式受限角色**切断 legacy role 兼容**：
- `bid-otherDept`
- `bid-administration`
- `bid-Team`

这三个角色不再继承 `ROLE_STAFF` / `ROLE_MANAGER`，只靠自身 `ROLE_<CODE>` + 细粒度 menuPermissions 鉴权。

**这次修复是正确的**——堵越权的方向完全正确，本特性**不动它**。

**但它留下了一个烂尾债**：切断 legacy 兼容的同时，**没有同步迁移依赖 legacy role 的 176 处 `hasAnyRole` 白名单**。这 176 处假设这三个角色"仍然有 `ROLE_MANAGER`"，但事实已经没有。从此双轨制矛盾被激活。

---

## 第二部分：根因分析

### 2.1 现象时间线

| 时间 | 事件 | 角色 | 表现 |
|---|---|---|---|
| 06-16 | `eb58f2817` 切断 legacy 兼容 | bid-otherDept 等 | 双轨制激活 |
| 06-27 起 | CO-362 → CO-466 共 20+ 个 PR | 各角色 | 反复修 403，每次单点补漏 |
| 07-02 16:49 | 生产故障（traceId `50f8ae0e...`） | bid-otherDept 09118 | `GET /api/task-extended-fields` → 403 |

### 2.2 最近故障的完整证据链（服务器日志铁证）

按【全链路日志排查 SOP】Layer 2 抓取的真实日志（07-02，traceId `50f8ae0e44554ea0a53c72cf953910c4`）：

```
16:49:47.119  UserDetails authorities built: user=09118 isOssUser=true
              roleCode=bid-otherDept skipLegacyCompat=true
              authorities=[bid-otherDept, ROLE_BID_OTHERDEPT, dashboard,
              task-board, task.view.own, task.handle.own, ...]
16:49:47.123  WARN  GlobalExceptionHandler - 权限不足 - URI: /api/task-extended-fields,
              User: 09118, Message: Access Denied
16:49:47.123  access_log method=GET uri=/api/task-extended-fields status=403 elapsed=0ms
```

**关键事实**：
- 用户 `09118` 真实角色是 `bid-otherDept`（不是 bid-Team）
- `bid-otherDept` 拥有 `task.handle.own`（**模型 B 应该放行**）
- 但被 `hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM','SALES')` 拦截（**模型 A 拒绝承认**）
- `bid-otherDept` 在 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 中，没有 `ROLE_MANAGER`，自身 `ROLE_BID_OTHERDEPT` 又不在白名单 → 403

**这就是双轨制的铁证**：同一个业务动作，两套模型给出矛盾结论。

### 2.3 根因（一句话）

> **`eb58f2817` 关闭了 legacy role 兼容（堵住了越权），但没有同步清理依赖 legacy role 的 176 处 `hasAnyRole` 白名单。系统从此分裂为"新模型给角色正确赋权 / 旧模型拒绝承认"的双轨制，每次冒泡都是一个 Controller 在新模型下被旧白名单误伤。**

### 2.4 为什么会反复返工 20+ 次

看 CO-466 的 commit message 自述：

> CO-452 修复详情页 403 时**遗漏了 5 个下载/导出/预览接口的 @PreAuthorize 注解**

每次修 403 都是**单 Controller、单方法**地补漏。但全仓有 **176 处、57 个 Controller**。同一个 `ProjectArchiveController` 里，CO-452 修了详情接口，CO-466 又发现同 Controller 的另外 5 个方法没改——**打地鼠模式**。

根因不是哪个角色被漏，而是**没有把"176 处旧模型"作为系统性技术债来治理**，每次只补眼前那个洞。

### 2.5 与历史教训的对照

这和项目已记录的多条 lessons 高度同构，是同一个模式在不同接口的反复出现：

| 教训 | 角色 | 表面 | 同一根因 |
|---|---|---|---|
| §28 CO-415 | bid-Team | `returnAccount` 被 hasAnyRole 一刀切 | 角色白名单 + 双轨制 |
| §26 CO-439 | bid-administration | 前端导航权限漏配 | 角色权限定义不完整覆盖 |
| §24 CO-375 | 多角色 | canUpload/canDelete 不对称 | Policy 设计缺整体视角 |
| **本次** | bid-otherDept | task-extended-fields 白名单漏角色 | 角色白名单 + 双轨制 |

**只要系统还在用 `hasAnyRole(角色枚举)` 做 Controller 级权限控制，这个 bug 就会以不同角色、不同接口的形式反复出现。**

---

## 第三部分：方案设计（核心审核内容）

### 3.1 设计目标

1. **根治**（非治标）：消除双轨制这个**缺陷形态本身**，不是补某个角色
2. **防复发**：迁移期间债务零增长，完成后有自动化门禁
3. **渐进**：不影响业务，可分批交付，每批独立部署
4. **不引入复杂度**：遵循 Constitution "Boring Proven Patterns"，用 Spring 原生能力，不造轮子

### 3.2 目标权限模型（最终形态）

全仓 `@PreAuthorize` **只允许两种表达式**：

```java
// 形态一：早过滤（是否登录）
@PreAuthorize("isAuthenticated()")

// 形态二：细粒度业务权限（是否拥有该权限键）
@PreAuthorize("hasAuthority('" + RoleProfileCatalog.SOME_PERMISSION + "')")
```

**禁止形态**（迁移完成后必须消失）：
- ❌ `hasAnyRole('ADMIN','MANAGER',...)` — 角色枚举式白名单
- ❌ `hasRole('ADMIN')` — 同上（例外：`SecurityConfig` 的 `/api/admin/**` 路径级兜底，是 defense-in-depth，保留）

**为什么这两种足够**：
- Controller 的天职是"早过滤"（是否登录 + 是否有模块权限），用 `isAuthenticated` 或 `hasAuthority` 表达
- 真正的"是否对该具体资源有操作权限"下沉到 Service 层 Policy（如 `ProjectDocumentWorkflowPolicy`），那里可以做 `roleCode + currentUserId + uploaderId` 多维度决策
- 这与 lessons §24 教训 5 完全一致："Controller `@PreAuthorize` 不能过度收紧，只做 isAuthenticated 早过滤"

### 3.3 三层治理方案

#### 第一层（P1）：立即修复生产故障

**目标接口**：`GET /api/task-extended-fields`（`TaskExtendedFieldController`）

**为什么选这个语义**：它是**全局只读字段 schema** 接口——
- 类注释第 21 行明确写"公开读取"
- Service `listEnabled()` 是纯查询 `findByEnabledTrueOrderBySortOrderAsc`，无任何身份维度
- 用途是返回字段元数据供 TaskForm 动态渲染输入控件
- 用 `hasAnyRole` 角色白名单收紧属于**明显的过度鉴权**

**修复**：删除方法级 `@PreAuthorize`，回退到类级已有的 `@PreAuthorize("isAuthenticated()")`。

```java
// 修复前（TaskExtendedFieldController.java:40）
@GetMapping
@PreAuthorize("hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM','SALES')")
public ResponseEntity<...> list() { ... }

// 修复后（类级已有 isAuthenticated，方法级不需要再收紧）
@GetMapping
public ResponseEntity<...> list() { ... }
```

**验证**：补 SecurityTest，覆盖 `bid-otherDept` / `bid-administration` 角色 200，匿名 401。

#### 第二层（P2）：架构测试守卫（止血防复发）

这是本方案的**关键创新点**。

**问题**：如果只做 P1，下次开发者新增 Controller 又用 `hasAnyRole`，债务继续增长。即使 P3 全部迁移完，也可能死灰复燃。

**方案**：在 ArchitectureTest 新增**两条正交规则**，禁止新增 `hasAnyRole`/`hasRole`。

**实现**（用项目已有的 ArchUnit 框架，当前 27 条 `@ArchTest` 规则）：

**规则 1：注解值扫描**（用真实 ArchUnit API，修正 gemini 指出的伪代码错误）

```java
@ArchTest
public static final ArchRule controllers_must_not_use_role_enumeration_auth =
    methods()
        .that().areAnnotatedWith(PreAuthorize.class)
        .should(new ArchCondition<JavaMethod>(
                "不使用 hasAnyRole/hasRole 角色枚举式白名单（Constitution VI）") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                String spel = method.getAnnotationOfType(PreAuthorize.class).value();
                if (spel.contains("hasAnyRole") || spel.contains("hasRole")) {
                    events.add(SimpleConditionEvent.violated(method,
                        "禁止 hasAnyRole/hasRole，改用 hasAuthority('<perm>') 或 isAuthenticated()，"
                        + "参见 Constitution VI 与 specs/024-preauthorize-unification；"
                        + "method=" + method.getFullName() + " spel=" + spel));
                }
            }
        });
```

**规则 2：总数一致性自验**（核心创新，独立于规则 1，用 AssertJ）

```java
/** 当前遗留使用点总数。每迁移一处递减 1，归零后此规则改为恒失败（守卫升级硬失败）。 */
private static final int EXPECTED_LEGACY_USE_COUNT = 176;

@ArchTest
public static final void legacy_hasanyrole_count_must_match_baseline(JavaClasses classes) {
    int actual = countPreAuthorizeWithHasAnyRoleOrHasRole(classes);  // 扫描实际注解
    Assertions.assertThat(actual)
        .as("hasAnyRole/hasRole @PreAuthorize 使用点总数须与 EXPECTED_LEGACY_USE_COUNT 一致；"
          + "不一致说明：1)新增了违规注解未走迁移流程 2)迁移后忘更新此常量")
        .isEqualTo(EXPECTED_LEGACY_USE_COUNT);
}
```

**双轨自验的设计理由**（采纳 gemini 改进建议 1）：

gemini 审核指出：Controller 级豁免无法发现"Controller 内部分方法迁移"。所以本方案改为**双轨**：

| 轨道 | 机制 | 防什么 |
|---|---|---|
| 规则 1（注解扫描） | 扫描每个 `@PreAuthorize` 的 SpEL，含 hasAnyRole/hasRole 即报 | 任何使用点都报，强制开发者走迁移流程 |
| 规则 2（总数断言） | 实际使用点数 == EXPECTED 常量 | 防偷偷新增、防迁移后忘改常量、防部分迁移 |

**两条规则协同**：迁移一处 → 实际数减 1 → 规则 2 要求 EXPECTED 同步减 1（开发者必须改常量）→ 规则 1 对剩余的存量仍报但不阻塞（因为 EXPECTED 已对齐）。最终 EXPECTED 归零 → 删除规则 2 → 规则 1 升级为硬失败。

**为什么不用 ArchUnit 自带的 freeze()**：freeze 也能冻结现状、禁止新增违规。但它的基线存储在 `archunit_store` 目录，团队理解成本高，错误信息不如自定义 `ArchCondition` 友好，且总数断言更直观地编码了"迁移进度"这个不变式。不作为主方案。

#### 第三层（P3）：存量分批迁移（治本）

176 处注解使用点按业务模块分 7 批迁移，每批一个 PR + SecurityTest 回归。

> 批次内 Controller 数和使用点数为精确 grep 值（响应 gemini 审核已复核）。部分批次合并了多模块，使用点数为近似（标 ~），以 P2 守卫的 EXPECTED 总数 176 为权威基准。

| 批次 | 模块 | Controller 数 | 使用点数 | 优先级理由 |
|---|---|---|---|---|
| **1** | task | 2 | ~7 | P1 故障载体，含本次生产接口 |
| **2** | casework | 4 | ~12 | CO-452/CO-466 已部分迁移，需收尾 |
| **3** | knowledge | 0（已完成 CO-394 A/B/C/D） | 0 | 仅守卫验证 |
| **4** | resources | 6 | ~20 | 含 CO-409 临时补丁（混合表达式）需清理 |
| **5** | project | 6 | ~30 | 含复合业务逻辑，需 Service Policy 兜底审查 |
| **6** | tender | 3 | ~20 | 含 EXTERNAL_API 历史包袱 |
| **7** | platform + analytics + 分散模块 + 4 个常量引用 Controller | ~36 | ~87 | 剩余收尾（含 BatchOperationController/Export/TenderUpload/BidResultQuery 的 ADMIN_MANAGER_EXPR 常量） |
| | **合计** | **57** | **176** | |

> 另有 2 处程序化鉴权（EC5）不在此表，单独在批次 7 处理。

**已验证的迁移范式**（CO-394-A，commit `b64592304`）：

```java
// 迁移前
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public ResponseEntity<...> create(...) { ... }

// 迁移后
private static final String CREATE_PERM = RoleProfileCatalog.BRAND_AUTH_CREATE_PERMISSION;
@PreAuthorize("hasAuthority('" + CREATE_PERM + "')")
public ResponseEntity<...> create(...) { ... }
```

要点：
- 类级放宽为 `isAuthenticated()`
- 方法级引用 `RoleProfileCatalog` 常量（**不硬编码权限键字符串**，避免拼写漂移）
- 补 `RoleProfileCatalogTest` 断言验证三角色权限分配
- 补 SecurityTest 回归（应有权限角色 200 + 无权限角色 403）

**每批 PR 必须包含**：
1. Controller 注解迁移 diff
2. **权限键覆盖率预检**（R6 对策）：扫描本批将引用的 `hasAuthority` 参数，断言每个权限键在 `RoleProfileCatalog` 至少被一个角色的 `menuPermissions` 持有，避免迁移后全角色 403
3. 若涉及新权限键：RoleProfileCatalog 注册 + Flyway V/U 迁移
4. SecurityTest 回归用例（应有权限角色 200 + 无权限角色 403）
5. ArchitectureTest 的 `EXPECTED_LEGACY_USE_COUNT` 常量同步递减（数量自验强制对齐）
6. PR 描述附 grep 前后对比（`grep -rEn '@PreAuthorize\("hasAnyRole...'` 行数）

**最终**：全部迁移完成后，`EXPECTED_LEGACY_USE_COUNT` 归零 → 删除规则 2（总数断言）→ 规则 1（注解扫描）升级为硬失败门禁，任何 `hasAnyRole`/`hasRole` 都直接构建失败。

### 3.4 Edge Case 处理（已识别 4 个）

#### EC1: SecurityConfig 路径级兜底（保留，不迁移）

`SecurityConfig.java` 的 `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 是 Servlet Filter 链的路径级兜底，**不是业务 Controller 的方法级 @PreAuthorize**。它是 URL 级早过滤，与"角色枚举式方法级白名单"是不同层面。

**决策**：保留不动。宪法 VI 已标注为例外。这是 defense-in-depth，与 `/api/admin/**` 下 Controller 方法本身也会用 `@PreAuthorize` 形成双重保险不冲突。

#### EC2: EXTERNAL_API 角色（转权限键）

`TenderIntegrationController.java:52` 的 `@PreAuthorize("hasRole('EXTERNAL_API')")` 是外部 API Key 鉴权，不是业务用户角色。

**决策**：迁移为 `@PreAuthorize("hasAuthority('integration.external')")`，在 RoleProfileCatalog 为外部 API Key 主体单独注册该权限键（不归属任何业务角色）。需同步确认 `ApiKeyAuthService` 授予权限的方式（若现状只授 `ROLE_EXTERNAL_API`，需改造）。列入批次 6 一起处理，单独写 SecurityTest。

#### EC3: 复合表达式（拆解到 Service）

如 `BatchOperationController` 的：
```java
@PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and !#type.equalsIgnoreCase('tender'))")
```

包含业务逻辑（`!#type.equalsIgnoreCase('tender')`），违反"Controller 不做业务授权"。

**决策**：拆解为 `hasAuthority` + Service 层 Policy：

```java
// Controller 只做模块权限早过滤
@PreAuthorize("hasAuthority('batch.operation')")
public ResponseEntity<...> run(@RequestParam String type, ...) {
    batchService.assertTypeAllowedForCurrentUser(type);  // 业务级限制下沉
    ...
}
```

列入批次 7，逐个拆解。

#### EC4: CO-409 混合补丁表达式（清理）

`CaCertificateController` 共 14 个 `@PreAuthorize` 注解，其中 **7 处**是混合补丁（精确 grep 确认，L77/84/91/240/253/265/275）：
```java
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')")
```

这是 CO-409 的临时补丁——双轨制最丑陋的形态（同时用 hasAnyRole 和 hasAuthority）。

**gemini 审核补充的关键洞察**：`hasAuthority('ROLE_BID_TEAM')` 用了 `ROLE_` 前缀的权限键，这本身就是反模式——Spring 的 `hasRole('BID_TEAM')` 和 `hasAuthority('ROLE_BID_TEAM')` 检查的是同一个 authority，语义重叠但实现路径不同。

**决策**：统一改为 `@PreAuthorize("hasAuthority('resource-ca')")`。CO-409 已为 bid-Team 注册了 `resource-ca` 业务权限键，迁移后语义等价，彻底消除 `ROLE_` 前缀混乱。该 Controller 全部 14 个注解在批次 4 一起对齐。列入批次 4（resources 模块）。

#### EC5: 程序化鉴权（非 `@PreAuthorize` 注解形态，单独处理）

**gemini 审核发现**：`hasAnyRole`/`hasRole` 不仅出现在 `@PreAuthorize` 注解中，还有 2 处**程序化鉴权**形态，不在此前统计的 176 注解使用点内：

| 文件 | 形态 | 说明 |
|---|---|---|
| `EndpointPermissionPolicy.java:46-47` | 返回字符串字面量 `"hasRole('ADMIN')"` / `"hasAnyRole('ADMIN','MANAGER')"` 作为动态权限策略 | 运行时按 URL 路径构造表达式，是动态权限映射 |
| `TenderAbandonAuthorizer.java:39-53` | Java 方法调用 `hasAnyRole(authentication, "ADMIN", "MANAGER")` | 程序化鉴权，自定义 AuthorizationManager |

**决策**：
- 这 2 处**不纳入 P2 守卫的注解扫描范围**（守卫只扫 `@PreAuthorize` 注解，符合 ArchUnit `methods().areAnnotatedWith(PreAuthorize.class)` 的语义）
- 但仍需治理——在 P3 批次 7 单独评估：`EndpointPermissionPolicy` 的字符串字面量可改为返回 `hasAuthority('xxx')` 形式；`TenderAbandonAuthorizer` 的程序化调用改为基于权限键的判断
- **R6 权限键覆盖率预检对这 2 处同样适用**：迁移时确认引用的权限键已注册

**理由**：注解形态和程序化形态是不同的技术通道，ArchUnit 守卫针对注解最自然有效；程序化鉴权需人工审查，不能被自动化规则完全覆盖。这 2 处作为技术债登记，不阻塞主迁移。

### 3.5 不变式（设计约束）

- ✅ **`ROLES_WITHOUT_LEGACY_ROLE_COMPAT` 机制不动**——它是堵越权的正确防线
- ✅ **API 契约不变**——仍是 200/403，前端无需任何改动
- ✅ **无 DB schema 变更**——权限键已在 RoleProfileCatalog seed + Flyway 历史迁移中；若发现缺失，单独补 V/U 脚本
- ✅ **不新增文件/抽象**——所有改动在现有 Controller + ArchitectureTest 文件内

---

## 第四部分：实施计划

### 4.1 任务分解（42 个任务，12 个 Phase）

> **排序调整**（响应 gemini 审核）：P1 hotfix 先行，P2 守卫紧随。原方案"P2 先于 P1"被推翻——生产故障不应被守卫的 4 任务延迟，且 P1 修复后该接口自然从基线 176 减到 175，P2 守卫的 EXPECTED 常量本就该排除已修复点。

```
Phase 1  Setup (T001-T002)           — 任务分支 + 基线确认
Phase 2  US1 P1 hotfix (T003-T008)   — TaskExtendedFieldController 修复（独立 PR，立即部署止血）
Phase 3  Foundational 守卫 (T009-T012) — P2 守卫 Red→Green（基线数 175，P1 已修复后）
Phase 4  US2 P2 完整生效 (T013-T016) — 守卫错误信息 + 教训沉淀
Phase 5  US3-B1 task 收尾 (T017-T018)
Phase 6  US3-B2 casework (T019-T022)
Phase 7  US3-B3 knowledge 验证 (T023-T024)
Phase 8  US3-B4 resources (T025-T028) — 含 CO-409 混合补丁清理
Phase 9  US3-B5 project (T029-T031)
Phase 10 US3-B6 tender (T032-T034)   — 含 EXTERNAL_API
Phase 11 US3-B7 收尾 (T035-T036)     — 剩余分散模块 + 2 处程序化鉴权（EC5）
Phase 12 Polish (T037-T042)          — 守卫升级硬失败 + 文档
```

### 4.2 关键排序决策：P1 先行 hotfix，P2 紧随

**采纳 gemini 审核建议**：P1 是已发生的生产故障（bid-otherDept 09118 用户），不应被 P2 守卫的 4 任务延迟。

**修订理由**：
- P1 修复本身只涉及删一行注解 + 补测试（< 30 分钟），可独立 hotfix PR 立即部署
- P1 修复后该接口从基线排除，P2 守卫建立时 `EXPECTED_LEGACY_USE_COUNT = 175`（而非 176）
- 两者无强依赖：P1 先上线止血，P2 在另一分支并行开发
- P2 守卫完成后，P3 批次迁移在守卫覆盖下进行

**唯一需要协调的点**：P1 hotfix PR 合入后，P2 守卫的 EXPECTED 常量初始化要基于"合入后的 main"（175），不是 P1 之前的 176。这要求 P2 开发基于最新 main 分支。

### 4.3 MVP 边界

**推荐先交付**：Phase 1-2（Setup + P1 hotfix）
- 部署后：生产故障解除（bid-otherDept 用户可访问 task-extended-fields）
- 可 STOP 验证一段时间，再决定是否启动 P2 守卫 + P3 迁移

### 4.4 多 Agent 并行策略（P3 阶段）

P3 的 7 个批次模块独立，适合多 worktree 团队协作：
- P1 hotfix（Phase 2）+ P2 守卫（Phase 3-4）由一个 Agent 串行完成（守卫依赖 P1 合入后的基线）
- P3 批次 2-7 分配给不同 Agent（每个 Agent 一个模块 worktree）
- 开工前各 Agent 跑 `who-touches.sh` 确认模块无冲突
- Polish（Phase 12）由最后合并的 Agent 执行

---

## 第五部分：验证方案

### 5.1 三层验证

**P1 验证**（立即可执行）：
```bash
# 单元测试：bid-otherDept/bid-administration 应返回 200
cd backend && mvn test -Dtest='*TaskExtendedField*Test'

# 真实接口（部署后）
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Cookie: <bid-otherDept 会话>" \
  http://127.0.0.1:8080/api/task-extended-fields
# 期望：200（修复前为 403）
```

**P2 验证**：
```bash
cd backend && mvn test -Dtest='ArchitectureTest'
# 应全绿（176 处全在豁免清单内 + 数量一致性自验通过）

# 负向验证：故意新增一个 hasAnyRole 注解 → 测试应失败
```

**P3 进度指标**：
```bash
cd backend
grep -rEn '@PreAuthorize\("hasAnyRole[^"]*"|@PreAuthorize\("hasRole[^"]*"' src/main/java \
  --include="*.java" | grep -v test | wc -l
# 应从 176 逐步降至 0（精确 grep 命令，响应 gemini 审核）
```

### 5.2 回归测试矩阵（每批 PR 必须覆盖）

| 角色 | 有权限接口 | 无权限接口 |
|---|---|---|
| admin | 200 | 200（all 权限） |
| bid-projectLeader | 200（若有 permissionKey） | 403 |
| bid-TeamLeader | 200（若有 permissionKey） | 403 |
| bid-Team | 200（若有 permissionKey） | 403 |
| bid-administration | 200（仅公开读取/字典） | 403 |
| bid-otherDept | 200（仅 task 相关） | 403 |
| anonymous | 401 | 401 |

### 5.3 完成定义（Definition of Done）

- [ ] P1: TaskExtendedFieldController 方法级 @PreAuthorize 删除，回归测试覆盖 bid-otherDept 200
- [ ] P2: ArchitectureTest 新增守卫，含 EXPECTED_LEGACY_USE_COUNT 总数自验（初始 175，P1 修复后）
- [ ] P3 批次 1-7: 每批 Controller 迁移 + SecurityTest + EXPECTED 递减 + 权限键覆盖率预检
- [ ] 最终: `grep hasAnyRole` 输出为空（SecurityConfig 除外），守卫升级硬失败
- [ ] 连续部署观察：1 个月内无新增因 hasAnyRole 漏配的 403 PR

### 5.4 成功标准（可度量）

| ID | 指标 | 度量方式 |
|---|---|---|
| SC-001 | bid-otherDept/bid-administration 访问 task-extended-fields 返回 200 | 接口测试 |
| SC-002 | 新增 hasAnyRole 时 ArchitectureTest 失败 | 负向测试 |
| SC-003 | grep 使用点数从 176 降至 0 | 进度指标 |
| SC-004 | 全量迁移后 3 个月内无此类 403 PR | 对比基线（目前 20+） |
| SC-005 | 100% 迁移 PR 含 SecurityTest 证据 | PR 审查 |

---

## 第六部分：风险与对策

| 风险 | 概率 | 影响 | 对策 |
|---|---|---|---|
| 迁移过程中漏改某个 Controller 方法 | 中 | 该方法仍 403 误伤 | 规则 2 总数自验 + 每批 SecurityTest 覆盖 |
| 某模块缺权限键注册（如 resource-bar 不存在） | 中 | 无法迁移该模块 | 批次任务内显式包含"权限键预检 + 补 Flyway V/U" |
| Service 层 Policy 未就位导致 Controller 放宽后越权 | 低 | 越权访问 | 批次 5（project）前审查 ProjectAccessScopeService 兜底 |
| 多 Agent 并行时 EXPECTED 常量合并冲突 | 中 | 合并困难 | 每个 Agent 只改自己批次的迁移 + EXPECTED 递减；或串行执行 |
| 前端缓存导致 P1 修复后用户仍看不到字段 | 低 | 用户感知修复延迟 | 已在 EC（前端缓存）说明，刷新页面即可；不影响新会话 |
| **R6** `hasAuthority` 权限键未注册致全角色 403 | **高** | **严重**（比原状态更糟） | **每批次权限键覆盖率预检**：扫描迁移目标 `hasAuthority` 参数，断言每个权限键在 RoleProfileCatalog 至少被一个角色 `menuPermissions` 持有 |
| **R7** Service 层 Policy 有 bug（如 §24 canUpload/canDelete 不对称），Controller 放宽后暴露 | 中 | 中 | P3 批次 5（project）前审查所有 Policy 类，对齐 lessons §24 教训 |
| **R8** 前端路由守卫引用旧角色名 | 低 | 用户体验 | API 契约 200/403 不变，前端路由守卫独立，本特性无需改前端；但若后续前端引用权限键，需同步审查 |

---

## 第七部分：审核检查清单

请审核时重点确认以下决策：

### 核心方向（必须认可其一才能继续）

- [ ] **是否同意根因判断**：双轨制（eb58f2817 切断 legacy 但未迁移 176 处白名单）是 20+ 次 403 反复返工的根本原因？
- [ ] **是否同意目标模型**：只允许 `isAuthenticated` + `hasAuthority` 两种形态，彻底消除 `hasAnyRole`？

### 方案细节（可调整）

- [x] **P1 目标接口选择**：TaskExtendedFieldController 删除方法级注解（而非补全角色）——同意"公开读取 schema 用 isAuthenticated"的语义判断？（gemini 审核认可）
- [x] **P2 守卫机制**：双轨自验 = ArchCondition 注解扫描（规则 1）+ EXPECTED 总数断言（规则 2）。**已采纳 gemini 改进**：豁免粒度从 Controller 名单改为总数整数断言；ArchUnit API 改用真实 `ArchCondition<JavaMethod>` + `check()` 方法。
- [x] **P3 批次顺序**：task → casework → knowledge(验证) → resources → project → tender → 收尾——合理（gemini 审核认可）。
- [x] **P1/P2 排序**：**已采纳 gemini 建议**改为 P1 先行 hotfix、P2 紧随。原"P2 先于 P1"推翻。

### Edge Cases（确认处理方式）

- [x] EC1: SecurityConfig 路径级兜底保留（gemini 审核认可）
- [x] EC2: EXTERNAL_API 转 `integration.external` 权限键（gemini 提醒需前置调研 ApiKeyAuthFilter，已纳入批次 6）
- [x] EC3: 复合表达式拆解到 Service Policy（gemini 审核认可）
- [x] EC4: CO-409 混合补丁统一清理为 hasAuthority（混合补丁确认为 7 处，补充 ROLE_ 前缀反模式说明）
- [x] **EC5（新增）**：2 处程序化鉴权（EndpointPermissionPolicy + TenderAbandonAuthorizer）单独处理，不在 P2 守卫注解扫描范围

### 落地决策（待你拍板）

- [ ] **F2 分支策略**：实现阶段走 `agent-start-task.sh` 切正式分支，还是留在当前 agent/zcode-init？
- [ ] **MVP 边界**：先交付 Phase 1-2（P1 hotfix）解除故障，还是 Phase 1-4（含守卫）一次性走完？
- [ ] **是否进 implement**：审核通过后立即执行，还是先提交 spec 产物？
- [ ] **是否需要 gemini 对修正后的设计文档做第二轮审核**？

---

## 附录 A：相关文件索引

### 代码（真相源）
- `backend/src/main/java/com/xiyu/bid/entity/RoleProfileCatalog.java` — 角色与权限键定义（7 角色 + 18+ 权限键常量）
- `backend/src/main/java/com/xiyu/bid/auth/UserDetailsServiceImpl.java` — authority 颁发逻辑（含 `ROLES_WITHOUT_LEGACY_ROLE_COMPAT`）
- `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java` — SecurityFilterChain（路径级兜底）
- `backend/src/main/java/com/xiyu/bid/task/controller/TaskExtendedFieldController.java` — P1 目标接口
- `backend/src/test/java/com/xiyu/bid/ArchitectureTest.java` — P2 守卫落地点（当前 27 条 `@ArchTest` 规则，新守卫将是第 28、29 条）

### Spec Kit 产物（本次起草）
- `.specify/memory/constitution.md` — 宪法 v1.3.0（新增原则 VI）
- `specs/024-preauthorize-unification/spec.md` — 功能规格（3 user stories + 7 FR + 5 SC）
- `specs/024-preauthorize-unification/plan.md` — 实现计划（Constitution Check 全绿）
- `specs/024-preauthorize-unification/research.md` — 6 个决策记录
- `specs/024-preauthorize-unification/tasks.md` — 42 任务分解
- `specs/024-preauthorize-unification/quickstart.md` — 验证命令清单

### 历史教训（同模式）
- `docs/lessons/lessons-learned.md` §24 — Policy 权限矩阵对称设计
- `docs/lessons/lessons-learned.md` §28 — CO-400/CO-415 hasAnyRole 陷阱
- `docs/lessons/lessons-learned.md` §26 — 前端导航权限 ≠ 后端 API 权限

### 关键 commit（迁移范式参考）
- `eb58f2817` — 切断 legacy 兼容（双轨制起点）
- `b64592304` — CO-394-A 标准迁移范式（品牌授权 Controller）
- `75094af5a` — CO-466 最新修复案例（含服务器日志铁证写法）

---

## 附录 B：176 处使用点完整清单（按表达式模式分组）

> **数据基线已用精确 grep 复核**（2026-07-02，响应 gemini 审核）：
> `grep -rEn '@PreAuthorize\("hasAnyRole[^"]*"|@PreAuthorize\("hasRole[^"]*"' backend/src/main/java --include="*.java" | grep -v "/test/"` = **176**
> 较初版文档的 177 减少 1（排除 `RoleProfileCatalog.java:254` 的 Javadoc 注释引用）。

| 表达式 | 数量 | 说明 |
|---|---|---|
| `hasAnyRole('ADMIN', 'MANAGER')` | **105** | 占 60%，误伤 bid-Team/bid-otherDept/bid-administration 的元凶 |
| `hasRole('ADMIN')` | 25 | 管理员专属（部分可保留） |
| `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_TEAM')` | 10 | 手抄角色列表 |
| `hasAnyRole('ADMIN', 'MANAGER') or hasAuthority('ROLE_BID_TEAM')` | 7 | CO-409 混合补丁（EC4） |
| `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN')` | 6 | 手抄 |
| `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM', 'SALES')` | 4 | 含幽灵项 SALES |
| `hasAnyRole('ADMIN', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` | 4 | 手抄 |
| `hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'BID_TEAM')` | 3 | 手抄 |
| `hasAnyRole('ADMIN','BID_PROJECTLEADER')` | 4 | 手抄 |
| `hasAnyRole('ADMIN', 'MANAGER', 'BID_TEAMLEADER', 'BIDADMIN', 'BID_PROJECTLEADER', 'SALES')` | 2 | 手抄 |
| `hasRole('EXTERNAL_API')` | 1 | 外部 API Key（EC2） |
| `hasRole('ADMIN') or (hasRole('MANAGER') and !#type...)` | 1 | 复合表达式（EC3） |
| 其他变体 | 4 | — |
| **注解使用点合计** | **176** | |

**另：2 处程序化鉴权（非 `@PreAuthorize` 注解形态，单独评估）**：
- `EndpointPermissionPolicy.java:46-47` — 返回字符串字面量 `"hasRole('ADMIN')"` 作为动态权限策略（运行时构造）
- `TenderAbandonAuthorizer.java:39-53` — Java 方法调用 `hasAnyRole(authentication, "ADMIN", "MANAGER")`（程序化鉴权）

**注解使用点涉及文件**：57 个 Controller + `RoleProfileCatalog`（注释）+ `PlatformAccountViewerPolicy`（注释）= 59 个文件

**注**：白名单里的 `'SALES'` 是**幽灵项**——销售实际 roleCode 是 `bid-projectLeader`（authority `ROLE_BID_PROJECTLEADER`，已列），系统里不存在 `ROLE_SALES`。这是早期角色改名后的死代码，迁移时自然消失。

**编译期内联说明**：`BatchOperationController`、`TenderUploadController`、`ExportController`、`BidResultQueryController` 4 个 Controller 把 `hasAnyRole('ADMIN','MANAGER')` 提取为 `static final String ADMIN_MANAGER_EXPR` 常量再在 `@PreAuthorize` 中引用。Java 编译器会内联 `static final String`，ArchUnit 读到的是字面量，守卫能正确捕获。P2 守卫的 Red→Green 测试已显式包含这 4 个 Controller 作为验证用例。

---

*本设计文档完成于 2026-07-02。请在通读后于第七部分「审核检查清单」逐项确认或指出需调整之处。*
