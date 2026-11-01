# 测试环境第 255 次部署报告

## 部署基本信息
- **部署时间**: 2026-11-01 19:32:50 CST（激活）→ 19:35 前后健康检查通过（脚本 79 次尝试，约 2.5 分钟）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `38c34354b`
- **提交 Commit**: `38c34354b`（!2948）
- **上一版本**: `09ccb7a84`（2026-10-31 21:46 CST 激活）
- **部署方式**: 按 `xiyu-server-deploy` SOP 手工执行 `package-release.sh` + `remote-deploy.sh`（固化 `VITE_API_BASE_URL=""`、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）
- **增量规模**: 15 个 commit（!2945 报告、!2946 生产报告、!2947 响应性合规修复、!2948 评分表 OCR 修复链）

---

## 变更核心内容
1. **评分表 OCR 链路修复（!2948 及前序 4 个 commit）**
   - `BaiduScanPageSelector`、`BaiduTableArtifactEnricher`、`ScoreParseEntryRouter` 调整：修复跨页评分表百度 OCR 前序页硬截断、多评分区间单簇漏扫、入口路由假成功短路、超长大表静默截断、fallback 倒退覆盖、方案总分冲突覆盖等问题。
   - 回归测试：`Project1382FullPipelineIntegrationTest`、`ScoreParseEntryRouterTest`、`BaiduTableArtifactEnricherTest`。
2. **合规响应性判定回归（!2947）**
   - `ResponsivenessReviewPolicy`、`ResponsivenessRuleCitation`、`DeviationEvidencePolicy`、`DeviationTableExtractor` 调整，回归法定偏离表第一性原理，消除全文负词定罪。
   - 回归测试：`Report147ResponsivenessRegressionTest`、`ResponsivenessReviewPolicyTest`。
3. **规格与知识沉淀**
   - `.wiki/pages/` 10 个文件回填（百度 OCR 双向连通、路由欠分仲裁、响应性设计方案与复盘）、`docs/lessons/` 追加。
4. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225（`score estimate freshness`）。
5. **前端**: 本次无前端源码改动（改动仅在后端 14 个文件 + docs/.wiki 15 个文件），构建产物 hash 与上一版本一致（`assets/index-xSKpZ2Mv.js`，193 个 assets）。

---

## 部署前预检
- **Flyway validate（服务器）**: `VALIDATE OK - all checksums match`（285 个迁移）
- **DB 已应用版本**: V1225（`2026-10-26 20:01:47`），与源码最新版本一致，无 pending 迁移
- **服务器健康（部署前）**: `/actuator/health` UP（db / redis / sidecar / aiProvider 均 UP），`xiyu-bid-backend` active

## 部署执行
- 早操三连：`sync-env.sh`（init 分支 ff-only 同步 origin/main）、`check-git-wrapper.sh`（7/7 通过）
- 本地打包：前端同源构建 + `mvn clean -DskipTests package`（BUILD SUCCESS，43.1s）
- 产物校验：jar 内 284 个 `V*.sql` 无重复；`apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`；Detail chunk `.upload(` 调用数 = 2；sidecar SHA-256 `e5fc03f3…`（schemaVersion 2.2，与服务器一致）
- 上传：`scp` 归档（156 MB）+ `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`
- 远程部署：数据库备份 `db-backups/winbid-38c34354b-20261101193239.sql.gz`（15.7 MB）→ Flyway validate 通过 → 原子切换 jar → 重启服务 → 健康检查连续 3/3 通过 → 前端入口一致性校验通过
- 旧版本 assets 保留：`cp -rn 09ccb7a84/frontend/assets → /srv/www/xiyu-bid/assets`（新旧 hash 完全一致，幂等 no-op）
- `._*` 元数据残留：0 个

## 部署后验证
> 取证时间 2026-11-01 19:32–19:38 CST（远程部署脚本输出 + 部署后独立 ssh/curl/SQL 复核）。

| 检查项 | 结果 |
|---|---|
| `/actuator/health`（服务器本地） | UP（db / redis / sidecar / aiProvider 均 UP） |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，符合预期） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health`（未认证） | HTTP 401 |
| 前端 `/`、`/login` | HTTP 200 / 200 |
| 前端入口 hash | `assets/index-xSKpZ2Mv.js`（与 release 目录一致） |
| 服务状态 | `xiyu-bid-backend` active（PID 2519） |
| 后端启动后 ERROR 日志 | 无（`journalctl -p err` 返回 `No entries`） |
| DB 迁移 | V1225 success=1（无新增） |
| 健康检查等待 | 连续 3/3 通过（脚本 79 次尝试，约 2.5 分钟） |
| 公共 assets / release assets | 193 / 193（一致） |

## 临时配置检查
- `/etc/xiyu-bid/backend.env` 存在 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项，非本次新增），无 `DEBUG` / `TRACE` 临时配置。

## 回滚信息
- 上一版本目录：`/opt/xiyu-bid/releases/09ccb7a84`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-38c34354b-20261101193239.sql.gz`
- 本次无 schema 变更、无前端产物变更，回滚只需切回上一版本 jar（前端目录可直接复用当前 hash）

## 风险提示
- 本次主体为评分表 OCR 解析链路修复，影响标书评分解析总分核验与跨页表格识别，重点回归点为多页评分表总分是否一致、长表是否被静默截断。
- 测试环境仍保留健康详情暴露（`SHOW_DETAILS=always`），生产环境不得照搬该配置。

## 结论
- **部署级验收**: ✅ 通过（健康检查、接口路由、前端资源一致性、启动日志均正常）
- **未覆盖项**: 未执行浏览器端业务功能回归（本次为后端解析/合规判定修复），且未执行 Sidecar 冒烟脚本（sidecar 指纹与上一版本一致，未变更）；建议业务方在测试环境按回归清单验证评分表解析与响应性核查结论。
- **部署状态**: 已成功交付测试环境 `http://172.16.38.78:8080/`
