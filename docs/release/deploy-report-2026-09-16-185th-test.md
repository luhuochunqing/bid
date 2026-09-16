# 第 185 次部署报告（test 环境）

**日期**：2026-09-16

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`c83ef88e1`

**前次 Release**：`bd432f486-api8080`（今日 00:37 临时一次性历史补发，未并入 main）

**状态**：✅ 部署成功，健康检查全绿，Smoke 全部符合预期

**生产环境**：未访问、未部署、未改配置或数据

## 部署概览

本次将测试环境从临时补发版本 `bd432f486` 对齐到最新 `origin/main`（`c83ef88e1`），含两个已合入 PR 的正式功能与一个新增迁移。

## 基线信息

| 项目 | 值 |
| --- | --- |
| Git commit | `c83ef88e1`（`!2666 fix(workbench): 补发历史商机预测提醒并实时解析收件人`） |
| 增量范围 | `44c2067b1..c83ef88e1`（15 个 commit） |
| 合并 PR | `!2665`（知识案例应答摘要可读性 + 标书原文来源标题定位）、`!2666`（商机预测提醒实时解析收件人） |
| 新增迁移 | `V1215__add_source_provenance_to_knowledge_case.sql` |

## 改动范围

- **PR !2665（WF 0915-01）**：knowledge_case 应答片段摘要可读性优化 + 标书原文来源标题定位。新增 V1215 五列 `source_section_title / source_section_path / source_document_name / source_page_number / source_section_index`（均可空，仅在结项沉淀或显式导入可证明时写入）。
- **PR !2666**：补发历史商机预测提醒，并改用实时解析 OSS 收件人，替代此前临时分支 `bd432f486` 的旧逻辑。

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| 服务器 app.jar SHA-256 | `6149c48b55cc02f8c8b15e4f8fbf1acfb65a2bd511c223b0a97f326c0dc86440` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-c83ef88e1-20260916125735.sql.gz`（13MB） |
| 部署前基线（DB） | 已应用至 V1214 |
| 回滚锚点 | 前次 Release `bd432f486-api8080` + 上述 DB 备份 + `U1215` 回滚脚本 |

## Flyway 预检结果

- 服务器 `flyway-repair-runner.sh validate`：✅ 274 个迁移 checksum 校验一致。
- DB 已应用版本：最新 V1214；`V1215` 待应用（预期 pending）。
- 回滚脚本 `U1215` 存在。
- V1215 为纯 ADD COLUMN（5 个可空列），不建索引/外键，幂等安全；已由本地 `FlywayMysqlContainerTest` 容器测试验证全量装配。

## 部署步骤

1. 早操三连：锚点分支 ff-only 同步至 `origin/main` `c83ef88e1`，本地门禁 7/7 通过。
2. 本地打包：`RELEASE_ID=c83ef88e1 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS；jar 内迁移无重复；sidecar 打包完成；OBS 直传 .upload()=2。
3. 产物校验：`apiBaseUrl=""`（同源）、`obsEnabled=true`、`V1215` 已打入 jar、前端 index 入口存在。
4. 上传归档（162MB）+ `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）。
5. 部署后保留上一版本 assets（`bd432f486` 目录 cp -rn 补回旧 hash 资源）。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 后端健康（127.0.0.1:18080） | ✅ UP；db/redis/sidecar/readiness 均 UP |
| V1215 迁移应用 | ✅ `success=1`，installed_on `2026-09-16 12:57:56` |
| 经 Nginx 8080 `/actuator/health` | ✅ 200 |
| `/actuator/health/readiness` | ✅ 200 |
| `POST /api/auth/login`（空体） | ✅ 400 |
| `/api/projects`（未认证） | ✅ 403 |
| `/api/integration/crm/health` | ✅ 401 |
| 前端 `/` 与 `/login` | ✅ 200 |
| 前端入口与 release 一致性 | ✅ `assets/index-Chhe0w0S.js` 一致 |

部署过程健康检查在 79 次尝试（约 2.6 分钟）后通过——符合已知的 Kafka/组织事件 SDK 同步注册阻塞 readiness 的行为，非本次引入的死锁。

## 配置清理检查

`/etc/xiyu-bid/backend.env` 仅命中 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`——为历史保留项（第 13-15 次多次决定保留），非本次 TEMP 调试配置（无 `DEBUG`/`TRACE`），未改动。

## 风险提示

- 完整后端套件（9,735 tests）在未改动模块存在存量基线失败（competition-intel、BidDocumentQualityEngine、共享 MySQL Testcontainers 生命周期），本次为增量部署未触碰这些模块，不声明全套件全绿。
- `SHOW_DETAILS=always` 保持历史决策，如需关闭请另行授权。

## 部署确认清单

- ✅ 环境门禁（test 172.16.38.78）
- ✅ Flyway 预检 3 步
- ✅ 本地打包 + 产物校验
- ✅ 上传 + 部署
- ✅ 健康检查 / 迁移验证 / Smoke
- ✅ 配置清理检查
- ✅ 前端资源保留