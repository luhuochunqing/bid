# 第 80 次测试环境部署报告

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 服务器 | winbid-01 (172.16.38.78) |
| 部署日期 | 2026-07-11 |
| 部署次数 | 第 80 次（测试环境） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `95dca45c1-api8080` |
| Commit | `95dca45c1` |
| Commit Message | fix(security): 删除人员白名单 + bid-SystemAdmin 独立角色与前端闭环 (#2021) |
| 上次部署 | `dfcfe5545-api8080` (2026-07-11T05:24:45Z) |
| 部署结果 | ✅ 成功 |

## 基线信息

- 分支：`agent/trae/serve-latest`（基于 `origin/main`）
- HEAD = `origin/main` = `95dca45c1`
- git status：干净
- GitHub 镜像：部署前落后 5 个 commit，部署后同步完成

## 增量改动

### 增量 Commits（5 个）

| Commit | 类型 | 说明 |
|---|---|---|
| `95dca45c1` | fix(security) | 删除人员白名单 + bid-SystemAdmin 独立角色与前端闭环 (#2021) |
| `46b165f23` | docs | 第 6 次生产环境部署报告 |
| `8d5c84758` | docs | 第 79 次测试环境部署报告 |
| `452dea0c6` | docs | 第 79 次测试环境部署报告 |
| `a660b4a67` | docs | 第 6 次生产环境部署报告 |

### 新增迁移

| 版本 | 文件 | 说明 |
|---|---|---|
| V1165 | `V1165__add_bid_system_admin_role.sql` | 新增 bid-SystemAdmin 角色 |
| U1165 | `U1165__add_bid_system_admin_role.sql` | 回滚脚本 |

### 功能性改动文件

**后端（8 个文件）**：
- `OssLoginFlowService.java`
- `OssRoleResolver.java`
- `RoleProfileCatalog.java`
- `OrganizationUserSyncWriter.java`
- `JobRoleLookupResolver.java`
- `ProjectTransferController.java`
- `application.yml`
- `V1165__add_bid_system_admin_role.sql` + rollback

**前端（7 个文件）**：
- `useProjectDetailTransfer.js` + spec
- `useProjectDraftingPermissions.js` + spec
- `roleCodes.js`
- `stores/user.js` + spec
- `utils/permission.js` + test

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: validate | ✅ VALIDATE OK - all checksums match (227 migrations) |
| Step 2: DB 版本对比 | DB 已应用 V1164 → 源码最新 V1165（1 个待应用） |
| Step 3: remote-deploy 内置 | ✅ 部署时自动 validate 通过 |

## 部署步骤

1. ✅ 环境门禁：用户确认部署到测试环境 172.16.38.78
2. ✅ 早操三连：sync-env + check-git-wrapper
3. ✅ 基线确认：HEAD = origin/main = `95dca45c1`
4. ✅ 服务器现状：deployed-release.json + health UP
5. ✅ Flyway 预检 3 步法
6. ✅ 本地打包：`RELEASE_ID=95dca45c1-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`
7. ✅ 产物校验：jar 内 227 个迁移文件无重复，前端入口 `assets/index-DrN9nJ2a.js`
8. ✅ 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true）
9. ✅ DB 备份：mysqldump 自动备份

## 验证结果

### V1165 迁移应用

| version | description | success | installed_on |
|---|---|---|---|
| 1165 | add bid system admin role | 1 | 2026-07-11 16:43:52 |

### 后端健康检查

| 组件 | 状态 |
|---|---|
| status | UP |
| aiProvider | UP (provider=custom, model=qwen3.7-max) |
| db | UP (MySQL) |
| diskSpace | UP |
| jwt | UP (HMAC-SHA256, 64 bytes) |
| livenessState | UP |
| readinessState | UP |
| redis | UP (6.2.19) |
| sidecar | UP (http://localhost:8000) |

### Smoke 测试

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| GET /actuator/health | 200 UP | 200 UP | ✅ |
| GET /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| POST /api/auth/login (空) | 400 | 400 | ✅ |
| GET /api/projects (无认证) | 403 | 403 | ✅ |
| GET /api/integration/crm/health | 401 | 401 | ✅ |

### 前端验证（服务器内部）

| 检查项 | 结果 |
|---|---|
| nginx status | active (running) |
| GET http://127.0.0.1/ | 200 |
| GET http://127.0.0.1/login | 200 |
| index.html assets | `assets/index-DrN9nJ2a.js`（与 release 一致） |

### Kafka SDK readiness 延迟

- 现象：后端启动后 `/actuator/health` 持续返回 503 约 4 分钟
- 恢复：自动恢复（已知行为，第 8/9/10/13/15/80 次均出现）
- API 在 readiness 503 期间正常服务（用户已成功登录并访问 /api/notifications/unread-count）

## GitHub 镜像同步

| 项目 | 结果 |
|---|---|
| 同步前 | GitHub 落后 Gitee 5 个 commit |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步后 | ✅ 两边 main 完全一致（`95dca45c1`） |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 用户历史决定（第 13/14/15 次均保留） |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` | 仅上述一项 | 非临时调试配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 jar | `/opt/xiyu-bid/releases/dfcfe5545-api8080/backend/app.jar` |
| 回滚命令 | `cp /opt/xiyu-bid/releases/dfcfe5545-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-95dca45c1-*.sql.gz` |
| 回滚迁移 | U1165（回滚 V1165 新增的 bid-SystemAdmin 角色） |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 执行 validate + DB 版本对比 |
| #2 Kafka SDK readiness 延迟 | ✅ 已识别并等待恢复（4 分钟） |
| #3 同源构建 baseURL="" | ✅ VITE_API_BASE_URL= 显式设空 |
| #4 Smoke admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| #5 GitHub 镜像同步 | ✅ 部署后同步 |
| #6 临时调试配置清理 | ✅ 检查（仅 SHOW_DETAILS=always 保留） |
| #8 systemctl sudo | ✅ SYSTEMCTL_SUDO=true |
| #16 Mac HTTP_PROXY 502 | ✅ --noproxy '*' + 服务器内部验证 |

## 风险提示

1. **V1165 为新增角色迁移**：新增 `bid-SystemAdmin` 角色，不可逆回滚（需执行 U1165 回滚脚本）
2. **Kafka SDK readiness 延迟**：已知行为，无需干预
3. **Mac HTTP_PROXY**：本地 curl 需加 `--noproxy '*'`

## 部署确认清单

- [x] 环境门禁通过
- [x] 基线确认（HEAD = origin/main）
- [x] Flyway 预检通过
- [x] 打包成功
- [x] 产物校验通过
- [x] 上传 + 部署成功
- [x] V1165 迁移应用
- [x] 后端健康检查 UP
- [x] Smoke 测试全通过
- [x] 前端验证通过
- [x] GitHub 镜像同步
- [x] 配置清理检查
- [x] 部署报告生成
