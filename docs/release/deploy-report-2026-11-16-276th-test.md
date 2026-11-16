# 测试环境第 276 次部署报告（评分/合规修复增量）

## 部署基本信息

- **部署时间**: 2026-11-16 18:22 CST 开始 → 18:28 激活 → 健康检查连续 3/3 通过（总尝试 80 次）
- **部署环境**: 测试环境（winbid-01: `172.16.38.78:8080`，公网入口 `https://winbid-test.ehsy.com`）
- **发布版本 ID**: `040742974`（第 276 次）
- **上一版本**: `392d0e94c`（第 275 次）
- **部署方式**: 标准链路 —— `scripts/release/package-release.sh`（同源构建 + `VITE_OBS_ENABLED=true` + `COPYFILE_DISABLE=1`）→ `scp` → `remote-deploy.sh`（`SYSTEMCTL_SUDO=true` + fail-closed DB 备份）
- **数据库迁移**: 无新增，`flyway validate` 285 个迁移校验和全部通过，DB 最新版本 `V1225` 未变

## 本轮发布内容

| 次序 | Commit / PR | 内容 |
|---|---|---|
| 1 | `8dd9334fd` / `52daafe2c`（!3030） | 修复长扫描 PDF 评分超时与客观项证据核验 |
| 2 | `ec439fc05`（!3031） | 统一章节方案标题、过滤伪方案并合并复核同格克隆 |
| 3 | `2af574728`（!3032） | 合规：区分原件页码与检索块并隔离旧核查缓存 |
| 4 | `884899682` / `df970b534`、`061877b70`（!3028 / !3029） | 部署报告文档（第 275 次测试、第 42 次生产） |

- 增量范围：`392d0e94c..040742974` 共 10 个 commit
- 改动面：backend 55 个文件，frontend 0 个文件（前端产物与上一版差异仅来自重新构建）

## 部署前预检

| 项 | 结果 |
|---|---|
| 基线 | `agent/trae-init` HEAD = `origin/main` = `040742974`，工作区干净 |
| 服务器现状 | 上一版 `392d0e94c` 运行中，backend/nginx active，健康 `UP` |
| Flyway validate | ✅ `Successfully validated 285 migrations`，checksums 全部一致 |
| DB 已应用版本 | 最新 `1225 score estimate freshness`（2026-10-26），本次无 pending 新迁移 |
| 磁盘 | `/dev/vda1` 99G，已用 74G（79%），剩余 21G |

## 打包与产物校验

`package-release.sh` 首次执行在容器测试门禁失败：`FlywayMysqlContainerTest » Could not connect to Ryuk at localhost:61470`。

- **根因**：本机 `agy`（Antigravity hub）进程占用了 `127.0.0.1:61470`，与 Testcontainers 为 Ryuk 分配的宿主机端口冲突，JVM 连接 `localhost` 命中错误服务。
- **处置**：未跳过门禁，直接重跑容器测试，Testcontainers 重新分配端口后 5/5 通过；随后完整打包成功。

| 校验项 | 结果 |
|---|---|
| 容器测试门禁（FlywayMysqlContainerTest） | 5 tests / 0 failures / 0 skipped |
| jar 内 Flyway 迁移 | 284 个 `V*.sql`，无重复版本 |
| `release-metadata.json` | `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false` |
| OBS 直传 | `Detail-*.js` 中 `.upload(` 调用数 = 2 |
| 前端入口 | `assets/index-DVuL2dOM.js` |
| 发布包 | `xiyu-bid-release-040742974.tar.gz`（156M） |

## 部署执行

1. `scp` 发布包 + `remote-deploy.sh` 到 `/opt/xiyu-bid/incoming/`
2. DB 备份（fail-closed）：`winbid-040742974-20261116182802.sql.gz`（16M）
3. Flyway validate 预检通过（任何版本切换之前）
4. 前端激活：自动保留上一版及再前一版 assets（`392d0e94c`、`b0c512488`），防止旧标签页 404
5. 停止 → 原子替换 jar → 写 `deployed-release.json` → 启动 `xiyu-bid-backend`
6. 健康检查连续 3/3 通过（总尝试 80 次，Kafka 启动期延迟属已知行为）
7. 前端一致性校验通过：`index.html` 引用 `assets/index-DVuL2dOM.js` 与 release 一致

## 验证结果

| 检查 | 期望 | 实测 |
|---|---|---|
| `/actuator/health` | 200 UP | 200，`db`/`aiProvider`/`diskSpace` 均 UP |
| `/actuator/health/readiness` | UP | UP |
| `POST /api/auth/login`（空体） | 400 参数校验 | 400（`Username/Password is required`） |
| `/api/projects`（未认证） | 401/403 | 401 |
| `/api/integration/crm/health`（未认证） | 401 | 401 |
| 前端 `/` 与 `/login` | 200 | 200 / 200 |
| 入口 JS | `index-DVuL2dOM.js` | 与 release 一致 |
| 公网入口 `https://winbid-test.ehsy.com/` | 200 且入口一致 | 200，`index-DVuL2dOM.js` |
| 后端日志 | 无 ERROR | 无 ERROR；仅 1 条 WARN 业务异常（本次 smoke 空体登录 400，预期） |
| 启动后 DB 迁移 | 无新增 | 最新仍为 `V1225` |

## 配置与清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍保留在 `/etc/xiyu-bid/backend.env`（测试环境历史决定项，非本次引入）。
- 无其他 `DEBUG` / `TRACE` 临时配置。

## 环境阻塞记录

首次执行时本机无法 SSH 到 `172.16.38.78`：

- 现象：`kex_exchange_identification: Connection closed by remote host`
- 根因：本机无内网通道，流量被 Clash TUN（`utun1024`，网关 `240.0.0.1`）接管；`nc` 对所有端口（含 9999）均"成功"属 TUN 假握手，不能作为连通性证据
- 处置：恢复内网通道后直连正常（`hostname=winbid-01`），部署按标准链路完成

## 风险与回滚

- 无新增迁移，无破坏性 schema 变更，DB 回滚压力低。
- 回滚锚点：
  - 上一版 release：`/opt/xiyu-bid/releases/392d0e94c`（jar + 前端完整保留）
  - DB 备份：`/opt/xiyu-bid/db-backups/winbid-040742974-20261116182802.sql.gz`（16M）
  - 回滚步骤见 `docs/release/ROLLBACK.md`
- 前端旧 hash 资源已按机制保留最近 2 个已激活版本，跨版本 preload 404 风险已覆盖。

## 待跟进

- `agy` 进程与 Testcontainers Ryuk 的随机端口冲突可能复现；再次遇到时优先重试容器测试（不要用 `XIYU_SKIP_CONTAINER_TEST=true` 绕过）。
- 本报告需提 PR 合入 main（建议标题：`docs(release): 第 276 次测试环境部署报告`）。
