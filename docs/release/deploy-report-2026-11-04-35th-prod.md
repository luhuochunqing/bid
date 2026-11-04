# 第 35 次生产环境部署报告 — 2026-11-04

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com`（内网 `http://172.16.10.149:80`） |
| Release ID | `d74e0f644` |
| 基线 commit | `d74e0f644`（`origin/main` HEAD） |
| 前一版本 | `9baae6bbc`（2026-11-03 19:52 CST 激活，第 34 次生产） |
| 部署时间 | 2026-11-04 09:04:07–09:04:36 CST |
| 状态 | ✅ 成功（健康 + Flyway + Smoke 全通过，**本次零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`9baae6bbc` 发布目录 + DB 备份 `winbid-d74e0f644-20261104090403.sql.gz`） |

环境门禁：用户通过问答交互卡片明确选择「(Recommended) 确认：生产环境 172.16.10.149」，符合生产授权红线。

---

## 部署概览

本次从生产版本 `9baae6bbc` 追平至 `origin/main` 最新 `d74e0f644`，覆盖 PR `!2957` 与 `!2958`。

- **与上次生产部署的关键差异：本次零数据库迁移**（部署前后成功迁移数均为 285 条 SQL 记录，最高版本仍为 `V1225`，`failed=0`）。
- **本次无 Sidecar 变更**：`document-converter-sidecar` 源码 SHA 未变（`e5fc03f3…`），Sidecar 冒烟 5 项全绿。
- **前端产物**：同源构建，入口为 `assets/index-gdQZVt-c.js`，OBS 直传校验通过（Detail chunk `.upload(` 调用数 = 2）。

---

## 变更范围与 PR

覆盖 PR：`!2957`（scoreparse 采纳死锁修复与弹窗优化）与 `!2958`（第 257 次测试环境部署报告）。

### 核心主题

1. **scoreparse 人工复核采纳死锁解除（PR !2957，针对线上项目 1117）**：
   - 修复文件：`backend/src/main/java/com/xiyu/bid/scoreparse/application/ScoreParseReviewAppService.java`
   - 根因：对于多物理表格或未声明统一总分场景，候选事实全被标记为方案外事实（`OUT_OF_SCHEME`）。原有代码在提取方案白名单时把 `OUT_OF_SCHEME` 排除导致白名单集合为空集 `[]`，从而用户指定有效方案后抛出 `一次采纳只能包含所选评分方案的事实` 异常。
   - 修复：分离 `allDocumentSchemes` 与 `verifiedDocumentSchemes`，当方案内事实为空时回退至全部候选方案，放行指定方案采纳生效，彻底消除死锁。
   - 单测保障：新增 `ScoreParseReviewAppServiceTest.submitReview_adoptCandidates_whenAllFactsOutOfSchemeAndRequestedSchemeProvided_allowsAdoption` 单测。
2. **前端采纳错误并排双重弹窗消除（PR !2957）**：
   - 修复文件：`src/api/modules/scoreParse.js`
   - 修复：在 `submitReview` API 调用中增加 `{ skipGlobalErrorMessage: true }`，避免 Axios 全局拦截器与页面业务 catch 块重复触发 `ElMessage.error`。
3. **知识沉淀与 Wiki**：
   - 教训库追加：`docs/lessons/lessons-learned.md §487`
   - Wiki 页面回填：`.wiki/pages/score-parse-service.md §34`
4. **测试环境真实浏览器 E2E 验证前置（PR !2958）**：
   - 使用 Google Chrome 无头自动化完整还原 1117 现场数据，验证采纳接口 HTTP 200，0 条错误消息，7 项评分标准（100分）顺利转入生效列表。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操 `sync-env.sh` | ✅ 完成 |
| 门禁 `check-git-wrapper.sh` | 7/7 通过 |
| 基线确认 | HEAD=`d74e0f644` |
| Flyway validate（生产机） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225，与源码最新版本一致，无 pending |
| 服务器健康（部署前） | `/actuator/health` UP，`xiyu-bid-backend` active |
| 磁盘余量 | 生产机可用 74 G |

---

## 部署执行

- 本地打包：同源构建 + OBS 启用 + Sentry 注入 + `mvn clean -DskipTests package`
- 产物校验：jar 内 284 个 `V*.sql` 无重复；`obsEnabled=true`；Detail chunk `.upload(` 调用数 = 2；
- 上传：`scp` 归档 `xiyu-bid-release-d74e0f644.tar.gz`（156 MB）+ `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`
- 远程部署：自动备份数据库到 `db-backups/winbid-d74e0f644-20261104090403.sql.gz` → Flyway validate 通过 → 原子切换 jar → 重启服务 → 健康检查连续 3/3 通过（15 次尝试）
- 旧版本 assets 保留：已从上一版本 `9baae6bbc` 保留 assets 到 `/srv/www/xiyu-bid/assets/`
- Sidecar 冒烟：`smoke-sidecar.sh` 5 项全部通过（健康检查、X-Schema-Version=2.2、1371 表格回放、v1/v2 探针）

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`d74e0f644` |
| `https://winbid.ehsy.com/actuator/health` | HTTP 200 UP（db/redis/sidecar/aiProvider 均 UP） |
| `https://winbid.ehsy.com/` | HTTP 200 |
| 前端入口一致性 | `assets/index-gdQZVt-c.js` 与 release 一致 |
| OBS 直传校验 | Detail chunk `.upload(` 调用数 = 2，已启用 |
| 前端资产保留 | `9baae6bbc` 旧 hash 文件已保留，防跨版本 404 |

---

## 回滚信息
- 回滚锚点：release id `d74e0f644`；上一已知良好版本 `9baae6bbc`
- 后端旧 jar：`/opt/xiyu-bid/releases/9baae6bbc/backend/app.jar`
- 前端旧 assets：已保留在 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-d74e0f644-20261104090403.sql.gz` 已就绪
- 回滚状态：**就绪（未执行）**
