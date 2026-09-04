# 第 158 次部署报告（test 环境）

**日期**：2026-09-04
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`3030a55a`
**部署时间**：08:48 - 08:53 CST
**状态**：✅ 成功

## 部署概览

- 增量 4 个 commit（1 条实质 PR + 2 条文档/wiki），**无新增 Flyway 迁移**（DB 与源码最新版本均为 V1210）。

- 本次核心为**资质核查合规逻辑修复**（PR !2536）：

  - **对接真实业务资质库 + 国字头前缀剥离**（`QualificationMatchPolicy` / `KnowledgeBaseOnDemandLoader`）：消除"资质未维护"的存量误报

  - 配套测试补全（`QualificationMatchPolicyTest` / `KnowledgeBaseRealQueryTest`）

- Flyway 预检及迁移验证全部通过（271 项迁移全量校验，无待应用迁移）。

## 基线信息

| 项目        | 值                                                                             |
| --------- | ----------------------------------------------------------------------------- |
| 基线 commit | `3030a55a`（origin/main，锚点分支 ff-only 同步，零差异）                                   |
| 前次部署      | `b5e189a7a`（2026-09-03 23:43 激活，第 157 次部署）                                    |
| 增量 commit | 4 个（`b5e189a7a..3030a55a`）                                                    |
| 新增迁移      | 无（DB 与源码均为 V1210）                                                             |
| 工作区状态     | 干净                                                                            |
| 早操三连      | 锚点分支 ff-only 同步至 `3030a55a` + check-git-wrapper 通过（wrapper 未激活，部署只读无需 commit） |

## 改动范围（4 个增量 commit / 1 条实质 PR）

| PR     | 描述                                                           |
| ------ | ------------------------------------------------------------ |
| !2535  | docs(release): 第 157 次部署报告 (test)                            |
| !2536  | fix(compliance): 对接真实业务资质库与国字头前缀剥离消除未维护误报                    |
| <br /> | docs(wiki): health-check frontmatter date update（随 !2536 合并） |

关键代码：修改 `QualificationMatchPolicy.java`、`KnowledgeBaseOnDemandLoader.java`（资质匹配策略 + 知识库按需加载），新增/修改 2 个测试类；另更新 `.wiki/pages/` 7 个页面 frontmatter 与 `docs/lessons/lessons-learned.md`。

## Flyway 预检与迁移验证

**Step 1**：`flyway-repair-runner.sh validate` → **通过**，271 migrations all checksums match（本次首次探测即全绿，无 `/tmp` 目录过时问题）。

**Step 2**：DB 最新 applied = V1210（`success=1`，2026-09-03 21:52 已应用），failed 记录 = 0，源码最高版本 = V1210 → 本次无待应用迁移。

**Step 3**：remote-deploy.sh 内置 validate → 通过（日志 `VALIDATE OK - all checksums match`）。

## 部署步骤时间线（CST）

| 时间     | 步骤                                                                                    |
| ------ | ------------------------------------------------------------------------------------- |
| 08:48  | 环境门禁确认（test / 172.16.38.78）+ 服务器连通性探测                                                 |
| 08:49  | 服务器现状探测（前次 release `b5e189a7a`，health UP）                                             |
| 08:49  | Flyway 预检：validate 通过（271 migrations）+ DB 版本对比 V1210 / failed=0                       |
| 08:50  | 本地打包完成（\~154M，obsEnabled=true，同源构建）+ jar 迁移无重复校验                                      |
| 08:52  | scp 上传归档（161.8M）到 `/opt/xiyu-bid/incoming/`                                           |
| 08:53  | remote-deploy 执行：DB 备份 → Flyway validate → 权限修复 → Frontend 激活 → 覆盖 jar → systemctl 重启 |
| 08:53  | 脚本内置健康检查 144 attempts 连续 3 次 UP（Kafka readiness 延迟在窗口内覆盖）                             |
| 08:53+ | Smoke 全绿 + 旧 assets 保留 + 配置清理检查 + DB 备份核验                                             |

## 验证结果

**后端健康（127.0.0.1:18080 与 Nginx 8080 双路径）**：`{"status":"UP"}`，readiness 200。

**迁移应用验证**：本次无新增迁移；V1210 保持 `success=1`（installed\_on 2026-09-03 21:52:25）。

**API Smoke（经 Nginx 8080，`--noproxy '*'`）**：

| 接口                                 | 期望     | 实际       |
| ---------------------------------- | ------ | -------- |
| `/actuator/health`                 | 200 UP | ✅ 200 UP |
| `/actuator/health/readiness`       | 200    | ✅ 200    |
| `POST /api/auth/login`（空 body）     | 400    | ✅ 400    |
| `/api/projects`（未认证）               | 403    | ✅ 403    |
| `/api/integration/crm/health`（未认证） | 401    | ✅ 401    |

**前端 Smoke（经 Nginx 8080）**：

- 根路径 `/`：200

- 登录页 `/login`：200

- 线上入口：`assets/index-CKov07Pq.js` 与构建产物完全一致

- 旧版本 hash 资源已从 `releases/b5e189a7a/frontend/` 以 `cp -rn` 保留到 `/srv/www/xiyu-bid/assets/`（防跨版本 404，保留 24h 惯例）

**产物与特性校验**：

- `release-metadata.json`：`obsEnabled=true`、`apiBaseUrl=""`（同源构建）

- OBS 直传校验：Detail chunk `.upload(` 调用数 = 2

- jar 内迁移文件含 V1210，无重复版本

- 无 macOS `._*` 残留文件（`COPYFILE_DISABLE=1` 生效，服务器 `find` 检查 = 0）

## 本次异常与处置

**无阻塞性异常。一次性提示**：scp 解包阶段日志出现大量 `Ignoring unknown extended header keyword LIBARCHIVE.xattr.com.apple.provenance` 警告——这是 macOS 打包含 xattr 元数据导致的**无害解包警告**（tar 已忽略，不影响文件内容与部署）。`COPYFILE_DISABLE=1` 已防 `._*` 残留；此 xattr 进拍可后续在打包时加 tar `--no-xattrs` 消除日志噪声（非必改）。

## 回滚信息

| 锚点         | 位置                                                                         |
| ---------- | -------------------------------------------------------------------------- |
| 前后端制品      | `/opt/xiyu-bid/releases/b5e189a7a/`（jar + frontend）                        |
| DB 备份      | `/opt/xiyu-bid/db-backups/winbid-3030a55a-20260904085254.sql.gz`（本次部署自动生成） |
| 迁移回滚脚本     | 本次无新增迁移，无需回滚脚本                                                             |
| Nginx 配置锚点 | `/etc/nginx/conf.d/xiyu-bid.conf.bak-20260817`（未变更）                        |
| 回滚 owner   | 部署执行 Agent（trae）                                                           |

回滚状态：**就绪，未需要**。

## 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：保留（第 13/14/15 次部署用户决定，测试环境常态配置）。

- 无其他 SHOW\_DETAILS/DEBUG/TRACE 临时调试配置。

## 部署确认清单

- [x] 环境门禁确认（test / 172.16.38.78）

- [x] 早操三连 + 基线零差异

- [x] Flyway 预检通过（validate + DB 版本对比）

- [x] 产物校验（迁移无重复 / obsEnabled / 同源构建）

- [x] DB 备份先行（winbid-3030a55a-\*.sql.gz）

- [x] 服务重启 + 健康 UP

- [x] 迁移应用验证（本次无新增，V1210 保持 success）

- [x] Smoke 全绿

- [x] 旧 assets 跨版本保留

- [x] 部署报告归档

## 风险提示

- 本次无数据库结构变更，回滚仅需切回前一版本制品（`b5e189a7a/`）+ DB 备份。

- `QualificationMatchPolicy` 对接真实业务资质库为国字头前缀剥离的匹配策略调整，建议后续用含国字头前缀资质的真实标书在测试环境做一次端到端核验，确认误报消除且无真实资质被误判。

- 服务器 `/opt/xiyu-bid/incoming/` 历史部署包与 `releases/` 旧版目录建议另行清理（保留最近 N 版 + 回滚锚点版本）。

