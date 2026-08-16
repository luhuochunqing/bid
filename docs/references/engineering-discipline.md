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
- `agent/*` 分支自动跳过 test:unit + E2E-UI 检查（`pre-push-gate.sh:39-43`，完整检查留给 CI），核心门禁（Flyway/锁/行预算/架构）仍保留
- agent-locks 属主校验按 `lock.branch === 当前本地分支名` 匹配；从其他分支名推送带迁移文件的 PR 分支会报"blocked by active lock"。解法：本地分支名与锁文件 `branch` 字段一致（重命名本地分支即可）
- 锁文件（`.agent-locks/<task>.yml`）必须同时覆盖 `db/migration-mysql` 和 `db/rollback/migration-mysql` 两个目录，缺一报"high-risk path changed without active lock"
- E2E-UI 联动检查看 `git diff <merge-base>..HEAD` 的 `src/(router|views)/` 变更；跳过方式是**最新提交** message 加 `[skip e2e-scope]`
- pre-push 门禁会自动把分支 rebase 到最新 origin/main，冲突时会停在 rebase 中间态继续跑检查（此时锁检查等结果不可信），必须先完成 rebase 再重推

**合法逃生路径（按优先级）**：
1. 从 `agent/*` 分支开发推送（自动跳过环境性检查）
2. `bash scripts/pre-push-gate.sh --skip-tests` 手动验证其余门禁全绿后，`PRE_PUSH_GATE=0 git push`（仅限失败项已确认为环境性、与改动无关的场景，并在汇报中显式声明）
3. 禁止把环境性失败当作"修测试"的理由去改这两个 spec——它们的环境断言本身是设计意图

## 3. PR 合并前 CI 必须绿（含后端编译）——自验收结论必须可复现（2026-08-16）

**事故**：PR !2292（AI 评分标准解析 V3）合入 main 后，后端在合并点 `005959888` 上 `mvn compile` 失败——`OpenAiScoreAnalyzer.java` 跨包裸用 `ScoreDocExcerptExtractor` 未 import（`a40c9e289` 引入）。但 PR 描述与 `docs/implementation-notes/prd-acceptance-handoff-2292-2293.md` 均声称"后端测试全部通过"。事实上：声明中的测试运行在引入编译错误的 commit 之前（或本地存在未提交修复），main 合入后无人再编译过。

**纪律**：
- **合并前置条件**：Gitee PR 合并时，CI（`.github/workflows/ci.yml` 的 backend job 含完整 `mvn` 编译+测试，含后端改动的 PR 不走 fast-path）必须为绿；不得在 CI 未完成或失败时合并，也不得只看前端/脚本类门禁结论。
- **自验收结论必须可复现**：PR 描述或验收文档中写"测试通过 / BUILD SUCCESS"时，必须附**可复现命令与运行 commit**（如 `mvn test -Dtest='com.xiyu.bid.scoreparse.**' @ <commit>`）；声明与 main 实际状态不符即视为验收失实。
- **最后一公里规则**：fix 类 commit 推送后（尤其是"对齐验收 N 点改动"这类批量收尾），必须以**最终 tip** 重跑编译与相关测试，再更新 PR 描述中的验证声明。
- **合并后冒烟**：合入 main 的 PR 若含 `backend/` 改动，合并后至少执行一次 `mvn compile` 冒烟（本地或 CI main 分支流水线），把"不可构建合入"的暴露时间从下次部署提前到合并时刻。
