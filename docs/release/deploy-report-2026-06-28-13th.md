# 第十三次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| **部署时间** | 2026-06-28 21:57 CST |
| **部署人** | Trae AI Agent |
| **目标服务器** | 172.16.38.78 (winbid-01.test) |
| **应用端口** | 8080 |
| **部署状态** | ✅ 成功 |

## 基线信息

| 项目 | 值 |
|---|---|
| **部署 commit** | `c577dec2c` (!1301) |
| **第 12 次部署基线** | `dfe1d8286` (!1296，未生成报告) |
| **新增 commit 数** | 10 个 |
| **新增迁移数** | 0 个（无 Flyway 变更） |

## 第 12 次部署后合入的 PR（5 个）

| PR 号 | 主题 | 分类 |
|---|---|---|
| !1297 | CO-381 直辖市/港澳台选二级后值只剩一级（归一规则未覆盖） | 前端修复 |
| !1298 | 修复 5 项安全漏洞（SSRF/路径遍历/敏感日志/认证语法） | 安全修复 |
| !1299 | CO-381 投标文件阶段感知只读守卫 | 功能 |
| !1300 | CO-361 登出不再清 OSS 权限缓存，修复登出后看板空 | 认证修复 |
| !1301 | CO-302 CRM 反查接口 host 修正为 CAC 服务 | CRM 集成 |

## 关于第 12 次部署的说明

第 12 次部署于 2026-06-28 21:04 执行（部署 `dfe1d8286` !1296），但**未生成部署报告**。本次部署在服务器上发现该部署的 release 目录 `/opt/xiyu-bid/releases/dfe1d828-api8080/` 和 DB 备份 `winbid-dfe1d828-api8080-20260628210348.sql.gz`，确认其已成功执行。

第 12 次部署包含的 PR（!1292~!1296）：
- !1292 chore(locks): prune 2 orphan locks
- !1293 fix(frontend): el-radio-button label 废弃改用 value + 第11次部署报告
- !1294 fix(project): CO-373 修复联调五类现场问题
- !1295 feat: 完善全链路日志系统
- !1296 docs: 添加全链路日志排查 SOP

## Flyway 预检与处置

### 预检 3 步法结果

| 步骤 | 检查项 | 结果 |
|---|---|---|
| Step 1 | `flyway-repair-runner.sh validate` | ✅ 170 个迁移（含 1 个 baseline）全部 checksum match |
| Step 2 | JAR 内迁移版本与 DB 一致性 | ✅ JAR 169 个 V 版本 = DB 169 个 V 版本，无缺失/漂移 |
| Step 3 | remote-deploy.sh 激活前自动 validate | ✅ 通过（170 migrations validated, execution time 00:00.081s） |

### 关键确认
- ✅ 本次无新迁移文件（git diff 确认 `db/migration-mysql/` 无变化）
- ✅ JAR 内无重复版本号（package-release.sh 内置校验通过）
- ✅ 第 3 次部署经验生效：即使无新迁移也无条件预检

## 部署步骤

### 1. 同步基线
- 早操同步：`bash scripts/sync-env.sh .`
- 当前分支：`agent/trae-init`（锚点分支，ff-only 同步到 origin/main）
- 同步结果：HEAD = `c577dec2c` = origin/main（0/0 完全同步）
- 工作区干净，无未提交变更

### 2. 本地打包
- **打包命令**：`RELEASE_ID="c577dec2c-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
- **前端构建**：
  - 生产同源构建模式（`VITE_API_BASE_URL=` 显式设空）
  - `src/api/config.js` 在 `import.meta.env.PROD=true` 时强制 `baseURL=""`（同源）
  - 产物：`index-DoxoN71K.js`
  - `check:frontend-api-base` 验证通过
- **后端构建**：
  - `mvn clean -DskipTests package`（强制 clean，避免脏 target）
  - 构建耗时：23.746s
  - jar 大小：155533150 bytes (约 155MB)
  - jar 内迁移文件：169 个 V*.sql + 1 个 B73 baseline
  - 重复版本校验：✅ 无重复

### 3. 代码版本验证
| 验证项 | 结果 | 说明 |
|---|---|---|
| jar 内 CrmProperties.class | ✅ 时间戳 21:54 | !1301 CO-302 修复已包含 |
| jar 内 AuthService.class | ✅ 编译时间 21:54 | !1300 CO-361 修复已包含 |
| jar 内 ProjectDocumentDownloadService.class | ✅ 存在 | !1299 CO-381 功能已包含 |
| jar 内 application.yml | ✅ 含 `cac-base-url` 配置 | !1301 CAC host 配置已包含 |
| jar 内迁移文件数 | ✅ 169 个 V + 1 个 B73 | 与生产 DB 一致 |
| 前端 index.html | ✅ index-DoxoN71K.js | 最新前端构建 |

> **注意**：`git.properties` 中 `git.commit.id=cd0f3a0`（旧 commit），这是 worktree `.git` 指向问题导致的已知现象（第 11 次部署已记录）。class 文件时间戳（21:54/21:55）和 application.yml 内容（含 `cac-base-url`）证明实际代码基于最新源码编译。

### 4. 上传与激活
- **上传**：archive (142MB) + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-c577dec2c-api8080-20260628215729.sql.gz`
- **remote-deploy.sh 执行流程**：
  1. ✅ 解压 archive 到 `/opt/xiyu-bid/releases/c577dec2c-api8080/`
  2. ✅ 激活前端（atomic swap 到 `/srv/www/xiyu-bid`）
  3. ✅ Flyway validate 预检通过（restart 前置，旧 jar 仍在运行）
  4. ✅ 更新后端 jar：`/opt/xiyu-bid/shared/backend/app.jar`
  5. ✅ 写入部署记录：`/opt/xiyu-bid/deployed-release.json`
  6. ✅ 重启后端服务

### 5. 后端重启与健康检查
- **重启时间**：2026-06-28 21:59:06 CST
- **PID**：18584
- **健康检查**：通过（等待约 2 分钟，Kafka SDK 初始化延迟符合预期）
- **前端一致性校验**：✅ `src="/assets/index-DoxoN71K.js"` 与 release 一致

## 验证结果

### 后端健康检查
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP", "details": { "database": "MySQL", "validationQuery": "isValid()" } },
    "diskSpace": { "status": "UP", "details": { "total": "105553760256", "free": "47675822080" } },
    "jwt": { "status": "UP", "details": { "algorithm": "HMAC-SHA256", "strength": "STRONG" } },
    "livenessState": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "redis": { "status": "UP", "details": { "version": "6.2.19" } }
  }
}
```

### API Smoke 测试
| 接口 | 结果 | 说明 |
|---|---|---|
| `/actuator/health` | ✅ 200 UP | 所有组件 UP |
| `/actuator/health/readiness` | ✅ 200 UP | Readiness 正常 |
| `POST /api/auth/login` | ✅ 400 | 空密码触发验证错误（接口路由正常） |
| `GET /api/projects` | ✅ 403 | 需要认证（接口正常） |
| `/api/integration/crm/health` | ✅ 401 | 需要认证（接口正常） |

### 前端验证
| 端点 | 结果 |
|---|---|
| `/` (首页) | ✅ 200 |
| `/login` | ✅ 200 |
| 前端入口 JS | ✅ `index-DoxoN71K.js`（与 release 一致） |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步方向 | Gitee main → GitHub main（单向镜像） |
| 同步前差异 | 10 个 commit（dfe1d8286..c577dec2c） |
| 同步后状态 | ✅ 完全一致（两边 HEAD = `c577dec2c`） |
| 同步命令 | `bash scripts/sync-to-github.sh` |

## 回滚信息

| 项目 | 值 |
|---|---|
| **回滚版本** | `dfe1d828-api8080`（第 12 次部署版本） |
| **回滚 jar 路径** | `/opt/xiyu-bid/releases/dfe1d828-api8080/backend/app.jar` |
| **回滚前端路径** | `/opt/xiyu-bid/releases/dfe1d828-api8080/frontend/` |
| **回滚命令** | 恢复旧 jar + 前端 + `sudo systemctl restart xiyu-bid-backend` |
| **DB 备份** | `/opt/xiyu-bid/db-backups/winbid-c577dec2c-api8080-20260628215729.sql.gz` |
| **Flyway 历史备份** | `/opt/xiyu-bid/backups/flyway-history/` |

## 经验沉淀应用情况

本次部署应用了 xiyu-server-deploy skill 中沉淀的 7 条关键经验：

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ 完整执行（validate + source-sync + remote-deploy 内置） |
| 2 | Readiness 延迟恢复 | ✅ 容忍 2 分钟 Kafka SDK 初始化，未急于回滚 |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，baseURL="" |
| 4 | Smoke 测试限制 | ✅ admin 密码未知，用 400/403/401 验证接口路由 |
| 5 | GitHub 镜像同步 | ✅ 部署后同步，两边一致 |
| 6 | 临时调试配置清理 | ⚠️ 发现 `SHOW_DETAILS=always`，用户决定保留现状 |
| 7 | 幂等迁移设计 | N/A（本次无新迁移） |

## 风险提示

1. **MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always**：`/actuator/health` 暴露 DB/Redis/JWT 组件详情。用户决定保留现状（运维监控需要）。如后续需收紧安全，可改为 `never` 并重启后端。

2. **git.properties commit id 不准确**：worktree `.git` 指向问题导致 `git.commit.id=cd0f3a0`（旧 commit）。不影响实际代码内容（class 文件基于最新源码编译），但影响版本追溯。建议后续排查 git-commit-id-maven-plugin 在 worktree 环境的配置。

3. **第 12 次部署报告缺失**：第 12 次部署（`dfe1d8286`）未生成报告，本次报告中补充记录了其部署的 PR 范围（!1292~!1296）。建议后续部署即使代码变更小也要生成报告，保持部署记录完整性。

## 部署确认

- [x] 早操同步 + 工作区干净
- [x] Flyway 预检 3 步法通过
- [x] 本地打包验证（前端同源 + 后端 clean build + 无重复迁移）
- [x] 代码版本验证（class 时间戳 + application.yml 内容）
- [x] DB 备份完成
- [x] remote-deploy.sh 执行成功（含 Flyway validate 预检）
- [x] 后端重启 + 健康检查 UP
- [x] API smoke 通过（health + readiness + 3 个接口路由）
- [x] 前端验证通过（页面 200 + JS 一致性）
- [x] GitHub 镜像同步完成
- [x] 部署报告生成
