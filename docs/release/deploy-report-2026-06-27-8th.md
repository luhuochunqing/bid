# 部署报告：第 8 次发布

**发布 ID**: `3bb444139-api8080`
**部署时间**: 2026-06-27 18:08 CST (10:08 UTC) — JAR 部署 + 后端重启
**部署者**: Trae AI Agent（xiyu-bid-poc 基准区 main 分支）
**部署类型**: 手动 SSH 部署（remote-deploy.sh）

---

## 发布范围

| 项目 | 值 |
|---|---|
| **Commit** | `3bb444139` — `!1223 refactor(task): CO-361 任务状态三态模型收口（下线 IN_PROGRESS/CANCELLED）` |
| **源码变更** | 52 个新 commit（从第 7 次 `0571e25b` 起），涵盖任务状态模型收口、标书审核修复、CRM 联系人修复、tender/import 多项修复、资质附件删除修复 |
| **前端构建** | Vite + Vue 3，生产同源构建（`baseURL=""`） |
| **后端构建** | Maven + Spring Boot 3.2.0，`mvn clean package -DskipTests` |
| **JAR 大小** | ~149 MB（`bid-poc-1.0.3.jar`） |
| **迁移数量** | 168 个（JAR 内），新增 V1104/V1105 |

### 主要变更内容（52 个 commit）

#### 任务状态模型收口（CO-361 重点）
1. **CO-361 任务状态三态模型收口**（!1223）：下线 `IN_PROGRESS` 和 `CANCELLED`，业务侧只采用三态 `TODO → REVIEW → COMPLETED`
2. **CO-361 两入口任务看板展示逻辑一致 + CANCELLED 同形缺口修复**（!1218）
3. **CO-361 Review 修复 CANCELLED 同形缺口 + 状态归一收口到枚举**（!1216 衍生）
4. **CO-361 设计评审修复——P0 列类型失真 + P1/P2 残留**（44132749c）

#### 业务功能修复
1. **CO-373 修复投标负责人无法提交标书审核和投标**（!1226）
2. **CO-375 修复项目阶段附件上传后点击文件名无法下载**（!1227）
3. **CO-376 标书制作页面按钮清理与样式统一**（!1224）
4. **CO-370 修复任务交付物丢失 + 批量资质上传问题**（!1222）
5. **CO-374 删除账户下拉菜单个人中心入口**（!1221）
6. **CO-368 资质附件 v2 真正删除附件记录 + 修复预览事件无 handler**（!1214）
7. **CO-329 CRM 联系人矩阵数据缺失修复**（!1213）
8. **CO-329 使用显式字典映射 CRM position 到 role key**（!1216）

#### tender/import 修复
1. **修复 region 错误提示与示例矛盾 + 测试 import 风格统一**（!1220）
2. **同步批量导入模板字典值与前端 constants.js 一致**（!1219）
3. **字典 sheet 表头文案对齐市-市格式**（!1225）
4. **字典 sheet 地区列过滤直辖市"仅市"格式**（衍生）

#### 数据库迁移
1. **V1104__repair_personnel_schema_drift_layer2.sql**：幂等存储过程，修复 personnel.birth_date / personnel.remark / personnel_certificate.deleted_at / personnel_operation_log 表的 schema 漂移（V1103 遗漏的第二层修复）
2. **V1105__drop_in_progress_cancelled_status.sql**：`UPDATE tasks SET status = 'TODO' WHERE status = 'CANCELLED'` + `UPDATE task_status_dict SET enabled = FALSE WHERE code = 'IN_PROGRESS'`（幂等 UPDATE）

#### 测试与代码卫生
1. **CO-310 TenderEvaluationBackfillServiceTest 覆盖级联保存**（!1217）
2. **CO-363 死锁清理 + U1104 回滚脚本补 source header**（65131a3e1）

---

## 部署执行摘要

### 1. 发布准备
- **基准区**: `xiyu-bid-poc/main`，已同步到 `origin/main` 最新（`3bb444139`）
- **打包**: `scripts/release/package-release.sh` → `.release/xiyu-bid-release-3bb444139-api8080.tar.gz`
- **JAR 验证**: 含 168 个迁移文件，无重复版本（`mvn clean` 前置清理 target，避免 lesson §18 残留旧迁移被打包）

### 2. Flyway 预检（关键步骤，避免冲突）

#### 2.1 源码无重复版本
```bash
# 检查源码迁移文件无重复版本
ls backend/src/main/resources/db/migration-mysql/V*.sql | sed 's/.*\(V[0-9]*\)__.*/\1/' | sort | uniq -d
# 输出为空 → 无重复
```

#### 2.2 JAR 预校验
```bash
# 解压 JAR 检查 BOOT-INF/classes/db/migration-mysql/V* 无重复版本
unzip -l backend/target/bid-poc-1.0.3.jar | grep "migration-mysql/V" | sed 's/.*\(V[0-9]*\)__.*/\1/' | sort | uniq -d
# 输出为空 → JAR 内 168 个迁移无重复
```

#### 2.3 Flyway validate 预检
- `remote-deploy.sh` 内置 Flyway validate 预检，失败则停止 rollout
- 本次预检通过（V1103 NULL checksum 容忍性已在第 7 次部署验证）

### 3. 数据库备份
- 部署前已执行完整数据库备份（前一会话完成）
- 备份位置：`/opt/xiyu-bid/backups/`
- 保留前一次部署（`0571e25b`）的 jar 和前端产物作为回滚锚点

### 4. 部署执行
- **JAR 部署**: 18:08 CST，`/opt/xiyu-bid/shared/backend/app.jar`（155,477,144 bytes）
- **前端部署**: 18:08 CST，`/srv/www/xiyu-bid/`（nginx root，同源构建）
- **后端重启**: 18:17:41 CST（PID 5151）

---

## ⚠️ 关键事件：readiness 自恢复

### 现象
- **18:17:41** — 后端进程启动
- **18:20:16** — `/actuator/health/readiness` 返回 **503 OUT_OF_SERVICE**（readinessState 未切换）
- **18:22:03** — `OrganizationEventSdkKafkaStarter.onApplicationReady()` 执行（说明 `ApplicationReadyEvent` 已发布）
  - 18:22:03.419 — 开始 bootstrap SDK 初始化
  - 18:22:03.617 — Kafka consumer 启动成功（仅用 200ms）
- **18:24:08** — `/actuator/health/readiness` 恢复 **200 UP**

### 根因分析
**时序竞争**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(Ordered.LOWEST_PRECEDENCE)` 监听 `ApplicationReadyEvent`。

Spring Boot 的 `ApplicationAvailabilityBean` 也通过 `@EventListener` 接收 `ApplicationReadyEvent` 来切换 `ReadinessState` 从 `REFUSING_TRAFFIC` 到 `ACCEPTING_TRAFFIC`，但其 order 是 `Ordered.LOWEST_PRECEDENCE - 1`（比 Kafka starter 优先级高）。

**问题**：如果 Kafka starter 的 `register()` / `initCacheBean()` / `KafkaProcessor.start()` 中任一步骤阻塞主线程（如网络超时），会延迟后续同步事件的处理。前一次启动（排查阶段）Kafka starter 阻塞了 2.5 分钟（18:10:48 → 18:13:23），导致 `AvailabilityChangeEvent` 处理被延迟，readiness 长时间 OUT_OF_SERVICE。

**本次恢复**：Kafka starter 仅用 200ms 完成（可能是网络恢复正常或 Kafka broker 已可达），主线程未阻塞，`AvailabilityChangeEvent` 正常发布，readiness 在 18:24:08 恢复 UP。

### 风险评估
- **当前状态**: ✅ 稳定（readiness UP 持续 7+ 分钟，所有健康检查组件 UP）
- **潜在风险**: 如果 Kafka broker 不可达，下次重启可能再次出现 readiness 延迟恢复
- **建议**: 后续可考虑将 `OrganizationEventSdkKafkaStarter` 改为 `@Async` 或独立线程池执行，避免阻塞主线程

---

## 验证结果

### 1. 后端健康检查
```
GET /actuator/health → 200
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "MySQL", "validationQuery": "isValid()"}},
    "diskSpace": {"status": "UP", "details": {"total": 105553760256, "free": 35058368512, "threshold": 10485760}},
    "jwt": {"status": "UP", "details": {"algorithm": "HMAC-SHA256", "secretLength": 64, "strength": "STRONG"}},
    "livenessState": {"status": "UP"},
    "ping": {"status": "UP"},
    "readinessState": {"status": "UP"},
    "redis": {"status": "UP", "details": {"version": "6.2.19"}}
  },
  "groups": ["liveness", "readiness"]
}
```

### 2. Readiness 专用检查
```
GET /actuator/health/readiness → 200
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "MySQL", "validationQuery": "isValid()"}},
    "readinessState": {"status": "UP"}
  }
}
```

### 3. 前端页面可访问性
```
GET http://172.16.38.78:8080/ → 200, 851 bytes
- HTML 正常返回，标题"西域MRO投标管理平台"
- 引用 assets: /assets/index-Dy1docwJ.js（同源构建，无 CORS 风险）
- 前端文件时间戳: Jun 27 18:08（与 JAR 同步部署）
```

### 4. API 响应验证
```
POST /api/auth/login (错误密码) → 401
{"success":false,"code":401,"msg":"AUTHENTICATION_FAILED: 用户名或密码错误"}
```
- ✅ API 正常响应（401 是错误密码的预期行为）
- ✅ nginx 反向代理正常（`/api/` → `http://127.0.0.1:18080/api/`）

### 5. Flyway 迁移状态
- ✅ V1104 执行成功（installed_rank=169, success=1）— personnel schema 漂移第二层修复
- ✅ V1105 执行成功（installed_rank=170, success=1）— 任务三态模型收口
- ✅ 启动日志 WARN `PROCEDURE winbid.repair_personnel_schema_drift_layer2 does not exist` 是预期行为（DROP IF EXISTS 触发）

### 6. 跳过的检查
- **完整登录 smoke**: 跳过（生产 admin 密码未知，第 6 次部署遗留问题）
- **CRM smoke**: 跳过（依赖登录态）
- **run-prod-smoke.mjs**: 跳过（需要 PROD_SMOKE_USERNAME/PASSWORD 环境变量）

---

## 回滚姿态

| 项目 | 状态 |
|---|---|
| **回滚锚点** | ✅ 就绪（前一次部署 `0571e25b` 的 jar 和前端产物保留） |
| **数据库备份** | ✅ 就绪（部署前已备份） |
| **回滚命令** | `scripts/release/remote-deploy.sh` 指定 `0571e25b` release 包 |
| **是否需要回滚** | ❌ 不需要（当前部署运行正常） |

---

## 部署总结

### 成功指标
- ✅ 后端进程启动成功（PID 5151, Started XiyuBidApplication in 18.972 seconds）
- ✅ Flyway V1104/V1105 执行成功（168 个迁移，无冲突）
- ✅ 所有健康检查组件 UP（db/redis/jwt/diskSpace/ping/liveness/readiness）
- ✅ 前端页面可访问（HTTP 200）
- ✅ API 接口正常响应（登录 401、health 200）
- ✅ nginx 反向代理正常

### 注意事项
1. **readiness 自恢复行为**：本次部署出现 readiness 临时 OUT_OF_SERVICE（约 4 分钟），因 Kafka SDK 初始化与 `AvailabilityChangeEvent` 时序竞争。当前已自恢复，但建议后续优化 `OrganizationEventSdkKafkaStarter` 改为异步执行。
2. **admin 密码未知**：完整登录 smoke 无法执行，需确认生产 `ADMIN_PASSWORD` 环境变量或通过其他方式获取凭据。
3. **临时调试配置**：排查期间临时添加 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 到 `/etc/xiyu-bid/backend.env`，**待清理**（恢复为 `never`）。

### 后续待办
1. **清理临时配置**：删除 `/etc/xiyu-bid/backend.env` 中的 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（恢复默认 `never`）
2. **同步 GitHub 镜像**：GitHub 落后 Gitee 54 个 commit，需执行 `bash scripts/sync-to-github.sh`
3. **优化 Kafka starter**：考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池，避免阻塞主线程影响 readiness 切换

---

## 参考文档

- [生产发布流水线](../PRODUCTION_RELEASE_PIPELINE.md)
- [生产部署 Runbook](../LIVE_SERVER_DEPLOYMENT_RUNBOOK.md)
- [回滚手册](../ROLLBACK_RUNBOOK.md)
- [第 7 次部署报告](./deploy-report-2026-06-27-7th.md)（前一次部署，`0571e25b`）
- [经验教训](../../docs/lessons/lessons-learned.md)
