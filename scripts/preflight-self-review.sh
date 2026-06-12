#!/usr/bin/env bash
# Input: git diff between current branch and origin/main
# Output: self-review checklist for PR body
# Pos: scripts/ — 提交前自审清单生成器 (2026-06-12 skill-progression-map)
# 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
cd "$ROOT_DIR"

echo ""
echo "=============================================="
echo "  提交前自审清单 (Phase 3: Code Review)"
echo "=============================================="
echo ""

# 1. 显示改动概览
echo "## 改动概览"
echo ""
git log origin/main..HEAD --oneline 2>/dev/null || echo "(no commits ahead of origin/main)"
echo ""
git diff origin/main..HEAD --stat 2>/dev/null || true
echo ""

# 2. 自审清单
echo "## 自审清单"
echo ""
echo "| # | 检查项 | 状态 |"
echo "|---|--------|------|"
echo "| 1 | 分层边界：纯核心（domain/policy/core）是否依赖了框架类？ | ⬜ |"
echo "| 2 | 安全配置：是否有新端点忘了加权限注解？ SecurityConfig 是否扩大了放行范围？ | ⬜ |"
echo "| 3 | Design Token 一致性：CSS 中是否有硬编码颜色未使用 CSS 变量？ | ⬜ |"
echo "| 4 | Mock / 遗留代码残留：是否引入了 isMockMode、demoPersistence 等过期表述？ | ⬜ |"
echo "| 5 | 数据库迁移（如有）：版本号递增？MySQL 8.0 兼容？回滚脚本存在？ | ⬜ |"
echo "| 6 | 代码质量：单文件 < 200 行？业务逻辑不写在 Controller 中？ | ⬜ |"
echo "| 7 | 构建验证：前端 npm run build？后端 mvn test？受影响 E2E 通过？ | ⬜ |"
echo ""
echo "## 说明"
echo "将上述清单粘贴到 PR description body 中，逐项确认后把 ⬜ 改为 ✅。"
echo "未附自审清单的 PR 可以被 reviewer 关闭。"
echo ""
echo "## 需要人工确认的问题"
echo ""
echo "- 本次改动的纯核心边界在哪里？外壳层在哪里？"
echo "- 若涉及 enum，是否确认前后端值契约一致？"
echo "- 是否有新的测试覆盖？覆盖率是否能达到 80%+？"
echo ""
echo "=============================================="

# 3. 自动触发相关门禁提醒
echo ""
echo "## 门禁状态速查"
echo ""

# 检查是否在当前分支有未 push 的 commit
AHEAD=$(git log origin/main..HEAD --oneline 2>/dev/null | wc -l | tr -d ' ')
echo "- 当前分支领先 main: ${AHEAD} commit(s)"

# 检查 mvn test 最近一次运行
if [ -f "backend/target/surefire-reports" ]; then
    echo "- 后端测试报告: $(find backend/target/surefire-reports -name '*.xml' 2>/dev/null | wc -l) files"
fi

# 检查 git status
echo ""
echo "当前工作目录状态:"
git status -s 2>/dev/null || true
echo ""
echo "=============================================="
