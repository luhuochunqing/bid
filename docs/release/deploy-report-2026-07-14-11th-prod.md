# 第 11 次生产环境部署报告 — 2026-07-14

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 11 次（生产） |
| 部署时间 | 2026-07-14 20:50:27 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `59d3763cd` |
| 上一版本 Release | `3fde377c4`（2026-07-13 17:55:54 CST，第 10 次生产部署） |
| 基线 commit | `59d3763cd`（origin/main） |
| 激活时间 | 2026-07-14T12:50:27Z |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 无（最新仍为 V1165） |
| Smoke 测试 | 全部通过 |
| GitHub 镜像 | 已同步（0 commits behind） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- 锚点分支：`agent/trae-init`（ff-only 同步到 origin/main）
- 早操 SOP：已执行 `sync-env.sh`，HEAD = origin/main = `59d3763cd`
- GitHub 镜像状态：已同步（0 commits behind）
- 本地门禁自检：7 项全部通过（hooksPath、pre-commit、pre-push、git wrapper、agent-locks 等）

## 增量改动（3fde377c4 → 59d3763cd，14 个 commit）

### 主要 PR 列表

| PR | 说明 |
|---|---|
| !2078 | fix(tender): 第三方推送路径去重改用四字段策略（+projectType） |
| !2075 | docs(lessons): 沉淀近期 session 工程经验（§56-§59 + CRM §13 + 商机洞察规格） |
| !2077 | chore(wiki): refresh health_checked dates to 2026-07-14 |
| !2076 | feat(tender): 标讯去重规则新增项目类型维度 |
| !2074 | feat(warehouse): CO-582 新增仓库信息导出 Word 合订本能力 |
| !2073 | docs(release): 第 10 次生产环境部署报告 |

### 改动范围

- **后端**：
  - 标讯第三方推送路径去重改用 `TenderDeduplicationService` 四字段策略（+projectType）
  - 去重逻辑优化：消除探针实体 + 统一异常类型
  - CO-582 仓库信息导出 Word 合订本能力（warehouse 模块）
  - CO-582 PR Code Review 问题修复
- **前端**：
  - 标讯去重规则新增项目类型维度（前端配合）
- **文档**：
  - 沉淀近期 session 工程经验（lessons-learned §56-§59 + CRM §13 + 商机洞察规格）
  - 第 10 次生产部署报告
  - Wiki health_checked 日期刷新

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（228 migrations） |
| Step 2: DB 已应用版本 vs 源码最新版本 | ✅ 一致（V1165，2026-07-12 08:57:40 应用） |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过（228 migrations, all checksums match） |
| 新增迁移文件 | 无（本次部署无数据库变更） |

## 部署步骤

1. **环境门禁**：用户确认部署到生产环境 172.16.10.149（AskUserQuestion 显式授权）
2. **早操 SOP**：`sync-env.sh` 完成，HEAD = origin/main = `59d3763cd`，7 项门禁通过
3. **服务器现状检查**：当前部署 `3fde377c4`（第 10 次生产部署），后端 health UP
4. **Flyway 预检 3 步法**：全部通过，DB V1165 = 源码 V1165
5. **本地打包**：
   - `RELEASE_ID=59d3763cd VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
   - 前端构建 + 后端 mvn clean package（28.168s）
6. **产物校验**：
   - ✅ release-metadata.json: `obsEnabled=true`, `apiBaseUrl=""`, `sentryEnabled=false`
   - ✅ Detail chunk `.upload(` 调用数=2（OBS 直传已启用，package-release.sh 内置校验）
   - ✅ jar 内最新 V1165，与源码一致，无重复版本
   - ✅ 前端入口 `assets/index-Cb5KSg_0.js`
   - ✅ tar.gz 153M
7. **上传 + 部署**：
   - scp tar.gz + remote-deploy.sh 到 `/opt/xiyu-bid/incoming/`
   - remote-deploy.sh 执行（SYSTEMCTL_SUDO=true）
   - Flyway validate 通过（228 migrations, all checksums match）
   - 后端服务重启：active/running since 20:50:27 CST（PID 5589）
   - 健康检查通过：consecutive 3/3, 14 attempts（约 28 秒，无 Kafka SDK 延迟）
   - 前端一致性验证：`/assets/index-Cb5KSg_0.js`
8. **前端资源保留**：从上一版本 `3fde377c4` 复制旧 assets 到 `/srv/www/xiyu-bid/assets/`（防止跨版本 404）

## 验证结果

### 后端健康检查（内部 18080）

| 组件 | 状态 | 详情 |
|---|---|---|
| overall | UP | - |
| aiProvider | UP | configured, qwen3.7-max |
| db | UP | MySQL, isValid() |
| diskSpace | UP | 80GB free / 98GB total |
| jwt | UP | HMAC-SHA256, 47 bytes |
| livenessState | UP | - |
| readinessState | UP | - |
| redis | UP | 6.2.19 |
| sidecar | UP | http://localhost:8000 |

### Smoke 测试（服务器本地，经 Nginx 8080）

| 检查项 | 结果 | 预期 |
|---|---|---|
| /actuator/health | HTTP 200 UP | ✅ |
| /actuator/health/readiness | HTTP 200 UP | ✅ |
| /api/auth/login POST | HTTP 400 参数校验失败 | ✅ |
| /api/projects | HTTP 403 需认证 | ✅ |
| /api/integration/crm/health | HTTP 401 需认证 | ✅ |

### 前端验证（服务器本地，经 Nginx 8080）

| 检查项 | 结果 | 预期 |
|---|---|---|
| 首页 / | HTTP 200 | ✅ |
| /login | HTTP 200 | ✅ |
| index.html 入口 | assets/index-Cb5KSg_0.js | ✅ 与 release 一致 |

### 迁移验证

- DB 最新版本：V1165（无变化，与部署前一致）
- 本次部署无新迁移应用（纯代码/文档变更）

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前落后 commit 数 | 0 |
| 同步状态 | ✅ 已同步（早操 SOP sync-env.sh 已确认） |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| SHOW_DETAILS=always | 保留 | 历史决定保留（第 13/14/15 次部署用户决定） |
| DEBUG/TRACE | 无 | ✅ 无临时调试配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 Release | `3fde377c4` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/3fde377c4` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-59d3763cd-*.sql.gz` |
| 回滚方式 | 恢复上一版本 jar + 前端（本次无迁移，无需恢复数据库） |

## 经验沉淀应用情况

1. **OBS 直传三层防护**（第 8 次生产事故根治）：
   - package-release.sh 默认 VITE_OBS_ENABLED=true
   - 打包时显式传入 VITE_OBS_ENABLED=true 作双保险
   - 产物校验 obsEnabled=true + Detail chunk `.upload(` 调用数=2
2. **Flyway 预检 3 步法**：部署前主动 validate + DB 版本对比，避免启动时才发现问题
3. **前端资源保留**：部署后从上一版本 release 目录 `cp -rn` 旧 assets，防止跨版本 404
4. **SYSTEMCTL_SUDO=true**：jetty 用户已配置 NOPASSWD sudo，避免服务重启失败
5. **COPYFILE_DISABLE=1**：避免 macOS `._*` 残留文件污染服务器
6. **Mac HTTP_PROXY 502 经验**：从本地 Mac 访问生产 172.16.10.149:8080 超时（curl 退出码 28），改用服务器本地 curl 验证（skill 第 16 条经验）

## 风险提示

1. **Nginx 8080 外部访问超时**：从本地 Mac 访问生产 172.16.10.149:8080 超时（curl 退出码 28），服务器内部访问全部正常。可能是 Mac HTTP_PROXY 干扰或网络策略限制，不影响服务正常运行。
2. **无新增 Flyway 迁移**：本次部署纯代码/文档变更，无数据库 schema 变更，回滚风险低。
3. **增量改动聚焦于标讯去重 + 仓库导出**：本次 14 个 commit 主要优化第三方推送路径去重（四字段策略 + projectType 维度）和新增仓库信息 Word 合订本导出，建议关注上线后标讯去重行为和仓库导出功能运行状态。

## 部署确认清单

- [x] 环境门禁确认（生产 172.16.10.149，AskUserQuestion 显式授权）
- [x] 早操 SOP + 基线确认（HEAD = origin/main = 59d3763cd）
- [x] 服务器现状检查（3fde377c4, health UP）
- [x] Flyway 预检 3 步法（全部通过，DB V1165 = 源码 V1165）
- [x] 本地打包（BUILD SUCCESS 28.168s, OBS obsEnabled=true）
- [x] 产物校验（jar V1165 最新, 前端入口 index-Cb5KSg_0.js, tar.gz 153M）
- [x] 上传 + 部署（remote-deploy.sh 成功，健康检查 3/3 通过）
- [x] 前端资源保留（3fde377c4 旧 assets 已复制）
- [x] 健康检查（health UP, readiness UP，无 Kafka SDK 延迟）
- [x] Smoke 测试（5 项全部符合预期）
- [x] 前端验证（/, /login 200, index.html 入口一致）
- [x] 迁移验证（DB V1165 无变化）
- [x] GitHub 镜像同步（0 commits behind，已同步）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 历史保留项）
- [x] 部署报告生成

## 回滚指引

如需回滚到上一版本 `3fde377c4`：

```bash
# 1. 恢复后端 jar
ssh jetty@172.16.10.149 'cp /opt/xiyu-bid/releases/3fde377c4/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'

# 2. 恢复前端
ssh jetty@172.16.10.149 'sudo cp -R /opt/xiyu-bid/releases/3fde377c4/frontend/* /srv/www/xiyu-bid/'

# 3. 等待健康检查
ssh jetty@172.16.10.149 'for i in $(seq 1 120); do if curl -fsS http://127.0.0.1:18080/actuator/health >/dev/null 2>&1; then echo "✅ 健康检查通过"; break; fi; sleep 2; done'

# 4. 恢复数据库（本次无迁移变更，无需恢复）
```
