#!/usr/bin/env bash
# Input: none
# Output: validates gitee-pr-helper.sh response-parsing contract against a mock API
# Pos: scripts/test/gitee-pr-helper-contract.sh
# 维护声明: gitee-pr-helper.sh 的响应校验收敛在 gitee_get + require_json_response；
#           本契约用本地 mock 服务器覆盖 5 种响应形态（无需真实 GITEE_TOKEN）。
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

echo
echo "契约测试通过: ${PASS_COUNT} 项断言全绿"
