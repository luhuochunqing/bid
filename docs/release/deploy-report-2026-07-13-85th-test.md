# 西域数智化投标管理平台 — 第 85 次部署报告（测试环境）

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 部署环境 | 测试环境（test） |
| 目标主机 | `winbid-01` / `172.16.38.78` |
| Release ID | `0ac531776-api8080` |
| 部署 Commit | `0ac531776` |
| 上一 Release | `f0366414b-api8080` |
| 部署时间 | 2026-07-13 14:18 CST |
| 打包时间 | 2026-07-13 14:16 CST |
| 部署人员 | trae Agent |
| 回滚状态 | 无需回滚 |

## 2. 基线信息

- **本地分支**：`agent/trae-init`（锚点分支，仅用于打包与部署，不做开发）
- **HEAD = origin/main**：`0ac531776 !2063 fix(bid-upload): 投标文件上传 UX 四项修复`
- **GitHub 镜像**：部署前落后 8 commit，部署后已同步至 `0ac531776`（两边 main 完全一致）
- **Git 工作区**：干净（`git status --short` 无输出）

## 3. PR / 改动范围

本次部署包含 12 个 commit，从 `f0366414b` 到 `0ac531776`。按主题归类：

### 3.1 OBS 直传部署三层防护（!2059，2 commit）

| Commit | 说明 |
|--------|------|
| `56ef8fd59` | chore(release): OBS 直传部署三层防护 — 防止漏传 VITE_OBS_ENABLED=true 回归 |
| `52ada6b78` | !2059 chore(release): OBS 直传部署三层防护 — 防止漏传 VITE_OBS_ENABLED=true 回归 |

**背景**：第 84 次测试 + 第 8 次生产均因 `package-release.sh` 默认 `VITE_OBS_ENABLED=false`，导致 OBS 大文件直传失效。本次三层防护：
1. `package-release.sh` 默认值改为 `true`（不传也启用）
2. 构建时校验 `Detail-*.js` 中 `.upload(` 调用数 ≥2
3. 部署后验证 `obsEnabled=true`

### 3.2 OBS 防护漏洞补丁（!2060，3 commit）

| Commit | 说明 |
|--------|------|
| `7d9ec9de4` | chore(release): OBS 防护漏洞补丁 — 修复 5 个绕过路径 |
| `b36cf19e7` | fix(release): deploy-prod.sh 死代码修复 — ssh 失败时 L3 OBS 校验能执行 |
| `b1126a2b3` | !2060 fix(release): OBS 防护漏洞补丁 — 5 个绕过路径 + deploy-prod.sh 死代码修复 |

**修复内容**：Review 发现 !2059 的三层防护存在 5 个绕过路径（CI/脚本环境变量泄漏、grep 容错、死代码等），全部修补。

### 3.3 package-release.sh grep 容错 + 第 9 次生产部署报告（!2061，3 commit）

| Commit | 说明 |
|--------|------|
| `2f212367d` | fix(release): package-release.sh grep 容错 + 第 9 次生产部署报告 |
| `4308a7e40` | fix(tender-intake): 前端 axios 超时对齐后端 AI 超时 |
| `bda25bf8a` | !2061 fix(release): package-release.sh grep 容错 + 第 9 次生产部署报告 |

### 3.4 前端 axios 超时对齐后端 AI 超时（!2062，1 commit）

| Commit | 说明 |
|--------|------|
| `f269d565c` | !2062 fix(tender-intake): 前端 axios 超时对齐后端 AI 超时 |

### 3.5 投标文件上传 UX 四项修复（!2063，3 commit）

| Commit | 说明 |
|--------|------|
| `7f2e84538` | fix(bid-upload): 投标文件上传 UX 四项修复 — 进度条/成功提示/列表刷新/删除同步 |
| `71721c166` | refactor(bid-upload): Review 修复三问题 — 关注点分离/进度条/DRY |
| `29827760e` | test(bid-upload): 补充 7 个测试覆盖 Review 发现的测试缺口 |
| `0ac531776` | !2063 fix(bid-upload): 投标文件上传 UX 四项修复 — 进度条/成功提示/列表刷新/删除同步 |

**四项修复**：上传进度条、成功提示、列表刷新、删除同步。

## 4. 数据库迁移

- **新增迁移**：无
- **源码最大版本**：V1165（`add bid system admin role`）
- **DB 已应用最大版本**：V1165
- **Flyway validate**：✅ 228 migrations，all checksums match

## 5. 部署步骤

### 5.1 环境门禁
- 用户声明：ENV=test
- AI 展示目标环境信息：`winbid-01` / `172.16.38.78`
- AskUserQuestion 确认：✅ 用户选择"确认：测试环境 172.16.38.78"

### 5.2 早操三连
- `bash scripts/check-git-wrapper.sh`：✅
- `git status --short`：干净
- HEAD = origin/main = `0ac5317765bb360ce5586b01d256ec683a1ee033`

### 5.3 服务器现状
- 上一版本 `f0366414b-api8080` 健康检查：UP（所有组件正常）
- 增量 commit：12 个（!2059-!2063 系列）
- 迁移文件变更：无

### 5.4 Flyway 预检 3 步
- **Step 1 validate**：✅ 228 migrations，all checksums match
- **Step 2 DB 版本对比**：源码 V1165 = DB V1165，无 pending
- **Step 3 remote-deploy 内置 validate**：✅ 通过

### 5.5 本地打包
```bash
RELEASE_ID="0ac531776-api8080" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```
- jar 构建：BUILD SUCCESS（28.294s）
- 产物校验：
  - `obsEnabled=true` ✅
  - `apiBaseUrl=""`（同源构建）✅
  - Detail chunk `.upload(` 调用数 = 2 ✅
  - jar 内 Flyway 迁移版本无重复 ✅
  - 前端入口：`assets/index-DBDNfPgO.js` ✅

### 5.6 上传 + 部署
- scp 上传 release tarball + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
- 执行 `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）：
  - Flyway validate 通过
  - 停止后端服务 ✅
  - 更新 backend artifact ✅
  - 写入 deployed-release.json ✅
  - 启动后端服务 ✅
  - 健康检查通过（consecutive 3/3, total attempts: 80）✅
  - 前端一致性验证：`src="/assets/index-DBDNfPgO.js"` ✅

### 5.7 前端资源保留（防跨版本 404）
- 从上一版本 `f0366414b-api8080/frontend/assets/` 复制 177 个文件到 `/srv/www/xiyu-bid/assets/`
- 使用 `cp -rn`：不覆盖新版本文件，仅补回旧 hash 文件
- 保留 24h，让旧标签页自然刷新

## 6. 验证结果

### 6.1 后端健康检查（经 Nginx 8080 代理到 18080）
| 检查项 | 结果 |
|--------|------|
| `GET /actuator/health` | HTTP 200，status=UP |
| `GET /actuator/health/readiness` | HTTP 200（无 Kafka 延迟） |
| `aiProvider` | UP（qwen3.7-max） |
| `db` | UP（MySQL） |
| `redis` | UP（6.2.19） |
| `sidecar` | UP（reachable） |
| `livenessState` | UP |
| `readinessState` | UP |

### 6.2 API Smoke（经 Nginx 8080）
| 接口 | 期望 | 实际 | 说明 |
|------|------|------|------|
| `POST /api/auth/login` | 400 | 400 | 空密码验证错误，接口路由正常 |
| `GET /api/projects` | 403 | 403 | 需认证，接口路由正常 |
| `GET /api/integration/crm/health` | 401 | 401 | 需认证，接口路由正常 |

### 6.3 前端页面（经 Nginx 8080）
| 路径 | HTTP | 说明 |
|------|------|------|
| `GET /` | 200 | 首页正常 |
| `GET /login` | 200 | 登录页正常 |
| 前端入口 JS | `assets/index-DBDNfPgO.js` | 与 release 一致 |

## 7. GitHub 镜像同步

- **部署前**：GitHub main 落后 Gitee main 8 个 commit
- **同步命令**：`bash scripts/sync-to-github.sh`
- **部署后**：两边 main 完全一致（`0ac5317765bb360ce5586b01d256ec683a1ee033`）✅

## 8. 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：**保留**（第 13-15 次部署用户已决定保留，便于排障）
- 无其他临时 DEBUG/TRACE 配置

## 9. 回滚信息

- **回滚锚点**：`/opt/xiyu-bid/releases/f0366414b-api8080/`
- **DB 备份**：本次无 schema 变更，未执行 mysqldump（remote-deploy.sh 内置备份命令在无迁移时为 no-op）
- **回滚命令**：
  ```bash
  ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
    sudo cp /opt/xiyu-bid/releases/f0366414b-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
    sudo cp -rf /opt/xiyu-bid/releases/f0366414b-api8080/frontend/* /srv/www/xiyu-bid/ && \
    sudo systemctl start xiyu-bid-backend'
  ```

## 10. 经验沉淀应用

| 经验项 | 应用情况 |
|--------|---------|
| OBS 直传漏传 VITE_OBS_ENABLED=true（第 84/8 次事故） | ✅ 本次三层防护 + 双保险显式传入 |
| macOS `._*` 残留文件（第 10 次） | ✅ 打包时 `COPYFILE_DISABLE=1` |
| 同源构建 baseURL=""（第 8 次生产） | ✅ `VITE_API_BASE_URL=` 显式空 |
| 前端 hash 资源跨版本 404（第 18 条经验） | ✅ 从上一版本 `cp -rn` 保留 assets |
| systemctl sudo 权限（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| Flyway 预检 3 步法 | ✅ 全部执行 |
| Smoke 测试 admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| Mac HTTP_PROXY 502（第 19/23 次） | ✅ curl 加 `--noproxy '*'` |

## 11. 风险提示

- 本次无数据库迁移，回滚安全
- 前端资源保留 24h，期间 `/srv/www/xiyu-bid/assets/` 会包含新旧两版本 hash 文件，属正常现象
- `SHOW_DETAILS=always` 保留中，health 端点会暴露组件详情，仅限测试环境

## 12. 部署确认清单

- [x] 环境门禁通过（用户确认测试环境）
- [x] 早操三连 + 基线确认
- [x] 服务器现状检查
- [x] Flyway 预检 3 步
- [x] 本地打包（OBS 直传 + 同源构建）
- [x] 产物校验（obsEnabled=true + .upload(=2 + 无重复迁移）
- [x] 上传 + 部署（SYSTEMCTL_SUDO=true）
- [x] 前端资源保留（cp -rn 上一版本 assets）
- [x] 健康检查 UP（health + readiness）
- [x] API Smoke（400/403/401）
- [x] 前端页面（200 + 入口 JS hash 一致）
- [x] GitHub 镜像同步（两边 main 一致）
- [x] 配置清理检查（SHOW_DETAILS=always 保留）
- [x] 部署报告生成

---

**部署结论**：第 85 次测试环境部署成功，服务健康，所有验证通过，无需回滚。
