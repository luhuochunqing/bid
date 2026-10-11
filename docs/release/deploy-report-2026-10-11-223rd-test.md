# 第 223 次部署报告（test）

## 部署概览

| 项 | 值 |
|---|---|
| 环境 | **测试（test）** |
| 目标主机 | `172.16.38.78`（winbid-01） |
| Release ID | `0f18887de-api8080` |
| 部署时间 | 2026-10-11 11:11 → 11:19:10 CST（远程部署 11:16:13 → 11:19:10） |
| 上一版本 | `33be39582-api8080`（2026-10-09 激活） |
| 部署人 | trae（xiyu-server-deploy skill / `scripts/release/deploy-test.sh`） |
| 访问入口 | `http://172.16.38.78:8080/` |

## 基线信息

- 分支：`agent/trae-init`（锚点分支，HEAD = origin/main，禁止开发，仅部署）
- HEAD commit：`0f18887ded3286899a3780169c014f126e18cfdb` = origin/main
- 工作区：干净（`git status --short` 无输出）
- 前端入口：`assets/index-CLxXMKYe.js`（上一版本 `index-DV6_IzKV.js`）
- 后端 jar：`bid-platform-1.0.3.jar`，builtAt `2026-10-11T03:15:57Z`
- sidecar SHA-256：`8a38ad284f00c00da27e825e1923436e42a61ad9b1384218d8ca742de11ed163`（上一版 `aa8f9ce9…`），schemaVersion = 2.2
- 后端重启时间：2026-10-11 11:16:29 CST（PID 7645）
- 打包产物：`.release/xiyu-bid-release-0f18887de-api8080.tar.gz`（161M）

## PR 列表

`!2817` `!2818` `!2819` `!2820` `!2821`

## 改动范围（增量 `33be39582..0f18887de`，共 17 个 commit / 5 个 PR）

本次为**立项预填与标讯时间处理**系列上线，**无数据库迁移文件变更**。

| 类别 | 内容 |
|---|---|
| fix(initiation) | 标讯转项目补齐 `bidOpenTime` 带入，修复立项提交校验失败（!2821）；保护立项已有字段不被覆盖并补充开标时间带入测试 |
| refactor(initiation) | 消除三元比较异味并收敛月份格式化工具方法 |
| fix(bidding) | 修复标讯创建时选择 `0点0分` 时间被改写为 `23:59:59` 的问题（!2819） |
| fix(tooling) | `gitee-pr-helper merge` 对已合并 PR 的 405 误报改判幂等，含 `create_pr` 同型响应体透传缺陷修正（!2818） |
| test(tooling) | 补 `merge405closed` 契约场景并订正断言数 |
| docs / wiki | 立项预填防覆盖教训、标讯截止时间零点格式化规则沉淀与 Wiki 回填（!2820）；第 222 次部署报告（!2817） |

## Flyway 预检结果

- Step 1 `flyway-repair-runner.sh validate`（部署前独立执行）→ **VALIDATE OK - all checksums match**（280 migrations）
- Step 2 DB 已应用版本 vs 源码最新版本：DB 最新 `V1220`，源码最新 `V1220`，增量无迁移文件变更 → **对齐**
- Step 3 remote-deploy 内置 validate → 通过（pending 新迁移数为 0）
- 结论：**无需新增迁移，无 Flyway 风险**

## 部署步骤

1. 环境门禁：用户显式声明测试环境 `172.16.38.78` 并确认
2. 早操三连：`sync-env.sh`（init 分支 ff-only，已与 origin/main 对齐）+ `check-git-wrapper.sh`（7 项全绿，`--no-verify` 被拦截）
3. 服务器现状检查：上一版本 `33be39582-api8080`，服务 active（自 2026-10-09 22:18:53），health 全组件 UP
4. Flyway 预检 3 步 → 通过
5. 本地打包（`deploy-test.sh` 固化参数）：`RELEASE_ID=0f18887de-api8080`、`VITE_API_BASE_URL=`（同源）、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、`VITE_SENTRY_DSN=`（测试环境留空）
6. 产物校验：前端同源（无 dev API 地址）、`FlywayMysqlContainerTest` 容器测试通过、jar 内迁移版本无重复、sidecar 打包完成、**OBS 直传启用（Detail chunk `.upload(` 调用数 = 2）**
7. 上传 `xiyu-bid-release-0f18887de-api8080.tar.gz`（161M）+ 部署（`SYSTEMCTL_SUDO=true`）
8. Remote deploy：**DB 自动备份** → Sidecar 检测源码变更并重建镜像 → 重启 Sidecar 容器 + 健康检查 → AI Provider 守卫（`AI_PROVIDER=custom`）→ Flyway validate → 激活前端 → 停后端 → 原子更新 jar → 写部署记录 → 起后端 → 健康检查（连续 3/3，共 80 次尝试）
9. 前端旧 assets 保留：**本次由部署人手工补执行**（见"风险提示"1），从上一版本 `33be39582-api8080` 目录 `cp -rn` 补回旧 hash 资源（193 → 276 个文件）

## 验证结果

| 检查项 | 结果 |
|---|---|
| 后端 health | `HTTP 200`，`status=UP`（db / redis / jwt / diskSpace / sidecar / aiProvider 全 UP） |
| readiness | `HTTP 200` UP（无 Kafka 启动延迟） |
| `/api/auth/login`（空体） | `HTTP 400`（空密码校验错误，路由正常） |
| `/api/projects`（未认证） | `HTTP 401`（需认证） |
| `/api/integration/crm/health`（未认证） | `HTTP 401`（需认证） |
| 前端 `/` / `/login` | `HTTP 200` / `HTTP 200` |
| 前端入口一致性 | `assets/index-CLxXMKYe.js`，与 release 记录一致 |
| OBS 直传 | `.upload(` 调用数 = 2（已启用） |
| Sidecar 发布冒烟 | **5/5 通过**（health 指纹 `X-Schema-Version=2.2`；1371 fixture v1 回放 tables=14 / cells=8215 / scoreTables=1；v1/v2 双版本探针均 200） |
| 上一版本旧资源可访问 | `assets/index-DV6_IzKV.js` → `HTTP 200`（跨版本 404 已缓解） |
| 触发配置清理检查 | `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（延续历史决定保留，非临时调试残留） |

## 回滚信息

| 锚点 | 值 |
|---|---|
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/33be39582-api8080`（含前端与后端 jar） |
| DB 备份（本次） | `/opt/xiyu-bid/db-backups/winbid-0f18887de-api8080-20261011111615.sql.gz`（14M） |
| DB 备份（上一版本） | `/opt/xiyu-bid/db-backups/winbid-33be39582-api8080-20261009220230.sql.gz`（14M） |
| Schema 变更 | **无** → 无需数据库回滚 |
| 回滚方式 | 回激活 `33be39582-api8080` 的 jar + 前端目录（无 schema 依赖，可直接回退） |

回滚状态：**ready，未使用**。

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| Skill 打包固化参数（OBS=true / 同源 / COPYFILE_DISABLE） | ✅ 全程走 `deploy-test.sh`，未手工拼凑 |
| Flyway 预检 3 步法 | ✅ 部署前独立 validate + DB 版本对比 |
| 破坏性 DROP COLUMN 特别关注 | ✅ 无迁移，不适用 |
| 前端 hash 资源跨版本 404（第 17 条） | ⚠️ 脚本未内置保留步骤，**手工补执行**（见风险 1） |
| Mac TUN 模式代理拦截内网（第 19/23 条） | ⚠️ **本次复发**：Clash Verge TUN 劫持 `172.16.38.78`（`utun5`/gateway `240.0.0.1`），SSH `kex_exchange_identification: Connection closed`；用户关闭 TUN 后恢复 |

## 风险提示

1. **前端旧 assets 保留未固化（中）**：`deploy-test.sh` 与 `remote-deploy.sh` 均未包含 SOP 7.5 的 `cp -rn` 保留步骤，本次由部署人手工补做（193 → 276）。若不固化，每次部署都会删除旧 hash 资源，未刷新标签页将产生 `Unable to preload CSS/JS` 404 噪声。建议将保留步骤加入 `remote-deploy.sh`。
2. **`/assets/` 缓存响应头与配置预期略有出入（低）**：实测 `Cache-Control: max-age=31536000`，未带 `immutable`；nginx 配置模板声明为 `public, immutable`。强缓存仍生效（1 年），非阻断，建议核对 `add_header` 继承规则。
3. **GitHub 镜像方向异常（中）**：`sync-env.sh` 报 `GitHub main 领先 Gitee 1 个 commit`，说明 GitHub 存在 Gitee 没有的改动，与"Gitee 为唯一 source of truth"约定冲突。需单独用 `scripts/sync-from-github.sh` 处理。
4. **本次无迁移**，无 Flyway 相关风险。
5. **部署前网络阻断**：本次部署曾因本机 Clash Verge TUN 模式拦截内网 IP 而中断，属**环境侧**问题，非代码或服务缺陷；生产部署前需同样确认 TUN 关闭或 `172.16.0.0/12` 已直连。

## 部署确认清单

- [x] 环境门禁通过（用户显式确认 test / `172.16.38.78`）
- [x] 早操三连 + git wrapper 检查通过
- [x] 基线干净、HEAD = origin/main
- [x] Flyway 预检 3 步通过（无迁移变更）
- [x] 打包产物校验通过（同源 / OBS=true / 容器测试 / 无重复迁移）
- [x] DB 自动备份完成
- [x] 后端健康检查通过（连续 3/3）
- [x] 前端入口与 release 一致
- [x] API Smoke 通过（health / readiness / login / projects / crm）
- [x] Sidecar 发布冒烟 5/5 通过
- [x] 旧版本前端资源保留已补做
- [x] 回滚锚点齐备（上一版本目录 + DB 备份）