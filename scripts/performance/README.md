一旦我所属的文件夹有所变化，请更新我。

# 性能脚本目录

这里存放西域数智化投标管理平台的真实 API 压测脚本和运行说明。
脚本只面向真实后端、MySQL 和 Redis，不使用前端 demo 或 mock 数据路径。

工具链按"契约 / 入口 / 采集 / 归档 / 判定"分层，每个文件都能单独 `bash -n`（或 `node --check`）
并在没有服务、没有 k6 的机器上跑；判断"这份包能不能交付"的代码集中在判定层，不散落在入口里。

依赖方向是**单向**的：入口按 `契约 → 守卫 → 前置 → 生命周期 → 判定 → 归档` 的顺序 source，
判定层在**调用时**读取归档层设置的 `TOOLCHAIN_FAILURE` / `TOOLCHAIN_FAILURE_REASON`
（所以顺序不影响取值，单独 source 判定层做单测时按"工具链正常"兜底）。反向调用只有一处：
归档层在清单自判不可交付时调用判定层的 `add_delivery_blocker`——`evidence-archive.sh`
的文件头已声明这条依赖，改动任一层的 source 顺序前请先看那里。

| 文件 | 地位 | 功能 |
|------|------|------|
| `sales-200.k6.js` | 压测脚本 | 模拟 200 个销售在线用户访问登录、项目、看板、导出和标书上传入队链路 |
| `run-stress-test.sh` | 执行入口 | 压测执行与原始证据归档入口：调用 `k6-tests/load-test.js` 跑指定档位，产出完整证据包（原始 summary / 逐请求采样 / 控制台日志 / 监控数据 / `manifest.json`）。内置 `XIYU_STRESS_CONFIRMED=1` 与环境守卫，默认关闭业务写入；证据不全时以 exit 12 拒绝交付 |
| `capture-monitoring.sh` | 采集入口 | 压测期间系统 CPU/内存、应用 JVM 堆与 GC、MySQL 连接与慢查询、Redis 连接与内存的逐采样记录，产出 CSV 时间序列 + 可读日志 + `component-status.csv`（逐组件有效采样行数），替代手工监控截图。只在要求 `mysql` 时才强制 `MYSQL_PASSWORD`；单独运行默认只要求 `system` |
| `run-preflight.sh` | 入口-预检层 | 命令行解析、默认值、拓扑开关互斥、`XIYU_STRESS_CONFIRMED` 与环境守卫、档位/组件白名单、`--base-url` 拒收 userinfo 与目标主机解析、账号池非空 JSON 校验（未设置与显式空串分开判）、k6 与压测脚本存在性检查。node 校验顺序是**先探能不能跑、再看在不在 PATH 上**：找得到但跑不动的 node 按工具链拒绝（exit 1），不报成"账号池不合法"（exit 2） |
| `cli-guards.sh` | 入口-参数守卫 | 两个入口脚本共用的"带值选项缺值即 exit 2"守卫，避免把下一个选项当成值吞掉 |
| `monitor-lifecycle.sh` | 入口-子进程层 | 监控采集子进程的启停（`.ready` 握手、有界等待、超时强杀），以及采集侧问题清单 `MONITOR_ISSUES` |
| `evidence-archive.sh` | 归档层 | 产物落盘之后、对外交付之前的三步，**顺序即契约**：控制台规范化 → 凭证脱敏 → 残留扫描 → 写清单。同时记录 `TOOLCHAIN_FAILURE` / `TOOLCHAIN_FAILURE_REASON`（哪一步的哪件工具没跑成），由判定层决定退出码 |
| `evidence-completeness.sh` | 判定层 | 完整性判定与交付出口：算 `MISSING_ARTIFACTS` 与 `DELIVERY_BLOCKER_CODES`（+给人和给清单看的两份文案），决定 exit 1（工具链不可用）/ exit 12（证据不齐）还是透传 k6 退出码 |
| `exit-code-self-test.sh` | 判定-回归 | 纯离线跑通 0 / k6 码 / 1 / 12 四条出口共 32 项断言，含"无 node""坏 node shim（找得到但跑不动）""挂死的 node shim（调用它永不返回）""扫描器崩溃""文案表重复 code""账号池非法或为空"等反例；开头四项断言先自检夹具自身（夹具自带的 node 在 PATH 首位、坏扫描器替身不得自递归），否则"用例 6 挂死"会被读成环境问题。本文件只写断言，替身与子进程上界在下一行 |
| `self-test-fixtures.sh` | 判定-回归（夹具层） | 上面那个自检的**夹具层**：把 k6 / curl / mysql / redis-cli 与监控侧的 top / uptime / sysctl / vm_stat / uname 全部换成固定输出的替身，**自带一个能跑的 node**（并清掉宿主可能定义的 node shell 函数），外加"坏扫描器""坏 node shim""挂死的 node shim""无 node 的影子 PATH"四个构造，并给出 `check` / `run_runner`（逐用例超时上界）/ `fixture_self_check`。被 `source` 使用，不单独执行（直接跑 exit 2 并提示） |
| `monitor-spec.sh` | 契约库 | 监控证据的**唯一契约源**：组件集合、各 CSV 表头、"有效采样行"判定列号。runner / 采集器 / 清单生成器都读它，`--self-check` 在无服务环境下自检三方自洽 |
| `delivery-blockers.tsv` | 契约库 | 交付阻塞项文案的**唯一来源**（`code<TAB>文案`），shell 侧与 JS 侧共读，跨进程传递的是 **code** 而不是文案；格式违规（多 TAB/缺文案/重复 code）两侧一律硬失败（exit 1），追加新 code 时两侧都要加引用分支 |
| `monitor-parse.sh` | 采集-解析层 | 纯解析：`top`/`vm_stat`/`jstat`/`mysql`/`redis-cli` 的原始文本 → 数值字段，不碰进程、不连服务、不写文件。**解析不出来一律留空、不猜**：非数值不会被 awk 的强制转换变成 0（`jstat` 堆字段混进 `foo` 时整列留空，而不是报一条"堆已用 0MB"的合法采样） |
| `monitor-readers.sh` | 采集-读取层 | 四个组件（system / app-jvm / mysql / redis）各采一次、各写一行、各记一次状态 |
| `monitor-status.sh` | 采集-状态层 | 组件状态的唯一持有者，产出 `component-status.csv`，并判定"这次采集算不算成功" |
| `scrub-secrets.mjs` | 脱敏脚本 | 归档前就地替换证据包里的凭证（JWT / 敏感键值 / Bearer / URL userinfo），并在脱敏后对整包做同组检测器的残留扫描（`--scan`），结论写入 `manifest.json` 的 `secret_scan`；同时导出 `scrubText` / `DETECTORS` 供清单侧复用 |
| `write-run-manifest.mjs` | 清单脚本 | 装配并写出 `manifest.json`（运行元数据、SLA 门禁声明与击穿项、头部指标、逐组件监控状态、各产物 sha256、完整性判定），写盘前对清单自身脱敏复验，并把自己的独立结论回传给 runner |
| `manifest-verify.mjs` | 清单-复核层 | 独立复核：产物是否齐、监控 CSV 有没有数据、包内还有没有凭证残留、阻塞项文案（整表严格校验，表外 code 直接拒绝），与 runner 注入值取**并集** |
| `k6-evidence.mjs` | 清单-解释层 | 把 k6 的三份产物读成清单字段：头部指标、声明的门禁、被击穿的门禁、退出码、脚本路径 |

## 压测证据归档（推荐入口）

需要出具可被第三方复核的压测报告时，走证据归档入口而不是手工拼 k6 命令：

```bash
export XIYU_STRESS_CONFIRMED=1
# 前提：命令跑在 172.16.38.78 这台应用宿主上（监控采集器只能采本机资源，见下面"监控宿主"一节）。
bash scripts/performance/run-stress-test.sh \
  --profile standard \
  --base-url http://172.16.38.78:8080 \
  --monitor-local-target \
  --enable-writes
```

产出物落在 `docs/testing/stress-test-artifacts/<UTC时间戳>-<档位>/`，
目录结构、门禁判定口径与已知复现差异见 `docs/testing/stress-test-artifacts/README.md`。

**监控宿主（`--no-monitor` / `--monitor-local-target`）**：两个开关**互斥**，同时给出是自相矛盾的
调用（一个说"本包不含监控"、一个说"本机就是目标、就地采集"），一律以 exit 2 拒绝，而不是让其中
一个静默生效。都**不给**时按 `--base-url` 的主机判定，只有三种结果：

| `--base-url` 主机 | 给的开关 | `monitor_scope` | 结果 |
|---|---|---|---|
| 回环地址（`127.0.0.1` / `localhost` / `::1` / `0.0.0.0`） | 不给 | `local-target` | 跑。回环在定义上就是本机，这是机器可核验的事实，不是替操作者猜 |
| 非回环主机 | `--monitor-local-target` | `local-target-asserted` | 跑。操作者显式声明"本机就是这台目标宿主机" |
| 非回环主机 | `--no-monitor` | `skipped` | 跑，但本包**不可独立交付**（见 exit 12），需在目标机另跑采集器 |
| 非回环主机 | 不给 | —— | **exit 2**。目标不在本机、监控采不了它，把本机数据写进这个包等于拿压测机的负载冒充目标机的负载 |

账号池从环境变量 `XIYU_K6_USERS` 传入（JSON 数组，格式同 `--users`）。别用命令行传口令：
`--users '[{"username":…,"password":…}]'` 会把测试环境口令留在本机 `ps` 的 argv 里。

```bash
export XIYU_K6_USERS='[{"username":"<账号>","password":"<口令>"}]'
```

必须是一段**合法 JSON**：非空数组，每项含字符串 `username` / `password`。入口用 node 校验，
不合法 → exit 2，本机没有 node → exit 1。这不是形式主义：`k6-tests/load-test.js` 的
`getUserPool()` 在解析失败（或解析出空数组）时会 catch 并**静默回退到内置默认账号池**——
压测照跑、曲线照出、门禁照判，而实际使用的认证身份与声明的账号池无关。带省略号的模板
（`'[{...},…]'`）正是这类输入，因此文档里的示例一律写成单个合法元素。

**但占位符是拦不住的**：上例里的 `<账号>` / `<口令>` 满足"非空数组 + 字符串字段"，入口只看
形状、看不出这是占位符，原样提交会被放行——k6 拿一个不存在的账号去登录，认证成功率被击穿。
好消息是它不静默（登录会失败、曲线会掉下来），坏消息是**只能靠执行前替换**；把"业务上无效
的账号格式"写进入口判据，会把一条一次性的人工检查换成一条永久生效的通用规则，得不偿失。

**"没提供"与"提供了但是空串"是两件事**：`XIYU_K6_USERS` 未设置 = 操作者没打算给（这时
k6 会用自己的默认池，行为已在本文档与证据目录 README 里写明）；设成空串（`XIYU_K6_USERS=`、
CI secret 没注入、配置被清空）或 `--users ''` = 提供了但没提供成，同样以 **exit 2** 拒绝。
判据是变量**存在性**（`${VAR+x}`）而不是"值非空"——`${VAR:-}` 会把这两种情况塌缩成同一个
空串，于是配置丢失被静默读成"没打算给账号池"，接着就是上面那条静默回退。

**退出码**：`0` = k6 全部门禁通过且证据完整；k6 自身码 = k6 未全通过（`99` 表示有门禁被击穿）
但证据完整；`1` = 拒绝执行（未确认环境 / 命中生产特征 / 缺 k6 或压测脚本 / 账号池因缺 node
无法校验 / 工具链自身不可用，例如读不到 `monitor-spec.sh` 的规格、`delivery-blockers.tsv`
的文案格式违规或某个 code 缺文案，或清单脱敏后仍残留凭证）；
`2` = 用法错误（未知参数、带值选项缺值、拓扑开关矛盾、档位或组件名拼错、账号池不是合法 JSON、
监控宿主不明确、`--base-url` 带 userinfo）；
**`12` = k6 已跑完但证据包不可交付，优先于 k6 退出码上报**。

`1` 与 `12` 的分界是**结论有没有被算出来**，不是"坏得有多严重"：`12` 表示结论算出来了、
结论是"本包不齐"，按缺件补采即可；`1` 表示工具链没能给出结论（没有 node、扫描器或清单
生成器起不来、它们的输出不可解析），补采多少次都不会有变化。因此 `1` 优先于 `12`。
这条分界有离线回归可跑，不需要起任何服务或向任何环境施压：

```bash
bash scripts/performance/exit-code-self-test.sh   # 32 项，覆盖 0 / k6 码 / 1 / 12 四条出口
```

`bash scripts/performance/run-stress-test.sh --help` 打印头部注释里的用法正文
（取"第 1 行到第一个空行"，因此**头部注释内不得插入空行**——写死行号时增删几行会静默印出半截用法）。

证据完整性是硬门禁，且查的是"有数据"而不只是"文件存在"：默认要求
`system,app-jvm,mysql,redis` 四个监控组件各至少一行有效采样（必需字段齐备且为数值）。
目标环境确实没有某个组件时，用 `--monitor-required-components system,app-jvm,mysql`
显式收窄（必需产物清单与逐份 CSV 的复算都只覆盖声明的集合）；不声明就少一个组件 → exit 12，
而不是交付一份"CSV 都在、其中一份全是空值"的包。

除"产物缺失/为空/无有效数据行"外，还有几条 **exit 12** 的情形记在 `manifest.json` 的
`delivery_blockers` 里（产物一个不少，但这份包仍不能交付）：本次用了 `--no-monitor`
（`external_monitoring_required: true`，监控原始数据需由目标机单独产出后一并交付），
或归档前的凭证残留扫描未通过（`secret_scan.status != passed`）。

判定分两类、不能合并记账：`missing_artifacts`（该有的产物没到位）与 `delivery_blockers`
（产物一个不少、但这份包仍不能交付）。两者的处置方式不同——前者补跑或补采，后者要换一种
交付方式——混在一张清单里会让读者以为只差文件。

`delivery_blockers` 里的每一句话都出自 `delivery-blockers.tsv`（`<code><TAB><文案>`），
shell 侧与清单生成器读同一份，**runner 传给清单生成器的是 code、不是文案**——传文案时调用方
可以塞进任意表外句子直通对外交付物，单一来源就名存实亡了。两侧都必须整表校验：非注释非空行
恰好一个 TAB、`code` 形如 `[a-z][a-z0-9_]*`、文案非空且无首尾空白、`code` 不重复；任一项
不满足即 **exit 1**（shell 在创建证据目录之前就拒绝，JS 在读表时抛 `ToolchainError`）。
重复 code 尤其要拦：不拦的话 awk 取首行、`Map` 取末行，两侧各自"正常"地给出不同文案。

组件集合、各 CSV 表头与"有效采样行"的判定列号定义在 `monitor-spec.sh` 一处，
采集器与 runner 都 source 它；改动后跑 `bash scripts/performance/monitor-spec.sh --self-check` 自检。

**凭证残留扫描的口径**（`scrub-secrets.mjs --scan`，结论写入 `manifest.json` 的 `secret_scan`）：

| `scan.status` | 退出码 | 含义 |
|---|---|---|
| `passed` | 0 | 全部产物都在检测器的覆盖范围内，且没有命中 |
| `failed` | 1 | 扫到了残留（脱敏没覆盖到，或脱敏后又被写回） |
| `incomplete` | 3 | 有产物**扫不了**（二进制/非 UTF-8 文本，或符号链接与其它非常规文件——本脚本不跟链接），无法证明它干净。这是失败关闭：不把"扫不了"当成"没问题" |
| `unverified` | —— | 扫描器没输出可解析的结论，或退出码与它自己 stdout 的 `scan_status=` 对不上 |

状态取**退出码与 stdout 两路来源的交集**，任一路缺失或彼此不一致就收敛到 `unverified`：
单独一路都可能因为包装层截断（`| head`、只记 rc）或输出被重定向而给出"看上去正常"的假信号。
`failed` / `incomplete` 进 `delivery_blockers`，同样是 exit 12；而 `unverified`（含扫描器
根本起不来、node 缺失）属于"结论没算出来"，按 exit 1 上报——那时让调用方去补采是白费力气。

符号链接单独点名，因为它是**真实逃逸路径**而不是理论风险：`walk` 早先只收普通文件，于是
一个目录里放一条指向目录外凭证文件的软链，整包扫描会给出 `scan_scanned=0 / status=passed`、
exit 0——链接目标里的凭证可读，而扫描器连"存在这个文件"都没报告。现在符号链接与其余非常规
文件一律计入"未覆盖"，`scan_unscanned_files` 逐个列出路径。证据包要可交付，就把这些文件移出去
（`monitoring/monitoring.log` 与四份 CSV 已经覆盖了监控证据本身，截图不是必需件）。

**覆盖范围是整包**：`--scan` 跑在 `manifest.json` 写盘之前，所以它逐文件扫的是"除 `manifest.json`
外的全部产物"；清单自身由 `write-run-manifest.mjs` 在写盘前用**同一组检测器**（`scrubText` 从
`scrub-secrets.mjs` import，不另写一份判据）再脱敏一次，次数记在 `secret_scan.manifest_self_redactions`，
脱敏后复验仍残留就**拒绝写出清单**（exit 1，工具链自身不可用）。检测器共四类：JWT / 敏感键值 /
`Bearer` / URL userinfo（`http://user:口令@host`）——最后一类是因为 `--base-url` 的自由文本会进
`target_base_url`，只有前三类时它能整包扫过 `passed`。入口侧另有拦截：`--base-url` 带 userinfo
直接 exit 2。

`manifest.json` **不照抄 runner 注入的结论**：产物是否齐、监控 CSV 有没有数据、包内还有没有
凭证残留，都由 `write-run-manifest.mjs` 独立复算一遍，再与注入值取**并集**；清单自己判出
不可交付时以 exit 12 结束，runner 把这份判断并回自己的退出码（清单侧的 0 与 12 都表示"清单已写出"）。
runner 少算的（例如某份产物是二进制、扫描根本没覆盖到它）只有清单会先发现，所以是清单的判断
并回 runner，而不是反过来。

各档位时长：`smoke` 30s（1 VU 冒烟）、`standard` 3.5m（10→50→100 VU）、
`stress` 5m（20→100→200 VU）、`endurance` 12m（50 VU 长稳）。

## 运行示例

```bash
API_BASE_URL=http://127.0.0.1:18089 \
K6_USERNAME=staff \
K6_PASSWORD='Test@123' \
k6 run scripts/performance/sales-200.k6.js
```

如需使用多账号，传入 JSON 数组：

```bash
SALES_USERS='[{"username":"小王","password":"XiyuDemo!2026"},{"username":"张经理","password":"XiyuDemo!2026"}]' \
k6 run scripts/performance/sales-200.k6.js
```

## 大标书专项

`upload-init` 可以直接压测。`upload-complete` 依赖共享存储中已存在对应文件，默认关闭。
正式演练时应先由演练环境准备共享存储文件，再设置：

```bash
K6_COMPLETE_UPLOAD=true k6 run scripts/performance/sales-200.k6.js
```

同步 `bid-agent` 招标文件解析链路存在内存与响应时间风险，默认不参与 200 并发主脚本。
需要专项验证时使用 `K6_BID_AGENT_SYNC_PARSE=true` 和 `TENDER_FILE_PATH=/path/to/tender.docx` 单独小并发执行。

本机如果后端仍使用默认 `/data/shared/tenders` 且 `/data` 不可写，可以先跳过上传入队链路调试其他接口：

```bash
API_BASE_URL=http://127.0.0.1:18089 \
K6_USERNAME=staff \
K6_PASSWORD='Test@123' \
K6_SKIP_EXPORT=true \
K6_SKIP_TENDER_UPLOAD=true \
k6 run --iterations 1 --vus 1 --no-thresholds scripts/performance/sales-200.k6.js
```

`K6_SKIP_EXPORT=true` 仅用于本机冒烟。正式压测应保留导出链路，以暴露导出线程、权限和资源限制问题。
