# 西域数智化投标管理平台性能、安全、备份恢复与监控交付说明

## 结论

当前阶段可以完成客户要求的方案、脚本和演练证据准备，但不能在未压测前承诺“200 人并发已通过”。现有系统已经具备 JWT 认证、登录限流、项目权限守卫、Redis 缓存、异步线程池、Actuator/Prometheus 指标、MySQL 备份脚本和恢复脚本；缺口主要在正式 200 并发压测报告、安全加固闭环、备份恢复演练记录和监控截图证据。

本报告作为客户交付材料的主索引，执行结果应在演练完成后补齐到“证据记录”章节。

## 性能测试方案

压测路径只使用真实 API、真实 MySQL 和 Redis：

1. 登录：`POST /api/auth/login`
2. 项目列表：`GET /api/projects`
3. 项目详情：`GET /api/projects/{id}`
4. 项目任务/文档：`GET /api/projects/{id}/tasks`、`GET /api/projects/{id}/documents`
5. 数据看板：`GET /api/analytics/overview`、`GET /api/analytics/product-lines`
6. 导出：`POST /api/export/excel`
7. 大标书受理：`POST /api/tenders/upload-init`，具备共享存储文件后再测 `upload-complete`

压测脚本：`scripts/performance/sales-200.k6.js`

建议验收阈值：

| 指标 | 阈值 |
|------|------|
| 普通查询接口 | p95 <= 1s |
| 写接口 | p95 <= 2s |
| 登录接口 | p95 <= 800ms |
| 上传入队接口 | p95 <= 2s |
| HTTP 错误率 | <= 1% |
| 应用 CPU 常态 | < 70% |
| 应用内存常态 | < 75% |

运行命令：

```bash
API_BASE_URL=http://127.0.0.1:18080 \
K6_USERNAME=staff \
K6_PASSWORD='Test@123' \
k6 run scripts/performance/sales-200.k6.js
```

大标书风险说明：

- `upload-init / upload-complete / task-status` 是 200 人并发场景的推荐路径。
- `BidTenderDocumentImportAppService` 当前仍存在同步读取 30MB 文件并解析的风险，不能作为 200 人同时上传大标书的主路径。
- 标书重任务以队列限流处理，当前默认 `max-global-concurrency=2`、`max-per-user-concurrency=1`，应先压测再调整。

## 安全方案

现有安全基础：

- JWT 认证，`JWT_SECRET` 少于 32 字符会启动失败。
- BCrypt 密码哈希。
- CORS 白名单来自环境变量。
- 登录接口基于 IP 限流。
- 项目访问通过 `ProjectAccessScopeService` 统一守卫。
- `/actuator/health` 和 `/actuator/info` 可公开，`/actuator/prometheus` 不公开。
- 导出接口已有鉴权、审计、限流和路径穿越防护。

P0 加固项：

| 项目 | 要求 | 状态 |
|------|------|------|
| JWT 密钥 | 生产必须提供 32 字符以上随机密钥 | 待环境确认 |
| 数据库密码 | 生产必须通过环境变量注入 | 待环境确认 |
| 平台账号加密密钥 | 生产必须提供 `PLATFORM_ACCOUNT_ENCRYPTION_KEY` | 待环境确认 |
| Admin 密码 | 禁止默认弱密码 | 待环境确认 |
| CORS | 生产只允许客户正式域名 | 待环境确认 |
| 错误详情 | 生产关闭 message、binding-errors、stacktrace | 已配置 |
| Swagger | 生产环境应关闭或限制内网访问 | 待环境确认 |

P1 加固项：

- 补充 CSP、HSTS、Referrer-Policy、Permissions-Policy 等安全响应头。
- 导出、删除、权限变更、平台账号密码查看等敏感操作纳入审计复核。
- 上传和导出接口维持独立频控，防止资源耗尽。
- 保持 stateless JWT API 的 CSRF 禁用说明；如使用 Cookie 承载 token，必须配合 Secure、HttpOnly、SameSite 策略。

安全验收建议：

```bash
cd backend
mvn test -Dtest=ActuatorSecurityIntegrationTest,ProductionSecurityPropertiesTest
mvn test -Dtest=ProjectAccessGuardCoverageTest
```

## 备份恢复演练

备份策略：

- MySQL 每日全量备份。
- 上线前、批量导入前、重大迁移前手动备份。
- 备份文件保存到受控目录，记录文件名、大小、sha256、开始时间、结束时间。

备份命令：

```bash
DB_ENGINE=mysql \
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=xiyu_bid \
DB_USER=xiyu_user \
DB_PASSWORD='******' \
BACKUP_DIR=.rehearsal-mysql/backups \
scripts/release/backup-db.sh
```

恢复演练必须使用独立恢复库，不覆盖当前开发、演示或生产库：

```bash
DB_ENGINE=mysql \
DB_HOST=127.0.0.1 \
DB_PORT=3306 \
DB_NAME=xiyu_bid_restore_20260427 \
DB_USER=xiyu_user \
DB_PASSWORD='******' \
CONFIRM_RESTORE=YES \
scripts/release/restore-db.sh .rehearsal-mysql/backups/<backup-file>.sql
```

验收指标：

| 指标 | 目标 |
|------|------|
| RPO | <= 24 小时 |
| RTO | <= 60 分钟 |
| 抽查范围 | 登录、项目列表、项目详情、标书文件记录、文档记录 |

演练记录模板：

| 字段 | 记录 |
|------|------|
| 演练日期 | 待补充 |
| 备份文件 | 待补充 |
| 文件大小 | 待补充 |
| sha256 | 待补充 |
| 恢复库名 | 待补充 |
| 备份开始/结束 | 待补充 |
| 恢复开始/结束 | 待补充 |
| RTO | 待补充 |
| RPO | 待补充 |
| 抽查结果 | 待补充 |
| 结论 | 待补充 |

## 监控截图清单

采集来源：

- Spring Boot Actuator
- Prometheus
- Grafana
- MySQL/Redis 运行指标

必须截图：

| 截图 | 内容 |
|------|------|
| 健康检查 | `/actuator/health` readiness/liveness |
| Prometheus target | 后端服务 scrape 正常 |
| 接口延迟 | HTTP p95/p99，压测期间曲线 |
| 错误率 | 4xx/5xx 和总错误率 |
| JVM | heap、GC、线程数 |
| 数据库 | Hikari 活跃连接、等待连接 |
| Redis | 连接数、命中率、内存 |
| 标书队列 | queued/running/retrying/dlq 任务数量 |
| 资源水位 | CPU、内存、磁盘 |

推荐 PromQL：

```promql
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri, method))
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}
hikaricp_connections_active
hikaricp_connections_pending
process_cpu_usage
system_cpu_usage
```

## 证据记录

| 证据 | 文件或截图 | 结论 |
|------|------------|------|
| 200 并发压测报告 | 待补充 | 未执行 |
| 大标书 10MB 专项 | 待补充 | 未执行 |
| 大标书 30MB 专项 | 待补充 | 未执行 |
| 安全配置测试 | 待补充 | 未执行 |
| 备份恢复演练 | 待补充 | 未执行 |
| 监控截图 | 待补充 | 未执行 |

## 对客户口径

建议对客户表达为：平台已具备性能、安全、备份恢复和监控的基础工程能力；当前正在补齐 200 人并发压测、备份恢复演练和监控截图证据。对于大标书文件，正式方案采用异步上传入队和后台限流处理，不承诺 200 人同时同步解析大标书。
