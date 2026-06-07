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
if [[ -f "$(dirname "${BASH_SOURCE[0]}")/dev-env.sh" ]]; then
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

    # init 分支：fetch 最新但不自动 rebase
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
          info "main-forward: init branch is $behind commits behind origin/main"
          info "main-forward:   init 分支建议定期同步以获取最新脚本和配置"
          info "main-forward:   手动同步命令:"
          info "main-forward:     git fetch origin main"
          info "main-forward:     git merge origin/main --ff-only"
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

# Step 1: sync env files
info "--- env file sync ---"
sync_env_files "$ROOT_DIR" "$TARGET_DIR"
echo

# Step 2: main-forward rebase
info "--- main-forward ---"
main_forward "$TARGET_DIR"
echo


info "=== sync-env done ==="
