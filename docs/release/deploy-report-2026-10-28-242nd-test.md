# 第 242 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-28 15:33 (UTC+8) |
| 部署方式 | `package-release.sh` + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`） |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `df41c64a7-api8080` |
| 部署分支 | 打包在 `agent/trae-init`（锚点分支，纯 ff-only 同步）；修复代码在 `agent/trae/fix-ai-ocr-vmodel` |
| HEAD Commit | `df41c64a7`（= PR !2902 squash 合并结果） |
| 旧版本 | `8c219056d`（第 241 次，2026-10-28 13:48） |
| 构建包 | `.release/xiyu-bid-release-df41c64a7-api8080.tar.gz`（155M，MD5 `a43b49b7fec5fa47d045a56040af41d1`） |
| 构建时间 | 2026-10-28T07:32:56Z（15:32:56 UTC+8） |
| 增量 | 13 个 commit，40 个文件（+1971 / -4）：backend 26 / frontend 6 / specs 3 / wiki 4 / docs 1 |
| 激活时间 | 2026-10-28 15:33:47 (UTC+8) |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-df41c64a7-20261028153337.sql.gz`（15M） |

## 部署前置缺陷（本次新增处置）

首次打包时前端 `vite build` 直接失败，`origin/main`（`a0c398eee`）不可构建：

```
[vite:vue] v-model cannot be used on a prop, because local prop bindings are not writable.
src/views/System/settings/AiModelSettingsPanel.vue:120
```

- **根因**：`AiModelSettingsPanel.vue` 第 133 行为裸 `defineModel('baiduOcrConfig', ...)`，未用 `const` 接收。Vue 编译器把模板中的 `baiduOcrConfig` 判定为 prop 而非可写 setup 绑定，`v-model="baiduOcrConfig"` 编译期报错。仓库内同类写法（`AssemblyPanel.vue`、`SectionTreePanel.vue`）均用 `const` 接收。
- **引入提交**：`279a935b6 feat(settings): 支持在前端可视化配置百度表格OCR引擎及连通性测试 (方案 A)`
- **修复**：1 行改动 → `const baiduOcrConfig = defineModel('baiduOcrConfig', { type: Object, required: true })`
- **PR**：!2902（分支 `agent/trae/fix-ai-ocr-vmodel`，commit `e8b6aad49`），已 squash 合并为 `df41c64a7`
- **验证**：修复前 `npm run build:api` 失败；修复后同一命令通过（built in 9.61s）

## PR 列表（增量 13 个 commit，自 8c219056d..df41c64a7）

| Commit | 说明 |
|---|---|
| `df41c64a7` | **!2902** fix(settings): 修复 AiModelSettingsPanel 裸 defineModel 导致的 v-model 编译失败 |
| `e8b6aad49` | fix(settings): 同上（PR 内提交） |
| `a0c398eee` | **!2900** feat(scoreparse): 实现百度智能云表格识别旁路对照管线与模型适配 |
| `279a935b6` | feat(settings): 支持在前端可视化配置百度表格 OCR 引擎及连通性测试（方案 A） |
| `15c721a85` | docs(wiki): 补充百度表格识别旁路管线架构知识库 |
| `e03eaf95d` | feat(scoreparse): 百度智能云表格识别旁路对照管线与模型适配 |
| `b5e6472b1` | **!2901** fix(scoreparse): 恢复评分项原文名称 |
| `819121063` | docs(wiki): 记录评分项名称恢复根因 |
| `dff66be7a` | test(scoreparse): 增加泛化项名金标回放 |
| `46a346837` | fix(scoreparse): 恢复评分项原文名称 |
| `750000da7` | **!2899** docs(release): 第 241 次测试环境部署报告 (test) |
| `7d5abe00b` | docs(release): 修正第 241 次部署报告评审意见 |
| `b6b9a74cd` | docs(release): 第 241 次测试环境部署报告 |

## 改动范围

- **数据库迁移**：无新增迁移（DB 停在 V1225，源码最大版本同为 V1225）
- **后端主源码/测试**：26 个文件，集中在 `scoreparse` 模块（百度智能云表格识别旁路对照管线、模型适配、评分项原文名称恢复、泛化项名金标回放）
- **前端**：6 个文件 —— `src/api/modules/settings.js`、`src/views/System/AiModelSettings.vue`、`AiModelSettingsPanel.vue`、`BaiduOcrSettingsCard.vue`、`useAiModelSettings.js`、`useAiModelSettings.spec.js`
- **Specs**：3 个文件（百度表格识别旁路相关 feature 的 spec/plan/tasks）
- **Wiki**：4 个文件（百度表格识别旁路管线架构、评分项名称恢复根因）
- **文档**：1 个部署报告（第 241 次 test）
- **Sidecar**：无变更（`sidecarSha256` = `c06803d7...`，与上一版本一致）

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1：服务器 `flyway-repair-runner.sh validate` | ✅ `VALIDATE OK - all checksums match`（285 个版本 = 284 个 `V*.sql` + `B73` 基线） |
| Step 2：DB 已应用版本 vs 源码最新版本 | ✅ DB 最新 V1225，源码 V1225，无 pending |
| Step 3：remote-deploy 内置 validate | ✅ 通过（激活新 jar 前执行，15:33:44） |
| 打包后 jar 内迁移校验 | ✅ `BOOT-INF/classes/db/migration-mysql/V*.sql` 共 284 个，无重复版本号 |

## 部署步骤

1. 环境门禁：`AskUserQuestion` 确认 `ENV=test` / 172.16.38.78
2. 早操：`sync-env.sh`（init 分支 ff-only，本地门禁 7/7 通过）+ 基线确认 `origin/main` @ `a0c398eee`，工作区干净
3. 服务器现状：旧版本 `8c219056d`、health UP、服务 active
4. 增量检查：11 commit，无迁移变更
5. Flyway 预检 3 步（validate 一次通过）
6. **打包失败 → 定位 main 前端编译缺陷 → 开任务分支修复（PR !2902）→ 合并 → 重新同步基线**
7. 本地打包：`VITE_API_BASE_URL=`（同源）+ `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1` + `mvn clean -DskipTests package`
8. 产物校验：`obsEnabled=true`、`.upload(`=2、`apiBaseUrl=""`、前端入口 `index-HK-_YaGO.js`
9. 上传 + 远程部署：`SYSTEMCTL_SUDO=true`，激活前自动 DB 备份 + Flyway validate
10. 旧版本前端 assets 补齐（`8c219056d` → 当前，防旧标签页跨版本 404）
11. 健康检查 + 迁移验证 + 外部 Smoke + 临时配置检查

## 验证结果

### 服务与健康

| 检查项 | 结果 |
|---|---|
| systemd `xiyu-bid-backend` | ✅ active (running)，15:33:47 启动 |
| `/actuator/health`（本机 18080） | ✅ UP（连续 3/3，共 80 次探测，约 2.7 分钟） |
| 健康组件 | ✅ `aiProvider / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar` 全部 UP |
| 错误日志（近 10 分钟，priority=err） | ✅ 无记录 |

> 健康检查耗时约 2.7 分钟，属已沉淀的 Kafka SDK readiness 延迟已知行为（第 8/9/10/13/15 次同类现象），非本次缺陷。

### 外部 Smoke（经 Nginx 8080，`--noproxy '*'`）

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ HTTP 200 |
| `/actuator/health/readiness` | ✅ HTTP 200（未出现 503） |
| `/`（前端首页） | ✅ HTTP 200 |
| `/login` | ✅ HTTP 200 |
| `POST /api/auth/login`（空包） | ✅ HTTP 400（参数校验生效） |
| `GET /api/projects`（未认证） | ✅ HTTP 401（认证守卫生效） |
| `GET /api/integration/crm/health`（未认证） | ✅ HTTP 401 |
| 前端入口 | ✅ `assets/index-HK-_YaGO.js`，与 release 一致 |

### OBS 直传

- ✅ `release-metadata.json` → `obsEnabled: true`
- ✅ Detail chunk `.upload(` 调用数 = 2

### 前端资产保留

- 部署后从上一版本 release 目录（`8c219056d`）的 `frontend/assets` 执行 `cp -rn` 补齐
- `/srv/www/xiyu-bid/assets` 共 277 个文件
- `._*` macOS 残留文件已清理

### 迁移应用验证

- ✅ `flyway_schema_history` 最新成功记录：V1225 `score estimate freshness`，success=1，installed_on = 2026-10-26 20:01:47
- `failed_count` = 0（`success=1` 过滤查询无失败记录）
- 本次无新增迁移，DB 结构未变化

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点（代码） | `8c219056d` |
| 回滚 jar | `/opt/xiyu-bid/releases/8c219056d/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/8c219056d/frontend/` |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚；如数据异常可用 `/opt/xiyu-bid/db-backups/winbid-df41c64a7-20261028153337.sql.gz` |
| 回滚操作 | 以 `8c219056d` 为 RELEASE_ID 重跑打包 + `remote-deploy.sh`（或直接回切 releases 目录内旧 artifact） |
| 回滚判断点 | 健康检查失败 / 登录不可用 / 项目或标讯核心路径阻断 / AI 模型设置页（本次改动区域）白屏或报错 |

## 经验沉淀应用情况

| 教训 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，`success=1` 过滤查询 |
| 同源构建（baseURL=""） | ✅ 显式 `VITE_API_BASE_URL=` |
| OBS 直传漏传 | ✅ `VITE_OBS_ENABLED=true` + 产物双校验 |
| systemctl sudo（第 15 次） | ✅ `SYSTEMCTL_SUDO=true` |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，部署后清理 |
| 前端 hash 资产跨版本 404 | ✅ 部署后 `cp -rn` 补齐（277 个文件） |
| Mac HTTP_PROXY 502 | ✅ 所有 curl 加 `--noproxy '*'` |
| 前端目录属主（第 10 次） | ✅ remote-deploy 自动 `sudo chown -R jetty` 修复非 jetty 属主条目 |
| **打包失败即阻断 rollout（新增）** | ✅ 前端构建失败后未继续部署，先修复 main 再重打包 |

## 风险提示

1. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `backend.env`**：历史用户决定保留的配置，非本次新引入，未清理。生产上线前需确认是否收敛。
2. **完整登录 Smoke 未执行**：admin 密码未知，采用 400/401 替代验证接口路由与守卫（与历史多次一致）。
3. **前端产物有变更**：本次含 AI 模型设置页重构（`AiModelSettingsPanel` / `BaiduOcrSettingsCard` / `useAiModelSettings`），入口 `index-HK-_YaGO.js` 与上一版本不同；已补齐旧 assets，旧标签页仍建议刷新。需重点回归「系统设置 → AI 模型配置」页面。
4. **服务器 `/tmp/migration-mysql/` 目录可能过时**：validate 已通过，不影响本次部署。
5. **main 曾处于不可构建状态**：`279a935b6` 合入后 `origin/main` 前端 build 失败，说明该提交的本地门禁未覆盖 `npm run build`。建议将 `npm run build`（或 `build:api`）纳入 pre-push gate，避免同类缺陷再次直达 main。

## 部署确认清单

- [x] 环境门禁确认（ENV=test / 172.16.38.78）
- [x] 基线干净（HEAD = origin/main = `df41c64a7`）
- [x] Flyway 预检 3 步通过
- [x] 产物校验（OBS / 迁移 / 前端入口）
- [x] DB 预部署备份完成
- [x] 服务健康 UP
- [x] 外部 Smoke 全通过
- [x] 前端资产保留完成
- [x] 部署记录已更新（`deployed-release.json` = `df41c64a7-api8080`）
- [x] 部署报告产出
