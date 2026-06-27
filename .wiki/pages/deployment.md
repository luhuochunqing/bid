---
title: 部署与上线
space: engineering
category: guide
tags: [部署, 上线, 发布, 运维, Docker, UAT]
sources:
  - .wiki/sources/implementation/西域数智化投标管理平台实施计划书SOW2026V1.4(格式校准).docx
  - docs/release/GO_LIVE_CHECKLIST.md
  - docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md
  - docs/release/ROLLBACK_RUNBOOK.md
  - docs/specs/UAT_PLAN.md
  - docs/specs/MYSQL_8_DEPLOYMENT.md
  - README.md
  - backend/src/main/resources/application.yml
  - .wiki/extracts/contract__西域数智化投标管理平台建设项目合同-V1_0420.docx.md
backlinks:
  - _index
  - api-openapi
  - architecture
  - contract-constraints
  - implementation/acceptance-and-closure
  - implementation/attachment4-gap-matrix
  - implementation/attachment4-requirement-task-book
  - implementation/attachment6-function-list-trace
  - implementation/delivery-playbook
  - implementation/milestones
  - implementation/risk-register
  - implementation/sow-2026-v1-4
  - implementation/weekly-status
  - requirements
  - team-and-timeline
created: 2026-04-15
updated: 2026-06-27
health_checked: 2026-06-27
---
# 部署与上线

## 1. 运行模式

SOW V1.4 要求甲方指定私有云/本地化部署，支持主流浏览器访问、HTTPS、权限控制、审计日志、备份恢复、自动化发布与失败回滚。完整执行基准见 [[implementation/sow-2026-v1-4]]，合同硬约束见 [[contract-constraints]]。

当前唯一支持的交付、联调、UAT、演示和验收路径是真实后端 API 模式：

| 维度 | API 模式 |
|------|----------|
| 环境变量 | `VITE_API_MODE=api` |
| 前端端口 | 1314 |
| 后端 API | `http://127.0.0.1:18080` |
| 数据来源 | 真实后端服务 |
| 适用场景 | 开发联调、UAT、发布演练、演示、生产 |
| 启动命令 | `npm run dev:api` 或 `npm run dev` |

历史 Mock/demo 适配只作为待清理遗留，不作为开发、联调、演示或验收路径。第三阶段必须按 SOW 要求隔离演示入口并清理 API 模式下的 Mock 数据硬编码路径。

**API 模式确认方法：**

```bash
cp .env.api .env
```

修改 `.env` 文件后需重启开发服务器。

## 1.1 SOW V1.4 部署容量基线

| 环境 | 组件 | 基线 |
|---|---|---|
| 正式生产 | 访问层 | 2 台，2 vCPU / 4 GB / 50 GB SSD，Nginx 或同类 Web 服务 |
| 正式生产 | 应用服务 | 2 台，4 vCPU / 8~16 GB / 100 GB SSD，Spring Boot 双节点 |
| 正式生产 | MySQL 8.0 | 主库 1 台 + 备库 1 台，均为 8 vCPU / 16 GB / 500 GB SSD |
| 正式生产 | Redis | 1 台，2~4 vCPU / 4 GB / 50 GB SSD |
| 正式生产 | 文件存储 | 初始 500 GB，可扩展，对象存储、NAS 或甲方现有文件服务 |
| 正式生产 | 监控日志 | 1 台，2 vCPU / 4 GB / 100 GB SSD，与甲方运维平台整合 |
| 试运行/预生产 | 访问层 + 应用 + MySQL + Redis | 单节点预生产配置，仅用于测试、预生产和短期试运行，不作为长期正式生产架构 |

---

## 2. 端口约定

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端开发服务器 | **1314** | 固定端口，vite.config.js / playwright.config.js / 演示统一使用 |
| 后端 API 服务 | **18080** | Spring Boot 应用端口 |
| MySQL 8 | **3306** | 生产主线数据库端口（容器或托管实例） |
| Redis | 6379 | 缓存与会话存储 |

> 注意：`14173` 等临时端口仅允许用于排查，排查结束后必须关闭，不作为项目约定端口。

---

## 3. 开发环境启动

### 3.1 前端

```bash
# 安装依赖
npm install

# 启动开发服务器（默认 API 模式）
npm run dev

# 生产构建
npm run build

# 预览构建结果
npm run preview
```

### 3.2 后端

```bash
cd backend

# 编译
mvn clean compile

# 启动应用
mvn spring-boot:run

# 运行测试
mvn test

# 打包
mvn clean package
```

**必要环境变量：**
- `DB_PASSWORD` -- 数据库密码（必填）
- `JWT_SECRET` -- JWT 密钥，最少 32 字符（必填）
- `CORS_ALLOWED_ORIGINS` -- 允许的 CORS 源（默认：localhost:5173,5174,3000）
- `SPRING_PROFILES_ACTIVE` -- 环境配置（建议 `prod,mysql`）
- `APP_TENDER_STORAGE_ROOT` -- 标书共享存储根目录（例如 `/data/shared/tenders`）

### 3.3 数据库

```bash
# 方式 1：本机连接
mysql -h localhost -P 3306 -u xiyu_user -p xiyu_bid

# 方式 2：容器连接
docker exec -it <mysql-container> mysql -u xiyu_user -p xiyu_bid
```

### 3.4 大标书异步处理参数

后端通过 `app.tender-processing.*` 配置并发阀值与保护策略：
- `max-global-concurrency`（默认 2）
- `max-per-user-concurrency`（默认 1）
- `max-retries`、`retry-delays-minutes`（默认 3 次：1/5/15 分钟）
- `cpu-threshold`、`memory-threshold`（超阈值暂停拉新任务）
- `worker-fixed-delay-ms`（默认 5000ms）

---

## 4. 发布前检查

以下为 Go-Live Checklist 关键检查项摘要：

### 4.1 发布前（Pre-release）

- [ ] 候选版本已冻结
- [ ] `docs/research/COMMERCIAL_SCOPE.md` 已确认正式版白名单与 demo-only 黑名单
- [ ] `npm run build` 和 `VITE_API_MODE=api npm run build` 均通过
- [ ] `mvn -DskipTests compile` 通过
- [ ] 关键测试通过，MySQL 主线回归验证通过
- [ ] 数据库备份已执行并校验产物存在
- [ ] 监控面板与告警规则已配置
- [ ] UAT 已通过并签字
- [ ] 已知 P0 缺陷为 0
- [ ] 发布演练脚本已执行并产出报告

### 4.2 发布中

- [ ] 执行 `scripts/release/preflight.sh` 预检
- [ ] 记录当前版本号 / 提交号
- [ ] 停止流量或进入维护窗口
- [ ] 执行数据库迁移
- [ ] 部署后端应用 + 前端静态资源
- [ ] 检查 `/actuator/health` 和关键接口返回
- [ ] 检查前端首页与主链路

### 4.3 发布后 30 分钟

- [ ] 登录主流程正常
- [ ] 项目 / 标讯列表可访问
- [ ] Knowledge 主链路可访问
- [ ] 资源审批与 BAR 证书借用可访问
- [ ] 无高优先级错误告警
- [ ] 数据库连接池稳定、Prometheus 指标可采集

---

## 5. 发布流程

标准发布流程分为四个阶段：

### 5.1 preflight（预检）

```bash
bash scripts/release/preflight.sh
```

执行构建验证、测试通过性检查、环境变量完整性检查。

### 5.2 rehearse（演练）

```bash
bash scripts/release/rehearse-release.sh
```

在本地模拟完整发布流程，包括数据库迁移、应用部署、健康检查，产出演练报告。

### 5.3 deploy（部署）

```bash
bash scripts/release/deploy.sh
```

执行正式部署：停止流量 -> 数据库迁移 -> 部署后端 -> 部署前端 -> 健康检查。

客户测试服务器 `172.16.38.78` 的实操发布、备份、Jumpserver 进入、生产 smoke、临时密钥清理和慢链路应急小包路径，见 `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md`。

### 5.4 signoff（签字确认）

```bash
node scripts/release/build-signoff-packet.mjs
```

生成正式签字包，包含 UAT 报告、版本信息、测试结果。Staging Gate 工作流通过后上传签字包。

---

## 6. 回滚策略

当发布失败或上线后出现 blocker 时，按以下步骤快速恢复：

### 6.1 触发回滚的条件

- 数据库迁移失败
- 应用无法启动
- 登录主流程失败
- 核心接口 5xx 持续出现
- 无法在 15 分钟内恢复核心业务

### 6.2 回滚步骤

1. **停止当前版本流量接入**
2. **回退前端**：将静态资源切换到上一个稳定版本
3. **回退后端**：将应用工件切换到上一个稳定版本
4. **重启服务**：重启并检查健康状态

### 6.3 数据库恢复

若数据库已被破坏性变更影响：

```bash
# 发布前备份
bash scripts/release/backup-db.sh

# 紧急恢复
CONFIRM_RESTORE=YES bash scripts/release/restore-db.sh <backup-file>
```

如果本机没有 MySQL 客户端工具，可设置 `MYSQL_CONTAINER_NAME=<mysql-container>` 使用 docker exec 回退路径。

### 6.4 回滚后验证

- `GET /actuator/health` 返回 `UP`
- 登录成功
- 项目 / 标讯列表可访问
- Knowledge 主链路可访问
- 资源主链路可访问
- 监控错误率恢复正常

### 6.5 事故记录

每次回滚需记录：发布时间、回滚时间、触发条件、影响范围、恢复耗时、后续修复 owner。

---

## 7. UAT 流程

### 7.1 角色分工

| 角色 | 职责 |
|------|------|
| 业务负责人 | 定义验收口径，签署通过 / 拒绝 |
| 销售代表 | 验证标讯、项目、结果闭环 |
| 技术代表 | 验证知识库、协作、文档能力 |
| 财务 / 资源代表 | 验证费用、BAR 证书借用、审批退还 |
| QA | 组织执行、记录问题、回归验证 |
| 技术负责人 | 修复 blocker 并给出版本说明 |

### 7.2 验收场景

1. **认证与权限**：登录、退出、未授权拦截、管理角色访问控制
2. **标讯到项目主链路**：标讯列表 -> 创建项目 -> 项目详情 -> 结果录入
3. **Knowledge 主链路**：资质 / 案例 / 模板列表查看与新增
4. **资源主链路**：费用审批、退还、BAR 证书借出与归还
5. **数据与观测**：Dashboard 聚合数据、health/prometheus 可用

### 7.3 准入与退出标准

- **准入**：前后端构建通过、关键测试通过、数据库备份完成、P0 缺陷为 0
- **退出**：所有 P0 场景通过、无 blocker 级问题、P1 问题有明确 owner、业务负责人签字确认

### 7.4 缺陷分级

| 级别 | 说明 |
|------|------|
| P0 | 阻塞上线，必须修复 |
| P1 | 影响主流程，原则上修复后再上线 |
| P2 | 不阻塞上线，需进入首个迭代 |

### 7.5 执行工具

- 自动 UAT 执行脚本：`node scripts/release/run-uat.mjs`
- Playwright API 联调栈启动：`bash scripts/test/start-api-e2e-stack.sh`
- 正式签字模板：`docs/specs/UAT_SIGNOFF_TEMPLATE.md`
- 签字包生成：`node scripts/release/build-signoff-packet.mjs`

详细团队分工与时间线参见 [[team-and-timeline]]。

---

## 8. 监控与运维

| 维度 | 说明 |
|------|------|
| 健康检查 | `GET /actuator/health`（UP/DOWN）、`GET /actuator/prometheus`（指标采集） |
| 监控指标 | Micrometer + Prometheus：JVM、HTTP 请求、连接池、业务指标 |
| 日志 | Logback，开发 DEBUG 控制台、生产 INFO 文件 + 结构化日志，AOP 审计切面 |
| 告警 | 健康异常、5xx 持续、连接池耗尽、内存超阈值 |

---

## 9. 生产测试服务器（172.16.38.78）部署实录

本节记录 `winbid-01` / `winbid-01.test` / `winbid-test.ehsy.com`（内网入口 `http://172.16.38.78:8080`）的真实部署经验。

### 9.1 环境拓扑

| 组件 | 配置 |
|------|------|
| 主机 | `winbid-01`（`172.16.38.78`） |
| OS | CentOS 7 / Alibaba Cloud Linux |
| 前端生效目录 | `/srv/www/xiyu-bid` |
| 后端 jar | `/opt/xiyu-bid/shared/backend/app.jar` |
| 后端服务 | `xiyu-bid-backend`（systemd，以 `jetty` 用户运行） |
| 后端监听端口 | `18080` |
| Nginx 入口 | `80` / `8080` 反代到 `127.0.0.1:18080`；前端静态资源由 Nginx 直接服务 |
| 数据库 | MySQL 8.0 RDS（`winbid-01.test.rds.ehsy.com`） |
| Java | `/opt/xiyu-tools/jdk-21/bin/java` |

### 9.2 部署前必做

1. 确认本地工作区在最新 `origin/main`：
   ```bash
   git fetch origin && git rebase origin/main && bash scripts/sync-env.sh .
   ```
2. 创建预部署 DB 备份：
   ```bash
   # 在服务器上执行（需 sudo 读取 /etc/xiyu-bid/backend.env）
   source /etc/xiyu-bid/backend.env
   export MYSQL_PWD="$DB_PASSWORD"
   mysqldump -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" --single-transaction --routines --triggers "$DB_NAME" \
     | gzip > /opt/xiyu-bid/db-backups/xiyu_bid-<commit>-pre-deploy-<timestamp>.sql.gz
   ```
3. 确认回滚锚点：
   - 上一个稳定 release 目录：`/opt/xiyu-bid/releases/<previous-release-id>`
   - 上一个稳定后端 jar：`/opt/xiyu-bid/releases/<previous-release-id>/backend/app.jar`
   - 上一个稳定前端：`/opt/xiyu-bid/releases/<previous-release-id>/frontend`

### 9.3 打包命令

```bash
rm -rf backend/target
RELEASE_ID=<commit-short-sha> VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- `VITE_API_BASE_URL=`（显式空值）表示前端走同源相对路径，匹配 Nginx 统一入口 `http://172.16.38.78:8080`。
- 若完全省略 `VITE_API_BASE_URL`，会 fallback 到 dev 地址 `127.0.0.1:18080`，导致浏览器端 CORS 失败。

### 9.4 产物校验

```bash
# 1. 确认前端 JS 没有写死 dev API 地址
rg "127\.0\.0\.1:18080|localhost:18080" ".release/<release-id>/frontend/assets"/*.js
# 期望无输出

# 2. 确认 jar 内 Flyway 迁移无重复版本
jar tf .release/<release-id>/backend/app.jar | rg 'BOOT-INF/classes/db/migration-mysql/V[0-9]+__' \
  | sed -E 's#.*\/(V[0-9]+)__.*#\1#' | sort | uniq -d
# 期望无输出
```

### 9.5 部署命令

```bash
# 上传并激活（在本地工作区执行）
scp .release/xiyu-bid-release-<release-id>.tar.gz jetty@172.16.38.78:/opt/xiyu-bid/incoming/
scp deploy-<release-id>.env jetty@172.16.38.78:/opt/xiyu-bid/incoming/
ssh jetty@172.16.38.78 'bash -lc "source /opt/xiyu-bid/incoming/deploy-<release-id>.env && bash -s"' < scripts/release/remote-deploy.sh
```

`deploy.env` 关键变量示例：

```bash
RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-<release-id>.tar.gz
RELEASE_ID=<release-id>
APP_ROOT=/opt/xiyu-bid
RELEASES_DIR=/opt/xiyu-bid/releases
FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid
BACKEND_SERVICE_NAME=xiyu-bid-backend
BACKEND_RUNTIME_DIR=/opt/xiyu-bid/shared/backend
BACKEND_JAR_PATH=/opt/xiyu-bid/shared/backend/app.jar
DEPLOYED_RELEASE_RECORD=/opt/xiyu-bid/deployed-release.json
HEALTHCHECK_URL=http://127.0.0.1:8080/actuator/health
SYSTEMCTL_SUDO=true
```

### 9.6 健康检查超时问题

**现象**：`remote-deploy.sh` 部署后 health check 返回 `503`，脚本退出，但等待约 2.5 分钟后后端 `/actuator/health` 自行恢复 `UP`。

**根因**：Spring Boot `ApplicationReadyEvent` 监听者 `OrganizationEventSdkKafkaStarter` 需要完成事件库 SDK 注册并启动 Kafka Consumer，在 `winbid-01` 上耗时约 151 秒；原脚本仅等待 60 × 2 秒 = 120 秒，因此提前判定失败。

**修复**：PR !876 将 `scripts/release/remote-deploy.sh` 的 health check 循环从 60 次增加到 120 次，最大等待时间延长至 4 分钟。

```bash
# 修复后
for _ in {1..120}; do
  if curl -fsS "$HEALTHCHECK_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
```

**验证**：2026-06-20 重新部署 `d180f1395` 时，脚本成功等待到 `UP` 并正常退出。

### 9.7 部署后验证清单

```bash
# 服务端
sudo systemctl is-active xiyu-bid-backend
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsS http://127.0.0.1:8080/actuator/health/liveness
curl -fsS http://127.0.0.1:8080/actuator/health/readiness

# 公网（本地执行）
curl -fsS -o /dev/null -w "%{http_code}\n" http://172.16.38.78:8080/
curl -fsS http://172.16.38.78:8080/actuator/health
```

### 9.8 已知限制

- 完整登录态业务 smoke 需要 `PROD_SMOKE_USERNAME` / `PROD_SMOKE_PASSWORD`，目前作为 GitHub/Gitee secrets 管理，本地手动部署时通常不携带。
- `/actuator/prometheus` 在 `protected` 模式下对匿名请求返回 `403`，属于预期行为。
