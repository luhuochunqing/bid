一旦我所属的文件夹有所变化，请更新我。

# 脚本目录

这里放仓库级校验、清理和维护脚本。
脚本优先服务构建门禁、治理收口和本地环境整理，不承载业务逻辑。

| 文件 | 地位 | 功能 |
|------|------|------|
| `check-worktree-protection.sh` | 推送前门禁 | 从受管目录直接枚举持久 worktree，校验目录有效性与 canonical common gitdir 一致性；支持 `--worktree-base`、`--expected-common-dir` 供隔离回归使用 |
| `check-worktree-protection.test.sh` | CLI 回归 | 在临时真实 Git 仓库中证明 linked worktree 通过、受管目录内独立 clone 被拒绝，不改动真实 worktree |
| `check-scoreparse-root-cause.sh` | 根因回归门禁 | 离线运行独立原文夹具及真实 DOCX 转换跨层回归，含 1382 Task291 线上事实全链路重放（`Project1382FullPipelineIntegrationTest`）；需设置含 Sidecar 依赖的 `SIDECAR_PYTHON`，失败不跳过 |
| `check-pre-push-dispatch.test.mjs` | 分发回归 | 执行实际脚本的参数/分支/新增检查分发段；记录调用，保证 agent 默认不跳过新增根因和 token 检查，显式 skip 生效 |
| `check-token-coverage.test.mjs` | CLI 回归 | 临时目录实测扫描空格路径、嵌套 node_modules 和扫描失败；不吞错伪造 0/0 |
| `check-doc-consistency.sh` | 兼容入口脚本 | 保留旧命令入口，转调新的文档治理检查器 |
| `agent-worktree-guard.sh` | 门禁脚本 | 阻止在基础分支、共享 bootstrap worktree 或缺少 `.agent-task-context` 的任务 worktree 中提交 |
| `check-doc-governance.mjs` | 门禁脚本 | 检查强制目录 README 和强制文件头注释是否符合规范 |
| `check-front-data-boundaries.mjs` | 门禁脚本 | 检查业务层是否发生遗留 Demo 回退代码污染 |
| `check-async-task-table.sh` | 门禁脚本 | 拦截新建 `*_export_task` / `*_import_task`（存量白名单除外），逼后来者接 `export_tasks` + `AbstractExportTaskStateService`（lessons §139） |
| `check-agent-locks.mjs` | 门禁脚本 | 检查本分支及 `origin/*` 分支 `.agent-locks.yml` 中的多 Agent 文件锁，阻止当前 diff 修改其他 Agent 持有的有效文件锁或目录锁 |
| `manage-agent-locks.mjs` | 管理脚本 | 通过 `agent:lock-acquire` / `agent:lock-release` 命令化登记和释放当前任务分支的文件锁，避免手写锁字段 |
| `check-line-budgets.mjs` | 门禁脚本 | 对核心源码目录执行 300 行棘轮门禁：默认检查当前工作区；pre-commit 走 staged，CI 走显式 diff 范围 |
| `check-duplicate-implementations.mjs` | 本地门禁脚本 | 防造轮子：push / 创建 PR / `ci:pre-pr` / `ci:local` 自动跑。不依赖 Gitee CI。PASS=无合理复用对象；FAIL=指出应复用的现有代码。豁免 `// REUSE-OK: <path>`，逃生阀 `DUPLICATE_IMPL_SKIP=1` |
| `generate-stress-test-data.mjs` | 数据生成脚本 | 压力测试数据批量生成工具：向 MySQL 8.0 批量生成万级标讯、千级项目及知识库案例仿真铺底数据，并附带一键清理脚本 |
| `check-version-sync.mjs` | 门禁脚本 | 校验根目录 `VERSION`、`package.json` 与 `backend/pom.xml` 是否保持一致 |
| `agent-finish-task.sh` | 收尾清理脚本 | 安全清理已合入的任务分支：三重合入检查（git branch -r --merged + git cherry + Gitee API）、清理 agent-locks 锁文件、切回锚点分支、删除本地分支、可选删除远端分支（--include-remote）和临时 worktree。支持 --dry-run 预览模式和 --force 跳过合入检查。
| `agent-start-task.sh` | 工作区初始化脚本 | 为指定 Agent 创建任务分支和本地 `.agent-task-context`（含 `--touch` 预检路径记录）；支持**两种模式**：（1）传统模式：创建独立 worktree 并完整初始化；（2）`--in-place` 模式：在 Agent 持久 worktree 内直接切分支，跳过 worktree 创建（适合串行小任务）。创建前可通过 `--touch` 触发 `who-touches.sh` 预检，自动执行 sync-env、安装 hooks、以离线优先方式安装 node 依赖，并支持通过 `--lock` / `--lock-dir` 批量登记初始文件锁，以及通过 `--push` 自动首推远端分支；无论真实执行还是 `--dry-run`，都会输出基于 touch/lock/push 状态的摘要和下一步提示，避免在默认工作区开发 |
| `agent-dev.sh` | 启动脚本 | 多 Agent 本地服务统一入口：自动识别当前 worktree 的前端、后端、sidecar 端口、数据库、Redis DB 和 launchd label，并提供 `morning/up/restart/status/logs/stop` 一键命令 |
| `agent-worktree-guard.sh` | 提交门禁脚本 | 阻止在 `main`、`agent/*-init`、共享 Agent bootstrap worktree 或缺少任务上下文的 worktree 中提交 |
| `line-budget.config.json` | 配置文件 | 声明 300 行门禁的纳入目录、文件类型与排除规则，供 pre-commit 与 CI 共用 |
| `wiki-common.mjs` | 基础库脚本 | 提供 Wiki ingest/build/check 共用的 frontmatter、目录、索引与链接处理能力 |
| `wiki-ingest.mjs` | 摄入脚本 | 扫描 `.wiki/sources/` 原始资料（含 `bidding/`、`contract/` 等分类），抽取到 `.wiki/extracts/`，并更新 Source Catalog |
| `wiki-build.mjs` | 编译脚本 | 规范化 `.wiki/pages/`，补齐实施空间页面，生成 Page Catalog 与 backlinks |
| `wiki-check.mjs` | 门禁脚本 | 校验 frontmatter、链接完整性、双索引一致性、抽取产物与时效健康度 |
| `check-java-coding-standards.sh` | 门禁脚本 | 检查暂存区 Java 代码规范（如 `catch(Exception)`、`Optional.get()`、原始泛型），并执行 changed-code 质量门禁：Checkstyle 默认全启；PMD 支持 `off|report|on` 分阶段只检查目标服务包；SpotBugs 支持 `auto|off|report|on`，并通过 `quality.includes` / `quality.onlyAnalyze` 缩圈到目标改动类 |
| `install-java-standards-hook.sh` | 安装脚本 | 将仓库 `.githooks/pre-commit` 安装到当前 Git hooksPath 或 common hooks 目录，支持多 worktree 共用门禁 |
| `local-ci.sh` | 门禁脚本 | GitHub Actions 账单受限期间的本地验收入口；提供 `quick`、`full`、`release` 三档真实 API 模式门禁，后端门禁前会清理构建输出，H2 集成测试显式固定 `ddl-auto=create-drop` 以避免本机环境变量污染，不替代、不修改远端 Actions 定义；其中 quick/full/release 共用前端快速门禁，已纳入 `npm run test:agent-start-task-contract` 守护任务启动脚本 dry-run 契约 |
| `review-workflow.py` | 三技能审查门禁 | 清理→测试→Google→西域独立审查，三轮以内自动修复；run 生成内容凭证，check 拦截不合规交付；纯文档变更（仅 `*.md`/`*.mdx` 或 `docs/`、`.wiki/` 内的 `.txt/.rst/.pdf/.png/.jpg/.jpeg/.gif/.webp/.docx/.pptx` 文档工件；代码、配置及未知类型不免审）check 直接放行 |
| `lib/review_gate.py` / `lib/review_agent.py` | 门禁内核 / Agent 适配 | 内容指纹、工件哈希、技能解析、Codex 或显式进程协议 |
| `ci-pre-pr.sh` | 提交前门禁脚本 | PR 提交前一站式本地门禁；默认覆盖前端治理检查、`test:agent-start-task-contract`、前端单元测试、API 构建、lint、路由/E2E 联动检查、pre-push-gate 与后端轻量质量门禁，可选追加 E2E 与 Flyway dry-run |
| `performance/` | 压测脚本目录 | 存放真实 API 性能压测脚本（200 销售并发 k6 主链路），以及压测执行与原始证据归档工具链（`run-stress-test.sh` / `capture-monitoring.sh` / `monitor-spec.sh` / `scrub-secrets.mjs` / `write-run-manifest.mjs`，产出物见 `docs/testing/stress-test-artifacts/`） |
| `clean-local-artifacts.sh` | 清理脚本 | 删除本地产生的测试、报告和演练产物 |
| `dev-env.sh` | 环境识别脚本 | 按当前 main checkout 或多 Agent worktree 导出专属前端端口、后端端口、sidecar 端口、数据库名和 Redis DB |
| `dev-frontend.sh` | 启动脚本 | 统一前端本地启动入口，强制 Vite 使用真实 API 模式和默认后端地址 |
| `dev-frontend-health.sh` | 健康检查脚本 | 用有界探针校验 `1323` 前端服务是否来自当前仓库，并确认运行时 API 模式和后端地址正确 |
| `dev-services.sh` | 启动脚本 | 管理本地文档转换 sidecar、后端、前端服务启动、停止、状态和 watchdog，并用当前代码指纹、启动参数和可配置等待预算校验端口是否属于当前工作区的最新进程；自动生成本地 sidecar 共享密钥并只注入子进程；从本地 0600 文件读取 DeepSeek API key 并在后端身份中只记录 key hash；前端启动前会重建 Vite 缓存 |
| `dev-services-launchd.sh` | 启动脚本 | 管理 macOS launchd 常驻服务，启动、停止、重启、卸载时同步清理 sidecar/后端/前端子进程并传递启动等待预算、DeepSeek key 文件路径和 sidecar 密钥文件路径，避免 key 明文写入 plist、残留端口占用和旧代码进程被复用 |
| `local-docker-stack.sh` | 启动脚本 | 为未安装本机 MySQL 的新 Mac 拉起本地 MySQL 8.0 与 Redis Docker 容器，生成 `.runtime/local-docker/local-docker.env`，并可转调 `dev-services.sh` 启动真实 API 模式前后端 |
| `start-backend.sh` | 启动脚本 | 多 Agent worktree 后端启动入口，注入独立端口、数据库和 Redis DB；自动在本机 Redis `6379/16379` 之间选择可用端口后转调后端启动脚本 |
| `start-frontend.sh` | 启动脚本 | 多 Agent worktree 前端启动入口，按 `dev-env.sh` 分配端口启动 Vite |
| `sync-env.sh` | 环境同步脚本 | 将根目录环境模板同步到指定 worktree，辅助多 Agent 环境初始化 |
| `sync-version.mjs` | 维护脚本 | 以根目录 `VERSION` 为单一版本源，同步前端 `package.json` 和后端 `backend/pom.xml` |
| `who-touches.sh` | 协作诊断脚本 | 列出未合并且触碰指定路径的 `agent/*` 分支，辅助多 Agent 冲突排查 |
| `release/` | 发布脚本目录 | 管理发布演练、后端预编译与启动诊断、复用演练栈的 E2E 门禁、产物打包、远端激活、备份恢复和生产 smoke 验活；生产 smoke 包含 CRM page-list 只读探针（`CRM_SMOKE_MODE`）和部署后 CRM token/config 日志回归扫描；数据库口径统一为 MySQL 8.0。一键部署脚本 `deploy-prod.sh`（生产）/ `deploy-test.sh`（测试）固化环境变量（含 `VITE_OBS_ENABLED=true`）并自动执行打包→上传→部署→验证全流程，`package-release.sh` 含 OBS 启用硬门禁（`VITE_OBS_ENABLED=true` 时校验 `Detail-*.js` 中 `.upload(` 调用数 ≥ 2） |
| `test/` | 测试基线目录 | Playwright 与 API 联调测试的启动、停止和说明脚本；其中 `agent-start-task-dryrun-contract.sh` 用于守护 `agent-start-task.sh --dry-run` 的输出契约，防止 touch/lock/push 协作提示回退；可通过 `npm run test:agent-start-task-contract` 执行 |

`check-scoreparse-root-cause.sh` 包含 `FullRuleSourceReplayTest` / `TextPrimaryScoreFactMapperTest`：actual DOCX 四入口、文本后置材料、PDF 首页/续页短引文与页码反例；完整规则恢复与数值可计算性分别验证。

`check-scoreparse-root-cause.sh` 同时运行 MarkdownRowQuotationRegressionTest、MarkdownRowQuotationGoldenReplayTest 和 Project1387PublicationRegressionTest：覆盖可选外侧管道、公式内部管道、ASCII规则边界、篡改回退和保留父子结构的全链路。

## 三技能自动审查与修复

参见 `AGENTS.md §提交 PR 前三技能门禁`。先在任务 worktree 执行 `run`，再提交、push、创建 PR。验证命令必须由任务作者提供，脚本不能判断测试选择是否充分。默认 Codex 的写入进程使用 workspace-write，审查进程使用 read-only；遵循调用者既有模型配置，不指定模型。

```bash
python3 scripts/review-workflow.py run --verify '["git","diff","--check"]'
python3 scripts/review-workflow.py check
```

仅 `git diff --check` 不能代替业务测试；上面是最小命令格式示例。可重复 `--verify`，单个进程默认超时 1800 秒，可用 `--timeout` 调整。所有 stdout/stderr 留在工件日志，控制台显示阶段。超时、无效报告或进程非零退出阻断，代码不会自动回滚。验证命令启动前即在 `verify-report.json` 写入 argv，每条完成后立即写入退出码；后续命令超时或无法启动时保留前序结果及当前命令的错误信息。阶段异常同样记录轮次、失败原因和已有工件哈希；已返回的进程保留退出码，超时或启动失败未取得退出码时记为 null。

### 其他宿主适配协议

`--agent-command '["/absolute/path/to/adapter"]'` 指定**受信任的本地程序**；执行器追加两个绝对路径：`request.json report.json`。request 包含 stage、round、原任务 scope、base、精确技能全文和 prompt；仅 fix 阶段收到上阶段失败报告。适配器必须启动独立审查会话并遵守只读要求，退出 0 后将以下 JSON 写到 report 路径。默认 Codex 使用相同契约并通过 JSON Schema 约束最终输出。

```json
{"verdict":"PASS","summary":"具体判断与验证边界","findings":[]}
```

verdict 为 PASS / NEEDS_FIXES / ESCALATE。finding 必须包含 severity（Critical/Major/Minor/Info）、file、line、message、disposition（open/accepted/deferred）、reason；Critical/Major 永远阻断，Minor/Info 的 accepted/deferred 须有具体理由。最终一轮全部通过后才生成 MERGE_READY；fix 自报 PASS 不算审查通过。

每次 run 清除上一轮放行状态，保留历史运行目录。凭证记录分支、origin/main tip、merge-base、完整改动文件内容/模式（含新增与删除）、每阶段请求/报告/日志哈希。路径收集显式禁用 Git 重命名检测，将移动绑定为旧路径删除与新路径新增，保证暂存/提交不改变同内容指纹，恢复旧路径会使凭证失效。最终交付要求工作区干净，允许同内容提交；PR 还读取 origin 上实际分支 SHA，与本地 HEAD 核对。执行器不会自动创建 PR 或向他人发消息。

纯删除 ref 的推送通过 hook 的远端认证、历史保护及 ref 检查后，不再运行当前分支凭证复检或代码质量门禁；混合推送仍校验非删除 ref 并执行质量门禁，空输入不按纯删除放行。直接运行 `pre-push-gate.sh` 仍须检查当前任务分支凭证。

本地凭证不等于密码学身份认证，也不能证明 LLM 判断必然正确；自定义适配器、测试命令以及有本地写权限的人属于信任边界。线下协议测试仅证明执行与阻断行为，不能代替真实 LLM 审查。

分支推送按**目标分支和实际提交 SHA**校验，支持 `git push origin HEAD:<当前分支>`；标签发布保留既有行为，不作为 PR 分支审查目标。

受保护基准允许现有镜像脚本的 `origin/main:main` 等表达式，但其 SHA 必须等于已知本地/跟踪基准；任务新提交改目标为 main 仍阻断。历史/force-push 保护仍由原 hook 执行。

`agent/<name>-init` 仅允许同步已知 origin/main SHA，不作为任务交付，也不能凭此跳过创建 PR 的检查。分流单测只截取参数/分支策略；三技能门禁由真实 Git 回归独立覆盖。
