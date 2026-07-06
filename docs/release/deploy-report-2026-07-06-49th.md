# 第 49 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-06 09:15 CST |
| Release ID | `ef831e2db-api8080` |
| 上一版本 | `42c41c736-api8080`（2026-07-05 22:06 CST 部署，第 48 次） |
| 部署类型 | 增量部署（33 个 commit，无新 DB 迁移） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 87 次） |
| Readiness | ✅ UP（87 次尝试说明有 Kafka SDK 延迟，属已知行为） |
| 部署耗时 | 约 2 分钟（09:13 打包完成 → 09:15 服务重启 → 健康检查通过） |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init`（锚点分支，部署维护行为） |
| HEAD commit | `ef831e2db` |
| 工作区状态 | 干净 |
| origin/main 同步 | ✅ HEAD = origin/main |
| GitHub 镜像 | ✅ 部署前落后 4 个 commit，已同步；部署后 0 落后 |

## 增量 PR 列表（33 个 commit，`42c41c736..ef831e2db`）

| Commit | PR | 描述 |
|---|---|---|
| `ef831e2db` | !1743 | fix(governance): 补齐 JSON 序列化门禁脚本头 + 同步品牌授权附件枚举定义 |
| `b388f3d47` | !1742 | 拆分 MarginQuerySupport 修复 ResponsibilityArchitectureTest 存量失败 |
| `3fedcab91` | !1740 | CO-469 P1 第九轮：JSON 序列化门禁修复 + 统一抽象重构 |
| `09cef72c1` | !1741 | feat(platform): CO-511 批量导入模板账号保管员字段从 userId 改为工号 |
| `74fb1a9d8` | !1739 | fix(项目档案): 修复筛选不生效 + 结项时间冗余 + 架构治理 + 常量统一 |
| `6d07818a4` | !1738 | fix(项目转移): 修复角色校验来源不一致导致的"当前角色：null"报错 |
| `41d786ef7` | !1737 | fix(audit): CO-469 P1 全仓审计 — JSON 字段写入路径统一治理 |
| `2c66d46f7` | !1735 | fix(warehouse): 筛选标签显示不全 + 导入模板省份列加下拉框 |
| `e8371518e` | !1736 | fix(personnel): CO-469 第八轮，彻底修复批量导入卡 5% 根因 |
| `d082c118d` | !1734 | feat(resource): 账户管理表格补充序号列 |
| `17e054d64` | !1733 | fix(platform-account): 修复导出接口 403 全员被拒（principal 类型不匹配） |
| `be6d4d330` | !1732 | feat: 人员库与业绩管理列表增加分页 |
| `81ac8ede5` | !1730 | co-508-margin-status-rule: Automation skill-progression-map update |
| `f66aa7029` | !1731 | docs(release): 第 48 次部署报告 |

## 改动范围

**核心业务变更**（6 个功能模块）：

### 1. CO-469 JSON 序列化治理（P1 第八/九轮）
- 全仓审计统一 JSON 字段写入路径，避免 `List/Map.toString()` 直接写入 MySQL JSON 字段（!1737、!1740）
- 新增 `scripts/check-json-field-serialization.sh` 门禁脚本并接入 pre-push（!1740）
- 补齐脚本 doc-governance 文件头（!1743）
- 沉淀 `docs/lessons/lessons-learned.md` §42 复盘（f66aa7029 之前的 commit）

### 2. 平台账户模块（!1741、!1733）
- 批量导入模板账号保管员字段从 `userId` 改为工号（CO-511）
- 修复导出接口 403 全员被拒（principal 类型不匹配）

### 3. 仓库信息模块（!1735）
- 修复筛选标签显示不全
- 导入模板省份列加下拉框

### 4. 人员库模块（!1736）
- CO-469 第八轮：彻底修复批量导入卡 5% 根因

### 5. 项目管理模块（!1738、!1739）
- 修复项目转移角色校验来源不一致导致"当前角色：null"报错
- 修复项目档案筛选不生效、结项时间冗余、常量统一

### 6. 资源与账户 UI（!1732、!1734）
- 人员库与业绩管理列表增加分页
- 账户管理表格补充序号列

## Flyway 预检结果

| 步骤 | 命令/检查 | 结果 |
|---|---|---|
| Step 1 | 服务器 `flyway-repair-runner.sh validate` | ✅ VALIDATE OK - all checksums match |
| Step 2 | DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1138，源码无新增迁移，版本一致 |
| Step 3 | `remote-deploy.sh` 内置 validate | ✅ 通过，无 pending 迁移 |

**迁移应用状态**：

| Version | Description | 状态 |
|---|---|---|
| V1138 | expand brand auth attachment enum | ✅ 已应用（2026-07-05 22:06:14） |
| V1137 | seed platform account export whitelist | ✅ 已应用（2026-07-05 22:06:14） |
| V1136 | warehouse attachment type to varchar | ✅ 已应用 |

本次部署 **无新增 DB 迁移**。

## 部署步骤

```bash
# 1. 早操同步 + GitHub 镜像同步
source scripts/dev-env.sh
bash scripts/sync-env.sh .
bash scripts/check-git-wrapper.sh
bash scripts/sync-to-github.sh

# 2. 服务器现状
ssh jetty@172.16.38.78 'cat /opt/xiyu-bid/deployed-release.json'
ssh jetty@172.16.38.78 'bash /opt/xiyu-bid/bin/flyway-repair-runner.sh validate'

# 3. 本地打包
RELEASE_ID="ef831e2db-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh

# 4. 上传 + 部署
scp .release/xiyu-bid-release-ef831e2db-api8080.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-ef831e2db-api8080.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:8080/actuator/health \
  RELEASE_ID=ef831e2db-api8080 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="source /etc/xiyu-bid/backend.env && mysqldump -h\"\${DB_HOST:-127.0.0.1}\" -P\"\${DB_PORT:-3306}\" -u\"\${DB_USER:-root}\" -p\"\${DB_PASSWORD}\" \"\${DB_NAME:-xiyu_bid_main}\" | gzip > /opt/xiyu-bid/db-backups/winbid-ef831e2db-\$(date +%Y%m%d%H%M%S).sql.gz && echo DB backup done" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

## 验证结果

### 后端健康

| 检查项 | URL | 结果 |
|---|---|---|
| Health | `http://172.16.38.78:8080/actuator/health` | ✅ 200 UP |
| Readiness | `http://172.16.38.78:8080/actuator/health/readiness` | ✅ 200 UP |

Health 组件全部 UP：`aiProvider`、`db`、`diskSpace`、`jwt`、`livenessState`、`ping`、`readinessState`、`redis`、`sidecar`。

### API Smoke

| 接口 | 预期 | 结果 |
|---|---|---|
| `POST /api/auth/login` (empty body) | 400 | ✅ 400 |
| `GET /api/projects` | 403 | ✅ 403 |
| `GET /api/integration/crm/health` | 401 | ✅ 401 |

### 前端验证

| 页面 | 结果 |
|---|---|
| `GET /` | ✅ 200 |
| `GET /login` | ✅ 200 |
| JS bundle 一致性 | ✅ `/assets/index-Drv-lcTX.js` 与 release 一致 |

### GitHub 镜像

| 检查项 | 结果 |
|---|---|
| 部署前落后 | 4 个 commit |
| 同步后 | ✅ 0 落后，Gitee/GitHub main 一致 |

## 回滚计划

| 项目 | 内容 |
|---|---|
| 上一稳定版本 | `42c41c736-api8080` |
| 上一版本产物 | `/opt/xiyu-bid/releases/42c41c736-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-ef831e2db-<timestamp>.sql.gz` |
| 回滚命令 | `RELEASE_ID=42c41c736-api8080` 重新执行 `remote-deploy.sh`，或 `sudo systemctl restart xiyu-bid-backend` 配合旧 jar |
| 数据回滚 | 本次无 schema 变更，原则上无需 DB 回滚；若需回滚可整库恢复 |

## 配置清理检查

| 配置项 | 当前值 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | 保留（运维监控需要，历史连续多次决定保留） |

## 风险提示

- **低风险**：本次部署为纯代码修复，无新增 DB 迁移，无破坏性 schema 变更。
- **已知行为**：后端健康检查耗时 87 次（约 2-3 分钟），由 `OrganizationEventSdkKafkaStarter` Kafka SDK 初始化延迟导致，属已知行为，已自恢复。
- **注意**：本次增量包含大量 CO-469 JSON 序列化治理代码，涉及全仓多处 `List/Map.toString()` 写入 JSON 字段的修复，已接入 pre-push 门禁。

## 部署确认清单

- [x] 早操同步完成
- [x] GitHub 镜像同步完成
- [x] 服务器当前版本确认
- [x] Flyway validate 通过
- [x] 本地打包成功
- [x] 产物校验通过（jar 内迁移无重复、前端 index.html 存在）
- [x] 上传 + 部署成功
- [x] 后端健康检查 UP
- [x] Readiness UP
- [x] API Smoke 通过
- [x] 前端页面验证通过
- [x] 部署报告生成
