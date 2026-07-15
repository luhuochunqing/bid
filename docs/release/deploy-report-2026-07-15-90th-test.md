# 第 90 次测试环境部署报告

> **环境**：测试环境（test）
> **部署时间**：2026-07-15 16:57 CST
> **Release ID**：`bbe4710fc-api8080`
> **操作人**：AI Agent（trae worktree）

## 一、部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 目标主机 | `winbid-01` (`172.16.38.78`) |
| Release ID | `bbe4710fc-api8080` |
| 基线 commit | `bbe4710fc` |
| 上一版本 | `fa11e2105-api8080`（2026-07-15 12:14 激活） |
| 增量 commit 数 | 4 |
| 新增迁移文件 | 无 |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |

## 二、基线信息

- **当前分支**：detached HEAD at `origin/main`（部署打包用，非开发行为）
- **HEAD = origin/main**：`bbe4710fc`（!2084 修复 Excel 导出状态英文 + 到期天数超大数字）
- **工作区状态**：干净
- **GitHub 镜像**：部署前落后 4 个 commit，部署后同步中

## 三、增量 PR 与改动范围

### 3.1 增量 commit（fa11e2105..bbe4710fc）

| commit | PR | 类型 | 说明 |
|---|---|---|---|
| `bbe4710fc` | !2084 | fix | 修复 Excel 导出状态英文 + 到期天数超大数字 |
| `a9536f88a` | - | fix | 修复 Excel 导出状态英文 + 到期天数超大一列 |
| `93f579ec9` | !2083 | docs | 第 89 次测试环境部署报告 |
| `5dc623673` | - | docs | 第 89 次测试环境部署报告 |

### 3.2 改动范围统计

| 模块 | 文件数 | 说明 |
|---|---|---|
| 后端 performance 模块 | 10 | DTO/Mapper/EnumLabels/ExcelExporter/ContractStatusPolicy/ContractStatus/CustomerLevel/CustomerType/DockingMethod/ProjectType |
| 后端测试 | 5 | ListPerformanceAppServiceTest/PerformanceEnumLabelsTest/PerformanceExcelExporterTest/PerformanceExcelGroupExportTest/PerformanceZipExporterTest/ContractStatusPolicyTest |
| 前端 | 1 | Performance.vue |
| 迁移文件 | 0 | 无 |
| 文档 | 1 | 第 89 次部署报告 |

### 3.3 主要功能变更

- **PR !2084 业绩管理 Excel 导出修复**：
  - 修复 Excel 导出状态列显示英文（中文枚举标签缺失）
  - 修复到期天数超大数字显示问题
  - 新增 `PerformanceEnumLabels` 统一管理枚举中文标签
  - `ContractStatusPolicy` 等领域值对象补全

## 四、Flyway 预检结果

### Step 1: 服务器 validate

```
VALIDATE OK - all checksums match
Successfully validated 228 migrations (execution time 00:00.089s)
```

### Step 2: DB 已应用版本（最近 5 条 success=1）

| version | description | installed_on |
|---|---|---|
| 1165 | add bid system admin role | 2026-07-11 16:43:52 |
| 1164 | lock oss user local passwords | 2026-07-10 21:13:25 |
| 1163 | add operator username to webhook delivery tasks | 2026-07-10 18:23:46 |
| 1162 | add margin permission to bid specialist | 2026-07-10 12:22:43 |
| 1161 | ca related platforms text | 2026-07-09 18:15:12 |

### Step 3: remote-deploy.sh 内置 validate

部署时自动执行，通过（228 migrations validated, all checksums match）。

## 五、部署步骤

### 5.1 本地打包

```bash
RELEASE_ID="bbe4710fc-api8080" \
VITE_API_BASE_URL= \
VITE_OBS_ENABLED=true \
COPYFILE_DISABLE=1 \
bash scripts/release/package-release.sh
```

- 构建结果：BUILD SUCCESS（26.66s）
- jar：`bid-platform-1.0.3.jar`
- 前端入口：`assets/index-BIDINASZ.js`
- archive 大小：153M

### 5.2 产物校验

- ✅ jar 内 Flyway 迁移版本无重复（227 个 V*.sql + B73 基线 = 228 migrations）
- ✅ OBS 直传已启用（Detail chunk .upload( 调用数=2）
- ✅ `release-metadata.json`：`obsEnabled=true`，`apiBaseUrl=""`，`sentryEnabled=false`
- ✅ 前端 index.html 入口：`assets/index-BIDINASZ.js`

### 5.3 上传 + 部署

```bash
scp .release/xiyu-bid-release-bbe4710fc-api8080.tar.gz \
    scripts/release/remote-deploy.sh \
    jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 '... SYSTEMCTL_SUDO=true \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- DB 备份：`/opt/xiyu-bid/db-backups/winbid-bbe4710fc-*.sql.gz`
- Flyway validate：通过（228 migrations, all checksums match）
- jar 覆盖：`/opt/xiyu-bid/shared/backend/app.jar`
- 服务重启：`xiyu-bid-backend.service` active running（PID 20326）
- 健康检查：consecutive 3/3, total attempts: 79

### 5.4 前端资源保留

```bash
sudo cp -rn /opt/xiyu-bid/releases/fa11e2105-api8080/frontend/assets/* \
            /srv/www/xiyu-bid/assets/
```

- ✅ 已保留上一版本 assets（防跨版本 404）

## 六、验证结果

### 6.1 健康检查

| 端点 | 状态 | 说明 |
|---|---|---|
| `/actuator/health` | UP | 所有组件正常（db/redis/jwt/sidecar/aiProvider） |
| `/actuator/health/readiness` | HTTP 200 | readinessState 正常（无 Kafka SDK 延迟） |

### 6.2 Smoke 测试

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 | 200 | ✅ |
| `/actuator/health/readiness` | 200 | 200 | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| `GET /api/integration/crm/health`（无认证） | 401 | 401 | ✅ |
| `GET /` | 200 | 200 | ✅ |
| `GET /login` | 200 | 200 | ✅ |

前端入口：`assets/index-BIDINASZ.js`（与本地打包一致）✅

## 七、GitHub 镜像同步

- 部署前：GitHub main 落后 Gitee main 4 个 commit
- 部署后：执行 `git push github main`（pre-push gate 通过，push 进行中）
- 备注：首次 push 因残留 vitest lockdir 被 pre-push gate 阻塞，清理 `/tmp/xiyu-vitest.lockdir` 后重试

## 八、回滚信息

- **回滚 jar**：`/opt/xiyu-bid/releases/fa11e2105-api8080/backend/app.jar`
- **回滚命令**：
  ```bash
  ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/fa11e2105-api8080/backend/app.jar \
    /opt/xiyu-bid/shared/backend/app.jar && \
    sudo systemctl restart xiyu-bid-backend'
  ```
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-bbe4710fc-*.sql.gz`
- **回滚状态**：未需要

## 九、经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，validate 通过（228 migrations） |
| Kafka SDK readiness 延迟（第 8/9/10/13/15 次出现） | ✅ 本次未出现，readiness 立即 UP |
| 生产前端同源构建（baseURL=""） | ✅ `VITE_API_BASE_URL=` 显式设空 |
| OBS 直传双保险 | ✅ `VITE_OBS_ENABLED=true` 显式传入 + 产物校验 obsEnabled=true |
| macOS `._*` 残留防护 | ✅ `COPYFILE_DISABLE=1` |
| 前端 hash 资源跨版本 404 | ✅ 从上一版本 release 目录 cp -rn 旧 assets |
| systemctl sudo 权限 | ✅ `SYSTEMCTL_SUDO=true` |
| Mac HTTP_PROXY 502 绕过 | ✅ curl 加 `--noproxy '*'` |
| vitest lockdir 残留清理 | ✅ 新增经验：pre-push gate 因 `/tmp/xiyu-vitest.lockdir` 残留阻塞，需手动清理 |

## 十、风险提示

1. **无新迁移**：本次无 Flyway 迁移变更，DB 风险极低。
2. **纯代码修复**：本次改动仅涉及 performance 模块 Excel 导出逻辑，影响范围可控。
3. **GitHub 镜像同步**：push 进行中，若最终失败需手动同步。

## 十一、部署确认清单

- [x] 环境门禁通过（用户确认测试环境 172.16.38.78）
- [x] 早操三连执行（sync-env.sh + check-git-wrapper.sh）
- [x] 基线确认（HEAD = origin/main = bbe4710fc）
- [x] 服务器现状检查（上一版本 fa11e2105，health UP）
- [x] Flyway 预检 3 步全部通过（228 migrations, all checksums match）
- [x] 本地打包成功（BUILD SUCCESS 26.66s，OBS 直传启用）
- [x] 产物校验通过（jar 内无重复迁移，前端入口一致，obsEnabled=true）
- [x] 上传 + 部署成功（jar 覆盖 + 服务重启，health 3/3）
- [x] 前端资源保留（从 fa11e2105-api8080 cp -rn 旧 assets）
- [x] 健康检查 UP（health UP, readiness 200，无 Kafka 延迟）
- [x] Smoke 测试全部通过（5 个接口 + 2 个前端页面）
- [x] 配置清理检查（`SHOW_DETAILS=always` 为用户历史决定保留）
- [x] 部署报告生成
- [ ] GitHub 镜像同步（push 进行中）
