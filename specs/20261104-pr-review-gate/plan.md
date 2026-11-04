# PR Review Gate Implementation Plan

**Goal:** 三技能顺序执行、自动修复、最终内容证据验证后才允许交付。
**Architecture:** Python 标准库；指纹与凭证、Agent 适配器、三轮状态机分离，凭证位于 worktree 私有 Git 元数据。四个 Shell 入口共用 check。
**Tech Stack:** Python 3、Bash、Git、Codex CLI，无新依赖。

## Constitution Check
无业务 API、DB、UI 改动；真实 Git/进程 TDD。delivery_target: working_tree，不自动 push/PR/部署。health 无可比基线，以复杂度审查和行为回归代替。

## 任务与文件范围
- [x] T1：新增 scripts/test/test_review_workflow.py，先运行缺凭证、重试、三轮、失效、越界 RED。
- [x] T2：新增 scripts/lib/review_gate.py、scripts/lib/review_agent.py、scripts/review-workflow.py，测试逐项 GREEN。
- [x] T3：修改 scripts/pr-create.sh、scripts/gitee-pr-helper.sh、.githooks/pre-push、scripts/pre-push-gate.sh，统一失败关闭，删除 ref 放行；更新 scripts/test/gitee-pr-helper-contract.sh 的离线凭证前置。
- [x] T4：修改 AGENTS.md、scripts/README.md、docs/references/engineering-discipline.md、.wiki/pages/agent-sop-quickref.md、.wiki/log.md。
- [x] T5：清理限定本次文件；重测、独立 Google/西域全 diff 审查与一致性分析。

## 验证与取舍
python3 -m unittest discover -s scripts/test -p test_review_workflow.py -v；bash -n 四入口；git diff --check；文档与锁门禁。测试使用真实 Git 和协议型子进程，不访问业务服务。只改用户级检查器不能随仓库分发；仅匹配技能名称不能证明执行。使用默认 Codex 及显式受信任适配协议，每次独立只读审查；越界立即停止。

## 实际回归发现
Google 第一轮阻断纯删除 push 的二次凭证复检，自动修复并补完整 hook 回归；真实 Git push 补查发现 HEAD 源表达式被误拒，补目标分支/SHA 校验及标签兼容回归。最终验收以 worktree 私有 review-gate/state.json 的完整通过轮次为准，不能以中间审查替代。

fix 第 1/3 轮：重命名检测使旧路径删除未进入指纹；路径收集改为 `--no-renames`。两项无需监听端口的真实 Git 回归（恢复旧路径失效、移动暂存/提交指纹稳定）均先 RED 后 GREEN。本阶段未嵌套运行 run/check；完整流程验证由主执行器执行。

fix 第 2/3 轮：提取验证执行与逐条持久化、单阶段调度、轮次调度和失败归档；后续验证命令超时/无法启动时保留已完成 argv/exit_code 与当前命令错误。新增 4 项无需监听端口的直接函数回归通过，未嵌套运行 run/check；全量 test_review_workflow.py 和监听端口契约测试由主执行器 verify 阶段执行。

## 限定收尾
按用户要求不再扩大实现，仅将 check 拆为单 ref、受保护来源、PR head 三个检查函数；保持既有验证语义和34项回归。完整验收与可交付状态由本次收尾的三技能运行记录判定；只有 MERGE_READY 才提交和合入。

fix 第 1/3 轮（校验职责）：将报告结构、finding 字段与阻断策略，以及凭证身份、阶段顺序、工件完整性和最终报告检查分别提取；保留原校验顺序、错误信息与失败关闭语义。新增 2 项直接函数行为保护测试在拆分前后均通过，既有 4 项验证证据函数测试通过；AST 校验各校验函数退出分支不超过 5。未嵌套运行 run/check、未写交付凭证；全量流程回归及监听端口契约测试由主执行器 verify 阶段执行。

总门禁兼容修复：同步 scripts/check-pre-push-dispatch.test.mjs 的参数策略截取边界；init 锚点仅允许 origin/main SHA 的同步，拒绝新代码借基准分支绕过审查。其余实现和文件范围不变。
