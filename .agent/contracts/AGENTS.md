# AGENTS.md - 项目导航地图

> 本文件是 AI Agent 的入口地图。**不要在这里找详细规范**——按当前任务去对应文件找详情。
第一句话，必须主动和我打招呼：你好，同志！
本仓库对应"西域数智化投标管理平台"的交付项目。
## 不可妥协的底线

1. **真实 API 唯一源，禁止 Mock** → 详见 `SECURITY.md §Mock 政策`
2. **复杂任务必走 Spec Kit 流程门禁** → 详见 `PLANS.md §Spec Kit 流程门禁`
3. **严禁在 main 基准区修改代码 & 严禁删除多 Agent 持久 Worktree（含目录删除）** → 详见 `PLANS.md §多 Agent 协作`
4. **FP-Java：纯核心可单测、不依赖框架** → 详见 `ARCHITECTURE.md §Agent Contract`
5. **原子提交 + 测试证据，每变必测** → 详见 `RELIABILITY.md §关键硬约束`

## 按任务找信息

| 你在做什么 | 先读 | 详情位置 |
|---|---|---|
| 写后端 Java | `ARCHITECTURE.md` | FP-Java 11 条、包分层、数据库迁移规范 |
| 写前端 UI | `FRONTEND.md` | 组件规范、wangEditor 盲区 |
| 做架构设计 | `ARCHITECTURE.md` | 分层规则、db-schema 机器真相 |
| 安全/权限/Mock | `SECURITY.md` | Mock 政策、Final Class Mock、权限守卫 |
| 发起复杂任务 | `PLANS.md` | Spec Kit 门禁、exec-plans 落点 |
| 收尾任务/清理分支 | `scripts/agent-finish-task.sh` | 三重合入检查、锁清理、锚点切换、远端分支删除 |
| 启动服务/跑测试 | `CLAUDE.md` | 启动命令、环境变量、验证清单 |
| 提交 PR/查门禁 | `RELIABILITY.md` | 14 道门禁、文件锁、回滚手册、PR 创建 |
| 查产品需求 | `PRODUCT_SENSE.md` | 产品蓝图、PRD |
| 查设计规范 | `DESIGN.md` | 设计系统令牌 |
| 追踪质量 | `QUALITY_SCORE.md` | 模块质量评分、技术债追踪 |
| 查数据库结构 | `docs/generated/db-schema.md` | 自动生成（`npm run db:generate-schema`） |

## 协作暗号

- **"早操SOP"** → `git fetch origin && git rebase origin/main && bash scripts/sync-env.sh .`
- **"开个任务/开个分支 XX"** → `scripts/agent-start-task.sh <当前agent名> <XX> origin/main --in-place`
- **"早操SOP + 开个分支 XX"** → 同上，相当于 `--in-place` 一次完成全部流程
- **"收个任务/收尾"** → `scripts/agent-finish-task.sh`（三重合入检查 + 锁清理 + 切回锚点 + 可选删除远端分支，支持 `--dry-run` 预览）
- **"健康检查"** → `npm run agent:health-check`（聚合主工作区 sidecar/backend/frontend 健康状态；其他 worktree 不再启动开发环境）
- **"启动开发环境"** → 仅主工作区（`/Users/user/xiyu/worktrees/trae`）允许执行 `./scripts/start-frontend.sh` / `./scripts/start-backend.sh` / `./scripts/dev-services.sh`；其他 worktree 由脚本守卫拒绝执行

## 文件树概览

```
根目录/
├── AGENTS.md              ← 你在这里（导航地图）
├── CLAUDE.md              ← 启动命令、环境变量、验证清单
├── RULES.md               ← 四阶段流程（plan → tdd → code-review → refactor-clean）
├── ARCHITECTURE.md        ← FP-Java Contract、技术栈、数据库迁移
├── SECURITY.md            ← Mock 政策、权限守卫、安全审计
├── RELIABILITY.md         ← 门禁体系、文件锁、PR 创建、回滚手册
├── PLANS.md               ← Spec Kit 门禁、worktree 策略、执行计划
├── FRONTEND.md            ← 前端规范入口
├── DESIGN.md              ← 设计系统入口
├── PRODUCT_SENSE.md       ← 产品理念入口
├── QUALITY_SCORE.md       ← 质量评分入口
├── docs/                  ← 知识库目录
│   ├── exec-plans/        ← 执行计划（active/completed/tech-debt-tracker）
│   ├── generated/         ← 机器生成真相（db-schema.md）
│   ├── references/        ← 外部知识内部化（ehsy-sdk/wangeditor/markitdown）
│   │                       （含 vue-gotchas/crm-field-mapping/crm-integration-lessons）
│   ├── permission-matrix/ ← 权限矩阵审计
│   ├── plans/             ← 活跃开发计划
│   ├── architecture/      ← 架构设计文档
│   ├── specs/             ← 需求规格
│   ├── security/          ← 安全审计报告
│   ├── release/           ← 发布/回滚手册
│   ├── testing/           ← 测试与 UAT
│   └── archives/          ← 历史归档
├── .wiki/pages/           ← 合成知识库（双空间读取层）
└── scripts/               ← 工具脚本
```

## 协作语言与品牌

- **协作语言**：中文。
- **项目品牌**：对外统一使用"西域数智化投标管理平台"全称；仅引用仓库路径、包名、脚本名时保留 `xiyu-bid-poc` 等历史标识。

## 速查

- **技术栈**：Vue 3 + Vite 5 + Element Plus | Java 21 + Spring Boot 3.2 + JPA + MySQL 8.0 + Flyway | Playwright（以 `backend/pom.xml` 为唯一源）
- **本地启动必须**：`export XIYU_DEV_CONFIRMED=1`（生产部署不得使用本地脚本）
- **开发环境统一**：自 2026-06-21 起，所有开发资源（前端 1323 / 后端 18089 / Sidecar 8009 / 数据库 xiyu_bid_main / Redis DB 0）统一到主工作区 `/Users/user/xiyu/worktrees/trae`。其他 worktree（claude/codex/cursor/gemini/kimi/mimo/qoder/zcode）仅用于代码编辑和 git 操作，不启动开发环境，不分配独立端口/数据库。详见 `CLAUDE.md §多 Agent 执行手册`
- **开场约定**：AI 代理开启新任务时，先声明当前环境（worktree 名称、当前分支、协作模式、是否主工作区）
