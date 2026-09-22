#!/usr/bin/env bash
# Input: run-stress-test.sh 的上下文（SCRIPT_DIR / RUN_DIR / BASE_URL / MONITOR_SCOPE / MONITOR_INTERVAL / MONITOR_REQUIRED_COMPONENTS）
# Output: 监控采集子进程的启停（含 .ready 握手、有界等待、超时强杀），以及采集侧问题清单 MONITOR_ISSUES
# Pos: scripts/performance/ - 压测入口的**监控子进程生命周期**层（起、等就绪、停、兜底杀）
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：这段代码是"压测跑完了但监控没采到"这类缺陷的高发区，且全部与信号、
# 竞态、有界等待有关——它们和参数解析、证据判定混在一个文件里时，既难单看也难单测。
# 这里的每个分支都对应一次实测踩到的失败：
#   - 采集器启动要 `rm -f .stop`，而 k6 可能秒退并在那之前 touch .stop → 停止标记被删、
#     采集器采满 900s 被强杀、component-status.csv 压根不生成（→ .ready 就绪握手）；
#   - Ctrl-C 时只 kill 不 exit，信号会被吞掉、脚本照常打印"压测结束"并以 0 退出（→ trap 带 exit 143）；
#   - `wait` 无界，采集器卡住整轮压测永久挂起（→ 有界等待 + 强杀）。
# 契约：MONITOR_SCOPE=skipped 时两个函数都是 no-op；调用方只需无脑按顺序调用。

# 启动采集器并等它就绪。设置 MONITOR_PID（未启动时为空串）。
monitor_collector_start() {
  MONITOR_PID=""
  MONITOR_ISSUES=""
  if [[ "$MONITOR_SCOPE" == "skipped" ]]; then
    return 0
  fi
  # 走到这里意味着"本机就是被压的目标宿主机"（回环地址，或操作者用 --monitor-local-target 声明）。
  # 按监听端口反查本机应用进程；同机多 java 进程时端口是最准的线索。
  APP_PORT="$(printf '%s' "$BASE_URL" | sed -nE 's#^[a-z]+://[^:/]+:([0-9]+).*#\1#p')"
  local monitor_args=(--out "$RUN_DIR" --interval "$MONITOR_INTERVAL" --duration 900
                      --required-components "$MONITOR_REQUIRED_COMPONENTS")
  if [[ -n "$APP_PORT" ]]; then monitor_args+=(--app-port "$APP_PORT"); fi
  bash "${SCRIPT_DIR}/capture-monitoring.sh" "${monitor_args[@]}" &
  MONITOR_PID=$!
  # Ctrl-C / TERM 时不要把采样器留成孤儿进程继续采到 900s。
  # 必须带 exit：trap 处理器返回后 bash 会从下一条命令继续执行，只 kill 不 exit 的话
  # 信号被吞掉——k6 照常把负载跑完，脚本还输出"✅ 全部阈值通过"并以 0 退出，
  # 监督者（CI / timeout / supervisor）看到的是"成功"而非"被中止"。
  # 143 = 128 + SIGTERM，符合"被 TERM 终止"的通行约定。
  trap 'kill "$MONITOR_PID" 2>/dev/null || true; exit 143' INT TERM

  # 必须等采集器写出 .ready 才能开始压测（原因见 capture-monitoring.sh 里的说明，以及
  # 本文件头部记的那次实测）。等不到也不中止压测：把原因记进 MONITOR_ISSUES，
  # 让完整性判定去拒收这份包——中止压测会连 k6 侧证据一起丢掉。
  local ready_wait=0
  while [[ ! -f "${RUN_DIR}/monitoring/.ready" ]] && [[ $ready_wait -lt 30 ]]; do
    # 采集器启动即失败（参数非法、缺依赖）时不再空等，让它下面按退出码走正常告警路径
    if ! kill -0 "$MONITOR_PID" 2>/dev/null; then break; fi
    sleep 1
    ready_wait=$((ready_wait + 1))
  done
  if [[ ! -f "${RUN_DIR}/monitoring/.ready" ]]; then
    echo "run-stress-test: 监控采集器未在 30s 内就绪（进程可能已退出），本次监控证据大概率不完整" >&2
    MONITOR_ISSUES="${MONITOR_ISSUES}监控采集器未在 30s 内就绪（明细见 monitoring/monitoring.log）"$'\n'
  fi
  echo "run-stress-test: 监控采集已启动 (pid=${MONITOR_PID}, interval=${MONITOR_INTERVAL}s, 采集口径=${MONITOR_SCOPE}, app_port=${APP_PORT:-<未解析>})"
}

# 停止采集器：先给停止标记并等它自己收尾，超时有界后强杀。
# 采集器非 0 退出（含 exit 3 = 必需组件没采到数据）时把原因追加进 MONITOR_ISSUES。
monitor_collector_stop() {
  if [[ -z "$MONITOR_PID" ]]; then
    return 0
  fi
  touch "${RUN_DIR}/monitoring/.stop" 2>/dev/null || true
  # 采样器上限 900s；wait 是无界的，采集器一旦卡住整轮压测就永久挂起，
  # 因此有界等待后再兜底 kill。
  local monitor_wait=0 monitor_forced=0 monitor_exit=0
  while kill -0 "$MONITOR_PID" 2>/dev/null && [[ $monitor_wait -lt 30 ]]; do
    sleep 1
    monitor_wait=$((monitor_wait + 1))
  done
  if kill -0 "$MONITOR_PID" 2>/dev/null; then
    echo "run-stress-test: 监控采集未在 30s 内退出，强制结束（pid=${MONITOR_PID}）" >&2
    kill "$MONITOR_PID" 2>/dev/null || true
    monitor_forced=1
  fi
  wait "$MONITOR_PID" 2>/dev/null || monitor_exit=$?
  rm -f "${RUN_DIR}/monitoring/.stop"
  echo "run-stress-test: 监控采集已停止"

  # 采集器可能启动即失败：interval 非法、mysql/redis-cli 缺失、宿主命令不可用。
  # 它的报错只落在自己的 stderr 里，而 k6 的负载已经照常跑完了——不在这里显式检查的话，
  # 交付出去的就是一个 monitoring/ 全空、却仍然打印"压测结束 ✅"的证据包。
  # 实测过这条路径（--monitor-interval abc）：5 个监控产物全为 null，runner 不告警。
  # 被强制结束的那次非 0 退出已在上面单独说明过，不重复告警。
  if [[ "$monitor_forced" != "1" ]] && [[ "$monitor_exit" != "0" ]]; then
    echo "run-stress-test: 监控采集器以退出码 ${monitor_exit} 结束，证据包缺少可用的监控数据" >&2
    if [[ "$monitor_exit" == "3" ]]; then
      MONITOR_ISSUES="${MONITOR_ISSUES}采集器判定必需组件未采到有效数据（exit 3），明细见 monitoring/component-status.csv"$'\n'
    else
      MONITOR_ISSUES="${MONITOR_ISSUES}监控采集器以退出码 ${monitor_exit} 结束（非组件判定失败），明细见 monitoring/monitoring.log"$'\n'
    fi
  fi
  # 缺哪些监控文件、哪些监控文件没有有效数据行，不在这里报——统一由完整性判定汇总，
  # 避免同一件事告警两遍。
}
