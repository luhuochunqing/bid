#!/usr/bin/env bash
# Input: flock lockfile, npm test command
# Output: serialized test execution with mutual exclusion
# Pos: scripts/ - Dev tooling and automation scripts
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
# scripts/test-queue.sh — 全局测试队列
#
# 使用 flock 在 /tmp/xiyu-vitest.lock 上做进程级互斥，
# 确保多 Agent 同时跑 npm run test:unit 时串行执行，
# 避免多 vitest 进程抢占 CPU。
#
# 接受所有 vitest 参数透传（--run, --watch, --reporter 等）。
#
set -euo pipefail

LOCKFILE="/tmp/xiyu-vitest.lock"
MAX_WAIT_SEC=300

if ! command -v flock &>/dev/null; then
  # macOS 没有 flock？用 mkdir 做目录锁兜底
  LOCKDIR="/tmp/xiyu-vitest.lockdir"
  mkdir "$LOCKDIR" 2>/dev/null || {
    echo "[test-queue] 另一个 vitest 正在运行，等待中..."
    # 轮询等待目录锁释放
    for _ in $(seq 1 $MAX_WAIT_SEC); do
      if mkdir "$LOCKDIR" 2>/dev/null; then
        break
      fi
      sleep 1
    done
  }
  trap "rmdir '$LOCKDIR' 2>/dev/null || true" EXIT
  exec npx vitest "$@"
fi

echo "[test-queue] 等待 vitest 锁...（超时 ${MAX_WAIT_SEC}s）"
exec flock --wait "$MAX_WAIT_SEC" "$LOCKFILE" npx vitest "$@"
