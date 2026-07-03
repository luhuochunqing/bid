# 第 36 次生产部署报告

**部署时间**：2026-07-03 10:46 - 10:56 (CST)
**部署人**：AI Agent (Trae)
**服务器**：winbid-01.test (172.16.38.78)
**Release ID**：`5f730424f-api8080`
**结果**：✅ 成功（健康检查延迟恢复，Kafka SDK 已知行为）

---

## 1. 部署概览

| 项目 | 值 |
|---|---|
| 目标服务器 | `172.16.38.78` (winbid-01.test) |
| SSH 用户 | `jetty` |
| App Root | `/opt/xiyu-bid` |
| Backend Port | `8080` |
| DB Name | `xiyu_bid_main` |
| Release ID | `5f730424f-api8080` |
| Commit | `5f730424f` (short) / `5f730424f...` (full) |
| 前端构建模式 | 同源构建 (`VITE_API_BASE_URL=`) |
| 新增迁移 | V1129, V1130 |
| 部署耗时 | ~10 分钟（含 4 分钟健康检查等待） |

---

## 2. 基线信息

### 2.1 Git 状态

- **当前分支**：`agent/trae-init`
- **HEAD**：`5f730424f` (Gitee main)
- **Git Status**：干净（无未提交变更）
- **早操三连**：✅ 通过（rebase 成功，本地门禁 7/7 通过，git wrapper 正常）

### 2.2 GitHub 镜像同步

- **部署前状态**：GitHub 落后 14 个 commit
- **部署后同步**：✅ 成功（`bash scripts/sync-to-github.sh`）
- **最终状态**：两边 main 完全一致（HEAD: `94fc0f17b`）

---

## 3. PR 列表与改动范围

### 3.1 增量 PR（相对于第 35 次部署 `665dd3abb`）

共 36 个 commit，涵盖以下 PR：

| PR | 标题 | 备注 |
|---|---|---|
| !1574 | feat: CA 印章类型多选支持 | V1129 迁移 |
| !1575 | fix: 人员教育经历入学日期可空 | V1130 迁移 |
| !1576-!1591 | （其他业务修复/优化） | 无迁移 |

### 3.2 改动文件统计

- **后端**：多个业务模块修复
- **前端**：对应 UI 修复
- **迁移**：新增 2 个迁移文件（V1129, V1130）

---

## 4. Flyway 预检结果

### 4.1 预检 3 步法

| 步骤 | 命令 | 结果 |
|---|---|---|
| Step 1: validate | `bash /opt/xiyu-bid/bin/flyway-repair-runner.sh validate` | ✅ VALIDATE OK - all checksums match |
| Step 2: DB 版本对比 | SQL 查询 `flyway_schema_history` | DB 最新 V1128，源码新增 V1129/V1130 |
| Step 3: remote-deploy 内置 | 自动 validate | ✅ 通过（仅 pending 新迁移为预期状态） |

### 4.2 迁移内容分析

**V1129__ca_seal_type_multiselect.sql**：
- 类型：MODIFY COLUMN（非破坏性）
- 内容：`ca_seal_type` VARCHAR(50) → VARCHAR(500)，支持多选值存储
- Rollback：`U1129__rollback_ca_seal_type_multiselect.sql` ✅ 存在

**V1130__personnel_education_start_date_nullable.sql**：
- 类型：MODIFY COLUMN（非破坏性）
- 内容：`education.start_date` 允许 NULL
- Rollback：`U1130__rollback_personnel_education_start_date_nullable.sql` ✅ 存在

---

## 5. 部署步骤执行

### 5.1 本地打包

```bash
RELEASE_ID="5f730424f-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- **耗时**：24.148s
- **产物位置**：`.release/5f730424f-api8080`
- **产物校验**：
  - jar 内迁移文件：193 个（含 V1129/V1130）✅
  - 前端入口：`index.html` 存在 ✅
  - `check:frontend-api-base` 通过 ✅

### 5.2 上传与部署

```bash
scp .release/xiyu-bid-release-5f730424f-api8080.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 '... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- **上传**：✅ 成功
- **DB 备份**：✅ 成功（`/opt/xiyu-bid/db-backups/winbid-5f730424f-api8080-*.sql.gz`）
- **Jar 覆盖**：✅ 成功
- **服务重启**：✅ 成功（systemd: Active: active (running)）

---

## 6. 验证结果

### 6.1 健康检查（Kafka SDK 延迟恢复）

**现象**：`remote-deploy.sh` 健康检查超时（4 分钟，120 attempts）

**根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程导致 readiness 延迟停留在 OUT_OF_SERVICE（skill 第 2 条经验）

**实际状态**：
- health: UP ✅
- readiness: UP ✅
- redis: UP ✅
- liveness: UP ✅

**结论**：服务正常运行，健康检查超时属 Kafka SDK 已知行为，非 crash-loop

### 6.2 迁移应用验证

```sql
SELECT version, description, success, installed_on 
FROM flyway_schema_history 
WHERE version IN ("1129", "1130") 
ORDER BY version;
```

| version | description | success | installed_on |
|---|---|---|---|
| 1129 | ca seal type multiselect | 1 | 2026-07-03 10:48:xx |
| 1130 | personnel education start date nullable | 1 | 2026-07-03 10:48:xx |

✅ V1129/V1130 已成功应用

### 6.3 Smoke 测试

| 检查项 | URL | 预期 | 实际 | 结果 |
|---|---|---|---|---|
| health | `http://172.16.38.78:8080/actuator/health` | 200 | 200 | ✅ |
| readiness | `http://172.16.38.78:8080/actuator/health/readiness` | 200 | 200 | ✅ |
| 登录接口 | `POST /api/auth/login` (空密码) | 400 | 400 | ✅ |
| 项目列表 | `/api/projects` | 403 | 403 | ✅ |
| CRM health | `/api/integration/crm/health` | 401 | 401 | ✅ |

**Smoke 结论**：✅ 全部通过（Admin 密码未知，使用 400/403/401 替代验证）

### 6.4 前端验证

```bash
curl -s http://172.16.38.78:8080/ | grep -oE 'assets/index-[A-Za-z0-9_-]+\.js'
```

- **结果**：前端入口正常，index.html 包含正确 JS 入口文件

---

## 7. GitHub 镜像同步

- **部署前检查**：GitHub 落后 16 个 commit
- **同步命令**：`bash scripts/sync-to-github.sh`
- **门禁结果**：通过 9 / 失败 0 / 跳过 10
- **推送结果**：`7259a888f..94fc0f17b  origin/main -> main`
- **最终验证**：两边 main 完全一致（HEAD: `94fc0f17b`）

---

## 8. 配置清理检查

```bash
sudo grep -E "SHOW_DETAILS|DEBUG|TRACE" /etc/xiyu-bid/backend.env
```

- **发现**：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`
- **决定**：保留（用户连续 3 次决定保留用于运维监控，见 skill 第 6 条经验）
- **风险**：生产暴露健康详情，后续如需收紧可改为 `never`

---

## 9. 回滚信息

### 9.1 回滚锚点

| 项目 | 值 |
|---|---|
| Previous Release | `665dd3abb-api8080` |
| Previous Commit | `665dd3abb` |
| Previous Jar | `/opt/xiyu-bid/releases/665dd3abb-api8080/backend/app.jar` |
| DB Backup | `/opt/xiyu-bid/db-backups/winbid-5f730424f-api8080-*.sql.gz` |

### 9.2 回滚步骤

1. **后端回滚**：
   ```bash
   sudo cp /opt/xiyu-bid/releases/665dd3abb-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar
   sudo systemctl restart xiyu-bid-backend
   ```

2. **迁移回滚**（如需）：
   - U1129：恢复 `ca_seal_type` VARCHAR(50)
   - U1130：恢复 `education.start_date` NOT NULL
   - **注意**：V1129/V1130 均为非破坏性 MODIFY，回滚无数据丢失风险

---

## 10. 经验沉淀应用情况

本次部署应用了以下 skill 经验：

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ 主动执行 validate + DB 版本对比 + remote-deploy 内置 |
| 2 | Kafka SDK readiness 延迟 | ✅ 健康检查超时后手动验证，确认服务正常（已沉淀为已知行为） |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| 4 | Smoke 测试限制 | ✅ Admin 密码未知，使用 400/403/401 替代验证 |
| 5 | GitHub 镜像同步 | ✅ 部署后执行 `sync-to-github.sh` |
| 6 | SHOW_DETAILS=always 保留 | ✅ 用户决定保留（连续 3 次） |
| 7 | 幂等迁移设计 | ✅ V1129/V1130 均为非破坏性 MODIFY |
| 8 | systemctl sudo 权限 | ✅ `SYSTEMCTL_SUDO=true`（默认） |
| 12 | rollback 脚本命名 | ✅ U1129/U1130 前缀正确 |

---

## 11. 风险提示

1. **健康检查延迟**：Kafka SDK 导致 readiness 可能延迟 2-5 分钟，属已知行为，非故障
2. **SHOW_DETAILS=always**：生产暴露健康详情，需监控是否泄露敏感信息
3. **Agent 任务分支**：当前有 2 个活跃任务分支，建议及时清理

---

## 12. 部署确认清单

| 检查项 | 状态 |
|---|---|
| 早操三连 | ✅ |
| Git 状态干净 | ✅ |
| Flyway 预检通过 | ✅ |
| 本地打包成功 | ✅ |
| 产物校验通过 | ✅ |
| 上传成功 | ✅ |
| DB 备份完成 | ✅ |
| 服务重启成功 | ✅ |
| 健康检查 UP | ✅ |
| 迁移应用成功 | ✅ |
| Smoke 测试通过 | ✅ |
| GitHub 同步完成 | ✅ |
| 配置清理检查 | ✅（保留 SHOW_DETAILS） |
| 部署报告生成 | ✅ |

---

**部署结论**：✅ 第 36 次部署成功，服务正常运行，所有验证项通过。