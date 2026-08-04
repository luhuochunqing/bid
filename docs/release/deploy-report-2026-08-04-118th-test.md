# 第 118 次测试环境部署报告

- **环境**：测试（test）
- **日期**：2026-08-04
- **Release ID**：`223f2a8ef`
- **上一版本**：`185f562e4`（第 117 次）

## 部署概览

| 项目 | 值 |
|------|-----|
| 目标服务器 | `172.16.38.78`（winbid-01） |
| 部署前健康状态 | UP |
| 增量 commit 数 | 14 |
| 新 Flyway 迁移 | 无 |
| 打包方式 | 同源构建（`VITE_API_BASE_URL=`） |
| OBS 直传 | 已启用 |
| 部署结果 | 成功 |

## 基线信息

| 项目 | 值 |
|------|-----|
| 源分支 | `agent/trae-init`（与 origin/main ff-only 同步） |
| HEAD commit | `223f2a8ef8dc1b21ea5b1a848f388b7adc295d86` |
| 工作区状态 | 干净 |

## PR 列表

| PR | 简述 |
|----|------|
| !2261 | docs(release): 第 116-117 次测试环境部署报告 |
| !2262 | docs(lessons): 精简 lessons-learned.md 主文档 + 归档历史性内容 |
| !2263 | fix(organization): 清理部门来源下拉的 ehsy 历史遗留选项 |
| !2264 | docs: wiki health_checked 更新 — lessons-learned 精简后同步 |
| !2265 | perf(organization): 部门树渲染性能优化 — 全局去重 + 默认只展开根节点（CO-605） |
| !2266 | fix(workbench): 修复 workbench-characterization 测试 vue-router mock 缺失 + 断言过期 |

## 改动范围

- **前端**：
  - `OrganizationManagement.vue`：部门树渲染性能优化（核心修复）
    - `buildSubTree` 改用全局共享 `Set` 去重，确保每个 `departmentCode` 只出现一次（修复 359 个重复 `department_code` 导致树节点从 800 膨胀到 22795 的问题）
    - `default-expand-all` 改为 `default-expanded-keys`（仅展开根节点），DOM 元素从 18 万降到几十
  - `OrganizationManagement.vue`：清理部门来源下拉的 `ehsy` 历史遗留选项
  - `workbench-characterization.spec.js`：修复 vue-router mock 缺失 + Pinia store 未 mock + 断言过期
- **文档**：
  - `lessons-learned.md`：精简主文档 + 归档历史性内容
  - `docs/release/deploy-report-2026-08-04-116th-test.md`、`117th-test.md`：补全第 116-117 次部署报告
  - `.wiki/`：wiki health_checked 更新
- **数据库迁移**：无
- **后端**：无业务代码变更

## Flyway 预检

- **Step 1**：部署前 `flyway-repair-runner.sh validate` → OK（245 migrations，all checksums match）
- **Step 2**：DB 最新版本 V1184（2026-08-04 20:54:47，第 117 次部署时应用），本次无新迁移
- **Step 3**：`remote-deploy.sh` 内置 validate 通过（245 migrations validated）

## 部署步骤

1. 环境门禁确认（测试环境 172.16.38.78）
2. 早操同步（锚点分支 `agent/trae-init` ff-only 到 origin/main）+ 基线确认
3. 清理 stale session 锁（PID 14076 实为 launchd 后端服务，PID 复用误判）
4. 服务器现状检查（`deployed-release.json` = 185f562e4，健康检查 UP）
5. Flyway validate 预检 3 步通过（零迁移风险）
6. 本地打包（`RELEASE_ID=223f2a8ef`，`VITE_API_BASE_URL=` 同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`，耗时 33.2s）
7. 产物校验：
   - jar 内 244 迁移文件，无重复版本
   - `release-metadata.json`：`obsEnabled=true`，`apiBaseUrl=""`
   - OBS `.upload(` 调用数 = 2（直传逻辑未被 tree-shake）
   - 前端入口：`assets/index-Nwjpxa6o.js`
   - archive 大小：154M
8. 上传 + `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）
9. 后端重启（systemd，22:53:06 CST），健康检查通过（79 次尝试，3/3 连续成功，Kafka SDK readiness 正常延迟）
10. 前端资源保留：从 185f562e4 release 目录 `cp -rn` 旧 180 个 assets 到 `/srv/www/xiyu-bid/assets/`

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
| 前端资源一致性 | 通过（`index-Nwjpxa6o.js`） |
| Flyway 迁移 | 无新迁移（DB 仍为 V1184） |

## GitHub 同步

- 同步前：GitHub 落后 14 个 commit
- 同步后：Gitee main 与 GitHub main 完全一致（`223f2a8ef8dc1b21ea5b1a848f388b7adc295d86`）

## 配置清理检查

- 发现 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留配置，第 13/14/15 次决定保留，非临时调试）
- 无 `DEBUG`/`TRACE` 临时配置

## 回滚信息

- 上一版本 Release ID：`185f562e4`
- 回滚方式：`remote-deploy.sh` 重新部署上一版本 release 包
- 回滚风险：极低
  - 本次无新 Flyway 迁移，无 DB schema 变更
  - 回滚步骤：直接 `remote-deploy.sh` 部署 185f562e4 release 包即可

## 经验沉淀应用

- ✅ Flyway 预检 3 步法（零迁移风险确认）
- ✅ OBS 直传显式启用（`VITE_OBS_ENABLED=true` 双保险 + 产物校验 `.upload(` 调用数）
- ✅ 同源构建（`VITE_API_BASE_URL=`）
- ✅ `COPYFILE_DISABLE=1` 防止 macOS `._*` 残留
- ✅ `SYSTEMCTL_SUDO=true` 避免 `Interactive authentication required`
- ✅ 前端资源保留（`cp -rn` 旧 180 个 assets，防止跨版本 404）
- ✅ Kafka SDK readiness 延迟容忍（79 次尝试属正常范围）
- ✅ stale session 锁清理（PID 复用误判处理）

## 风险提示

- 本次含 PR !2265 部门树渲染性能优化，需验证组织管理页面加载速度（之前 812 条部门记录 + 359 重复 department_code 导致页面卡顿，修复后应为秒开）
- 本次含 PR !2263 部门来源下拉清理，需验证下拉只显示"全部来源"和"oss"选项（不再有 ehsy）
- 无 DB 迁移风险

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操同步 + 基线确认
- [x] 服务器健康检查
- [x] Flyway 预检 3 步（零迁移风险）
- [x] 本地打包 + 产物校验（OBS + 同源 + 无重复迁移）
- [x] 上传 + 部署（`SYSTEMCTL_SUDO=true`）
- [x] 前端资源保留（cp -rn 旧 180 个 assets）
- [x] 健康检查等待（79 次尝试通过）
- [x] Smoke 验证（7 项全部通过）
- [x] GitHub 镜像同步（14 commit → 0）
- [x] 配置清理检查
- [x] 部署报告生成
