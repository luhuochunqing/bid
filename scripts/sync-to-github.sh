#!/usr/bin/env bash
# Input: none（从当前 worktree 执行）
# Output: 将 Gitee main 推送到 GitHub main，保持双远程基线对齐
# Pos: scripts/ - Gitee → GitHub 单向镜像同步脚本 (2026-06-26)
# 维护声明: 同步 Gitee main 到 GitHub main；新增远程仓库时请同步脚本。
# 用法: bash scripts/sync-to-github.sh
# 安全: 只推送 main 分支，不修改工作区内容；force-with-lease 安全强推
#
# 背景:
#   项目双远程架构——Gitee（origin）为主仓库，GitHub（github）为 AI 协作入口 + 镜像。
#   长期不推 GitHub → 分叉 → AI 在旧基线开发 → cherry-pick 回来覆盖 Gitee 新逻辑。
#   本脚本在每次 Gitee 合并 PR 后手动执行，确保 GitHub main 始终等于 Gitee main。

set -euo pipefail

# ─── helpers ─────────────────────────────────────────────────────────────────

info()  { echo "sync-to-github: $*" ; }
warn()  { echo "sync-to-github: WARNING: $*" >&2; }
die()   { echo "sync-to-github: ERROR: $*" >&2; exit 1; }

# ─── pre-flight checks ───────────────────────────────────────────────────────

# 必须在 worktree 根目录执行
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# 检查 github remote 是否存在
if ! git remote get-url github &>/dev/null; then
  die "未找到 'github' remote。请先配置 GitHub 远程仓库。"
fi

GITHUB_URL=$(git remote get-url github)
GITEE_URL=$(git remote get-url origin 2>/dev/null || echo "")

info "Gitee (origin):  $GITEE_URL"
info "GitHub (github): $GITHUB_URL"
echo ""

# ─── 1. 拉取 Gitee main 最新 ────────────────────────────────────────────────

info "1/4  拉取 Gitee main 最新..."
git fetch origin main --prune
GITEE_HEAD=$(git rev-parse origin/main)
info "  Gitee main HEAD: $GITEE_HEAD"
echo ""

# ─── 2. 拉取 GitHub main 当前状态 ──────────────────────────────────────────

info "2/4  拉取 GitHub main 当前状态..."
git fetch github main --prune 2>/dev/null || true
GITHUB_HEAD=$(git rev-parse github/main 2>/dev/null || echo "unknown")
info "  GitHub main HEAD (推送前): $GITHUB_HEAD"
echo ""

# 已经一致就不用推
if [ "$GITEE_HEAD" = "$GITHUB_HEAD" ]; then
  info "✅ 两边 main 已完全一致，无需推送。"
  exit 0
fi

# 计算差异规模
AHEAD=$(git rev-list --count github/main..origin/main 2>/dev/null || echo "?")
BEHIND=$(git rev-list --count origin/main..github/main 2>/dev/null || echo "?")
info "  Gitee 领先 GitHub: $AHEAD 个 commit"
info "  GitHub 领先 Gitee: $BEHIND 个 commit"
echo ""

# ─── 3. 推送 Gitee main → GitHub main ──────────────────────────────────────

info "3/4  推送 Gitee main → GitHub main（force-with-lease）..."
info "  将 GitHub main 从 $GITHUB_HEAD"
info "  强制更新到           $GITEE_HEAD"
echo ""

# ═══════════════════════════════════════════════════════════════
#  单向安全检查：确保 Gitee main 是 GitHub main 的超集或快进
#  防止误操作把旧版本推过去覆盖新代码
# ═══════════════════════════════════════════════════════════════
if git merge-base --is-ancestor "$GITEE_HEAD" "$GITHUB_HEAD" 2>/dev/null \
   && [ "$GITEE_HEAD" != "$GITHUB_HEAD" ]; then
  die "安全拦截：Gitee main 是 GitHub main 的祖先（Gitee 更旧）。
    这意味着 GitHub main 有 Gitee 没有的改动。
    禁止用旧的 Gitee main 覆盖新的 GitHub main！
    正确做法：
      1. 先用 scripts/sync-from-github.sh 把 GitHub 的改动 cherry-pick 回 Gitee
      2. 审查通过合入 Gitee main 后
      3. 再执行本脚本镜像同步"
fi

# 如果 GitHub 有独有 commit，警告
if [ "$BEHIND" != "0" ] && [ "$BEHIND" != "?" ]; then
  warn "⚠️  GitHub main 有 $BEHIND 个独有 commit 将被覆盖！"
  warn "    被覆盖的 commit 列表："
  git log --oneline origin/main..github/main 2>/dev/null | head -10 | sed 's/^/      /' >&2
  if [ "$BEHIND" -gt 10 ]; then
    warn "      ...（共 $BEHIND 个，仅显示前 10 个）"
  fi
  echo ""
  warn "    如果这些 commit 包含需要保留的改动，请先 cherry-pick 到 Gitee 再执行本脚本。"
  warn "    继续推送将不可恢复地丢失这些 GitHub 独有 commit。"
  echo ""

  read -p "确认覆盖 GitHub main？(y/N) " -r
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    info "已取消。"
    exit 0
  fi
  echo ""
fi

# ═══ 门禁与传输解耦（2026-08-26 SIGPIPE 根治）═══
# 根因：git push 的时序是「ssh 连接建立 → pre-push hook 运行 → 传包」。
# hook 内跑质量门禁（前端测试/构建）耗时数分钟，期间连接空闲，远端会掐断空闲连接；
# hook 通过后 git 开始传包，写入已死管道 → SIGPIPE(exit 141)，且 stdout 重定向也救不了。
# 解法：显式先跑同一份 pre-push-gate.sh（强度不变），通过后再推送 —— 连接建立后立即传包，无空闲窗口。
info "运行推送前质量门禁（与 hook 同一份脚本，强度不变）..."
if ! bash scripts/pre-push-gate.sh; then
  die "质量门禁未通过，取消镜像同步。"
fi
echo ""
# PRE_PUSH_GATE=0: 跳过 hook 内联门禁（已在上方独立跑过，此处仅为避免重建空闲窗口）
# FORCE_PUSH_OK=1: 允许 root-commit ratchet 放行（GitHub 单向镜像本就是经批准的 force 覆盖）
FORCE_PUSH_OK=1 PRE_PUSH_GATE=0 git push github origin/main:refs/heads/main --force-with-lease
echo ""

# ─── 4. 验证结果 ───────────────────────────────────────────────────────────

info "4/4  验证推送结果..."
git fetch github main --prune
GITHUB_NEW_HEAD=$(git rev-parse github/main)

if [ "$GITEE_HEAD" = "$GITHUB_NEW_HEAD" ]; then
  info "✅ 同步成功！两边 main 完全一致。"
  info "   HEAD: $GITHUB_NEW_HEAD"
else
  die "❌ 同步失败！Gitee=$GITEE_HEAD, GitHub=$GITHUB_NEW_HEAD"
fi
echo ""

# ─── summary ────────────────────────────────────────────────────────────────

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  ✅ Gitee → GitHub 镜像同步完成                              ║"
echo "╠══════════════════════════════════════════════════════════════╣"
echo "║  Gitee main:  $GITEE_HEAD ║"
echo "║  GitHub main: $GITHUB_NEW_HEAD ║"
echo "║  状态: 完全一致                                              ║"
echo "╚══════════════════════════════════════════════════════════════╝"
