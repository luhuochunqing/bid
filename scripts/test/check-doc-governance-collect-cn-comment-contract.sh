#!/usr/bin/env bash
# Input: scripts/check-doc-governance.mjs 修复后的 checkGovernedFile 逻辑
# Output: PASS / FAIL — 验证 check 对 session-gate.sh header 的误判已修
# Pos: scripts/test/check-doc-governance-collect-cn-comment-contract.sh
# 维护声明: 若 check-doc-governance.mjs 的 commentLines 收集逻辑变化，请同步更新本契约。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# 测试用 inline content（不依赖文件系统）
# session-gate.sh 的真实 header：Pos + 维护声明在 L11/12，夹 4 行多行 list 注释
# 修复前会因 >= 4 早 break 而误报缺 Pos + 维护
SESSION_GATE_SH_HEADER='#!/usr/bin/env bash
# Input: current repo root (ROOT_DIR), current git branch, optional CHAT_ONLY env
# Output: exit 1 if blocked (anchor branch / session conflict / rebase failure); 0 otherwise
#   - 锚点分支 => 拒绝，提示用 '\''早操SOP + 开个分支 <任务名>'\'' 切开发分支
#   - session 互斥 => 拒绝（同 worktree 只能一个开发 session）
#   - 自动同步: 非聊天模式下自动 git fetch + rebase origin/main
#   - rebase 后检查锚点 + Flyway 版本自愈
#   - 锁释放时：
#     * 提醒未推送 commit + 自动 push
#     * 清理远端已删除的残留本地分支
# Pos: scripts/多 Agent Session 互斥门禁 — worktree 独占 + 锚点阻断 + 自动同步
# 维护声明: 本脚本被 dev-env.sh source 加载，也被环境初始化时独立引用。
# 不设 set -e，因为是被 source 的（调用方负责错误处理）

SESSION_LOCK=".session-active"
'

# 标准 4 行 header（对照组，应通过）
STANDARD_HEADER='#!/usr/bin/env bash
# Input: foo
# Output: bar
# Pos: scripts/ - foo bar
# 维护声明: baz
'

# 缺 Pos（应报错）
MISSING_POS_HEADER='#!/usr/bin/env bash
# Input: foo
# Output: bar
# 维护声明: baz
'

# 缺维护声明（应报错）
MISSING_MAINT_HEADER='#!/usr/bin/env bash
# Input: foo
# Output: bar
# Pos: scripts/ - foo bar
'

# 用 node 跑 inline JS 复用修复后的 check 逻辑
run_check_inline() {
  local content="$1"
  local case_name="$2"
  echo "== $case_name =="

  # 复用 scripts/lib/doc-governance-checker.mjs 的导出函数
  # 注意：必须用 ESM 模式 (--input-type=module)
  local violations
  violations=$(node --input-type=module -e "
    import { checkGovernedFileContent } from '$(echo "$REPO_ROOT/scripts/lib/doc-governance-checker.mjs" | sed 's|/|\\/|g')';
    const result = checkGovernedFileContent(\`$content\`);
    process.stdout.write(result.violations.join('\n'));
  " 2>&1)

  echo "  violations:"
  if [[ -z "$violations" ]]; then
    echo "    (none)"
  else
    echo "$violations" | sed 's/^/    /'
  fi
}

assert_no_violations() {
  local case_name="$1"
  local content="$2"
  local violations
  violations=$(node --input-type=module -e "
    import { checkGovernedFileContent } from '$(echo "$REPO_ROOT/scripts/lib/doc-governance-checker.mjs" | sed 's|/|\\/|g')';
    const result = checkGovernedFileContent(\`$content\`);
    if (result.violations.length > 0) {
      console.error('expected no violations, got:', result.violations);
      process.exit(1);
    }
  " 2>&1)
  if [[ $? -ne 0 ]]; then
    echo "[FAIL] $case_name: $violations" >&2
    exit 1
  fi
  echo "[PASS] $case_name"
}

assert_has_violation() {
  local case_name="$1"
  local content="$2"
  local expected_violation="$3"
  local violations
  violations=$(node --input-type=module -e "
    import { checkGovernedFileContent } from '$(echo "$REPO_ROOT/scripts/lib/doc-governance-checker.mjs" | sed 's|/|\\/|g')';
    const result = checkGovernedFileContent(\`$content\`);
    if (!result.violations.includes('$expected_violation')) {
      console.error('expected violation \"$expected_violation\" not found, got:', result.violations);
      process.exit(1);
    }
  " 2>&1)
  if [[ $? -ne 0 ]]; then
    echo "[FAIL] $case_name: $violations" >&2
    exit 1
  fi
  echo "[PASS] $case_name"
}

echo "=========================================="
echo "check-doc-governance.mjs commentLines 修复契约"
echo "=========================================="
echo

# Case 1: session-gate.sh 真实 header（修复前会误报）—— 修复后应通过
assert_no_violations "session-gate.sh: Pos + 维护声明在 L11/12 夹 4 行 list 注释" "$SESSION_GATE_SH_HEADER"

# Case 2: 标准 4 行 header —— 应通过
assert_no_violations "标准 4 行 header" "$STANDARD_HEADER"

# Case 3: 缺 Pos —— 应报错
assert_has_violation "缺 Pos 时报错" "$MISSING_POS_HEADER" 'missing header line "Pos:"'

# Case 4: 缺维护声明 —— 应报错
assert_has_violation "缺维护声明时报错" "$MISSING_MAINT_HEADER" "missing maintenance declaration"

# Case 5: 多行 list 注释后的真实代码（`SESSION_LOCK=...` 不以 # 开头）—— 应正确 break
MULTILINE_HEADER='#!/usr/bin/env bash
# Input: foo
# Output: bar
# Pos: scripts/ - foo bar
# 维护声明: baz
# 附加说明
# 更多信息
SESSION_LOCK="active"
'
assert_no_violations "多行 list 注释后接真实代码" "$MULTILINE_HEADER"

echo
echo "All check-doc-governance commentLines contract checks passed."
