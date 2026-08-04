# 第 117 次测试环境部署报告

- **环境**：测试（test）
- **日期**：2026-08-04
- **Release ID**：`185f562e4`
- **上一版本**：`c8cec7a16`（第 116 次）

## 部署概览

| 项目 | 值 |
|------|-----|
| 目标服务器 | `172.16.38.78`（winbid-01） |
| 部署前健康状态 | UP |
| 增量 commit 数 | 31 |
| 新 Flyway 迁移 | V1184（`create_performance_export_task`） |
| 打包方式 | 同源构建（`VITE_API_BASE_URL=`） |
| OBS 直传 | 已启用 |
| 部署结果 | 成功 |

## 基线信息

| 项目 | 值 |
|------|-----|
| 源分支 | `agent/trae-init`（与 origin/main ff-only 同步） |
| HEAD commit | `185f562e468d7c4cfca4ddfd0b4a983cf6213226` |
| 工作区状态 | 干净（仅未跟踪的 uploads/ 目录） |

## PR 列表

| PR | 简述 |
|----|------|
| !2250 | feat(performance): 业绩合订本 Word 导出功能 — CO-602（含 V1184 迁移） |
| !2254 | fix(performance): P2 #13-#23 修复 + useAsyncTask 轮询修复 — CO-602 |
| !2255 | docs(lessons): PR !2250 评审修复知识沉淀 — CO-602 |
| !2256 | fix(performance): 补全 @Auditable 审计注解 — CO-602 P2 #14 |
| !2257 | fix(project): 项目页面部门显示始终用实时反查覆盖历史快照 — CO-602 |
| !2258 | fix(audit): 修复导出端点 @Auditable 被白名单静默丢弃 — CO-602 |
| !2259 | docs(lessons): 审计 action 命名白名单陷阱 — CO-602 |
| !2260 | wiki: 新建 audit-whitelist-pitfalls 页 + 回填索引 — CO-602 |

## 改动范围

- **后端**：
  - `ProjectQueryService.java`：去掉 `isBlank` 前置条件，始终用实时部门反查覆盖 `tender.department` 历史快照（修复生产 06442 调岗场景部门显示错误）
  - 业绩合订本 Word 导出功能（异步任务 + 四级标题 + 央企共享优化）
  - `@Auditable` 审计注解补全 + 导出端点白名单修复
  - `useAsyncTask` 轮询连续失败 5 次才停轮
- **前端**：
  - `PerformanceBundleExportDialog.vue`：业绩合订本导出对话框
  - `useAsyncTask.js`：轮询失败重试逻辑修复
  - `formatBytes.js`：字节格式化工具
- **数据库迁移**：
  - V1184：创建 `performance_export_task` 表（对标 `warehouse_export_task`，独立表避免与仓库导出任务混杂）
- **文档**：
  - `lessons-learned.md`：调岗场景教训（#107）+ 审计 action 白名单陷阱
  - `wiki/audit-whitelist-pitfalls.md`：新建页面
  - `engineering-discipline.md`、`vue-gotchas.md`：补充规范
- **测试**：
  - `ProjectQueryServiceTest.java`：新增调岗场景单元测试（模拟生产 06442 事故，15/15 通过）
  - `useAsyncTask.spec.js`、`formatBytes.spec.js`：新增前端单元测试

## Flyway 预检

- **Step 1**：部署前 `flyway-repair-runner.sh validate` → OK（244 migrations，all checksums match）
- **Step 2**：DB 最新版本 V1183（2026-08-02 09:52:53），V1184 待应用（预期）
- **Step 3**：`remote-deploy.sh` 内置 validate 通过

## 部署步骤

1. 环境门禁确认（测试环境 172.16.38.78）
2. 早操同步（锚点分支 `agent/trae-init` ff-only 到 origin/main）+ 基线确认
3. 服务器现状检查（`deployed-release.json`、健康检查 UP）
4. Flyway validate 预检 3 步通过
5. 本地打包（`RELEASE_ID=185f562e4`，`VITE_API_BASE_URL=` 同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`，耗时 32.7s）
6. 产物校验：
   - jar 内 244 迁移文件，V1184 存在，无重复版本
   - `release-metadata.json`：`obsEnabled=true`，`apiBaseUrl=""`
   - 前端入口：`assets/index-D6TsHuhE.js`
   - archive 大小：154M
7. 上传 + `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）
8. 后端重启（systemd，20:54:40 CST），健康检查通过（79 次尝试，3/3 连续成功）
9. 前端资源保留：从 c8cec7a16 release 目录 `cp -rn` 旧 assets 到 `/srv/www/xiyu-bid/assets/`

## 验证结果

| 检查项 | 结果 |
|--------|------|
| `/actuator/health` | 200 UP |
| `/actuator/health/readiness` | 200 UP |
| `/` | 200 |
| `/login` | 200 |
| `POST /api/auth/login` | 400（空密码验证拒绝） |
| `/api/projects` | 403（未认证拒绝） |
| `/api/integration/crm/health` | 401（未认证拒绝） |
| 前端资源一致性 | 通过（`index-D6TsHuhE.js`） |
| V1184 迁移应用 | ✅ 已应用（2026-08-04 20:54:47） |

## GitHub 同步

- 同步前：GitHub 落后 31 个 commit
- 同步后：Gitee main 与 GitHub main 完全一致（`185f562e4`）

## 配置清理检查

- 发现 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留配置，第 13/14/15 次决定保留，非临时调试）
- 无 `DEBUG`/`TRACE` 临时配置

## 回滚信息

- 上一版本 Release ID：`c8cec7a16`
- 回滚方式：`remote-deploy.sh` 重新部署上一版本 release 包
- 回滚风险：低
  - V1184 是 `CREATE TABLE IF NOT EXISTS`（新建表，不影响现有数据）
  - U1184 回滚脚本已存在（`DROP TABLE performance_export_task`）
  - 回滚步骤：先执行 U1184 回滚脚本，再 `remote-deploy.sh` 部署 c8cec7a16 release 包

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（避免启动失败）
- ✅ OBS 直传显式启用（`VITE_OBS_ENABLED=true` 双保险）
- ✅ 同源构建（`VITE_API_BASE_URL=`）
- ✅ `COPYFILE_DISABLE=1` 防止 macOS `._*` 残留
- ✅ `SYSTEMCTL_SUDO=true` 避免 `Interactive authentication required`
- ✅ 前端资源保留（`cp -rn` 旧 assets，防止跨版本 404）
- ✅ Kafka SDK readiness 延迟容忍（79 次尝试属正常范围）

## 风险提示

- V1184 新建 `performance_export_task` 表，首次使用时业绩合订本导出功能需端到端验证
- 本次含 PR !2257 部门实时反查修复，需验证工号 10323（/project/29）和 06442 的部门显示

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操同步 + 基线确认
- [x] 服务器健康检查
- [x] Flyway 预检 3 步
- [x] 本地打包 + 产物校验（OBS + 同源 + 无重复迁移）
- [x] 上传 + 部署（`SYSTEMCTL_SUDO=true`）
- [x] 前端资源保留（cp -rn 旧 assets）
- [x] 健康检查等待（79 次尝试通过）
- [x] V1184 迁移应用验证
- [x] Smoke 验证（7 项全部通过）
- [x] GitHub 镜像同步
- [x] 配置清理检查
- [x] 部署报告生成
