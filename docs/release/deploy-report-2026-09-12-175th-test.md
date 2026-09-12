# 第 175 次部署报告（test 环境）

**日期**：2026-09-12
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`69dbd8743`
**部署时间**：20:40 - 20:45 CST
**状态**：✅ 成功

## 部署概览

- 增量 commit 自上次部署 `8665d6758`（第 174 次），**无新增 Flyway 迁移**。
- 主要变更：
  - **Sidecar 容器鉴权**（PR !2629，origin `trae/enable-sidecar-auth`）：`fix(deploy)` 容器透传 `SIDECAR_SHARED_KEY` 启用鉴权。
  - **scoreparse 声明总分判定**（PR !2630，origin `codex/scoreparse-1381-total-source`）：`fix(scoreparse)` 以结构化汇总行判定声明总分。
  - **第 174 次部署报告合入**（`ae81d1ca4`，docs only）。
- 本次**无新增 Flyway 迁移**（DB 最高版本维持 V1214）。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `69dbd8743`（origin/main，本地 HEAD 已一致）             |
| 前次部署      | `8665d6758`（2026-09-12 第 174 次部署）                   |
| 增量 commit | `8665d6758..69dbd8743`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 锚点分支 `agent/trae-init`，HEAD = origin/main = `69dbd8743`，工作区干净 |

## 增量 commit 变更（`8665d6758..69dbd8743`）

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `ae81d1ca4` | PR !2628 | docs(release): 第 174 次测试环境部署报告 |
| `5395558f4` | PR !2629 | fix(deploy): sidecar 容器透传 SIDECAR_SHARED_KEY 启用鉴权 |
| `dd8606942` | PR !2630 | fix(scoreparse): 以结构化汇总行判定声明总分 |

## 变更文件范围（22 个）

- `backend/src/`（12）：sidecar 鉴权配置透传 + scoreparse 声明总分判定逻辑
- `scripts/release/`：sidecar 鉴权相关部署脚本
- `specs/059-score-parse-evidence-pipeline/`（3）
- 文档：`.wiki/` / `docs/release/` / `docs/lessons/` / `docs/exec-plans/`

## Flyway 预检结果

- **Step 1**：服务器 `flyway-repair-runner.sh validate` → `VALIDATE OK - all checksums match`（274 migrations）
- **Step 2**：DB 已应用最高版本 `V1214` = 仓库源码最高 `V1214`，两侧一致，无漂移
- **Step 3**：`remote-deploy.sh` 内置 validate 通过（`VALIDATE OK`）

## 部署步骤

1. 环境门禁：用户确认部署到 test（172.16.38.78）
2. 早操：`sync-env.sh` 在锚点分支被守卫拦截 → 手动 `git fetch origin main` 确认 HEAD = origin/main = `69dbd8743`，工作区干净
3. 服务器现状：`deployed-release.json` = `8665d6758`（10:58:57Z），health UP
4. Flyway 预检 3 步法通过（无新迁移）
5. 本地打包：`RELEASE_ID=69dbd8743 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS（36.5s）
6. 产物校验：`obsEnabled=true`、jar 内迁移 273 个无重复、前端入口 `index-DAgz5x1Y.js`、归档 155M
7. scp 归档 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）→ **Sidecar 源码有变动**（8a46edb24 → bcc2f4af3），自动重建镜像 + 重启容器；后端重启成功，健康检查通过（3/3，80 attempts）
8. 前端资源保留：`deployed-release.json` 的 releaseDir 已指向新版本，从上一版本 `8665d6758/frontend/assets` 手动 `cp -rn` 保留旧 assets（126 个文件）

## 验证结果

| 检查项 | 结果 |
| ----- | --- |
| `/actuator/health` | ✅ 200 |
| `/actuator/health/readiness` | ✅ 200 |
| `POST /api/auth/login`（空 body） | ✅ 400（参数校验错误，路由正常） |
| `GET /api/projects` | ✅ 403（需认证） |
| `GET /api/integration/crm/health` | ✅ 401（需认证） |
| 前端 `/` | ✅ 200 |
| 前端 `/login` | ✅ 200 |
| 前端入口 JS | ✅ `assets/index-DAgz5x1Y.js`（与 release 一致） |
| Sidecar 健康 | ✅ UP（`http://127.0.0.1:8000/health`，容器已重建） |

> 本轮 readiness 立即返回 200，未出现 Kafka SDK 延迟 503 的历史已知行为。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（backend.env）——**历史部署中 3 次由用户决定保留的已知白名单项**，非本次引入，按既有决定保留。

## 回滚信息

- 回滚锚点：上一版本 release 目录 `/opt/xiyu-bid/releases/8665d6758/`（前端 assets 已保留在 `/srv/www/xiyu-bid/assets/`）
- 回滚命令：`remote-deploy.sh` 重跑上一个 Release ID；数据库无迁移变更，无回滚风险
- 回滚 Owner：本次未执行，待人工确认

## 风险提示

- 无数据库结构变更，无数据回滚风险
- Sidecar 容器已重建（新增鉴权 `SIDECAR_SHARED_KEY`），建议回归验证文档转换功能（侧边文档转换链路）
- scoreparse 变更集中在声明总分判定，建议回归 1381 评审用例

## 部署确认清单

- [x] 环境门禁（test 172.16.38.78 已确认）
- [x] 早操同步（HEAD = origin/main）
- [x] Flyway 预检 3 步通过
- [x] 打包 + 产物校验（OBS 启用、迁移无重复）
- [x] 健康检查 + Smoke 全部通过
- [x] 前端资源保留 + Sidecar 重建验证
- [x] 部署报告生成
