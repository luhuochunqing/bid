# Git 陷阱（gotchas）

记录 Gitee/GitHub 协作中的 git 命令陷阱与正确做法。

---

## 1. squash merge 后 `git branch -d` 判"未完全合并"失败（spec 047 / 2026-08-18）

### 问题

PR 在 Gitee 上以 **squash merge** 合入 `main` 后，本地任务分支执行删除失败：

```bash
# ❌ 报错：分支未完全合并
git branch -d agent/trae/047-fix-score-weight-gap
error: The branch 'agent/trae/047-fix-score-weight-gap' is not fully merged.
```

同时被清理的另一个分支 `agent/trae/047-score-weight-convergence` 因为 `--dry-run` 阶段没走到、远端未自动清理，残留成死引用。

### 根因

- **squash merge 线性化 history**：PR 的全部 commit 被 `git squash` 压缩成 main 上的**单个 commit**，commit hash 与分支原 commit 不重叠。
- `git branch -d` 只做"该分支每个 commit 是否被 main 可达"的祖先判断。squash 后分支上的 commit 在 main 的提交图中**不再直接可见**，git 据此判定"未完全合并"，拒绝删除。
- 这是 git 的**保守安全机制**，不是错误，但会误导你以为变更没合进去。

### 危害

1. 本地任务分支堆积删不掉，`git branch --merged main | grep agent/` 失效（squash 分支不会被列进 merged）。
2. 远端分支若未手动清理，Gitee 上残留死引用，污染分支列表与 `who-touches.sh` 检测。
3. 误判为"PR 没合上"而重复排查。

### 正确做法

**方式 1（推荐）：合并前确认内容已入 main，再用 `-D` 强删本地分支**

```bash
# ① 确认 PR 的改动确实已在 main（GitHub/Gitee 的 squash 合并按钮点了就是合了）
git branch --contains <merge-commit-sha> main   # 或直接 git log main -1 --oneline 看到合并点

# ② 本地分支强删：-d 会因 squash 判未合并，改用 -D
git branch -D agent/trae/047-fix-score-weight-gap
```

**方式 2：远端分支必须手动清理（不会自动删）**

```bash
git push origin --delete agent/trae/047-score-weight-convergence
```

**方式 3：优先用 `scripts/agent-finish-task.sh --include-remote` 收尾**

脚本内部做好三重合入检查 + 本地/远端同步清理，`--dry-run` 预览后再执行，避免手动步骤遗漏。

### 通用规则

1. **squash merge 的分支删除一律用 `git branch -D`**：`-d` 的"未完全合并"判断对 squash 分支不成立，不是内容没合进去。
2. **先确认后强删**：删前用 `git log origin/main --oneline` 或 Gitee PR 状态确认改动已合入，再 `-D`，绝不凭感觉删。
3. **PR 合入 ≠ 分支清理完成**：远端分支不会自动删，必须 `git push origin --delete` 或 `agent-finish-task.sh --include-remote`。
4. **收尾流程顺序别乱**：合并 PR → 清理本地+远端分支 → 回锚点分支 → 知识沉淀；远程分支清理是最容易漏的一环。

### 相关文档

- [AGENTS.md §收尾流程](../../AGENTS.md) — 主路径 `agent-finish-task.sh`，手动 fallback 五步
- [lessons-learned.md §stale 分支清理](./lessons-learned.md#L627) — 区分任务分支（可删）与 bootstrap 种子分支（禁止删 `-init`）