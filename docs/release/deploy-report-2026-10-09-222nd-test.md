# 第 222 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `33be39582-api8080` |
| 部署时间 | 2026-10-09 22:02:29 → 22:21:36 CST |
| 上一版本 | `f645780f4`（2026-09-29 激活） |
| 部署人 | trae（xiyu-server-deploy skill / `deploy-test.sh`） |

## 基线信息

- 分支：`agent/trae-init`（锚点分支，HEAD = origin/main，禁止开发，仅部署）
- HEAD commit：`33be39582101e14ebbfe4fd321feee3812ca242a` = origin/main
- 前端入口：`assets/index-DV6_IzKV.js`（较上一版本 `index-CDf8nFsr.js` 有变更）
- 后端 jar：`bid-platform-1.0.3.jar`，jar 内 `git.commit.id` = `33be39582101e14ebbfe4fd321feee3812ca242a`（与 HEAD 一致）
- sidecar SHA-256：`aa8f9ce9b13ba0a3…`（上一版 `4323526a9c5ee51f…`），schemaVersion = 2.2
- 后端重启时间：2026-10-09 22:18:53 CST（PID 4548）

## 改动范围（增量 `f645780f4..33be39582`，共 60 个 commit / 14 个 PR）

本次部署为评分解析（scoreparse）与案例来源回填（casework）系列的集中上线，**无数据库迁移文件变更**。

| 类别 | 内容 |
|---|---|
| fix(scoreparse) | 评分汇总误判修复与正确结果确定性回补（!2816）；项名恢复边界证明重构（!2804/!2805）；评分小计单项满分误识别防劫持（!2810/!2813）；汇总项 UI 遮挡与主客观权重四舍五入（!2811）；未启用提取功能时不再弹全局错误（!2809） |
| fix(review-ext) | 单项合计语义回归修复（真实总分被删）（!2815）；正文与兜底解析复用单项语义 |
| fix(casework) | 案例来源回填原子校验 + 扫描快照防过期写入（!2812）；应用端口隔离来源回填持久化 |
| fix(gates) | 撤除后严格审查两项 P2：回放段检查 Maven 退出码并隔离旧报告、Sidecar 探测超时真正生效（!2808） |
| docs / wiki | 评分解析语义规则全覆盖、任务基线对齐、wiki 回填与知识沉淀 |

PR 清单：`!2803` ~ `!2816`。

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate` → **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 增量 `f645780f4..33be39582` 无迁移文件变更，DB 版本对齐源码最新（V1220）
- Step 3 remote-deploy 内置 validate → 通过
- 结论：**无需新增迁移，无 Flyway 风险**

### 预检异常与处置（本次特有）

部署前 standalone validate 报大量 `Detected applied migration not resolved locally`。根因：服务器 `/tmp/migration-mysql/` 目录**存在但为空**（迁移源丢失），Flyway 无法在本地解析任何已应用迁移。

处置：将仓库 `backend/src/main/resources/db/migration-mysql/` 同步到服务器 `/tmp/migration-mysql/`（288 个文件，最高 V1220），清理 macOS `._*` 残留后重跑 validate → **通过**。

## 部署步骤

1. 环境门禁：用户显式声明测试环境 `172.16.38.78`
2. 服务器现状检查：上一版本 `f645780f4`，后端 health UP（首次探测遇 Redis 瞬时 `Connection reset by peer`，复测全组件 UP）
3. Flyway 预检 3 步 → 同步迁移源后通过
4. 本地打包（`deploy-test.sh` 固化参数）：`RELEASE_ID=33be39582-api8080`、`VITE_API_BASE_URL=`（同源）、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、`VITE_SENTRY_DSN=`（测试环境留空）
5. 产物校验：前端同源无 dev API 地址、`FlywayMysqlContainerTest` 容器测试通过、jar 内迁移版本无重复、sidecar 打包完成、**OBS 直传启用（Detail chunk `.upload(` 调用数 = 2）**
6. 上传 `xiyu-bid-release-33be39582-api8080.tar.gz`（161M）+ 部署（`SYSTEMCTL_SUDO=true`）
7. Remote deploy：DB 自动备份 → Sidecar 检测到源码变更并重建镜像 → 重启 Sidecar 容器 + 健康检查 → AI Provider 守卫 → Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（连续 3/3，共 81 次尝试）
8. 前端旧 assets 保留：从上一版本 `f645780f4` 目录 `cp -rn` 补回旧 hash 资源（193 → 277 个文件）

### 部署异常与处置

- `deploy-test.sh` 第 23 行 `source dev-env.sh` 会加载 `session-gate.sh`，后者在锚点分支上 `exit 1`，导致脚本在 Step 0 前即退出。部署 SOP 明确允许从锚点分支发版，故以 `SKIP_SESSION_GATE=1` 跳过开发态 session 门禁后重跑成功。
- Step 6 脚本自带健康探测瞬时返回 DOWN（`curl -fsS` 命中一次 503），系 Redis 健康抖动；`remote-deploy` 内置健康检查已连续 3/3 通过，独立复核 `/actuator/health` 实测 HTTP 200 UP。

## 验证结果

| 验证项 | 结果 |
|---|---|
| 后端健康（经 Nginx 8080） | **UP（HTTP 200）**，全组件 UP（db/redis/sidecar/aiProvider/jwt/diskSpace） |
| readiness | HTTP 200（`readinessState` UP） |
| 健康检查（remote-deploy 内置） | 连续 3/3，第 81 次尝试通过，service active/running |
| Flyway 迁移 | 无新迁移，V1218-V1220 全部 success=1 |
| 前端一致性 | `src="/assets/index-DV6_IzKV.js"` 与 release 匹配 |
| Smoke - `/actuator/health` | HTTP 200（预期） |
| Smoke - `/actuator/health/readiness` | HTTP 200（预期） |
| Smoke - `POST /api/auth/login`（空体） | HTTP 400（预期） |
| Smoke - `/api/projects` 未认证 | HTTP 401（预期） |
| Smoke - `/api/integration/crm/health` | HTTP 401（预期） |
| 前端 `/` 与 `/login` | HTTP 200 |
| Sidecar 发布冒烟 | **PASSED**（health 指纹 200 UP + `X-Schema-Version=2.2`、fixture v1 回放 tables=14 cells=8215 scoreTables=1、v1 探针 200、v2 探针 200；通过 5 / 失败 0） |
| OBS 直传启用 | ✅ Detail chunk `.upload(` 调用数 = 2 |
| 旧 assets 保留 | ✅ 193 → 277 个文件 |
| 数据库备份 | ✅ `/opt/xiyu-bid/db-backups/winbid-33be39582-api8080-20261009220230.sql.gz` |
| 配置清理检查 | 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决定保留项，非临时 DEBUG/TRACE） |

## Smoke 接口路由说明

- admin 密码未配置完整登录 smoke，改用 400/401 验证接口路由可达性（skill 经验第 4 条）
- 本次 `deploy-test.sh` 已内置 Sidecar 发布冒烟（smoke-sidecar.sh），覆盖 health 指纹 + 1371 回放 + 双版本探针

## 回滚信息

- 回滚锚点：release 目录 `/opt/xiyu-bid/releases/f645780f4/`；更早已知良好 `658aa15ec`
- 后端旧 jar：`/opt/xiyu-bid/releases/f645780f4/backend/`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-33be39582-api8080-20261009220230.sql.gz`（部署前自动 mysqldump）
- 回滚状态：**就绪（未执行）**

## 经验沉淀应用情况

- 环境门禁硬约束：✅ 用户显式声明测试环境后才执行
- 锚点分支保护：✅ 全程仅做只读部署与发版，未在锚点开发
- OBS 直传：✅ 明确 obsEnabled=true（防第 84 次测试漏传事故）
- 前端同源构建：✅ 产物验证无 dev API 地址
- macOS 代理：✅ curl 均加 `--noproxy '*'`
- Mac `._*` 残留：✅ `COPYFILE_DISABLE=1`；迁移源同步后额外清理了服务器侧 `._*`
- 打包强制 clean：✅ jar 内迁移无重复（防 V1096 事故复发）
- 容器测试门禁：✅ `FlywayMysqlContainerTest` 打包前置验证 Spring 装配
- Sidecar 发布冒烟：✅ 5/5 通过
- 旧 assets 保留：✅ 执行 `cp -rn` 防跨版本 404

## 风险提示

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留（历史决定项），无新增临时调试配置。
- 本次为评分解析（scoreparse）系列大范围修复（项名恢复、小计满分防劫持、汇总语义回补），建议在测试环境对评分解析相关历史样本做一次功能回归，重点验证：汇总总分、小计列、单项满分识别、项名恢复。
- 服务器 `/tmp/migration-mysql/` 会在重启/清理后丢失；后续部署若遇 `not resolved locally`，按本次处置同步迁移源即可，无需 repair。
- `readiness` 本次未出现 503 长延迟（Kafka SDK 延迟为已知行为，本次未发生）。

## 部署确认清单

- [x] 环境门禁通过（test）
- [x] 基线 = origin/main（`33be39582`）
- [x] Flyway validate 通过
- [x] 后端 health UP + readiness UP
- [x] Smoke 全部符合预期
- [x] Sidecar 冒烟通过
- [x] 前端入口一致
- [x] OBS 直传启用校验通过
- [x] 旧 assets 已保留
- [x] 配置清理检查完成
- [x] 数据库备份已生成