# 测试环境第 260 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-07 10:12:14 CST（激活）→ 10:15 左右健康检查连续 3/3 通过（脚本 80 次尝试，约 2.7 分钟）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `0d833ae84-api8080`
- **提交 Commit**: `0d833ae84`（!2973）
- **上一版本**: `adbb2facf-api8080`（2026-11-06 07:44 CST 激活，对应第 259 次）
- **部署方式**: 按 `xiyu-server-deploy` SOP 执行 `scripts/release/package-release.sh` + `scripts/release/remote-deploy.sh`（`VITE_API_BASE_URL=""` 同源构建、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、`SYSTEMCTL_SUDO=true`）
- **增量规模**: 11 个 commit（含 PR !2969、!2970、!2971、!2972、!2973），92 个文件变更（+4436 / -338）

---

## 变更核心内容

1. **标书质量核查校准（PR !2973）**
   - 校准标书质量核查证据与人工确认边界，避免证据缺失时被误判为通过。
2. **仓储到期提醒去重（PR !2972）**
   - 防止到期提醒与企微推送重复投递，新增并发场景规格文档。
3. **评分解析修复（PR !2971）**
   - 方案分档主观特征补全，修复项目 1119 主客观分误判。
4. **OCR 兼容中转契约（PR !2970）**
   - 兼容 APIHub 中转实际成功码与顶层 token 结构。
5. **文档（PR !2969）**
   - 第 259 次测试环境部署报告入库，补充测试环境 OCR 网关开关变更记录。
6. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225。
7. **前端产物**: 入口 hash 为 `assets/index-ZLT6PNtE.js`。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操 `sync-env.sh` | ✅ `init branch is up-to-date with origin/main` |
| 本地门禁自检（`sync-env.sh` 内置） | 7/7 通过 |
| 基线确认 | `agent/trae-init`，HEAD=`0d833ae84`=`origin/main`，工作区干净 |
| 服务器连通性（环境门禁后） | ✅ `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01` |
| Flyway validate（服务器，jetty 身份） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225，与源码最新 `V1225__score_estimate_freshness.sql` 一致，无 pending |
| 服务器部署前状态 | `deployed-release.json` releaseId=`adbb2facf-api8080`，`xiyu-bid-backend` active，health UP |
| 磁盘余量 | `/` 27G 可用（72% 已用） |
| `/tmp/flyway-repair-extract` 归属 | `jetty:jetty`（无 root 污染，规避第 259 次中止问题） |

### 部署前网络阻断（非事故，已解除）

首次连通性探测失败，部署未开始：

- 现象：`ssh` 返回 `kex_exchange_identification: Connection closed by remote host`（认证前断开，6 把密钥结果一致）；`curl :8080` 超时 `HTTP 000`；`nc` 显示 TCP 握手"成功"。
- 根因：本机仅 `192.168.2.3`（家庭网）+ `240.0.0.1`（Clash Verge TUN fake-ip）两个 IPv4，`172.16.38.78` 被 Clash TUN 接管且无真实内网路径；aTrust 进程在跑但**隧道网卡未建立**。
- 处置：用户登录 aTrust 建立隧道（出现 `utun7`，地址 `2.0.0.1`，路由 `172.16.32.0/19 → utun7`）后重试即恢复。
- 结论：`nc` 的 "succeeded" 在 TUN 场景下不代表可达，SSH 必须在认证前握手成功才算通。

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`（`mvn clean -DskipTests package` BUILD SUCCESS 42.094s）
- **产物校验**：`release-metadata.json` 中 `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`；jar 内 284 个 Flyway 迁移文件无重复版本；Detail chunk `.upload(` 调用数 = 2；归档 160 MB，无 macOS `._*` 残留；Sidecar SHA-256 `e5fc03f3…` 与线上一致（源码无变动，跳过容器重建）
- **上传**：`scp` 归档 + `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`
- **远程部署**：
  1. 数据库备份写入 `/opt/xiyu-bid/db-backups/winbid-0d833ae84-api8080-20261107101204.sql.gz`（16 MB）
  2. Flyway validate 通过 → 原子切换 jar → `systemctl restart xiyu-bid-backend` → 健康检查连续 3/3 通过（80 次尝试）
  3. 前端目录切换并通过入口一致性校验（`/assets/index-ZLT6PNtE.js`）
- **旧版本 assets 保留**：从 `adbb2facf-api8080` release 目录 `cp -rn` 旧 hash 资源；Smoke 中发现两版前的 `Detail-L_BB6tCw.js`（属 `82b9dbe75`）返回 404，又从该 release 补拷一次，`assets` 目录现 362 个文件，该文件复验 HTTP 200。
- **Sidecar**：`http://127.0.0.1:8000/health` 返回 200

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`0d833ae84-api8080`，activatedAt=`2026-11-07T02:12:14Z` |
| `/actuator/health`（服务器本地） | UP |
| `/actuator/health`（经 Nginx 8080） | HTTP 200（0.05s） |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200（0.03s，无 Kafka readiness 延迟） |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，接口路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200 |
| 前端入口一致性 | `assets/index-ZLT6PNtE.js` 与 release 一致 |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，无新迁移 |
| 后端启动日志 | `Started XiyuBidApplication in 22.969 seconds`，自启动以来 `ERROR` 计数 = 0 |
| 服务进程 | `xiyu-bid-backend` active，MainPID=23424，JDK 21 |
| Sidecar `/health`（8000） | HTTP 200 |
| OBS 直传 | `obsEnabled=true`，Detail chunk `.upload(` = 2 |
| 前端资产保留 | 旧 hash 文件已保留（362 个 assets 文件），抽样旧 chunk HTTP 200 |
| 临时调试配置 | 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决定保留），无 DEBUG/TRACE 残留 |

---

## 回滚信息

- 回滚锚点：release id `adbb2facf-api8080`；上一已知良好版本 `82b9dbe75-api8080`
- 后端旧 jar：`/opt/xiyu-bid/releases/adbb2facf-api8080/backend/app.jar`
- 前端旧 assets：已保留在 `/srv/www/xiyu-bid/assets/`
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-0d833ae84-api8080-20261107101204.sql.gz`
- 回滚命令：
  ```bash
  ssh jetty@172.16.38.78 'sudo systemctl stop xiyu-bid-backend && \
    sudo cp /opt/xiyu-bid/releases/adbb2facf-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
    sudo cp -r /opt/xiyu-bid/releases/adbb2facf-api8080/frontend/* /srv/www/xiyu-bid/ && \
    sudo systemctl start xiyu-bid-backend'
  ```
- 回滚状态：**就绪（未执行）**

---

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ validate + DB 版本对比 + remote-deploy 内置预检，全部通过 |
| 第 259 次 `/tmp/flyway-repair-extract` root 残留 | ✅ 预检以 `jetty` 身份执行，部署前确认目录属主为 jetty，未再触发中止 |
| 同源构建（`baseURL=""`） | ✅ `apiBaseUrl=""`，入口一致性校验通过 |
| OBS 直传双保险 | ✅ 显式 `VITE_OBS_ENABLED=true`，且校验 `.upload(` = 2 |
| macOS `._*` 残留 | ✅ `COPYFILE_DISABLE=1`，归档内 `._` 计数 0 |
| 前端 hash 资源跨版本 404 | ⚠️ 观察到两版前 chunk 404，已按「上一版本 + 额外一版」补齐 assets |
| `SYSTEMCTL_SUDO=true` | ✅ 由脚本默认值 + 显式传参双保险，重启成功 |

---

## 风险提示

1. **网络依赖**：本机必须建立 aTrust 隧道（`utun7`）才能直连测试环境；Clash TUN 会接管 `172.16.0.0/12` 流量，未连 VPN 时 `nc` 会误报可达，排障时以 SSH 认证前握手为准。
2. **跨版本 assets 保留范围**：脚本只保留"上一版本"资源；两版以上的旧标签页仍可能 404，本次已临时补齐 `82b9dbe75` 的资源，后续可考虑在 `remote-deploy.sh` 中固定保留最近 2 个版本。
3. **本次无数据库结构变更**：DB 保持 V1225，回滚仅需切回旧 jar + 旧前端，无需 DB 回滚。
4. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`** 仍保留（历史决定），health 输出含组件细节，测试环境可接受，生产不得沿用。

---

## 部署确认清单

- [x] 环境门禁确认（用户选择「确认：测试环境 172.16.38.78」）
- [x] 早操同步 + 基线与 origin/main 一致
- [x] 服务器连通性恢复（aTrust 隧道）
- [x] Flyway 预检 3 步通过
- [x] 本地打包 + 产物校验（OBS、迁移无重复、同源 baseURL、无 `._*`）
- [x] 部署前数据库备份
- [x] 原子切换 + 服务重启 + 健康检查 3/3
- [x] Smoke 测试与 Sidecar 健康验证
- [x] 旧版本 assets 保留
- [x] 部署报告归档
