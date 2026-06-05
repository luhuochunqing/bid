# Quality Gate Rollout Plan

一旦我所属的文件夹有所变化，请更新我。

## 目标
- 把质量门禁从“配置存在但默认关闭”推进到“对核心包和正在整治的模块默认可执行”。
- 先收紧低噪音、高信号的检查，再逐步扩大范围。
- 保持 ratchet 原则：已经变绿的范围不再回退。

## 当前状态
- `quality-audit` 已可执行：会运行 Checkstyle、PMD、SpotBugs，但不会阻断构建。
- `quality-strict` 已可执行：当前由 Checkstyle 与 SpotBugs 在默认范围内阻断构建。
- PMD 已收口为提醒层：继续为复杂度和坏味道提供盘点信号，但默认不作为 strict 失败条件。
- `QUALITY_GATE_GUIDE.md` 已定义运行方式、扩圈准入标准和 CI 使用原则。
- 当前默认范围：
  - `marketinsight.core`
  - `admin.settings.core`
  - `task.core`
  - `bidresult.core`
  - `projectworkflow`

## 问题分桶
- `A` 类：机械修复
  - 定义：低争议、低成本、可批量处理的问题
  - 典型示例：
    - 星号导入
    - 格式与命名问题
    - 显而易见的 `null` 归一化
    - 简单 PMD / Checkstyle 告警
  - 当前状态：
    - 默认 strict 范围内首轮已基本清零
    - 这类问题不再作为独立治理项目推进，而作为模块进入 strict 前的清场动作
- `B` 类：设计修复
  - 定义：涉及职责划分、边界表达、异常语义和服务体量的结构性问题
  - 典型示例：
    - 超大 service
    - 依赖过多的 orchestration 类
    - 错误边界不清
    - 用异常承载业务流
    - 业务决策与 I/O 编排混杂
  - 当前状态：
    - 是下一阶段的主要治理重点
    - 重点观察对象以 `MaintainabilityArchitectureTest` 当前的超限/豁免服务为主
- `C` 类：测试缺口
  - 定义：自动化验证不足，导致 strict 范围难以扩大或质量回退无法及时发现
  - 典型示例：
    - 纯逻辑没有单测
    - 模块缺少最小回归测试入口
    - 受保护范围缺少 coverage ratchet
  - 当前状态：
    - 架构门禁和核心纯逻辑单测已起步
    - 但覆盖率 ratchet 仍未正式落地，是治理闭环中的剩余缺口

## 第一阶段：机械问题清零
- 状态：已完成
- 目标：把 Checkstyle 和低争议静态问题清零。
- 已完成：
  - `projectworkflow` 下的 JPA 星号导入已修复。
  - `bidresult.core.FunctionalResult` 的 PMD 命名冲突已修复。
  - 默认 strict 范围内首轮 `A` 类问题已完成清场。
- 退出条件：
  - `mvn -Pjava-quality,java-quality-spotbugs,quality-audit checkstyle:check pmd:check spotbugs:check`
    在默认范围内只剩高价值问题。

## 第二阶段：核心值对象防御性复制
- 状态：已完成
- 已完成：
  - `quality-strict` 下 26 个 `EI_EXPOSE_REP / EI_EXPOSE_REP2` 已清零
- 根因：
  - 多个 `core` record/值对象直接持有并返回可变 `List`/`Map`
- 处理策略：
  - 在 canonical constructor 中做 `List.copyOf(...)` / `Map.copyOf(...)`
  - 对外 getter 继续返回不可变集合
- 首批处理文件：
  - `admin/settings/core/CoreAccessProfile`
  - `admin/settings/core/DepartmentGraph`
  - `admin/settings/core/DepartmentScopeRule`
  - `admin/settings/core/RoleAccessRule`
  - `admin/settings/core/UserScopeRule`
  - `bidresult/core/AwardRegistrationValidation`
  - `task/core/BidSubmissionPolicy`
  - `task/core/DeliverableAssociationPolicy`

## 第三阶段：默认 strict 绿灯
- 状态：已完成
- 目标：
  - 默认范围下 Checkstyle / SpotBugs / 架构门禁全绿
- 验证命令：
  - `mvn -Pjava-quality,java-quality-spotbugs,quality-strict -DforkCount=0 -Dtest=FPJavaArchitectureTest,ScoreDraftPolicyTest,ProjectWorkflowServiceTest test checkstyle:check pmd:check spotbugs:check`

## 第四阶段：逐步扩圈
- 状态：进行中
- 顺序建议：
  1. `projectworkflow` 扩到相邻纯逻辑模块
  2. `bidresult` / `task` 的非 core 但已稳定模块
  3. 其余受保护模块
- 原则：
  - 先 audit
  - 先按 `A/B/C` 和 `L1/L2/L3` 双维度分类
  - 先清掉 Checkstyle / SpotBugs 阻断项，再 strict
  - 扩圈时必须同步更新 `quality.includes`、`quality.onlyAnalyze` 与计划文档
- 当前重点：
  - `B` 类：优先收缩超大 service、减少豁免、明确错误边界
  - `C` 类：为候选模块补最小回归测试入口

## 第五阶段：覆盖率 ratchet
- 状态：待处理
- 当前：
  - `jacoco.minimum.coveredratio = 0.10`
- 建议：
  - 先对默认 strict 范围建立更高阈值
  - 再考虑提高全仓阈值
- 不建议：
  - 在全仓测试基线不稳定前，直接抬升全局覆盖率门槛

## 门禁语义基线
- Checkstyle：机械阻断（import、IllegalCatch 等）。Javadoc 缺失自 2026-06 起作为 L3 历史债由 suppressions.xml blanket 压制（ratchet 基线 0，通过显式宽报告命令激活；修直后未用 pom 绑定以避免弯路）。未来 carve-out 受保护子树时才要求真实 Javadoc（见 code-formatting.md + implementation-notes “修直过程” + CheckstyleJavadocDebtRatchetTest）。
- PMD：提醒与分桶输入，不阻断
- SpotBugs：重点包真实风险阻断
- SpotBugs suppressions：统一由 `config/spotbugs/exclude.xml` 管理，默认应为空或接近为空
