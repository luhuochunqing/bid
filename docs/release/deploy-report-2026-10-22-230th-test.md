# 第 230 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-22 18:34 (UTC+8) |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `de9a8aea9` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit（本次部署的代码版本） | `de9a8aea9` |
| 旧版本 | `fa8a65229`（第 229 次，2026-10-22 13:24） |
| 构建包 | `.release/xiyu-bid-release-de9a8aea9.tar.gz`（163M） |
| 增量 | 6 个 commit，16 个文件（+616/-6） |
| 激活时间 | 2026-10-22T10:34:41Z（18:34:41 UTC+8） |

## PR 列表（增量 6 个 commit，自 fa8a65229..de9a8aea9）

| Commit | 说明 |
|---|---|
| `c3a92f3a1` | docs(release): 第 229 次测试环境部署报告（021cf93e4 → fa8a65229） |
| `3bf83c169` | docs(release): 按 S6 审查意见修正第 229 次报告口径 |
| `f92869976` | docs(release): 标注第 229 次报告提交的首次/修订区分（S6 round 2 意见） |
| `bc7814949` | **!2843** docs(release): 第 229 次测试部署报告（auto-merge） |
| `57ffd1554` | fix(scoreparse): 附件评分方案识别与权重闭合，修复评分项解析失败 |
| `157627b02` | docs+test(scoreparse): 1391 金标夹具与 wiki/教训回填 |
| `de9a8aea9` | **!2844** fix(scoreparse): 附件评分方案识别与权重闭合，修复评分项解析失败（auto-merge） |

## 改动范围

- **后端（9 文件）**：`backend/src/main/java/com/xiyu/bid/scoreparse/`
  - 新增 `OriginalWeightClosurePolicy`（权重闭合判定）
  - 修改 `OriginalScoringTextScopePolicy`（评分文本范围识别）、`OriginalScoringSchemePolicy`、`OriginalScoreEvidencePolicy`、`OriginalScoringContext`、`ScoreUnitPolicy`、`MultimodalScoreFactValidator`、`OriginalSourcePublicationGuard`
- **后端测试（4 文件）**：`Project1391ScopeWeightClosureTest`、`GoldenCasesReplayTest` 增量 + 金标夹具 `case-1391-appendix-weight-closure.*`
- **前端**：**零变更**（前端入口 hash 与上一版一致，`index-CUPjUaBu.js`）
- **数据库迁移**：**无新迁移**（源码与 DB 同步在 V1220）
- **Sidecar 源码**：**零变更**（指纹 `24c0104f` 与已部署版本一致 → 跳过重建）
- **文档**：`docs/lessons/lessons-learned.md`、`.wiki`（1 页 + 索引）、RCA 页面、第 229 次部署报告

## Flyway 预检结果

| 检查项 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK（Successfully validated 280 migrations，checksums 全匹配） |
| Step 2: DB 已应用版本 | 最新 V1220（2026-09-27），与源码 max V1220 一致，无 pending |
| Step 3: remote-deploy 内置 validate | ✅ 通过（激活前自动预检，280 migrations） |

## 部署步骤

1. **环境门禁**：AskUserQuestion 确认 ENV=test（172.16.38.78）✅
2. **早操三连**：`bash scripts/sync-env.sh .`（锚点分支 ff-only 已最新，本地门禁 7/7 就绪）✅
   - 注：锚点分支上 `source scripts/dev-env.sh` 会被 session-gate 阻断，直接以 `bash scripts/sync-env.sh .` 完成同步
3. **基线确认**：HEAD = origin/main = `de9a8aea9`，工作区干净 ✅
4. **服务器现状**：旧版 `fa8a65229` 运行中，health UP、service active ✅
5. **Flyway 预检 3 步法** ✅（见上表）
6. **本地打包**：`RELEASE_ID=de9a8aea9 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` ✅（BUILD SUCCESS，退出码 0）
7. **产物校验** ✅：同源 `apiBaseUrl=""`、`obsEnabled=true`、Detail chunk 内 `.upload(` 调用数=2、jar 内 V 迁移无重复版本、前端入口 `assets/index-CUPjUaBu.js`
8. **上传 + 部署**：scp 163M 归档 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）✅ 一次成功
9. **DB 备份** ✅：`winbid-de9a8aea9-20261022183432.sql.gz`（15M）
10. **Sidecar**：指纹未变（`24c0104f`），**未触发重建**；容器 `Up 5 hours`（即上次部署启动），健康组件 `sidecar: UP`
11. **前端资源保留** ✅：从 `releases/fa8a65229/frontend/assets` 用 `cp -rn` 补回旧 hash 文件，当前 assets 193 个
12. **健康检查** ✅：79 次尝试后连续 3/3 通过（含 Kafka readiness 延迟，属已知行为）
13. **Smoke 测试** ✅（7/7 + Sidecar 5/5，见下节）
14. **配置清理检查**：仅历史保留项 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`，非本次引入

## 验证结果

### 健康检查

```
✅ Flyway validate 通过（无 pending 新迁移）
✅ Health check passed (consecutive 3/3, total attempts: 79, service: active/running)
✅ Frontend matches release (src="/assets/index-CUPjUaBu.js")
✅ Sidecar 未重建（指纹一致），health 组件 sidecar=UP
```

健康组件全绿：aiProvider（custom / gemini-3.5-flash）/ db（MySQL）/ diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar 均 UP。

### Smoke 测试（经 Nginx 8080 代理到后端 18080）

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| /actuator/health | 200 | 200 UP | ✅ |
| /actuator/health/readiness | 200 | 200 UP | ✅ |
| POST /api/auth/login（空参） | 400 | 400 | ✅ |
| /api/projects（未认证） | 401 | 401 | ✅ |
| /api/integration/crm/health（未认证） | 401 | 401 | ✅ |
| 前端 / | 200 | 200 | ✅ |
| 前端 /login | 200 | 200 | ✅ |
| 前端入口资源 | 与 release 一致 | `assets/index-CUPjUaBu.js` | ✅ |
| gzip + 强缓存头 | gzip + public, immutable | `Content-Encoding: gzip` + `Cache-Control: public, immutable, max-age=31536000` | ✅ |
| Sidecar 冒烟（smoke-sidecar.sh，服务器 127.0.0.1:8000） | 5/5 | 5 通过 / 0 失败 / 0 警告 | ✅ |

Sidecar 冒烟明细：fixture v1 形态回放通过（tables=14 cells=8215 scoreTables=1）；v1 探针 200（X-Schema-Version=1.0）；v2 探针 200（DocumentArtifact，schemaVersion=2.2，响应头 2.2）。

> 本地对 `127.0.0.1:8000` 跑同一脚本会失败（本地无该端口 sidecar），对本地开发 8009 跑会在 `/extract-tables` 探针处返回 401（本地实例鉴权配置差异）。以服务器侧结果为准。

### 部署记录确认

```json
"releaseId": "de9a8aea9",
"activatedAt": "2026-10-22T10:34:41Z",
"releaseDir": "/opt/xiyu-bid/releases/de9a8aea9",
"sidecarSha256": "24c0104ff62244e9f16b66bc4f371b097714ecbae88dbe09a77afd930cb63273",
"obsEnabled": true
```

## 本次观察与改进点

**1. Sidecar 指纹漂移修复经本次部署实证生效**

第 229 次报告预期"下一次部署若 sidecar 源码无变更将跳过构建"。本次 sidecar 源码零变更、metadata 内容哈希与线上一致（`24c0104f`），实际**未触发 docker build**，容器 `Up 5 hours` 保持上次部署启动的实例。`!2841` 修复按预期工作，该风险项可关闭。

**2. 前端 assets 手工保留产生非 jetty 属主条目**

本次 `remote-deploy.sh` 在激活前端目录前检测到 `/srv/www/xiyu-bid/assets/` 存在 root 属主条目（上次手工 `sudo cp -rn` 遗留），自动执行 `sudo chown -R jetty` 修复后才继续。说明"手工 sudo cp -rn + 不修属主"的组合会给下一次部署留下隐患。建议：补回旧 assets 后立即 `sudo chown jetty:jetty`，或将这步固化进 `remote-deploy.sh` 作为默认后置动作。

**3. `/api/projects` 未认证返回 401 而非 403**

与第 228/229 次一致，401 语义正确，不视为问题；SOP 模板期望值仍待更正。

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release | `fa8a65229`（第 229 次） |
| 上一版本 Release 目录 | `/opt/xiyu-bid/releases/fa8a65229`（本次部署后仍存在） |
| DB 备份 | `winbid-de9a8aea9-20261022183432.sql.gz`（部署前自动备份，15M） |
| 回滚方式 | 原子 jar 替换恢复上一版 + `systemctl restart xiyu-bid-backend`；本次无 schema 变更，DB 无需回滚 |
| 回滚 owner | 运维 + 当班开发 |

## 风险提示

1. **评分解析行为变更**：本次上线"附件评分方案识别与权重闭合"修复，测试环境需重点回归附件类评分方案（如 1391 案例）的解析与权重闭合结果，确认人工复核不影响既有采纳流程。
2. **无前端变更**：前端入口 hash 与上一版一致，若前端表现异常应优先排除缓存/浏览器因素，而非本次构建。
3. **Kafka readiness 延迟**：本次 79 次尝试后 UP，属已知行为；超过 4 分钟需排查 Kafka broker。
4. **重启窗口**：后端 18:34:42 重启，health 连续 3/3 通过前约 2–3 分钟 API 不可用；测试环境可接受，生产部署需按维护窗口安排。
5. **DB 备份与恢复**：本次无 schema 变更，回滚仅需替换 jar。

## 部署确认清单

- [x] 环境门禁（用户确认 ENV=test，部署 de9a8aea9）
- [x] 早操三连通过（sync-env ff-only 已最新 + 本地门禁 7/7）
- [x] 服务器现状确认（旧版 `fa8a65229` health UP）
- [x] Flyway 预检 3 步法通过（无 pending、无 checksum mismatch）
- [x] 本地打包成功（BUILD SUCCESS，退出码 0）
- [x] 产物校验通过（obsEnabled=true、同源 baseURL、OBS 直传 2 处、迁移无重复）
- [x] DB 备份完成（15M）
- [x] remote-deploy.sh 一次成功（SYSTEMCTL_SUDO=true）
- [x] Sidecar 未重建（指纹一致），健康门禁通过
- [x] 健康检查通过（3/3 连续）
- [x] 前端旧 assets 保留（assets 193 个）
- [x] Smoke 测试 7 项 + Sidecar 冒烟 5 项全部通过
- [x] 前端入口资源与 release 一致
- [x] 临时调试配置检查（仅历史保留 SHOW_DETAILS=always）
- [x] 部署记录确认（deployed-release.json 已更新）
- [x] 部署报告生成

**部署状态：✅ 成功（一次成功，无人工回退；后端重启后约 2–3 分钟 health 重试窗口内 API 不可用，非零中断）**
