一旦我所属的文件夹有所变化，请更新我。

# 压测原始证据包目录

这里存放 `scripts/performance/run-stress-test.sh` 每次执行产出的**原始压测证据**：
k6 原始结果文件、逐请求采样、压测期间的系统/JVM/MySQL/Redis 监控时间序列，以及一份带
sha256 的 `manifest.json` 清单。

**它的用途**：让压测报告里的每一个数字都能被第三方独立复核——客户验收、内部审计、
或事后复盘时不必依赖"当时截图还在不在"。

---

## 一、当前证据状态（重要，先读这段）

| 产物 | 状态 |
| :--- | :--- |
| 压测脚本（`k6-tests/load-test.js`、`scripts/generate-stress-test-data.mjs`） | ✅ 已在仓库中，随 `5b2c06993` 一起提交 |
| 压测执行与证据归档工具链（本目录 + `scripts/performance/run-stress-test.sh`） | ✅ 已建立（本目录首次引入） |
| **2026-08-28 那次实测的原始数据、结果文件、监控截图** | ❌ **仓库与开发机上均不存在** |

`docs/testing/STRESS_TEST_REPORT_2026-08-28.md` 报告正文（11,092 请求 / 52.5 QPS / 100% 成功率等）
**没有对应的原始产物落盘**：当时只沉淀了脚本、操作指南和报告，k6 的 `--summary-export`
与监控采样都没有归档。原始文件只可能仍在当时执行压测的那台机器或执行人手上。

> 因此本目录**不能**用于"补一个看起来像原始数据的文件"。要拿到原始证据只有两条路：
> ① 向当时的执行人要回原始文件；② 用下文的工具链**重新跑一次**，产出属于该次运行的、
> 可核验的原始证据。第二次跑出来的数据对应的是第二天的代码与数据状态，不能冒充 8-28 那次。

---

## 二、证据包目录结构

每次运行产出 `docs/testing/stress-test-artifacts/<UTC时间戳>-<档位>[-<标签>]/`：

```
20260921T062456Z-smoke-harness-selfcheck/   # 下面 examples/ 里保留的那次自检运行
├── manifest.json          # 清单：运行元数据 + SLA 门禁声明与击穿项 + 头部指标 + 逐组件监控状态 + 各产物 sha256
├── k6-summary.json        # k6 原始汇总（--summary-export），各接口 avg/med/p90/p95/p99
├── k6-raw-samples.csv     # k6 逐指标逐时刻原始采样（--out csv），画图/复核都用它
├── k6-console.log         # k6 控制台完整输出（含 checks 明细与阈值告警原文）
├── k6-exit-code           # k6 退出码：0=门禁全过，99=有门禁被击穿（abortOnFail 提前中止实测也是 99）
└── monitoring/
    ├── system.csv         # 采样时刻、CPU user/sys/idle、load1/5、内存 used/total
    ├── app-jvm.csv        # 应用进程堆/元空间/线程数/YGC/FGC/CGC/进程 CPU/RSS
    ├── mysql.csv          # Threads_connected / running、Slow_queries、Innodb_row_lock_waits、Aborted_connects、Questions
    ├── redis.csv          # connected_clients、used_memory、instantaneous_ops_per_sec、命中率计数
    ├── component-status.csv # 逐组件状态：required / available / **有效采样行数** / 失败原因
    └── monitoring.log     # 上述四项的可读快照行（需要"截图"时直接截这里，或据此作图）
```

`monitoring.log` 就是监控截图的**可复核替代品**：它是压测期间逐采样的真实文本记录，
比一张无法校验时间戳的截图更硬。

`monitoring/component-status.csv` 回答的是"这份监控到底有没有数据"，而不是"文件在不在"：
采集器在 mysql 客户端缺失、Redis 不可达、应用进程找不到时，留下的正是只有表头或整行空值的
CSV——非空、有行数，却撑不起报告第五章的任何结论。该文件逐组件记录**必需字段齐备且为数值的
行数**（`usable_samples`）与失败原因，`required=1` 的组件只要一行都没有，采集器就以
**exit 3** 结束，runner 随即以 **exit 12** 判证据不可交付。

`examples/` 下保留了一次**工具链自检运行**（1 VU 冒烟，本机 dev 环境），
用于展示证据包的真实形态。**它不是 2026-08-28 报告的证据，不得引用为报告数据。**
目录名带 UTC 时间戳，每次重新生成都会变，以 `examples/` 下实际存在的那个目录为准。

该自检运行同时是**采集脚本的回归样本**：`monitoring/system.csv` 的内存占用按本机真实页大小
（Apple Silicon 16 KB）折算，`monitoring/mysql.csv` 按变量名而非返回行序取值。

**示例包必须由一次运行原子产出，不允许手工拼装或事后搬家。** 三类可核验痕迹会互相印证
"它们来自同一次调用"：`k6-console.log` 与 `monitoring/monitoring.log` 里记录的输出路径、
`manifest.json` 的 `k6_script` 相对路径、以及各产物的 sha256。任何一项与其余不一致，
第三方就能立刻判定该包被改动过。需要更换示例时请重跑，不要编辑目录里的数字或路径：

```bash
export XIYU_STRESS_CONFIRMED=1
export MYSQL_PASSWORD='<本机 MySQL 口令>'   # 采集器不再内置任何默认口令，必须显式提供
export XIYU_K6_USERS='[{"username":"<账号>","password":"<口令>"},…]'
rm -rf docs/testing/stress-test-artifacts/examples/<旧目录>
bash scripts/performance/run-stress-test.sh --profile smoke \
  --base-url http://127.0.0.1:18089 \
  --out docs/testing/stress-test-artifacts/examples \
  --label harness-selfcheck
```

`--out` 指向 `examples/` 时，证据包会**直接**生成在该目录下，`monitoring/monitoring.log`
与 `k6-console.log` 里记录的输出路径天然自洽，无需事后搬运。
（两处路径记录的口径不同，核对时注意：`monitoring.log` 的 `out=` 与 `k6-console.log`
的 `output: csv (...)` 都是**调用时原样传入的 `--out`**，示例里因此是相对仓库根的路径，
不是绝对路径；`k6-console.log` 的 `script:` 行则是 k6 自己解析出的绝对路径。
`manifest.json` 的 `k6_script` 记录的是**相对仓库根**的 `k6-tests/load-test.js`，
与证据包写到哪儿无关——它由 k6 脚本自身的位置推导，所以把证据归档到外部盘
（`--out /mnt/evidence/xxx`）时仍然给相对路径；只有 k6 脚本本身就不在仓库里
（例如被单独拷到压测机上）才退化为绝对路径。）

两点关于"改了就坏"的准确边界（别高估也别低估）：

- `manifest.json` 的 `artifacts` 覆盖 10 个产物，**不含 `manifest.json` 自身**，
  `k6_script` 字段也不参与任何哈希。所以"改清单本身"在当前实现下**检测不到**——
  它的自校验能力只针对那 10 个文件。
- `examples/` 下的样例包是**完整入库**的：连 `k6-raw-samples.csv` 一起提交
  （冒烟档约 110 KB，体积可控）。因此对 clone 下来的样例包做全量 sha256 校验，
  正确结果是 **10/10 全部通过**；任何一项不符都确属异常。
  真实运行的证据包则落在本目录下、**不进 git**——标准档的逐请求采样可达数十 MB，
  由 `.gitignore` 排除，随交付附件单独给出（`examples/` 下另有显式例外，见该文件注释）。
  也就是说："仓库里没有某次真实运行的 raw samples"是预期；"样例包里缺"才是异常。

归档前工具链会做两处**有记录**的规范化，它们发生在 sha256 计算**之前**，
所以清单里记的就是规范化后的字节，自校验不受影响：

- `k6-console.log` 的行尾空白被删除（k6 自己输出的横幅与指标行带行尾空格，不处理会让
  `git diff --check` 常红；只删行尾空白，可见字符与行序不变）。
- **凭证脱敏**：k6 的 `--summary-export` 会把 `setup()` 的返回值原样写进 `setup_data`，
  而负载脚本的 `setup()` 正在那里登录——所以原始 summary 里带着一组可直接使用的
  bearer token。`scripts/performance/scrub-secrets.mjs` 在归档前把它们替换为
  `<redacted:jwt>`，替换处数记在 `manifest.json` 的 `credential_redactions` 字段。
  这不是"改原始产物"，而是归档前的必要脱敏：一份给第三方复核的性能证据包，
  没有任何理由携带可用身份令牌。

---

## 三、如何产出报告级原始证据

```bash
# 测试环境标准阶梯（10→50→100 VU，3.5 分钟，与 2026-08-28 报告口径一致）
export XIYU_STRESS_CONFIRMED=1
export MYSQL_PASSWORD='<采集器连接 MySQL 的口令>'   # 无默认值，必须显式提供
bash scripts/performance/run-stress-test.sh \
  --profile standard \
  --base-url http://172.16.38.78:8080 \
  --monitor-local-target \  # 仅当本机就是 172.16.38.78 这台应用宿主时才能加
  --enable-writes           # 复现报告的"受控写入 567 笔"必须显式开启；不写库时省略
```

- 不加 `--enable-writes` 时压测**只读**，不向环境写入业务数据，也不会触发真实的待立项通知。
- `--profile` 只接受 `smoke` / `standard` / `stress` / `endurance` 四个档位，其余值直接拒绝。
  （k6 侧是 `STAGE_CONFIGS[PROFILE] || STAGE_CONFIGS.standard`，拼错的档位会**静默按 standard 跑**，
  而 manifest 记的是你输入的那个值——两边对不上，容量结论就是错的，所以在入口挡掉。）
- 压测机上需能连到 MySQL 8.0 与 Redis（默认 `127.0.0.1:3306` / `6379`，可用
  `MYSQL_HOST`/`MYSQL_PORT`/`MYSQL_USER`/`MYSQL_PASSWORD`/`REDIS_HOST`/`REDIS_PORT` 覆盖）。
- **监控组件必须真的采到数据**，默认要求 `system,app-jvm,mysql,redis` 四个组件各至少一行
  有效采样（必需字段齐备且为数值）。目标环境确实没有其中某个组件时，显式收窄范围：
  `--monitor-required-components system,app-jvm,mysql`。不声明就少一个组件 → **exit 12**，
  而不是交付一份"四份 CSV 都在、其中一份全是空值"的包。
- **收窄就是声明范围，判定严格按声明的集合执行**：必需产物清单与逐份 CSV 的有效数据行
  复算都只覆盖你声明的组件；范围外的组件不参与判定，因此一份"按声明范围完整"的包可以
  正常交付（`exit 0`）。但它**不会**被顺手藏起来——实际采样情况（`usable_samples` 与原因）
  仍逐组件记在 `manifest.json` 的 `monitoring_components` 里并标 `required=false`，
  "这台机器没有 Redis"是事实，读者有权看到。

**监控宿主必须说清楚 —— 这是本工具最容易产出"看起来对、其实无意义"数据的地方。**
采集器采的是**它自己所在这台机器**的 CPU/内存/JVM，以及它连得到的 MySQL/Redis。
所以：

| 目标 | 本机是不是目标宿主 | 该怎么传 | manifest 记的 `monitor_scope` |
| :--- | :--- | :--- | :--- |
| `http://127.0.0.1:18089` | 是 | 不用加参数 | `local-target` |
| `http://172.16.38.78:8080` | 是（命令就跑在这台机器上） | `--monitor-local-target` | `local-target-asserted` |
| `http://172.16.38.78:8080` | 否（命令跑在另一台压测机上） | `--no-monitor`，并到目标机单独跑采集器 | `skipped` |

目标非回环地址、又没给这两个参数之一时，脚本**拒绝执行**（exit 2）：它宁可让你明确表态，
也不肯把压测机的负载写成目标环境的负载——那样产出的 `monitoring/*.csv` 看上去完整无缺，
却会把报告里"目标系统资源稳定"的结论整个带偏。

- 远端场景的正确做法：把 `scripts/performance/capture-monitoring.sh` 放到应用宿主机上单独跑，
  `--out` 指向同一个证据目录，并显式设 `MYSQL_PASSWORD`。
- `--no-monitor` 产出的包会在末尾打印告警、manifest 里 `monitor_scope: "skipped"`；
  这类包按设计**不算**"证据齐全"，交付时需另行附上目标机的监控记录。

**完整性是硬门禁。** 原始结果（`k6-summary.json` / `k6-raw-samples.csv` / `k6-console.log`）、
监控产物（四份 CSV + `component-status.csv` + `monitoring.log`，`--no-monitor` 时除外）、
`manifest.json` 任何一项缺失或为空，脚本都会以 **exit 12** 结束，且优先于 k6 的退出码上报——
k6 返回 0 只说明门禁通过，不说明这份包能交付。k6 的原始退出码仍完整保留在 `k6-exit-code`
与 `manifest.json` 里。

**"存在"之外还查"有数据"。** 只判文件非空，会把"只有表头"或"整列空值"的监控文件当成完整
证据放行——那正是采集器在组件采不到数据时留下的形态。所以 runner 会**独立复算**你声明范围内
每份监控 CSV 里必需列齐备且为数值的行数（不复用采集器自报的状态文件，清单不给自己发合格证），
任何一份为 0 行即计入 `missing_artifacts` 并 exit 12；具体到哪个组件、为什么失败，
记在 manifest 的 `monitoring_components`（逐组件有效行数与原因）与 `monitoring_issues` 里。
`monitoring.log` 与 `component-status.csv` 无条件必需——它们记录"采了什么、采到没有"。

manifest 的 `evidence_complete` / `deliverable_ready` 字段与 `missing_artifacts` 列表记录同一判定。

跑完后按 `manifest.json` 的 `headline_metrics` 与各接口 p95 填报告 §4.2 的表格，
并把整个证据目录作为附件随报告交付。

---

## 四、复现 2026-08-28 报告前必须核对的五项

这五项是"用仓库里的脚本能否复现报告数字"的差异点。**在核对清楚前，不建议把该报告
当作已完成 SLA 门禁的证据对外引用。** 它们需要原始 `k6-summary.json` 才能定论。

1. **请求总数对不上**：报告 §1.1 称 11,092 次请求，§4.2 分项加总为 10,844 次，差 248 次。
   分项明细见报告 §4.2。

2. **两项指标与仓库内 k6 门禁冲突**：报告 §1.1 的全局 P95 = 1.55 s，而 `k6-tests/load-test.js`
   写的门禁是 `http_req_duration: p(95)<1500`；报告 §4.2 的"项目列表检索" P95 = 2.12 s，
   而门禁是 `xiyu_project_list_duration: p(95)<1000`。按当前脚本，这两项都会让 k6 以
   非 0 退出码结束（退出码 99），与报告"达标 ✅"的结论不一致。
   可能是当时的门禁值不同，也可能是报告误填——需要原始 summary 或 console 日志。

3. **压测目标环境与脚本默认值不一致**：报告声明测试环境为 `http://172.16.38.78:8080`
   （`winbid-01.test`），而 `k6-tests/load-test.js` 与 `docs/testing/STRESS_TEST_GUIDE.md`
   的默认 `BASE_URL` 是 `127.0.0.1:18080`。复现时必须显式传 `--base-url`。

4. **默认账号池不是可移植的**：`k6-tests/load-test.js` 的 `DEFAULT_USER_POOL` 写死了
   `admin` / `zhangjingli` / `xiaowang` / `xiaozhao` 四人，但只有 `admin` 在本项目
   任何 profile 下都存在。另外三个的真实情况是：

   | 账号 | 实际情况 | 依据 |
   | :--- | :--- | :--- |
   | `zhangjingli` | 仅由 UAT / 发布演练栈创建，其口令与脚本里写的默认值**不同** | `scripts/test/start-api-e2e-stack.sh`、`scripts/release/rehearsal-up.sh` |
   | `xiaowang` | 由 e2e 栈创建，口令是 `E2eDemoDataInitializer` 的默认值 | CLAUDE.md「默认登录凭据 / e2e」一节 |
   | `xiaozhao` | **全仓无任何创建点**，只出现在 `k6-tests/load-test.js` 与 `k6-tests/security-checklist.md` | `grep -rn xiaozhao backend/src src e2e scripts` 无命中 |

   直接用默认池时，认证成功率门禁必然被击穿，复现不出"多角色并发"。复现时用目标环境
   的真实账号显式覆盖账号池，从环境变量传入：

   ```bash
   # 口令只存在于你自己的 shell 环境里；`--users '<JSON>'` 等效，但会把口令写进
   # 本机 `ps` 的 argv，共享压测机上不要那么用。
   export XIYU_K6_USERS='[{"username":"<账号>","password":"<口令>"},…]'
   bash scripts/performance/run-stress-test.sh --profile standard --base-url <目标> …
   ```

   本文档不写任何环境的具体口令——本机 dev 环境的账号与口令以仓库 `CLAUDE.md`
   「默认登录凭据」一节为准（那是给本地联调用的，不是给文档复制的）。

5. **裸跑 `k6 run` 与本目录的工具链，默认写入行为相反**：`k6-tests/load-test.js:27` 是
   `__ENV.ENABLE_WRITES || 'true'`，即**裸跑 k6 默认开写**（10% 流量 `POST /api/tenders` 落库）；
   而 `run-stress-test.sh` 默认**只读**，必须显式 `--enable-writes` 才写。
   本 README 第三节"不写库时省略"只对包装器成立。照 `docs/testing/STRESS_TEST_GUIDE.md`
   里的裸 `k6 run` 示例操作时，务必显式带上 `ENABLE_WRITES=false`。

---

## 五、相关文件

| 文件 | 作用 |
| :--- | :--- |
| `scripts/performance/run-stress-test.sh` | 压测执行入口，产出本目录的证据包 |
| `scripts/performance/capture-monitoring.sh` | 压测期间系统/JVM/MySQL/Redis 监控采样 |
| `scripts/performance/write-run-manifest.mjs` | 生成 `manifest.json`（含 sha256 与门禁判定） |
| `k6-tests/load-test.js` | k6 压测脚本本体（场景权重、门禁阈值、各接口 Trend 指标） |
| `docs/testing/STRESS_TEST_GUIDE.md` | 铺底数据生成、导入、执行、清理的操作 SOP |
| `docs/testing/STRESS_TEST_REPORT_2026-08-28.md` | 2026-08-28 实测报告（附录八为本次补充的证据复现清单） |
