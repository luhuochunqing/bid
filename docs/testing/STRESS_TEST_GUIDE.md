# 压力测试操作执行指南 (SOP)

> 本指南用于指导如何在**西域测试环境**上完成端到端的压力测试执行、指标提取、系统监控以及一键清理数据。

---

## 阶段一：生成与导入铺底仿真数据

为避免“空表压测”导致查询计划失真，压测前必须向测试数据库注入基准数据。

### 1. 批量生成铺底 SQL
在项目根目录下执行数据生成脚本（支持自定义数量）：

```bash
# 生成 10,000 条标讯 + 2,000 个项目 + 500 条知识库案例
node scripts/generate-stress-test-data.mjs \
  --tenders 10000 \
  --projects 2000 \
  --cases 500 \
  --out ./stress-test-seed-data.sql
```

### 2. 导入测试环境数据库
将生成的 SQL 文件导入西域测试库（以 MySQL 8.0 为例）：

```bash
# 本地 Docker MySQL 或远端测试库
mysql -h 127.0.0.1 -P 3306 -u xiyu_user -pXiyuDB!2026 xiyu_bid_main < stress-test-seed-data.sql
```

---

## 阶段二：启动后端服务与资源监控

### 1. 确保后端服务运行在测试端口
```bash
# 开发宿主启动服务（端口 18080）
export XIYU_DEV_CONFIRMED=1
./scripts/dev-services.sh start
```

### 2. 打开系统资源监控终端
建议开两个终端窗口分别观察：
- **终端 1：CPU 与内存监控**
  ```bash
  top -pid $(pgrep -f "xiyu-bid-platform" || pgrep -f "java")
  ```
- **终端 2：MySQL 活动连接与慢查询监控**
  ```bash
  watch -n 1 'mysql -u xiyu_user -pXiyuDB!2026 -e "SHOW PROCESSLIST;" | grep -v Sleep'
  ```

---

## 阶段三：执行 k6 压力测试

> ⚠️ **裸跑 `k6 run` 默认会写库**：`k6-tests/load-test.js` 里 `ENABLE_WRITES` 的默认值是
> `true`，10% 流量会走 `POST /api/tenders` 真实落库。下面四条命令因此都显式带上
> `ENABLE_WRITES=false`（纯只读）。确实需要复现"受控写入"时才改成 `true`。
> 包装器 `scripts/performance/run-stress-test.sh` 的默认值相反（只读），两者不要混记。

### 1. 冒烟校验 (1 VU / 30s)
先运行单并发冒烟，确认接口畅通与 Token 正常分发：
```bash
STRESS_PROFILE=smoke ENABLE_WRITES=false BASE_URL=http://127.0.0.1:18080 k6 run k6-tests/load-test.js
```

### 2. 标准阶梯压测 (10 -> 50 -> 100 VU / 3.5m)
日常业务基准与高峰负载测试：
```bash
STRESS_PROFILE=standard ENABLE_WRITES=false BASE_URL=http://127.0.0.1:18080 k6 run k6-tests/load-test.js
```

### 3. 极限摸高压测 (20 -> 100 -> 200 VU / 5m)
探寻系统极限容量与拐点：
```bash
STRESS_PROFILE=stress ENABLE_WRITES=false BASE_URL=http://127.0.0.1:18080 k6 run k6-tests/load-test.js
```

### 4. 输出 JSON / 汇总指标
如需导出详细指标数据用于生成图表：
```bash
STRESS_PROFILE=stress ENABLE_WRITES=false BASE_URL=http://127.0.0.1:18080 k6 run --summary-export=k6-summary.json k6-tests/load-test.js
```

---

## 阶段四：填报测试报告

1. 打开 [docs/testing/stress-test-report-template.md](stress-test-report-template.md)；
2. 将 k6 终端输出的 `http_req_duration (p95, p99)`、`xiyu_error_rate`、各模块 Trend 耗时以及 top 监控到的 CPU 峰值，填入报告表格中的 `[XXX]` 占位符；
3. 核对第四章《测试环境至生产环境容量等比换算模型》的公式系数；
4. 导出为 Markdown 转成 PDF/Word 提交客户验收。

> **要出可被第三方复核的报告，请改走证据归档入口**（阶段三的等价替代，且自动留存原始数据）：
> ```bash
> export XIYU_STRESS_CONFIRMED=1
> # 前提：命令跑在 172.16.38.78 这台应用宿主上。跑在别的压测机上时把 --monitor-local-target
> # 换成 --no-monitor，并到目标机单独跑 capture-monitoring.sh（监控只能采本机）。
> bash scripts/performance/run-stress-test.sh \
>   --profile standard --base-url http://172.16.38.78:8080 --monitor-local-target --enable-writes
> ```
> 它会一并跑完阶段二、三并落盘：k6 原始汇总、逐请求采样、控制台日志，以及压测期间的
> 系统/JVM/MySQL/Redis 监控时间序列（即本指南阶段二手动开终端看的那些指标）。
> 产出物在 `docs/testing/stress-test-artifacts/<时间戳>-<档位>/`，填报口径见该目录 README。
> 缺 `--monitor-local-target` / `--no-monitor` 时脚本会在压测开始前 exit 2，而不是替你猜
> "本机资源数据代表哪台机器"；`--no-monitor` 产出的包缺监控原始数据，脚本以 exit 12 结束，
> 需补齐目标机的 `monitoring/` 后才能作为报告级证据。

---

## 阶段五：一键清理压测测试数据

压测全部完成后，执行自动生成的清理脚本，一键清理所有带 `[压测基准]` 和 `STRESS_TEST` 标识的数据，恢复干净数据库状态：

```bash
mysql -h 127.0.0.1 -P 3306 -u xiyu_user -pXiyuDB!2026 xiyu_bid_main < cleanup-stress-test-data.sql
```
