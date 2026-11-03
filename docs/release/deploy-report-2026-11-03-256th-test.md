# 测试环境第 256 次部署报告

## 部署基本信息
- **部署时间**: 2026-11-03 18:26:45 CST（激活）→ 18:29 前后健康检查连续 3/3 通过（脚本 80 次尝试，约 2.6 分钟）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `3a0ed6dc6`
- **提交 Commit**: `3a0ed6dc6`（!2955）
- **上一版本**: `38c34354b`（2026-11-01 19:32 CST 激活，第 255 次）
- **部署方式**: 按 `xiyu-server-deploy` SOP 手工执行 `package-release.sh` + `remote-deploy.sh`（固化 `VITE_API_BASE_URL=""`、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）
- **增量规模**: 18 个 commit（含 7 个 PR 合并；非合并提交 11 个 = 7 个代码/测试 + 4 个文档类，覆盖 !2949、!2950、!2951、!2952、!2953、!2954、!2955）
- **部署前探测**: VPN 数据面一度异常（ping 100% 丢包 / SSH kex 被服务端关闭 / 8080 空响应），用户重连后恢复；服务器确认 `deployed-release.json` releaseId=`38c34354b`，与部署报告推算一致

---

## 变更核心内容

1. **评分表解析（scoreparse）修复链（!2950 / !2952 / !2953 / !2954）**
   - 区分提取记录与方案候选并展示来源诊断（`753d9a4d0`、`d663e9d3f`）
   - 按闭合原文单元核验分值（`9d58dc69c`、`30b285fef`）
   - 修复原文行归属与稳定父边核验、跨行错分、跨表重号与数字档位核验（`3b91bbad9`、`8c6188b74`）
   - 恢复可信跨页续文并排除公共说明误计、保留 reviewer-note 边界与 owned continuation evidence（`4ff31345f`、`e06df3f7a`、`513121eb7`）
   - 生产代码涉及 13 个类：`ShadowLogicalRowRestoration`、`DeterministicScoreFactExtractor`、`LogicalRuleSourceUnit`、`OriginalRuleRestoration`、`OriginalScoreEvidencePolicy`、`OriginalScoreWeightEvidencePolicy`、`ScoreEvidenceClaim`、`ScoreTableSummaryPolicy`、`ScoreWeightSourceEvidence`、`BaiduCrossPageTableLinker`、`BaiduRepeatedHeaderPolicy`、`BaiduScanPageSelector`、`BaiduTableArtifactAdapter`
   - 测试资产：3 组新增 golden 用例（`case-score-weight-source-boundaries`、`case-score-unit-authority`、`case-owned-headerless-continuation`）+ `ScoreUnitAuthorityGoldenReplay`、`ScoreWeightBoundaryGoldenReplay`、`GoldenCasesReplayTest`、`BaiduRepeatedHeaderContinuationTest`、`BaiduTableArtifactEnricherTest`
2. **前端评分抽取展示**
   - `scoreExtractionPresentation.js`、`ScoreCandidateReviewCard.vue`、`ScoreExtractionPanel.vue`：诊断标签防继承键（`5db156cd2`），展示提取记录/方案候选来源
3. **规格与知识沉淀**
   - `specs/082-score-weight-validation/`、`specs/20261102-ocr-continuation-mainline/`；`docs/lessons/lessons-learned.md` 追加 29 行；`.wiki` 回填
4. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`）
5. **前端产物**: 入口 hash 由 `assets/index-xSKpZ2Mv.js` 变为 `assets/index-0sWD1cwq.js`，assets 数量 193（与上一版本一致）

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操 `sync-env.sh` | ✅ 完成（锚点分支 ff-only，无需 rebase） |
| 门禁 `check-git-wrapper.sh` | 7/7 通过（`sync-env` 内联执行） |
| `git status` | 干净，HEAD=`origin/main`=`3a0ed6dc6` |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225（2026-10-26 20:01:47），与源码最新版本一致，无 pending |
| 服务器健康（部署前） | `/actuator/health` UP（db / redis / sidecar / aiProvider 均 UP），`xiyu-bid-backend` active |
| 磁盘余量 | `/opt` 可用 28 G |

## 部署执行

- 本地打包：前端同源构建 + `mvn clean -DskipTests package`（BUILD SUCCESS，50.9s）
- 产物校验：jar 内 284 个 `V*.sql` 无重复；`apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`；Detail chunk `.upload(` 调用数 = 2；sidecar SHA-256 `e5fc03f3…`（schemaVersion 2.2，与第 255 次一致）
- 上传：`scp` 归档（156 MB）+ `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`；归档 MD5 本地与远端一致（`201e8a8b1f7a640e542170b91d9bfb7f`）
- 远程部署：数据库备份 `db-backups/winbid-3a0ed6dc6-20261103182635.sql.gz`（16 MB）→ Flyway validate 通过 → 原子切换 jar → 重启服务 → 健康检查连续 3/3 通过（80 次尝试）→ 前端入口一致性校验通过
- 旧版本 assets 保留：`cp -rn 38c34354b/frontend/assets → /srv/www/xiyu-bid/assets`（公共 277 / 当前 release 193，旧 hash 资源保留供旧标签页使用）
- `._*` 元数据残留：0 个

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`3a0ed6dc6`，activatedAt=`2026-11-03T10:26:45Z` |
| `/actuator/health`（服务器本地） | UP（db / redis / sidecar / aiProvider 均 UP） |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，符合预期） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health`（未认证） | HTTP 401 |
| 前端 `/`、`/login` | HTTP 200 / 200 |
| 前端入口 hash | `assets/index-0sWD1cwq.js`（与 release 目录一致） |
| 服务状态 | `xiyu-bid-backend` active（PID 4812） |
| 后端启动后 ERROR 日志 | 无（`journalctl -p err --since "10 min ago"` 返回 `No entries`） |
| DB 迁移 | V1225 success=1（无新增） |
| 健康检查等待 | 连续 3/3 通过（80 次尝试，约 2.6 分钟；readiness 一次通过，无 Kafka 延迟） |
| 公共 assets / release assets | 277 / 193（旧版本资源已保留） |

## 临时配置检查
- `/etc/xiyu-bid/backend.env` 存在 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次新增），无 `DEBUG` / `TRACE` 临时配置。

## 回滚信息
- 上一版本目录：`/opt/xiyu-bid/releases/38c34354b`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-3a0ed6dc6-20261103182635.sql.gz`（16 MB）
- 本次无 schema 变更；回滚 = 切回上一版本 jar + 前端入口 `assets/index-xSKpZ2Mv.js`（旧 hash 仍在公共 assets 中）

## 风险提示
- 本次主体为评分表解析（scoreparse）原文边界、权重来源与分值核验修复，回归重点：跨页评分表续文是否完整、权重档位是否按原文边界判定、方案总分是否与原文单元一致。
- 前端评分抽取面板展示逻辑有变更（来源诊断标签），需回归「评分候选复核卡」展示字段。
- 测试环境仍保留健康详情暴露（`SHOW_DETAILS=always`），生产环境不得照搬该配置。

## 结论
- **部署级验收**: ✅ 通过（健康检查、接口路由、前端资源一致性、启动日志均正常）
- **未覆盖项**: 未执行浏览器端业务功能回归（本次为后端解析链 + 小范围前端展示改动），且未执行 Sidecar 冒烟脚本（sidecar 指纹与上一版本一致）；建议业务方在测试环境按回归清单验证评分表解析与评分候选展示。
- **部署状态**: 已成功交付测试环境 `http://172.16.38.78:8080/`
