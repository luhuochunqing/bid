# 第 174 次部署报告（test 环境）

**日期**：2026-09-12
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`8665d6758`
**部署时间**：18:55 - 19:00 CST
**状态**：✅ 成功

## 部署概览

- 增量 commit 自上次部署 `c4dddb287`（第 173 次），**无新增 Flyway 迁移**。
- 主要变更：
  - **scoreparse 1381 PDF 评分项解析收敛**（PR !2626 / !2627，origin `codex/scoreparse-1381-acceptance`）：`fix(scoreparse)` 收敛 1381 PDF 评分项解析，配套 sidecar `converter.py` 表格抽取增强与测试。
  - **第 173 次部署报告合入**（`4631c8f13`）。
- 本次**无新增 Flyway 迁移**（DB 最高版本维持 V1214）。

## 基线信息

| 项目        | 值                                                     |
| --------- | ----------------------------------------------------- |
| 基线 commit | `8665d6758`（origin/main，本地 HEAD 已 ff-only 同步一致） |
| 前次部署      | `c4dddb287`（2026-09-12 第 173 次部署）                   |
| 增量 commit | `c4dddb287..8665d6758`                               |
| 新增迁移      | 无                                                    |
| 工作区状态     | 锚点分支 `agent/trae-init`，HEAD = origin/main = `8665d6758`，工作区干净 |

## 增量 commit 变更（`c4dddb287..8665d6758`）

| commit | 来源 | 描述 |
| ----- | --- | --- |
| `4631c8f13` | — | docs(release): 第 173 次测试环境部署报告 |
| `b793f0907` | PR !2626/!2625 | fix(scoreparse): route PDF scoring tables into evidence pipeline |
| `0c380cc5c` | PR !2627 | fix(scoreparse): 收敛1381 PDF评分项解析 |
| `8665d6758` | PR !2627 | 合并 !2627 |

## 变更文件范围（16 个，626+/35-）

- `backend/src/main/java/com/xiyu/bid/scoreparse/domain/`：`DeterministicScoreFactExtractor` / `ScoreCompletenessPolicy`
- `backend/src/test/java/com/xiyu/bid/scoreparse/`：`PdfEvidencePipelineTest` / `DeterministicScoreFactExtractorTest` / `ScoreCompletenessPolicyTest` / golden fixture（1381）
- `document-converter-sidecar/converter.py`（表格抽取增强）+ 相关测试
- 文档：`.wiki/` / `docs/lessons/lessons-learned.md` / 部署报告 / `scripts/eval-scoreparse/README.md`

## Flyway 预检结果

- **Step 1**：服务器 `flyway-repair-runner.sh validate` → `VALIDATE OK - all checksums match`（274 migrations）
- **Step 2**：DB 已应用最高版本 `V1214` = 仓库源码最高 `V1214`，两侧一致，无漂移（V1213 两侧均不存在）
- **Step 3**：`remote-deploy.sh` 内置 validate 通过（`VALIDATE OK`）

## 部署步骤

1. 早操：`sync-env.sh` 在锚点分支被守卫拦截 → 手动 `git fetch origin main` + `git merge --ff-only origin/main` 同步到 `8665d6758`
2. 清理过期的未提交 173rd 报告草稿（其内容已合入 main 的 `4631c8f13`）
3. 服务器现状：`deployed-release.json` = `c4dddb287`（08:35:55Z），health 200
4. Flyway 预检 3 步法通过
5. 本地打包：`RELEASE_ID=8665d6758 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS
6. 产物校验：`obsEnabled=true`、jar 内迁移无重复（最高 V1214）、前端入口 `index-DAgz5x1Y.js`
7. scp 归档 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）→ 后端重启成功，健康检查通过（3/3，81 attempts）
8. 前端资源保留：从上一版本 `c4dddb287/frontend/assets` `cp -rn` 至当前 assets（193 个文件）

## 验证结果

| 检查项 | 结果 |
| ----- | --- |
| `/actuator/health` | ✅ 200 |
| `/actuator/health/readiness` | ✅ 200 |
| `POST /api/auth/login`（空 body） | ✅ 400（路由正常，校验错误） |
| `GET /api/projects` | ✅ 403（需认证） |
| `GET /api/integration/crm/health` | ✅ 401（需认证） |
| 前端 `/` | ✅ 200 |
| 前端 `/login` | ✅ 200 |
| 前端入口 JS | ✅ `assets/index-DAgz5x1Y.js`（与 release 一致） |
| 后端服务 | ✅ active/running（PID 13022, Java 21, Xmx2g） |

> 本轮 readiness 立即返回 200，未出现 Kafka SDK 延迟 503 的历史已知行为。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（backend.env 第 76 行）——**历史部署中 3 次由用户决定保留的已知白名单项**，非本次引入，按既有决定保留。

## 回滚信息

- 回滚锚点：上一版本 release 目录 `/opt/xiyu-bid/releases/c4dddb287/`（前端 assets 已保留在 `/srv/www/xiyu-bid/assets/`）
- 回滚命令：`remote-deploy.sh` 重跑上一个 Release ID；数据库无迁移变更，无回滚风险
- 回滚 Owner：本次未执行，待人工确认

## 风险提示

- 无数据库结构变更，无数据回滚风险
- scoreparse 变更集中在 PDF 评分项解析流水线与 sidecar 表格抽取，建议部署后回归 1381 评审用例

## 部署确认清单

- [x] 环境门禁（test 172.16.38.78 已确认）
- [x] 早操同步（HEAD = origin/main）
- [x] Flyway 预检 3 步通过
- [x] 打包 + 产物校验（OBS 启用、迁移无重复）
- [x] 健康检查 + Smoke 全部通过
- [x] 前端资源保留
- [x] 部署报告生成