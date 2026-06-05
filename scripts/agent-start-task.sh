#!/usr/bin/env bash
# Input: agent name, task slug, optional base ref, and optional initial lock paths
# Output: isolated worktree, task branch, local .agent-task-context, and optional .agent-locks.yml entries
# Pos: scripts/多 Agent 工作区初始化
# 维护声明: 若工作区根目录、分支前缀、任务上下文字段或文件锁参数变化，请同步更新 scripts/README.md。
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/agent-start-task.sh <agent> <task-slug> [base-ref] [options]

Options:
  --lock <path>           Acquire an initial file lock after worktree creation.
  --lock-dir <path>       Acquire an initial directory lock after worktree creation.
  --lock-reason <reason>  Reason used for all initial locks.
  --lock-days <days>      Lock lifetime in days. Default: 1.
  --dry-run               Print the planned worktree and lock operations without changing files.

Example:
  scripts/agent-start-task.sh codex project-task-breakdown-from-tender origin/main
  scripts/agent-start-task.sh codex project-page --lock src/views/Project/Detail.vue --lock-reason "项目详情页改造"
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
LOCK_REASON=""
LOCK_DAYS=1
LOCK_PATHS=()
LOCK_SCOPES=()

while [[ $# -gt 0 ]]; do
  case "$1" in
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
      if [[ "$BASE_REF_SET" == "1" ]]; then
        echo "agent-start-task: unexpected argument '$1'." >&2
        usage >&2
        exit 1
      fi
      BASE_REF="$1"
      BASE_REF_SET=1
      shift
      ;;
  esac
done

if [[ "${#LOCK_PATHS[@]}" -gt 0 && -z "$LOCK_REASON" ]]; then
  LOCK_REASON="任务 $TASK_SLUG 初始锁"
fi

if [[ ! "$LOCK_DAYS" =~ ^[1-9][0-9]*$ ]]; then
  echo "agent-start-task: --lock-days must be a positive integer." >&2
  exit 1
fi

WORKTREES_ROOT="/Users/user/xiyu/worktrees"
WORKTREE_PATH="$WORKTREES_ROOT/$AGENT_NAME-$TASK_SLUG"
BRANCH_NAME="$AGENT_NAME/$TASK_SLUG"

if [[ ! "$AGENT_NAME" =~ ^[a-z][a-z0-9-]*$ ]]; then
  echo "agent-start-task: invalid agent name '$AGENT_NAME'." >&2
  exit 1
fi

if [[ ! "$TASK_SLUG" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
  echo "agent-start-task: invalid task slug '$TASK_SLUG'." >&2
  exit 1
fi

if [[ "$DRY_RUN" == "1" ]]; then
  echo "Dry run task worktree:"
  echo "  worktree: $WORKTREE_PATH"
  echo "  branch:   $BRANCH_NAME"
  echo "  base:     $BASE_REF"
  if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
    echo "  initial locks:"
    for index in "${!LOCK_PATHS[@]}"; do
      printf "    lock %-10s %s\n" "${LOCK_SCOPES[$index]}:" "${LOCK_PATHS[$index]}"
      echo "    node scripts/manage-agent-locks.mjs acquire --path ${LOCK_PATHS[$index]} --scope ${LOCK_SCOPES[$index]} --reason $LOCK_REASON --days $LOCK_DAYS"
    done
  fi
  echo
  echo "After creating real locks, push the branch so other Agents can see them:"
  echo "  git push -u origin $BRANCH_NAME"
  exit 0
fi

if [[ -e "$WORKTREE_PATH" ]]; then
  echo "agent-start-task: worktree already exists: $WORKTREE_PATH" >&2
  exit 1
fi

if git show-ref --verify --quiet "refs/heads/$BRANCH_NAME"; then
  echo "agent-start-task: branch already exists: $BRANCH_NAME" >&2
  exit 1
fi

mkdir -p "$WORKTREES_ROOT"
git fetch origin --prune
git worktree add -b "$BRANCH_NAME" "$WORKTREE_PATH" "$BASE_REF"

cat > "$WORKTREE_PATH/.agent-task-context" <<EOF
agent=$AGENT_NAME
task=$TASK_SLUG
branch=$BRANCH_NAME
base=$BASE_REF
worktree=$WORKTREE_PATH
created_at=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
EOF

# 安装 git hooks（本地 CI 门禁），新 worktree 自动激活
(cd "$WORKTREE_PATH" && bash scripts/install-githooks.sh)

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
  done
  (
    cd "$WORKTREE_PATH"
    node scripts/check-agent-locks.mjs
  )
fi

echo "Created task worktree:"
echo "  worktree: $WORKTREE_PATH"
echo "  branch:   $BRANCH_NAME"
echo "  base:     $BASE_REF"
if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
  echo "  initial locks:"
  for index in "${!LOCK_PATHS[@]}"; do
    printf "    lock %-10s %s\n" "${LOCK_SCOPES[$index]}:" "${LOCK_PATHS[$index]}"
  done
fi
echo
echo "Next:"
echo "  cd $WORKTREE_PATH"
echo "  scripts/install-java-standards-hook.sh"
echo "  npm run agent:lock-check"
if [[ "${#LOCK_PATHS[@]}" -gt 0 ]]; then
  echo "  git add .agent-locks.yml"
  echo "  git commit -m \"chore: register initial agent locks for $TASK_SLUG\""
  echo "  git push -u origin $BRANCH_NAME"
else
  echo "  npm run agent:lock-acquire -- --path <path> --scope file --reason \"<reason>\""
  echo "  git push -u origin $BRANCH_NAME"
fi
