# 第 148 次部署报告（test 环境）

**日期**：2026-08-28
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`353ba8bcb`
**部署时间**：13:37 - 14:45（含两项计划外排障：本地 Docker 守护进程僵死、remote-deploy.sh SIGPIPE 静默中断）
**状态**：✅ 成功

> 序号说明：前一份报告为 147th（2026-08-28 11:53，`c57a1062e-api8080`），本次为同日第二次部署。

## 基线信息

| 项目 | 值 |
|------|-----|
| 基线 commit | `353ba8bcb`（origin/main，HEAD = origin/main 零差异） |
| 前次部署 | `c57a1062e-api8080`（2026-08-28 11:59 CST 激活） |
| 增量 commit | 14 个 |
| 新增迁移 | **无**（DB 停留在 V1204，无 pending） |
| 工作区状态 | 干净 |

## 改动范围（14 个增量 commit，重点 PR）

| PR | 描述 |
|----|------|
| !2445 | **fix(integration): 接口六 CRM 首联 422 死锁修复**——移除合并后全量必填校验，校验范围对齐契约字段集 |
| !2446 | **fix(project): 删除商机预测与项目列表客户类型筛选"其他"选项**（含 §172 知识沉淀） |
| !2443 | **fix: 评分表格解析列对齐策略**——优先列 3 括号分值（含 wholeRowFallback 死代码清理） |
| !2444 | fix(workbench): AI 商机预测时间字段单行统一排版（含 Vue 3 SFC 内联 h() 陷阱沉淀） |
| !2442 | docs(release): 第 147 次部署报告 |

## 事故一：本地 Docker 守护进程僵死（打包卡 30+ 分钟）

**现象**：`package-release.sh` 在"容器测试门禁"阶段卡住 30+ 分钟无输出，surefire 进程 CPU 187% 但 `surefire-reports/` 无新报告。

**排查链**：
1. `docker version --format '{{.Server.Version}}'` 超时（exit 124）——Server 段无响应，`docker ps` 同样挂死
2. `jstack <surefire-pid>` 抓到主线程栈：阻塞在 `org.testcontainers.dockerclient...PingCmdExec`——Testcontainers 对 Docker 守护进程的 socket 读永不返回

**根因**：Docker Desktop 守护进程僵死（`com.docker.backend` 进程无响应）。容器测试门禁（`FlywayMysqlContainerTest`）强依赖 Testcontainers，守护进程僵死后测试无限等待。

**处置**：
1. 杀掉卡死的构建与 surefire 进程
2. 优雅退出 Docker Desktop → 旧 `com.docker.backend` 进程残留挡住新实例 → `pkill -f com.docker.backend` + `kill -9` 清残留 → 重新 `open -a Docker`
3. 守护进程恢复（version 29.1.2），重新打包全程正常（总耗时约 6 分钟，其中后端 33 秒）

**防复发提示**：`package-release.sh` 的容器测试门禁没有超时保护——若本地 Docker 僵死会无限挂起。后续可考虑 `timeout` 包裹 mvn 容器测试。

## 事故二：remote-deploy.sh SIGPIPE 静默中断（部署中断后修复重跑）

**现象**：首次执行 remote-deploy.sh，DB 备份成功、前端解压成功，输出停在 `==> Activating frontend assets` 后**无任何报错退出**。服务器状态：`releases/353ba8bcb` 已解压、`deployed-release.json` 仍指向旧版、前端未替换、后端仍跑旧 jar（服务未中断）。

**排查链**：
1. 线上 `index.html` 仍是旧 hash `index-dzqkyp3D.js` → 脚本死在 rm/cp/mv 激活序列之前
2. 死亡点无任何 stderr 输出 → `set -euo pipefail`（第 6 行）下某命令静默失败
3. 第 143 行 `NON_OWNED="$(find ... | head -n 5)"`：验证发现 `/srv/www/xiyu-bid/assets/` 存在大量非 jetty 属主文件——第 147 次部署后的"保留旧 assets"步骤用 `sudo cp -rn` 拷入数百个 root 属主文件

**根因**（20000 条目本地复现验证，旧代码 exit 141）：
- 非属主条目 > 5 个 → `head -n 5` 读满即退出 → `find` 仍在写入 → SIGPIPE（exit 141）
- `pipefail` 放大 141 为管道整体退出码 → `set -e` 静默杀死脚本
- **脚本自己设计的 `sudo chown -R` 修复分支（第 144-148 行）永远执行不到**——守卫本身成了死因

**处置（服务器侧解锁）**：手动 `sudo chown -R jetty:jetty /srv/www/xiyu-bid`（jetty 有 NOPASSWD sudo）→ find 返回 0 条目 → 无 SIGPIPE → 重跑 remote-deploy.sh 全程成功。

**根治（本 PR）**：`scripts/release/remote-deploy.sh` 第 143 行 find 侧加 `|| true` 吞掉 SIGPIPE，让 chown 修复分支能正常接手：

```bash
# 修复前
NON_OWNED="$(find "$FRONTEND_PUBLIC_DIR" ! -user "$DEPLOY_USER" -print 2>/dev/null | head -n 5)"
# 修复后
NON_OWNED="$( { find "$FRONTEND_PUBLIC_DIR" ! -user "$DEPLOY_USER" -print 2>/dev/null || true; } | head -n 5)"
```

**防复发链断点**：部署后"保留旧 assets"步骤（SOP 7.5）今后必须在 `sudo cp -rn` 之后追加 `sudo chown -R jetty:jetty /srv/www/xiyu-bid`，否则每次都会给下一次部署埋雷（本次已同步执行）。

## Flyway 预检（3 步法）

| 步骤 | 结果 |
|---|---|
| 1. 服务器 validate | ✅ VALIDATE OK，265 migrations checksum 全部一致 |
| 2. DB 版本对比 | ✅ DB 已应用至 V1204（全 success=1），源码无新增迁移（预期持平） |
| 3. remote-deploy 内置 validate | ✅ 激活前再次通过 |

## 打包与产物校验

```bash
RELEASE_ID="353ba8bcb" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

| 校验项 | 结果 |
|---|---|
| jar 内 Flyway 迁移版本无重复 | ✅ |
| OBS 直传启用（Detail chunk `.upload(` =2） | ✅ |
| `release-metadata.json` 中 `obsEnabled` | `true` ✅ |
| 前端入口 chunk | `assets/index-Dil2hPCg.js` ✅（与线上实际一致） |
| 包大小 | 154M（md5 `e6b652622cb0…` 本地/服务器一致） |

> 插曲：首次打包因 worktree 缺 `node_modules` 报 `vite: command not found`（首次在此 worktree 打包），`npm ci` 后恢复，非脚本缺陷。

## 部署步骤

| 步骤 | 结果 | 备注 |
|------|------|------|
| 0. 环境门禁 | ✅ | AskUserQuestion 确认 test（172.16.38.78） |
| 1. 早操三连 | ✅ | sync-env 通过；git wrapper 检测到 raw git（无害提醒） |
| 2. 基线确认 | ✅ | HEAD = origin/main = `353ba8bcb`，工作区干净 |
| 3. 服务器现状 | ✅ | 前版 c57a1062e 全组件 UP |
| 4. Flyway 预检 | ✅ | 见上表 |
| 5. 本地打包 | ⚠️→✅ | Docker 僵死排障 45 分钟后恢复，重打包成功（事故一） |
| 6. 产物校验 | ✅ | 全项通过 |
| 7. 上传+部署 | ⚠️→✅ | 首跑 SIGPIPE 中断（事故二），chown 解锁后重跑成功 |
| 7.5 旧 assets 保留 | ✅ | cp -rn + chown 统一属主（NON-JETTY=0） |
| 8. 健康检查 | ⚠️→✅ | 脚本 4 分钟窗口未过（Kafka readiness 延迟），手动轮询 5 分钟后 UP |
| 9. 迁移验证 | ✅ | V1204 仍为最新（无新增，预期） |
| 10. Smoke | ✅ | 见下节 |
| 11. 配置清理 | ✅ | 仅 `SHOW_DETAILS=always`（第 13/14/15 次部署用户拍板保留） |

## 验证结果

**后端**（经 Nginx 8080）：

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | HTTP 200 `{"status":"UP",...}` |
| `/actuator/health/readiness` | HTTP 200 UP |
| `POST /api/auth/login`（空 body） | HTTP 400（预期：参数校验拦截） |
| `GET /api/projects`（无凭证） | HTTP 403（预期：需认证） |
| `GET /api/integration/crm/health` | HTTP 401（预期：需认证） |

**前端**（经 Nginx 8080）：

| 检查项 | 结果 |
|---|---|
| `/` | HTTP 200 |
| `/login` | HTTP 200 |
| 入口 chunk | `assets/index-Dil2hPCg.js`（与构建产物一致） |

**readiness 延迟记录**：服务 14:31:26 重启，`/actuator/health` 整体 OUT_OF_SERVICE（readinessState 未就绪，其余组件全 UP），14:36:14 自愈转 UP——约 5 分钟，与第 8/9/10/13/15/147 次一致（Kafka SDK 初始化延迟，已知行为，无需回滚）。注意 remote-deploy.sh 健康等待窗口为 4 分钟 < 本次实际 5 分钟，故脚本报"Health check failed"但服务随后自愈——误报，建议后续将窗口调至 6-8 分钟或对 readinessState 单独宽容。

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚锚点 | `/opt/xiyu-bid/releases/c57a1062e-api8080/backend/app.jar` + 同目录 `frontend/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-353ba8bcb-*.sql.gz`（部署前生成） |
| 回滚命令 | 恢复旧 jar + `sudo systemctl restart xiyu-bid-backend` |
| 本次是否需要 | **否**（全量验证通过） |

## 部署确认清单

- [x] 环境门禁 AskUserQuestion 确认
- [x] 早操三连 + 基线零差异
- [x] Flyway 预检 3 步法通过
- [x] 产物校验全项（OBS/迁移无重复/入口 hash）
- [x] DB 备份在位
- [x] 健康检查 UP + readiness 200
- [x] Smoke 8 项全过
- [x] 旧 assets 保留 + 属主统一（防 SIGPIPE 复发）
- [x] 配置清理检查（仅保留历史拍板的 SHOW_DETAILS）
- [x] 部署报告（本文档）
- [x] remote-deploy.sh SIGPIPE 修复 PR（随本报告同 PR 提交）

## 风险提示

1. **remote-deploy.sh 修复未合入前，服务器上的仍是旧版**——本次已在服务器侧手动 chown 解锁当前部署；但若"保留旧 assets"步骤再次引入 root 属主文件且未 chown，下一次部署仍会踩 SIGPIPE 中断（修复合入后自动免疫）。
2. 容器测试门禁无超时保护，本地 Docker 僵死会挂起打包（建议后续加 timeout）。
3. 健康检查 4 分钟窗口对 Kafka readiness 延迟（实测最长 6 分钟）偏紧，脚本存在误报可能。
