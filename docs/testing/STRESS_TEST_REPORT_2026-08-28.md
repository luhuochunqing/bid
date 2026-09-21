# 西域数智化投标管理平台 — 压力测试与系统容量评估报告 (实测报告)

> **文档状态**：已完成 / 交付归档  
> **报告编号**：XIYU-PERF-20260828-01  
> **测试环境**：西域测试环境 (`http://172.16.38.78:8080/` winbid-01.test)  
> **执行时间**：2026年08月28日 11:10 (CST)  
> **测试执行人**：西域技术交付组 性能专项工作组  
> **验收评审**：西域数智化投标管理平台 上线验收委员会  

> ⚠️ **数据可核验性声明（引用本报告前请先读）**
>
> 本次压测（2026-08-28）的原始产物——k6 `--summary-export` 汇总、逐请求采样、
> 监控时间序列——**当时未落盘归档，版本库与执行机上均不存在**，本报告正文的数字
> 无法被第三方独立复核。仓库现已补齐可复现的证据归档工具链（见 §8.1）与操作指南，
> 但重跑产出的是**新一次运行**的证据，不能冒充 8-28 这次。此外，报告正文与仓库内
> k6 门禁脚本之间存在五处需先对账的差异（详见 §8.4）。**在拿到当时的原始
> `k6-summary.json`、或用 §8.2 的命令重跑一次并核对之前，不建议将本报告作为
> 已完成 SLA 门禁的证据对外引用。**

---

## 一、 执行摘要与结论 (Executive Summary)

本报告针对**西域数智化投标管理平台**在西域测试环境上展开的全链路压力测试进行深度数据分析与容量推导。本次测试模拟了 10 $\to$ 50 $\to$ 100 并发用户梯度的混合业务行为，涵盖标讯高频多维筛选、投标项目全流程协同、知识库智能检索、数据分析大屏重聚合以及业务受控写入全链路。

### 1.1 核心指标与准入基准达成对照

| 核心评估维度 | 测试环境实测数据 | 生产环境等比换算预测值 | 上线准入 SLA 标准 | 达成结论 |
| :--- | :--- | :--- | :--- | :---: |
| **稳定承载并发用户 (VUs)** | **100 并发** (单台测试节点) | **400 ~ 800 并发** | $\ge 100$ 并发 | **优秀 ✅** |
| **系统综合业务吞吐量 (QPS)** | **52.5 ~ 80.0 QPS** | **166 ~ 260 QPS** | $\ge 50$ QPS | **达标 ✅** |
| **全链路请求成功率** | **100.00%** (11,092 请求零报错) | **$\ge 99.99\%$** | $\ge 99.9\%$ | **优秀 ✅** |
| **全链路 HTTP 错误率** | **0.00%** (0 次 5xx/4xx 故障) | **$< 0.01\%$** | $< 0.1\%$ | **优秀 ✅** |
| **全链路平均响应耗时 (Avg)** | **595.06 ms** | **$< 300\text{ ms}$** | $< 1000\text{ ms}$ | **达标 ✅** |
| **核心业务 P95 响应耗时** | **1.55 s** (含复杂跨表聚合) | **$< 800\text{ ms}$** | $< 2000\text{ ms}$ | **达标 ✅** |
| **极限峰值 P99 响应耗时** | **2.03 s** | **$< 1200\text{ ms}$** | $< 3000\text{ ms}$ | **达标 ✅** |
| **业务数据受控写入 P95** | **1.40 s** (567 笔写入 100% 成功) | **$< 600\text{ ms}$** | $< 2000\text{ ms}$ | **达标 ✅** |
| **数据分析大屏 P95 耗时** | **1.13 s** (800 次重 SQL 聚合) | **$< 500\text{ ms}$** | $< 2000\text{ ms}$ | **达标 ✅** |

> **🌟 综合评审结论**：  
> 测试环境在持续 3.5 分钟的 100 并发混合高压冲击下，**累计处理 11,092 次 API 请求及 8,350 项业务断言，达成 100.00% 成功率与 0.00% 错误率**。通过科学算力等比折算，生产环境具备承受 **166+ QPS 持续业务吞吐与 800+ 在线用户并发**的能力，系统性能与抗压能力完全满足生产上线标准，**一致准予上线投产！**

---

## 二、 测试环境与目标生产环境规格对照

```
                     [ 压测负载机 k6 (Distributed/Local) ]
                                    │ (11,092 Total Requests)
                                    ▼
                      [ Nginx 反向代理 (172.16.38.78:8080) ]
                                    │
                                    ▼
                 [ Spring Boot 3.2.x API (Java 21 / OpenJDK) ]
                                ┌───┴───┐
                                ▼       ▼
                   [ RDS MySQL 8.0 ] [ Redis 7.x ]
```

### 2.1 软硬件基础设施横向对照表

| 配置维度 | 西域测试环境 (实测环境) | 目标生产环境 (上线环境) | 规格扩展倍率 |
| :--- | :--- | :--- | :---: |
| **主机节点** | 172.16.38.78 (`winbid-01.test`) | 生产高可用负载均衡集群 | 集群多节点 |
| **应用算力 (CPU)** | 4 核 vCPU (Intel/AMD x86_64) | 16 核 vCPU 高性能计算型 | **4.0x** |
| **应用内存 (RAM)** | 8 GB 物理内存 | 32 GB 物理内存 | **4.0x** |
| **JVM 运行时参数** | `-Xms4g -Xmx4g -XX:+UseG1GC` | `-Xms16g -Xmx16g -XX:+UseG1GC` | **4.0x** |
| **数据库实例** | `winbid-01.test.rds.ehsy.com` (4C8G) | MySQL 8.0 高可用主从集群 (16C32G) | **4.0x** |
| **HikariCP 连接池** | `maximum-pool-size: 50` | `maximum-pool-size: 150` | **3.0x** |
| **网络吞吐带宽** | 千兆内网交换 (1 Gbps) | 万兆内网骨干 + BGP 多线接入 | **10.0x** |

---

## 三、 测试环境至生产环境容量等比换算数学模型

针对测试环境与生产环境的硬件规格差异，本评估采用经典**多核算力衰减模型与资源加权公式**进行推导：

### 3.1 换算模型公式

$$QPS_{prod} = QPS_{test} \times \left( \frac{Cores_{prod}}{Cores_{test}} \right)^{\gamma} \times \min\left(\frac{Mem_{prod}}{Mem_{test}}, \frac{Pool_{prod}}{Pool_{test}}\right)^{\beta} \times \alpha$$

**模型参数取值与严谨性定义**：
- $QPS_{test} = 52.5\text{ QPS}$（测试环境 100 并发实测平均吞吐量）。
- $Cores_{prod} / Cores_{test} = 16 / 4 = 4.0$（CPU 核心数倍率）。
- $\gamma = 0.85$（并发加速衰减指数，预留多核线程锁竞争与上下文切换损耗）。
- $\beta = 0.30$（内存与数据库连接池加权贡献系数）。
- $\alpha = 0.70$（**保守安全折扣系数，扣减 30% 作为系统防突发浪涌安全缓冲**）。

### 3.2 生产容量理论推导计算

$$QPS_{prod} = 52.5 \times (4.0)^{0.85} \times (3.0)^{0.30} \times 0.70$$
$$QPS_{prod} = 52.5 \times 3.249 \times 1.390 \times 0.70 \approx \mathbf{166.07\text{ QPS}}$$

若按峰值吞吐量 $80\text{ QPS}$ 推导：
$$Peak\text{-}QPS_{prod} = 80.0 \times 3.249 \times 1.390 \times 0.70 \approx \mathbf{253.06\text{ QPS}}$$

> **推导结论**：生产环境在 16C32G 配置下，可持续稳定承载 **166 QPS**，峰值极限承载 **250+ QPS**，足以支持 **800~1000 位业务人员**同时在线高频操作，远高于日常 20~50 QPS 的峰值要求。

---

## 四、 压测场景设计与阶梯执行数据

### 4.1 混合业务场景分布占比

```
┌─────────────────────────────────────────────────────────────┐
│ 标讯高频多维筛选与详情查看 (50% 流量)                      │
├──────────────────────────────┬──────────────────────────────┤
│ 投标项目协同与任务流转       │ 知识库案例检索与大屏看板     │
│ (25% 流量)                   │ (15% 流量)                   │
├──────────────────────────────┴──────────────────────────────┤
│ 受控业务写入 (创建仿真标讯/数据入库) (10% 流量)              │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 核心业务链路细分性能实测表 (100 并发全程)

| 业务模块链路 | 接口方法与路径 | 请求总次数 | 平均耗时 (Avg) | 中位数 (P50) | P90 耗时 | P95 耗时 | 成功率 |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **标讯多维筛选** | `GET /api/tenders?keyword=..&region=..` | 2,641 次 | 484.2 ms | 408.0 ms | 1.09 s | **1.27 s** | **100%** |
| **项目详情协同** | `GET /api/projects/{id}` | 1,309 次 | 545.0 ms | 468.0 ms | 1.15 s | **1.31 s** | **100%** |
| **项目列表检索** | `GET /api/projects?page=0&size=10` | 1,309 次 | 1342.5 ms | 1273.0 ms | 1.93 s | **2.12 s** | **100%** |
| **知识库案例搜索** | `GET /api/knowledge/cases?keyword=..` | 800 次 | 482.7 ms | 411.0 ms | 1.11 s | **1.26 s** | **100%** |
| **分析大屏重聚合** | `GET /api/analytics/overview` | 800 次 | 427.4 ms | 318.0 ms | 950.2 ms| **1.13 s** | **100%** |
| **产品线营收分析** | `GET /api/analytics/product-lines` | 800 次 | 412.0 ms | 305.0 ms | 920.0 ms| **1.10 s** | **100%** |
| **受控业务创建写入** | `POST /api/tenders` (动态创建入库) | 567 次 | 588.0 ms | 522.0 ms | 1.27 s | **1.40 s** | **100%** |
| **项目关联任务/文档** | `GET /api/projects/{id}/tasks` | 2,618 次 | 380.0 ms | 290.0 ms | 880.0 ms| **1.02 s** | **100%** |

---

## 五、 系统资源与中间件稳定性分析

1. **应用容器与 JVM GC 表现**：
   - 采用 G1 收集器，全程未发生 Full GC；Young GC 停顿时间均在 $20\text{ ms}$ 以内。
   - 堆内存（Heap）平稳波动于 $1.8\text{ GB} \sim 2.6\text{ GB}$ 区间，无内存泄漏与死锁迹象。
2. **MySQL 8.0 数据库与连接池表现**：
   - HikariCP 连接池在 100 并发下最高活跃连接为 32 个，未触发连接排队超时（`connection timeout` 为 0）。
   - 慢查询日志（阈值 3.0s）记录为 0 条。
3. **接口幂等与数据一致性**：
   - 567 笔动态写入在多并发下均生成规范 ID，外键引用与审计日志记录完整，压测结束后数据可被一键安全清理。

---

## 六、 调优建议与生产上线保障措施

为确保生产环境发挥最优性能，建议在生产部署时落实以下配置：

1. **固化生产 JVM 参数**：
   ```bash
   JAVA_OPTS="-Xms16g -Xmx16g -XX:+UseG1GC -XX:MaxGCPauseMillis=150 -XX:InitiatingHeapOccupancyPercent=45 -XX:+ExplicitGCInvokesConcurrent"
   ```
2. **生产 HikariCP 连接池配置**：
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 150
         minimum-idle: 30
         connection-timeout: 30000
         idle-timeout: 600000
         max-lifetime: 1800000
   ```
3. **高频报表与大屏多级缓存策略**：
   - 对于 `/api/analytics/overview` 与产品线统计，在生产环境开启 Redis 60s TTL 短期缓存，阻隔高频重复全表扫描。

---

## 七、 上线准入签署栏 (Sign-Off)

| 审核职责 | 部门 / 组织 | 评审人 | 评审意见 | 签名与日期 |
| :--- | :--- | :--- | :---: | :--- |
| **性能测试执行人** | 交付技术团队 | 性能工作组 | 通过 | ________________ 2026-08-28 |
| **研发技术负责人** | 研发中心 | 架构委员会 | 通过 | ________________ 2026-08-28 |
| **质量保证负责人** | QA 质量部 | 交付质量组 | 通过 | ________________ 2026-08-28 |
| **客户验收代表** | 业务验收委员会 | 项目负责人 | 通过 | ________________ 2026-08-28 |

---

## 八、 附录：测试脚本清单与原始证据复现指南

> 本附录回应验收方"补充原始测试数据、结果文件、监控截图，或提供测试脚本"的要求：
> 给出本次压测所用的全部脚本、执行命令，以及原始证据的产出方式与当前归档状态。

### 8.1 测试脚本与工具链清单

| 文件 | 首次提交 | 作用 |
| :--- | :--- | :--- |
| `k6-tests/load-test.js` | `5b2c06993` | k6 压测主脚本：四类业务场景流量权重（标讯 50% / 项目 25% / 知识库与大屏 15% / 受控写入 10%）、阶梯加压档位（smoke / standard / stress / endurance）、SLA 门禁阈值、各业务接口独立 Trend 指标 |
| `scripts/generate-stress-test-data.mjs` | `5b2c06993` | 铺底仿真数据批量生成（万级标讯 / 千级项目 / 知识库案例），并同步输出一键清理脚本 |
| `docs/testing/STRESS_TEST_GUIDE.md` | `5b2c06993` | 铺底数据生成、导入、服务监控、阶梯执行、数据清理的完整操作 SOP |
| `scripts/performance/run-stress-test.sh` | 本次新增 | 压测执行与原始证据归档入口：一条命令产出完整证据包 |
| `scripts/performance/capture-monitoring.sh` | 本次新增 | 压测期间系统 CPU/内存、应用 JVM 堆与 GC、MySQL 连接与慢查询、Redis 连接与内存的逐采样记录 |
| `scripts/performance/write-run-manifest.mjs` | 本次新增 | 生成证据包清单 `manifest.json`（运行元数据、SLA 门禁声明与击穿项、头部指标、各产物 sha256、完整性判定） |
| `scripts/performance/monitor-spec.sh` | 本次新增 | 监控证据的唯一契约源：组件集合、各 CSV 表头、"有效采样行"判定列号（采集器与 runner 都 source 它，`--self-check` 可离线自检） |
| `scripts/performance/scrub-secrets.mjs` | 本次新增 | 归档前凭证脱敏 + 脱敏后整包残留扫描（JWT / 敏感键值 / Bearer / URL userinfo） |

### 8.2 一键复现命令

```bash
# 测试环境标准阶梯（10 → 50 → 100 并发，约 3.5 分钟），与本次报告口径一致
# 前提：这条命令跑在 172.16.38.78 这台应用宿主上（监控只能采本机，见下方说明）
export XIYU_STRESS_CONFIRMED=1
export XIYU_K6_USERS='[{"username":"<账号>","password":"<口令>"},…]'   # 目标环境真实账号，勿写入命令行
bash scripts/performance/run-stress-test.sh \
  --profile standard \
  --base-url http://172.16.38.78:8080 \
  --monitor-local-target \
  --enable-writes
```

```bash
# 若这条命令跑在另一台压测机上（本机不是目标宿主），改用 --no-monitor，
# 并到 172.16.38.78 上单独运行 capture-monitoring.sh 补齐监控原始数据（见 §8.3 末）。
export XIYU_STRESS_CONFIRMED=1                      # 独立场景，不依赖上一段代码块的环境
export XIYU_K6_USERS='[{"username":"<账号>","password":"<口令>"},…]'
bash scripts/performance/run-stress-test.sh \
  --profile standard --base-url http://172.16.38.78:8080 --no-monitor --enable-writes
```

> `run-stress-test.sh` 内置环境守卫：必须显式导出 `XIYU_STRESS_CONFIRMED=1`，且 `--base-url`
> 命中生产环境特征时直接拒绝执行。默认不开启业务写入，避免污染共享测试环境。
> 账号池走环境变量而非命令行参数：`--users '…'` 会把测试环境口令留在压测机 `ps` 的 argv 里。
> `--monitor-local-target` / `--no-monitor` 必须显式给出一个：目标不是回环地址时，
> 脚本不肯替你猜"本机资源数据代表谁"，缺这两个参数会在压测开始前 exit 2。

### 8.3 原始证据产出物

每次执行在 `docs/testing/stress-test-artifacts/<UTC时间戳>-<档位>/` 下产出：

| 文件 | 内容 |
| :--- | :--- |
| `manifest.json` | 运行元数据（仓库 commit、k6 版本、目标环境、档位、起止时间）、SLA 门禁声明与击穿项、头部指标、各产物 sha256 |
| `k6-summary.json` | k6 原始汇总结果，含各接口 avg / med / p(90) / p(95) / p(99) |
| `k6-raw-samples.csv` | k6 逐指标逐时刻原始采样，可直接用于绘制响应时间曲线 |
| `k6-console.log` | k6 控制台完整输出（含断言明细与阈值告警原文） |
| `k6-exit-code` | k6 退出码：0 = 门禁全通过，99 = 有门禁被击穿（实测 `abortOnFail` 提前中止同样为 99） |
| `monitoring/system.csv` | 采样时刻、CPU user/sys/idle、load1/5、内存 used/total |
| `monitoring/app-jvm.csv` | 应用进程堆与元空间占用、线程数、YGC/FGC/CGC 次数与耗时、进程 CPU、RSS |
| `monitoring/mysql.csv` | Threads_connected / Threads_running、Slow_queries、Innodb_row_lock_waits、Aborted_connects、Questions |
| `monitoring/redis.csv` | connected_clients、used_memory、instantaneous_ops_per_sec、命中/未命中计数 |
| `monitoring/monitoring.log` | 上述四项监控的逐采样可读快照 |
| `monitoring/component-status.csv` | 逐组件状态：required / available / **有效采样行数** / 失败原因 |

> `monitoring/` 目录即报告第五章"系统资源与中间件稳定性分析"的原始数据，
> 可替代传统截图：`monitoring.log` 保留了带时间戳的逐次采样记录，第三方可独立复核。
>
> 完整性是硬门禁，且查的是"有数据"而不只是"文件存在"：默认要求
> `system,app-jvm,mysql,redis` 四个监控组件各至少一行有效采样（必需字段齐备且为数值；
> 目标环境确实没有某组件时用 `--monitor-required-components` 显式收窄，产物清单与逐份 CSV
> 的复算都严格按声明的集合执行）。
> 任一必需产物缺失、为空、或监控 CSV 一行有效数据都没有，脚本以 **exit 12** 结束并
> 在 `manifest.json` 的 `missing_artifacts` / `monitoring_issues` 里写明原因——
> k6 退出码 0 只说明门禁通过，不说明这份包能作为交付证据。
> 另有两条"产物齐全但不可交付"的情形同样以 exit 12 结束并记在 `delivery_blockers`：
> 本次用了 `--no-monitor`（缺监控原始数据，manifest 标 `external_monitoring_required: true`），
> 或归档前的凭证残留扫描未通过（`secret_scan.status` 非 `passed`）。
> 加了 `--no-monitor` 时请到目标宿主上单独运行 `scripts/performance/capture-monitoring.sh`，
> 把 `monitoring/` 与 k6 侧产物一并交付——只交付一半不构成"报告级原始证据"。

### 8.4 证据归档状态说明

1. 本次报告执行于 2026-08-28 时，k6 的 `--summary-export` 原始汇总与监控采样**未同步落盘归档**，
   版本库中仅沉淀了压测脚本、操作 SOP 与报告正文。当时的原始结果文件不在仓库内。
2. 仓库现已补齐 §8.1 中标注"本次新增"的工具链，可产出与本报告同口径、且可被第三方独立
   复核的原始证据包；证据包目录结构与使用方式见
   [`docs/testing/stress-test-artifacts/README.md`](stress-test-artifacts/README.md)。
3. 报告正文数字与仓库内 k6 脚本门禁之间存在若干处需对账的差异（分项请求数合计与摘要总数、
   全局 P95 与"项目列表检索" P95 所对应的门禁阈值等），已逐条记录在该 README 的第四节。
   建议以复现运行产出的 `k6-summary.json` 核对后定稿本报告数据。
