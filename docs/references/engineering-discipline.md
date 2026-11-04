# 工程纪律与流程约束

> 跨任务复用的工程纪律：部署机制、门禁行为、分支/锁约定。单点技术坑进 `docs/lessons/`，本文件只收"反复适用"的流程性约束。

## 1. 生产 JVM 内存只认 JAVA_OPTS——运维文档必须引用真实生效机制（2026-08-04）

**事实**：生产 systemd（`xiyu-bid-backend.service`）通过 `ExecStart=... java ${JAVA_OPTS} ... -jar app.jar` 消费 `/etc/xiyu-bid/backend.env` 中的 `JAVA_OPTS`（第 96 次部署建立的机制，见 `docs/release/deploy-report-2026-07-17-96th-test.md`）。`JVM_MEMORY` 只在 dev 脚本 `backend/start.sh` 中消费，**写进 backend.env 不会生效**。

**事故形态**：PR !2250 初版 runbook 要求生产设置 `JVM_MEMORY="-Xmx4g..."`，运维照做后堆不生效（现网实为 `-Xmx2g`），4GB 堆成为"注释里修了生产"的纸面修复。

**纪律**：
- 写运维文档时，引用的变量名/机制必须在真实部署单元（systemd unit、remote-deploy.sh）中 `grep` 验证存在，禁止凭 dev 脚本类推生产。
- 仓库内的 systemd 模板（`docs/release/systemd/xiyu-bid-backend.service`）必须与现网 unit 保持同步，模板/现网漂移本身就是事故源。
- 部署报告中的配置变更（如"backend.env 新增 JAVA_OPTS"）是机制真相源，写新文档前先查最近一次部署报告。

## 2. feat/* 分支推送门禁行为与合法逃生路径（2026-08-04）

**背景**：非 `agent/*` 分支（如 `feat/performance-bundle-export`）在非主 worktree 推送时会触发完整 pre-push 门禁，其中两个前端脚本测试是环境性的、在非主 worktree 必然失败：

- `scripts/start-env-detection.spec.js`：断言当前 worktree 是主工作区（trae），其他 worktree 必失败
- `scripts/sidecar-dev-services.spec.js`：断言本 worktree sidecar 服务在运行，非主 worktree 不启动 sidecar 必失败

**门禁行为要点**：
- `agent/*` 分支自动跳过 test:unit + E2E-UI 检查（`pre-push-gate.sh:39-43`，完整检查需在本地另行补跑），核心门禁（Flyway/锁/行预算/架构）仍保留
- agent-locks 属主校验按 `lock.branch === 当前本地分支名` 匹配；从其他分支名推送带迁移文件的 PR 分支会报"blocked by active lock"。解法：本地分支名与锁文件 `branch` 字段一致（重命名本地分支即可）
- 锁文件（`.agent-locks/<task>.yml`）必须同时覆盖 `db/migration-mysql` 和 `db/rollback/migration-mysql` 两个目录，缺一报"high-risk path changed without active lock"
- E2E-UI 联动检查看 `git diff <merge-base>..HEAD` 的 `src/(router|views)/` 变更；跳过方式是**最新提交** message 加 `[skip e2e-scope]`
- pre-push 门禁会自动把分支 rebase 到最新 origin/main，冲突时会停在 rebase 中间态继续跑检查（此时锁检查等结果不可信），必须先完成 rebase 再重推

**合法逃生路径（按优先级）**：
1. 从 `agent/*` 分支开发推送（自动跳过环境性检查）
2. **pre-commit vitest 门禁**：`.githooks/pre-commit` 的 testing-gate（`scripts/check-testing-standards.sh`）对暂存前端文件跑 vitest；纯源码 worktree 无 `node_modules` 时 `npx vitest` 会因 `vite.config.js` 加载失败（`Cannot find package 'vite'`）中止 commit。逃生阀 `SKIP_TESTING_GATE=1 git commit`（脚本第 50-52 行专为"无完整 node_modules 的 worktree 环境"设；后端 arch 门禁仍照常跑，不受影响）
3. `bash scripts/pre-push-gate.sh --skip-tests` 手动验证其余门禁全绿后，`PRE_PUSH_GATE=0 git push`（仅限失败项已确认为环境性、与改动无关的场景，并在汇报中显式声明）。注意：`--skip-tests` 只跳 test:unit + build，**不跳第 13 步 eslint（`pre-push-gate.sh:669` 无 SKIP_TESTS 守卫）**；无 `node_modules` 时 eslint 仍会 command-not-found 失败，此时直接 `PRE_PUSH_GATE=0 git push`
4. 禁止把环境性失败当作"修测试"的理由去改这两个 spec——它们的环境断言本身是设计意图
5. 以上逃生阀仅针对环境性失败；**禁止 `--no-verify`**（会一并跳过 Flyway/锁/行预算/架构等核心门禁）

## 3. PR 合并前必须本地跑通编译+测试（含后端编译）——自验收结论必须可复现（2026-08-16）

**事故**：PR !2292（AI 评分标准解析 V3）合入 main 后，后端在合并点 `005959888` 上 `mvn compile` 失败——`OpenAiScoreAnalyzer.java` 跨包裸用 `ScoreDocExcerptExtractor` 未 import（`a40c9e289` 引入）。但 PR 描述与 `docs/implementation-notes/prd-acceptance-handoff-2292-2293.md` 均声称"后端测试全部通过"。事实上：声明中的测试运行在引入编译错误的 commit 之前（或本地存在未提交修复），main 合入后无人再编译过。

**纪律**：
- **合并前置条件**：本项目无远程 CI runner（GitHub Actions / Gitee CI 均不执行），验证门禁全部在本地跑（`pre-push-gate.sh` + `mvn`/`npm`）。Gitee PR 合并前，含后端改动的 PR 必须在本地跑通完整 `mvn` 编译+测试，不得只凭前端/脚本类门禁结论合并，也不得在未本地验证或验证失败时合并。
- **自验收结论必须可复现**：PR 描述或验收文档中写"测试通过 / BUILD SUCCESS"时，必须附**可复现命令与运行 commit**（如 `mvn test -Dtest='com.xiyu.bid.scoreparse.**' @ <commit>`）；声明与 main 实际状态不符即视为验收失实。
- **最后一公里规则**：fix 类 commit 推送后（尤其是"对齐验收 N 点改动"这类批量收尾），必须以**最终 tip** 重跑编译与相关测试，再更新 PR 描述中的验证声明。
- **合并后冒烟**：合入 main 的 PR 若含 `backend/` 改动，合并后至少在本地执行一次 `mvn compile` 冒烟，把"不可构建合入"的暴露时间从下次部署提前到合并时刻。

## 4. Code Review 三透镜纪律——行为正确性之外还有结构健康与交付就绪（2026-08-25）

**事故**：PR !2399（报价评分截断治理）经历四轮审查（初审/外部复审/二审修复/终审），前三轮全部集中在"行为正确性"平面（正则边界、测试覆盖、合成用例），而依赖方向合理性、全局常量影响面（`CONTEXT_AFTER_LINES` 3→8）、跨语言关键词契约无自动化、产物文件入库、`mergeable: false` 冲突等结构/交付层问题四轮均未进入检查面，直到第五轮（人工）才被全部发现。

**根因**：修复轮审查以上一轮 findings 清单为范围（清单继承盲区）；"ArchUnit 全绿/测试全绿"被用来顶替判断性结论；复审子代理收到作者修复意图后产生确认偏误；收敛压力下补丁式修复挤掉了重设计考量。

**纪律**：
- **三透镜全覆盖**：每个 PR 审查必须走完 A 行为正确性 / B 结构健康（依赖方向、影响面、复杂度、耦合形态）/ C 交付就绪（合入状态、仓库卫生、契约自动化）三个透镜；B 的发现不得用"测试全绿"驳回，C 的发现不得用"行为正确"驳回。
- **全量 diff 原则**：任何一轮审查（含修复轮/复审轮）必须从 `origin/main...branch` 全量 diff 起步；前轮清单只作附件、不作范围。
- **审查独立性契约**：派发审查子代理时禁止携带修复意图/预期语义/验证结论，只给 diff + 标准 + 问题。
- **合入前硬检查**：合入工具（`scripts/gitee-pr-helper.sh merge`）强制断言 `mergeable=true`；收尾脚本（`agent-finish-task.sh`）对开放 PR 打印冲突预警。
- **跨语言契约必须自动化**：Java `PriceScoringKeywords` ↔ Python `converter.py` 关键词表由 `scripts/check-keyword-sync.mjs` 校验（pre-push 门禁 §5.6），注释级契约视为违规。
- 技能落点：`.agents/skills/xiyu-code-review/SKILL.md`（第 0 步状态快照 + §7 结构健康 + §8 交付就绪 + 结论模板分栏）。

## 5. Worktree 三层架构--基准仓库/开发宿主/临时任务隔离（2026-08-25）

**背景**：多 Agent 协同开发中长期积累大量持久 worktree（claude/codex/cursor 等），每个占 1-2GB+，磁盘膨胀严重。早期文档把 `trae` 称为"主工作区"，导致概念混淆--`trae` 实际是 `.git` 基准仓库 `xiyu-bid-poc` 的一个 worktree，而非独立仓库。

**纪律**：
- **三层结构**：基准仓库（`xiyu-bid-poc`，.git 根，worktree 唯一拉取源）→ 开发宿主（`trae` worktree，唯一常驻，跑 docker/后端/前端/launchd）→ 临时任务 worktree（`xiyu-<agent>-<task>`，纯源码，用完即删）。
- **临时 worktree 纯源码**：`agent-start-task.sh` 默认不装 `node_modules`（`--with-deps` 可选），临时 worktree 仅做代码编辑 + git 操作，不启动开发环境。
- **命名约定**：临时 worktree 统一命名为 `xiyu-<agent>-<task>`，`agent-start-task.sh` 和 `agent-finish-task.sh` 的 `WORKTREES_ROOT` 路径必须一致。
- **收尾自动清理**：`agent-finish-task.sh` Step 8 删临时 worktree + Step 10 扫孤儿 worktree + `git worktree prune`。
- **`--in-place` 可选**：旧持久 worktree 内联模式保留为 `--in-place` 可选，默认走独立临时 worktree。
- 文档中"主工作区"口径已废弃，统一用"开发宿主"（trae）+ "基准仓库"（xiyu-bid-poc）。

## 6. push 基础设施--SIGPIPE 根治与 root ratchet 基线调整（2026-08-26）

**事故**：`sync-to-github.sh` 连续两次死于门禁通过后的传输阶段（exit 141 = SIGPIPE）。对照实验锁定方向：同一 HEAD 用文档化开关 `PRE_PUSH_GATE=0` 跳过 gate 后推送瞬间成功——网络、SSH 凭据、本地管道全部排除，问题出在 pre-push gate 执行期间的残留状态。

**根因（两层机制）**：

1. **主机制：git push 时序缺陷**。git push 的实际顺序是「ssh 连接建立 → pre-push hook 运行 → 传包」。hook 能收到远端 refs 即证明连接先于 hook 建立；当 hook 内跑质量门禁（前端测试/构建）耗时数分钟，期间已建立的连接处于空闲状态，远端（Gitee/GitHub 均有此策略，镜像推 GitHub 时尤甚）会掐断空闲连接；hook 通过后 git 开始写包，写入已死管道 → SIGPIPE(141)。stdout 重定向到文件也救不了——死的是网络层传输，不是本地 fd。
2. **放大器：test-queue exec-trap 缺陷**。`scripts/test-queue.sh` 的 macOS 分支（无 flock）原实现为 `exec npx vitest "$@"`——bash 的 `exec` 会用目标命令替换当前 shell 进程映像，**EXIT trap 随之永久失效**，目录锁 `/tmp/xiyu-vitest.lockdir` 在每轮运行后必然孤儿化（与信号无关）；后续 gate 撞上残留锁会静默空转等待最多 300s，且等待循环结束后无失败检查照样继续。这把"门禁可能超时"放大成"必然超时"，显著提高触发空闲连接被掐的概率。

**修复**：

- **门禁与传输解耦**（`scripts/sync-to-github.sh`）：推送前显式执行同一份 `scripts/pre-push-gate.sh`（强度不变，失败即中止），通过后以 `FORCE_PUSH_OK=1 PRE_PUSH_GATE=0 git push` 完成传输——连接建立后立即传包，不存在空闲窗口。此方案无论远端超时策略如何都成立，优于"猜测并适配各家超时阈值"。
- **exec-trap 修复**（`scripts/test-queue.sh`）：去掉 `exec`，改为普通调用 + 单引号延迟展开的 EXIT trap，锁目录每轮必被清理。

**root ratchet 基线调整**：main 历史现存 8 个 root commits（2026-06-05~07 Gitee 迁移期遗留断段），旧上限 4 导致自 6 月起所有直推 main 都会被误拦——近期无人察觉是因为大家都走 PR/Web 合并（不触发本地 hook）。两处定义（`.githooks/pre-push` 与 `.github/workflows/branch-history-guard.yml`）同步从 4 提至 8：解封存量直推，新增 root commits 仍会被拦截。历史断段清理属 `git filter-repo` 专项决策（本次不做），完成清理后必须同步下调两个文件中的基值。

**纪律**：

- **pre-push hook 内禁止 `exec` 启动带 trap 清理的子流程**：`exec` 替换进程映像后 trap 全部失效；需要前台阻塞语义就直接调用，靠 `set -euo pipefail` 保证非零退出仍触发清理。
- **长门禁不得内联在传输关键路径上**：任何「先建连接 → 干重活 → 再传输」的结构都有空闲超时风险；正确形态是「干重活（独立步骤）→ 建连接 → 立即传输」。
- **ratchet 双处定义必须同步改**：本地 hook 与 CI workflow 是同一约束的两份拷贝，只改一处会造成"本地能过 CI 挂"或反向漂移；调整时必须在注释中写明日期、现存数量、调整原因和下调条件。
- **验证路径**：Fix B 的活体验证 = 携带本提交直推 origin main 且**不带** `FORCE_PUSH_OK`（8 roots ≤ 上限 8 应天然放行）；Fix A 的活体验证 = 改造后的 `sync-to-github.sh` 以真实增量走新解耦路径推 GitHub 成功。两者均已实测通过。
- **同日活体复现（直推路径）**：本节落盘当天，携带本提交首次直推 origin main 时再次实证根因——内联门禁全绿（16 通过/0 失败）后传输前静默死亡（远端 ref 未更新、门禁通过消息之后无任何传输输出，exit code 还被管道下游吞掉），同一 HEAD 改用 `PRE_PUSH_GATE=0` 后瞬时成功。至此该机制共四度实证（GitHub 镜像路径 3 次 + Gitee 直推路径 1 次），全部符合「长门禁空闲窗口」预测。**待团队决策的根治方向**：普通直推路径的内联门禁与 git「先建连接、后传包」时序存在结构性冲突，可选方案为推广「显式先跑 `pre-push-gate.sh` → `PRE_PUSH_GATE=0` 传输」的推送包装脚本（sync-to-github.sh 已内置同型解法），或接受间歇性静默失败 + 重试；另注意 shell 管道会吞掉 git push 的真实退出码（SIGPIPE 141 变成 tail 的 0），排障时必须重定向到文件再取 `$?`。
- **普通 `git push` 路径五度实证 + keep-alive 缓解（2026-09-13）**：`agent/trae/score-multi-table-split` 分支推送时再次复现同型故障——`git push` 已配置 alias 包装器（`.githooks/git-push-wrapper.sh`）先显式跑 `pre-push-gate.sh`（29 通过/0 失败）再调真实 push，真实 push 的 pre-push hook 又内联触发第二遍门禁，门禁通过后传输阶段仍死于 SIGPIPE（exit 141，远端 ref 未更新）。至此普通直推路径共两度实证（Gitee），证明「门禁与传输解耦」方案尚未覆盖 `git push` 通用路径。**新缓解手段**：`GIT_SSH_COMMAND="ssh -o ServerAliveInterval=15 -o ServerAliveCountMax=10 -o TCPKeepAlive=yes -o BatchMode=yes -o ConnectTimeout=15 -4" git push ...` 实测一次成功——keep-alive 包对抗「空闲连接被掐」机制，与 Gitee SSH 长事务建议（见 deploy-report-2026-08-28-147th-test §SSH 连接中断）同源。注意 keep-alive 属「适配超时」类方案，仅作直推路径兜底；根治方向仍以「显式先跑门禁 → 传输」解耦为准。

## 7. 多服务/容器组件部署闭环与接口探活硬门禁（2026-09-07）

**事故**：线上/测试服务器 Sidecar 容器由于更新流程与主包打包脱节，缺少 `/extract-tables` 接口长达数周。部署脚本健康检查只探测通用的 `/health`（返回 200 即放行），而业务层静默降级为 lossy 的 Markdown 解析，造成用户项目（如 1371）评分项严重失准。

**纪律**：
- **全栈打包完整性**：发布打包脚本（`package-release.sh`）必须包含所有生产运行时组件（含 Python Sidecar、离线转换服务等），严禁任何服务组件依赖手动拉取或遗漏。
- **容器自动更新与指纹感知**：部署脚本（`remote-deploy.sh`）必须对比组件 hash/sha256；若容器源文件变动，必须自动执行 Docker 镜像构建并平滑重启容器，严禁依赖人工实机维护。
- **业务关键接口探活硬门禁**：部署验证（`deploy-test.sh` / `deploy-prod.sh` Step 6）严禁仅依赖通用健康检查（如 `/actuator/health` 或 `/health` 200）。凡具备独立功能模块的服务，必须对关键业务 endpoint（如 `POST /extract-tables`）进行真实探活；遇 404 或故障必须阻断部署，禁止静默失败。


## 8. 外部 API 非幂等响应码不可直接判成败——必须以资源终态复核（2026-10-10）

**事故**：2026-10-09 第 222 次测试部署收尾时，`scripts/gitee-pr-helper.sh merge` 报
`❌ 合并失败 (HTTP 405)` 并 exit 1，但 `origin/main` 实际已包含该 PR 的 commit，
复跑 `check` 显示 `State: merged`。误报根因：Gitee `PUT /pulls/{n}/merge` 对
**已合并/已关闭**的 PR 返回 405（而非幂等 2xx），而脚本仅按 HTTP 状态码判成败。
处置代价：agent 见"合并失败"会倾向重试或误判流程中断，人需额外查证才能确认已合入。

**纪律**：

- **判成败看资源终态，不看单次响应码**：对具备"重复执行语义"的外部写接口（合并 PR、发布、上线、去重写入），非 2xx 响应必须复核资源终态（如 `GET /pulls/{n}` 的 `state`）再定成败；终态已达成 → 幂等放行 exit 0 并打印说明（本次实现以 `幂等放行` 为可断言关键字）。
- **不得用"一律放行"替代终态复核**：把 405 一律当成功会吞掉"权限不足 / 状态非法"等真实失败。本次同期固定对抗用例 `merge405open`（终态仍 `open`）断言必须 exit 1。
- **误报是双向风险**：假失败（报错但已成功）诱导重试与流程误判；假成功（放行但未生效）漏掉真实故障。二者都要用回归用例同时锁住。
- **此类修复的正确落点是契约测试**：`scripts/test/gitee-pr-helper-contract.sh` 用本地 mock 服务器覆盖响应形态（无需真实 token），本次由 5 场景扩到 9 场景（新增 `merge405` 幂等放行、`merge405open` 真失败、`merge405closed` 已关闭未合并、`create403` 创建失败仍透传响应体），修复前 RED、修复后 33 项断言全绿（含变异实验：回退修复即 FAIL）。
- **失败诊断路径要有断言，否则"应输出"可能从未生效**：同分支既有 `head -c 500 "$result_file" 2>/dev/null >&2`——重定向从左到右生效，`2>/dev/null` 先把 fd2 指向 /dev/null，随后的 `>&2` 使 fd1 也指向 /dev/null，API 响应体**从未输出**（恰是本次 405 看不到任何服务端消息的原因）。修正为 `> head -c 500 file >&2` 后由断言锁住。教训："把错误信息打出来"这类语句必须被测试断言覆盖，否则长期静默失效；`create_pr()` 第 299 行存在同型缺陷，已在本 PR 一并修正并由 `create403` 场景断言锁住。
- **验证路径必须含实机复核**：mock 之外，对真实已合并 PR 复跑 `merge` 验证退出码 0；对不存在 PR 验证仍 exit 1（不被误判为成功）。

## 2026-11-04 三技能交付审查闭环

任务完成后，Agent 必须先运行仓库自动审查流程，不能先 push 或先建 PR：

```bash
python3 scripts/review-workflow.py run \
  --verify '["git","diff","--check"]' \
  --verify '["python3","-m","unittest","discover","-s","scripts/test","-p","test_review_workflow.py"]'
```

上例验证命令只适用于门禁脚本本身的修改；业务任务必须替换/补齐该任务的真实测试、构建命令。每个 `--verify` 是 JSON argv 数组，顺序执行，不经 shell 展开。

执行顺序：`ai-slop-cleaner` 标准写入 → 验证命令 → 独立只读 `google-code-review` → 独立只读 `xiyu-code-review`。发现问题自动交给写入 Agent 修复，再从清理开始完整重跑，最多三轮；第三轮仍失败、ESCALATE、越界或进程故障立即停止，报告问题，不创建 PR。Minor/Info 也必须记录接受/延期理由，不能静默忽略。

默认调用已登录的 Codex CLI，每阶段新进程；三个精确技能必须安装在项目或用户 `.agents/skills` / `.codex/skills`。其他宿主可用 `--agent-command '<JSON argv>'` 指定受信任适配器，见 `scripts/README.md`。

通过后提交最终代码，再正常 push、运行 `scripts/pr-create.sh`。凭证放在 worktree 私有 Git 元数据目录，可用 `git rev-parse --git-path review-gate` 定位。相同文件内容的提交可复用凭证，修改内容、分支或基线必须重新运行。PR 远端 head 必须与当前本地 HEAD 相同。

四个入口共用检查器，缺失即阻断；`UNIFIED_DEV_FLOW_SKIP_REVIEW` 和 `PRE_PUSH_GATE=0` 不能跳过 pre-push 中独立的三技能检查。run 不自动提交、push、建 PR、合并或部署。本地记录属于可信开发流程，不能防御主动修改脚本或绕过本地 hook；Gitee 服务端保护需单独配置。
