---
title: Gitee token 多源可见性与统一解析链（Agent 提 PR 找不到 token 的根因与唯一真相源规范）
space: engineering
category: guide
tags: [gitee, token, PAT, 凭证, 环境变量, zshenv, git-credentials, PR, agent]
created: 2026-09-03
updated: 2026-09-03
health_checked: 2026-09-05
sources:
  - scripts/gitee-token.sh
  - scripts/gitee-pr-helper.sh
  - scripts/pr-create.sh
  - scripts/agent-finish-task.sh
  - scripts/check-branch-hygiene.sh
  - scripts/gitee-auto-merge-gate.sh
backlinks:
  - _index
---
# Gitee token 多源可见性与统一解析链

## 现象
远端用 Gitee、token 放本地环境变量，但各 Agent 完成任务提 PR 时反复"找不到 GITEE_TOKEN"或 401，同机不同时刻结局不同，困扰数月。

## 根因（2026-09-03 实测定案，lessons §215）
**token 可见性取决于谁启动进程树的 shell 类型，与仓库无关：**
- `~/.zshenv` 只有 zsh 读；`~/.zshrc` 仅交互式 zsh 读；**bash 不读任何 zsh rc**；GUI/launchd 全都不读
- 交互终端启动的 agent 有 token；bash 子进程 / GUI / 清理过 env 的 harness 全没有
- 旧 `gitee-token.sh` 把 `git credential fill` 排第一，而 `~/.git-credentials` 积累多条历史 PAT、第一条是**过期 401 死 token** → 兜底工具自己兜回死底
- 5 条 Gitee API 链路防线不一致：主路径零兜底、两处静默跳过（把鉴权失败伪装成"无 PR"）

## 规范：唯一真相源 + 统一解析链（PR !2521 落地）
- **唯一真相源**：`~/.config/xiyu/credentials.env`（chmod 600），`~/.zshenv`/`~/.zshrc` 只留一行 `[ -f "$HOME/.config/xiyu/credentials.env" ] && . "$HOME/.config/xiyu/credentials.env"`
- **解析链优先级**（`scripts/gitee-token.sh`）：env → credentials.env → ~/.zshenv grep（bash 兼容）→ git credential fill（末位 + `GIT_TERMINAL_PROMPT=0` 防挂死）
- **接线**：gitee-pr-helper / pr-create / agent-finish-task / check-branch-hygiene / gitee-auto-merge-gate 五脚本统一在 token 为空时自动走解析链；静默跳过改显式提示 + 修复路径
- 调试：`GITEE_TOKEN_DEBUG=1` 打印命中来源（不打印 token 值）
- 测试：`scripts/test/gitee-token-contract.sh`（假 HOME 隔离，14 断言）

## 陷阱清单
- **同一 token 分放多个 rc 文件会漂移**：实测 `.zshenv` 与 `.zshrc` 的 GITEE_TOKEN 是两个不同值、都有效——不同 shell 拿到的令牌都不一样，是"改了没效果"的深层来源
- **`git credential fill` 只回第一条匹配**：`~/.git-credentials` 积累过期条目后，fill 返回的是死 token；credential store 类来源必须排解析链最后
- **`>>` 追加命令重跑会累积重复行**：同值不同引号写法（`"x"` vs `x`）sort -u 也去不掉，要按变量去重
- **MCP `config.json` headers 是另一套 token 配置**：改 token 必须重启会话才生效（lessons §212）
- **远程是 SSH 时 push 不需要 token**：token 仅用于 Gitee API（PR 操作）
