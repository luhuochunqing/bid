#!/usr/bin/env bash
# Input:  remote service name (default: xiyu-bid-backend), journalctl access via sudo
# Output: SDK registration status, Bean scan status, Kafka consumer thread status, and overall result
# Pos:   scripts/release/ - 部署验证与运维辅助脚本
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
# verify-org-sdk.sh — 组织架构 SDK 启动验证
# 检查西域事件库 SDK 是否在服务启动后成功完成注册、Bean 扫描、以及 Kafka 消费线程启动。
# 必须在远程目标主机上运行，依赖 journalctl 和 systemctl（需要 sudo）。
set -euo pipefail

BACKEND_SERVICE="${BACKEND_SERVICE_NAME:-xiyu-bid-backend}"
MAX_WAIT_SEC="${VERIFY_SDK_MAX_WAIT:-30}"
POLL_INTERVAL_SEC=2

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { printf "${GREEN}[SDK-CHECK]${NC} %s\n" "$*"; }
log_warn()  { printf "${YELLOW}[SDK-CHECK]${NC} %s\n" "$*"; }
log_error() { printf "${RED}[SDK-CHECK]${NC} %s\n" "$*"; }

# Determine the log window: use service start time if available from systemd.
SINCE_RAW="3 min ago"
if TS_LINE="$(sudo systemctl show -p ActiveEnterTimestamp "$BACKEND_SERVICE" 2>/dev/null)"; then
  TS_LINE="${TS_LINE#ActiveEnterTimestamp=}"
  TS_CLEAN="$(echo "$TS_LINE" | sed -n 's/^[A-Z][a-z][a-z] //; s/ [A-Z]*$//p')"
  if [[ -n "$TS_CLEAN" ]]; then
    SINCE_RAW="$TS_CLEAN"
  fi
fi

search_logs() {
  sudo journalctl -u "$BACKEND_SERVICE" --since="$SINCE_RAW" --no-pager 2>/dev/null || true
}

log_info "开始组织架构 SDK 启动验证 (service=$BACKEND_SERVICE, since=$SINCE_RAW, timeout=${MAX_WAIT_SEC}s)"

ELAPSED=0
REGISTER_OK=false
SCAN_OK=false
KAFKA_OK=false

while [[ $ELAPSED -lt $MAX_WAIT_SEC ]]; do
  LOGS="$(search_logs)"

  if [[ "$REGISTER_OK" == "false" ]]; then
    if echo "$LOGS" | grep -q "Kafka consumer started successfully"; then
      REGISTER_OK=true
      SCAN_OK=true
      KAFKA_OK=true
      log_info "✅ 组织架构 SDK 启动链全部完成 (register → scan → kafka start)"
      echo "$LOGS" | grep "服务注册响应结果" | tail -1 || true
      echo "$LOGS" | grep "缓存数量" | tail -1 || true
      exit 0
    fi
    if echo "$LOGS" | grep -q "service client up !!!"; then
      REGISTER_OK=true
      log_info "✅ 事件总线注册已确认 (service client up)"
    fi
  fi

  if [[ "$REGISTER_OK" == "true" && "$SCAN_OK" == "false" ]]; then
    if echo "$LOGS" | grep -q "初始化缓存所有的Spring Bean 完成"; then
      SCAN_OK=true
      log_info "✅ @AcceptEvent Bean 扫描完成"
    fi
  fi

  if [[ "$SCAN_OK" == "true" && "$KAFKA_OK" == "false" ]]; then
    if echo "$LOGS" | grep -qE "Kafka consumer started|partitions have reassigned"; then
      KAFKA_OK=true
      log_info "✅ Kafka 消费线程已启动"
      log_info "✅ 组织架构 SDK 启动验证全部通过"
      exit 0
    fi
  fi

  sleep "$POLL_INTERVAL_SEC"
  ELAPSED=$((ELAPSED + POLL_INTERVAL_SEC))
done

# Timeout — report what we know
if [[ "$REGISTER_OK" == "false" ]]; then
  log_error "❌ SDK 未向事件总线注册"
  log_error "   请检查: XIYU_ORG_EVENT_SDK_ENABLED=true, 网络连通性, backend.env 配置"
elif [[ "$SCAN_OK" == "false" ]]; then
  log_error "❌ SDK 已注册但未完成 @AcceptEvent Bean 扫描"
elif [[ "$KAFKA_OK" == "false" ]]; then
  log_error "❌ SDK 已注册并扫描完成但 Kafka 消费线程未启动"
  log_error "   请检查: KafkaProcessor bean, Kafka broker 连通性"
fi

log_error "服务启动以来的 SDK 相关日志:"
sudo journalctl -u "$BACKEND_SERVICE" --since="$SINCE_RAW" --no-pager 2>/dev/null \
  | grep -iE "com.ehsy|org-event-sdk-kafka|sdk|service client|cachebean|kaffka|register" || true

exit 1
