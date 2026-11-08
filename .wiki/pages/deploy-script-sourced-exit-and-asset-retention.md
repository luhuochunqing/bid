---
title: 部署脚本两类陷阱：sourced shell 的 exit 与前端 hash 资产保留
space: engineering
category: lessons
tags: [部署, shell, session-gate, 早操, sync-env, assets, hash, Sentry, 404]
created: 2026-11-08
updated: 2026-11-08
health_checked: 2026-11-08
sources:
  - scripts/session-gate.sh
  - scripts/dev-env.sh
  - scripts/sync-env.sh
  - scripts/release/remote-deploy.sh
  - docs/release/deploy-report-2026-11-07-261st-test.md
backlinks:
  - _index
  - gate-command-scope-pitfalls
---
# 部署脚本两类陷阱：sourced shell 的 exit 与前端 hash 资产保留

> **一句话**：被 `source` 的脚本不能用 `exit` 表达失败；前端整体替换资产目录会删掉旧 hash，必须显式保留最近 N 版。

两条都是第 261 次测试环境部署（`b08b1760c2`）暴露的，修复分别在 !2978 / !2979。

## 陷阱 1：被 `source` 的脚本用 `exit` 会杀死调用方 shell

| 项 | 内容 |
|---|---|
| 触发脚本 | `scripts/session-gate.sh`（由 `dev-env.sh`、`session-gate-wrapper.sh` source） |
| 现象 | 锚点分支 `agent/trae-init` 上执行 `source scripts/dev-env.sh && bash scripts/sync-env.sh .`，**sync-env 完全不执行**，终端直接退出 |
| 根因 | `session_gate` 判定失败后执行 `exit 1`。`exit` 作用于整个进程，不是脚本自身 ⇒ 杀掉调用方 shell，`&&` 链被截断 |
| 误判 | 表面像"guard 拒绝 sync-env"，实际是 `source` 这一环先中断；`sync-env.sh` 内部早已设置 `XYYU_SYNC_ENV_ACTIVE=1` 豁免锚点拦截，只是从未被执行到 |

**修复约定**：按调用方式分流。

```bash
if ! session_gate; then
  if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then exit 1; fi   # 直接执行：门禁失败
  return 1                                                # 被 source：失败语义保留，不杀 shell
fi
```

**判定规则**（写任何被 `source` 的脚本都用得上）：
- `${BASH_SOURCE[0]}` 是脚本自身路径，`$0` 是调用方 → 两者相等即"直接执行"
- 被 source 时的失败只能用 `return N`；`exit` 无论 N 是多少都会终止调用方
- 失败返回值仍要保留（`source dev-env.sh && npm run dev` 仍应被拦），不能改成 `|| true` 吞掉

**影响面**：
- 锚点分支早操恢复正常，`sync-env.sh` 的 ff-only 同步可执行
- `deploy-test.sh` / `deploy-prod.sh` 中 `source dev-env.sh || true` 的兜底重新生效
- 历史上第 222 / 224 / 235 次测试部署、第 35 次生产部署都用 `SKIP_SESSION_GATE=1` / `CHAT_ONLY=1` 逃生阀绕过，本次是根因修复，逃生阀不再是必需路径

## 陷阱 2：前端整体替换资产目录会删除旧 hash → 404 + Sentry 噪声

| 项 | 内容 |
|---|---|
| 触发脚本 | `scripts/release/remote-deploy.sh`（`rm -rf $FRONTEND_PUBLIC_DIR && mv pending`） |
| 现象 | 部署后部分用户报页面样式丢失；Nginx 对 `assets/*.css` 返回 404；Sentry 出现 `Unable to preload CSS` |
| 根因 | Vite 产物文件名带内容 hash。整体替换目录只保留新版本 hash；未刷新旧标签页的 `<link rel="preload">` 指向已删除文件 |
| 证据 | 第 261 次部署后手工补拷 `0d833ae84`、`82b9dbe75`、`3291a9015` 三个版本 assets 才消除 404 |

**保留策略（!2979 实现）**：

1. **公开目录是权威来源**：激活前先把当前 `/srv/www/xiyu-bid/assets` 合并进 pending。覆盖上线前手工补齐的更早 hash。
2. **历史目录只接纳已激活版本**：用 `.frontend-activated` 标记筛最近 `ASSETS_RETAIN_RELEASES`（默认 2）个 release。标记只在公开目录真实切换后写入。
3. **`cp -rn` 补拷**：只补回缺失的旧 hash，不覆盖新版本同名文件。
4. **失败即中止**：任一复制失败立即退出，不切公开目录、不重启后端。

**为什么不用 mtime 排序**：按目录 mtime 取"最近 N 个"会选中预检失败、从未对外提供服务的候选，挤掉真正需要保留的版本。

**已知边界**：
- 首次接入时历史 release 都没有 `.frontend-activated`，只靠公开目录合并兜底
- 既不在公开目录、也不在标记历史范围内的资源无法恢复
- 目录删除再移动的短暂切换间隙仍存在，未改为原子 rename

## 可复用验证方法

```bash
# 陷阱 1：source 后 shell 是否存活
bash -c 'source scripts/dev-env.sh; echo "AFTER rc=$?"'    # 必须打印 AFTER

# 陷阱 1：直接执行仍须拦
bash scripts/session-gate.sh; echo "rc=$?"                  # 锚点分支应为 1

# 陷阱 2：构造 a/b 已激活 + fail 未激活 + 公开目录独有旧文件
# 期望：a/b 与旧文件进入公开目录，fail 不进入，新版本同名文件未被覆盖
```

## 副作用（Cross-Module Impact）

- 修改 `session-gate.sh` / `remote-deploy.sh` 都改变提交范围 ⇒ 作废既有评审凭证，需重跑三技能门禁
- `remote-deploy.sh` 改动**尚未上服务器**，下一次部署才首次执行新逻辑
- 相关页：[[gate-command-scope-pitfalls]]（门禁命令作用域与 diff_hash 口径）、[[deployment]]（部署总体流程）
