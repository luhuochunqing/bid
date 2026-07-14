# 第 87 次部署报告（测试环境）

## 部署环境

- **环境**：测试 (`test`)
- **主机**：`winbid-01` (`172.16.38.78`)
- **部署时间**：2026-07-14 19:00:29 CST
- **部署人**：trae agent

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `398da5de5-api8080` |
| 基线 commit | `398da5de5` |
| 上一版本 | `56285042f-api8080` (2026-07-13) |
| 增量 commit 数 | 14 |
| 新增 Flyway 迁移 | 无 |
| 包大小 | 153M |
| OBS 直传 | ✅ 已启用 |
| 部署结果 | ✅ 成功 |

## 基线信息

- **分支**：`agent/trae-init`（锚点分支，ff-only 同步到 `origin/main`）
- **HEAD commit**：`398da5de5`
- **GitHub 镜像**：部署后同步完成，两边 main 完全一致

## 增量 PR / Commit 列表

| PR | Commit | 说明 |
|---|---|---|
| !2075 | 398da5de5 | docs(lessons): 沉淀近期 session 工程经验（§56-§59 + CRM §13 + 商机洞察规格）|
| !2077 | 6bc73658c | chore(wiki): refresh health_checked dates to 2026-07-14 |
| !2076 | be6deac9e | feat(tender): 标讯去重规则新增项目类型维度 |
| - | 3f2103c63 | chore(wiki): refresh health_checked dates to 2026-07-14 |
| - | 82c118a01 | feat(tender): 标讯去重规则新增项目类型维度 |
| - | aee8ffe65 | docs(lessons): 沉淀近期 session 工程经验 |
| !2074 | b64f96818 | feat(warehouse): CO-582 新增仓库信息导出 Word 合订本能力 |
| - | 2b5642513 | fix(warehouse): CO-582 修复 PR-2074 Code Review 问题 |
| - | 29b017f66 | feat(warehouse): CO-582 新增仓库信息导出 Word 合订本能力 |
| !2073 | 59dc10484 | docs(release): 第 10 次生产环境部署报告 |
| - | 8eb68dfdd | docs(release): 第 10 次生产环境部署报告 |
| !2072 | 3fde377c4 | fix(project-doc): OBS 直传招标文件下载 404 修复 |
| - | 8cfd35a77 | fix(project-doc): 修复 MIME 类型默认文件名扩展名推断错误 |
| - | ace839d49 | fix(project-doc): OBS 直传招标文件下载 404 修复 |

## 主要变更范围

1. **CO-582 仓库信息导出 Word 合订本**（!2074）：新增仓库信息批量导出为 Word 合订本的能力
2. **标讯去重规则新增项目类型维度**（!2076）：标讯去重规则新增项目类型维度
3. **OBS 直传招标文件下载 404 修复**（!2072）：修复 MIME 类型默认文件名扩展名推断错误，修复 OBS 直传招标文件下载 404
4. **工程经验沉淀**（!2075）：沉淀近期 session 工程经验到 lessons-learned.md §56-§59、CRM §13、商机洞察规格

## Flyway 预检结果

- ✅ Step 1: `flyway-repair-runner.sh validate` 通过（228 migrations, all checksums match）
- ✅ Step 2: DB 最新已应用版本 `V1165`（add bid system admin role, 2026-07-11）
- ✅ Step 3: remote-deploy.sh 内置 validate 通过
- ✅ 无新增迁移文件

## 部署步骤

1. ✅ 环境门禁：用户确认部署到测试环境 172.16.38.78
2. ✅ 早操三连：sync-env.sh ff-only 同步到 origin/main
3. ✅ 服务器现状：deployed-release.json 显示上一版本 56285042f-api8080，health UP
4. ✅ Flyway 预检 3 步法通过
5. ✅ 本地打包：`RELEASE_ID=398da5de5-api8080 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
6. ✅ 产物校验：obsEnabled=true, 227 迁移文件无重复, .upload( 调用数=2
7. ✅ 上传 + 部署：scp + remote-deploy.sh (SYSTEMCTL_SUDO=true)
8. ✅ 前端资源保留：从 56285042f-api8080 保留 assets，177 → 254 文件
9. ✅ 健康检查：health UP（含 readinessState）
10. ✅ Smoke 测试：7 项全部通过
11. ✅ GitHub 镜像同步：两边 main 完全一致

## 产物校验

| 检查项 | 结果 |
|---|---|
| `release-metadata.json` obsEnabled | `true` |
| `apiBaseUrl` | `""` (同源构建) |
| jar 内迁移文件数 | 227 (无重复) |
| 前端入口 | `assets/index-Cb5KSg_0.js` + `assets/index-Cmq0rLNS.css` |
| Detail chunk `.upload(` 调用数 | 2（OBS 直传未被 tree-shake）|
| archive 大小 | 153M |

## 验证结果

### 健康检查

```
{"status":"UP","components":{
  "aiProvider":UP, "db":UP, "diskSpace":UP, "jwt":UP,
  "livenessState":UP, "ping":UP, "readinessState":UP,
  "redis":UP, "sidecar":UP
}}
```

### Smoke 测试（经 Nginx 8080 代理）

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 | 200 | ✅ |
| `/actuator/health/readiness` | 200 | 200 | ✅ |
| `/api/auth/login` (空 body) | 400 | 400 | ✅ |
| `/api/projects` (无认证) | 403 | 403 | ✅ |
| `/api/integration/crm/health` (无认证) | 401 | 401 | ✅ |
| 前端首页 `/` | 200 | 200 | ✅ |
| 前端 `/login` | 200 | 200 | ✅ |
| 前端入口 `index.html` | 与 release 一致 | `assets/index-Cb5KSg_0.js` | ✅ |

## 已知现象：Kafka SDK readiness 延迟

- **现象**：后端 19:00:29 启动后，`/actuator/health` 持续返回 503 约 4 分钟
- **根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程导致 readiness 延迟恢复
- **恢复**：19:04:32 后 readiness 自恢复为 UP，所有组件正常
- **历史出现**：第 8、9、10、13、15 次均出现，已沉淀为已知行为，无需回滚
- **remote-deploy.sh 警告**：健康检查脚本在 4 分钟窗口内（120 次 × 2 秒）未捕获到 3 次连续成功，但服务实际已恢复

## GitHub 镜像同步

- 部署前：GitHub 镜像落后 Gitee 14 commit
- 同步命令：`bash scripts/sync-to-github.sh`
- 部署后：Gitee main = GitHub main = `398da5de5b8da3b9e2660cd14f2a847c336ef83b`

## 临时调试配置检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留（第 13/14/15 次均决定保留，便于排障）
- 无其他临时 DEBUG/TRACE 配置

## 回滚信息

- **回滚命令**：
  ```bash
  ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/56285042f-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'
  ```
- **回滚 posture**：ready（无需执行）
- **DB 状态**：无新增迁移，回滚无需恢复数据库

## 经验沉淀应用

本次部署应用了以下经验：
1. ✅ Flyway 预检 3 步法（第 6 次事故后建立）
2. ✅ OBS 直传显式传入 `VITE_OBS_ENABLED=true`（第 84 次测试 + 第 8 次生产事故后建立）
3. ✅ 同源构建 `VITE_API_BASE_URL=`（第 3 次事故后建立）
4. ✅ `COPYFILE_DISABLE=1` 避免 macOS `._*` 残留（第 10 次发现）
5. ✅ `SYSTEMCTL_SUDO=true`（第 15 次发现，PR !1324 修复）
6. ✅ 前端资源保留防止跨版本 404（第 18 条经验）
7. ✅ `curl --noproxy '*'` 绕过 Mac HTTP_PROXY（第 19/23 次发现）
8. ✅ Kafka SDK readiness 延迟已沉淀为已知行为（第 8 次后建立）

## 风险提示

- 无新增风险
- Kafka SDK readiness 延迟现象持续存在（已知行为，非阻塞）

## 部署确认清单

- [x] 环境门禁通过
- [x] 早操三连通过
- [x] Flyway 预检 3 步法通过
- [x] 产物校验通过（OBS、同源、迁移文件、前端入口）
- [x] 健康检查 UP
- [x] Smoke 测试 7 项全部通过
- [x] 前端资源保留
- [x] GitHub 镜像同步
- [x] 临时配置检查
- [x] 部署报告生成
