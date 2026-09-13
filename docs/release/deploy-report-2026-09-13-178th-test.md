# 第 178 次部署报告（test 环境）

**日期**：2026-09-13
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`4f0dd644e`
**部署时间**：12:03 - 12:06 CST
**状态**：✅ 成功

## 部署概览

- 增量 commit 自上次部署 `289e26baa`（第 177 次，含 PR !2636/!2637/!2638），**无新增 Flyway 迁移**（DB 最高版本维持 V1214）。
- 主要变更：
  - **scoreparse 保留合并后首叶子表**（PR !2641，`fix(scoreparse): preserve merged origin first leaf`）：`DeterministicScoreFactExtractor` 修复多表格拆分后合并来源的首个叶节点保留逻辑，避免父级来源映射丢失。
  - **Wiki 陈旧日期批量维护**（PR !2640，`docs(wiki)` + `scripts/wiki-build.mjs`/`wiki-fix.mjs`）：刷新 131 处 health_checked 陈旧门禁日期并重建页面索引。
  - **金标夹具补充**（`merged-parent-first-leaf-table-v2.json`）：`GoldenCorpusBaselineTest` 回归锚点覆盖合并父级首叶表场景。
  - docs: 第 177 次部署报告合入 main。

## 基线信息

| 项目 | 值 |
| --- | --- |
| 基线 commit | `4f0dd644e`（origin/main 最新，本地已 ff 同步） |
| 前次部署 | `289e26baa`（2026-09-13，第 177 次部署） |
| 增量 commit | `289e26baa..4f0dd644e` |
| 新增迁移 | 无 |
| 工作区状态 | 锚点分支 `agent/trae-init` 已 ff-only 同步至 origin/main；重复暂存文件已清理 |

## 增量 commit 变更（`289e26baa..4f0dd644e`）

| commit | 来源 | 描述 |
| --- | --- | --- |
| `447be1fe5` | PR !2641 | fix(scoreparse): preserve merged origin first leaf |
| `036ee1774` | PR !2640 | docs(wiki): 批量维护刷新 health_checked 日期与页面索引 |
| `6fc214285` | PR !2640 | auto-merge（agent/gemini/fix-wiki-stale-dates） |
| `9d2e4f863` | PR !2641 | auto-merge（agent/codex/scoreparse-1377-merged-origin） |
| `9544c35a4`/`4f0dd644e` | docs | 第 177 次部署报告 |

## Flyway 预检结果

- **validate**：✅ `VALIDATE OK - all checksums match`（274 migrations）
- **DB 已应用版本**：最高 V1214（`track opportunity event publish`，2026-09-10）与 source 最新一致
- **新增迁移**：无（本次无 schema 变更，无需执行新迁移）

## 部署步骤

1. 早操三连 + ff-only 同步至 `origin/main`（`4f0dd644e`）
2. 本地打包：`RELEASE_ID=4f0dd644e VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh`（同源构建 + OBS 直传）
3. 产物校验：OBS `obsEnabled=true`、`apiBaseUrl=""`、前端入口 `index-DzwHnHa1.js`、jar 内 273 个迁移无重复
4. 上传 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`，含 DB 备份 + Flyway validate 预检 + 健康等待）
5. 前端资源保留（从上一版本 `289e26baa` assets `cp -rn` 补回旧 hash 文件）

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 后端健康检查 | ✅ UP（remote-deploy 内 3/3） |
| Flyway validate | ✅ OK |
| readiness | ✅ HTTP 200 |
| 空登录 `/api/auth/login` | ✅ HTTP 400（无凭据，路由可达） |
| `/api/projects` | ✅ HTTP 403（需认证） |
| `/api/integration/crm/health` | ✅ HTTP 401（需认证） |
| 前端 `/` | ✅ HTTP 200 |
| 前端 `/login` | ✅ HTTP 200 |
| 前端入口 | ✅ `assets/index-DzwHnHa1.js` 与 release 一致 |
| gzip 压缩 | ✅ `Content-Encoding: gzip` |
| hash 资源强缓存 | ✅ `Cache-Control: public, immutable, max-age=31536000` |
| 前端资源保留 | ✅ 旧版 `index-Brt3Qysc.js` 保留（共 193 静态资源） |

## 回滚信息

- **回滚方向**：回滚至 `289e26baa`（DB 无新增迁移，回滚仅需重新激活上一版本 jar + 前端，无需 DB 回滚）
- **DB 备份**：已执行（`/opt/xiyu-bid/db-backups/winbid-4f0dd644e-*.sql.gz`）
- **回滚责任人**：待定（本次未触发回滚）

## 经验沉淀应用

- OBS 直传：健康检查显示 `obsEnabled=true` + Detail chunk `.upload(` 调用数=2，双保险生效
- 同源构建 `apiBaseUrl=""` 无 IP 字面量
- `SYSTEMCTL_SUDO=true` 保证服务重启成功
- `COPYFILE_DISABLE=1` 避免 macOS `._*` 残留
- `curl --noproxy '*'` 绕过 Mac 代理 502

## 风险提示

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 为历史已确认保留项（第 13/14/15 次部署决策），非本次新增，继续保留。
- 113 处 wiki 陈旧日期批量维护已合入，若后续 wiki-health-check 有告警已消除。

## 部署确认清单

- [x] 环境门禁（AskUserQuestion 确认 test）
- [x] 早操三连 + 基线同步
- [x] 服务器现状与增量确认
- [x] Flyway 预检（validate + DB 版本对比，无新迁移）
- [x] 本地打包 + 产物校验
- [x] remote-deploy（DB 备份 + validate + 健康等待）
- [x] 前端资源保留
- [x] 健康 + Smoke + 配置清理检查
- [x] 部署报告生成