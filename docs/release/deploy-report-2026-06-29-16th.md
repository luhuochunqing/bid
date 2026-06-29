# 第十六次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| **部署时间** | 2026-06-29 14:46:04 CST（服务重启时间） |
| **部署人** | Trae AI Agent |
| **目标服务器** | 172.16.38.78 (winbid-01.test) |
| **应用端口** | 8080 |
| **部署状态** | ✅ 成功 |

## 基线信息

| 项目 | 值 |
|---|---|
| **部署 commit** | `361a5653f` (!1332) |
| **第 15 次部署基线** | `05656257e` (!1319) |
| **新增 commit 数** | 27 个（含 merge commits） |
| **新增迁移数** | 0 个（纯代码改动部署） |

## 第 15 次部署后合入的 PR（12 个）

| PR 号 | 主题 | 分类 |
|---|---|---|
| !1321 | CO-395 结项阶段退回凭证文件名+下载链接 | 修复 |
| !1322 | CO-398 任务看板截止时间统一为 YYYY-MM-DD 格式 | 修复 |
| !1323 | 品牌授权工具栏按钮改为右侧并排摆放 | 修复 |
| !1324 | 第十五次部署后续 — SYSTEMCTL_SUDO 默认值修复 + 部署报告 | 发布 |
| !1325 | CO-397 任务详情抽屉增加提交审核/驳回/通过按钮 | 功能 |
| !1326 | CORS 白名单补放 X-Trace-Id，修复前端登录预检失败 | 安全 |
| !1327 | CO-401 删除标赛建立时自动生成的待分配任务 | 清理 |
| !1328 | CO-383 前端 投标文件删除权限支持上传者本人 | 修复 |
| !1329 | CO-392 修复结项预览端点 @PreAuthorize 角色白名单漏配导致投标辅助人员 403 | 修复 |
| !1330 | CO-400 账户详情 dialog 调用详情接口拉取完整字段 | 修复 |
| !1331 | CO-402 标讯联系人2仅姓名为空时不返回到 contactInfo 数组 | 修复 |
| !1332 | CO-393 补齐 resource 父权限以解锁 OSS 用户路由访问 | 修复 |

## 改动范围

- **纯代码改动部署**：无 Flyway 迁移文件变更（`backend/src/main/resources/db/migration-mysql/` 与 `db/rollback/migration-mysql/` 均无 diff）
- 改动覆盖：前端 UI（任务看板/任务抽屉/品牌授权/账户详情 dialog）、后端权限（CO-392/CO-393/CO-383）、CORS 安全头、结项凭证下载、标讯联系人空值处理、自动任务清理

## Flyway 预检与处置

### 预检 3 步法结果

| 步骤 | 检查项 | 结果 |
|---|---|---|
| Step 1 | `flyway-repair-runner.sh validate` | ✅ 173 migrations validated, all checksums match |
| Step 2 | DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1109，源码最新 V1109（无 pending） |
| Step 3 | remote-deploy.sh 激活前自动 validate | ✅ 通过（173 migrations validated, execution time 00:00.082s） |

### 关键确认

- ✅ 本次无新迁移文件，DB schema 与第 15 次部署后保持一致
- ✅ JAR 内 172 个 V*.sql + 1 个 B73 baseline（与第 15 次一致，无重复版本）
- ✅ 服务器 `flyway_schema_history` 最新 5 个版本：1105、1106、1107、1108、1109（与第 15 次部署报告一致）

## 部署步骤

### 1. 同步基线

- 早操三连：`source scripts/dev-env.sh` + 手动 ff-only 同步 + `bash scripts/check-git-wrapper.sh`
- 当前分支：`agent/trae-init`（锚点分支，ff-only 同步到 origin/main）
- 同步结果：HEAD = `361a5653f` = origin/main（Already up to date）
- 工作区干净，无未提交变更
- GitHub 镜像落后 26 commits（部署后已同步）

### 2. 本地打包

- **打包命令**：`RELEASE_ID="361a5653f-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
- **前端构建**：
  - 生产同源构建模式（`VITE_API_BASE_URL=` 显式设空）
  - `src/api/config.js` 在 `import.meta.env.PROD=true` 时强制 `baseURL=""`（同源）
  - 产物：`index-BrZImfgp.js` + `index-DydLY9hE.css`
  - `check:frontend-api-base` 验证通过
- **后端构建**：
  - `mvn clean -DskipTests package`（强制 clean，避免脏 target）
  - 构建耗时：23.122s
  - jar 内迁移文件：172 个 V*.sql + 1 个 B73 baseline（与第 15 次一致）
  - 重复版本校验：✅ 无重复

### 3. 代码版本验证

| 验证项 | 结果 | 说明 |
|---|---|---|
| jar 内最高 V 版本 | ✅ V1109 | 与源码、DB 一致（无新迁移） |
| jar 内 migration-mysql/ V 数 | ✅ 172 个 | 与源码一致 |
| jar 内 B73 baseline | ✅ 存在 | 与历史一致 |
| 前端 index.html | ✅ index-BrZImfgp.js | 最新前端构建 |

### 4. 上传与激活

- **上传**：archive + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-361a5653f-<timestamp>.sql.gz`
- **remote-deploy.sh 执行流程**：
  1. ✅ 解压 archive 到 `/opt/xiyu-bid/releases/361a5653f-api8080/`
  2. ✅ 激活前端（atomic swap 到 `/srv/www/xiyu-bid`）
  3. ✅ DB 备份完成（mysqldump + gzip）
  4. ✅ Flyway validate 预检通过（173 migrations, execution time 00:00.082s）
  5. ✅ 更新后端 jar：`/opt/xiyu-bid/shared/backend/app.jar`
  6. ✅ 写入部署记录：`/opt/xiyu-bid/deployed-release.json`
  7. ✅ `sudo systemctl restart xiyu-bid-backend` 成功（`SYSTEMCTL_SUDO=true`，第 15 次部署后已修复默认值）

### 5. 后端重启与健康检查

- **重启时间**：2026-06-29 14:46:04 CST（自动 sudo 重启，无人工干预）
- **PID**：13629
- **健康检查**：通过（第 1 次轮询，2 秒）
  - ✅ 未出现 Kafka SDK readiness 延迟（与第 15 次的 2 分 36 秒相比显著改善）
- **前端一致性校验**：✅ `src="/assets/index-BrZImfgp.js"` 与 release 一致

## 验证结果

### 后端健康检查

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP", "details": { "total": 105553760256, "free": 47282511872 } },
    "jwt": { "status": "UP", "details": { "algorithm": "HMAC-SHA256", "secretLength": 64, "strength": "STRONG" } },
    "livenessState": { "status": "UP" },
    "ping": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "redis": { "status": "UP", "details": { "version": "6.2.19" } }
  }
}
```

### API Smoke 测试

| 接口 | 结果 | 说明 |
|---|---|---|
| `/actuator/health` | ✅ 200 UP | 所有组件 UP |
| `/actuator/health/readiness` | ✅ 200 UP | Readiness 正常（无 Kafka 延迟问题） |
| `POST /api/auth/login` | ✅ 400 | 空密码触发验证错误（`Username is required; Password is required`），接口路由正常 |
| `GET /api/projects` | ✅ 403 | 需要认证（接口正常） |
| `/api/integration/crm/health` | ✅ 401 | 需要认证（接口正常） |

### 前端验证

| 端点 | 结果 |
|---|---|
| `/` (首页) | ✅ 200 |
| `/login` | ✅ 200 |
| 前端入口 JS | ✅ `index-BrZImfgp.js`（与 release 一致） |

### Flyway 迁移应用确认

本次无新迁移，DB schema 与第 15 次部署后保持一致：

```sql
SELECT version, description, success, installed_on FROM flyway_schema_history
WHERE type="SQL" AND success=1 ORDER BY installed_rank DESC LIMIT 5;

version  description                                          success  installed_on
1109     add resource permissions to bid project leader       1        2026-06-29 11:56:25
1108     platform account contact person userid               1        2026-06-29 11:56:25
1107     account borrow application project and comment       1        2026-06-29 08:20:14
1106     add created by to tasks                              1        2026-06-28 15:44:19
1105     drop in progress cancelled status                    1        2026-06-27 18:10:35
```

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步方向 | Gitee main → GitHub main（单向镜像） |
| 同步前差异 | 26 个 commit（HEAD 推进过程中 GitHub 镜像落后） |
| 同步后状态 | ✅ 完全一致（两边 HEAD = `361a5653f`） |
| 同步命令 | `bash scripts/sync-to-github.sh` |

## 回滚信息

| 项目 | 值 |
|---|---|
| **回滚版本** | `05656257e-api8080`（第 15 次部署版本） |
| **回滚 jar 路径** | `/opt/xiyu-bid/releases/05656257e-api8080/backend/app.jar` |
| **回滚前端路径** | `/opt/xiyu-bid/releases/05656257e-api8080/frontend/` |
| **回滚命令** | 恢复旧 jar + 前端 + `sudo systemctl restart xiyu-bid-backend` |
| **DB 备份** | `/opt/xiyu-bid/db-backups/winbid-361a5653f-<timestamp>.sql.gz` |
| **回滚风险** | 低（本次无 DB schema 变更，回滚纯代码切换） |

## 经验沉淀应用情况

本次部署应用了 xiyu-server-deploy skill 中沉淀的关键经验：

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ 完整执行（validate + source-sync SQL 查询 + remote-deploy 内置） |
| 2 | Readiness 延迟恢复 | ✅ 本次未出现 Kafka SDK 延迟（2 秒内 UP），但仍保留 4 分钟容忍窗口 |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，baseURL="" |
| 4 | Smoke 测试限制 | ✅ admin 密码未知，用 400/403/401 验证接口路由 |
| 5 | GitHub 镜像同步 | ✅ 部署后执行 `sync-to-github.sh`，两边一致 |
| 6 | 临时调试配置清理 | ⚠️ `SHOW_DETAILS=always` 保留现状（用户连续四次决定保留，运维监控需要） |
| 7 | systemctl sudo 权限 | ✅ 第 15 次部署后 `SYSTEMCTL_SUDO=true` 已设为默认，本次自动 sudo 重启成功，无人工干预 |
| 8 | 幂等迁移设计 | N/A（本次无新迁移） |

## 风险提示

1. **纯代码部署的低风险性**：本次无 Flyway 迁移文件变更，DB schema 与第 15 次部署后完全一致，回滚仅需切换 jar + 前端，无 DB 回滚风险。

2. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`**：`/actuator/health` 暴露 DB/Redis/JWT 组件详情。用户连续四次决定保留现状（运维监控需要）。如后续需收紧安全，可改为 `never` 并重启后端。

3. **服务器 `/tmp/migration-mysql/` 目录过时**：仍停留在旧版本，导致 `flyway-repair-runner.sh info` 可能误标版本为 "Future"。不影响 validate（validate 只看 checksum），建议后续部署时同步更新该目录。

4. **本次未触发 Kafka SDK readiness 延迟**：与第 15 次的 2 分 36 秒相比显著改善（2 秒内 UP）。但该问题在第 8/9/10/13/15 次均出现过，本次未出现不代表根因已修复，后续部署仍应保留 4 分钟容忍窗口。

5. **GitHub 镜像同步习惯化**：本次部署前 GitHub 镜像落后 26 commits，部署后已同步。建议每次部署完成后均执行 `bash scripts/sync-to-github.sh` 作为标准步骤。

## 部署确认

- [x] 早操三连 + 工作区干净
- [x] Flyway 预检 3 步法通过（无新迁移，validate + DB 版本对比 + remote-deploy 内置）
- [x] 本地打包验证（前端同源 + 后端 clean build + 无重复迁移）
- [x] 代码版本验证（jar 内 172 V + 1 B73 + 最高 V1109）
- [x] DB 备份完成
- [x] remote-deploy.sh 执行成功（含 Flyway validate 预检 + 自动 sudo 重启）
- [x] 后端重启 + 健康检查 UP（2 秒，无 Kafka 延迟）
- [x] API smoke 通过（health + readiness + 3 个接口路由）
- [x] 前端验证通过（页面 200 + JS 一致性 `index-BrZImfgp.js`）
- [x] GitHub 镜像同步完成（两边 HEAD = `361a5653f`）
- [x] 部署报告生成
