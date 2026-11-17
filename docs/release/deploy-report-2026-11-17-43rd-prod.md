# 第 43 次生产环境部署报告 — 2026-11-17

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `5cbbf4925` |
| 基线 commit | `5cbbf4925`（与 `origin/main` HEAD 完全一致） |
| 前一版本 | `df970b534`（2026-11-14 08:14 CST 激活） |
| 部署时间 | 2026-11-17 08:31:49 CST（激活），健康检查随后通过 |
| 状态 | ✅ 成功（Flyway validate + 健康检查 + 内外部 Smoke + Sidecar 冒烟全通过，**零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`/opt/xiyu-bid/releases/df970b534` + 本次部署前 DB 备份） |

环境门禁：用户通过问答卡片明确选择「确认：生产环境 172.16.10.149」。

---

## 部署概览

从生产版本 `df970b534` 追平至 `origin/main` 最新 `5cbbf4925`，增量 **22 个 commit / 9 个 PR（!3029–!3037）/ 103 个文件（+2714 / -307）**。

- **零数据库迁移**：`df970b534..5cbbf4925` 无 `db/migration-mysql/` 变更；部署前后 `flyway_schema_history` 最高版本仍为 `V1225`，`failed` 记录 0 条。
- **Sidecar 有变更**：`scoring_extraction.py`（+83/-32）与 `tests/test_scoring_pdf.py`（+68），sidecar SHA `f30b7d0d…` → `b52dabc9…`，schemaVersion `2.2`；已触发镜像重建与容器重启，发布冒烟 5/5 通过。
- **前端产物**：同源构建（`apiBaseUrl=""`），入口 `assets/index-WpycfdEz.js`；`obsEnabled=true`，Detail chunk `.upload(` = 2；`sentryEnabled=true`。
- **旧资源保留**：`/srv/www/xiyu-bid/assets/` 共 641 个文件（本次 193 + 历史保留 448），避免旧标签页 preload 404。
- 发布包 156M。

---

## 变更范围与 PR

| PR | 主题 |
|---|---|
| !3029 | 第 42 次生产部署报告 + 生产 E2E 验收与部署经验沉淀 |
| !3030 | 长扫描 PDF 评分超时修复 + 客观项证据核验 |
| !3031 | scoreparse：统一章节方案标题、过滤伪方案、合并复核同格克隆 |
| !3032 | 合规核查：区分原件页码与检索块，隔离旧核查缓存 |
| !3033 | 案例推荐：语料 IDF 与 BM25 长度归一修复推荐偏离 |
| !3034 | scoreparse：固定得分规则被表格权重放大导致 OMS/WMS 计分不一致 |
| !3035 | 第 277 次测试环境部署报告 |
| !3036 | scoreparse：抽取覆盖缺口不再整任务失败并输出可读原因 |
| !3037 | 第 278 次测试环境部署报告 |

> 说明：本批次以「评分解析（scoreparse / scoring）稳定性」为主——固定得分权重、覆盖缺口容错、长扫描件 OCR 超时、引用/页码隔离；配套 casework 推荐相关性与文档沉淀。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 基线确认 | ✅ 分支 `agent/trae-init`，HEAD = `origin/main` = `5cbbf4925`，工作区干净 |
| 服务器健康（部署前） | `/actuator/health` UP、`/actuator/health/readiness` UP，服务 active |
| 部署前版本 | `deployed-release.json` → `releaseId=df970b534` |
| Flyway validate（生产机） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | `V1225`（2026-10-27 应用），与源码最新版本一致，无 failed 记录 |
| 磁盘 | `/` 使用 27G / 99G（29%） |

---

## 部署执行

使用 `deploy-prod.sh` 一键脚本（`ENV=prod`，Release ID `5cbbf4925`）：

```bash
printf 'y\n' | XYYU_SYNC_ENV_ACTIVE=1 XIYU_DEV_CONFIRMED=1 ENV=prod \
  bash scripts/release/deploy-prod.sh 5cbbf4925
```

- 打包：`package-release.sh`（同源 API、`VITE_OBS_ENABLED=true`、Sentry DSN 注入）
  - `mvn clean -DskipTests package` BUILD SUCCESS
  - Sidecar 打包 SHA-256 `b52dabc9…`，schemaVersion `2.2`
  - OBS 产物校验：Detail chunk `.upload(` = 2
- 上传 + 远程部署：DB 预备份 → Sidecar 升级（镜像重建 + 容器重启）→ Flyway validate → 后端 jar 原子替换 → systemd 重启 → 健康检查
- DB 预备份：`winbid-5cbbf4925-20261117083136.sql.gz`，7,504,699 字节

---

## 验证结果

| 验证项 | 结果 |
|---|---|
| 健康检查（服务器内） | ✅ 连续 3/3 通过（第 15 次尝试），`xiyu-bid-backend` active/running |
| `/actuator/health`（域名） | ✅ HTTP 200 UP（db / redis / aiProvider / sidecar / jwt 等组件 UP） |
| `/actuator/health/readiness`（域名） | ✅ HTTP 200 |
| 前端 `/`、`/login` | ✅ HTTP 200，入口 `assets/index-WpycfdEz.js` 与 release 一致 |
| `POST /api/auth/login`（空 body） | ✅ HTTP 400（参数校验生效，路由可达） |
| `GET /api/projects`（未认证） | ✅ HTTP 401（鉴权生效） |
| `GET /api/integration/crm/health`（未认证） | ✅ HTTP 401（鉴权生效） |
| Sidecar 发布冒烟 | ✅ 5/5：health + `X-Schema-Version=2.2`；fixture 回放 tables=14 cells=8215；v1/v2 探针均 200 |
| Flyway 迁移 | ✅ validate 通过，无新增 pending（零迁移） |
| OBS 直传回归门禁 | ✅ Detail chunk `.upload(` = 2 |
| 部署记录 | ✅ `deployed-release.json` → `releaseId=5cbbf4925`，`activatedAt=2026-11-17T00:31:49Z` |

---

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一版后端 jar | `/opt/xiyu-bid/releases/df970b534/backend/app.jar` |
| 上一版前端 | 同目录 `frontend/`（旧 hash 资源已保留在公共目录） |
| 上一版 Sidecar | 源码位于 `/opt/xiyu-bid/sidecar`，回滚需重新解包旧 release 的 `sidecar/sidecar.tar.gz` 并重建镜像 |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-5cbbf4925-20261117083136.sql.gz`（7.5M） |
| 回滚可行性 | 高（本次零迁移，回滚无需 DB 结构回退） |
| 是否执行 | 否，未触发 |

---

## 异常与处置

1. **`._*` 元数据残留第 3 次复发（283 个）**
   - 现象：`/srv/www/xiyu-bid` 下出现 283 个 `._*` 文件（278 个在 `assets/`），时间戳均为本次部署。
   - 来源分析：仅凭部署后的残留及时间戳，尚不能确定本次文件来自主归档 `tar`。`remote-deploy.sh` 会合并当前公开目录及历史已激活 release 的 assets，已有 `._*` 也会随之复制；主归档是否带入这些文件，仍需检查本次发布包及历史目录，不能直接沿用第 42 次报告的归因。
   - 处置：`find /srv/www/xiyu-bid -name "._*" -delete` 清理，残留归零；前端 `/`、`/login` 复验 200，assets 641 个不受影响。
   - 部署后代码修复：已在 `scripts/release/remote-deploy.sh` 的历史 assets 合并后、公开目录切换前加入 `find "$PENDING_FRONTEND_DIR" -name '._*' -delete`，清理待激活目录中的元数据残留。该修复不属于本次已部署基线 `5cbbf4925`，尚未随部署验证；上述现场清理结果不能证明代码修复已在生产生效。
2. **起始 `dev-env.sh` 提示 worktree 被占用**
   - 现象：脚本启动阶段打印「此 worktree 已被占用」，随后在 `XYYU_SYNC_ENV_ACTIVE=1` 维护模式下继续。
   - 影响：无。早操、基线确认、打包、部署均正常执行，退出码 0。

---

## 风险提示

- 本次为评分解析（scoreparse / scoring）集中修复（22 commit），建议业务方上线后用真实项目走一遍「投标文件实际评分 → 评分抽屉 → 证据/引用」，重点验证：
  - 固定得分项在含表格权重的方案中不再被放大（!3034）
  - 抽取覆盖缺口场景返回可读原因而非整任务失败（!3036）
  - 长扫描 PDF（数百页）评分不再 OCR 超时（!3030）
- Sidecar 已重建（`scoring_extraction.py` 变更），表格解析链路为本次高风险面；冒烟仅覆盖形态与 fixture 回放，不代表真实标书结果质量。
- 后端 `/etc/xiyu-bid/backend.env` 保留 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决策，非本次引入）。

---

## 部署确认清单

- [x] 环境门禁确认（用户选择生产 172.16.10.149）
- [x] 基线 = `origin/main`，工作区干净
- [x] Flyway validate 通过（零迁移，DB=V1225 无 failed）
- [x] DB 预备份完成（7.5M）
- [x] Sidecar 升级 + 冒烟 5/5 通过
- [x] 后端 jar 原子替换 + systemd 重启成功
- [x] 健康检查连续 3/3 通过
- [x] 内外部 Smoke 通过
- [x] OBS 直传门禁通过
- [x] 旧版前端资源保留（641 个 assets）
- [x] `._*` 残留清理归零
- [x] 回滚锚点就绪
- [x] 生产 E2E 验收（见下节，既有项目 1123，未新建项目/标讯）

---

## 生产 E2E 验收（升级内容逐项）

验收环境：`https://winbid.ehsy.com`，系统管理员会话；使用既有项目 **1123（陕西煤业化工集团 2027 年度集中采购项目）**，未新建任何项目/标讯/文件。允许的操作：触发实际打分、发起 AI 标书质量核查、打开 AI 智能案例推荐、点击各类只读/复用按钮。

| 升级点（PR） | 验收动作 | 结果 |
|---|---|---|
| 长文档不超时 + 覆盖明细（!3030） | 对 586MB《投标文件.docx》点击「重新打分」，全程观察任务状态 | ✅ PASS：08:46:05 → 09:06:08 完成（20m03s，`elapsedMillis=933168`），`COMPLETED/100%`；界面「原件处理完整；已处理 6624/6624 页/单元，未读取 0、处理失败 0、识别为空 121」 |
| 覆盖缺口不整任务失败（!3036） | 检查本次任务失败项与覆盖统计 | ✅ PASS（失败分支未触发）：`failedUnitCount=0 / unreadUnitCount=0 / enumerationComplete=true`，`failed=0`，任务未整体失败；缺口可读文案因数据无缺口未出现 |
| 固定得分不被表格权重放大（!3034） | 核对每项得分与权重、规则文本声明分值 | ✅ PASS：第 12 项「X≥2000万得9分」权重 9 → 满足 → 实得 9；`totalActualScore=9`、`weightWarning=false`；全表无「得分 > 权重」或「得分 > 文本声明分值」 |
| 统一章节方案标题 / 过滤伪方案（!3031） | 打开「AI 评分标准解析」核对候选与方案外条款 | ✅ PASS（主路径）：候选 12 项「有效合计 100 分」；营业执照、资质要求、响应函签字盖章等非计分条款被隔离到「当前方案外或来源未验证事实（不计当前候选、不计当前总分）」 |
| 页码溯源 + 隔离旧核查缓存（!3032） | 打开质量核查 → 重新核查 → 查看详情定位 | ✅ PASS（主路径）：旧报告提示「这是历史核查报告，问题页码尚未校验…」；确认弹窗「重新核查将跳过缓存…重新执行 25 项深度比对」；新报告生成于 `2026/11/17 09:19:18`；无物理页码时显示「未取得原文件页码，请按原文摘录检索」并附原文摘录 |
| 案例推荐相关性（!3033） | 分别查看第 1 项与第 4 项的推荐结果 | ✅ PASS（相关性）：第 4 项推荐 319 条，Top 为「商品系统对接能力和方案」「商品选商寻源方案」「产品技术指标符合程度」等商品/产品线主题；第 1 项 160 条均为财务评价/财务状况，无跨主题漂移 |
| 部署后可用性 | 登录、项目详情、评分抽屉、质量核查、案例推荐 | ✅ PASS：全链路可操作，无阻断性错误 |

### 未覆盖项（数据限制，不构成放行依据）

1. `!3036` 缺口可读原因：需构造「未读取/解析失败」文档才能触发，生产数据 `unread=0 / failed=0`，文案未出现。
2. `!3032` 正例「文件名：第 N 页」：当前证据来自 docx 内嵌图片，无物理页码可解析，仅验证到兜底文案与聚合逻辑。
3. `!3031`「请先选择评分方案」守卫：本项目已选中方案，触发会真实改动生产评分方案，按约束未执行。

### 数据影响与观察

- 项目 1123 实际打分被重新生成：任务 `d29e33ef-4c80-4565-a617-ae1dd1ac4234`，1 项满足（9 分）/ 6 客观待确认 / 5 主观待专家评审 / 失败 0；原结果被覆盖。
- 质量核查生成新报告（`2026/11/17 09:19:18`），旧报告不再展示。
- 586MB docx 转换约 15 分钟（sidecar 400% CPU / 1.75GB 内存），链路可用但耗时长，需关注大文件转换时长。
- 第 2 项「客户合作业绩」由「满足 12 分」变为「待确认（COVERAGE_PROOF_INSUFFICIENT）」，属证据充分性守卫行为，非本次修复引入，但会影响业务预期，建议业务确认。
