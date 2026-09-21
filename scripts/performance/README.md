一旦我所属的文件夹有所变化，请更新我。

# 性能脚本目录

这里存放西域数智化投标管理平台的真实 API 压测脚本和运行说明。
脚本只面向真实后端、MySQL 和 Redis，不使用前端 demo 或 mock 数据路径。

| 文件 | 地位 | 功能 |
|------|------|------|
| `sales-200.k6.js` | 压测脚本 | 模拟 200 个销售在线用户访问登录、项目、看板、导出和标书上传入队链路 |
| `run-stress-test.sh` | 执行脚本 | 压测执行与原始证据归档入口：调用 `k6-tests/load-test.js` 跑指定档位，产出完整证据包（原始 summary / 逐请求采样 / 控制台日志 / 监控数据 / `manifest.json`）。内置 `XIYU_STRESS_CONFIRMED=1` 与环境守卫，默认关闭业务写入 |
| `capture-monitoring.sh` | 采集脚本 | 压测期间系统 CPU/内存、应用 JVM 堆与 GC、MySQL 连接与慢查询、Redis 连接与内存的逐采样记录，产出 CSV 时间序列 + 可读日志，替代手工监控截图 |
| `write-run-manifest.mjs` | 清单脚本 | 解析证据目录既有产物，生成 `manifest.json`（运行元数据、SLA 门禁声明与击穿项、头部指标、各产物 sha256） |

## 压测证据归档（推荐入口）

需要出具可被第三方复核的压测报告时，走证据归档入口而不是手工拼 k6 命令：

```bash
export XIYU_STRESS_CONFIRMED=1
bash scripts/performance/run-stress-test.sh \
  --profile standard \
  --base-url http://172.16.38.78:8080 \
  --enable-writes
```

产出物落在 `docs/testing/stress-test-artifacts/<UTC时间戳>-<档位>/`，
目录结构、门禁判定口径与已知复现差异见 `docs/testing/stress-test-artifacts/README.md`。

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
