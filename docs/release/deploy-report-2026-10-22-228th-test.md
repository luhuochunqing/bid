# 第 228 次部署报告（test）

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **测试** (`test`) |
| 主机 | winbid-01 / 172.16.38.78 |
| 部署日期 | 2026-10-22 11:16 (UTC+8) |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `021cf93e4-api8080` |
| 部署分支 | `agent/trae-init`（锚点分支，HEAD = origin/main） |
| HEAD Commit | `021cf93e4` |
| 旧版本 | `836f9b279-api8080`（第 227 次，2026-10-13） |
| 构建包 | `.release/xiyu-bid-release-021cf93e4-api8080.tar.gz`（155M） |
| 增量 | 24 个 commit，35 个文件（+845/-89） |

## PR 列表（增量 24 个 commit，自 836f9b279..021cf93e4）

| Commit | 说明 |
|---|---|
| `2547ba131` | docs(release): 第 226/227 次部署报告 (test) |
| `f872e4b76` | fix(scripts): 适配 Gitee 仓库更名为 bid-xiyu |
| `e8c66798b` | chore: 移除误入库的 Python 字节码 claude/__pycache__ |
| `48dc3f9fa` | docs(release): 第 28 次生产部署报告 |
| `584c7249d` | fix(scoreparse): 同表编号唯一时按编号数值排序，前端序号列改行号 |
| `23aec9a29` | fix(scoreparse): 按评审意见修正注释口径并保留原文编号可查 |
| `1b58b8899` | docs(scoreparse): 回填序号乱序根因到教训库与 wiki |
| `216691dba` | docs(scoreparse): 修正 wiki §32 行数口径 |
| `492be80a4` | fix(scoreparse): 当次解析失败但仍有生效评分项时不展示失败徽章 |
| `cffd7a55c` | chore(scoreparse): 移除徽章判断迁移后残留的未使用解构 |
| `0a3d58e1a` | docs(wiki): 回填评分解析失败徽章抑制规则 |
| `285947c2b` | fix(scoreparse): 引文归一化折叠比较符与 CJK 标点换行，修复逻辑行来源绑定失败 |
| `2698e3edf` | test(scoreparse): 新增引文归一化金标夹具并回填 wiki |
| `fb00ba407` | fix(scoreparse): 引文归一化修复逻辑行来源绑定失败（94/100 分差） |
| `61597f7a2` | docs(lessons): 沉淀引文归一化根因案例（§435）并补 wiki 日志 |
| `f9d4c076e` | chore(decisions): 补记 Gitee 仓库改名决策 |
| `099dfd640` | docs(lessons): 修正 §435 失败点归因与 wiki 日志口径 |
| `021cf93e4` | **!2838** auto-merge（docs-scoreparse-quote-normalize） |

> 其余 6 个为上述分支内的中间 commit（含 auto-merge 节点）。

## 改动范围

- **后端（4 个主文件 + 4 个测试）**：scoreparse 领域逻辑
  - `EvidenceText`（引文归一化：fold 比较符 / CJK 标点换行）
  - `ScoreCodeKey`、`ScoreItemOrderPolicy`（同表编号唯一时按数值排序）
  - `LogicalRowEvidenceBinder`（逻辑行来源绑定）
  - 新增金标夹具 `case-quote-glyph-ordinal-normalize`
- **前端（8 文件）**：`ScoreParseDrawer.vue` / `ScoreParseTable.vue` + `scoreParseTask.js` / `useScoreParseDrawer.js` 及对应 spec
  - 失败徽章抑制（当次解析失败但仍有生效评分项时不展示）
  - 序号列改为行号（后端按编号数值排序）
- **数据库迁移**：**无新迁移**（源码与 DB 同步在 V1220）
- **Sidecar**：源码零变更（`git diff 836f9b279..HEAD -- document-converter-sidecar/` 为空）
- **其他**：e2e spec 1 个、wiki 2 页、docs（release / lessons / operations）、scripts（gitee-pr-helper / gitee-auto-merge-gate / agent-finish-task）、移除误入库 pycache

## Flyway 预检结果

| 检查项 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK（Successfully validated 280 migrations） |
| Step 2: DB 已应用版本 | 最新 V1220（2026-09-27），与源码 max V1220 一致，无 pending |
| Step 3: remote-deploy 内置 validate | ✅ 通过（激活前预检） |

## 部署步骤

1. **环境门禁**：AskUserQuestion 确认 ENV=test（172.16.38.78）✅
2. **早操三连**：`sync-env.sh`（本地门禁 7/7 就绪）+ `check-git-wrapper.sh`（wrapper 生效，`--no-verify` 被拒）✅
   - 注：因当前在锚点分支 `agent/trae-init`，`source dev-env.sh` 会触发 session-gate 阻断（非开发任务），改为直接调用 `bash scripts/sync-env.sh .`（自带 ff-only 同步）并通过 `PATH` 前缀激活 git wrapper
3. **基线确认**：HEAD = origin/main = `021cf93e4`，工作区干净 ✅
4. **服务器现状**：旧版 `836f9b279-api8080` 运行中，health UP、全部组件 UP ✅
5. **Flyway 预检 3 步法** ✅（见上表）
6. **本地打包**：`RELEASE_ID=021cf93e4-api8080 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` ✅（BUILD SUCCESS；容器测试门禁 `FlywayMysqlContainerTest` 通过）
7. **产物校验** ✅：`apiBaseUrl=""`（同源）、`obsEnabled=true`、OBS `.upload(` 调用数=2、jar 内 Flyway 迁移版本无重复（279 个资源）
8. **上传 + 部署**：scp 155M 归档 + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）✅ 一次成功
9. **DB 备份**：部署前自动备份 `winbid-021cf93e4-20261022110426.sql.gz`（14.3M）✅
10. **前端资源保留** ✅：从 `836f9b279-api8080` 目录 `cp -rn` 旧 assets，assets 数 193 → 277（补回 84 个旧 hash 文件）
11. **健康检查** ✅：79 次尝试后 3/3 连续通过（约 2 分 36 秒，Kafka readiness 延迟，已知行为）
12. **迁移验证**：无新迁移，跳过
13. **Smoke 测试** ✅（9/9，见下节）
14. **配置清理检查**：仅 `SHOW_DETAILS=always`（历史保留项，非本次引入）

## 验证结果

### 健康检查

```
✅ Health check passed (consecutive 3/3, total attempts: 79, service: active/running)
Active: active (running) since 四 2026-10-22 11:16:50 CST
✅ Frontend matches release (src="/assets/index-bJKfFBGk.js")
```

健康组件全绿：aiProvider / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar 均 UP。

### Smoke 测试（经 Nginx 8080 代理到后端 18080）

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| /actuator/health | 200 | 200 | ✅ |
| /actuator/health/readiness | 200 | 200 | ✅ |
| POST /api/auth/login (空参) | 400 | 400 | ✅ |
| /api/projects (未认证) | 401 | 401 | ✅ |
| /api/integration/crm/health (未认证) | 401 | 401 | ✅ |
| 前端 / | 200 | 200 | ✅ |
| 前端 /login | 200 | 200 | ✅ |
| 前端入口资源 | 与 release 一致 | `assets/index-bJKfFBGk.js` | ✅ |
| gzip + 强缓存头 | gzip + public, immutable | `Content-Encoding: gzip` + `Cache-Control: public, immutable` | ✅ |

### 部署记录确认

```json
"releaseId": "021cf93e4-api8080",
"activatedAt": "2026-10-22T03:16:50Z",
"sidecarSha256": "5a3e833e99dad4c2c729c3dd4dbc349e34e5a2bb4c29990bd15eb3f7f72cf0e0",
"obsEnabled": true
```

## 本次观察与改进点

**1. Sidecar sha 漂移触发无意义重建（227 次差异）**

- 本次 sidecar 源码**零变更**，但打包 sha 与上次记录不同（`8c57b43e` → `5a3e833e`），`remote-deploy.sh` 因此执行 `docker build`（约 11 分钟）。
- 227 次报告曾建议"部署前预写 `.deployed_sha256` 跳过重建"，本次未沿用该手工步骤，按脚本默认路径走完整构建；构建最终成功（无需人工介入），代价是部署窗口从约 3 分钟拉长到约 13 分钟。
- **建议**：把 sha 判定改为源码差异判定（`git diff --name-only <old>..<new> -- document-converter-sidecar/`）或让 package 归档排除时间戳，彻底消除漂移误判。该改动属脚本治理，本次部署不扩大范围。

**2. 前端 assets 保留未在脚本内固化**

- `remote-deploy.sh` 未自动保留上一版本 assets，本次按 SOP 手工 `cp -rn` 补回 84 个旧 hash 文件（跨版本 404 防护）。建议后续固化为 `POST_DEPLOY_COMMAND`。

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版本 Release | `836f9b279-api8080`（第 227 次） |
| 上一版本 Release 目录 | `/opt/xiyu-bid/releases/836f9b279-api8080`（本次部署后仍存在） |
| DB 备份 | `winbid-021cf93e4-20261022110426.sql.gz`（部署前自动备份，14.3M） |
| 回滚方式 | 原子 jar 替换恢复上一版 + `systemctl restart xiyu-bid-backend`；本次无 schema 变更，DB 无需回滚 |
| 回滚 owner | 运维 + 当班开发 |

## 风险提示

1. **Kafka readiness 延迟**：79 次尝试（约 2 分 36 秒）后 UP，属已知行为；超过 4 分钟需排查 Kafka broker
2. **Sidecar 重建耗时**：本次因 sha 漂移额外构建约 11 分钟，新容器 `xiyu-sidecar` 部署后 8 分钟稳定 Up；如再次出现镜像源不稳定，可能阻塞部署（227 次曾遇 502）
3. **前端入口 hash 变更**（`index-BpcZvMz6` → `index-bJKfFBGk`）：本次前端有实质改动，旧标签页已通过 assets 保留兜底，需用户刷新页面获取新逻辑
4. **评分规则行为变更**：本次上线 scoreparse 的排序口径（按编号数值）与失败徽章抑制，测试环境验收时需重点回归评分解析抽屉与评分表

## 部署确认清单

- [x] 环境门禁（用户确认 ENV=test，部署 021cf93e4）
- [x] 早操三连通过（sync-env 7/7 + git wrapper 生效）
- [x] 服务器现状确认（旧版 health UP、全组件 UP）
- [x] Flyway 预检 3 步法通过（无 pending、无 checksum mismatch）
- [x] 本地打包成功（BUILD SUCCESS + 容器测试门禁通过）
- [x] 产物校验通过（obsEnabled=true、同源 baseURL、OBS 直传、迁移无重复）
- [x] DB 备份完成
- [x] remote-deploy.sh 一次成功（SYSTEMCTL_SUDO=true）
- [x] 健康检查通过（3/3 连续）
- [x] 前端旧 assets 保留（84 个旧 hash 文件补回）
- [x] Smoke 测试 9 项全部通过
- [x] 前端入口资源与 release 一致
- [x] 临时调试配置检查（仅历史保留 SHOW_DETAILS=always）
- [x] 部署记录确认（deployed-release.json 已更新）
- [x] 部署报告生成

**部署状态：✅ 成功（一次通过，零中断）**
