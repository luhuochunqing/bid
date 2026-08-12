# 第 123 次测试环境部署报告 — 2026-08-12

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试 (test)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.38.78` |
| 用途 | 日常部署验证、功能测试 |
| 部署序号 | 第 123 次（测试） |
| 部署时间 | 2026-08-12 14:17:40 CST（服务启动） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `4fc4e1868` |
| 上一版本 Release | `0ef90d10c`（2026-08-12 11:39:24 CST，第 122 次测试部署） |
| 基线 commit | `4fc4e1868`（origin/main HEAD） |
| 激活时间 | 2026-08-12T14:17:40 CST（systemd 启动） |
| 健康检查通过 | 2026-08-12T14:17:41 CST（remote-deploy 内置，79 次尝试后 3/3 连续通过） |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 增量 commit | 14 个（相对上一版本） |
| 新增 Flyway 迁移 | 0 个 |
| Smoke 测试 | 全部通过 |
| GitHub 镜像 | ✅ 已同步 |

## 背景

本次部署为【业绩合订本导出 OOM 修复】（`fix-performance-bundle-oom`）。

上一版本 `0ef90d10c`（第 122 次）已包含标讯事件推送 Bean 注册修复。本次增量核心为业绩合订本导出（PDF 渲染）内存溢出问题的根治：原 300 DPI 渲染 + 单文件最多 30 页，在 30 条业绩批量导出时 A4 单页约 26MB × 900 页 ≈ 23.5GB，触发 OOM。

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`（主工作区）
- 部署分支：`agent/trae-init`（锚点分支，HEAD = origin/main）
- 早操：sync-env.sh 通过（本地门禁 7/7 就绪）
- GitHub 镜像：部署前落后 22 commit，已通过 sync-to-github.sh 同步至一致

## 增量改动（0ef90d10c → 4fc4e1868，14 个 commit）

### 关键改动

| Commit | 描述 |
|---|---|
| a83c63564 | fix: 业绩合订本导出 OOM — 降 PDF 渲染 DPI 300→150 + 页数上限 30→10 |
| 236c6e1d6 | feat(release): 打包前强制运行容器测试，防止漏加 @Service 导致 crash-loop |
| 6dec3160f | docs: 沉淀业绩批量导入按合同名 upsert 去重踩坑（§112） |
| 52151c8fc | chore: .agents/skills/ 整体加入 gitignore，移除本地 skill 的 git 跟踪 |
| 其他 | wiki 健康检查日期刷新、容器测试 @Service 教训回填 |

### 改动范围聚合

| 目录 | 主要内容 |
|---|---|
| backend/performance/infrastructure | `PerformanceWordStyleConfig.java`：`PDF_RENDER_DPI` 300→150，`MAX_PDF_PAGES_PER_FILE` 30→10 |
| backend/src/test | `FlywayMysqlContainerTest`（打包门禁引用的容器测试） |
| scripts/release | `package-release.sh` 新增容器测试门禁（逃生阀 `XIYU_SKIP_CONTAINER_TEST`） |
| docs/wiki | 教训沉淀、健康检查日期刷新 |

> **本次部署业务代码变更为纯常量修改**（PDF DPI 与页数上限），不新增任何 Bean / @Service，不涉及 Spring Bean 装配。

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（246 migrations） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过 |

本次部署无新增迁移文件（迁移变更列表为空）。

## 部署步骤

1. ✅ 环境门禁确认（测试环境 172.16.38.78）
2. ✅ 早操三连（sync-env.sh + check-git-wrapper.sh）
3. ✅ GitHub 镜像同步至一致（4fc4e1868）
4. ✅ 本地打包（RELEASE_ID=4fc4e1868, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1）
5. ✅ 产物校验（jar 迁移无重复、obsEnabled=true、Detail .upload(=2、index 入口 index-ayUbZQ1Y.js）
6. ✅ scp 上传到 `/opt/xiyu-bid/incoming/`（154M archive + remote-deploy.sh）
7. ✅ remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
8. ✅ Flyway validate 通过（246 migrations validated）
9. ✅ DB 备份完成（`winbid-4fc4e1868-20260812141732.sql.gz`，11M）
10. ✅ 后端服务重启（active/running，PID 10632，2026-08-12 14:17:41 CST）
11. ✅ 健康检查通过（3/3，79 次尝试，无 Kafka 延迟）
12. ✅ 前端一致性验证（assets/index-ayUbZQ1Y.js）
13. ✅ 前端资源保留（从上一版本 0ef90d10c cp -rn 旧 assets）

## 验证结果

### 后端启动验证

- systemd active (running)，Main PID 10632
- 健康检查 UP（aiProvider/db/diskSpace/jwt/redis/sidecar 全部 UP）
- readiness UP

### Smoke 测试（全部通过）

| # | 测试 | 结果 | 备注 |
|---|---|---|---|
| 1 | /actuator/health | ✅ HTTP 200 | UP |
| 2 | /actuator/health/readiness | ✅ HTTP 200 | UP |
| 3 | POST /api/auth/login（空 body） | ✅ HTTP 400 | 参数校验 |
| 4 | GET /api/projects（无认证） | ✅ HTTP 403 | 需认证 |
| 5 | GET /api/integration/crm/health（无认证） | ✅ HTTP 401 | 需认证 |
| 6 | 前端首页 `/` | ✅ HTTP 200 | — |
| 7 | 前端 /login | ✅ HTTP 200 | — |

### 迁移应用验证

本次无新增迁移，无需验证新迁移版本。DB 已应用版本健康（246 migrations validated）。

## GitHub 同步

- ✅ 部署前已通过 `bash scripts/sync-to-github.sh` 同步（Gitee main 4fc4e1868 → GitHub main 4fc4e1868）
- 部署后确认 `github/main..origin/main = 0`，镜像一致

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release ID | `0ef90d10c` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-4fc4e1868-20260812141732.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/0ef90d10c/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'` |

> 上一版本 `0ef90d10c` 为已知良版本（第 122 次部署成功），可直接回滚。

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（经验 #1）
- ✅ OBS 直传显式传入 VITE_OBS_ENABLED=true（经验 #10）
- ✅ COPYFILE_DISABLE=1 避免 macOS `._*` 残留（经验 #14）
- ✅ SYSTEMCTL_SUDO=true（经验 #8）
- ✅ --noproxy '*' 避免 Mac HTTP_PROXY 502（经验 #16）
- ✅ 前端 hash 资源跨版本 404 保留旧 assets（经验 #18）
- ✅ 容器测试门禁逃生阀（`XIYU_SKIP_CONTAINER_TEST=true`，本次改动为纯常量不涉及 Bean 装配）

## 风险提示

1. **容器测试门禁在本机 Docker 不兼容**：增量 commit `236c6e1d6` 引入打包前强制运行 `FlywayMysqlContainerTest`，但本机 Docker Desktop 的 socket 与 docker-java 原生客户端不兼容（返回 400 BadRequest），Testcontainers 无法启动 MySQL 容器。本次部署经用户确认使用逃生阀 `XIYU_SKIP_CONTAINER_TEST=true` 跳过（本次改动为纯常量修改，不涉及 Spring Bean 装配，容器测试对本次改动无验证价值）。**改进方向**：在能正常跑 Testcontainers 的环境（如 CI）保持容器测试门禁，或修复本机 Docker socket 兼容性。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] GitHub 镜像同步至一致
- [x] Flyway 预检通过（3 步法）
- [x] 打包产物校验通过（jar 迁移无重复 + OBS obsEnabled=true + Detail .upload(=2）
- [x] 部署成功（jar 覆盖 + 服务重启）
- [x] 健康检查通过（启动后瞬时）
- [x] Smoke 测试通过（7/7）
- [x] 前端资源保留（防跨版本 404）
- [x] 部署报告生成

---

## 变更记录

| 日期 | 变更内容 |
|------|------|
| 2026-08-12 | 首次创建，基于第 123 次测试环境部署结果 |