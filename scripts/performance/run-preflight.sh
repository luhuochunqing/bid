#!/usr/bin/env bash
# Input: run-stress-test.sh 的命令行参数（"$@"）、以及 SCRIPT_DIR / REPO_ROOT 等已由入口建立的上下文
# Output: 校验通过的运行参数（PROFILE / BASE_URL / OUT_ROOT / ENABLE_WRITES / MONITOR_SCOPE /
#         MONITOR_INTERVAL / MONITOR_REQUIRED_COMPONENTS / RUN_LABEL / K6_SCRIPT / K6_USERS_JSON / TARGET_HOST）
# Pos: scripts/performance/ - 压测入口的**参数解析与前置校验**层：读完参数就决定"这次运行合法吗"
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
#
# 为什么单独一层：这一层里每一条校验都对应一次**静默出错**的实测——拼错档位会被 k6 静默
# 降级成 standard、--monitor-interval abc 会产出一包 null 产物、组件名拼错会把"没采到"
# 退化成"没这项要求"、非回环目标会把压测机的负载冒充成目标机的负载。它们必须在创建证据
# 目录、curl、k6 **之前**全部跑完；和压测执行混在一个文件里时，"校验是否都排在副作用之前"
# 这件事只能靠通读全文来确认。
#
# 本层不做任何有副作用的动作（不建目录、不发请求、不起进程），全部问题以 usage 错误
# （exit 2）或拒绝执行（exit 1）结束。两个退出码的分工见 run-stress-test.sh 的头部注释。

# 校验失败一律由本函数内直接 exit —— 它是入口脚本的同一个 shell（source 进来的函数），
# 因此不需要调用方接返回值再判断，也就没有"忘了判断返回值"这条漏检路径。
run_preflight_parse() {
  PROFILE="standard"
  BASE_URL=""
  OUT_ROOT="${REPO_ROOT}/docs/testing/stress-test-artifacts"
  ENABLE_WRITES=0
  RUN_MONITOR=1
  MONITOR_LOCAL_TARGET=0
  MONITOR_INTERVAL=5
  # 本包声称"监控证据完整"就必须说清完整到什么程度：组件集合里每个组件各至少要有一行有效采样。
  # 由调用方声明而不是采集器替它假定——目标环境确实没有 Redis 时，操作者应当显式
  # 收窄成 system,app-jvm,mysql，而不是让一份缺组件的包以"完整"的名义交付出去。
  # 默认值直接由 monitor-spec.sh 的组件全集派生，本文件不再另抄一份组件名单。
  MONITOR_REQUIRED_COMPONENTS="$(printf '%s' "$MONITOR_COMPONENTS" | tr ' ' ',')"
  RUN_LABEL=""
  # 账号池默认从环境变量取，而不是只认命令行：`--users '[{...,"password":"..."}]'` 会把
  # 测试环境口令写进进程 argv，同机任何账号 `ps aux` 就能读到。文档因此无法给出一个
  # 既真实可跑、又不泄露口令的 --users 示例。XIYU_K6_USERS 让口令只存在于调用者的环境里。
  K6_USERS_JSON="${XIYU_K6_USERS:-}"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --profile) require_arg "$@"; PROFILE="$2"; shift 2 ;;
      --base-url) require_arg "$@"; BASE_URL="$2"; shift 2 ;;
      --out) require_arg "$@"; OUT_ROOT="$2"; shift 2 ;;
      --label) require_arg "$@"; RUN_LABEL="$2"; shift 2 ;;
      --enable-writes) ENABLE_WRITES=1; shift ;;
      --no-monitor) RUN_MONITOR=0; shift ;;
      --monitor-local-target) MONITOR_LOCAL_TARGET=1; shift ;;
      --monitor-interval) require_arg "$@"; MONITOR_INTERVAL="$2"; shift 2 ;;
      --monitor-required-components) require_arg "$@"; MONITOR_REQUIRED_COMPONENTS="$2"; shift 2 ;;
      --k6-script) require_arg "$@"; K6_SCRIPT="$2"; shift 2 ;;
      --users) require_arg "$@"; K6_USERS_JSON="$2"; shift 2 ;;
      # 用法正文由入口脚本持有（它才是操作者面对的那个文件），因此这里回印 $0。
      # sourced 文件里 $0 仍是入口脚本路径，不是本文件。
      # 取"第 1 行到第一个空行"而不是写死行号：头部注释增删几行时写死的区间会静默印出
      # 半截用法（多印/少印都不会报错）。入口脚本头部注释内因此不得插入空行，已在那侧注明。
      -h|--help) sed -n '1,/^$/p' "$0"; exit 0 ;;
      *) echo "run-stress-test: 未知参数 $1" >&2; exit 2 ;;
    esac
  done

  # 两个开关同时给出是自相矛盾的调用：--no-monitor 说"本包不含监控"，--monitor-local-target
  # 说"本机就是目标、就地采集"。此前两者都接受，实际由判定顺序决定谁生效（--no-monitor 在前），
  # 于是 --monitor-local-target 被静默忽略：操作者以为在本机采了监控，拿到的却是一个
  # monitor_scope=skipped 的包。压测本身照常跑完，没有任何一处会提示这个矛盾。
  # 这个检查必须在 XIYU_STRESS_CONFIRMED 守卫**之前**：它是纯粹的用法错误，与"环境是否已确认"
  # 无关，放在守卫之后会让未导出该变量的调用（例如静态回归）拿到 exit 1 而不是 exit 2。
  if [[ "$RUN_MONITOR" == "0" ]] && [[ "$MONITOR_LOCAL_TARGET" == "1" ]]; then
    echo "run-stress-test: --no-monitor 与 --monitor-local-target 不能同时使用，请二选一" >&2
    echo "                 --no-monitor             本包不含监控证据（改在目标机上单独跑 capture-monitoring.sh）" >&2
    echo "                 --monitor-local-target   本机就是被压的目标宿主机，就地采集监控" >&2
    exit 2
  fi

  if [[ "${XIYU_STRESS_CONFIRMED:-}" != "1" ]]; then
    cat >&2 <<'EOF'
run-stress-test: 拒绝执行 —— 压测会向目标环境施加真实负载。
请先确认目标环境并显式导出：
  export XIYU_STRESS_CONFIRMED=1
EOF
    exit 1
  fi

  if [[ -z "$BASE_URL" ]]; then
    echo "run-stress-test: 必须通过 --base-url 指定目标环境（例如 http://172.16.38.78:8080）" >&2
    exit 2
  fi

  # URL 里带 userinfo（http://user:口令@host:8080）一律拒绝，两个理由都是静默出错：
  #   1) 它会**逐字写进 manifest.json 的 target_base_url**，而整包凭证扫描排在该文件写盘之前，
  #      等于把一份可用凭证放进一个自称"扫描通过"的证据包里（清单自身的补扫只是第二道保险）；
  #   2) 下面的主机解析取的是 `://` 之后到第一个 ':' 的一段，带 userinfo 时会解析出 "user"，
  #      于是监控宿主判定、manifest 的 target_host 全部记成这个不存在的"主机"，且全程无报错。
  local _authority
  _authority="$(printf '%s' "$BASE_URL" | sed -nE 's#^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]*).*#\1#p')"
  case "$_authority" in
    *@*)
      echo "run-stress-test: --base-url 不能包含用户信息（user:pass@host）：$BASE_URL" >&2
      echo "                 该口令会被写进证据包的 manifest.json，且会让主机解析取到 'user' 这一段。" >&2
      echo "                 需要认证请把凭据放进环境变量，不要写在 URL 里。" >&2
      exit 2
      ;;
  esac

  # 档位必须白名单校验。k6-tests/load-test.js 用的是 `STAGE_CONFIGS[PROFILE] || STAGE_CONFIGS.standard`，
  # 拼错档位不会报错，而是**静默按 standard 跑**——而 manifest 记的是操作者输入的那个错误档位，
  # 于是"实际负载曲线"和"证据里写的档位"对不上，第三方按它做容量结论就是错的。
  case "$PROFILE" in
    smoke|standard|stress|endurance) ;;
    *)
      echo "run-stress-test: --profile 仅支持 smoke|standard|stress|endurance（收到 '${PROFILE}'）" >&2
      echo "                未校验时 k6 会静默回退到 standard，而 manifest 仍记录你输入的值。" >&2
      exit 2
      ;;
  esac

  TARGET_HOST="$(printf '%s' "$BASE_URL" | sed -nE 's#^[a-zA-Z][a-zA-Z0-9+.-]*://(\[[^]]*\]|[^:/?#]+).*#\1#p')"
  if [[ -z "$TARGET_HOST" ]]; then
    echo "run-stress-test: 无法从 --base-url 解析出主机名：$BASE_URL" >&2
    exit 2
  fi
  # 监控采集器采的是**它自己所在这台机器**的 CPU/内存/JVM，以及它连得到的 MySQL/Redis。
  # 目标不在本机时，把这些数据放进以远端 base_url 命名的证据包里，等于用压测机的负载
  # 冒充目标环境的负载——报告第五章那些"目标系统资源稳定"的结论会因此完全失真，
  # 而证据包看上去毫无异常。所以目标不是本机时不存在默认值，必须由操作者显式声明。
  TARGET_IS_LOCAL=0
  case "$TARGET_HOST" in
    127.0.0.1|localhost|::1|\[::1\]|0.0.0.0) TARGET_IS_LOCAL=1 ;;
  esac

  MONITOR_SCOPE=""
  if [[ "$RUN_MONITOR" == "0" ]]; then
    MONITOR_SCOPE="skipped"
  elif [[ "$TARGET_IS_LOCAL" == "1" ]]; then
    # 回环地址在定义上就是本机，这一条是机器可核验的，不是替操作者猜。
    MONITOR_SCOPE="local-target"
  elif [[ "$MONITOR_LOCAL_TARGET" == "1" ]]; then
    MONITOR_SCOPE="local-target-asserted"
  else
    # 变量一律写成 ${VAR} 带花括号：本仓库脚本正文是中文，裸变量名紧跟全角字符时
    # （例如变量名后面直接跟一个全角左括号），多字节字符在 UTF-8 locale 下会被 bash
    # 视作标识符字符吞进变量名，于是报 `TARGET_HOST?: unbound variable` 并中止——实测踩过。
    cat >&2 <<EOF
run-stress-test: 监控宿主不明确，拒绝执行。
  --base-url 指向 ${TARGET_HOST}（非本机），但监控采集器只能采**本机**资源。
  直接把本机数据写进这个证据包，会把压测机的负载当成 ${TARGET_HOST} 的负载交付。
请明确二选一：
  --monitor-local-target   本机就是被压的目标宿主机（此时才允许本机采集）
  --no-monitor             本包不含监控证据；请改在目标机上单独跑 capture-monitoring.sh
EOF
    exit 2
  fi

  # 监控间隔在**启动压测之前**校验。采集器自己是后台进程，它那侧校验失败只会往日志里写一行
  # 然后退出，而 k6 照跑不误——跑完一轮、monitoring/ 却是空的，操作者看到的仍是"压测结束"。
  # 实测过这条路径：--monitor-interval abc 会产出 5 个监控产物全为 null 的证据包。
  if [[ ! "$MONITOR_INTERVAL" =~ ^[0-9]+$ ]] || [[ "$MONITOR_INTERVAL" -lt 1 ]]; then
    echo "run-stress-test: --monitor-interval 必须是 ≥1 的整数秒（收到 '${MONITOR_INTERVAL}'）" >&2
    exit 2
  fi

  # 必需组件名同样白名单校验：拼错一个名字（sys 而不是 system）会让逐组件判定找不到对应
  # 规格 —— 那本该是"这一项没采到"，却会静默退化成"没有这项要求"。白名单来自 monitor-spec.sh，
  # 不在本文件另抄一份。
  local _c _normalized=""
  for _c in $(printf '%s' "$MONITOR_REQUIRED_COMPONENTS" | tr ',' ' '); do
    case " ${MONITOR_COMPONENTS} " in
      *" ${_c} "*) ;;
      *)
        echo "run-stress-test: --monitor-required-components 只支持 ${MONITOR_COMPONENTS// /|}（收到 '${_c}'）" >&2
        exit 2
        ;;
    esac
    _normalized="${_normalized} ${_c}"
  done
  MONITOR_REQUIRED_COMPONENTS="${_normalized# }"
  if [[ -z "$MONITOR_REQUIRED_COMPONENTS" ]]; then
    echo "run-stress-test: --monitor-required-components 不能为空；不含监控证据请改用 --no-monitor" >&2
    exit 2
  fi

  # 大小写不敏感后再匹配：只写 *prod*|*Prod*|*PROD* 会让 pRODuction-host、PrOd-host 直接放行。
  # 匹配整个 URL（含路径）而不是只匹配 host，宁可多拦也不漏拦。
  local _base_url_lc
  _base_url_lc="$(printf '%s' "$BASE_URL" | tr '[:upper:]' '[:lower:]')"
  case "$_base_url_lc" in
    *prod*|*xiyu-bid.com*|*bid.xiyu*)
      echo "run-stress-test: 拒绝执行 —— BASE_URL 命中生产特征：$BASE_URL" >&2
      exit 1
      ;;
  esac

  if ! command -v k6 >/dev/null 2>&1; then
    echo "run-stress-test: 未安装 k6（macOS: brew install k6）" >&2
    exit 1
  fi

  if [[ ! -f "$K6_SCRIPT" ]]; then
    echo "run-stress-test: k6 脚本不存在：$K6_SCRIPT" >&2
    exit 1
  fi
}
