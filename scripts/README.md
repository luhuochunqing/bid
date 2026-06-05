一旦我所属的文件夹有所变化，请更新我。

# 脚本目录

这里放仓库级校验、清理和维护脚本。
脚本优先服务构建门禁、治理收口和本地环境整理，不承载业务逻辑。

| 文件 | 地位 | 功能 |
|------|------|------|
| `check-doc-consistency.sh` | 兼容入口脚本 | 保留旧命令入口，转调新的文档治理检查器 |
| `agent-worktree-guard.sh` | 门禁脚本 | 阻止在基础分支、共享 bootstrap worktree 或缺少 `.agent-task-context` 的任务 worktree 中提交 |
| `check-doc-governance.mjs` | 门禁脚本 | 检查强制目录 README 和强制文件头注释是否符合规范 |
| `check-front-data-boundaries.mjs` | 门禁脚本 | 检查业务层是否发生遗留 Demo 回退代码污染 |
| `check-agent-locks.mjs` | 门禁脚本 | 检查本分支及 `origin/*` 分支 `.agent-locks.yml` 中的多 Agent 文件锁，阻止当前 diff 修改其他 Agent 持有的有效文件锁或目录锁 |
| `manage-agent-locks.mjs` | 管理脚本 | 通过 `agent:lock-acquire` / `agent:lock-release` 命令化登记和释放当前任务分支的文件锁，避免手写锁字段 |
| `check-line-budgets.mjs` | 门禁脚本 | 对核心源码目录执行 300 行棘轮门禁：默认检查当前工作区；pre-commit 走 staged，CI 走显式 diff 范围 |
| `check-version-sync.mjs` | 门禁脚本 | 校验根目录 `VERSION`、`package.json` 与 `backend/pom.xml` 是否保持一致 |
| `agent-start-task.sh` | 工作区初始化脚本 | 为指定 Agent 创建独立 worktree、任务分支和本地 `.agent-task-context`，支持通过 `--lock` / `--lock-dir` 批量登记初始文件锁，避免在默认工作区开发 |
| `agent-dev.sh` | 启动脚本 | 多 Agent 本地服务统一入口：自动识别当前 worktree 的前端、后端、sidecar 端口、数据库、Redis DB 和 launchd label，并提供 `morning/up/restart/status/logs/stop` 一键命令 |
| `agent-worktree-guard.sh` | 提交门禁脚本 | 阻止在 `main`、`agent/*-init`、共享 Agent bootstrap worktree 或缺少任务上下文的 worktree 中提交 |
| `line-budget.config.json` | 配置文件 | 声明 300 行门禁的纳入目录、文件类型与排除规则，供 pre-commit 与 CI 共用 |
| `wiki-common.mjs` | 基础库脚本 | 提供 Wiki ingest/build/check 共用的 frontmatter、目录、索引与链接处理能力 |
| `wiki-ingest.mjs` | 摄入脚本 | 扫描 `.wiki/sources/` 原始资料（含 `bidding/`、`contract/` 等分类），抽取到 `.wiki/extracts/`，并更新 Source Catalog |
| `wiki-build.mjs` | 编译脚本 | 规范化 `.wiki/pages/`，补齐实施空间页面，生成 Page Catalog 与 backlinks |
| `wiki-check.mjs` | 门禁脚本 | 校验 frontmatter、链接完整性、双索引一致性、抽取产物与时效健康度 |
| `check-java-coding-standards.sh` | 门禁脚本 | 检查暂存区 Java 代码规范（如 `catch(Exception)`、`Optional.get()`、原始泛型），并执行 changed-code 质量门禁：Checkstyle 默认全启；PMD 支持 `off|report|on` 分阶段只检查目标服务包；SpotBugs 支持 `auto|off|report|on`，并通过 `quality.includes` / `quality.onlyAnalyze` 缩圈到目标改动类 |
| `install-java-standards-hook.sh` | 安装脚本 | 将仓库 `.githooks/pre-commit` 安装到当前 Git hooksPath 或 common hooks 目录，支持多 worktree 共用门禁 |
| `local-ci.sh` | 门禁脚本 | GitHub Actions 账单受限期间的本地验收入口；提供 `quick`、`full`、`release` 三档真实 API 模式门禁，后端门禁前会清理构建输出，H2 集成测试显式固定 `ddl-auto=create-drop` 以避免本机环境变量污染，不替代、不修改远端 Actions 定义 |
| `performance/` | 压测脚本目录 | 存放真实 API 性能压测脚本，当前包含 200 销售并发 k6 主链路 |
| `clean-local-artifacts.sh` | 清理脚本 | 删除本地产生的测试、报告和演练产物 |
| `dev-env.sh` | 环境识别脚本 | 按当前 main checkout 或多 Agent worktree 导出专属前端端口、后端端口、sidecar 端口、数据库名和 Redis DB |
| `dev-frontend.sh` | 启动脚本 | 统一前端本地启动入口，强制 Vite 使用真实 API 模式和默认后端地址 |
| `dev-frontend-health.sh` | 健康检查脚本 | 用有界探针校验 `1314` 前端服务是否来自当前仓库，并确认运行时 API 模式和后端地址正确 |
| `dev-services.sh` | 启动脚本 | 管理本地文档转换 sidecar、后端、前端服务启动、停止、状态和 watchdog，并用当前代码指纹、启动参数和可配置等待预算校验端口是否属于当前工作区的最新进程；自动生成本地 sidecar 共享密钥并只注入子进程；从本地 0600 文件读取 DeepSeek API key 并在后端身份中只记录 key hash；前端启动前会重建 Vite 缓存 |
| `dev-services-launchd.sh` | 启动脚本 | 管理 macOS launchd 常驻服务，启动、停止、重启、卸载时同步清理 sidecar/后端/前端子进程并传递启动等待预算、DeepSeek key 文件路径和 sidecar 密钥文件路径，避免 key 明文写入 plist、残留端口占用和旧代码进程被复用 |
| `local-docker-stack.sh` | 启动脚本 | 为未安装本机 MySQL 的新 Mac 拉起本地 MySQL 8.0 与 Redis Docker 容器，生成 `.runtime/local-docker/local-docker.env`，并可转调 `dev-services.sh` 启动真实 API 模式前后端 |
| `start-backend.sh` | 启动脚本 | 多 Agent worktree 后端启动入口，注入独立端口、数据库和 Redis DB；自动在本机 Redis `6379/16379` 之间选择可用端口后转调后端启动脚本 |
| `start-frontend.sh` | 启动脚本 | 多 Agent worktree 前端启动入口，按 `dev-env.sh` 分配端口启动 Vite |
| `sync-env.sh` | 环境同步脚本 | 将根目录环境模板同步到指定 worktree，辅助多 Agent 环境初始化 |
| `sync-version.mjs` | 维护脚本 | 以根目录 `VERSION` 为单一版本源，同步前端 `package.json` 和后端 `backend/pom.xml` |
| `who-touches.sh` | 协作诊断脚本 | 列出未合并且触碰指定路径的 `agent/*` 分支，辅助多 Agent 冲突排查 |
| `release/` | 发布脚本目录 | 管理发布演练、后端预编译与启动诊断、复用演练栈的 E2E 门禁、产物打包、远端激活、备份恢复和生产 smoke 验活；数据库口径统一为 MySQL 8.0 |
| `test/` | 测试基线目录 | Playwright 与 API 联调测试的启动、停止和说明脚本 |
