#!/usr/bin/env bash
# Input: none
# Output: validates gitee-token.sh resolution chain & gitee-pr-helper.sh auto-fallback
# Pos: scripts/test/gitee-token-contract.sh
# 维护声明: gitee-token.sh 是所有 Gitee API 脚本的唯一 token 解析入口
#           （env → ~/.config/xiyu/credentials.env → ~/.zshenv → git credential fill）。
#           本契约用隔离的假 HOME 覆盖 4 级优先级 + 失败提示 + helper 自动兜底
#           （lessons §215：agent 进程树多为 bash 不读 zsh rc，token 必须
#           脱离 shell 类型可解析）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TOKEN_SCRIPT="$REPO_ROOT/scripts/gitee-token.sh"
HELPER_SCRIPT="$REPO_ROOT/scripts/gitee-pr-helper.sh"

PASS_COUNT=0

assert_eq() {
  local got="$1" want="$2" description="$3"
  if [[ "$got" != "$want" ]]; then
    echo "[FAIL] $description" >&2
    echo "        got:  $got" >&2
    echo "        want: $want" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

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

assert_fails_with() {
  local description="$1"; shift
  local stderr_file="$1"; shift
  if "$@" >/dev/null 2>"$stderr_file"; then
    echo "[FAIL] unexpectedly succeeded: $description" >&2
    exit 1
  fi
  echo "[PASS] $description"
  PASS_COUNT=$((PASS_COUNT + 1))
}

# --- 隔离环境：每个场景独立的假 HOME，git 配置全指向假 HOME ---
TOK_ENV="tokenv-priority1-aaaa"
TOK_FILE="tokfile-priority2-bbbb"
TOK_ZSH="tokzsh-priority3-cccc"
TOK_GIT="tokgit-priority4-dddd"

FAKE_HOME="$(mktemp -d)"
STDERR_FILE="$FAKE_HOME/stderr.txt"
export HOME="$FAKE_HOME"
export GIT_CONFIG_GLOBAL="$FAKE_HOME/.gitconfig"
export GIT_CONFIG_SYSTEM=/dev/null
export GIT_CONFIG_NOSYSTEM=1
trap 'rm -rf "$FAKE_HOME"' EXIT

new_fake_home() {
  rm -rf "$FAKE_HOME"
  FAKE_HOME="$(mktemp -d)"
  HOME="$FAKE_HOME"
  GIT_CONFIG_GLOBAL="$FAKE_HOME/.gitconfig"
  STDERR_FILE="$FAKE_HOME/stderr.txt"
}

# --- 场景 1: 环境变量永远最优先（GITEE_TOKEN=xxx ./script 用法兼容） ---
new_fake_home
mkdir -p "$FAKE_HOME/.config/xiyu"
echo "export GITEE_TOKEN='$TOK_FILE'" > "$FAKE_HOME/.config/xiyu/credentials.env"
got="$(GITEE_TOKEN="$TOK_ENV" bash "$TOKEN_SCRIPT")"
assert_eq "$got" "$TOK_ENV" "优先级1: 环境变量胜过 credentials.env"

# --- 场景 2: env 为空 → credentials.env 兜底 ---
got="$(env -u GITEE_TOKEN bash "$TOKEN_SCRIPT")"
assert_eq "$got" "$TOK_FILE" "优先级2: credentials.env 被解析"

# --- 场景 2b: 引号/无 export 前缀两种写法都能解析 ---
echo "GITEE_TOKEN=\"$TOK_FILE\"" > "$FAKE_HOME/.config/xiyu/credentials.env"
got="$(env -u GITEE_TOKEN bash "$TOKEN_SCRIPT")"
assert_eq "$got" "$TOK_FILE" "优先级2b: 无 export 前缀 + 双引号写法兼容"

# --- 场景 3: env + credentials.env 都没有 → ~/.zshenv grep 兜底 ---
new_fake_home
echo "export GITEE_TOKEN='$TOK_ZSH'" > "$FAKE_HOME/.zshenv"
got="$(env -u GITEE_TOKEN bash "$TOKEN_SCRIPT")"
assert_eq "$got" "$TOK_ZSH" "优先级3: ~/.zshenv 被 grep（bash 不读 zshenv 的历史兼容）"

# --- 场景 3b: 调试输出命中来源，且不污染 stdout 的 token ---
out="$(env -u GITEE_TOKEN GITEE_TOKEN_DEBUG=1 bash "$TOKEN_SCRIPT" 2>"$STDERR_FILE")"
assert_eq "$out" "$TOK_ZSH" "调试模式: stdout 仍只有 token"
assert_contains "$(cat "$STDERR_FILE")" ".zshenv" "调试模式: stderr 标注命中来源"

# --- 场景 4: 前三级全空 → git credential fill 最后手段 ---
new_fake_home
printf '[credential]\n\thelper = store\n' > "$FAKE_HOME/.gitconfig"
echo "https://xiyu:$TOK_GIT@gitee.com" > "$FAKE_HOME/.git-credentials"
got="$(env -u GITEE_TOKEN bash "$TOKEN_SCRIPT")"
assert_eq "$got" "$TOK_GIT" "优先级4: git credential fill 兜底"

# --- 场景 5: 四级全空 → exit 1 + 可执行的修复指引 ---
new_fake_home
env -u GITEE_TOKEN bash "$TOKEN_SCRIPT" >/dev/null 2>"$STDERR_FILE" && {
  echo "[FAIL] 四级全空时应 exit 1" >&2; exit 1;
}
echo "[PASS] 四级全空: exit 1"
PASS_COUNT=$((PASS_COUNT + 1))
assert_contains "$(cat "$STDERR_FILE")" "credentials.env" "失败提示: 指向 credentials.env 修复路径"
assert_contains "$(cat "$STDERR_FILE")" "personal_access_tokens" "失败提示: 附申请入口"

# --- 场景 6: 集成 — gitee-pr-helper.sh 在 env 无 token 时自动兜底 ---
# mock API: /ok → 200 空数组；/e401 → 401 错误字典
MOCK_PORT=$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1",0)); print(s.getsockname()[1]); s.close()')

python3 - "$MOCK_PORT" <<'PY' &
import json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(sys.argv[1])

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        scenario = self.path.lstrip("/").split("/", 1)[0]
        if scenario == "e401":
            body, code = json.dumps({"message": "401 Unauthorized"}).encode(), 401
        else:
            body, code = json.dumps([]).encode(), 200
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
PY
MOCK_PID=$!

# 等 mock 就绪
for _ in $(seq 1 50); do
  if curl -s -o /dev/null "http://127.0.0.1:${MOCK_PORT}/ok"; then break; fi
  sleep 0.1
done

new_fake_home
mkdir -p "$FAKE_HOME/.config/xiyu"
echo "export GITEE_TOKEN='$TOK_FILE'" > "$FAKE_HOME/.config/xiyu/credentials.env"

out="$(env -u GITEE_TOKEN \
  GITEE_API_BASE="http://127.0.0.1:${MOCK_PORT}/ok" \
  bash "$HELPER_SCRIPT" list some-branch 2>"$STDERR_FILE")"
assert_contains "$out" "未找到开放 PR" "集成: helper 无 env token 时经解析链成功调 API"

out="$(env -u GITEE_TOKEN \
  GITEE_API_BASE="http://127.0.0.1:${MOCK_PORT}/e401" \
  bash "$HELPER_SCRIPT" list some-branch 2>"$STDERR_FILE" || true)"
assert_contains "$(cat "$STDERR_FILE")" "无效或已过期" "集成: 401 时提示更新令牌（新文案指向解析链）"

# --- 场景 7: 集成 — 四级全空时 helper 报带修复指引的错误 ---
new_fake_home
assert_fails_with "集成: helper 无任何 token 来源时报错" "$STDERR_FILE" \
  env -u GITEE_TOKEN \
  GITEE_API_BASE="http://127.0.0.1:${MOCK_PORT}/ok" \
  bash "$HELPER_SCRIPT" list some-branch
assert_contains "$(cat "$STDERR_FILE")" "找不到可用的 GITEE_TOKEN" "集成错误: 报错含解析链说明"

kill "$MOCK_PID" 2>/dev/null || true
wait "$MOCK_PID" 2>/dev/null || true

echo ""
echo "✅ gitee-token 契约测试全部通过: ${PASS_COUNT} 项断言"
