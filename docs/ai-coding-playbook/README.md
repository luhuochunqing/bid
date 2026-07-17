# AI Coding 经验手册（ai-coding-playbook）

> 本项目（西域数智化投标管理平台）是我们第一个完全基于 AI Coding 的交付项目。
> 本手册从 **2059 个已合并 PR** 的全量数据中提炼可复用经验，供下一个 AI Coding 项目直接取用。

## 核心数字

| 指标 | 数值 | 口径 |
|---|---|---|
| 已合并 PR | 2059（base=main 2045） | Gitee 全量 |
| 开发周期 | 约 5 周（2026-06-08 ~ 2026-07-13） | git 可见历史 |
| fix+hotfix 占比 | **59%**（1223/2059） | Gitee 全量 |
| 并行 AI Agent | 约 10 个（trae/claude/codex/zcode/mimo/kimi/qoder/cursor/gemini 等） | merge 分支名 |
| revert/回退类 | 36（约 1.7%），其中真事故性约 10 起（0.5%） | 标题关键词 |
| 中位改动 | 3 个文件/PR | git diff |
| 关联 Linear issue | 582 个 merge commit 带 CO-XXX | git |

一句话定性：**功能开发只占四成，修复占六成——AI 并行开发的产能规划必须把"修复预算"当成一等公民；而绝大多数 bug 的共同分母是"关键事实没有机器可校验的单一来源"。**

## 章节索引

| 章 | 主题 | 你会得到什么 |
|---|---|---|
| [01](01-bug-root-causes.md) | Bug 根因模式全景 | 13 个全局根因模式 + 15 个经典案例 + Top 5 速查表 |
| [02](02-multi-agent-collaboration.md) | 多 Agent 协作体系 | 协作模式全景 + 9 类并行故障 + Day-1 配置单 |
| [03](03-passthrough-pattern.md) | 透传范式 | 前端到后端"真实信号原样透传"的完整范式 + Day-1 组件清单 |
| [04](04-quality-gates.md) | 门禁体系进化 | 17 道门禁盘点 + 假绿事故 + "第一天只装 5 道门" |
| [05](05-rollback-discipline.md) | 回退与恢复纪律 | 36 次回退的 6 类聚类 + 恢复三级进化 + 5 条门禁级纪律 |
| [06](06-next-project-checklist.md) | 下个项目行动清单 | 跨章汇总的分阶段 Checklist（先看这章也行） |

## 全书十大核心结论

1. **修复占六成是结构性成本，不是某个 agent 不行。** 8 个非集成者 agent 的 fix 占比一致落在 59%~66%，做产能计划时先留修复预算，并专设 integrator（主工作区）角色。
2. **Bug 的共同分母是"事实无单源"。** 权限事实分散五处、前后端契约靠口头、外部系统契约靠猜——AI 只见局部就动手，改一处漏其余。契约/权限/枚举必须机器可校验地单源化。
3. **首轮修症状不修根因，是最贵的元模式。** CO-469 修八轮、自我审批修六轮。纪律：同根因二次复发 = 强制根因分析；先拿日志/DB 铁证再改代码。
4. **透传范式是对抗 AI 本能的核心范式。** AI 爱吞异常、爱 Mock 兜底、爱硬编码状态码、爱泄露内部错误——透传（真实信号原样流过所有层，中间层只做安全判定）每条反模式都有生产事故背书。
5. **门禁的最大敌人是自己的假绿。** 一个 `:-0` 默认值让 14 道门禁建成一周从未生效。每道门必须有阴性对照测试，默认值 fail-closed，装门当天故意违规验证一次。
6. **门禁几乎全是事故税，没有一道是提前预言的。** 正确姿势是"事故→RCA→门禁→防复发验证"闭环，第一天只装 5 道最小门。
7. **并行合并病是特有病种。** Flyway 撞号、Bean 冲突、rebase 丢变更、同步覆盖他人代码——冲突成本要左移（开工强制 rebase + push 前门禁），全局序号要集中仲裁。
8. **摩擦成本定律：门禁摩擦过高会被绕过而非遵守。** 单文件锁每次 rebase 必撞，全员习惯性无视告警；改 per-task 锁后才恢复约束力。
9. **协作资源必须配 janitor。** 锁/分支/worktree 泄漏是常态（约 180 个清锁 commit），收尾脚本内建清理，别指望 agent 自觉。
10. **回退恢复一律 cherry-pick，禁止手工重写。** git blame 可追溯性是团队资产；revert PR 规范化（"撤销什么/保留什么" + 守护测试）把回退升级为"回退 + 防再犯"。

## 挖掘方法与数据出处

- 数据管线：git 全量 merge commit 统计（`!PR号` 解析、类型/模块/agent/改动区域分类）→ Gitee API 拉取 2059 个 PR 正文 → 剥除模板 → 1198 个有实质内容的 fix PR 分 6 块做根因聚类 → 4 个专题深挖（revert、多 Agent 协作、透传、门禁）。
- 中间产物（findings JSON、统计底稿、挖掘脚本）本次挖掘时保存在本地 `.runtime/pr-mining/`（该目录已被 .gitignore，不随仓库分发）；需要复查时可用同一管线重跑：git 历史 + Gitee API（`GET /repos/allinai888/bid/pulls?state=merged`）即可复现全部输入数据。
- 手册中所有 PR 号（`!XXXX`）、commit hash、计数均出自上述分析，未做外推；计数为规模指示器（单 PR 多根因时归主因）。
- 相关既有文档：`docs/lessons/`（逐案根因分析）、`docs/references/rollback-recovery-playbook.md`、`RELIABILITY.md`（门禁体系）、`ARCHITECTURE.md`（FP-Java Contract）。
