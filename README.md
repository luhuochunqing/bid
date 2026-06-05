一旦我所属的文件夹有所变化，请更新我。

# 西域数智化投标管理平台

本仓库是“西域数智化投标管理平台”的前后端一体化代码仓库。
目录名、NPM 包名、Maven 构件名中仍保留 `xiyu-bid-poc`、`bid-poc` 等历史命名，这些属于仓库遗留，不代表项目仍按 POC 方式交付。

## 项目背景

西域数智化投标管理平台面向企业投标全生命周期管理场景，目标是把商机获取、项目立项、编标协同、知识复用、费用与资源管理、结果闭环和管理分析统一到一套私有化部署系统中。

当前仓库已经进入真实 API 交付开发阶段：
- **唯一支持路径**：真实后端 API
- **统一决策**：彻底删除双模式，不再把 Mock 作为正常开发路径
- **当前现实**：仓库中仍残留部分 Mock 相关脚本与代码，它们只属于待清理技术债，不应被继续使用或扩散

## 核心功能

### 1. 工作台
- 关键指标统计卡片
- 我的待办事项
- 投标日历
- 进行中项目总览

### 2. 标讯中心
- 外部标讯获取与入库
- AI 匹配与解读
- 标讯详情与分析
- 相关案例推荐

### 3. 投标项目
- 项目立项
- 项目详情与进度跟踪
- 协同任务分配
- 项目级招标文件解析，并复用解析结果生成任务看板和 AI 标书初稿
- AI 标书初稿生成、缺漏检查与写入文档编辑器
- AI 合规检查与质量评分
- 投标结果闭环

### 4. 知识资产中心
- 资质库
- 案例库
- 模板库

### 5. 资源管理
- 费用管理
- 保证金退还跟踪与自动提醒
- 招标平台账户管理
- BAR（投标资产台账）相关能力

### 6. 数据分析
- 管理驾驶舱
- 中标率趋势
- 竞争情报分析
- ROI 分析
- 区域与业务分布分析

### 7. 系统设置
- 用户与角色权限
- 系统参数配置
- 预警与审计相关配置

## 技术栈

### 前端
- Vue 3（Composition API）
- Vite 5
- Element Plus
- Pinia
- Vue Router 4
- Axios
- ECharts
- Sass / CSS Variables
- Vitest
- Playwright

### 后端
- Spring Boot 3.2
- Java 21
- Spring Security + JWT
- Spring Data JPA
- MySQL 8.0
- Redis
- Flyway

## 快速开始

### 前置依赖

- Node.js 18+
- npm
- Java 21
- Maven 3.9+
- MySQL 8.0
- Redis

### 推荐启动方式

```bash
npm install
npm run dev:stable:start
npm run dev:stable:status
```

说明：
- `npm run dev:stable:start` 会调用 `scripts/dev-services.sh`，统一拉起文档转换 sidecar、后端和前端，适合日常联调和反复重启
- `npm run dev:all` 是兼容入口，会先按当前目录加载 `scripts/dev-env.sh`，再转调同一套 `scripts/dev-services.sh` 稳定启动逻辑
- 主目录默认启动到前端 `127.0.0.1:1314`、后端 `127.0.0.1:18080`、sidecar `127.0.0.1:8000`、数据库 `xiyu_bid_main`
- 多 Agent worktree 会自动使用专属端口和数据库，例如 Codex worktree 使用前端 `1316`、后端 `18082`、sidecar `8002`、数据库 `xiyu_bid_codex`
- 后端默认使用 `dev,mysql`
- 启动脚本会自动传入本地 MySQL 默认用户 `xiyu_user`
- 启动脚本会自动识别本机 Redis：优先 `6379`，若仅 Docker 暴露 `16379` 也会自动切换
- 启动脚本会为文档转换 sidecar 自动生成本地共享密钥，保存到 `.runtime/dev-services/sidecar.shared-key`，并同时注入 sidecar 与后端，不写入源码
- 前端会以 `VITE_API_MODE=api` 连接真实后端（不新增前端 mock 主入口）
- 如需覆盖本地连接信息，可在启动前设置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`

### 新 Mac / 未安装 MySQL 的 Docker 启动方式

如果新电脑已有 Docker Desktop，但没有本机 MySQL，可以直接用项目脚本拉起本地 MySQL 8.0 和 Redis 容器，并把变量写入 `.runtime/local-docker/local-docker.env`：

```bash
npm install
npm run dev:docker:start
```

常用命令：

```bash
npm run dev:docker:status
npm run dev:docker:logs
npm run dev:docker:stop
```

默认容器变量：
- MySQL：`127.0.0.1:3306/xiyu_bid_main`，用户 `xiyu_user`，密码 `XiyuDB!2026`
- Redis：`127.0.0.1:6379/0`
- 前端：`127.0.0.1:1314`
- 后端：`127.0.0.1:18080`

如果端口被占用，可显式覆盖：

```bash
DB_PORT=13306 REDIS_PORT=16379 npm run dev:docker:start
```

### 运行模式矩阵

| 场景 | 前端数据来源 | 后端 Profile | 数据行为 |
| --- | --- | --- | --- |
| 本地联调（推荐） | 真实 API | `dev,mysql`（稳定启动脚本默认） | 连接 MySQL 8.0，仅真实数据库数据，不注入 Demo |
| 生产真实库 | 真实 API | `prod,mysql`（或等价真实库 profile） | 连接 MySQL 8.0，仅真实数据库数据，不注入 Demo |
| 自动化 E2E 基线 | 真实 API | `e2e`（测试脚本专用） | API 返回真实数据 + 内存 Demo 融合；Demo 使用负数 ID 且只读 |
| 历史 mock 资产 | 禁止作为页面主路径 | 不适用 | 仅保留为迁移技术债与参考，不允许新增页面直连 |

### 稳定常驻启动（推荐长期联调）

当你需要服务在当前终端退出后仍保持可用，请使用守护式脚本：

```bash
npm run dev:stable:start
npm run dev:stable:status
npm run dev:stable:logs
npm run dev:stable:watch:start
npm run dev:stable:watch:status
```

这套方式会自动：
- 以 `dev,mysql` 启动后端
- 传入本地数据库和 Redis 连接参数
- 校验前端是否真的是当前工作区对应的 API 模式实例
- 校验前后端进程是否匹配当前代码指纹和启动参数，避免拉取新代码后复用旧进程
- 前端启动前重建 Vite 依赖优化缓存，降低分支切换后的 `chunk-*.js` 404 风险

停止服务：

```bash
npm run dev:stable:watch:stop
npm run dev:stable:stop
```

查看当前运行模式（可快速确认是否 e2e Demo 融合）：

```bash
npm run dev:mode
```

### 手动启动方式

```bash
# 终端 1：启动后端
cd /Users/user/xiyu/xiyu-bid-poc/backend
SPRING_PROFILES_ACTIVE=dev,mysql \
DB_HOST=localhost \
DB_PORT=3306 \
DB_NAME=xiyu_bid \
DB_USERNAME=xiyu_user \
DB_PASSWORD='XiyuDB!2026' \
REDIS_HOST=localhost \
REDIS_PORT=16379 \
JWT_SECRET='xiyu-bid-poc-local-dev-secret-key-please-change-in-prod-32bytes-min' \
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=18080"

# 终端 2：启动前端
cd /Users/user/xiyu/xiyu-bid-poc
VITE_API_MODE=api VITE_API_BASE_URL=http://127.0.0.1:18080 npm run dev -- --host 127.0.0.1 --port 1314
```

如果你的 Redis 本机就跑在 `6379`，把上面的 `REDIS_PORT=16379` 改成 `6379` 即可。

### 访问地址

- 前端：<http://127.0.0.1:1314>
- 后端健康检查：<http://127.0.0.1:18080/actuator/health>

## 演示账号

| 用户名 | 角色 | 说明 |
|--------|------|------|
| 小王 | 销售（sales/staff） | 普通业务人员 |
| 张经理 | 经理（manager） | 部门经理 |
| 李总 | 管理员（admin） | 系统管理员 |

## 验证命令

### 前端与文档

```bash
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
npm run build
```

`npm run check:line-budgets` 默认检查当前工作区；如需与 pre-commit 完全一致的暂存区口径，执行 `npm run check:line-budgets:staged`。

### 前端测试

```bash
npm run test:unit
npm run test:e2e
```

### 后端测试

```bash
cd backend
mvn test -Dtest=<相关测试类>
mvn test -Dtest=FlywayBaselineContextTest,FlywayMysqlContainerTest
mvn test -Dtest=ArchitectureTest
mvn test
```

### 后端质量门禁

```bash
cd backend
mvn -Pjava-quality,java-quality-spotbugs,quality-strict -DskipTests -Djacoco.skip=true checkstyle:check pmd:check spotbugs:check
```

说明：
- `quality-strict` 现在作为全局开关恢复可用，用来启用 Checkstyle、PMD、SpotBugs 的真实门禁配置。
- 涉及后端模块边界、迁移脚本或真实 API 链路的改动，默认同时运行 Flyway 与 ArchitectureTest。

## 当前验证基线

截至 2026-04-22：
- `npm run check:front-data-boundaries` 可通过
- `npm run check:doc-governance` 可通过
- `npm run build` 可通过
- `npm run test:unit` 可通过，当前基线为 `61` 个测试文件、`341` 个测试
- `npm run test:e2e` 可通过，当前基线为 `26` 通过、`2` 跳过
- `backend` 的 `mvn test` 可通过，当前基线为 `1043` 个测试

后端架构测试已修复的历史问题包括：
- `config -> service` 依赖：`E2eDemoDataInitializer` 不再直接依赖 `RoleProfileService`
- `config <-> service` 循环依赖：`RateLimitService` 不再依赖 `ExportConfig`

这意味着 `ArchitectureTest` 已可作为常规后端架构门禁执行；后续任务仍需据实报告验证结果。

## E2E 基线

### API 模式 Playwright

- `npm run test:e2e` 会优先复用已运行的前后端环境
- 若本地 `127.0.0.1:18080` 和 `127.0.0.1:1314` 都可用，测试会直接使用现有环境
- 若环境未启动，Playwright 会调用 `scripts/test/start-api-e2e-stack.sh` 准备 API 联调环境
- 测试结束后，仅会关闭由本次 Playwright 启动的进程
- `e2e` profile 仅用于自动化 E2E 基线，会在后端启用 H2 Demo 融合（全角色生效）：读取接口返回“真实数据 + 内存 Demo 数据”，写接口对 Demo 负数 ID 返回只读提示
- CI 当前至少执行三条真实 API 冒烟链路：
  - `e2e/commercial-main-flow.spec.js`
  - `e2e/project-detail-workflow.spec.js`
  - `e2e/document-editor-case-knowledge.spec.js`

### 手动控制 E2E 基线

```bash
bash scripts/test/start-api-e2e-stack.sh
npm run test:e2e
bash scripts/test/stop-api-e2e-stack.sh
```

## 项目结构

```text
xiyu-bid-poc/
├── src/
│   ├── api/                 # API 层与客户端配置；仍含待删除的历史 mock 遗留
│   ├── components/          # 公共组件
│   ├── config/              # 前端配置
│   ├── router/              # 路由配置
│   ├── stores/              # Pinia 状态管理
│   ├── styles/              # 样式与设计变量
│   ├── utils/               # 工具函数
│   └── views/               # 业务页面（Dashboard/Bidding/Project/Knowledge/Resource/Analytics/System/AI/Document）
├── backend/                 # Spring Boot 后端
├── e2e/                     # Playwright 用例
├── scripts/                 # 校验、测试、发布与辅助脚本
├── docs/                    # 项目治理、交付与实施文档
└── start.sh                 # 一键联调启动脚本
```

## 文档入口

- `AGENTS.md`：协作口径与智能体约定
- `RULES.md`：四阶段流程、核心业务逻辑架构约束、红线与当前基线
- `CLAUDE.md`：执行入口、命令、验证清单与环境坑点
- `docs/FRONTEND_REAL_DATA_GOVERNANCE.md`：前端真实数据治理
- `docs/DOCUMENTATION_GOVERNANCE.md`：文档治理规范
- `WIKI.md`：Wiki 知识库治理说明

## 当前状态

- 已统一决策为“真实 API 单一路径”，不再接受双模式扩张
- 仓库中仍有部分 Mock 遗留代码与命令，后续需要继续清理
- 前端构建与文档治理基线稳定
- 后端架构测试已修复，可作为常规门禁执行
- 仓库命名中的 `POC` 属于历史遗留，后续需要逐步完成命名去 POC 化

---

## 多 Agent 协作工作区

为支持多 Agent (Claude, Codex, Gemini, Cursor) 并行开发，本项目采用 Git Worktree 物理隔离方案：
- **基准区 (Main)**: `/Users/user/xiyu/xiyu-bid-poc/` (只同步，不开发)
- **Agent 工作区 (Worktrees)**: `/Users/user/xiyu/worktrees/`
- **隔离机制**: 每个 Agent 拥有独立的端口映射与数据库实例。

详见 `CLAUDE.md` 获取各 Agent 的端口分配表及启动指令。
