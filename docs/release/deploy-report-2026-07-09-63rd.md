# 第 63 次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 63 次 |
| 部署环境 | **测试环境**（test） |
| 部署日期 | 2026-07-09 |
| Release ID | `1f276d050-api8080-obs` |
| 部署时间 | 2026-07-09 13:26:23 CST（2026-07-09T05:26:23Z） |
| 前置 Release | `d4a8e5ad7-api8080-obs`（2026-07-09 11:38:41 CST 激活） |
| 部署结果 | ✅ 成功（健康检查 3/3 通过，无 Kafka SDK 延迟） |
| 新增 Flyway 迁移 | 无 |
| 回滚状态 | 未需回滚 |
| 部署性质 | 正常增量部署（保持 OBS 直传开关启用） |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步至 origin/main） |
| 部署 commit | `1f276d050`（!1928 fix CO-537 Tender.department 写入时持久化根因修复） |
| 前置 commit | `d4a8e5ad7`（!1921 紧急回退 audit-logs 鉴权 hasAuthority→hasAnyRole） |
| 增量 commit 数 | 20 |
| GitHub 镜像 | 部署前落后 29 commit → 部署后同步完成，两边 main 一致 |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080（nginx 反代到 18080） |
| 构建命令 | `RELEASE_ID="1f276d050-api8080-obs" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh` |

## PR 列表

本次部署涵盖 20 个增量 commit（按时间倒序）：

| Commit | PR | 描述 | 类型 |
|---|---|---|---|
| `1f276d050` | !1928 | fix(CO-537): Tender.department 写入时持久化根因修复——3 个写入入口改用 enricher 反查 | fix |
| `1d626fbdb` | !1927 | docs(release): 补充第 61/62 次部署报告与生产一键部署脚本 | docs |
| `45a5116bc` | - | fix(CO-537): Tender.department 写入时持久化根因修复——3 个写入入口改用 enricher 反查 | fix |
| `cbe353767` | - | docs(release): 补充第 61/62 次部署报告与生产一键部署脚本 | docs |
| `6c9be9c88` | !1924 | fix(project-doc): 项目文档 6 入口接入 OBS 直传，修复 APISIX 网关 413 | fix |
| `f893ed622` | - | revert(obs-upload): 标讯文件上传回退 OBS 直传，恢复 multipart + AI 解析 | revert |
| `fb5bf6d0b` | !1926 | feat(drafting): 案例切片推荐迁移到标书编制页面 | feat |
| `14792addd` | - | refactor(obs-upload): 清理死代码守卫，集中化常量，动态化 UI 文案 | refactor |
| `12950962c` | - | feat(drafting): 案例切片推荐迁移到标书编制页面 | feat |
| `350466b4b` | !1925 | fix(CO-558): 项目文档下载权限真正修复——新增 isAssignedBidSpecialist 排除 bid-projectLeader | fix(perm) |
| `b6eb64198` | - | fix(obs-upload): OBS 直传成功后跳过 store+parse，避免 413 | fix |
| `ef87368cf` | - | fix(obs-upload): 去除 useObsUpload 内部 ElMessage + OBS 启用时放宽 50MB 限制 | fix |
| `d89947876` | - | fix(CO-558): 项目文档下载权限真正修复——新增 isAssignedBidSpecialist 排除 bid-projectLeader | fix(perm) |
| `38f2f33d9` | - | fix(project-doc): 项目文档 6 入口接入 OBS 直传，修复 APISIX 网关 413 | fix |
| `c57a1771d` | !1922 | fix(tender): 标讯详情页 department 兜底——单条路径与批量路径对齐 | fix |
| `69c39e2d5` | !1923 | refactor(notification): 设计评审 P0/P1 修复 — 统一角色枚举、可见性过滤与文案策略 | refactor |
| `0850d66a6` | - | refactor(notification): 设计评审 P0/P1 修复 — 统一角色枚举、可见性过滤与文案策略 | refactor |
| `8bdfb5fd6` | - | fix(tender): 标讯详情页 department 兜底——单条路径与批量路径对齐 | fix |
| `c0c8f345a` | - | docs(frontend): 消息中心图标/标签映射与模块 README 更新 | docs |
| `39d4552af` | - | refactor(notification): 消息中心触发点统一使用新策略并补齐测试 | refactor |
| `61f99c326` | - | feat(notification): 新增系统通知文案模板与项目角色接收人策略 | feat |

## 改动范围

### 数据库
- **无 Flyway 迁移变更**（DB 风险低）
- DB 已应用最新版本仍为 V1159

### 后端
- **tender 模块**：CO-537 Tender.department 写入时持久化根因修复，3 个写入入口改用 enricher 反查；标讯详情页 department 兜底
- **project 模块**：CO-558 项目文档下载权限真正修复，新增 `isAssignedBidSpecialist` 排除 `bid-projectLeader`
- **project-doc 模块**：项目文档 6 入口接入 OBS 直传，修复 APISIX 网关 413
- **obs-upload 模块**：清理死代码守卫，集中化常量，动态化 UI 文案；标讯文件上传回退 OBS 直传，恢复 multipart + AI 解析
- **notification 模块**：设计评审 P0/P1 修复，统一角色枚举、可见性过滤与文案策略；新增系统通知文案模板与项目角色接收人策略；消息中心触发点统一使用新策略并补齐测试
- **drafting 模块**：案例切片推荐迁移到标书编制页面

### 前端
- **OBS 直传开关**：`VITE_OBS_ENABLED=true`（保持启用，与第 62 次一致）
- **项目文档 6 入口接入 OBS 直传**
- **标讯文件上传**：回退 OBS 直传，恢复 multipart + AI 解析（避免 413）
- **消息中心**：图标/标签映射与模块 README 更新
- **案例切片推荐**：迁移到标书编制页面
- **项目文档下载权限**：修复 bid-projectLeader 角色越权问题

## Flyway 预检 3 步法

| 步骤 | 结果 | 备注 |
|---|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（222 migrations） | 2026-07-09 13:24:17 CST |
| Step 2: DB 版本对比 | ✅ DB 最新=V1159，与源码一致（无新迁移） | success=1 |
| Step 3: remote-deploy.sh 内置 validate | ✅ VALIDATE OK - all checksums match（222 migrations） | 2026-07-09 13:26:20 CST |

## 部署步骤

### 1. 早操三连（在锚点分支 `agent/trae-init` 上 ff-only 同步）
- `git fetch origin main --prune`
- `git merge --ff-only origin/main`（HEAD 从 `1d626fbdb` 更新到 `1f276d050`）
- `export PATH="/Users/user/xiyu/worktrees/trae/scripts:$PATH"` 激活 git wrapper
- `bash scripts/check-git-wrapper.sh` ✓

### 2. 本地打包（生产同源构建模式）
- 命令：`RELEASE_ID="1f276d050-api8080-obs" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh`
- jar：`bid-platform-1.0.3.jar`
- 构建耗时：26.645s
- jar 内 Flyway 迁移版本无重复 ✓

### 3. 产物校验
- jar 内 Flyway 迁移文件数：221（与源码一致）
- 前端 index.html 入口：`assets/index-DJHn03p4.js`
- Archive 大小：160,670,151 字节（约 160MB）

### 4. 上传 + 部署
- scp archive + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
- 执行 `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）：
  - Flyway validate ✓
  - DB backup ✓
  - 停止 `xiyu-bid-backend` 服务 ✓
  - 更新 backend artifact ✓
  - 写入 `deployed-release.json` ✓
  - 启动 `xiyu-bid-backend` 服务 ✓（PID 18641）
  - 健康检查 ✓（3/3 consecutive，80 attempts）
  - 前端一致性验证 ✓

## 验证结果

### 健康检查
| 检查项 | 结果 | 备注 |
|---|---|---|
| `/actuator/health` | HTTP 200 UP | 所有组件 UP（aiProvider, db, diskSpace, jwt, livenessState, ping, readinessState, redis, sidecar） |
| `/actuator/health/readiness` | HTTP 200 UP | **无 Kafka SDK 延迟**（不同于第 8/9/10/13/15 次的 2-4 分钟延迟） |

### Smoke 测试
| 检查项 | 预期 | 实际 |
|---|---|---|
| `POST /api/auth/login`（空 body） | 400 | 400 ✓ |
| `GET /api/projects`（需认证） | 403 | 403 ✓ |
| `GET /api/integration/crm/health`（需认证） | 401 | 401 ✓ |
| `GET /` | 200 | 200 ✓ |
| `GET /login` | 200 | 200 ✓ |
| 前端入口 | `assets/index-DJHn03p4.js` | `assets/index-DJHn03p4.js` ✓ |

### Flyway 迁移应用验证
```
version  description                                  success  installed_on
1159     drop duplicate roles code index              1        2026-07-09 10:44:25
1158     cleanup duplicate roles add unique constraint 1        2026-07-09 10:44:25
1157     add unique index to warehouse name            1        2026-07-09 08:08:53
```
- DB 仍为 V1159（无新迁移应用，与源码一致）✓

## GitHub 镜像同步

| 项目 | 部署前 | 部署后 |
|---|---|---|
| GitHub vs Gitee 落后 commit 数 | 29 | 0 |
| Gitee main HEAD | `1f276d050` | `1f276d050` |
| GitHub main HEAD | （落后 29 commits） | `1f276d050` |
| 同步命令 | `bash scripts/sync-to-github.sh` | ✓ |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需回滚 |
| 回滚 posture | ready（旧 release 目录仍保留在 `/opt/xiyu-bid/releases/d4a8e5ad7-api8080-obs/`） |
| 回滚方式 | 激活旧 release 目录 + 重启 systemd 服务 |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-1f276d050-api8080-obs-<timestamp>.sql.gz` |

## 配置清理检查

| 配置项 | 状态 | 备注 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 用户在第 13/14/15 次部署时已决定保留（非临时配置） |
| `DEBUG`/`TRACE` 其他临时配置 | 无 | ✓ |

## 经验沉淀应用情况

本次部署应用了以下历史经验：
1. **Flyway 预检 3 步法**（第 6 次事故后建立）：✅ 全部通过
2. **同源构建 `VITE_API_BASE_URL=`**（第 8 次发现）：✅ 应用
3. **OBS 直传开关保持启用**（第 62 次延续）：✅ 应用
4. **`SYSTEMCTL_SUDO=true`**（第 15 次发现）：✅ 应用
5. **`--noproxy '*'` 绕过 Mac HTTP_PROXY**（第 19/23 次发现）：✅ 应用
6. **GitHub 镜像同步作为标准步骤**（第 13 次起）：✅ 应用
7. **SentryAppender 不手动声明**（第 34 次事故后修复）：✅ 未引入
8. **本次未出现 Kafka SDK readiness 延迟**（不同于第 8/9/10/13/15 次）

## 风险提示

1. **CO-537 Tender.department 写入持久化修复**：3 个写入入口改用 enricher 反查，需关注生产环境首次部署时是否有性能影响（enricher 反查增加 DB 查询）
2. **项目文档 6 入口接入 OBS 直传**：需关注 OBS 流量与配额
3. **标讯文件上传回退 multipart**：标讯文件不再走 OBS 直传，需关注大文件上传场景是否仍出现 413

## 部署确认清单

- [x] 环境门禁通过（用户确认测试环境 172.16.38.78）
- [x] 早操三连完成（git wrapper 激活，HEAD = origin/main）
- [x] Flyway 预检 3 步法全通过
- [x] 本地打包成功（jar 内迁移无重复）
- [x] 产物校验通过（221 迁移文件，前端入口一致）
- [x] remote-deploy.sh 执行成功
- [x] 健康检查 HTTP 200 UP（所有组件 UP）
- [x] Readiness HTTP 200 UP（无 Kafka 延迟）
- [x] Smoke 测试通过（400/403/401 均符合预期）
- [x] 前端页面可访问（HTTP 200，入口一致）
- [x] Flyway 迁移应用验证通过（DB 仍为 V1159）
- [x] GitHub 镜像同步完成（两边 main 一致）
- [x] 配置清理检查通过（仅 SHOW_DETAILS=always，用户已决定保留）
- [x] 部署报告生成

## 部署历史 commit 链（更新）

| # | 环境 | 日期 | Release ID | 新增迁移 | 备注 |
|---|---|---|---|---|---|
| 62 | test | 2026-07-09 | `d4a8e5ad7-api8080-obs` | 无 | PR !1921 紧急回退 audit-logs 鉴权，启用前端 OBS 直传 |
| **63** | **test** | **2026-07-09** | **`1f276d050-api8080-obs`** | **无** | **PR !1922-!1928 增量部署，CO-537/558 修复，通知 P0/P1，案例切片迁移** |
