# 第 115 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | test（测试环境） |
| Release ID | `41f342a8c` |
| 部署时间 | 2026-08-03 22:10:16 CST |
| 服务器 | winbid-01（172.16.38.78） |
| 部署类型 | 增量部署（AI 评分按钮修复 + 业绩附件路径配置 + OOM 根因修复 + E2E 修复） |
| 健康状态 | ✅ UP |
| 回滚状态 | 不需要 |

## 基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步） |
| HEAD commit | `41f342a8c` |
| 上一版本 commit | `557d3091d`（第 114 次部署） |
| GitHub 镜像同步 | ✅ 已同步（无差异） |
| 工作区状态 | 干净（git status --short 无输出） |

## PR 列表

| PR # | 标题 | 类型 |
|---|---|---|
| !2248 | fix(performance): 补齐 application-prod.yml 业绩附件路径显式配置（XIYU-1R 根因修复） | bug fix |
| !2247 | fix(score-parse): AI 评分标准解析按钮事件名错配导致点击无反应 | bug fix |
| !2246 | fix(e2e): 恢复被关闭的 PR #2244 — CO-601 E2E 测试修复（数据隔离 + /bidAdmin 角色） | test fix |
| (无 PR) | fix(oom): 后端 OOM 根因修复：JVM 堆限制 + 日志降级 + size 上限保护 | bug fix |
| (无 PR) | docs: 知识沉淀 — 业绩附件导出空图根因分析（lessons-learned #97） | docs |
| (无 PR) | docs(release): 第 114 次测试环境部署报告 (test, 557d3091d) | docs |

## 改动范围

### 主要功能

1. **AI 评分标准解析按钮修复**（!2247）
   - 按钮事件名错配导致点击无反应
   - 前端事件绑定修正

2. **业绩附件路径显式配置**（!2248）
   - `application-prod.yml` 补齐 `PERFORMANCE_ATTACHMENT_ROOT` 和 `APP_UPLOAD_PERFORMANCE_DIR` 显式配置
   - XIYU-1R 根因修复：生产环境业绩附件导出空图问题
   - 知识沉淀：lessons-learned #97

3. **后端 OOM 根因修复**（无 PR，commit `be155d710`）
   - JVM 堆限制 + 日志降级 + size 上限保护
   - `CustomFieldsCodec` 改动（23 行）
   - `TenderController` 改动（9 行）
   - `application-dev.yml` 改动（13 行）
   - 新增 `TenderControllerSizeCapTest`（152 行测试）

4. **CO-601 E2E 测试修复**（!2246）
   - 数据隔离 + `/bidAdmin` 角色
   - `project-form-custom-fields.spec.js` 大幅重写（1298 行）
   - `ProjectTaskBoardCard.spec.js` + `ProjectTaskBoardCard.vue` 调整

### 新增 Flyway 迁移

无新增迁移文件（`backend/src/main/resources/db/migration-mysql/` 无变更）。

## Flyway 预检结果

| 步骤 | 结果 | 详情 |
|---|---|---|
| Step 1: validate | ✅ 通过 | 244 migrations validated, all checksums match |
| Step 2: DB 版本对比 | ℹ️ 跳过 | 本次无新迁移，无需对比 |
| Step 3: remote-deploy 内置 | ✅ 通过 | 部署时自动 validate 通过（VALIDATE OK - all checksums match） |

## 部署步骤

| 步骤 | 结果 | 详情 |
|---|---|---|
| 环境门禁 | ✅ | 用户确认测试环境 172.16.38.78 |
| 早操三连 | ✅ | sync-env（锚点分支 ff-only）+ check-git-wrapper |
| 基线确认 | ✅ | HEAD=41f342a8c，工作区干净 |
| 服务器现状 | ✅ | 上一版本 557d3091d（08-02），health UP |
| Flyway 预检 | ✅ | validate 通过（244 migrations） |
| 本地打包 | ✅ | RELEASE_ID=41f342a8c + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1 |
| 产物校验 | ✅ | obsEnabled=true, jar 内 243 个 V*.sql 无重复, 前端入口 assets/index-DJ8YbiAg.js |
| 上传 + 部署 | ✅ | scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） |
| 前端资源保留 | ✅ | 从 /opt/xiyu-bid/releases/557d3091d/frontend/assets/ cp -rn 旧 assets（257 个文件） |
| 健康检查 | ✅ | consecutive 3/3, 79 attempts |
| 部署记录写入 | ✅ | /opt/xiyu-bid/deployed-release.json（releaseId=41f342a8c） |

## 验证结果

### 后端健康检查

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅（无 Kafka SDK 延迟） |

### API Smoke 测试

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| `GET /api/integration/crm/health`（无认证） | 401 | 401 | ✅ |

### 前端验证

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /`（经 Nginx 8080） | 200 | 200 | ✅ |
| `GET /login`（经 Nginx 8080） | 200 | 200 | ✅ |
| 前端入口 asset | 与 release 一致 | `assets/index-DJ8YbiAg.js` | ✅ |

## GitHub 镜像同步

| 项目 | 状态 |
|---|---|
| GitHub main 落后 Gitee | 0 commit |
| 同步状态 | ✅ 已同步 |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 历史决定保留（第 13-15 次均确认） |
| `DEBUG` / `TRACE` 临时配置 | 无 | 干净 |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ remote-deploy 内置 validate 通过（无新迁移，跳过手动 DB 版本对比） |
| OBS 直传双保险 | ✅ VITE_OBS_ENABLED=true 显式传入 + 产物校验 obsEnabled=true + Detail chunk .upload( 调用数=2 |
| 同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| macOS ._* 残留 | ✅ COPYFILE_DISABLE=1 |
| SYSTEMCTL_SUDO=true | ✅ 避免第 15 次 Interactive authentication 事故 |
| 前端 hash 资源跨版本 404 | ✅ 从上一版本 release 目录 cp -rn 旧 assets 保留 24h（257 个文件） |
| Kafka SDK readiness 延迟 | ✅ 本次未出现（readiness 首次即 200） |
| 生产环境业绩附件路径修复（XIYU-1R） | ✅ application-prod.yml 显式配置补齐，测试环境同步受益 |

## 风险提示

1. **无新增 Flyway 迁移**：本次部署无 DDL 变更，回滚无需 DB 恢复
2. **OOM 修复涉及 JVM 堆限制**：服务器 JVM `-Xmx2g`（systemd 服务配置），开发环境 start.sh 默认 `-Xmx1g`，需确认生产环境配置一致
3. **E2E 大幅重写**：project-form-custom-fields.spec.js 改动 1298 行，需关注后续 E2E CI 稳定性

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚方式 | 回退到上一版本 jar + 前端 |
| 上一版本 Release ID | `557d3091d` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/557d3091d` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-41f342a8c-*.sql.gz` |
| 回滚命令 | `ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && cp /opt/xiyu-bid/releases/557d3091d/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl start xiyu-bid-backend'` |
| DB 回滚 | ℹ️ 本次无 DDL 变更，无需 DB 回滚 |

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连 + 基线干净
- [x] Flyway 预检通过（244 migrations, all checksums match）
- [x] 打包参数正确（OBS + 同源 + COPYFILE_DISABLE）
- [x] 产物校验通过（obsEnabled + 迁移文件无重复 + 前端入口一致）
- [x] 部署成功（remote-deploy.sh 退出码 0）
- [x] 健康检查通过（UP + readiness 200，无 Kafka 延迟）
- [x] Smoke 测试全部符合预期（health/readiness/login/projects/crm）
- [x] 前端入口一致（assets/index-DJ8YbiAg.js）
- [x] 前端资源保留（257 个旧 assets 文件）
- [x] 配置清理检查（仅历史保留项，无临时调试配置）
- [x] GitHub 镜像同步（无差异）
- [x] 部署报告生成
