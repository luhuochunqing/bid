# 西域数智化投标管理平台 — 第 82 次部署报告（生产环境）

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 部署环境 | 生产环境（prod） |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| Release ID | `527a0c940` |
| 部署 Commit | `527a0c940` |
| 上一 Release | `4dd914ea2-api8080` |
| 部署时间 | 2026-07-12 08:57 CST |
| 打包时间 | 2026-07-12 08:45 CST |
| 部署人员 | trae Agent |
| 回滚状态 | 无需回滚（服务 UP，所有 Smoke 通过） |

## 2. 基线信息

- **本地分支**：`agent/trae/deploy-report-82nd-prod`
- **HEAD = origin/main**：`c6294a4dc !2033 docs(release): 第 82 次测试环境部署报告`
- **GitHub 镜像**：已同步，Gitee main 与 GitHub main 完全一致
- **Git 工作区**：干净（`git status --short` 无输出）

## 3. PR / 改动范围

本次生产部署从上一生产 Release `4dd914ea2-api8080` 推进到 `527a0c940`，包含多个已合并并验证过的 PR：

| PR / Commit | 说明 |
|-------------|------|
| `!2032` / `66c245218` | feat(rate-limit): 限流提示友好化优化（文案/交互/协议三层） |
| `!2030` / `d57d4af4a` | fix(ui): 库房附件与项目文档操作列单行展示 |
| `!2029` / `9f1ffcbec` | fix(resource): CA 列表操作列加宽，三按钮单行展示 |
| `!2028` / `9a41d5fbe` | docs(release): 补充第 80 次部署报告与客户数据部署脚本 |
| `!2025` / `1644b514e` | fix(warehouse): 列表默认展示已关仓，与筛选「全部」语义对齐 |
| `!2024` / `228da8b00` | fix(tender): 批量导入模板示例地区改为推荐一级+二级格式 |
| `!2023` / `9c798b5f7` | docs(release): 第 81 次测试环境部署报告 |
| `!2022` / `908002217` | fix: 06234 OSS 角色解析回归 — sysRoleList roleName 不再使用 positionToRoleMapper |
| `#2021` / `95dca45c1` | fix(security): 删除人员白名单 + bid-SystemAdmin 独立角色与前端闭环 |
| `!2018` / `dfcfe5545` | fix(crm): 修复 PR !2011 字段分离回归导致去重校验失效（CO-277 纯数字 id 误存为 code） |
| `!2017` / `c4a34fb0d` | docs(release): 第 78 次测试环境部署报告 |
| `!2016` / `e857e37ef` | fix(crm): 修复 CRM 推送"半关联"状态导致去重校验失效 |

**主要改动文件范围**：
- `backend/src/main/java/com/xiyu/bid/config/RateLimitFilter.java`
- `backend/src/main/java/com/xiyu/bid/exception/RateLimitResponseFactory.java`
- `backend/src/main/java/com/xiyu/bid/security/role/RoleProfileService.java`
- `backend/src/main/java/com/xiyu/bid/security/RoleCodeResolver.java`
- `backend/src/main/java/com/xiyu/bid/integration/crm/CrmTenderLinkService.java`
- `backend/src/main/java/com/xiyu/bid/tender/service/TenderImportAppService.java`
- `backend/src/main/java/com/xiyu/bid/warehouse/service/WarehouseQueryService.java`
- `src/api/client.js`
- `src/api/rate-limit-message-resolver.js`
- `src/views/Bidding/list/components/BulkImportDialog.vue`
- `src/components/project/detail/ProjectDetailDocumentsCard.vue`
- `src/views/Resource/CAManagement.vue`
- `src/views/Warehouse/*.vue`

**新增 Flyway 迁移**：V1165 `add bid system admin role`（生产 DB 于 2026-07-12 08:57:40 成功应用）

## 4. OBS 直传修复（本次部署关键修复）

### 4.1 问题

过往生产环境部署中，前端构建未显式传入 `VITE_OBS_ENABLED=true`，导致构建产物中 `isObsEnabled` 被编译为 `false`（`!1`），项目文档、立项招标文件等场景的 OBS 大文件直传链路未启用。

### 4.2 修复方式

本次生产打包命令显式传入 `VITE_OBS_ENABLED=true`，构建模式为同源（`VITE_API_BASE_URL=` 空）：

```bash
RELEASE_ID="527a0c940" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh
```

### 4.3 验证

生产环境 `/srv/www/xiyu-bid/assets/Detail-*.js` 验证结果：

| 检查项 | 结果 |
|--------|------|
| `Detail-hW57jr83.js` 中 `.upload(` 调用数 | 2 ✅ |
| `obs-direct:` 前缀存在 | ✅ |
| 构建产物中无 `127.0.0.1:18089` 硬编码 | ✅ |

结论：生产环境 OBS 浏览器直传已启用，大文件（≤500MB）将优先走 OBS 直传，失败自动回退 multipart。

## 5. 生产环境端口与 Nginx 配置说明

生产服务器 `backend.env` 显式配置：

```bash
SERVER_PORT=18080
```

因此后端真实监听端口为 `18080`，Nginx 配置已正确代理：

```nginx
listen 80;
listen 8080;
location /api/ {
    proxy_pass http://127.0.0.1:18080/api/;
    ...
}
location /actuator/ {
    proxy_pass http://127.0.0.1:18080/actuator/;
    ...
}
```

健康检查通过 `http://127.0.0.1:8080/actuator/health`（Nginx 代理）与 `http://127.0.0.1:18080/actuator/health`（直连后端）均返回 `UP`。

> 注：`deployed-release.json` 中 `packageMetadata.apiBaseUrl` 显示为 `http://127.0.0.1:18089`，经核查未写入前端构建产物（assets 中无 `18089` 字面量），属于元数据残留，不影响运行时同源请求。

## 6. Flyway 预检结果

| 步骤 | 结果 |
|------|------|
| 服务器 `flyway-repair-runner.sh validate` | ✅ 通过 |
| DB 已应用最新版本 | V1165 `add bid system admin role`（2026-07-12 08:57:40） |
| JAR 内迁移版本重复校验 | ✅ 无重复（227 files） |
| 部署中内置 validate | ✅ 通过 |

## 7. 部署步骤

```bash
# 1. 本地打包（关键：VITE_OBS_ENABLED=true + 同源 baseURL）
RELEASE_ID="527a0c940" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh

# 2. 上传产物与部署脚本
scp .release/xiyu-bid-release-527a0c940.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.10.149:/opt/xiyu-bid/incoming/

# 3. 执行远程部署（SYSTEMCTL_SUDO=true）
RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-527a0c940.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=527a0c940 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh
```

部署脚本健康检查通过，服务正常启动。

## 8. 验证结果

| 检查项 | 命令/路径 | 结果 |
|--------|-----------|------|
| 后端 health（Nginx 代理） | `GET http://127.0.0.1:8080/actuator/health` | ✅ 200 UP |
| 后端 health（直连后端） | `GET http://127.0.0.1:18080/actuator/health` | ✅ 200 UP |
| 后端 readiness | `GET /actuator/health/readiness` | ✅ 200 UP |
| DB 组件 | health details | ✅ UP |
| Redis 组件 | health details | ✅ UP |
| Sidecar 组件 | health details | ✅ UP（localhost:8000） |
| AI Provider | health details | ✅ UP（qwen3.7-max） |
| 登录路由（空密码） | `POST /api/auth/login {}` | ✅ 400（预期） |
| 项目列表（未认证） | `GET /api/projects` | ✅ 403（预期） |
| 前端入口 | `GET /` | ✅ 200 |
| 前端登录页 | `GET /login` | ✅ 200 |
| 已部署 release 记录 | `/opt/xiyu-bid/deployed-release.json` | ✅ `527a0c940` |
| OBS 直传启用 | Detail chunk `.upload(` / `obs-direct:` | ✅ 已启用 |

## 9. GitHub 镜像同步

```bash
bash scripts/sync-to-github.sh
```

- Gitee main: `c6294a4dc`（与 origin/main 一致）
- GitHub main: 已同步 ✅

## 10. 回滚信息

| 项目 | 值 |
|------|-----|
| 回滚触发 | 未触发 |
| 上一可用 release | `4dd914ea2-api8080` |
| 上一 release 目录 | `/opt/xiyu-bid/releases/4dd914ea2-api8080` |
| 回滚命令 | `sudo cp /opt/xiyu-bid/releases/4dd914ea2-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| DB 备份 | 部署前已自动备份至 `/opt/xiyu-bid/db-backups/` |

## 11. 问题与经验沉淀

### 11.1 生产环境后端端口为 18080（非 8080）

**现象**：生产服务器 `backend.env` 配置 `SERVER_PORT=18080`，后端真实监听 18080；若直接用 8080 访问后端服务会失败。

**处置**：Nginx 已正确将 `/api/` 与 `/actuator/` 代理到 `127.0.0.1:18080`，外部通过 Nginx 的 80/8080 端口访问正常。

**建议**：后续部署文档与自动化脚本应显式读取 `backend.env` 中的 `SERVER_PORT` 作为健康检查目标，避免默认假设 8080。

### 11.2 生产环境 OBS 构建门禁

**现象**：生产前端构建必须显式传入 `VITE_OBS_ENABLED=true`，否则构建产物会 tree-shake 掉 OBS 直传逻辑。

**处置**：本次打包已显式传入，并通过 `.upload(` 调用数 + `obs-direct:` 前缀双重验证。

**建议**：将 `VITE_OBS_ENABLED=true` 写入生产构建流水线默认环境变量，避免人工遗漏。

## 12. 部署确认清单

- [x] 环境门禁确认（生产环境 172.16.10.149）
- [x] 早操三连 + 基线确认
- [x] 服务器现状检查（deployed-release.json、Nginx 配置、端口监听）
- [x] Flyway 预检 3 步
- [x] 本地打包（VITE_OBS_ENABLED=true，VITE_API_BASE_URL= 空）
- [x] 产物校验（JAR 迁移、前端 OBS 开关）
- [x] 上传 + 部署到生产服务器
- [x] 健康检查 + Smoke 测试（含 Nginx 代理与直连后端双路径）
- [x] 迁移应用验证（V1165）
- [x] GitHub 镜像同步
