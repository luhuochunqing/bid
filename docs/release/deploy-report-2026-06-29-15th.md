# 第十五次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| **部署时间** | 2026-06-29 11:56:18 CST（服务重启时间） |
| **部署人** | Trae AI Agent |
| **目标服务器** | 172.16.38.78 (winbid-01.test) |
| **应用端口** | 8080 |
| **部署状态** | ✅ 成功 |

## 基线信息

| 项目 | 值 |
|---|---|
| **部署 commit** | `05656257e` (!1319) |
| **第 14 次部署基线** | `cb6796cef` (!1311) |
| **新增 commit 数** | 24 个（含 merge commits） |
| **新增迁移数** | 2 个（V1108、V1109） |

## 第 14 次部署后合入的 PR（9 个）

| PR 号 | 主题 | 分类 |
|---|---|---|
| !1312 | CO-390 contactPerson 字段升级 userId + custodian/caCustodian 物理删除 | 功能 |
| !1313 | CO-388 账户列表按角色展示操作列（编辑/登记归还/下架/借用） | 功能 |
| !1314 | CO-389 资源账户详情字段漂移修复 + 平台密码字段补全 + bidAdmin 权限放开 | 修复 |
| !1315 | CO-391 bid_admin 人员证书批量导入模板下载 403 修复 | 修复 |
| !1316 | CO-392 结项阶段投标负责人/辅助人员内容显示与管理员不一致 | 修复 |
| !1317 | CO-383 上传者本人可删除自己上传的文档（未提交前可重传） | 修复 |
| !1318 | CO-387 修复详情接口缺失主/副投标负责人 ID 导致权限判断失效 | 修复 |
| !1319 | CO-393 项目负责人无法访问账户管理/CA信息管理页面 + 视图权限对齐蓝图 | 修复 |
| !1320 | 第十四次部署报告 | 文档 |

## 改动范围

- **56 个文件变更**：+2225 行 / -795 行
- **2 个新 Flyway 迁移**：
  - `V1108__platform_account_contact_person_userid.sql`（**非幂等**）：DROP+ADD `contact_person`（VARCHAR→BIGINT），DROP `custodian`、`ca_custodian` 冗余列。旧字符串数据按业务确认不回填，直接丢弃。
  - `V1109__add_resource_permissions_to_bid_project_leader.sql`（**幂等**）：3 个 UPDATE 使用 `NOT LIKE` 检查避免重复追加 `resource`、`resource-account`、`resource-ca` 菜单权限到 `bid-projectLeader` 角色。

## Flyway 预检与处置

### 预检 3 步法结果

| 步骤 | 检查项 | 结果 |
|---|---|---|
| Step 1 | `flyway-repair-runner.sh validate` | ✅ 171 migrations validated, all checksums match |
| Step 2 | DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1107，源码最新 V1109，V1108+V1109 为预期 pending |
| Step 3 | remote-deploy.sh 激活前自动 validate | ✅ 通过（171 migrations validated, execution time 00:00.084s） |

### 关键确认

- ✅ 本次有 2 个新迁移文件（V1108、V1109），完整执行 3 步预检
- ✅ JAR 内无重复版本号（package-release.sh 内置校验通过）
- ✅ V1108 在后端启动后 7 秒内成功执行（installed_on: 2026-06-29 11:56:25）
- ✅ V1109 在后端启动后 7 秒内成功执行（installed_on: 2026-06-29 11:56:25）

### 预检过程发现

- 服务器 `/tmp/migration-mysql/` 目录停留在 V1106（旧版），导致 `flyway-repair-runner.sh info` 将 V1107 误标为 "Future"。这是 source 目录过时问题，不影响 validate（validate 只检查 checksum 一致性）。
- 通过 SQL 直接查询 `flyway_schema_history` 确认 V1107 已应用（installed_on: 2026-06-29 08:20:14，与第 14 次部署报告一致）。

## 部署步骤

### 1. 同步基线

- 早操同步：`bash scripts/sync-env.sh .`
- 当前分支：`agent/trae-init`（锚点分支，ff-only 同步到 origin/main）
- 同步结果：HEAD = `05656257e` = origin/main（0/0 完全同步）
- 工作区干净，无未提交变更
- GitHub 镜像已同步（origin/main = github/main）

### 2. 本地打包

- **打包命令**：`RELEASE_ID="05656257e-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
- **前端构建**：
  - 生产同源构建模式（`VITE_API_BASE_URL=` 显式设空）
  - `src/api/config.js` 在 `import.meta.env.PROD=true` 时强制 `baseURL=""`（同源）
  - 产物：`index-DI_I07Qg.js`
  - `check:frontend-api-base` 验证通过
- **后端构建**：
  - `mvn clean -DskipTests package`（强制 clean，避免脏 target）
  - 构建耗时：23.648s
  - jar 内迁移文件：172 个 V*.sql + 1 个 B73 baseline（较第 14 次 +2）
  - 重复版本校验：✅ 无重复
- **Archive 大小**：136M

### 3. 代码版本验证

| 验证项 | 结果 | 说明 |
|---|---|---|
| jar 内 V1108 迁移文件 | ✅ 存在 | CO-390 contactPerson userId 升级 |
| jar 内 V1109 迁移文件 | ✅ 存在 | CO-393 bid-projectLeader 资源菜单权限 |
| jar 内 migration-mysql/ V 数 | ✅ 172 个 | 与源码一致（14th 是 170，+2 V1108+V1109） |
| jar 内 B73 baseline | ✅ 存在 | 与历史一致 |
| 前端 index.html | ✅ index-DI_I07Qg.js | 最新前端构建 |

### 4. 上传与激活

- **上传**：archive (137M) + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-05656257e-api8080-20260629115419.sql.gz`（2.8M）
- **remote-deploy.sh 执行流程**：
  1. ✅ 解压 archive 到 `/opt/xiyu-bid/releases/05656257e-api8080/`
  2. ✅ 激活前端（atomic swap 到 `/srv/www/xiyu-bid`）
  3. ✅ DB 备份完成（mysqldump + gzip）
  4. ✅ Flyway validate 预检通过（171 migrations, execution time 00:00.084s）
  5. ✅ 更新后端 jar：`/opt/xiyu-bid/shared/backend/app.jar`
  6. ✅ 写入部署记录：`/opt/xiyu-bid/deployed-release.json`（activatedAt: 2026-06-29T03:54:24Z）
  7. ⚠️ `systemctl restart xiyu-bid-backend` 失败：`Interactive authentication required`
  8. ✅ 手动执行 `sudo systemctl restart xiyu-bid-backend` 成功

### 5. 后端重启与健康检查

- **重启时间**：2026-06-29 11:56:18 CST（手动 sudo 重启）
- **PID**：3443（旧 PID 22793 已退出）
- **V1108 + V1109 应用时间**：2026-06-29 11:56:25（启动后 7 秒）
- **健康检查**：通过（第 78 次轮询，约 2 分 36 秒）
  - Kafka SDK 启动延迟符合预期（与第 13 次部署一致）
- **前端一致性校验**：✅ `src="/assets/index-DI_I07Qg.js"` 与 release 一致

## 验证结果

### 后端健康检查

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP", "details": { "total": "105553760256", "free": "47616274432" } },
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
| 前端入口 JS | ✅ `index-DI_I07Qg.js`（与 release 一致） |

### Flyway 迁移应用确认

```sql
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version IN (1107, 1108, 1109) ORDER BY version;

version  description                                          success  installed_on
1107     account borrow application project and comment       1        2026-06-29 08:20:14
1108     platform account contact person userid               1        2026-06-29 11:56:25
1109     add resource permissions to bid project leader       1        2026-06-29 11:56:25
```

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步方向 | Gitee main → GitHub main（单向镜像） |
| 同步前差异 | 0 个 commit（早操 sync-env.sh 已确认） |
| 同步后状态 | ✅ 完全一致（两边 HEAD = `05656257e`） |
| 同步命令 | 无需执行（已同步） |

## 回滚信息

| 项目 | 值 |
|---|---|
| **回滚版本** | `cb6796cef-api8080`（第 14 次部署版本） |
| **回滚 jar 路径** | `/opt/xiyu-bid/releases/cb6796cef-api8080/backend/app.jar` |
| **回滚前端路径** | `/opt/xiyu-bid/releases/cb6796cef-api8080/frontend/` |
| **回滚命令** | 恢复旧 jar + 前端 + `sudo systemctl restart xiyu-bid-backend` |
| **DB 备份** | `/opt/xiyu-bid/db-backups/winbid-05656257e-api8080-20260629115419.sql.gz` |
| **V1108 回滚注意** | DROP COLUMN 已删除 custodian/ca_custodian/contact_person(旧 VARCHAR)，回滚需手动 ADD 回原列 + 恢复旧字符串数据（已丢弃，无法恢复） |
| **V1109 回滚注意** | UPDATE 追加的菜单权限需手动从 menu_permissions 字段中移除 `resource`、`resource-account`、`resource-ca` |

## 经验沉淀应用情况

本次部署应用了 xiyu-server-deploy skill 中沉淀的 7 条关键经验：

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ 完整执行（validate + source-sync SQL 查询 + remote-deploy 内置） |
| 2 | Readiness 延迟恢复 | ✅ 容忍 2 分 36 秒 Kafka SDK 初始化，未急于回滚 |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，baseURL="" |
| 4 | Smoke 测试限制 | ✅ admin 密码未知，用 400/403/401 验证接口路由 |
| 5 | GitHub 镜像同步 | ✅ 两边已一致（无需推送） |
| 6 | 临时调试配置清理 | ⚠️ `SHOW_DETAILS=always` 保留现状（用户连续三次决定保留，运维监控需要） |
| 7 | 幂等迁移设计 | ⚠️ V1108 非幂等（破坏性 DROP+ADD），V1109 幂等（LIKE 检查） |

## 风险提示

1. **V1108 是破坏性 schema 变更**：DROP 了 `custodian`、`ca_custodian` 列 + 旧 `contact_person` VARCHAR 列。旧字符串数据已丢弃，无法回滚恢复。生产环境靠 Flyway 版本号机制防重复执行，正常流程无风险；如需回滚 DB 状态，需从 DB 备份恢复整张 `platform_accounts` 表。

2. **remote-deploy.sh systemctl 权限问题**：本次 `remote-deploy.sh` 的 `systemctl restart` 步骤因 `Interactive authentication required` 失败（默认 `SYSTEMCTL_SUDO=false`）。jar 已被覆盖但服务未重启，通过手动 `sudo systemctl restart` 完成。建议后续部署时设置 `SYSTEMCTL_SUDO=true` 环境变量（jetty 用户已配置 NOPASSWD sudo），让 remote-deploy.sh 自动用 sudo 重启。

3. **MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always**：`/actuator/health` 暴露 DB/Redis/JWT 组件详情。用户连续三次决定保留现状（运维监控需要）。如后续需收紧安全，可改为 `never` 并重启后端。

4. **服务器 `/tmp/migration-mysql/` 目录过时**：停留在 V1106，导致 `flyway-repair-runner.sh info` 将 V1107 标为 "Future"。不影响 validate（validate 只看 checksum），但建议后续部署时同步更新该目录到最新版本，避免 info 输出误导。

5. **V1108 + V1109 rollback 脚本状态更正**：部署时误判为"无对应 rollback 脚本"，实际核查发现 `db/rollback/migration-mysql/` 下已存在 `U1108__*.sql`、`U1109__*.sql`（命名前缀是 `U` 不是 `RV`）。`U1108` 恢复列结构（数据已丢失无法恢复），`U1109` 含 REGEXP_REPLACE 回滚示例。原文"建议补充 rollback 脚本"作废。

## 部署确认

- [x] 早操同步 + 工作区干净
- [x] Flyway 预检 3 步法通过（含 V1108 + V1109 新迁移）
- [x] 本地打包验证（前端同源 + 后端 clean build + 无重复迁移）
- [x] 代码版本验证（V1108 + V1109 在 jar 内 + 172 V + 1 B73）
- [x] DB 备份完成（2.8M）
- [x] remote-deploy.sh 执行成功（含 Flyway validate 预检）
- [x] 后端重启 + 健康检查 UP（手动 sudo restart）
- [x] V1108 + V1109 迁移已应用（11:56:25）
- [x] API smoke 通过（health + readiness + 3 个接口路由）
- [x] 前端验证通过（页面 200 + JS 一致性）
- [x] GitHub 镜像同步完成（两边一致）
- [x] 部署报告生成
