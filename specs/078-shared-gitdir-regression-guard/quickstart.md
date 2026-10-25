# 验证指南

## 自动回归

```bash
bash scripts/check-worktree-protection.test.sh
```

预期：linked worktree 正例通过，受管目录内的独立 clone 被拒绝。

## 当前仓库拓扑

```bash
bash scripts/check-worktree-protection.sh
```

预期：所有受管持久 worktree 的 canonical common gitdir 与主仓库一致。
