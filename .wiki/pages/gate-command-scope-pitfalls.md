---
title: 门禁命令的"作用域"陷阱（检查了什么 ≠ 命令名说了什么）
space: engineering
category: lessons
tags: [门禁, doc-governance, line-budget, 评审凭证, diff_hash, pre-push, wiki]
created: 2026-09-22
updated: 2026-09-22
health_checked: 2026-09-22
sources:
  - scripts/check-doc-governance.mjs
  - scripts/lib/doc-governance-checker.mjs
  - scripts/check-line-budgets.mjs
  - scripts/pre-push-gate.sh
  - .wiki/WIKI.md
backlinks:
  - _index
  - project-list-server-side-paging
---
# 门禁命令的"作用域"陷阱（检查了什么 ≠ 命令名说了什么）

> **一句话**：本项目多道门禁是**同一命令的不同 flag 走不同代码路径**，"跑过了"三个字不足以当证据——
> 必须记下**当时的形式**（带不带 flag、工作树脏不脏、在哪个 worktree 里跑），否则会出现
> "门禁通过"与"门禁根本没看这块"同时为真。以下三条都在项目列表服务端分页交付时实际踩过。

## 陷阱 1：`check:doc-governance` 默认模式**不扫 `backend/`**

| 形式 | 覆盖范围 | 谁在跑 |
|---|---|---|
| `npm run check:doc-governance`（默认） | 只 `walk('src')`、`walk('scripts')`（`scripts/check-doc-governance.mjs:200`）| 手动 / `npm run build` |
| `node scripts/check-doc-governance.mjs --staged` | **暂存区**里受治理文件的头注释，含 `backend/**/{controller,service,config}/**.java` | `pre-commit` 钩子 |

- 后果：后端 java 文件**头注释缺失**（缺 `Input:` / `Output:` / `Pos:` / 维护声明）在默认模式下**永远不报**。
- 这条规则**没有豁免 / 祖父条款**（`scripts/lib/doc-governance-checker.mjs` 无 marker、无白名单），
  且 `const isJavaFile = false` 写死：java 文件扫到 `package` 行即停。
- **实践含义**：任何**存量**受治理 java 文件只要自创建起就没写这段头，**第一次被改动**时就会被
  pre-commit 拦下——拦的是一个"存量缺口"，不是本次引入的缺陷。补头是**纯注释变更**，
  但它会改 `diff_hash` ⇒ 按铁律要重跑验证并重评（见陷阱 3）。

## 陷阱 2：`check:line-budgets` 的裸调用在**干净工作树**上等于没检查

| 形式 | 口径 | 干净工作树下的输出 |
|---|---|---|
| `npm run check:line-budgets`（无 flag） | `workingTree=true` ⇒ `git diff HEAD`（**未提交**改动）| `guarded_changes=0` ← **一个文件都没检查** |
| `node scripts/check-line-budgets.mjs --base <merge-base>` | `git diff <base>..HEAD` | `guarded_changes=N`（本次为 5）|
| `--staged` | 暂存区 | pre-commit 链内 |

- `pre-push-gate.sh` 走的是 `--base` 口径（`GATE_BASE = merge-base origin/main HEAD`）。
- **判据**（`classifyBudgetChange`）：原 ≤ 300 行、新 > 300 ⇒ `CROSSED_LIMIT`（红）；
  本来就 > 300 且继续增长 ⇒ `GREW_WHILE_OVER_LIMIT`。`excludeFiles` 是历史遗留文件的**祖父条款**，
  新代码超预算的正确出路是**拆组件**（抽同包 Support / Service 类），不是往白名单里加。
- **实践含义**：交付态（已提交、工作树干净）报 `guarded_changes=0` 时，**不要**把"line-budget 通过"
  写成证据——它什么都没看。

## 陷阱 3：S6 评审凭证的 `diff_hash` 有两种口径，且**依赖调用位置**

| 谁 | 算的是什么 |
|---|---|
| 评审工件惯例 | **被评审的工作树**内容（`git diff`，含未提交改动）|
| `--guard-pr` → `compute_diff_hash(project_dir)` | `git diff <merge-base(origin/main,HEAD)>..HEAD`，**只含已提交内容**，且**在 `project_dir` 里跑 git** |

- 在**同一个 worktree** 里两种口径可以同时满足；跨 worktree（评审的是工作树、门禁读的是另一条
  分支的提交范围）则**数学上不可能**同时满足 ⇒ 会出现"评审结论有效，但门禁判凭证失效"。
- **合规解法（不要伪造 hash、不要开逃生阀）**：**把被评审内容放到一条独立分支上**，
  让"已提交范围"与"被评审内容"成为同一份东西。可先用**临时索引**在不写任何 ref 的前提下证明同一性：
  `GIT_INDEX_FILE=/tmp/idx git read-tree <base>` → `git add <paths>` → `git write-tree`
  → `git diff <base>..<tree> | shasum -a 256`，与评审工件里的 `diff_hash` 逐字节比对。
- 配套纪律：`--guard-pr` 必须在**那个 PR worktree 内**执行，否则同一函数会算回别的值。
- 注意：`.wiki/**` 也在提交范围内 ⇒ **回填 wiki 也会改 hash**，同样触发重评（见陷阱 4）。

## 陷阱 4：`pre-push` 的 Wiki 健康检查会因"有代码变更但无 `.wiki/` 变更"直接红

- `scripts/pre-push-gate.sh` 的 §16 用 `git diff --name-only "$GATE_BASE"..HEAD` 分别判
  `CODE_CHANGED` 与 `WIKI_CHANGED`；自 2026-08-06 起默认模式为 **`error`**（不再是 warning）。
- 触发的正当处置是**真回填**（`.wiki/pages/<topic>.md` + `_index.md` + `log.md`），
  不是 `WIKI_CHECK_MODE=warning`（需 PR 里给出理由）也不是 `WIKI_CHECK_SKIP=1`（仅生产事故抢修）。
- **报价**：一次回填 = 新增页面 + 索引行 + 日志条目 ⇒ **改提交范围 ⇒ 改 `diff_hash` ⇒ 多一轮独立评审**。
  wiki 回填不是"顺手补个文档"，它按铁律要付一轮评审成本，排期时要算进去。

## 副作用（Cross-Module Impact）

- 这四条都会**改 hash / 改提交范围**，因而与"任何源码（含纯注释）变更都作废既有验证与评审结论"
  叠加：补一条头注释也能让 130 条绿测试与 `MERGE_READY` **同时失效**，代价是重跑 + 重评。
- 与之相对的两个逃生阀**都不是**这种场景的正当出口：`--no-verify`（系统级禁止，`scripts/git`
  包装器直接拒绝）、`UNIFIED_DEV_FLOW_SKIP_REVIEW=1`（仅限生产事故抢修）；`WIKI_CHECK_SKIP=1` 同理。
- 相关页：[[project-list-server-side-paging]]（这些坑正是在它交付时踩出来的）。
