# 测试环境第 262 次部署报告

## 部署基本信息

- **部署时间**: 2026-11-08 09:35:04 CST（DB 备份）→ 09:35:14 激活 → 09:35:39 后端启动完成 → 健康检查连续 3/3 通过（脚本 80 次尝试，约 2.6 分钟）
- **部署环境**: 测试环境（winbid-01.test: `172.16.38.78:8080`）
- **发布版本 ID**: `fad867b81`
- **提交 Commit**: `fad867b81`（PR !2979 / !2980 / !2978 等）
- **上一版本**: `b08b1760c2`（2026-11-07 22:45:02 CST 激活，对应第 261 次）
- **部署方式**: 按 `xiyu-server-deploy` SOP 执行 `scripts/release/package-release.sh` + `scripts/release/remote-deploy.sh`（`VITE_API_BASE_URL=""` 同源构建、`VITE_OBS_ENABLED=true`、`COPYFILE_DISABLE=1`、`SYSTEMCTL_SUDO=true`）
- **增量规模**: 14 个 commit，19 个文件变更（+644 / -97）

> 说明：打包时基线为 `fad867b81`（= 当时 `origin/main`）。部署执行完成后基线快进到 `43d901bd1`（!2981 wiki 回填，纯文档），不含代码改动，不影响本次已激活的产物。

---

## 变更核心内容

1. **招标文件 HTML 标签清洗与人性化文案（PR !2980）**
   - 合规核查资格提取前清洗招标文件 HTML 标签，单次实体解码契约、比较符保护、单元格精准定位；修复审查发现的证据页码与原始引文断链；同步新增 `Quality1360UnblockTest` 回归护栏。
2. **前端 hash 资产保留策略（PR !2979）**
   - `remote-deploy.sh` 部署后保留最近 2 个版本的 hash 资源（`ASSETS_RETAIN_RELEASES` 默认 2，按激活标记筛选历史 release，公开目录资产始终保留），修复旧标签页 `<link rel="preload">` 跨版本 404。
3. **早操链路修复（PR !2978）**
   - `scripts/session-gate.sh` 被 `source` 时改用 `return`，避免锚点分支早操链路中断。
4. **文档**
   - 第 261 次测试环境部署报告入库、`.wiki` 部署脚本陷阱回填（sourced exit / hash 资产保留）、`lessons-learned.md` 与 `decisions.tsv` 沉淀。
5. **数据库迁移**: 本次**无新增迁移文件**，DB 保持 V1225。
6. **前端产物**: 入口 hash 为 `assets/index-ZLT6PNtE.js`（与上一版一致）。

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操基线同步 `sync-env.sh` | ✅ 通过（init 分支 ff-only，`origin/main` 无新提交差异） |
| 基线确认 | `agent/trae-init`，HEAD=`fad867b81`=`origin/main`，工作区仅 1 个未跟踪 wiki 文件 |
| `check-git-wrapper.sh` | ⚠️ 独立 shell 中显示 `Wrapper NOT active`（`dev-env.sh` 的 PATH 不跨命令持久）；本次无 push 操作，不涉及绕过 |
| 服务器连通性（环境门禁确认后） | ✅ `ssh jetty@172.16.38.78` 正常，hostname=`winbid-01` |
| 服务器部署前状态 | `deployed-release.json` releaseId=`b08b1760c2`，`xiyu-bid-backend` active，health HTTP 200 |
| Flyway validate（服务器） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | V1225，与源码最新 `V1225__score_estimate_freshness.sql` 一致，无 pending |

---

## 部署执行

- **本地打包**：`scripts/release/package-release.sh`（`mvn clean -DskipTests package` BUILD SUCCESS 41.8s）
- **产物校验**：`release-metadata.json` 中 `apiBaseUrl=""`、`obsEnabled=true`、`sentryEnabled=false`；jar 内 284 个 Flyway 迁移文件无重复版本；Detail chunk `.upload(` 调用数 = 2；归档 156 MB
- **上传**：`scp` 归档 + `remote-deploy.sh` → `/opt/xiyu-bid/incoming/`（一次调用成功）
- **远程部署**：
  1. 数据库备份写入 `/opt/xiyu-bid/db-backups/winbid-fad867b81-20261108093504.sql.gz`（16 MB）
  2. Flyway validate 通过 → 原子切换 jar → `systemctl restart xiyu-bid-backend`
  3. 前端目录切换，激活前自动修复少量非 jetty 属主条目，入口一致性校验通过（`/assets/index-ZLT6PNtE.js`）
  4. 健康检查连续 3/3 通过（80 次尝试，约 2.6 分钟，无 Kafka readiness 延迟）
- **旧版本 assets 保留**：由 `remote-deploy.sh` 内置保留策略完成（最近 2 个版本合并），`assets` 目录现 278 个文件
- **Sidecar**：`http://127.0.0.1:8000/health` 返回 200（容器未重建，SHA-256 与上一版一致）

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `deployed-release.json` | releaseId=`fad867b81`，activatedAt=`2026-11-08T01:35:14Z` |
| `/actuator/health`（经 Nginx 8080） | HTTP 200 |
| `/actuator/health/readiness`（经 Nginx 8080） | HTTP 200 |
| `/api/auth/login`（空 body） | HTTP 400（参数校验，接口路由正常） |
| `/api/projects`（未认证） | HTTP 401（鉴权生效） |
| `/api/integration/crm/health`（未认证） | HTTP 401（鉴权生效） |
| 前端 `/`、`/login` | HTTP 200 |
| 前端入口一致性 | `assets/index-ZLT6PNtE.js` 与 release 一致 |
| 前端缓存策略 | `assets/index-ZLT6PNtE.js`：`Content-Encoding: gzip`、`Cache-Control: public, immutable` |
| 数据库迁移 | 最新 `success=1` 仍为 V1225，无新迁移 |
| 后端启动日志 | 新进程 PID 10002，`Started XiyuBidApplication in 23.486 seconds`，启动后 `level=ERROR` 计数 = 0 |
| Sidecar `/health`（8000） | HTTP 200 |
| OBS 直传 | `obsEnabled=true`，Detail chunk `.upload(` = 2 |
| 旧 hash 资产 | `Detail-L_BB6tCw.js`、`List-DXE_9Zj6.js` 均 HTTP 200 |

---

## 已知项与风险提示

1. `/etc/xiyu-bid/backend.env` 中存在 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` —— 历史遗留配置（第 13/14/15 次部署已记录并决定保留），本次**未新增**任何临时调试配置。
2. `/assets/*` 响应同时带 `Cache-Control: max-age=31536000`（nginx `expires`）与 `Cache-Control: public, immutable`（`add_header`）两条头，浏览器合并处理无功能影响；如需收敛可在后续调整 nginx 配置，本次不改动。
3. 锚点分支直接 `source scripts/dev-env.sh` 会被 session-gate 拦截（提示禁止开发），早操改为直接执行 `bash scripts/sync-env.sh .`（脚本内部自激活 dev-env 并设置 `XYYU_SYNC_ENV_ACTIVE=1`）。
4. 旧标签页跨版本 404 风险由本次上线的「保留最近 2 个版本资产」策略覆盖，仍建议用户侧刷新页面获取新入口。

---

## 回滚信息

- **上一版本产物**: `/opt/xiyu-bid/releases/b08b1760c2/`（jar + frontend 均保留）
- **数据库备份**: `/opt/xiyu-bid/db-backups/winbid-fad867b81-20261108093504.sql.gz`（16 MB，部署前快照）
- **回滚步骤**: 将 `b08b1760c2` 的 `backend/app.jar` 覆盖 `/opt/xiyu-bid/shared/backend/app.jar` → `sudo systemctl restart xiyu-bid-backend` → 恢复 `b08b1760c2/frontend` 到 `/srv/www/xiyu-bid` → 健康检查
- **回滚状态**: 未触发（本次无需回滚）

---

## 部署确认清单

- [x] 环境门禁确认（测试 172.16.38.78）
- [x] 早操基线同步完成
- [x] Flyway 三步预检通过，无 pending 迁移
- [x] 打包产物校验（同源构建 / OBS 直传 / 迁移无重复）
- [x] DB 部署前备份完成
- [x] 后端健康检查 + 前端入口一致性校验通过
- [x] 冒烟测试（health / readiness / 三个接口路由 / 前端页面）全部符合预期
- [x] 旧版本 assets 保留生效
