# 测试环境第 257 次部署报告

## 部署基本信息
- **部署时间**: 2026-11-04 08:21:38 CST（激活）→ 08:24:19 CST 健康检查连续 3/3 通过（脚本 80 次尝试，约 2.6 分钟）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `3291a9015-api8080`
- **提交 Commit**: `3291a9015`（!2957）
- **上一版本**: `3a0ed6dc6`（2026-11-03 18:26 CST 激活，第 256 次）
- **部署方式**: 按 `xiyu-server-deploy` SOP 执行 `scripts/release/deploy-test.sh`（固化 `VITE_API_BASE_URL=""`、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`）
- **增量规模**: 2 个 commit（含 1 个 PR 合并；非合并提交 1 个：`1d2b4078d`，覆盖 PR !2957）
- **网络与连通性**: 本机 TUN 代理拦截已解除（`utun7` 企业内网直连），SSH 连通性测试通过

---

## 变更核心内容

1. **后端解除方案采纳白名单死锁（PR !2957）**
   - 修复文件：`backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java`
   - 根因：对于多物理表格或未声明统一总分场景（如项目 1117），候选事实均为 `OUT_OF_SCHEME`；原有校验在过滤合法方案池时直接得到空集合，导致用户人工采纳时被误判并抛出 `一次采纳只能包含所选评分方案的事实`。
   - 修复：分离 `allDocumentSchemes` 与 `verifiedDocumentSchemes`，当方案内事实为空时回退扩展至全部候选方案，放行用户指定的目标方案，彻底消除死锁。
   - 测试保障：新增 `ScoreParseReviewAppServiceTest.submitReview_adoptCandidates_whenAllFactsOutOfSchemeAndRequestedSchemeProvided_allowsAdoption` 单测，全套 26 个用例全绿通过。
2. **前端消除重复错误弹窗（PR !2957）**
   - 修复文件：`src/api/modules/scoreParse.js`
   - 修复：在 `adoptCandidates` 调用中增加 `{ skipGlobalErrorMessage: true }`，避免 Axios 全局拦截器与组件 catch 块并排弹出两条重复错误消息。
3. **知识沉淀与 Wiki**
   - 教训库追加：`docs/lessons/lessons-learned.md §487`
   - Wiki 页面回填：`.wiki/pages/score-parse-service.md §34`
4. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225
5. **前端产物**: 入口 hash 为 `assets/index-DyKe5lqD.js`

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操 `sync-env.sh` | ✅ 完成 |
| 门禁 `check-git-wrapper.sh` | 7/7 通过 |
| 基线确认 | HEAD=`3291a9015` |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225，与源码最新版本一致，无 pending |
| 服务器健康（部署前） | `/actuator/health` UP，`xiyu-bid-backend` active |

---

## 部署执行

- 本地打包：前端同源构建 + `mvn clean -DskipTests package`（BUILD SUCCESS，42.8s）
- 产物校验：jar 内迁移无重复；`apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`；Detail chunk `.upload(` 调用数 = 2；
- 上传：`scp` 归档 `xiyu-bid-release-3291a9015-api8080.tar.gz`（156 MB）+ `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`
- 远程部署：数据库备份自动写入 `db-backups/winbid-3291a9015-api8080-*.sql.gz` → Flyway validate 通过 → 原子切换 jar → 重启服务 → 健康检查连续 3/3 通过（80 次尝试）
- 旧版本 assets 保留：已从上一版本 `3a0ed6dc6` 保留 assets 到 `/srv/www/xiyu-bid/assets/`
- Sidecar 冒烟：`smoke-sidecar.sh` 5 项全部通过（健康检查、X-Schema-Version=2.2、1371 表格回放、v1/v2 探针）

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`3291a9015-api8080` |
| `/actuator/health`（服务器本地） | UP |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（经 Nginx 8080） | HTTP 200（返回 400 参数校验信息，接口正常） |
| 前端首页 `/` | HTTP 200 |
| 前端入口一致性 | `assets/index-DyKe5lqD.js` 与 release 一致 |
| OBS 直传校验 | Detail chunk `.upload(` 调用数 = 2，已启用 |
| 前端资产保留 | `3a0ed6dc6` 旧 hash 文件已保留，防跨版本 404 |

---

## 回滚信息
- 回滚锚点：release id `3291a9015-api8080`；上一已知良好版本 `3a0ed6dc6`
- 后端旧 jar：`/opt/xiyu-bid/releases/3a0ed6dc6/backend/app.jar`
- 前端旧 assets：已保留在 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`/opt/xiyu-bid/db-backups/` 已生成最新快照
- 回滚状态：**就绪（未执行）**
