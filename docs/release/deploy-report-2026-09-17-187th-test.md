# 第 187 次部署报告（test 环境）

**日期**：2026-09-17

**环境**：test（172.16.38.78 / winbid-01）

**Release ID**：`99fa214f9`

**前次 Release**：`39230f4f4`（2026-09-16 部署）

**状态**：✅ 部署成功，健康检查全绿，Smoke 全部符合预期

**生产环境**：未访问、未部署、未改配置或数据

## 部署概览

本次将测试环境从 `39230f4f4` 对齐到最新 `origin/main`（`99fa214f9`），含已合入 main 的多个 PR 改动，**无新增数据库迁移**。

## 基线信息

| 项目 | 值 |
| --- | --- |
| Git commit | `99fa214f9`（`!2678 feat(case-detail): 原文所在标题调整为纯文本展示取消下载链接`） |
| 增量范围 | `39230f4f4..99fa214f9`（9 个 commit） |
| 主要合并 PR | `!2678`（case-detail 原文标题纯文本化）、`!2677`（部门全选 URL 超长 414 修复）、`!2676`（Kimi K3 等推理模型 temperature 兼容）、`!2675`（评分证据 JSON 转义修复）、`!2674`（第 20 次生产部署报告）、`!2673`（第 186 次部署报告） |
| 新增迁移 | 无 |

## 改动范围

- **!2678**：案例详情"原文所在标题"调整为纯文本展示，取消下载链接。
- **!2677**：数据分析部门全选时正规化参数，避免 URL 超长触发 414 报错。
- **!2676**：AI 推理模型（Kimi K3 等）固定 temperature 的自适应兼容。
- **!2675**：评分证据 JSON 序列化转义与抽取换行清洗缺陷修复（含 wiki 回填）。

## 发布与回滚证据

| 项目 | 结果 |
| --- | --- |
| 后端 jar | `app.jar`（`bid-platform-1.0.3.jar`，BUILD SUCCESS） |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-99fa214f9-<时间戳>.sql.gz`（remote-deploy 自动备份） |
| 部署前基线（DB） | 已应用至 V1215 |
| 回滚锚点 | 前次 Release `39230f4f4` + DB 备份 |

## Flyway 预检结果

- 服务器 `flyway-repair-runner.sh validate`：✅ 275 个迁移 checksum 校验一致，`VALIDATE OK`。
- 本次无新增迁移，DB 基线 V1215 与应用一致。
- remote-deploy 激活前内置信道再跑 validate，同样通过。

## 部署步骤

1. 早操：锚点分支 ff-only 同步至 `origin/main` `99fa214f9`，工作区干净，本地门禁就绪。
2. 本地打包：`RELEASE_ID=99fa214f9 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 package-release.sh` → BUILD SUCCESS；jar 内迁移无重复；sidecar 打包完成；OBS 直传 `.upload()`=2。
3. 产物校验：`obsEnabled=true`、前端 index 入口 `assets/index-24eAS0n1.js` 存在、jar `app.jar` 就位。
4. 上传归档（155MB）+ `remote-deploy.sh` 部署（`SYSTEMCTL_SUDO=true`）。
5. 部署后从前次 Release `39230f4f4` 目录 `cp -rn` 补回旧 assets（防跨版本 `preload` 404）。

## 验证结果

| 检查项 | 结果 |
| --- | --- |
| 后端健康（127.0.0.1:18080） | ✅ UP（remote-deploy 内 80 次探测 3/3 连贯） |
| `/actuator/health/readiness` | ✅ 200（readiness 正常，无 Kafka 延迟卡死） |
| 经 Nginx 8080 `/actuator/health` | ✅ 200 |
| `POST /api/auth/login`（空体） | ✅ 400 |
| `/api/projects`（未认证） | ✅ 403 |
| `/api/integration/crm/health` | ✅ 401 |
| 前端 `/` 与 `/login` | ✅ 200 |
| 前端入口与 release 一致性 | ✅ `assets/index-24eAS0n1.js` 一致 |
| 迁移应用 | ✅ 无新迁移，基线 V1215 保持 |

部署过程后端 health 在 80 次尝试后通过，服务 active/running——符合已知的 Kafka/组织事件 SDK 同步注册阻塞 readiness 的行为模式，非本次引入死锁。

## 配置清理检查

`/etc/xiyu-bid/backend.env` 未改动；本次无 `DEBUG`/`TRACE`/临时调试配置。

## 风险提示

- 本次为增量部署，改动集中在前端展示与 AI/评分解析逻辑，未触碰数据库结构。
- `SHOW_DETAILS=always` 保持历史决策（本次未纳入检查输出，如需关闭请另行授权）。

## 部署确认清单

- ✅ 环境门禁（test 172.16.38.78）
- ✅ Flyway 预检 3 步
- ✅ 本地打包 + 产物校验（含 OBS obsEnabled）
- ✅ 上传 + 部署
- ✅ 健康检查 / 迁移验证 / Smoke
- ✅ 配置清理检查
- ✅ 前端资源保留