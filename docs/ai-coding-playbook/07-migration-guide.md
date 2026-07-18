# 07｜迁移指南：把本项目的经验搬到下一个项目

> [06 章](06-next-project-checklist.md) 回答"下个项目该做什么"，本章回答"**从哪里搬、怎么改**"。
> 原则：第 1 层资产拷贝适配，第 2 层模板填空，第 3 层纪律照抄，第 4 层知识阅读——不要只带文档走。

## 复用四层模型

| 层 | 内容 | 适配成本 | 不搬的代价 |
|---|---|---|---|
| 1 代码资产 | 协作脚本 / 门禁骨架 / 透传骨架 / ArchUnit / PR 模板 | 半天 ~ 两天 | 重新踩一遍已交过学费的坑 |
| 2 模板 | AGENTS.md、PR 模板、RCA/lessons 模板 | ~1 小时 | PR 元数据纪律从第一天就失守 |
| 3 纪律条款 | 写进新项目 AGENTS.md 的底线 | 零 | 靠自觉 = 没有 |
| 4 知识读物 | 本手册六章 | 团队读一次 | 判断时没有模式库 |

## Day-0：拷贝清单（第 1 层资产）

| 资产 | 本仓库位置 | 适配点 | 为什么（证据章） |
|---|---|---|---|
| 任务开场脚本 | `scripts/agent-start-task.sh` | 分支前缀 `agent/<name>/`、worktree 根路径 | [02](02-multi-agent-collaboration.md) §2.3 |
| 任务收尾脚本 | `scripts/agent-finish-task.sh` | 锚点分支名、锁清理范围 | 02 §2.3（janitor 内建） |
| 谁在动这个文件 | `scripts/who-touches.sh` | 基本原样可用 | 02 §2.2（git 事实协调） |
| 文件锁三件套 | `scripts/manage-agent-locks.mjs`、`check-agent-locks.mjs`、`cleanup-stale-locks.sh` | 锁目录 `.agent-locks/`、锁生命周期 | 02 §2.4（per-task 锁 vs 单文件锁） |
| pre-push 门禁骨架 | `scripts/pre-push-gate.sh` | **裁剪到 5 道最小门，别整体搬**（570 行是事故长出来的）；默认值必须 fail-closed | [04](04-quality-gates.md) §3、§6 |
| pre-commit 骨架 | `scripts/pre-commit.sh` | 同上，保留编译+冲突标记扫描即可 | 04 §6（!2012 的教训） |
| Flyway 防撞号七件套 | `scripts/check-flyway-*.sh` + `assign-flyway-version.sh` | 仅栈用 Flyway 才搬；换迁移工具则只搬"集中取号 + 已发布不可变 + 撞号检测"三原则 | 04 §2（第 4 周六道门） |
| 透传后端骨架 | `backend/src/main/java/com/xiyu/bid/exception/`（BusinessException / GlobalExceptionHandler / ExceptionMessageSanitizer / ExceptionResponseStrategy / ApiResponse）+ `config/Trace*.java` | 改包名、删业务异常子类；**Sanitizer 五道检查原样可用** | [03](03-passthrough-pattern.md) §3.2、§3.4 |
| 透传前端骨架 | `src/api/client.js`、`src/utils/download.js` | 基本原样可用；记得 CORS 白名单同步自定义 header | 03 §3.6 |
| ArchUnit 规则集 | `backend/src/test/**/ArchitectureTest.java`（RULE 1-19）+ `FPJavaArchitectureTest` | 包结构映射；行数棘轮第一天装（零存量） | 04 §5（RULE 13 反例） |
| PR 模板 | `.github/pull_request_template.md`（Scope Check / Architecture Self-Check） | 直接可用；加一行"根因/证据必填" | [01](01-bug-root-causes.md) §3（PR 元数据纪律） |

> 技术栈不同（非 Vue/Spring）时：脚本和骨架不搬，但每行"为什么"列的原则全部成立——按原则在新栈重写等价物。

## 第一周：模板与纪律落地（第 2、3 层）

**模板**：

- 新项目 `AGENTS.md` 照本项目样式写成"导航地图"：底线清单 + "按任务找信息"表 + 协作暗号，别把规范塞一个文件。
- RCA 模板照 `docs/lessons/root-cause-analysis-*.md`：现象 → 证据（日志/DB 铁证）→ 根因 → 防线。修 bug 前先搜同类问题。
- revert PR 模板（源自 !2067/!2095 的规范化实践）：撤销什么 / 保留什么 / 疑点链路 / 守护测试。

**纪律条款（可直接照抄进新项目 AGENTS.md 底线）**：

1. 真实 API 唯一源，禁止 Mock 兜底——假数据会污染生产库（!1350）。
2. 恢复被回退代码一律 cherry-pick，禁止手工重写（保 author、保 blame）。
3. 修 bug 先取证（日志 / traceId / DB），禁止无铁证改代码。
4. 同根因二次复发 = 强制根因分析 + 强制抽象或强制 lint 规则。
5. 约束 AI 的设施（门禁/脚本/hook）：默认值 fail-closed + 装门当天故意违规验证一次。
6. 事故闭环：事故 → RCA → 门禁 → 防复发验证；门禁不预言式设计，但事故后 24h 内装门。
7. 权限/枚举/外部契约单源化 + 机器可校验（ArchUnit / 契约测试），安全默认拒绝。
8. 透传纪律：消息从产生点原样流到 UI，中间层只做安全判定，禁止吞掉/改写/编造/兜底。

## 持续运转

- **事故→门禁闭环**：每道新门回写门禁清单（名称/防什么/催生事故 PR/成本）；门禁超过约 10 道后加元门禁（自检脚本、逃生阀留痕、hook 无回环）。
- **lessons 沉淀**：每个非平凡 bug 修完留一篇 RCA；每月把 RCA 聚类，复发模式升级为门禁或 ArchUnit 规则——本手册就是这样从 2059 个 PR 里长出来的。
- **修复预算**：排期按"fix 占六成"预留；主工作区设 integrator 角色专司集成发布。

## 明确别搬的

- **完整的 570 行 pre-push-gate.sh**：那是本项目 5 周事故史的形状，不是你的。按 04 章 §6 装 5 道最小门，其余交给你的事故驱动生长。
- **业务耦合资产**：CRM/OSS/蓝图/权限矩阵等集成代码与配置——教训（契约机器化、权限单源）带走，实现别带。
- **xiyu 特定 skills 与部署脚本**（`.agents/skills/xiyu-*`、`scripts/*deploy*`）：与本项目基础设施强耦合。
- **"先设计完备门禁体系"的想法本身**：04 章的核心结论就是没有一道门是提前预言出来的。

## 搬运顺序一页纸

1. Day-0 上午：拷协作脚本族 + 裁过的 5 道门禁 + PR 模板 → 故意违规验证每道门
2. Day-0 下午：拷透传骨架 + ArchUnit 核心子集 → 跑通一个"业务异常消息到前端弹窗"的集成测试
3. Day-1：写 AGENTS.md（含上面 8 条纪律）+ Flyway 取号规则（如适用）
4. 第一周：RCA 模板就位 + 权限/枚举单源化 + 统一上传下载封装
5. 之后：什么都不用预先做——事故会来告诉你下一道门装什么
