# 第 64 次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 64 次 |
| 部署环境 | **测试环境**（test） |
| 部署日期 | 2026-07-09 |
| Release ID | `22638f08a-api8080-obs` |
| 部署时间 | 2026-07-09 15:20:48 CST（服务启动） |
| 前置 Release | `1f276d050-api8080-obs`（2026-07-09 13:26:23 CST 激活） |
| 部署结果 | ✅ 成功（健康检查恢复后 UP，无回滚） |
| 新增 Flyway 迁移 | 无 |
| 回滚状态 | 未需回滚 |
| 部署性质 | 正常增量部署（保持 OBS 直传开关启用） |
| 健康检查延迟 | 4 分 37 秒（Kafka SDK readiness 延迟，已知行为，自恢复） |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae/deploy-test-2026-07-09`（任务分支，基于 origin/main） |
| 部署 commit | `22638f08a`（!1934 fix(upload) InitiationStage uid 与 OBS customUpload 修复） |
| 前置 commit | `1f276d050`（!1928 fix CO-537 Tender.department 写入时持久化根因修复） |
| 增量 commit 数 | 14（含 2 个 docs 和重复 merge commit） |
| GitHub 镜像 | 部署前 GitHub 领先 1 commit（lock 清理 chore）→ force-with-lease 覆盖后两边一致 |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080（nginx 反代到 18080） |
| 构建命令 | `RELEASE_ID="22638f08a-api8080-obs" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh` |

## PR 列表

本次部署涵盖 14 个增量 commit（按时间倒序），去重后有效 PR：

| Commit | PR | 描述 | 类型 |
|---|---|---|---|
| `22638f08a` | !1934 | fix(upload): 补充 !1932 未覆盖的 InitiationStage uid 与 OBS customUpload 修复 | fix |
| `0895f420b` | !1935 | docs(release): 第 63 次部署报告 (test) | docs |
| `50b1e45bb` | !1932 | fix: 修复 stage 组件回填文件列表缺少 uid 导致 [ElUpload] file to be removed not found | fix |
| `2812b50ff` | !1933 | fix(CO-565): 仓库/业绩/品牌授权表格操作列按钮一行显示 | fix |
| `d918d45b9` | !1930 | CO-490 fix(margin): toLdt 增加 java.sql.Date 处理，修复缴纳/应退日期丢失 | fix |
| `a98649eee` | !1929 | fix(CO-554 v3): 判定和下载只认 attachments，杜绝无附件误显示下载按钮 | fix |
| `7c8450226` | !1931 | fix(CO-560): 平台账号导入事务崩溃三连修复——长度校验+@Async 代理+REQUIRES_NEW 隔离 | fix |

## 改动范围

### 数据库
- **无 Flyway 迁移变更**（DB 风险低）
- DB 已应用最新版本仍为 V1159

### 后端
- **margin 模块**（CO-490）：`toLdt` 增加 `java.sql.Date` 处理，修复缴纳/应退日期丢失
- **平台账号导入模块**（CO-560）：事务崩溃三连修复——长度校验 + `@Async` 代理 + `REQUIRES_NEW` 隔离；补强异常翻译层
- **附件下载模块**（CO-554 v3）：判定和下载只认 `attachments`，杜绝无附件误显示下载按钮

### 前端
- **OBS 直传开关**：`VITE_OBS_ENABLED=true`（保持启用，与第 62/63 次一致）
- **upload 模块**：修复 stage 组件回填文件列表缺少 uid 导致 `[ElUpload] file to be removed not found`；补充 InitiationStage uid 与 OBS customUpload 修复；customUpload onSuccess/onError 双重调用根因修复
- **表格 UI**（CO-565）：仓库/业绩/品牌授权表格操作列按钮一行显示

## Flyway 预检结果（3 步法）

| 步骤 | 结果 | 说明 |
|---|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（222 migrations） | DB 当前状态健康 |
| Step 2: DB 版本对比 | ✅ DB V1159 = 源码 V1159 | 无新增迁移需应用 |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK | 覆盖 jar 前自动 validate 通过 |

## 部署步骤

### 1. 环境门禁（Step 0）
- 用户确认部署到测试环境 `172.16.38.78`（winbid-01 测试服务器）

### 2. 早操三连 + 基线确认
- `source scripts/dev-env.sh` + `bash scripts/sync-env.sh .` + `bash scripts/check-git-wrapper.sh`
- git status 干净，HEAD = `22638f08a`
- GitHub 镜像领先 1 commit（lock 清理 chore），记录为后续处理项

### 3. 服务器现状探测
- 当前部署：`1f276d050-api8080-obs`（2026-07-09 05:26:23 UTC 激活）
- 后端健康：UP（DB、Redis、Sidecar、JWT 全部 UP）
- AI Provider: qwen3.7-max 已配置
- 增量 commit：14 个，无 Flyway 迁移变更

### 4. Flyway 预检 3 步法
- 全部通过（见上方表格）

### 5. 本地打包
- `RELEASE_ID="22638f08a-api8080-obs" VITE_API_BASE_URL= VITE_OBS_ENABLED=true bash scripts/release/package-release.sh`
- 构建成功（33.377s）
- jar 内 Flyway 迁移版本无重复 ✅
- Release archive: `.release/xiyu-bid-release-22638f08a-api8080-obs.tar.gz`

### 6. 产物校验
- jar 内 V*.sql 文件数：221 + B73 baseline = 222 migrations（与服务器一致）✅
- 无重复版本号 ✅
- 前端入口：`assets/index-BJnXb0y7.js`

### 7. 上传 + 部署
- `COPYFILE_DISABLE=1 scp` 上传 archive + remote-deploy.sh 到服务器
- 执行 `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）
- Flyway validate 通过
- 服务停止 → jar 更新 → 服务启动（15:20:48 CST）

### 8. 健康检查
- ⚠️ 健康检查 120 次未通过（remote-deploy.sh 内置检查）
- **根因**：Kafka SDK readiness 延迟（lessons-learned 第 2 条，历史第 8/15 次均出现）
- **恢复**：4 分 37 秒后自恢复（15:25:25 CST `/actuator/health` UP，`/actuator/health/readiness` 200）
- 业务接口在 readiness 503 期间全部正常响应（登录、tenders、notifications 均返回 200）

## 验证结果

### Smoke 测试（全部通过）

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| POST `/api/auth/login`（空 body） | 400 | 400 | ✅ |
| GET `/api/projects` | 403 | 403 | ✅ |
| GET `/api/integration/crm/health` | 401 | 401 | ✅ |
| 前端首页 `/` | 200 | 200 | ✅ |
| 前端 `/login` | 200 | 200 | ✅ |
| 前端入口 JS | `assets/index-BJnXb0y7.js` | `assets/index-BJnXb0y7.js` | ✅ |

### 迁移应用验证
- DB 已应用最新版本：V1159（`drop duplicate roles code index`，2026-07-09 10:44:25）
- 无新迁移需应用

## GitHub 同步

| 项目 | 值 |
|---|---|
| 部署前状态 | GitHub main 领先 Gitee 1 commit（`494436d97 chore(locks): prune stale expired locks`，GitHub bot 自动删除过期 lock 文件） |
| 处理方式 | force-with-lease 覆盖 GitHub main（用户确认） |
| 部署后状态 | ✅ 两边 main 完全一致（`22638f08a`） |
| 被覆盖的 commit | `494436d97 chore(locks): prune stale expired locks`（删除 `.agent-locks/co-523-fix-alert-rules-type-enum.yml`） |
| 风险提示 | Gitee main 上该过期 lock 文件仍存在，建议后续单独清理 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 是否需要回滚 | 否 |
| 回滚姿态 | ready（如需可恢复前置 release） |
| 前置 release | `1f276d050-api8080-obs` |
| 前置 jar 位置 | `/opt/xiyu-bid/releases/1f276d050-api8080-obs/backend/app.jar` |
| 回滚命令 | `sudo cp /opt/xiyu-bid/releases/1f276d050-api8080-obs/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| DB 回滚 | 不需要（无 schema 变更） |
| DB 备份 | `winbid-22638f08a-api8080-obs-<timestamp>.sql.gz`（remote-deploy.sh 自动创建） |

## 经验沉淀应用情况

| 经验条目 | 本次应用 | 说明 |
|---|---|---|
| 第 2 条：Kafka SDK readiness 延迟 | ✅ 应用 | 4 分 37 秒自恢复，未急于回滚，业务接口正常 |
| 第 3 条：生产前端同源构建 | ✅ 应用 | `VITE_API_BASE_URL=` 显式设空，同源构建 |
| 第 4 条：Smoke 测试限制 | ✅ 应用 | 用 400/403/401 替代完整登录 smoke |
| 第 6 条：临时调试配置清理 | ✅ 应用 | 检查 SHOW_DETAILS/DEBUG/TRACE，仅 SHOW_DETAILS=always（用户决定保留） |
| 第 7 条：幂等迁移设计 | ✅ 应用 | 无新迁移，DB 风险低 |
| 第 8 条：systemctl sudo 权限 | ✅ 应用 | `SYSTEMCTL_SUDO=true` |
| 第 14 条：macOS `._*` 残留文件 | ✅ 应用 | `COPYFILE_DISABLE=1` scp |
| 第 16 条：Mac HTTP_PROXY 502 | ✅ 应用 | `curl --noproxy '*'` |

## 风险提示

1. **Gitee main 过期 lock 文件**：`.agent-locks/co-523-fix-alert-rules-type-enum.yml` 仍存在，建议后续单独清理（lock 清理 commit 被 GitHub 覆盖）
2. **Kafka SDK readiness 延迟**：已知行为，本次 4 分 37 秒，建议考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行

## 部署确认清单

- [x] 环境门禁通过（用户确认测试环境）
- [x] 早操三连执行
- [x] 基线确认（git status 干净，HEAD = 22638f08a）
- [x] 服务器现状探测（健康 UP，release 1f276d050-api8080-obs）
- [x] Flyway 预检 3 步全部通过
- [x] 本地打包成功（jar 内无重复迁移版本）
- [x] 产物校验通过（222 migrations，前端入口一致）
- [x] 上传 + 部署成功（SYSTEMCTL_SUDO=true）
- [x] 健康检查通过（4 分 37 秒后 UP，Kafka readiness 自恢复）
- [x] Smoke 测试全部通过
- [x] 迁移应用验证（V1159 已应用）
- [x] GitHub 镜像同步（force-with-lease 覆盖后两边一致）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 保留，无临时 DEBUG/TRACE）
- [x] 部署报告生成
