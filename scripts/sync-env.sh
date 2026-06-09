#!/usr/bin/env bash
# Input: target worktree directory and root environment template files
# Output: copied environment files for the target worktree
# Pos: scripts/多 Agent 环境文件同步脚本
# 维护声明: 仅维护本地 worktree 环境文件同步；新增环境模板时请同步脚本目录说明。
#
# 功能:
#   1. 同步 .env.api 等环境模板文件到目标 worktree
#   2. 自动执行 main-forward rebase（将当前分支 rebase 到最新 origin/main）
#      - 仅对 agent worktree 内的非保护分支生效（main、*-init 等跳过）
#      - 对共享 worktree（如 main）的分支不做 rebase
#      - 有未提交变更时自动 stash → rebase → pop
#      - rebase 失败时保留 rebase 现场，给出手动解决指引
set -euo pipefail

# Activate project environment (ports + **critical**: the scripts/git wrapper that
# enforces the system-level ban on `git push --no-verify` / `git commit --no-verify`).
# This ensures all git operations performed by "早操" (main-forward rebase etc.)
# go through the safety wrapper even if the caller did not source dev-env first.
#
# SPECIAL: For anchor-branch maintenance (sync-env on agent/*-init), we set
# XYYU_SYNC_ENV_ACTIVE=1 so session-gate.sh skips the anchor-branch block
# (only maintenance git operations like fetch/ff-only are performed, not development).
if [[ -f "$(dirname "${BASH_SOURCE[0]}")/dev-env.sh" ]]; then
  export XYYU_SYNC_ENV_ACTIVE=1
  # shellcheck disable=SC1091
  source "$(dirname "${BASH_SOURCE[0]}")/dev-env.sh" || true
fi

# ─── helpers ─────────────────────────────────────────────────────────────────

warn()  { echo "sync-env: WARNING: $*" >&2; }
info()  { echo "sync-env: $*" ; }
die()   { echo "sync-env: ERROR: $*" >&2; exit 1; }

# ─── utility: is_agent_worktree ──────────────────────────────────────────────
# 判断目标目录是否为 agent 工作区（非 main 共享目录）。
# 在 agent 工作区内的任何非保护分支都应做 main-forward。
# 返回 0 表示是 agent worktree，1 表示不是。
is_agent_worktree() {
  local worktree_root="$1"

  # agent worktree 路径模式: /Users/user/xiyu/worktrees/<name>
  # 排除 agent 子目录: /Users/user/xiyu/worktrees/codex-project-task
  # 排除 .claude/worktrees/: /Users/user/xiyu/xiyu-bid-poc/.claude/worktrees/...
  case "$worktree_root" in
    /Users/user/xiyu/worktrees/[a-z]*)
      # top-level worktree dirs (claude, codex, cursor, gemini, integrator 等)
      # 仅 bootstrap worktree 或 旧版单名 agent worktree
      return 0
      ;;
    /Users/user/xiyu/worktrees/[a-z]*-[a-z]*)
      # 新版 agent-<task> task worktree
      return 0
      ;;
  esac

  # 非 worktrees/ 下的目录：main 共享仓库或其他
  return 1
}

# ─── main-forward rebase ─────────────────────────────────────────────────────

main_forward() {
  local worktree_root="$1"

  (
    # 进入目标 worktree（cd 到子 shell）
    cd "$worktree_root" || die "Cannot cd into $worktree_root"

    # 仅在 Git 仓库内生效
    if ! git rev-parse --is-inside-work-tree &>/dev/null; then
      info "main-forward: not a git repo, skipping rebase"
      return 0
    fi

    # 检测 detached HEAD
    local branch
    branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null)" || {
      warn "main-forward: detached HEAD detected, skipping rebase"
      return 0
    }

    # 保护分支不做自动 rebase
    #   - main/master: 基准分支，不应在 worktree 内修改
    #   - agent/*-init: bootstrap 锚点分支，fetch 最新代码但不自动 rebase（由 init-sync 接管）
    #   - integrate/baseline: 集成基线分支
    local is_init_branch=0
    case "$branch" in
      main|master|integrate/baseline|HEAD)
        info "main-forward: skipped (protected branch '$branch')"
        return 0
        ;;
      agent/*-init)
        is_init_branch=1
        info "main-forward: init branch detected, will fetch latest without auto-rebase"
        ;;
    esac

    # 决定是否对当前 worktree 启用 main-forward
    local worktree_name
    worktree_name="$(basename "$worktree_root")"

    if is_agent_worktree "$worktree_root"; then
      info "main-forward: agent worktree detected, checking branch '$branch'"
    else
      # 共享目录（main 仓库根目录等）跳过
      info "main-forward: skipped (shared worktree '$worktree_name', not an agent worktree)"
      return 0
    fi

    # 获取当前未提交的所有变更（staged + unstaged + untracked）
    local has_stash=0
    local stash_msg=""
    if ! git diff-index --quiet HEAD 2>/dev/null \
       || ! git diff --cached --quiet 2>/dev/null \
       || [[ -n "$(git status --porcelain 2>/dev/null)" ]]; then
      # 使用唯一 stash 名（含时间戳和 worktree 名）避免堆积
      local ts stash_name
      ts="$(date +%Y%m%d_%H%M%S 2>/dev/null || date +%s)"
      stash_name="main-forward/${worktree_name}/${ts}"
      stash_msg="main-forward pre-rebase stash for $worktree_name"
      info "main-forward: stashing uncommitted changes..."
      git stash push -m "$stash_msg" --include-untracked 2>/dev/null || {
        warn "main-forward: git stash failed (maybe nothing to stash), continuing"
      }
      has_stash=1
    fi

    # Fetch latest origin/main
    info "main-forward: fetching latest origin/main..."
    if ! git fetch origin main --prune 2>&1; then
      warn "main-forward: git fetch origin main failed"
      if (( has_stash )); then
        info "main-forward: restoring stashed changes..."
        git stash pop 2>/dev/null || warn "main-forward: stash pop failed — check stash manually"
      fi
      return 0
    fi

    # init 分支：fetch 最新并自动 ff-only 同步（无分歧时）
    if (( is_init_branch )); then
      local origin_main_sha local_head_sha
      origin_main_sha="$(git rev-parse "origin/main" 2>/dev/null)" || origin_main_sha=""
      local_head_sha="$(git rev-parse HEAD 2>/dev/null)" || local_head_sha=""

      if [[ "$origin_main_sha" == "$local_head_sha" ]]; then
        info "main-forward: init branch is up-to-date with origin/main"
      else
        local behind ahead
        behind=$(git rev-list --count "HEAD..origin/main" 2>/dev/null || echo "0")
        ahead=$(git rev-list --count "origin/main..HEAD" 2>/dev/null || echo "0")
        if [[ "$ahead" == "0" && "$behind" != "0" ]]; then
          info "main-forward: init branch is $behind commits behind origin/main, auto ff-only syncing"
          if git merge origin/main --ff-only 2>&1; then
            info "main-forward: init branch ff-only sync completed successfully"
            # 自动 push init 分支到远端，避免 unpushed 积累
            local tracking_branch
            tracking_branch="$(git rev-parse --abbrev-ref --symbolic-full-name @{upstream} 2>/dev/null)" || tracking_branch=""
            if [[ -n "$tracking_branch" ]]; then
              info "main-forward: pushing init branch to remote..."
              if PRE_PUSH_GATE=0 git push origin HEAD:"$(git rev-parse --abbrev-ref HEAD)" 2>&1; then
                info "main-forward: init branch pushed successfully"
              else
                warn "main-forward: failed to push init branch — you may need to push manually"
              fi
            else
              info "main-forward: no upstream tracking branch, skipping auto-push"
              info "main-forward:   手动推送: git push origin $(git rev-parse --abbrev-ref HEAD)"
            fi
          else
            warn "main-forward: ff-only sync failed — init branch may have diverged"
            warn "main-forward:   手动处理: git fetch && git merge origin/main"
          fi
        else
          info "main-forward: init branch has diverged from origin/main (behind=$behind, ahead=$ahead)"
          info "main-forward:   如需同步，建议手动处理: git fetch && git merge origin/main"
        fi
      fi

      if (( has_stash )); then
        info "main-forward: restoring stashed changes..."
        git stash pop 2>/dev/null || warn "main-forward: stash pop failed — check stash manually"
      fi
      return 0
    fi

    # 检查是否需要 rebase
    # 比较 origin/main 的当前值与 HEAD 的 merge-base
    # 如果 merge-base == origin/main，说明没有分歧，无需 rebase
    local origin_main_sha current_base
    origin_main_sha="$(git rev-parse "origin/main" 2>/dev/null)" || origin_main_sha=""
    current_base="$(git merge-base HEAD origin/main 2>/dev/null)" || current_base=""

    if [[ -z "$current_base" ]]; then
      warn "main-forward: cannot determine merge-base, skipping rebase"
      if (( has_stash )); then
        git stash pop 2>/dev/null || warn "main-forward: stash pop failed — check stash manually"
      fi
      return 0
    fi

    if [[ "$origin_main_sha" == "$current_base" ]]; then
      info "main-forward: already up-to-date with origin/main ($(git log -1 --oneline "$current_base" 2>/dev/null || echo "$current_base")), no rebase needed"
      if (( has_stash )); then
        info "main-forward: restoring stashed changes..."
        git stash pop 2>/dev/null || warn "main-forward: stash pop failed — check stash manually"
      fi
      return 0
    fi

    # 执行 rebase（不使用 --autosquash，避免非预期合并 commit）
    info "main-forward: rebasing '$branch' onto latest origin/main..."
    info "main-forward:   current base: $(git log -1 --oneline "$current_base" 2>/dev/null || echo "$current_base")"
    info "main-forward:   new base:    $(git log -1 --oneline "origin/main" 2>/dev/null || echo "$origin_main_sha")"

    if git rebase origin/main 2>&1; then
      info "main-forward: rebase completed successfully"
    else
      warn "main-forward: rebase failed with conflicts — rebase left in progress"
      warn "main-forward:   1. Resolve conflicts in editor"
      warn "main-forward:   2. git add . (stage resolved files)"
      warn "main-forward:   3. git rebase --continue (continue rebase)"
      warn "main-forward:   4. git stash pop (restore your changes)"
      warn "main-forward:   or: git rebase --abort (discard rebase, restore original state)"
      # rebase 失败时 stash 仍然存在，不需要 pop
      return 0
    fi

    # rebase 成功，恢复 stash
    if (( has_stash )); then
      info "main-forward: restoring stashed changes..."
      if git stash pop 2>&1; then
        info "main-forward: stash restored successfully"
      else
        warn "main-forward: stash pop had conflicts — resolve manually:"
        warn "main-forward:   git stash pop (in $worktree_root)"
      fi
    fi

    info "main-forward: done for '$branch'"
  )
}

# ─── env file sync ────────────────────────────────────────────────────────────

sync_env_files() {
  local root_dir="$1"
  local target_dir="$2"

  local FILES=(
    ".env.api"
  )

  for file in "${FILES[@]}"; do
    local source_file="$root_dir/$file"
    local target_file="$target_dir/$file"
    if [ -f "$source_file" ]; then
      if [ "$source_file" = "$target_file" ]; then
        info "sync-env: skipped $file; source and target are the same"
      else
        cp "$source_file" "$target_file"
        info "sync-env: copied $file → $target_dir"
      fi
    else
      warn "sync-env: $file not found in $root_dir, skipped"
    fi
  done
}

# ─── main ─────────────────────────────────────────────────────────────────────

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TARGET_DIR="$1"

if [ -z "$TARGET_DIR" ]; then
  echo "Usage: ./scripts/sync-env.sh <target-worktree-dir>"
  echo ""
  echo "What it does:"
  echo "  1. Copies env template files (.env.api) to the target worktree"
  echo "  2. main-forward rebase on agent worktree branches"
  echo "       agent worktrees: /Users/user/xiyu/worktrees/<name> or /Users/user/xiyu/worktrees/<name>-<task>"
  echo "       protected branches (main, agent/*-init, etc.) are skipped"
  echo "       non-agent worktrees (shared main repo, etc.) are skipped"
  echo ""
  echo "Examples:"
  echo "  ./scripts/sync-env.sh /Users/user/xiyu/worktrees/codex-project-page"
  echo "  ./scripts/sync-env.sh .        # current directory"
  exit 1
fi

# Resolve TARGET_DIR to absolute path
if [[ "$TARGET_DIR" != /* ]]; then
  if [ -d "$TARGET_DIR" ]; then
    TARGET_DIR="$(cd "$TARGET_DIR" && pwd)"
  else
    TARGET_DIR="$(cd "$ROOT_DIR" && cd "$TARGET_DIR" && pwd)" \
      || die "Cannot resolve target directory: $TARGET_DIR"
  fi
fi

info "=== sync-env ==="
info "Root:    $ROOT_DIR"
info "Target:  $TARGET_DIR"

echo
info "--- env file sync ---"
sync_env_files "$ROOT_DIR" "$TARGET_DIR"
echo

# Step 2: main-forward rebase
info "--- main-forward ---"
main_forward "$TARGET_DIR"
echo





# ─── 遗忘提醒 — 检查上次 session 是否有未推送 commit ──────────────────
forgetfulness_check() {
  local branch
  branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null)" || return 0

  # 只在 agent 的任务分支上提醒（跳过锚点分支）
  case "$branch" in
    agent/*-init|main|master) return 0 ;;
  esac

  # 计算未推送 commit 数
  local unpushed
  unpushed=$(git log --oneline "@{u}..HEAD" -- 2>/dev/null | wc -l | tr -d ' ') || unpushed=0

  if [[ "$unpushed" -eq 0 ]]; then
    return 0
  fi

  # 计算最近一次 commit 距今时间
  local last_commit_ts last_commit_age now
  last_commit_ts=$(git log -1 --format=%ct HEAD 2>/dev/null) || last_commit_ts=0
  now=$(date +%s 2>/dev/null || echo 0)
  last_commit_age=$(( now - last_commit_ts ))

  local ONE_HOUR=$((60 * 60))
  local ONE_DAY=$((24 * ONE_HOUR))

  # 1 小时内的新 commit 不催（可能还在开发中）
  if [[ "$last_commit_age" -le "$ONE_HOUR" ]]; then
    return 0
  fi

  local age_str
  if [[ "$last_commit_age" -gt "$ONE_DAY" ]]; then
    age_str="$(( last_commit_age / ONE_DAY )) 天"
  else
    age_str="$(( last_commit_age / ONE_HOUR )) 小时"
  fi

  echo
  echo "╔══════════════════════════════════════════════════════════════╗"
  echo "║  ⚠  你上次在 '$branch' 上有 $unpushed 个未推送的 commit  ║"
  echo "║     距今已有 $age_str，建议立即推送以避免冲突累积           ║"
  echo "║                                                          ║"
  echo "║     git push origin $branch                               ║"
  echo "╚══════════════════════════════════════════════════════════════╝"
  echo
}



# ─── 分叉警告 — 检查当前分支落后 origin/main 多少 ─────────────────
divergence_warning() {
  local branch
  branch="$(git symbolic-ref --quiet --short HEAD 2>/dev/null)" || return 0

  case "$branch" in
    agent/*-init|main|master) return 0 ;;
  esac

  # 计算落后 main 的 commit 数
  local behind
  behind=$(git rev-list --count "HEAD..origin/main" 2>/dev/null || echo "0")

  # 超过 5 个落后 commit 时给醒目警告
  if [[ "$behind" -ge 5 ]]; then
    local ahead
    ahead=$(git rev-list --count "origin/main..HEAD" 2>/dev/null || echo "0")
    echo
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  ⚠  当前分支落后 main $behind 个 commit                    ║"
    echo "║     你在前面有 $ahead 个自己的 commit                       ║"
    echo "║                                                          ║"
    echo "║     分叉越大，合并时冲突风险越高！                          ║"
    echo "║     建议尽快创建 PR 合并到 main，不要等开发完再合           ║"
    echo "║                                                          ║"
    echo "║     git fetch origin                                       ║"
    echo "║     git rebase origin/main                                 ║"
    echo "║     scripts/pr-create.sh "<type>(<scope>): <desc>" ...     ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo
  elif [[ "$behind" -ge 3 ]]; then
    echo
    echo "╔══════════════════════════════════════════════════════════════╗"
    echo "║  ⚠  当前分支落后 main $behind 个 commit                     ║"
    echo "║     分叉已开始扩大，建议考虑创建 PR                          ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    echo
  fi
}
forgetfulness_check
divergence_warning

# ── 本地门禁自检（嵌入早操，Agent 无需额外操作） ──
if [[ -f "$(dirname "${BASH_SOURCE[0]}")/check-local-gates.sh" ]]; then
  bash "$(dirname "${BASH_SOURCE[0]}")/check-local-gates.sh" 2>/dev/null && info "✅ 本地门禁就绪"
fi

info "=== sync-env done ==="
