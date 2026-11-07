# 第 39 次生产环境部署报告 — 2026-11-07

> 编号说明：`docs/release/` 最后一篇生产报告为「36th-prod」（`c28e89db6`，2026-11-04）。此后服务器上还有两次生产发布未留报告：`96fe0cc06`（2026-11-05 20:15）与 `889ccb4c3`（2026-11-05 21:05）。本次按实际发布顺序续编为 **39th**。

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产（prod）** |
| 目标主机 | `winbid-01` / `172.16.10.149` |
| 对外入口 | `https://winbid.ehsy.com` |
| Release ID | `2960e7ea5` |
| 基线 commit | `2960e7ea5`（与 `origin/main` HEAD 完全一致） |
| 前一版本 | `889ccb4c3`（2026-11-05 21:05 CST 激活） |
| 部署时间 | 2026-11-07 11:38:39–11:38:42 CST（激活），健康检查随后通过 |
| 状态 | ✅ 成功（Flyway validate + 健康检查 + Smoke 全通过，**零数据库迁移**） |
| 回滚状态 | 未执行，锚点就绪（`/opt/xiyu-bid/releases/889ccb4c3` 完整前后端产物） |

环境门禁：用户通过问答卡片明确选择「确认：生产环境 172.16.10.149」，基线选择「origin/main 最新 2960e7ea5」。

---

## 部署概览

从生产版本 `889ccb4c3` 追平至 `origin/main` 最新 `2960e7ea5`，增量 **22 个 commit / 9 个 PR**。

- **零数据库迁移**：`889ccb4c3..2960e7ea5` 无 `db/migration-mysql/` 变更；部署前后 `flyway_schema_history` 最高版本仍为 `V1225`。
- **无 Sidecar 变更**：sidecar SHA `e5fc03f3…`、schemaVersion `2.2` 与前版一致，未触发容器升级。
- **前端产物**：同源构建（`apiBaseUrl=""`），入口 `assets/index-ZLT6PNtE.js`；`obsEnabled=true`，Detail chunk `.upload(` = 2。
- **Sentry**：本次 `sentryEnabled=false`（沿用上一版现状，用户确认保持关闭）。
- **旧资源保留**：已从 `releases/889ccb4c3/frontend/assets/` 补回旧 hash 文件到 `/srv/www/xiyu-bid/assets/`，避免旧标签页 preload 404。

---

## 变更范围与 PR

| PR | 主题 |
|---|---|
| !2966 | OCR 接入 APIHub 中转并保留前端账号配置 |
| !2967 | 第 36 次生产部署报告（补） |
| !2968 | review-gate：纯文档变更免三技能审查 |
| !2969 | 第 259 次测试环境部署报告 |
| !2970 | OCR 兼容中转实际成功码与顶层 token |
| !2971 | scoreparse 方案分档主观特征补全 + 项目 1119 主客观分误判修复 |
| !2972 | warehouse 到期提醒与企微重复投递防护 |
| !2973 | compliance 标书质量核查证据与人工确认边界校准 |
| !2974 | 第 260 次测试环境部署报告 |

---

## 部署前预检

| 检查项 | 结果 |
|---|---|
| 早操 `sync-env.sh` | ✅ init 分支与 `origin/main` 一致（锚点分支以 `CHAT_ONLY=1` 跳过开发门禁，仅只读同步） |
| 门禁 `check-git-wrapper.sh` | ✅ 7/7 通过（`git` 命中 `scripts/git` 包装器） |
| 基线确认 | HEAD = `2960e7ea5` = `origin/main`，工作区干净 |
| Flyway validate（生产机） | `VALIDATE OK - all checksums match`（285 个迁移） |
| DB 已应用版本 | `V1225`（2026-10-27 应用），与源码最新版本一致，无 pending |
| 服务器健康（部署前） | `/actuator/health` UP（db / redis / sidecar / aiProvider 全 UP），服务 active |

---

## 部署执行

按 `xiyu-server-deploy` 技能标准 SOP 手工执行（未使用 `deploy-prod.sh` 一键脚本）：

- 打包：`RELEASE_ID=2960e7ea5 VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 bash scripts/release/package-release.sh`
  - 同源构建 + `mvn clean -DskipTests package` + Flyway 容器测试，发布包 160 M
  - 产物校验：jar 内 284 个 `V*.sql` 无重复；`obsEnabled=true`；`sentryEnabled=false`
- 上传：`scp` 发布包 + `remote-deploy.sh` 至 `/opt/xiyu-bid/incoming/`
- 远程部署：`remote-deploy.sh`（`SYSTEMCTL_SUDO=true`），Flyway validate 通过 → 激活前端/后端 → 重启服务 → 健康检查连续 3/3 通过（第 15 次探测）
- 一致性校验：前端入口 `assets/index-ZLT6PNtE.js` 与 release 一致

---

## 部署后验证

| 检查项 | 结果 |
|---|---|
| `/actuator/health`（服务器本地 18080） | HTTP 200，`status=UP` |
| `/actuator/health/readiness` | HTTP 200 UP |
| Nginx 代理 `127.0.0.1:8080/actuator/health` | HTTP 200 |
| `POST /api/auth/login`（空体） | HTTP 400（参数校验，路由正常） |
| `GET /api/projects`（未认证） | HTTP 401 |
| `GET /api/integration/crm/health` | HTTP 401 |
| 前端 `/`、`/login` | HTTP 200 / 200 |
| 前端入口 | `assets/index-ZLT6PNtE.js`（与 release 一致） |
| 公网域名 `https://winbid.ehsy.com/` | HTTP 200 |
| 部署记录 | `deployed-release.json` → `releaseId=2960e7ea5`，`obsEnabled=true` |
| 旧版 assets | ✅ 已从 `889ccb4c3` 补回 |
| `._*` 残留 | ✅ 无 |
| 临时调试配置 | `/etc/xiyu-bid/backend.env` 仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史保留项） |
| 磁盘 | `/` 使用 27%（25G / 99G） |

> 说明：本地 Mac 直连 `172.16.10.149:8080` 不可达（`000`，生产 8080 未对外开放），因此 API/前端 Smoke 在服务器本地经 Nginx 与 `127.0.0.1:18080` 执行；对外可达性以 `https://winbid.ehsy.com` HTTP 200 佐证。

---

## 部署后配置变更（2026-11-07 12:25 / 13:05 CST）

### 变更一：开启 OCR 中转开关（12:25）

- 生产 `/etc/xiyu-bid/backend.env` 新增 `APP_SCORE_PARSE_BAIDU_OCR_GATEWAY_ENABLED=true`（原默认 `false`）
- 备份：`/etc/xiyu-bid/backend.env.bak-20261107`；重启后健康检查通过（22.079s）

### 变更二：修正中转地址并注入账号（13:05）

- **根因**：`boran_pro` 账号属于**生产 APIHub**（`apihub.ehsy.com`），而默认地址是测试中转 `apihub-test.ehsy.com`
  - `apihub-test.ehsy.com/user/login` → `{"code":"50003","msg":"用户名密码错误"}`（网络正常，0.044s）
  - `apihub.ehsy.com/user/login` → `{"code":"0","msg":"success","token":"…","tokenRemainTime":86400}`
  - 测试库 `lastTestMessage` 记录的「APIHub 登录失败，请检查测试环境网络和中转账号配置」（2026-11-06）即此原因，非网络故障
- 变更项（生产 `backend.env`，权限 `640 root:jetty`）：
  - `APP_SCORE_PARSE_BAIDU_OCR_TOKEN_URL=http://apihub.ehsy.com/user/login`
  - `APP_SCORE_PARSE_BAIDU_OCR_TABLE_URL=http://apihub.ehsy.com/search`
  - `APP_SCORE_PARSE_BAIDU_OCR_USERNAME=boran_pro`
  - `APP_SCORE_PARSE_BAIDU_OCR_PASSWORD=<已注入，勿外泄>`
- 备份：`/etc/xiyu-bid/backend.env.bak-20261107-ocr`
- 执行：`sudo systemctl restart xiyu-bid-backend`，健康检查第 13 次（约 26s）通过
- 验证：`/proc/<MainPID>/environ` 含全部 5 个 OCR 配置项；`18080` 与 `8080` health 均 200；近 3 分钟无 ERROR 日志；生产服务器实登 APIHub 返回 `code=0` 并取得 token
- 结果：生产 OCR 中转**功能已真正启用**（开关 + 账号 + 地址三项齐备）
- **测试环境**：按用户决定保持现状（仍指向 `apihub-test.ehsy.com`），待 APIHub 提供测试环境账号后再配
- **不入库说明**：账号/密码仅存在于服务器 `backend.env`（`640 root:jetty`），不写入仓库、不写入数据库页面配置；仓库 `.env.example` 保持默认关闭
- **回滚方式**：恢复 `backend.env.bak-20261107-ocr` 或删除上述 5 行中的账号/地址项后 `sudo systemctl restart xiyu-bid-backend`

### 变更三：生产实际解析验证（12:38–12:40）

在实际生产服务器上，用生产 env 同款账号按应用完全一致的请求契约（`code=BAIDU_OCR_DOC_API`、`noCache=1`、`params.pdf_file` + `pdf_file_num`）调用 `http://apihub.ehsy.com/search` 完成两次真实解析：

| 用例 | 文件 | HTTP | 耗时 | 结果 |
|---|---|---|---|---|
| 1 | 纯文本 PDF（12,975 B） | 200 | 0.741s | `code=0`、`isSuccess=1`、`table_num=0`（无表格结构，符合预期） |
| 2 | 含 3×3 表格 PDF（1,063 B） | 200 | 0.586s | `code=0`、`isSuccess=1`、`table_num=1`，9 个单元格全部正确 |

用例 2 识别结果（逐字核对无误）：`Item / Score / Remark / Technical / 92 / pass / Commercial / 85 / pass`；APIHub `log_id=2118926068654162912`。

- **额度状态**：两次响应均 `errorCode=null`，未出现限额/限流/鉴权错误，说明账号额度可用；APIHub 响应体不含剩余额度字段，月度余量需在 APIHub 控制台查看。
- **结论**：生产 OCR 中转链路（网络 → 登录鉴权 → 文档解析）端到端可用。
- **边界说明**：本次为 APIHub 层实测；应用内 scoreparse 触发还需业务账号登录与真实评分文件，未在本次执行。

---

## 回滚信息

- 代码回滚锚点：`/opt/xiyu-bid/releases/889ccb4c3`（前后端完整产物）；旧 jar 可从该目录取回。
- 数据库回滚：**不需要**（本次零迁移）。
- 数据库备份：`/opt/xiyu-bid/db-backups/winbid-2960e7ea5-post-20261107114021.sql.gz`（6.7 M，`-- Dump completed` 校验通过，**部署后补做**）。

---

## 经验与风险提示

1. **DB 备份命令缺陷复发（第 2 次）**：`remote-deploy.sh` 的 `DB_BACKUP_COMMAND` 默认 `mysqldump` 加 `LOCK TABLES`，生产账号 `ea_bid_pro` 无该权限（`1044 Access denied ... when using LOCK TABLES`）；`mysqldump | gzip` 管道吞掉失败退出码，仍然打印 `DB backup done`，产出 371 字节"假备份"。第 36 次报告已记录并给出修复建议，但 `scripts/release/deploy-prod.sh` 中该命令仍未修复。
   - 本次处理：删除假备份，改用 `mysqldump --single-transaction` 生成完整备份（6.7 M，已验证完整）。
   - 服务器 `mysqldump` 为 `5.5.68-MariaDB`，**不支持** `--no-lock-tables`；`--single-transaction` 单独可用。
   - 建议修复：命令内启用 `set -o pipefail` + 校验 dump 尾部 `-- Dump completed` + 文件大小下限，失败即中止部署。
2. **Sentry 未启用**：本次构建未注入 `VITE_SENTRY_DSN`，前端错误上报关闭；用户已确认与上一版现状保持一致。若后续需要恢复监控，按 `deploy-prod.sh` 内置 DSN 重新打包即可。
3. **报告编号断档**：`96fe0cc06`、`889ccb4c3` 两次生产发布无报告，本次编号已按服务器发布顺序续接并说明口径。
4. **GitHub 镜像方向异常**：早操提示 "GitHub main 领先 Gitee 119 个 commit"。Gitee 为唯一主仓库，本次未处理；若 GitHub 侧确有未回流改动，需单独用 `scripts/sync-from-github.sh` 核对。

---

## 部署确认清单

- [x] 环境门禁经用户卡片确认（prod / 172.16.10.149）
- [x] 早操同步，HEAD = `origin/main` = `2960e7ea5`
- [x] Flyway validate 通过，无 pending 迁移
- [x] 产物校验（迁移去重 / OBS / 同源构建）
- [x] 部署后健康检查 + readiness
- [x] API 路由 Smoke（400 / 401）
- [x] 前端入口与页面可达（含公网域名 200）
- [x] 旧版 assets 保留
- [x] DB 备份有效性（部署后补做，已验证完整）
- [x] OCR 中转已启用并完成一次生产实际解析验证（`table_num=1`，9 个单元格全对）
- [ ] 部署报告 PR 合入（待提交）
