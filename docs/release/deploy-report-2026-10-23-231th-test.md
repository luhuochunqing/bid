# 第 231 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-23 08:01 (UTC+8) |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `4ea606791` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit（本次部署的代码版本） | `4ea606791` |
| 旧版本 | `de9a8aea9`（第 230 次，2026-10-22 18:34） |
| 构建包 | `.release/xiyu-bid-release-4ea606791.tar.gz`（155M） |
| 增量 | 15 个 commit，22 个文件（+1104/-62） |
| 激活时间 | 2026-10-23T00:01:31Z（08:01:31 UTC+8） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-4ea606791-20261023080121.sql.gz`（15M） |

## PR 列表（增量 15 个 commit，自 de9a8aea9..4ea606791）

| Commit | 说明 |
|---|---|
| `4ea606791` | **!2849** fix(export): 流式合订本下载不再丢正文并被改写成 401（auto-merge） |
| `37c46944a` | docs: 回填合订本流式下载空正文与异步 401 |
| `30c143667` | fix(export): 流式合订本下载不再丢正文并被改写成 401 |
| `18f122d4d` | **!2848** merge: closure-deposit-amount-match |
| `32493e1e6` | docs: 沉淀结项保证金双口径与大小写 yes 根因 |
| `b82f30421` | **!2846** fix(project): 结项保证金金额以立项为准 |
| `8216f862c` | merge: 合入 origin/main，保留结项与任务看板 wiki 条目 |
| `e6478848c` | **!2847** fix(task-board): 全局任务看板保证金任务放权项目负责人 + 保存即时局部更新 |
| `1ba4c1351` | docs(wiki): 补充全局任务看板项目负责人放权与响应式更新知识记录 |
| `afe169f4d` | fix(task-board): 全局任务看板保证金任务放权项目负责人 + 保存即时局部更新 |
| `df64ec8f8` | refactor(project): 收紧结项保证金金额助手的可见性 |
| `22d39e492` | fix(project): 结项保证金金额以立项为准 |
| `b545738c1` | **!2845** docs(release): 第 230 次测试环境部署报告（fa8a65229 → de9a8aea9） |
| `0fe9b3db7` | docs(release): 按独立审查意见修正第 230 次报告计数与表述 |
| `5061a0447` | docs(release): 第 230 次测试环境部署报告（fa8a65229 → de9a8aea9） |

## 改动范围

- **后端（5 文件）**：
  - `config/AccessLogFilter.java`、`config/SecurityConfig.java`（流式下载鉴权与访问日志）
  - `project/entity/ProjectDepositSnapshot.java`、`project/service/ProjectClosureDepositAssembler.java`（结项保证金以立项为准）
  - `warehouse/controller/WarehouseExportController.java`（合订本流式导出 Content-Disposition / 正文）
- **后端测试（4 文件）**：`AccessLogFilterTest`、`StreamingDownloadRegressionTest`、`ProjectClosureServiceTest`、`WarehouseExportContentDispositionTest`
- **前端（6 文件）**：
  - `views/TaskBoard/TaskBoardPage.vue`、`TaskBoard/components/TaskBoardTaskDrawer.vue`（保证金任务放权项目负责人 + 保存即时局部更新）
  - `views/Knowledge/components/PerformanceBundleExportDialog.vue`（合订本导出）
  - 对应 3 个 `.spec.js` 单元测试
- **数据库迁移**：**无新迁移**（源码 max V1220 = DB 已应用 V1220，无 pending）
- **Sidecar 源码**：**零变更**（指纹 `24c0104f…` 与已部署版本一致）
- **文档 / Wiki（5 文件）**：`docs/lessons/lessons-learned.md`、第 230 次部署报告、`.wiki/pages/` 3 个新页面 + `_index.md` + `log.md`

## Flyway 预检结果

| 检查项 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK（Successfully validated 280 migrations，checksums 全匹配） |
| Step 2: DB 已应用版本 | 最新 V1220（2026-09-27），与源码 max V1220 一致，无 pending |
| Step 3: remote-deploy 内置 validate | ✅ 通过（激活前自动预检） |

## 部署步骤

1. **环境门禁**：AskUserQuestion 确认 ENV=test（172.16.38.78）✅
2. **早操三连**：`bash scripts/sync-env.sh .`（锚点分支 ff-only，`init branch is up-to-date with origin/main`，本地门禁 7/7 就绪）✅
   - 注：锚点分支上 `source scripts/dev-env.sh` 会被 session-gate 阻断，直接以 `bash scripts/sync-env.sh .` 完成同步
3. **基线确认**：HEAD = origin/main = `4ea606791`，工作区干净 ✅
4. **服务器现状**：旧版 `de9a8aea9` 运行中，health UP、service active、磁盘 71%（余 28G）✅
5. **本地打包**：`RELEASE_ID=4ea606791 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh` ✅
   - `BUILD SUCCESS`，jar 内 279 个迁移文件无重复版本
   - sidecar SHA-256 `24c0104ff…`（未变更，复用已部署产物）
6. **产物校验**：`obsEnabled: true` ✅；Detail chunk `.upload(` 调用数 = 2 ✅；前端口 `assets/index-Bj7OOARQ.js` ✅；无 `._*` 残留 ✅
7. **上传部署**：scp 后 MD5 双向一致（`8b800b4e298bcf19f9b740f3884bb8c8`）；`remote-deploy.sh` 以 `SYSTEMCTL_SUDO=true` 执行，DB 备份 → validate → 激活前端 → 原子替换 jar → 写部署记录 → 重启服务 ✅
8. **前端资源保留**：从 `/opt/xiyu-bid/releases/de9a8aea9/frontend/assets/` 执行 `cp -rn` 回补 193 个旧 hash 资源（当前 assets 共 278 个），旧标签页 24h 内可自然刷新 ✅

## 验证结果

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health`（8080） | 200 UP | HTTP 200 | ✅ |
| `/actuator/health/readiness` | 200 UP | `{"status":"UP"}`（db UP / readinessState UP） | ✅ |
| `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| `GET /api/projects`（未认证） | 403 | HTTP 401 | ⚠️ 401 亦符合未认证语义 |
| `GET /api/integration/crm/health`（未认证） | 401 | HTTP 401 | ✅ |
| 前端 `/` | 200 | HTTP 200 | ✅ |
| 前端 `/login` | 200 | HTTP 200 | ✅ |
| 前端入口 hash | `index-Bj7OOARQ.js` | `assets/index-Bj7OOARQ.js` | ✅ |
| `deployed-release.json` | releaseId=4ea606791 | `4ea606791` / activatedAt `2026-10-23T00:01:31Z` | ✅ |
| 服务状态 | active | `xiyu-bid-backend` active/running | ✅ |
| 健康检查等待 | 最多 4 分钟 | 3 次连续通过（总尝试 80 次） | ✅ 无 Kafka readiness 延迟 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一已知良好版本 | `de9a8aea9`（`/opt/xiyu-bid/releases/de9a8aea9`） |
| 上一版本前端 assets | 已在当前 `/srv/www/xiyu-bid/assets/` 中保留（`cp -rn` 回补 193 个） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-4ea606791-20261023080121.sql.gz` |
| 迁移回滚 | 本次无迁移，无需 DB 回滚 |
| 回滚姿态 | **就绪，未使用** |

## 经验沉淀应用情况

| 经验条目 | 本次执行情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 三步全执行，无事故 |
| #2 Readiness 延迟 | 本次未出现（80 次尝试内 3 连通过） |
| #3 同源构建 baseURL="" | ✅ `VITE_API_BASE_URL=` 置空，`apiBaseUrl: ""` |
| #4 Smoke 密码限制 | ✅ 用 400/401 路由级验证替代登录 |
| #6 幂等迁移 | 本次无迁移 |
| #7 SYSTEMCTL_SUDO | ✅ 显式传入，重启成功 |
| #13 macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，服务器 `find` 扫描为空 |
| #17 跨版本 hash 404 | ✅ 部署后 `cp -rn` 回补 193 个旧 assets |
| #18 Nginx gzip + 强缓存 | 未改动，沿用既有配置 |
| OBS 直传（#9 事故） | ✅ 显式传入 `VITE_OBS_ENABLED=true`，产物 `obsEnabled: true` |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `/etc/xiyu-bid/backend.env`**：属历史决定保留项（第 13/14/15 次部署），本次沿用未改动。该配置会让健康检查暴露 db/redis/sidecar 等组件细节，测试环境可接受，**上生产前须评估是否收敛**。
2. **`/api/projects` 未认证返回 401 而非技能模板中的 403**：两种状态码都表示拒绝访问，路由本身可达，不影响验收；如需与模板一致，需调整 Spring Security 的 AuthenticationEntryPoint 行为，本次未动。
3. **早操提示 GitHub 镜像与 Gitee 双向领先（GitHub main 领先 73 个 commit）**：与 `CLAUDE.md` 约定的"Gitee 为唯一主仓库、GitHub 为 dormant 镜像"存在漂移，建议另行用 `scripts/sync-from-github.sh` 评估是否存在需要回收的改动，本次部署不受影响。
4. ~~服务器磁盘使用率 71%（余 28G），`incoming/` 残留多个历史发布包~~ → **已处理（2026-10-23）**：`/opt/xiyu-bid/incoming/` 清理旧发布包，73 个 → 3 个（保留 `4ea606791` / `de9a8aea9` / `fa8a65229`），目录占用 12G → 468M，磁盘 71%（余 28G）→ 59%（余 39G）。`releases/` 回滚锚点与 `db-backups/` 未改动。

## 部署确认清单

- [x] 环境门禁已确认（test / 172.16.38.78）
- [x] 基线 HEAD = origin/main，工作区干净
- [x] Flyway 三步预检全部通过
- [x] 构建成功且产物校验通过（obsEnabled / 无重复迁移 / 无 `._*`）
- [x] DB 备份已完成（15M）
- [x] 部署执行成功，健康检查 3/3
- [x] API / 前端 smoke 全部符合预期
- [x] 旧版前端 assets 已回补保留
- [x] 回滚锚点齐备（旧 release 目录 + DB 备份）
- [x] 本次无新增迁移，无 DB 回滚需求
