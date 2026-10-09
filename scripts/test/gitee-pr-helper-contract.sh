#!/usr/bin/env bash
# Input: none
# Output: validates gitee-pr-helper.sh response-parsing contract against a mock API
# Pos: scripts/test/gitee-pr-helper-contract.sh
# 维护声明: gitee-pr-helper.sh 的响应校验收敛在 gitee_get + require_json_response；
#           本契约用本地 mock 服务器覆盖响应形态（无需真实 GITEE_TOKEN）：
#           list 5 种 + merge 405 幂等放行/真失败 2 种。
#           根因背景（2026-09-03）: 401 错误字典 {"message":...} 曾被 len() 计成
#           "1 个开放 PR"（假阳性）后在解析循环 AttributeError 崩溃；非 JSON 响应
#           则静默计 0 把鉴权失败伪装成"无 PR"（假阴性）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TARGET_SCRIPT="$REPO_ROOT/scripts/gitee-pr-helper.sh"

PASS_COUNT=0

assert_contains() {
  local haystack="$1" needle="$2" description="$3"
  if [[ "$haystack" != *"$needle"* ]]; then
    echo "[FAIL] missing: $description" >&2
    echo "        expected snippet: $needle" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

assert_not_contains() {
  local haystack="$1" needle="$2" description="$3"
  if [[ "$haystack" == *"$needle"* ]]; then
    echo "[FAIL] unexpectedly present: $description" >&2
    echo "        forbidden snippet: $needle" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

# --- 启动 mock API：场景编码在路径首段（/ok /empty /e401 /badjson /dictaslist） ---
MOCK_PORT=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')

python3 - "$MOCK_PORT" <<'PY' &
import json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1])

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):  # 静默访问日志
        pass

    def do_GET(self):
        scenario = self.path.lstrip("/").split("/", 1)[0]

        def send(code, obj=None, raw=None):
            body = raw if raw is not None else json.dumps(obj).encode()
            ctype = "text/html" if raw is not None else "application/json"
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        if scenario == "ok":
            send(200, [{"number": 2511, "title": "feat: demo PR", "state": "open",
                        "mergeable": True,
                        "html_url": "https://gitee.com/allinai888/bid/pull/2511"}])
        elif scenario == "empty":
            send(200, [])
        elif scenario == "e401":
            # 本次修复的核心回归形态：401 错误字典
            send(401, {"message": "401 Unauthorized: Access token is expired"})
        elif scenario == "badjson":
            # 网关/代理返回 HTML 的形态
            send(200, raw=b"<html>502 gateway error</html>")
        elif scenario == "dictaslist":
            # 200 但形状不是数组（防御）
            send(200, {"message": "unexpected dict payload"})
        elif scenario == "merge405":
            # 合并 405 但 PR 终态已合并 → 应幂等放行（2026-10-09 PR !2817 实证）
            send(200, {"number": 2511, "state": "merged", "mergeable": True})
        elif scenario == "merge405open":
            # 合并 405 且 PR 仍 open → 真失败，不得被幂等逻辑吞掉
            send(200, {"number": 2511, "state": "open", "mergeable": True})
        elif scenario == "merge405closed":
            # 合并 405 且 PR 已关闭但未合并 → 仍须失败，不得当幂等成功
            send(200, {"number": 2511, "state": "closed", "mergeable": True})
        else:
            send(404, {"message": "no such scenario"})

    def do_PUT(self):
        scenario = self.path.lstrip("/").split("/", 1)[0]

        def send(code, obj):
            # ensure_ascii=False：真实 Gitee 返回字面中文，转义会削弱断言保真度
            body = json.dumps(obj, ensure_ascii=False).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        # Gitee 对已合并/已关闭 PR 的重复合并返回 405（非幂等 2xx）
        if scenario in ("merge405", "merge405open", "merge405closed"):
            send(405, {"message": "Pull Request 已合并或已关闭。"})
        else:
            send(404, {"message": "no such scenario"})

HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
PY
MOCK_PID=$!
trap 'kill "$MOCK_PID" 2>/dev/null || true' EXIT

# 轮询等待 mock 端口就绪（盲 sleep 在慢启动环境下会闪断第一个请求）
for _ in $(seq 1 40); do
  if python3 -c "import socket; s=socket.socket(); s.settimeout(0.3); s.connect(('127.0.0.1', ${MOCK_PORT})); s.close()" 2>/dev/null; then
    break
  fi
  sleep 0.25
done

run_list() {
  # $1=scenario; 输出全局 out / code
  local scenario="$1"
  out="$(GITEE_TOKEN="contract-test-token" \
    GITEE_API_BASE="http://127.0.0.1:${MOCK_PORT}/${scenario}/v5" \
    GITEE_OWNER="allinai888" GITEE_REPO="bid" \
    bash "$TARGET_SCRIPT" list "agent/codex/demo-branch" 2>&1)" || code=$?
}

run_merge() {
  # $1=scenario; $2=PR 编号; 输出全局 out / code
  local scenario="$1" pr="$2"
  out="$(GITEE_TOKEN="contract-test-token" \
    GITEE_API_BASE="http://127.0.0.1:${MOCK_PORT}/${scenario}/v5" \
    GITEE_OWNER="allinai888" GITEE_REPO="bid" \
    bash "$TARGET_SCRIPT" merge "$pr" 2>&1)" || code=$?
}

echo "== 契约场景 =="

# 场景 1: 正常列表 → 逐条输出，退出 0
code=0; run_list ok
assert_contains "$out" "找到 1 个开放 PR" "ok: 正确计数 1 条"
assert_contains "$out" "#2511 [open] feat: demo PR" "ok: 输出 PR 行"
assert_contains "$out" "mergeable=True" "ok: 输出 mergeable"
[[ "$code" == "0" ]] && { echo "[PASS] ok: 退出码 0"; PASS_COUNT=$((PASS_COUNT + 1)); } || { echo "[FAIL] ok: 期望退出码 0，实际 $code" >&2; exit 1; }

# 场景 2: 空列表 → 提示创建，退出 0
code=0; run_list empty
assert_contains "$out" "未找到开放 PR" "empty: 空数组提示创建"
[[ "$code" == "0" ]] && { echo "[PASS] empty: 退出码 0"; PASS_COUNT=$((PASS_COUNT + 1)); } || { echo "[FAIL] empty: 期望退出码 0，实际 $code" >&2; exit 1; }

# 场景 3（核心回归）: 401 错误字典 → 明确报错 + token 提示，退出 1；
# 绝不允许再出现"找到 1 个开放 PR"假阳性或 AttributeError 崩溃栈
code=0; run_list e401
assert_contains "$out" "查询 PR 失败 (HTTP 401)" "e401: 报出 HTTP 状态码"
assert_contains "$out" "Access token is expired" "e401: 透传 API 消息"
assert_contains "$out" "GITEE_TOKEN" "e401: 给出 token 更新提示"
assert_not_contains "$out" "找到" "e401: 不得假阳性计数"
assert_not_contains "$out" "Traceback" "e401: 不得泄漏 python 崩溃栈"
[[ "$code" == "1" ]] && { echo "[PASS] e401: 退出码 1"; PASS_COUNT=$((PASS_COUNT + 1)); } || { echo "[FAIL] e401: 期望退出码 1，实际 $code" >&2; exit 1; }

# 场景 4: 200 + 非 JSON（HTML）→ 报"不是合法 JSON"，退出 1
code=0; run_list badjson
assert_contains "$out" "响应不是合法 JSON" "badjson: 识别非 JSON 响应"
assert_not_contains "$out" "未找到开放 PR" "badjson: 不得把解析失败伪装成无 PR"
[[ "$code" == "1" ]] && { echo "[PASS] badjson: 退出码 1"; PASS_COUNT=$((PASS_COUNT + 1)); } || { echo "[FAIL] badjson: 期望退出码 1，实际 $code" >&2; exit 1; }

# 场景 5: 200 + 字典（期望数组）→ 报形状不符，退出 1
code=0; run_list dictaslist
assert_contains "$out" "期望JSON 数组" "dictaslist: 报出形状不符"
assert_contains "$out" "unexpected dict payload" "dictaslist: 透传 API message"
[[ "$code" == "1" ]] && { echo "[PASS] dictaslist: 退出码 1"; PASS_COUNT=$((PASS_COUNT + 1)); } || { echo "[FAIL] dictaslist: 期望退出码 1，实际 $code" >&2; exit 1; }

# 场景 6（核心回归）: 合并返回 405 但 PR 终态已合并 → 幂等放行，退出 0；
# 不得再报"合并失败"（2026-10-09 PR !2817 误报根因）
code=0; run_merge merge405 2511
assert_contains "$out" "幂等放行" "merge405: 识别重复合并为幂等成功"
assert_not_contains "$out" "合并失败" "merge405: 不得误报合并失败"
[[ "$code" == "0" ]] && { echo "[PASS] merge405: 退出码 0"; PASS_COUNT=$((PASS_COUNT + 1)); } || { echo "[FAIL] merge405: 期望退出码 0，实际 $code" >&2; exit 1; }

# 场景 7（对抗）: 合并返回 405 且 PR 仍 open → 真失败必须保留，退出 1；
# 防止"把 405 一律当成功"的过度放宽
code=0; run_merge merge405open 2511
assert_contains "$out" "合并失败 (HTTP 405)" "merge405open: 保留真实失败判定"
assert_contains "$out" "Pull Request 已合并或已关闭。" "merge405open: 仍透传 API 响应体"
assert_not_contains "$out" "幂等放行" "merge405open: 不得被幂等逻辑吞掉"
[[ "$code" == "1" ]] && { echo "[PASS] merge405open: 退出码 1"; PASS_COUNT=$((PASS_COUNT + 1)); } || { echo "[FAIL] merge405open: 期望退出码 1，实际 $code" >&2; exit 1; }

# 场景 8: 合并 405 且 PR 已关闭未合并 → 报状态提示 + 仍失败，退出 1
code=0; run_merge merge405closed 2511
assert_contains "$out" "状态为 closed（已关闭但未合并）" "merge405closed: 报出 closed 状态提示"
assert_contains "$out" "合并失败 (HTTP 405)" "merge405closed: 仍判定为失败"
assert_not_contains "$out" "幂等放行" "merge405closed: 不得当作幂等成功"
[[ "$code" == "1" ]] && { echo "[PASS] merge405closed: 退出码 1"; PASS_COUNT=$((PASS_COUNT + 1)); } || { echo "[FAIL] merge405closed: 期望退出码 1，实际 $code" >&2; exit 1; }

echo
echo "契约测试通过: ${PASS_COUNT} 项断言全绿"
