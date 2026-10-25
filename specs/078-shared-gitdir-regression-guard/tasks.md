# Tasks: 共享 Gitdir 防复发门禁

## Phase 1: User Story 1 - 受管 worktree 拓扑一致性（P1）

- [x] T001 [US1] 在 `scripts/check-worktree-protection.test.sh` 构造 linked worktree 通过与独立 clone 失败场景
- [x] T002 [US1] 在 `scripts/check-worktree-protection.sh` 增加显式参数、目录枚举和 canonical common gitdir 比较
- [x] T003 [US1] 运行 `bash scripts/check-worktree-protection.test.sh` 完成 RED→GREEN

## Phase 2: 集成与验收

- [x] T004 在 `scripts/pre-push-gate.sh` 接入拓扑检查和独立回归
- [x] T005 在 `scripts/README.md` 记录脚本职责和用法
- [x] T006 运行 shell 自测、pre-push 调度测试和 `git diff --check`

## Dependencies

- T004 依赖 US1。
