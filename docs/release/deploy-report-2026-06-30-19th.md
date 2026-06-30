# 第十九次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| **部署时间** | 2026-06-30 08:49:53 CST（服务重启时间） |
| **部署人** | Trae AI Agent |
| **目标服务器** | 172.16.38.78 (winbid-01.test) |
| **应用端口** | 8080 |
| **部署状态** | ✅ 成功 |

## 基线信息

| 项目 | 值 |
|---|---|
| **部署 commit** | `bd7eedfaf` (HEAD = origin/main) |
| **第 18 次部署基线** | `681871c2f` (!1345) |
| **新增 commit 数** | 65 个 |
| **新增迁移数** | 2 个（V1112 + V1113） |
| **回滚脚本配套** | ✅ U1112 + U1113 均已存在 |
| **代码改动统计** | 114 文件，+6007/-841 行 |
| **打包耗时** | 22 秒（mvn clean -DskipTests package） |
| **Release Archive** | `.release/xiyu-bid-release-bd7eedfaf-api8080.tar.gz`（143 MB） |
| **Release ID** | `bd7eedfaf-api8080` |

## PR 列表（增量范围内合入 main 的 PR）

| PR | 标题 | 主要范围 |
|---|---|---|
| !1374 | docs(test-infra): 确认生产 sql_mode 为空 + 关闭 FlywayMysqlContainerTest TODO | 文档 |
| !1373 | fix: 修复 OrganizationDirectoryHttpGateway 的 OkHttp3 GET body 限制 | 后端 HTTP 客户端 |
| !1372 | test(CO-386/CO-403): PlatformAccountBorrowService 真实 MySQL 集成测试 + V1113 schema 漂移修复 | 测试 + V1113 迁移 |
| !1371 | fix: WeComApiClient & OrganizationDirectoryHttpGateway 显式指定 SimpleClientHttpRequestFactory | 后端 HTTP 客户端 |
| !1370 | fix(tender): CRM 反查工号本地无匹配时不应返回 noMatch | CRM 集成 |
| !1369 | fix: 根因修复 sidecar GET 请求带 body 报错 + 补单元测试 | Sidecar 健康检查 |
| !1368 | fix(test-infra): 消除 Flyway 测试静默跳过 + CI 检测 Skipped 测试 | 测试基础设施 |
| !1367 | fix(test-infra): 修复 Flyway 测试门禁两个结构性缺口 + 发现 V1077 迁移失败 | 测试基础设施 |
| !1366 | fix(CO-400 round5): 密码查看权限支持投标专员作为绑定联系人 | 权限 |
| !1365 | fix(CO-403): 修复借用申请管理员权限缺失 + 双归还入口数据不一致 | 借用申请 |
| !1364 | fix(CO-400 四轮): 投标专员作为绑定联系人时编辑页密码回显 | 权限 |
| !1363 | fix(CO-408): 修复评标/凭证/复盘文件回填不生效 + 修复 6 个坏测试 | 项目阶段 |
| !1362 | feat: AI 功能防复发加固 + Sidecar 配置统一 + 前端错误提示优化 | AI + Sidecar + UX |
| !1361 | fix(CO-403): 纠正结项阶段编辑/提交/审核权角色错配 + 补职责分离守卫 | 权限 |
| !1360 | CO-413 task board reject reason dialog | 任务看板 |
| !1359 | fix: 批量修复 16+ 个标讯中心/项目/品牌授权模块 bug | Bug 修复 |
| !1357 | fix(migration): V1112 清理历史【待立项】占位任务 根治 CO-400 复发 | V1112 迁移 |
| !1357 | fix(ai): 修复 AI 真实调用配置断裂 + 标书审查新增 AI 预审 | AI 配置 |
| !1356 | fix(CO-411): task board auto refresh on status change | 任务看板 |
| !1354 | fix(CO-412): tender table project name column auto wrap | 标讯列表 |
| !1352 | feat: AI 能力最小可感知实现（商机预测推送 + 风险评估规则 + 拆解 7 领域） | AI 能力 |
| !1350 | fix(qualification): 删除资质 AI 解析的 Mock 兜底，改为业务异常透传 | 资质 |
| !1355 | 沉淀 CO-390 经验（UserPicker 选人控件 + 联动回填 4 层链路 SOP） | 文档 |
| !1353 | feat(UX): 统一优化错误提示体验（HTTP 错误消息 + ElMessage 样式 + 表单验证提示） | UX |
| !1351 | fix(docinsight): 修复 checkSidecarHealth IllegalArgumentException 导致 500 | Sidecar |
| !1349 | docs(release): 补齐第 16/17 次部署报告 | 文档 |
| !1348 | fix(scripts): 增加 Flyway 版本号撞号检测（第 26 条经验沉淀） | 防护体系 |
| !1347 | docs(release): 第 18 次部署报告 | 文档 |
| !1346 | fix(CO-408): restore file list on load for evaluation/evidence/retrospective | 项目阶段 |

## 改动范围

### 数据库迁移（新增 2 个）

| 版本 | 文件 | 类型 | 幂等性 | 风险 |
|---|---|---|---|---|
| V1112 | `V1112__cleanup_legacy_pending_initiation_tasks.sql` | DELETE WHERE | ✅ 幂等（DELETE 带 WHERE，重跑 no-op） | 低（清理前端不可见的占位任务，零业务影响） |
| V1113 | `V1113__fix_account_borrow_applications_status_enum.sql` | ALTER TABLE MODIFY COLUMN | ⚠️ 非幂等（Flyway 版本号机制防重复） | 低（MySQL 8.0 INSTANT 元数据操作不锁表，ENUM 取值与现有数据对齐） |

回滚脚本配套：`U1112__cleanup_legacy_pending_initiation_tasks.sql`、`U1113__fix_account_borrow_applications_status_enum.sql`（位置 `db/rollback/migration-mysql/`）。

### 主要功能改动

1. **AI 能力加固**：
   - Sidecar 配置统一（`app.converter.sidecar-*` → `app.doc-insight.sidecar-*`）
   - AI 服务重试机制（429/5xx）
   - 健康检查与启动校验
   - 移除 RoutingAiProvider legacy mock 判断，让数据库配置真正生效
   - 标书审查新增 AI 预审摘要
   - 商机预测推送 CRM 事件库最小可感知实现
2. **Sidecar HTTP 客户端修复**：
   - 根因修复 OkHttp3 GET 请求带 body 报错
   - 显式指定 `SimpleClientHttpRequestFactory` 避开 OkHttp3 限制
   - `SidecarHealthIndicator` 改用 JDK HttpClient
   - `checkSidecarHealth` IllegalArgumentException 捕获
3. **权限修复**：
   - CO-400 round5：投标专员作为绑定联系人时密码查看/回显
   - CO-403：借用申请管理员权限 + 双归还入口一致性 + 结项阶段角色错配
   - 职责分离守卫补全
4. **任务看板**：
   - CO-413 待审核任务驳回原因对话框
   - CO-411 状态变更后自动刷新
5. **Bug 修复**：
   - CO-408 评标/凭证/复盘文件回填
   - CO-412 标讯列表项目名列自动换行
   - CRM 反查工号本地无匹配处理
   - 16+ 标讯中心/项目/品牌授权模块 bug 批量修复
6. **测试基础设施加固**：
   - 消除 Flyway 测试静默跳过 + CI 检测 Skipped 测试
   - 修复 Flyway 测试门禁两个结构性缺口
   - 增加 Flyway 版本号撞号检测（第 26 条经验沉淀）
   - PlatformAccountBorrowService 真实 MySQL 集成测试

## Flyway 预检结果（3 步法）

### Step 1: 服务器 flyway validate
```
Successfully validated 175 migrations (execution time 00:00.086s)
VALIDATE OK - all checksums match
```
✅ DB 当前状态健康，无 checksum mismatch。

### Step 2: DB 已应用版本 vs 源码最新版本对比

部署前 DB 最新已应用版本（按 installed_rank DESC）：

| version | description | success | installed_on |
|---|---|---|---|
| 1111 | add pending approval to platform accounts status | 1 | 2026-06-29 17:08:35 |
| 1110 | cleanup legacy pending assignment tasks | 1 | 2026-06-29 17:08:35 |
| 1109 | add resource permissions to bid project leader | 1 | 2026-06-29 11:56:25 |
| 1108 | platform account contact person userid | 1 | 2026-06-29 11:56:25 |
| 1107 | account borrow application project and comment | 1 | 2026-06-29 08:20:14 |
| 1106 | add created by to tasks | 1 | 2026-06-28 15:44:19 |
| 1105 | drop in progress cancelled status | 1 | 2026-06-27 18:10:35 |
| 1104 | repair personnel schema drift layer2 | 1 | 2026-06-27 18:10:35 |

- 源码最新版本：V1113
- 待应用迁移：V1112 + V1113（与源码增量一致 ✅）

### Step 3: remote-deploy.sh 内置 validate
```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

### 迁移安全性评估

- **V1112**: `DELETE FROM tasks WHERE status='TODO' AND title LIKE '【待立项】%'` —— 幂等（DELETE 带 WHERE），零业务影响（前端本就不展示）
- **V1113**: `ALTER TABLE account_borrow_applications MODIFY COLUMN status ENUM(...)` —— MySQL 8.0 INSTANT 元数据操作不锁表，ENUM 取值与现有数据对齐

## 部署步骤

### Step 4: 本地打包（生产同源构建模式）

```bash
RELEASE_ID="bd7eedfaf-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- `VITE_API_BASE_URL=` 显式设空，触发 `src/api/config.js` 同源构建（`baseURL=""`）
- `mvn clean -DskipTests package` 强制 clean
- BUILD SUCCESS（22 秒）
- jar 内 Flyway 迁移版本无重复 ✅
- 产物路径：`.release/xiyu-bid-release-bd7eedfaf-api8080.tar.gz`

### Step 5: 产物校验

- jar 内 V1112/V1113 文件存在 ✅
- jar 内迁移文件总数：176 个
- 重复版本号检查：✅ 无重复
- 前端入口：`assets/index-CzlO-urD.js` + `assets/index-Cmq0rLNS.css`

### Step 6: 上传 + 部署

```bash
scp .release/xiyu-bid-release-bd7eedfaf-api8080.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-bd7eedfaf-api8080.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:8080/actuator/health \
  RELEASE_ID=bd7eedfaf-api8080 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="source /etc/xiyu-bid/backend.env && mysqldump ... | gzip > /opt/xiyu-bid/db-backups/winbid-bd7eedfaf-api8080-$(date +%Y%m%d%H%M%S).sql.gz && echo DB backup done" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- `SYSTEMCTL_SUDO=true` 显式传（默认也是 true，PR !1324 后固化）
- DB 备份位置：`/opt/xiyu-bid/db-backups/winbid-bd7eedfaf-api8080-*.sql.gz`
- remote-deploy.sh 自动顺序：DB 备份 → 解压 → Flyway validate → 覆盖 jar → 切换前端 → sudo systemctl restart → 健康检查

## 验证结果

### 健康检查

| 时间点 | 事件 |
|---|---|
| 08:48:23 | Flyway validate 通过（覆盖 jar 前） |
| 08:49:53 | 后端服务重启（PID 9101） |
| 08:49:59 | V1112 应用成功 |
| 08:50:00 | V1113 应用成功 |
| 08:52:20-08:52:44 | health 持续 503（Kafka SDK 启动中） |
| 08:52:45 | OrganizationEventSdkKafkaStarter 启动 Kafka SDK |
| 08:52:45 | Kafka consumer started successfully |
| 08:52:46 | health 返回 200 ✅ |

- 健康检查等待耗时：**2 分 53 秒**（08:49:53 → 08:52:46）
- 这是 Kafka SDK readiness 延迟的已知行为（第 8、9、10、13、15、19 次出现，第 6 次累计）

### 迁移应用验证

```sql
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version IN ("1112","1113") ORDER BY version;
```

| version | description | success | installed_on |
|---|---|---|---|
| 1112 | cleanup legacy pending initiation tasks | 1 | 2026-06-30 08:49:59 |
| 1113 | fix account borrow applications status enum | 1 | 2026-06-30 08:50:00 |

✅ 两个新迁移均 success=1 已应用。

### API Smoke 测试（admin 密码未知，用 400/403/401 验证接口路由）

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 UP | 200 | ✅ |
| `GET /actuator/health/readiness` | 200 UP | 200 | ✅ |
| `POST /api/auth/login` (空 body) | 400（验证错误） | 400 | ✅ |
| `GET /api/projects` | 403（需认证） | 403 | ✅ |
| `GET /api/integration/crm/health` | 401（需认证） | 401 | ✅ |

### 前端验证

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /` | 200 | 200 | ✅ |
| `GET /login` | 200 | 200 | ✅ |
| 入口 JS | 与 release 一致 | `assets/index-CzlO-urD.js` | ✅ |

> **登录 smoke 因 admin 密码未授予而跳过**，用 400/403/401 替代验证策略（第 6 次起固化）。

## GitHub 镜像同步

```bash
git log --oneline github/main..origin/main | wc -l   # 输出 0
```

| 检查项 | 结果 |
|---|---|
| `github/main..origin/main` 落后 commit 数 | 0 ✅ |
| `github/main` HEAD | `bd7eedfaf` |
| `origin/main` HEAD | `bd7eedfaf` |
| 镜像同步状态 | ✅ 完全一致，无需 `sync-to-github.sh` |

## 回滚信息

| 项目 | 值 |
|---|---|
| **回滚姿态** | ready（未触发） |
| **上一可用 release** | `681871c2f-api8080`（第 18 次，2026-06-29 17:08:28 CST 激活） |
| **回滚 jar 位置** | `/opt/xiyu-bid/releases/681871c2f-api8080/backend/app.jar` |
| **回滚前端位置** | `/opt/xiyu-bid/releases/681871c2f-api8080/frontend/` |
| **DB 备份** | `/opt/xiyu-bid/db-backups/winbid-bd7eedfaf-api8080-*.sql.gz`（部署前） |
| **V1112 回滚限制** | U1112 仅恢复任务记录结构，已删除的占位任务需从 DB 备份恢复（业务确认零影响） |
| **V1113 回滚限制** | U1113 将 ENUM 改回 VARCHAR(30)（数据无损失） |
| **回滚命令** | 详见 `docs/release/ROLLBACK.md` |

## 经验沉淀应用情况

### 已应用的 15 条核心经验

1. **Flyway 预检 3 步法**（第 1 条）：✅ 部署前主动 validate + DB 版本对比 + remote-deploy 内置
2. **Kafka SDK readiness 延迟**（第 2 条）：✅ 出现 2 分 53 秒延迟，未急于回滚，自恢复
3. **生产前端同源构建**（第 3 条）：✅ `VITE_API_BASE_URL=` 显式设空
4. **Smoke 测试限制**（第 4 条）：✅ admin 密码未知，用 400/403/401 替代
5. **GitHub 镜像同步**（第 5 条）：✅ 部署前确认 `github/main..origin/main = 0`
6. **临时调试配置清理**（第 6 条）：✅ 检查 SHOW_DETAILS/DEBUG/TRACE，仅 SHOW_DETAILS=always 保留（用户连续 4 次决定保留）
7. **幂等迁移设计**（第 7 条）：✅ V1112 幂等，V1113 非幂等但版本号机制防重复
8. **systemctl sudo 权限**（第 8 条）：✅ `SYSTEMCTL_SUDO=true` 显式传，无 Interactive authentication 错误
9. **git.properties commit id 不准确**（第 9 条）：未影响（用 release id 替代追溯）
10. **破坏性 schema 变更**（第 10 条）：本次无 DROP COLUMN 类破坏性变更
11. **/tmp/migration-mysql/ 目录过时**（第 11 条）：未影响（直接 SQL 查询确认）
12. **rollback 脚本命名规范**（第 12 条）：✅ U1112/U1113 命名前缀正确（不是 RV）
13. **前端目录权限**（第 13 条）：未触发（remote-deploy.sh 一致性验证通过）
14. **macOS ._ 残留文件**（第 14 条）：未触发
15. **Flyway 防护体系**（第 15 条）：✅ 15 项防护全部生效，第 11-19 次未发生 Flyway 启动失败事故

### 本次部署新增观察

- **HTTP 代理导致外部 502**：Mac 上 `HTTP_PROXY=http://127.0.0.1:7897` 导致从外部访问服务器 8080 返回 502，绕过代理（`curl --noproxy '*'`）后 200。**未来部署 smoke 测试必须加 `--noproxy '*'`**，避免误判。

## 风险提示

1. **V1113 非幂等**：`ALTER TABLE MODIFY COLUMN` 不带 IF NOT EXISTS，重复执行会失败。生产环境靠 Flyway 版本号机制防重复执行，正常流程无风险。如手动操作需谨慎。
2. **V1112 删除数据**：DELETE 删除了 `status='TODO' AND title LIKE '【待立项】%'` 的占位任务。业务确认零影响（前端本就不展示，测试数据可重建）。如需恢复，从 DB 备份恢复整张 `tasks` 表。
3. **Kafka SDK readiness 延迟**：第 6 次累计出现（第 8/9/10/13/15/19 次）。延迟 2-5 分钟，可自恢复，不必急于回滚。修复方向：将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池。
4. **SHOW_DETAILS=always 保留**：第 4 次连续决定保留（第 13/14/15/19 次）。生产 health 端点暴露详情，便于运维监控。如需收紧安全，改为 `never` 并重启后端。
5. **HTTP 代理影响外部访问**：Mac 上的 HTTP 代理（127.0.0.1:7897）会导致外部访问服务器 8080 返回 502。本地 smoke 测试必须加 `--noproxy '*'`。

## 部署确认清单

| 检查项 | 结果 |
|---|---|
| 早操三连（dev-env + sync-env + check-git-wrapper） | ✅ |
| 基线确认（HEAD = origin/main，工作区干净） | ✅ |
| GitHub 镜像同步（github/main..origin/main = 0） | ✅ |
| 服务器连通性 + 健康检查 | ✅ |
| 增量 commit + 迁移文件变更确认 | ✅（65 commit，V1112+V1113） |
| Flyway 预检 3 步法 | ✅（validate OK + DB 版本对比 + remote-deploy 内置） |
| 迁移幂等性评估 | ✅（V1112 幂等，V1113 非幂等但版本号防重复） |
| 回滚脚本配套（U1112/U1113） | ✅ |
| 本地打包（VITE_API_BASE_URL= 同源构建） | ✅ |
| 产物校验（jar 内迁移文件数 + 无重复版本） | ✅（176 文件无重复） |
| 上传 archive + remote-deploy.sh | ✅ |
| DB 备份（部署前自动 mysqldump） | ✅ |
| remote-deploy exit 0 | ✅ |
| 健康检查 UP（容忍 Kafka 延迟 2 分 53 秒） | ✅ |
| 迁移应用验证（V1112 + V1113 success=1） | ✅ |
| Smoke 测试（health/readiness/3 接口/前端） | ✅ |
| 前端入口一致性（index-CzlO-urD.js） | ✅ |
| 配置清理检查（仅 SHOW_DETAILS=always 保留） | ✅ |
| GitHub 镜像同步状态（部署后复查） | ✅ |
| 部署报告生成 | ✅（本文件） |

## 部署历史 commit 链（更新至第 19 次）

| # | 日期 | Release ID | 新增迁移 | 备注 |
|---|---|---|---|---|
| 16 | 2026-06-29 | （第 16 次报告补齐于 !1349） | - | - |
| 17 | 2026-06-29 | （第 17 次报告补齐于 !1349） | - | - |
| 18 | 2026-06-29 | `681871c2f-api8080` | V1110 + V1111 | V1110 撞号事故 + hotfix PR !1345 |
| **19** | **2026-06-30** | **`bd7eedfaf-api8080`** | **V1112 + V1113** | **65 commit 增量，AI 加固 + Sidecar HTTP 客户端根因修复 + 测试基础设施加固** |

---

**部署完成时间**：2026-06-30 08:53 CST
**报告生成时间**：2026-06-30 08:55 CST
**回滚姿态**：ready（未触发）
