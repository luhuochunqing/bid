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
6. **恢复被回退的代码默认走 cherry-pick，禁止手工重写** → 详见 `RELIABILITY.md §回退恢复纪律`（git blame 可追溯性是团队资产）
7. **Agent Wiki 维护纪律** → 详见 `.wiki/WIKI.md`
   - 任务收尾必须过 wiki checkpoint（`agent-finish-task.sh` 已内置）
   - 复杂问题答完必须回填 `.wiki/pages/`（复合查询回填）
   - pre-push 必须过 `wiki-health-check`（2 周过渡期至 2026-08-06 后转 hard fail）

## "执行 co-数字" 标准开场三步（强制，不依赖任何 skill）

用户说"执行 co-XXX"时，**无论 dev-credentials skill 是否在可用列表中**，必须先做这三步，不得跳过、不得用其他动作替代：

1. **凭证自检**（只判存在性，绝不打印 token 值）：
   ```bash
   printf "GITEE=%s LINEAR=%s\n" "${GITEE_TOKEN:+<有值>}" "${LINEAR_API_KEY:+<有值>}"
   ```
   - 两个都 `<有值>` → 继续
   - 任一为空 → 停下，告诉用户去检查 `~/.zshenv`，不要硬往下跑

2. **查 Linear issue**（拿到标题/描述/状态/UUID）：
   ```bash
   curl -s -X POST https://api.linear.app/graphql \
     -H "Content-Type: application/json" \
     -H "Authorization: $LINEAR_API_KEY" \
     -d '{"query":"query { issue(id: \"CO-XXX\") { id title identifier description state { name } team { id } } }"}'
   ```

3. **查 team states**（拿到状态机 UUID，后续流转要用）：
   ```bash
   curl -s -X POST https://api.linear.app/graphql \
     -H "Content-Type: application/json" \
     -H "Authorization: $LINEAR_API_KEY" \
     -d '{"query":"query { team(id: \"<step2拿到的teamId>\") { states { nodes { id name type } } } }"}'
   ```
   缓存结果：记录"Todo / 开发中 / 评审中 / 已完成"对应的 stateId UUID。

**禁止行为**：
- 不查 Linear 就开始猜任务内容
- skill 不在列表就放弃 Linear 访问（token 在 `~/.zshenv`，与 skill 无关）
- 跳过凭证自检直接干活

完成三步后，再按"按任务找信息"表进入正常流程。

## 按任务找信息

| 你在做什么 | 先读 | 详情位置 |
|---|---|---|
| 写后端 Java | `ARCHITECTURE.md` | FP-Java 11 条、包分层、数据库迁移规范 |
| 写前端 UI | `FRONTEND.md` | 组件规范、wangEditor 盲区 |
| 做架构设计 | `ARCHITECTURE.md` | 分层规则、db-schema 机器真相 |
| 安全/权限/Mock | `SECURITY.md` | Mock 政策、Final Class Mock、权限守卫 |
| 发起复杂任务 | `PLANS.md` | Spec Kit 门禁、exec-plans 落点 |
| 收尾任务/清理分支 | `scripts/agent-finish-task.sh` | 三重合入检查、锁清理、锚点切换、远端分支删除 |
| 恢复被回退的代码 | `docs/references/rollback-recovery-playbook.md` | cherry-pick 优先纪律、三种例外、CO-338 真实案例（**禁止手工重写**） |
| 启动服务/跑测试 | `CLAUDE.md` | 启动命令、环境变量、验证清单 |
| 提交 PR/查门禁 | `RELIABILITY.md` | 14 道门禁、文件锁、回滚手册、PR 创建 |
| 查产品需求 | `PRODUCT_SENSE.md` | 产品蓝图、PRD |
| 查设计规范 | `DESIGN.md` | 设计系统令牌 |
| 追踪质量 | `QUALITY_SCORE.md` | 模块质量评分、技术债追踪 |
| 查数据库结构 | `docs/generated/db-schema.md` | 自动生成（`npm run db:generate-schema`） |
| 修 bug 前 | `docs/lessons/lessons-learned.md` | 先搜索同类问题，避免重复踩坑 |
| 维护 Wiki | `.wiki/WIKI.md` | Agent Wiki 运行规范（摄入/Lint/回填触发器、AI-First 编写标准） |
| 开新 AI Coding 项目/复盘经验 | `docs/ai-coding-playbook/` | 从 2059 个 PR 提炼的经验手册：bug 根因模式、多Agent协作、透传范式、门禁、回退纪律、行动清单 |

## 协作暗号

- **"早操SOP"** → `git fetch origin && git rebase origin/main && bash scripts/sync-env.sh .`（自动检测 GitHub 镜像状态；如需同步加 `SYNC_TO_GITHUB=1`，仅主工作区生效）
- **"早操SOP + 同步 GitHub"** → `SYNC_TO_GITHUB=1 bash scripts/sync-env.sh .`（主工作区专用，顺便把 Gitee main 推到 GitHub 保持镜像最新）
- **"开个任务/开个分支 XX"** → `scripts/agent-start-task.sh <当前agent名> <XX> origin/main --in-place`
- **"早操SOP + 开个分支 XX"** → 同上，相当于 `--in-place` 一次完成全部流程
- **"收个任务/收尾"** → 见下方"收尾流程"小节（主路径：`scripts/agent-finish-task.sh`；手动 fallback：五步流程）
- **"健康检查"** → `npm run agent:health-check`（聚合主工作区 sidecar/backend/frontend 健康状态；其他 worktree 不再启动开发环境）
- **"启动开发环境"** → 仅主工作区（`/Users/user/xiyu/worktrees/trae`）允许执行 `./scripts/start-frontend.sh` / `./scripts/start-backend.sh` / `./scripts/dev-services.sh`；其他 worktree 由脚本守卫拒绝执行
- **"推 GitHub 镜像"** → `bash scripts/sync-to-github.sh`（Gitee main → GitHub main 单向镜像，含安全检查，禁止反向覆盖）
- **"同步 GitHub 改动"** → `bash scripts/sync-from-github.sh <commit-or-branch>`（GitHub → Gitee 增量 cherry-pick，禁止 merge/覆盖）

### 收尾流程

> **"收个任务/收尾"暗号的展开。**
> **主路径**：直接 `scripts/agent-finish-task.sh`（三重合入检查 + 锁清理 + 切回锚点 + 可选删除远端分支，支持 `--dry-run` 预览）。本节五步流程仅在需要手动逐步处理或排查脚本失败时使用。
> **主仓库为 Gitee（`origin`）**，禁止使用 `gh pr` / GitHub auto-merge 工作流（自 2026-06 迁移到 Gitee 后已过期，详见 `CLAUDE.md §自动合并`）。

#### 第 1 步：合并 PR

- 检查当前任务分支是否有打开的 PR（Gitee）：
  - 优先：通过 Gitee MCP（`mcp_gitee` 的 `list_repo_pulls`）查询
  - 或：`bash scripts/gitee-pr-helper.sh status <当前分支>`
- 若有打开的 PR，确认 CI 通过后合并：
  - 优先：在 Gitee Web UI 点击合并按钮
  - 或：通过 Gitee MCP 的 `merge_pull` 合并
- 若 PR 有冲突，先告知用户解决冲突再合并（**禁止** `git push --force` 绕过门禁）
- 若无 PR，检查是否需要推送当前分支：`git status` + `git log origin/<当前分支>..HEAD`

#### 第 2 步：回到锚定分支

- 锚定分支约定（与 `scripts/agent-finish-task.sh` 一致）：
  - 任务分支（`agent/<name>/<task>`）的锚点是 `main`（唯一基线分支）
  - Bootstrap 种子分支（`agent/<name>-init`）受 `agent-worktree-guard.sh` 保护，**禁止删除**
- 切换并同步：
  ```bash
  git checkout main
  git pull --rebase origin main
  ```
- 若刚合入 PR，确认 `git log main` 能看到合入 commit

#### 第 3 步：清理任务分支

- 列出本地已合并的任务分支：`git branch --merged main | grep -E "^agent/" | grep -v -- "-init$"`
- 列出本地未合并的任务分支：`git branch --no-merged main | grep -E "^agent/"`
- 向用户展示分支清单，询问是否删除已合并的任务分支
- 对用户确认删除的分支执行：`git branch -d <分支名>`
- 如需同时清理远端分支：`scripts/agent-finish-task.sh --include-remote`（**禁止**手删 `agent/<name>-init` 远端，那是持久锚点）

#### 第 4 步：检查未提交代码和 stash

- 检查工作区状态：`git status`
- 检查 stash 列表：`git stash list`
- 若有未提交改动或未清理的 stash，展示给用户并询问处理方式（提交 / stash pop / 丢弃）
- 提醒：每个 worktree 的工作区应保持干净，避免下一次早操 `sync-env.sh` rebase 时撞 conflict

#### 第 5 步：知识沉淀

- 调用 `knowledge-capture` 技能，回顾本次 session 中产生的有价值知识（bug 根因、决策、踩坑、需求确认等），按类型沉淀到 `docs/` 对应目录：
  - bug 根因 / 修复案例 → `docs/lessons/lessons-learned.md`（追加到现有文档，**禁止**新建独立页面）
  - 工程纪律 / 流程约束 → `docs/references/engineering-discipline.md`
  - 前端踩坑 → `docs/references/frontend-pitfalls.md`
  - 架构决策 → `docs/architecture/`
- **严禁**新建 `*.md` 顶层文档，优先追加到已有文件

## 文件树概览

> 本节只列 agent 会用到的入口目录和顶层文件；代码子目录不展开（详见 `CLAUDE.md §路径提示`）。

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
├── README.md              ← 项目对外 README
├── package.json           ← 前端 + 治理脚本入口（npm scripts）
├── docker-compose.yml     ← 本地 MySQL 8.0 + Redis 栈（xiyu 专用）
├── playwright.config.js   ← E2E 配置
├── VERSION                ← 当前版本号
├── .env.example           ← 环境变量模板（sync-env.sh 同步源）
│
├── src/                   ← 前端业务代码（Vue 3 + Vite 5）
├── backend/               ← 后端业务代码（Java 21 + Spring Boot 3.2，含 pom.xml/start.sh）
├── document-converter-sidecar/  ← 文档转换 Sidecar（Python，端口 8009）
├── e2e/                   ← Playwright E2E 测试
├── e2e-coverage/          ← E2E 覆盖率追踪（按蓝图小节）
├── api-tests/             ← HTTP 接口测试集（.http 文件）
├── k6-tests/              ← k6 性能测试
├── public/                ← 前端静态资源
│
├── specs/                 ← Spec Kit 工作目录（按 feature 编号，每目录含 spec/plan/tasks.md）
│
├── docs/                  ← 知识库目录
│   ├── architecture/      ← 架构设计文档（含 8-modules、preauthorize-unification-design 等）
│   ├── artifacts/         ← 演示脚本、讲标稿
│   ├── assets/            ← 客户汇报稿、准备计划表
│   ├── audit/             ← 权限审计报告（项目/标讯）
│   ├── bugfix/            ← 单点 bugfix 笔记
│   ├── deployment/        ← 部署相关文档
│   ├── design-system/     ← 设计系统主文档
│   ├── exec-plans/        ← 执行计划（active/completed/tech-debt-tracker）
│   ├── generated/         ← 机器生成真相（db-schema.md，禁止手改）
│   ├── governance/        ← 治理流程文档（如 async-governance）
│   ├── implementation-notes/ ← 按 CO-XXX 编号的实现笔记
│   ├── integration/       ← 外部系统对接文档（CRM/OSS/标讯/组织目录）
│   ├── issues/            ← 历史问题记录（01-creatorId-fix 等）
│   ├── lessons/           ← 教训库（lessons-learned.md + root-cause-analysis-co-XXX.md）
│   ├── operations/        ← 运维工作流
│   ├── permission-matrix/ ← 权限矩阵审计
│   ├── plans/             ← 活跃开发计划（含 PRD 文档）
│   ├── prototypes/        ← 原型设计
│   ├── references/        ← 外部知识内部化（ehsy-sdk/wangeditor/markitdown/crm-field-mapping/rollback-recovery-playbook 等）
│   ├── release/           ← 发布/回滚手册 + 部署报告（deploy-report-YYYY-MM-DD-*.md）
│   ├── reports/           ← 周期性报告（沙箱 DNS、数据权限覆盖等）
│   ├── research/          ← 调研文档（API 集成、商业范围）
│   ├── reviews/           ← 代码评审记录
│   ├── security/          ← 安全审计报告
│   ├── specs/             ← 需求规格与 UAT 模板
│   ├── tasks/             ← 任务拆解
│   ├── testing/           ← 测试与 UAT（含 manual-cases/）
│   └── archives/          ← 历史归档（含 plans-YYYY-MM/）
│
├── .wiki/                 ← 合成知识库（双空间读取层）
│   ├── pages/             ← Wiki 页面（_index.md 为导航）
│   ├── catalog/           ← 页面/源目录索引
│   └── sources/           ← Wiki 原始素材（按 bidding/customer/competitor 等分类）
│
├── conductor/             ← Gemini 任务轨道（tracks.md / product.md / workflow.md）
│
├── scripts/               ← 工具脚本（见下）
│   ├── agent-start-task.sh / agent-finish-task.sh / agent-worktree-guard.sh
│   ├── sync-env.sh / sync-to-github.sh / sync-from-github.sh
│   ├── dev-services.sh / dev-services-launchd.sh / start-frontend.sh / start-backend.sh
│   ├── pre-commit.sh / pre-push-gate.sh / gitee-pr-helper.sh / pr-create.sh
│   ├── new-migration.sh / next-migration-version.sh / generate-db-schema.mjs
│   ├── who-touches.sh / manage-agent-locks.mjs / check-hot-path-locks.mjs
│   ├── check-*.mjs|.sh    ← 各类门禁检查（Flyway/line-budget/e2e-selectors/rolecode 等）
│   ├── release/           ← 发布脚本（deploy.sh / remote-deploy.sh / rehearsal-*.sh / backup-db.sh）
│   └── lib/               ← 共享 lib（agent-lock-store.mjs / doc-governance-checker.mjs）
│
├── .githooks/             ← Git 钩子（pre-commit / pre-push / post-checkout）
├── .github/workflows/     ← CI workflows（ci.yml / main-release.yml / staging-gate.yml）
├── .specify/              ← Spec Kit 配置（templates/ + memory/constitution.md）
├── .agent/                ← Multi-Agent 契约（contracts/ + workflows/）
├── .agents/skills/        ← 本地 skills（lark-* / xiyu-deploy）
└── .agent-locks/          ← Per-task 文件锁（每任务一个 .yml）
```

## 协作语言与品牌

- **协作语言**：中文。
- **项目品牌**：对外统一使用"西域数智化投标管理平台"全称；仅引用仓库路径、包名、脚本名时保留 `xiyu-bid-poc` 等历史标识。

## 速查

- **技术栈**：Vue 3 + Vite 5 + Element Plus | Java 21 + Spring Boot 3.2 + JPA + MySQL 8.0 + Flyway | Playwright（以 `backend/pom.xml` 为唯一源）
- **本地启动必须**：`export XIYU_DEV_CONFIRMED=1`（生产部署不得使用本地脚本）
- **开发环境统一**：自 2026-06-21 起，所有开发资源（前端 1323 / 后端 18089 / Sidecar 8009 / 数据库 xiyu_bid_main / Redis DB 0）统一到主工作区 `/Users/user/xiyu/worktrees/trae`。其他 worktree（claude/codex/cursor/gemini/kimi/mimo/qoder/zcode）仅用于代码编辑和 git 操作，不启动开发环境，不分配独立端口/数据库。详见 `CLAUDE.md §多 Agent 执行手册`
- **开场约定**：AI 代理开启新任务时，先声明当前环境（worktree 名称、当前分支、协作模式、是否主工作区）
