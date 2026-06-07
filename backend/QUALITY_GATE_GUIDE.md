# Quality Gate Governance Guide

一旦我所属的文件夹有所变化，请更新我。

## 目的
- 让质量门禁从“能跑”变成“团队知道何时跑、为什么失败、何时扩圈”。
- 固化 `quality-audit` 与 `quality-strict` 的职责，避免每次靠口头约定。

## 模式定义
- `quality-audit`
  - 用途：盘点问题、形成治理清单
  - 行为：运行 Checkstyle、PMD、SpotBugs，但不阻断构建
  - 适用场景：
    - 新模块准备纳入受保护范围前
    - 普通 PR 的例行质量盘点
    - 扩圈前的现状摸底
- `quality-strict`
  - 用途：对受保护范围进行真实阻断
  - 行为：在当前默认范围内，Checkstyle 与 SpotBugs 违规会失败；PMD 继续保留为提醒层
  - 适用场景：
    - 修改默认受保护范围内代码
    - 扩圈 PR
    - 调整质量门禁配置本身

## 工具职责
- Checkstyle
  - 角色：机械规范门禁
  - 关注点：导入、命名、基础格式与一致性
  - 默认策略：在 strict 中阻断
- PMD
  - 角色：复杂度与坏味道提醒
  - 关注点：大方法、可读性风险、命令式坏味道、潜在设计压力
  - 默认策略：audit 和 strict 都输出问题，但不阻断构建
  - 使用方式：优先作为 `A/B` 类分桶输入，而不是直接当作扩圈成败开关
- SpotBugs
  - 角色：真实风险门禁
  - 关注点：空指针、集合暴露、错误 API 使用、可见性和状态安全问题
  - 默认策略：在 strict 中阻断受保护范围
  - 范围控制：只分析 `quality.onlyAnalyze` 指定的重点包
  - 例外管理：统一收口到 `config/spotbugs/exclude.xml`，且必须保持极小规模

## 当前默认受保护范围
- `marketinsight.core`
- `admin.settings.core`
- `task.core`
- `bidresult.core`
- `projectworkflow`

范围定义来源只有两处：
- `pom.xml` 中的 `quality.includes`
- `pom.xml` 中的 `quality.onlyAnalyze`

禁止在其他脚本、工作流或临时命令中维护第二份范围清单。

## 问题分级
- `L1` 机械问题
  - 示例：导入、命名、显而易见的简单坏味道
  - 要求：进入 strict 前必须清零
- `L2` 核心结构问题
  - 示例：集合暴露、不可变性缺口、边界泄漏
  - 要求：在受保护范围内必须清零
- `L3` 历史债
  - 示例：大范围旧代码复杂度、广泛分布的长期坏味道、**全仓 Javadoc 缺失（37k+ 历史债，2026-06 已 blanket 压制 + ratchet 保护）**
  - 要求：先在 audit 中收集，进入专项治理，不阻断未纳入 strict 的范围
  - Javadoc 特殊处理：见 `config/checkstyle/suppressions.xml` 注释 + `docs/code-formatting.md` "Javadoc 规则现状" 小节。ratchet 通过显式宽报告命令激活（修直后未用 pom 绑定，以避免生命周期耦合）。未来通过 carve-out 特定受保护子树 + 真实 Javadoc 补齐来收紧（A 类机械）。详见 implementation-notes.md “修直过程”。

## 问题分桶
- `A` 类：机械修复
  - 适用范围：低成本、低争议、适合批量修复的问题
  - 常见来源：Checkstyle、低风险 PMD、简单 SpotBugs
  - 示例：星号导入、格式问题、命名问题、显而易见的 `null` 处理
  - 治理要求：作为模块进入 strict 前的清场动作，不单独长期挂账
- `B` 类：设计修复
  - 适用范围：影响职责边界、依赖结构、异常语义和可维护性的结构问题
  - 常见来源：架构门禁、复杂度检查、服务体量检查、代码审查
  - 示例：超大 service、依赖过多、异常承载业务流、业务决策与 I/O 混杂
  - 治理要求：进入专项治理，是默认 strict 范围之外的主要扩圈阻力
- `C` 类：测试缺口
  - 适用范围：自动化验证不足导致的扩圈风险
  - 常见来源：JaCoCo、模块回归入口缺失、纯逻辑缺少单测
  - 示例：纯函数无单测、缺最小回归测试入口、受保护范围没有 coverage ratchet
  - 治理要求：候选模块扩圈前至少补齐最小回归测试入口；覆盖率 ratchet 作为单独阶段推进

`A/B/C` 与 `L1/L2/L3` 不是替代关系：
- `A/B/C` 回答“这是什么类型的治理工作”
- `L1/L2/L3` 回答“它在当前阶段是否应该阻断”

## 模块扩圈准入标准
一个模块进入 `quality-strict` 前，必须同时满足：
- Checkstyle 0 阻断问题
- SpotBugs 0 阻断问题
- 现有架构门禁通过
- 至少有最小回归测试入口

补充说明：
- PMD 不再作为“0 问题才能进 strict”的硬门槛，而是要求完成分桶、确认没有未接受的高风险坏味道，并形成专项治理结论。
- 对于明显属于 `B` 类的大 service / 错误边界问题，可以带计划进入 backlog，但不能伪装成 SpotBugs 豁免。

## 扩圈操作步骤
1. 用 `quality-audit` 在候选模块范围内跑质量盘点
2. 先按 `A/B/C` 分桶，再按 `L1/L2/L3` 分类
3. 清理 `A` 类问题与需要阻断的 `L1/L2`
4. 为 `B` 类建立专项治理项，并明确不纳入本次扩圈的剩余债务
5. 补最小回归测试入口，清理 `C` 类缺口
6. 同时更新：
   - `pom.xml` 的 `quality.includes`
   - `pom.xml` 的 `quality.onlyAnalyze`
   - 如有必要，更新 `config/spotbugs/exclude.xml`
   - `QUALITY_GATE_PLAN.md`
7. 跑 strict 验证通过后再合入

## 覆盖率策略
- 当前全仓 JaCoCo 阈值仍维持保守值
- 下一阶段采用“受保护范围 ratchet”策略，而不是直接提高全仓阈值
- 任何覆盖率阈值提升都必须附带：
  - 适用范围
  - 当前基线
  - 新阈值
  - 回归命令

## 运行命令
```bash
# 审计模式
mvn -Pjava-quality,java-quality-spotbugs,quality-audit checkstyle:check pmd:check spotbugs:check

# 严格模式
mvn -Pjava-quality,java-quality-spotbugs,quality-strict -DforkCount=0 test checkstyle:check pmd:check spotbugs:check
```

## 踩坑提示（2026-06 新增）

### ⚠️ 直接 `mvn checkstyle:check` 不走项目配置

**问题**：直接调用 `mvn checkstyle:check`（不带 `-P` profile）会使用 maven-checkstyle-plugin 的**默认值**：
- `configLocation = sun_checks.xml`（默认规则集，不是项目的 `config/checkstyle/checkstyle.xml`）
- `suppressionsLocation = ${checkstyle.suppressions.location}`（默认空）
- 全仓扫描，约 39k 违规

**症状**：在终端看到 39329 个 Checkstyle 错误，让人误以为项目的 `config/checkstyle/suppressions.xml` 没生效、配置 bug 之类的。

**真实情况**：项目的自定义 checkstyle 配置**只在 `<profile><id>java-quality</id>` 内部**，未激活 profile 时所有配置都不生效。

**正确做法**：永远用 `-Pjava-quality` 激活：
```bash
mvn -Pjava-quality -Dquality.skip=false \
    -Dquality.includes="<pattern>" \
    -Dquality.failOnViolation=<true|false> \
    checkstyle:check
```

也可以按需附加 `,quality-audit`（审计，不阻断）或 `,quality-strict`（严格，违规失败）。

**调试时如果想看实际加载的 configLocation**：
```bash
mvn -e -X -Pjava-quality checkstyle:check 2>&1 | grep configLocation
# 应输出 (f) configLocation = config/checkstyle/checkstyle.xml
# 如果输出 (f) configLocation = sun_checks.xml 则说明 profile 未激活
```

## CI 约定
- 普通 PR：默认运行 `quality-audit`
- 修改受保护范围或质量门禁配置的 PR：额外运行 `quality-strict`
- 扩圈 PR：必须同时更新范围配置、计划文档和验证结果
- 若新增 SpotBugs 过滤项，PR 描述必须说明原因、影响范围和后续移除条件
