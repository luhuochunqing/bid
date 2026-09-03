# fix-gitee-token 实现笔记（2026-09-03）

> 分支 `agent/zcode/fix-gitee-token`，根治"各 Agent 提 PR 反复找不到 GITEE_TOKEN"数月顽疾。
> 诊断证据与教训正文见 `docs/lessons/lessons-learned.md §215`，本文件记录实现时的规格外决策与权衡。

## 改动清单

| 文件 | 改动 |
|---|---|
| `scripts/gitee-token.sh` | 重写：四级解析链 env → `~/.config/xiyu/credentials.env` → `~/.zshenv` grep → `git credential fill`；`GITEE_TOKEN_DEBUG=1` 打印命中来源 |
| `scripts/gitee-pr-helper.sh` | `check_token()` 前自动兜底解析；401 文案指向解析链；header 环境变量/变更历史同步 |
| `scripts/pr-create.sh` | 删除内联 zshenv grep，改调统一解析器；错误框更新 |
| `scripts/agent-finish-task.sh` | 检查 C 前自动兜底；token 仍缺时显式 warn（原为静默跳过） |
| `scripts/check-branch-hygiene.sh` | Gitee 查询前自动兜底；无 token 提示给出修复路径 |
| `scripts/gitee-auto-merge-gate.sh` | 前置检查前自动兜底；跳过时说明原因与修复路径 |
| `scripts/test/gitee-token-contract.sh` | 新增：假 HOME 隔离，14 项断言 |

## 规格外决策与权衡

1. **env 排第一而不是 credentials.env**：`GITEE_TOKEN=xxx ./script` 是所有脚本 header 里承诺的用法（CI/临时注入场景），显式注入必须永远赢，否则临时令牌会被文件里的旧令牌覆盖。
2. **`git credential fill` 从第一位降到末位**：这是本次 bug 的直接根因——`~/.git-credentials` 积累 3 条历史条目，fill 只回第一条（实测 401 死 token）。权衡：keychain 里的有效凭证也要多走三级才被命中，但"拿到死 token 静默 401"比"多查两级文件"代价大得多。
3. **zshenv grep 保留为第 3 级而不是删掉**：老机器上 `~/.zshenv` 仍是现状配置，直接删兜底会让未迁移的宿主立刻断链。迁移（第 1 层）是用户 home 目录操作，不属本 PR，grep 是过渡期的兼容垫。
4. **`GIT_TERMINAL_PROMPT=0` + `GIT_ASKPASS=/bin/true`**：fill 在无凭证时可能交互式追问用户名/密码，agent 后台进程会挂死，必须显式禁用。
5. **check-branch-hygiene 里不重复调解析器做提示分支**：初稿在提示 elif 里重跑 gitee-token.sh，发现是死代码（上方已解析过、失败则必然再失败），收敛为三分支。
6. **不动 AGENTS.md 的 co-XXX 三步**：开场凭证自检逻辑本身仍然正确（token 缺失就停下），本次只改"脚本侧能不能自己找到 token"。等第 1 层（home 目录迁移）落地后，可再更新文档把 `~/.config/xiyu/credentials.env` 写成官方唯一真相源。
7. **home 目录两层（credentials.env 迁移 + 清理 `~/.git-credentials` 死条目）不进 PR**：属于用户机器配置而非仓库代码，PR 描述里附可直接执行的命令。

## 验证证据

- `bash -n` 全部 7 个改动文件语法通过
- 新契约 `gitee-token-contract.sh`：14/14 绿（4 级优先级、引号写法、调试输出、全空报错、helper 集成、401 文案）
- 存量回归 `gitee-pr-helper-contract.sh`：18/18 绿
- 实机验证（本机）：`~/.zshenv` token 打 API 200 有效；旧 `gitee-token.sh` 返回 401 死 token（bug 复现依据）
