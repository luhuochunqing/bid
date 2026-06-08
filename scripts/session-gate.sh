#!/usr/bin/env bash
# Input: current repo root (ROOT_DIR), current git branch, optional CHAT_ONLY env
# Output: exit 1 if blocked (anchor branch / session conflict / rebase failure); 0 otherwise
#   - 锚点分支 => 拒绝，提示用 '早操SOP + 开个分支 <任务名>' 切开发分支
#   - session 互斥 => 拒绝（同 worktree 只能一个开发 session）
#   - 自动同步: 非聊天模式下自动 git fetch + rebase origin/main
#   - rebase 后检查锚点 + Flyway 版本自愈
#   - 锁释放时：
#     * 提醒未推送 commit + 自动 push
#     * 清理远端已删除的残留本地分支
# Pos: scripts/多 Agent Session 互斥门禁 — worktree 独占 + 锚点阻断 + 自动同步
# 维护声明: 本脚本被 dev-env.sh source 加载，也被环境初始化时独立引用。
#           新增锚点分支模式时请同步 case 分支。
# 不设 set -e，因为是被 source 的（调用方负责错误处理）

SESSION_LOCK=".session-active"
ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

session_gate() {
  # 守护进程/定时任务模式跳过全部 session 门禁
  if [[ "${WATCHDOG_MODE:-}" == "1" || "${SKIP_SESSION_GATE:-}" == "1" ]]; then
    return 0
  fi
  local lockfile="$ROOT_DIR/$SESSION_LOCK"
  local branch
  branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null)" || branch="detached"

  # ── 聊天模式：跳过一切门禁，只写个标记锁 ──
  if [[ -n "${CHAT_ONLY:-}" && "${CHAT_ONLY:-}" == "1" ]]; then
    echo "CHAT_$$" > "$lockfile"
    echo "mode=chat" >> "$lockfile"
    echo "ts=$(date +%s)" >> "$lockfile"
    echo "session-gate: 聊天模式，跳过门禁"
    return 0
  fi

  # ── 规则 1：锚点分支禁止开发 ──
  # Exceptions:
  #   - XYYU_SYNC_ENV_ACTIVE=1: sync-env.sh maintenance on anchor (fetch/ff-only, not development)
  #   - XIYU_DEV_CONFIRMED=1: explicitly authorized development override
  if [[ "${XYYU_SYNC_ENV_ACTIVE:-}" != "1" && "${XIYU_DEV_CONFIRMED:-}" != "1" ]]; then
    case "$branch" in
      main|master|integrate/*|agent/*-init)
        echo ""
        echo "╔══════════════════════════════════════════════════════════════╗"
        echo "║  ❌ 当前在锚点分支 '$branch'，禁止直接开发！                ║"
        echo "║                                                            ║"
        echo "║   ⚡ 请对 Agent 说：                                        ║"
        echo "║       '早操SOP + 开个分支 <任务名>'                         ║"
        echo "║                                                            ║"
        echo "║     或手动执行：                                            ║"
        echo "║       export XIYU_DEV_CONFIRMED=1                          ║"
        echo "║       bash scripts/agent-start-task.sh <agent> <task> --in-place"
        echo "╚══════════════════════════════════════════════════════════════╝"
        echo ""
        return 1
        ;;
    esac
  fi

  # ── 规则 2：已有活跃 session（非聊天） ──
  if [[ -f "$lockfile" ]]; then
    local old_pid old_branch old_mode
    old_pid=$(head -1 "$lockfile" 2>/dev/null || echo "")
    old_branch=$(sed -n 's/^branch=//p' "$lockfile" 2>/dev/null || echo "unknown")
    old_mode=$(sed -n 's/^mode=//p' "$lockfile" 2>/dev/null || echo "")

    if [[ -n "$old_pid" ]] && kill -0 "$old_pid" 2>/dev/null; then
      # 兼容旧锁文件（无 mode 字段）
      [[ -z "$old_mode" ]] && old_mode="开发"

      echo ""
      echo "╔══════════════════════════════════════════════════════════════╗"
      echo "║  ❌ 此 worktree 已被占用！                                  ║"
      echo "║     PID: $old_pid  分支: $old_branch  模式: $old_mode       ║"
      echo "║                                                            ║"
      echo "║   ⚡ 选择一：前往其他 worktree 开 session                    ║"
      echo "║     ls /Users/user/xiyu/worktrees/                         ║"
      echo "║                                                            ║"
      echo "║   ⚡ 选择二：如果只是聊天，设 CHAT_ONLY=1：                 ║"
      echo "║       CHAT_ONLY=1 source scripts/dev-env.sh                ║"
      echo "╚══════════════════════════════════════════════════════════════╝"
      echo ""
      return 1
    else
      echo "session-gate: 检测到僵尸锁（PID $old_pid 已退出），自动接管"
    fi
  fi

  # ── 规则 3：自动同步（git fetch + rebase origin/main） ──
  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  🔄 自动同步: git fetch + rebase origin/main ...            ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""

  # stash 未提交变更
  local stashed=0
  if ! git diff --quiet --ignore-submodules HEAD 2>/dev/null; then
    echo "session-gate: 有未提交变更，先 git stash"
    git stash push -m "session-gate auto-stash $(date '+%Y%m%d%H%M%S')" 2>/dev/null || true
    stashed=1
  fi

  # fetch + rebase
  if git fetch origin main --depth=50 2>/dev/null; then
    local behind_count
    behind_count=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo "0")
    if [[ "$behind_count" -gt 0 ]]; then
      echo "session-gate: 落后 origin/main ${behind_count} 个 commit，执行 rebase..."
      if git rebase origin/main 2>/dev/null; then
        echo "session-gate: ✅ rebase origin/main 成功"
      else
        echo ""
        echo "╔══════════════════════════════════════════════════════════════╗"
        echo "║  ❌ rebase 冲突，请手动解决！                               ║"
        echo "║     git status 查看冲突文件                                  ║"
        echo "║     解决后 git rebase --continue                             ║"
        echo "╚══════════════════════════════════════════════════════════════╝"
        echo ""
        return 1
      fi
    else
      echo "session-gate: 已是最新，无需 rebase"
    fi
  else
    echo "session-gate: 无法 fetch origin/main（可能离线），跳过同步"
  fi

  # ── 恢复 stash ──
  if [[ "$stashed" -eq 1 ]]; then
    local stash_entry
    stash_entry=$(git stash list 2>/dev/null | grep "session-gate auto-stash" | head -1 | cut -d: -f1 || true)
    if [[ -n "$stash_entry" ]]; then
      echo "session-gate: 恢复 stash ($stash_entry)"
      git stash pop 2>/dev/null || echo "session-gate: ⚠ stash pop 失败（可能有冲突），请手动 git stash pop"
    fi
  fi

  # ── 规则 4：rebase 后检查 Flyway 迁移版本冲突，自动重编号 ──
  if [[ -x "$ROOT_DIR/scripts/assign-flyway-version.sh" ]]; then
    echo "session-gate: 检查 Flyway 迁移版本..."
    bash "$ROOT_DIR/scripts/assign-flyway-version.sh" 2>/dev/null || true
  fi

  # ── 规则 5：rebase 后如果落到锚点分支，重新拦截 ──
  # Exception: XYYU_SYNC_ENV_ACTIVE=1 (sync-env maintenance) skips this block too
  branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null)" || branch="detached"
  if [[ "${XYYU_SYNC_ENV_ACTIVE:-}" != "1" ]]; then
    case "$branch" in
      main|master|integrate/*|agent/*-init)
        echo ""
        echo "╔══════════════════════════════════════════════════════════════╗"
        echo "║  ❌ rebase 后仍在锚点分支 '$branch'！                       ║"
        echo "║     当前 worktree 不可用于开发。                             ║"
        echo "║                                                            ║"
        echo "║   ⚡ 请对 Agent 说：                                        ║"
        echo "║       '开个分支 <任务名>'                                    ║"
        echo "╚══════════════════════════════════════════════════════════════╝"
        echo ""
        return 1
        ;;
    esac
  fi

  echo ""
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  ✅ 门禁通过，可以开始开发！                                 ║"
  echo "║     分支: $branch                                           ║"
  echo "║     Worktree: $(basename "$ROOT_DIR")                        ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo ""

  # 写入锁
  echo "$$" > "$lockfile"
  echo "branch=$branch" >> "$lockfile"
  echo "worktree=$(basename "$ROOT_DIR")" >> "$lockfile"
  echo "mode=develop" >> "$lockfile"
  echo "ts=$(date +%s)" >> "$lockfile"
}

# ── 锁释放时：清理锁 + 提醒 + 收尾清理 ──
release_session_lock() {
  local lockfile="$ROOT_DIR/$SESSION_LOCK"
  if [[ ! -f "$lockfile" ]]; then
    return 0
  fi

  local my_pid old_mode
  my_pid=$(head -1 "$lockfile" 2>/dev/null || echo "")
  old_mode=$(sed -n 's/^mode=//p' "$lockfile" 2>/dev/null || echo "")

  # 只释放自己的锁
  if [[ "$my_pid" != "$$" && "$my_pid" != "CHAT_$$" ]]; then
    return 0
  fi

  rm -f "$lockfile"

  # 聊天模式不提醒
  if [[ "$old_mode" == "chat" ]]; then
    echo "session-gate: 锁已释放"
    return 0
  fi

  # ── 检查未推送的 commit ──
  local unpushed_count pr_exists
  unpushed_count=$(git rev-list --count origin/main..HEAD 2>/dev/null || echo "0")
  pr_exists=$(git ls-remote origin "refs/heads/$(git symbolic-ref --short HEAD 2>/dev/null)" 2>/dev/null | wc -l | tr -d ' ')

  if [[ "$unpushed_count" -gt 0 ]]; then
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  ⏰ 你有 ${unpushed_count} 个未推送的 commit！                  ║"

    if [[ "$pr_exists" -eq 0 ]]; then
      echo "║  远端还没有这个分支，说明 PR 还没提。                        ║"
      echo "║                                                            ║"
      echo "║  ⚡ 请执行：                                                ║"
      echo "║     1. git push                                            ║"
      echo "║     2. 对 Agent 说「提个 PR」                               ║"
    else
      echo "║  远端已有此分支，说明已 push 但可能是忘了提 PR。             ║"
      echo "║                                                            ║"
      echo "║  ⚡ 对 Agent 说「提个 PR」创建 PR                           ║"
    fi

    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""

    if [[ "$pr_exists" -eq 0 ]]; then
      echo "session-gate: 自动 push 中..."
      git push origin HEAD:"$(git symbolic-ref --short HEAD 2>/dev/null)" 2>/dev/null || true
      echo ""
    fi
  fi

  # ── 清理远端已删除的残留本地分支 ──
  local stale_branches
  stale_branches=$(git branch -vv 2>/dev/null | grep ': gone]' | awk '{print $1}' || true)
  if [[ -n "$stale_branches" ]]; then
    local count
    count=$(echo "$stale_branches" | wc -l | tr -d ' ')
    echo ""
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  🧹 发现 ${count} 个远端已删除的残留本地分支                    ║"
    echo "║                                                            ║"
    echo "$stale_branches" | while read -r b; do
      printf "║     • %-60s  ║\n" "$b"
    done
    echo "║                                                            ║"
    echo "║  自动删除中...                                              ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo ""
    echo "$stale_branches" | while read -r b; do
      [[ -n "$b" ]] && git branch -D "$b" 2>/dev/null && echo "  ✅ 已删除: $b" || true
    done
    echo ""
  fi

  echo "session-gate: 锁已释放"
}

# 主入口
if ! session_gate; then
  exit 1
fi
trap release_session_lock EXIT TERM INT
