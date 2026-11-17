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
   - 根因：`deploy-prod.sh` 调用 `package-release.sh` 时未设置 `COPYFILE_DISABLE=1`，主归档 `tar` 收入了 macOS 扩展属性文件（与第 42 次报告同一根因）。
   - 处置：`find /srv/www/xiyu-bid -name "._*" -delete` 清理，残留归零；前端 `/`、`/login` 复验 200，assets 641 个不受影响。
   - 待办：建议给 `package-release.sh` 主归档补 `COPYFILE_DISABLE=1`（本次未改代码）。
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
