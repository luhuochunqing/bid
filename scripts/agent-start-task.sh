#!/usr/bin/env bash
# Input: agent name, task slug, optional base ref, and optional initial lock paths
# Output: isolated worktree or in-place branch, task branch, local .agent-task-context, and optional .agent-locks.yml entries
# Pos: scripts/多 Agent 工作区初始化
# 维护声明: 若工作区根目录、分支前缀、任务上下文字段或文件锁参数变化，请同步更新 scripts/README.md。
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/agent-start-task.sh <agent> <task-slug> [base-ref] [options]

Options:
  --in-place             早操三连 + 在当前 worktree 内创建分支（自动 fetch + rebase + sync-env）
  --lock <path>          Acquire an initial file lock after worktree creation.
  --lock-dir <path>      Acquire an initial directory lock after worktree creation.
  --touch <path>         Run who-touches preflight check for a planned change path.
  --force-touch-conflict Continue even if who-touches finds active agent branches.
  --push                 Push the new branch to origin after initialization completes.
  --lock-reason <reason> Reason used for all initial locks.
  --lock-days <days>     Lock lifetime in days. Default: 1.
  --dry-run              Print the planned operations without changing files.

Example:
  scripts/agent-start-task.sh codex project-task-breakdown-from-tender origin/main
  scripts/agent-start-task.sh codex project-page --lock src/views/Project/Detail.vue --lock-reason "项目详情页改造"
  scripts/agent-start-task.sh codex project-page --touch src/views/Project/Detail.vue
  scripts/agent-start-task.sh codex quick-fix origin/main --in-place
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -lt 2 ]]; then
  usage >&2
  exit 1
fi

AGENT_NAME="$1"
TASK_SLUG="$2"
shift 2

BASE_REF="origin/main"
BASE_REF_SET=0
DRY_RUN=0
IN_PLACE=0
LOCK_REASON=""
LOCK_DAYS=1
LOCK_PATHS=()
LOCK_SCOPES=()
TOUCH_PATHS=()
FORCE_TOUCH_CONFLICT=0
AUTO_PUSH=0

POSITIONAL_REST=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --in-place)
      IN_PLACE=1
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --lock)
      if [[ $# -lt 2 ]]; then
        echo "agent-start-task: --lock requires a path." >&2
        exit 1
      fi
      LOCK_PATHS+=("$2")
      LOCK_SCOPES+=("file")
      shift 2
      ;;
    --lock-dir)
      if [[ $# -lt 2 ]]; then
        echo "agent-start-task: --lock-dir requires a path." >&2
        exit 1
      fi
      LOCK_PATHS+=("$2")
      LOCK_SCOPES+=("directory")
      shift 2
      ;;
    --touch)
      if [[ $# -lt 2 ]]; then
        echo "agent-start-task: --touch requires a path." >&2
        exit 1
      fi
      TOUCH_PATHS+=("$2")
      shift 2
      ;;
    --force-touch-conflict)
      FORCE_TOUCH_CONFLICT=1
      shift
      ;;
    --push)
      AUTO_PUSH=1
      shift
      ;;
    --lock-reason)
      if [[ $# -lt 2 ]]; then
        echo "agent-start-task: --lock-reason requires text." >&2
        exit 1
      fi
      LOCK_REASON="$2"
      shift 2
      ;;
    --lock-days)
      if [[ $# -lt 2 ]]; then
        echo "agent-start-task: --lock-days requires a positive integer." >&2
        exit 1
      fi
      LOCK_DAYS="$2"
      shift 2
      ;;
    -*)
      echo "agent-start-task: unknown option '$1'." >&2
      usage >&2
      exit 1
      ;;
    *)
      POSITIONAL_REST+=("$1")
      shift
      ;;
  esac
done

if [[ "${#POSITIONAL_REST[@]}" -gt 1 ]]; then
  echo "agent-start-task: unexpected arguments: ${POSITIONAL_REST[*]}" >&2
  usage >&2
  exit 1
fi

if [[ "${#POSITIONAL_REST[@]}" -eq 1 ]]; then
  BASE_REF="${POSITIONAL_REST[0]}"
  BASE_REF_SET=1
fi

if [[ "${#LOCK_PATHS[@]}" -gt 0 && -z "$LOCK_REASON" ]]; then
  LOCK_REASON="任务 $TASK_SLUG 初始锁"
fi

if [[ "${#TOUCH_PATHS[@]}" -eq 0 && "${#LOCK_PATHS[@]}" -gt 0 ]]; then
  TOUCH_PATHS=("${LOCK_PATHS[@]}")
fi

if [[ ! "$LOCK_DAYS" =~ ^[1-9][0-9]*$ ]]; then
  echo "agent-start-task: --lock-days must be a positive integer." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

WORKTREES_ROOT="${WORKTREES_ROOT:-$HOME/xiyu/worktrees}"
if [[ "$IN_PLACE" == "1" ]]; then
  # --in-place 模式：在当前 worktree 内创建分支
  WORKTREE_PATH="$REPO_ROOT"
  WORKTREE_NAME="$(basename "$WORKTREE_PATH")"
  CREATE_WORKTREE=0
else
  WORKTREE_PATH="$WORKTREES_ROOT/$AGENT_NAME-$TASK_SLUG"
  WORKTREE_NAME="$AGENT_NAME-$TASK_SLUG"
  CREATE_WORKTREE=1
fi
BRANCH_NAME="agent/$AGENT_NAME/$TASK_SLUG"
WORKTREE_CREATED=0
LOCKS_ACQUIRED=0
ACQUIRED_LOCK_PATHS=()
ACQUIRED_LOCK_SCOPES=()

run_touch_preflight() {
  if [[ "${#TOUCH_PATHS[@]}" -eq 0 ]]; then
    return 0
  fi
  local conflicts=0
  for touch_path in "${TOUCH_PATHS[@]}"; do
    local result
    result="$(cd "$REPO_ROOT" && node scripts/who-touches.mjs --json "$touch_path" 2>/dev/null || true)"
    if [[ -n "$result" ]]; then
      local active_branches
      active_branches="$(echo "$result" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    active = [b for b in data if b.get('active', False) and b.get('branch') != '$BRANCH_NAME']
    print(json.dumps(active))
except: print('[]')
" 2>/dev/null)"
      if [[ "$active_branches" != "[]" && -n "$active_branches" ]]; then
        echo "agent-start-task: who-touches conflict on $touch_path" >&2
        echo "  active branches:"
        echo "$active_branches" | python3 -c "
import sys, json
for b in json.load(sys.stdin):
    print(f'    - {b.get(\"branch\", \"?\")} (agent: {b.get(\"agent\", \"?\")})')
" 2>/dev/null || true
        conflicts=1
      fi
    fi
  done
  if [[ "$conflicts" == "1" ]]; then
    if [[ "$FORCE_TOUCH_CONFLICT" == "1" ]]; then
      echo "agent-start-task: conflict override enabled; proceeding despite conflicts." >&2
    else
      echo "agent-start-task: aborting due to who-touches conflicts. Use --force-touch-conflict to override." >&2
      exit 1
    fi
  fi
}

cleanup_on_error() {
  local exit_code=$?
  if [[ "$exit_code" -eq 0 ]]; then
    return 0
  fi

  echo "agent-start-task: initialization failed, cleaning up partial state..." >&2

  if [[ "$LOCKS_ACQUIRED" == "1" && -d "$WORKTREE_PATH" ]]; then
    for index in "${!ACQUIRED_LOCK_PATHS[@]}"; do
      (
        cd "$WORKTREE_PATH"
        node scripts/manage-agent-locks.mjs release \
          --path "${ACQUIRED_LOCK_PATHS[$index]}" \
          --scope "${ACQUIRED_LOCK_SCOPES[$index]}"
      ) >/dev/null 2>&1 || true
    done
  fi

  if [[ "$CREATE_WORKTREE" == "1" && "$WORKTREE_CREATED" == "1" ]]; then
    git worktree remove "$WORKTREE_PATH" --force >/dev/null 2>&1 || true
  fi

  if [[ "$CREATE_WORKTREE" == "0" ]]; then
    # --in-place 模式：只清理分支
    if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
      git branch -D "$BRANCH_NAME" >/dev/null 2>&1 || true
    fi
  else
    if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
      git branch -D "$BRANCH_NAME" >/dev/null 2>&1 || true
    fi
  fi
}
trap cleanup_on_error EXIT

if [[ ! "$AGENT_NAME" =~ ^[a-z][a-z0-9-]*$ ]]; then
  echo "agent-start-task: invalid agent name '$AGENT_NAME'." >&2
  exit 1
fi

if [[ ! "$TASK_SLUG" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
  echo "agent-start-task: invalid task slug '$TASK_SLUG'." >&2
  exit 1
fi

if [[ "$DRY_RUN" == "1" ]]; then
  echo "Dry run task branch:"
  echo "  worktree: $WORKTREE_PATH"
  echo "  branch:   $BRANCH_NAME"
  echo "  base:     $BASE_REF"
  echo "  mode:     $([ "$IN_PLACE" == "1" ] && echo 'in-place (current worktree)' || echo 'new worktree')"
  echo "  touch:    ${#TOUCH_PATHS[@]} paths"
  echo "  morning:  fetch + rebase origin/main + sync-env.sh"
  echo "  locks:    ${#LOCK_PATHS[@]} locks"
  echo
  if [[ "${#TOUCH_PATHS[@]}" -gt 0 ]]; then
    echo "  touch paths:"
    for touch_path in "${TOUCH_PATHS[@]}"; do
      echo "    - $touch_path"
    done
  fi
  echo
  echo "Expected status summary:"
  if [[ "${#TOUCH_PATHS[@]}" -gt 0 ]]; then
    echo "  - .agent-task-context will record declared touch paths"
  else
    echo "  - no touch paths declared; consider using --touch for collaborative visibility"
  fi
  if [[ "$FORCE_TOUCH_CONFLICT" == "1" ]]; then
    echo "  - conflict override is enabled; coordination evidence should be recorded if conflicts appear"
  fi
  if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
    echo "  - initial locks will be registered in the new worktree"
  else
    echo "  - no initial locks will be registered automatically"
  fi
  if [[ "$AUTO_PUSH" == "1" ]]; then
    echo "  - branch will be pushed automatically and become visible to other agents"
    echo "  post-create push: git push -u origin $BRANCH_NAME"
  else
    echo "  - branch will remain local until you push it manually"
  fi
  exit 0
fi

# 检查分支是否已存在
if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
  echo "agent-start-task: branch already exists: $BRANCH_NAME" >&2
  exit 1
fi

# --in-place 模式：确认当前在锚点分支上，pull 最新 main，再切分支
if [[ "$IN_PLACE" == "1" ]]; then
  current_branch="$(git rev-parse --abbrev-ref HEAD)"
  if [[ "$current_branch" != "agent/${AGENT_NAME}-init" ]]; then
    echo "agent-start-task: --in-place requires being on the anchor branch 'agent/${AGENT_NAME}-init' (currently on '$current_branch')" >&2
    exit 1
  fi
  # --- 早操三连：基线对齐 + 环境同步（agent-sop-quickref.md §一）---
 
  echo "agent-start-task: [morning-routine] git fetch origin..."
  git fetch origin
  echo "agent-start-task: [morning-routine] git rebase origin/main..."
  git rebase origin/main
  echo "agent-start-task: [morning-routine] sync-env.sh ."
  bash scripts/sync-env.sh .
  # -------------------------------------
  echo "agent-start-task: creating branch $BRANCH_NAME in current worktree..."
  git checkout -b "$BRANCH_NAME"
  cat > "$WORKTREE_PATH/.agent-task-context" <<EOF
agent=$AGENT_NAME
task=$TASK_SLUG
branch=$BRANCH_NAME
base=$BASE_REF
worktree=$WORKTREE_PATH
repo_root=$REPO_ROOT
mode=in-place
created_from_branch=$(git rev-parse --abbrev-ref HEAD | sed "s/.*/agent\/${AGENT_NAME}-init/")
created_from_rev=$(git rev-parse HEAD)
created_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

  if [[ "${#TOUCH_PATHS[@]}" -gt 0 ]]; then
    {
      echo "touch_paths<<'PATHS'"
      printf '%s\n' "${TOUCH_PATHS[@]}"
      echo "PATHS"
    } >> "$WORKTREE_PATH/.agent-task-context"
  fi

  # 增量安装 hooks（检查是否已安装）
  if [[ ! -f "$WORKTREE_PATH/.githooks/pre-commit" ]]; then
    (cd "$WORKTREE_PATH" && bash scripts/install-githooks.sh && bash scripts/install-java-standards-hook.sh) || true
  fi

  # 配置 git alias，防止 --no-verify 绕过门禁（仅首次设置）
  if ! git config alias.push 2>/dev/null | grep -q 'git-push-wrapper'; then
    git config alias.push '!bash .githooks/git-push-wrapper.sh'
    echo "agent-start-task: git alias push → .githooks/git-push-wrapper.sh (门禁强制)"
  fi
  if ! git config alias.commit 2>/dev/null | grep -q 'git-commit-wrapper'; then
    git config alias.commit '!bash .githooks/git-commit-wrapper.sh'
    echo "agent-start-task: git alias commit → .githooks/git-commit-wrapper.sh (门禁强制)"
  fi

  # 增量安装 node 依赖（检查 node_modules 是否存在）
  if [[ -f "$WORKTREE_PATH/package.json" && ! -d "$WORKTREE_PATH/node_modules" ]]; then
    echo "agent-start-task: package.json detected, running fast offline-first pnpm install..."
    (cd "$WORKTREE_PATH" && pnpm install --prefer-offline --reporter=silent || pnpm install)
  fi

  if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
    for index in "${!LOCK_PATHS[@]}"; do
      (
        cd "$WORKTREE_PATH"
        node scripts/manage-agent-locks.mjs acquire \
          --path "${LOCK_PATHS[$index]}" \
          --scope "${LOCK_SCOPES[$index]}" \
          --reason "$LOCK_REASON" \
          --days "$LOCK_DAYS"
      )
      ACQUIRED_LOCK_PATHS+=("${LOCK_PATHS[$index]}")
      ACQUIRED_LOCK_SCOPES+=("${LOCK_SCOPES[$index]}")
      LOCKS_ACQUIRED=1
    done
    (cd "$WORKTREE_PATH" && node scripts/check-agent-locks.mjs)
  fi

  if [[ "$AUTO_PUSH" == "1" ]]; then
    git -C "$WORKTREE_PATH" push -u origin "$BRANCH_NAME"
  fi

  trap - EXIT

  echo "Created task branch (in-place):"
  echo "  worktree: $WORKTREE_PATH"
  echo "  branch:   $BRANCH_NAME"
  echo "  base:     $BASE_REF"
  if [[ "${#TOUCH_PATHS[@]}" -gt 0 ]]; then
    echo "  touch paths:"
    for touch_path in "${TOUCH_PATHS[@]}"; do
      echo "    - $touch_path"
    done
  fi
  if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
    echo "  initial locks:"
    for index in "${!LOCK_PATHS[@]}"; do
      printf "    lock %-10s %s\n" "${LOCK_SCOPES[$index]}:" "${LOCK_PATHS[$index]}"
    done
  fi
  if [[ "$AUTO_PUSH" == "1" ]]; then
    echo "  remote branch: origin/$BRANCH_NAME"
  fi
  echo
  echo "Status summary:"
  if [[ "${#TOUCH_PATHS[@]}" -gt 0 ]]; then
    echo "  - who-touches preflight completed for declared paths"
  else
    echo "  - no touch paths declared"
  fi
  if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
    echo "  - initial locks registered"
  fi
  echo
  echo "Next:"
  echo "  Develop on this branch, commit, push, and create a PR."
  echo "  After PR merged, run:"
  echo "    git checkout agent/${AGENT_NAME}-init"
  echo "    git pull origin main"
  echo "    git branch -D $BRANCH_NAME"
  exit 0
fi

# ─── 以下是传统模式（新 worktree）────────────────────────────────────────────────
if ! git rev-parse --verify --quiet "$BASE_REF" >/dev/null; then
  echo "agent-start-task: base ref does not exist or cannot be resolved: $BASE_REF" >&2
  exit 1
fi

run_touch_preflight

mkdir -p "$WORKTREES_ROOT"
git fetch origin --prune
git worktree add -b "$BRANCH_NAME" "$WORKTREE_PATH" "$BASE_REF"
WORKTREE_CREATED=1

cat > "$WORKTREE_PATH/.agent-task-context" <<EOF
agent=$AGENT_NAME
task=$TASK_SLUG
branch=$BRANCH_NAME
base=$BASE_REF
worktree=$WORKTREE_PATH
repo_root=$REPO_ROOT
created_from_branch=$(git rev-parse --abbrev-ref HEAD)
created_from_rev=$(git rev-parse HEAD)
created_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

if [[ "${#TOUCH_PATHS[@]}" -gt 0 ]]; then
  {
    echo "touch_paths<<'PATHS'"
    printf '%s\n' "${TOUCH_PATHS[@]}"
    echo "PATHS"
  } >> "$WORKTREE_PATH/.agent-task-context"
fi

# 同步环境模板并执行 main-forward，确保新 worktree 进入可开发状态
(cd "$WORKTREE_PATH" && bash scripts/sync-env.sh .)

# 安装 git hooks（本地 CI 门禁），新 worktree 自动激活
(cd "$WORKTREE_PATH" && bash scripts/install-githooks.sh && bash scripts/install-java-standards-hook.sh)

# 配置 git alias，防止 --no-verify 绕过门禁（仅首次设置）
if ! git -C "$WORKTREE_PATH" config alias.push 2>/dev/null | grep -q 'git-push-wrapper'; then
  git -C "$WORKTREE_PATH" config alias.push '!bash .githooks/git-push-wrapper.sh'
  echo "agent-start-task: git alias push → .githooks/git-push-wrapper.sh (门禁强制)"
fi
if ! git -C "$WORKTREE_PATH" config alias.commit 2>/dev/null | grep -q 'git-commit-wrapper'; then
  git -C "$WORKTREE_PATH" config alias.commit '!bash .githooks/git-commit-wrapper.sh'
  echo "agent-start-task: git alias commit → .githooks/git-commit-wrapper.sh (门禁强制)"
fi

# 自动检测并以离线优先方式安装 node 依赖，缩减冷启动时间
if [[ -f "$WORKTREE_PATH/package.json" ]]; then
  echo "agent-start-task: package.json detected, running fast offline-first pnpm install..."
  (
    cd "$WORKTREE_PATH"
    pnpm install --prefer-offline --reporter=silent || pnpm install
  )
fi

if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
  for index in "${!LOCK_PATHS[@]}"; do
    (
      cd "$WORKTREE_PATH"
      node scripts/manage-agent-locks.mjs acquire \
        --path "${LOCK_PATHS[$index]}" \
        --scope "${LOCK_SCOPES[$index]}" \
        --reason "$LOCK_REASON" \
        --days "$LOCK_DAYS"
    )
    ACQUIRED_LOCK_PATHS+=("${LOCK_PATHS[$index]}")
    ACQUIRED_LOCK_SCOPES+=("${LOCK_SCOPES[$index]}")
    LOCKS_ACQUIRED=1
  done
  (
    cd "$WORKTREE_PATH"
    node scripts/check-agent-locks.mjs
  )
fi

if [[ "$AUTO_PUSH" == "1" ]]; then
  git -C "$WORKTREE_PATH" push -u origin "$BRANCH_NAME"
fi

trap - EXIT

echo "Created task worktree:"
echo "  worktree: $WORKTREE_PATH"
echo "  branch:   $BRANCH_NAME"
echo "  base:     $BASE_REF"
if [[ "${#TOUCH_PATHS[@]}" -gt 0 ]]; then
  echo "  touch paths:"
  for touch_path in "${TOUCH_PATHS[@]}"; do
    echo "    - $touch_path"
  done
fi
if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
  echo "  initial locks:"
  for index in "${!LOCK_PATHS[@]}"; do
    printf "    lock %-10s %s\n" "${LOCK_SCOPES[$index]}:" "${LOCK_PATHS[$index]}"
  done
fi
if [[ "$AUTO_PUSH" == "1" ]]; then
  echo "  remote branch: origin/$BRANCH_NAME"
fi
echo

echo "Status summary:"
if [[ "${#TOUCH_PATHS[@]}" -gt 0 ]]; then
  echo "  - who-touches preflight completed for declared paths"
  if [[ "$FORCE_TOUCH_CONFLICT" == "1" ]]; then
    echo "  - conflict override was used; record coordination evidence in your PR or task notes"
  fi
else
  echo "  - no touch paths declared; consider using --touch for collaborative visibility"
fi
if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
  echo "  - initial locks are already registered in this worktree"
else
  echo "  - no initial locks registered yet; acquire locks before editing hot paths"
fi
if [[ "$AUTO_PUSH" == "1" ]]; then
  echo "  - branch has already been pushed so other agents can see it"
else
  echo "  - branch has not been pushed yet; push before ending the session"
fi

echo

echo "Next:"
echo "  cd $WORKTREE_PATH"
echo "  npm run agent:lock-check"
if [[ "$AUTO_PUSH" != "1" ]]; then
  echo "  git push -u origin $BRANCH_NAME"
fi
if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
  echo "  git status"
  echo "  git add .agent-locks/"
  echo "  git commit -m \"chore: register initial agent locks for $TASK_SLUG\""
else
  echo "  npm run agent:lock-acquire -- --path <path> --scope file --reason \"<reason>\""
fi
if [[ "$FORCE_TOUCH_CONFLICT" == "1" ]]; then
  echo "  # 在 PR 描述或任务记录中补充冲突协调说明"
fi
