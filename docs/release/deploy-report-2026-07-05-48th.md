# 第 48 次生产部署报告

> **部署日期**：2026-07-05
> **Release ID**：`adb09dec1-api8080`
> **部署类型**：增量代码部署（无新增 Flyway 迁移）
> **部署结果**：✅ 成功
> **回滚状态**：未触发，未需要

---

## 一、部署概览

| 项目 | 值 |
|---|---|
| Release ID | `adb09dec1-api8080` |
| 部署时间 | 2026-07-05 16:35:58 CST |
| 健康检查通过 | 2026-07-05 16:38:38 CST（88 次 attempts，约 3 分钟） |
| 前端入口 | `/assets/index-DrBl1wRR.js` |
| jar 大小 | 150M |
| jar 名称 | `bid-poc-1.0.3.jar` |
| builtAt | 2026-07-05T08:33:23Z |
| sentryEnabled | false |

---

## 二、基线信息

| 项目 | 值 |
|---|---|
| 主工作区 | `/Users/user/xiyu/worktrees/trae` |
| 任务分支 | `agent/trae/deploy-48th` |
| 锚点分支 | `agent/trae-init` |
| 上一次部署 commit | `e8f1a36c3` (第 47 次) |
| 本次部署 commit | `adb09dec1c1489b8e37568e1c62ef6c377fffe57` |
| 增量 commit 数 | 16 |
| 新增 Flyway 迁移 | 无（纯代码部署） |
| GitHub 镜像同步 | ✅ 同步完成（两边 main 一致） |

---

## 三、PR 列表与改动范围

### 增量 commit（e8f1a36c3..adb09dec1，共 16 个）

| Commit | PR | 描述 |
|---|---|---|
| `adb09dec1` | !1708 | CO-490 fix(margin): INIT 分支 JOIN tasks/pc + 缴纳方式翻译 + 项目负责人兜底 |
| `95a7bcf4d` | !1710 | fix(CO-503): 仓库信息模块导出按钮名称规范化 [v2] |
| `59133c40d` | !1711 | fix(project): CO-504 流标/弃标不再跳过结项审核，统一走结项申请流程 |
| `bf6a623ec` | !1712 | docs(release): 第 47 次部署报告 |
| `398387d06` | !1714 | feat(platform-account): 补全平台账户台账导出功能 |
| `0c87e3b8d` | !1715 | CO-487 fix delete error message |
| `20d9d48e9` | !1716 | feat(common): CO-505 批量导入模板日期格式统一兼容 |
| `256c8e8f3` | - | feat(common): CO-505 批量导入模板日期格式统一兼容（中间提交） |
| `99770504c` | !1713 | fix(platform-account): 批量导入接口权限与类级对齐，解决 bid-Team 用户 403 |
| `6fb59ed6c` | - | feat(platform-account): 补全平台账户台账导出功能（中间提交） |
| `ba7a0e153` | - | fix(project-doc): CO-487 结项项目删除附件应返回友好提示而非"系统状态冲突" |
| `38083d2fb` | - | fix(platform-account): 批量导入接口权限与类级对齐（中间提交） |
| `13d65b44f` | - | docs(release): 第 47 次部署报告（中间提交） |
| `e87f586e1` | - | fix(CO-503): 仓库信息模块导出按钮名称规范化 |
| `e33a9b997` | - | fix(project): CO-504 流标/弃标不再跳过结项审核 |
| `6ada001b2` | - | CO-490 fix(margin): INIT 分支 JOIN tasks/pc + 缴纳方式翻译 |

### 改动范围（按模块）

| 模块 | 主要变更 |
|---|---|
| margin（保证金） | CO-490：INIT 分支 JOIN tasks/pc，缴纳方式翻译，项目负责人兜底 |
| warehouse（仓库信息） | CO-503：导出按钮名称规范化 |
| project（项目管理） | CO-504：流标/弃标不再跳过结项审核，统一走结项申请流程 |
| project-doc（结项文档） | CO-487：删除附件返回友好提示而非"系统状态冲突" |
| platform-account（平台账户） | 补全台账导出功能，批量导入接口权限与类级对齐，解决 bid-Team 403 |
| common | CO-505：批量导入模板日期格式统一兼容 |

---

## 四、Flyway 预检结果

### Step 1：服务器 validate

```
✅ VALIDATE OK - all checksums match (200 migrations validated, 00:00.087s)
```

### Step 2：DB 已应用版本对比

```
version  description                                     success  installed_on
1136     warehouse attachment type to varchar            1         2026-07-05 14:34:32
1135     create bid case slice                           1         2026-07-05 14:34:32
1134     personnel operation log allow null personnel   1         2026-07-04 15:51:45
1133     add bid review assignment table hotfix          1         2026-07-04 08:05:43
1132     add has lease contract to warehouse             1         2026-07-04 07:54:08
```

DB 已应用最新版本 V1136，与源码一致，本次无新增迁移。

### Step 3：remote-deploy.sh 内置 validate

```
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

---

## 五、部署步骤执行

### 5.1 本地打包

```bash
RELEASE_ID="adb09dec1-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- `VITE_API_BASE_URL=` 显式设空，触发同源构建（`baseURL=""`）
- `mvn clean -DskipTests package` BUILD SUCCESS（26.657s）
- jar 内 Flyway 迁移版本无重复（199 个 V*.sql 文件）

### 5.2 上传 + 部署

```bash
scp .release/xiyu-bid-release-adb09dec1-api8080.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 '... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

关键参数：
- `RELEASE_ID=adb09dec1-api8080`
- `SYSTEMCTL_SUDO=true`（jetty 用户 NOPASSWD sudo 重启服务）
- `DB_BACKUP_COMMAND` 预置 mysqldump + gzip 备份

### 5.3 后端重启日志

```
==> Restarting backend service xiyu-bid-backend
● xiyu-bid-backend.service - XiYu Smart Bidding Backend
   Active: active (running) since Sun 2026-07-05 16:35:58 CST; 20ms ago
   Main PID: 9325 (java)
==> Waiting for health check http://127.0.0.1:8080/actuator/health
✅ Health check passed (consecutive 3/3, total attempts: 88, service: active/running)
```

健康检查 88 次 attempts（约 3 分钟）才通过——属于已知行为（`OrganizationEventSdkKafkaStarter` 阻塞主线程，Kafka SDK 初始化延迟 readiness）。Kafka broker 可达后自恢复。

---

## 六、验证结果

### 6.1 健康检查

| 端点 | HTTP | 状态 |
|---|---|---|
| `/actuator/health` | 200 | UP |
| `/actuator/health/readiness` | 200 | UP |

组件状态：
- `aiProvider` UP（provider=doubao, model=deepseek-v3-2-251201, apiKeyConfigured=true）
- `db` UP（MySQL, isValid()）
- `diskSpace` UP（free 36.8GB / total 105.5GB）
- `jwt` UP（HMAC-SHA256, 64 bytes, STRONG）
- `livenessState` UP
- `ping` UP
- `readinessState` UP
- `redis` UP（version 6.2.19）
- `sidecar` UP（url=http://localhost:8000, reachable）

### 6.2 API Smoke 测试

> Admin 密码未授予，使用 400/403/401 替代验证（第 8 次起固化策略）。

| 接口 | 预期 HTTP | 实际 HTTP | 说明 |
|---|---|---|---|
| `POST /api/auth/login` | 400 | 400 | 空密码触发验证错误，接口路由正常 |
| `GET /api/projects` | 403 | 403 | 需认证，接口正常 |
| `GET /api/integration/crm/health` | 401 | 401 | 需认证，接口正常 |

### 6.3 前端验证

| 路径 | HTTP | 说明 |
|---|---|---|
| `GET /` | 200 | 前端入口正常 |
| `GET /login` | 200 | 登录页正常 |
| 前端 JS 入口 | `/assets/index-DrBl1wRR.js` | 与 release 一致 |

---

## 七、GitHub 镜像同步

```bash
git log --oneline github/main..origin/main | wc -l   # 部署前 16
bash scripts/sync-to-github.sh
```

```
✅ Gitee → GitHub 镜像同步完成
   Gitee main:  adb09dec1c1489b8e37568e1c62ef6c377fffe57
   GitHub main: adb09dec1c1489b8e37568e1c62ef6c377fffe57
   状态: 完全一致
```

---

## 八、回滚信息

### 8.1 回滚准备

| 项目 | 值 |
|---|---|
| 上一版本 Release ID | `e8f1a36c3-api8080` |
| 上一版本 release 目录 | `/opt/xiyu-bid/releases/e8f1a36c3-api8080/` |
| 数据库备份 | `/opt/xiyu-bid/db-backups/winbid-adb09dec1-api8080-<timestamp>.sql.gz` |
| 回滚触发条件 | 健康检查 / P0 Smoke 失败 |
| 回滚必要性 | ❌ 未需要（部署成功） |

### 8.2 回滚命令（如需）

```bash
# 后端 jar 回滚
ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/e8f1a36c3-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'

# 前端回滚
ssh jetty@172.16.38.78 'sudo rsync -a --delete /opt/xiyu-bid/releases/e8f1a36c3-api8080/frontend/ /srv/www/xiyu-bid/'

# DB 回滚（如本次有迁移——本次无新增，不需要）
# gunzip < /opt/xiyu-bid/db-backups/winbid-adb09dec1-api8080-<ts>.sql.gz | mysql ...
```

---

## 九、配置清理检查

```bash
ssh jetty@172.16.38.78 'sudo grep -E "SHOW_DETAILS|DEBUG=|TRACE" /etc/xiyu-bid/backend.env'
```

发现：
- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`

**处置**：保留（第 13、14、15、47 次用户连续决定保留，运维监控需要）。如后续需收紧安全，可改为 `never` 并重启后端。

---

## 十、经验沉淀应用情况

| 经验条目 | 本次是否触发 | 处置 |
|---|---|---|
| 1. Flyway 预检 3 步法 | ✅ | 全部通过，200 migrations validate OK |
| 2. Readiness 延迟恢复 | ✅ | 88 次 attempts（约 3 分钟）通过，已知行为，自恢复 |
| 3. 生产前端同源构建 | ✅ | `VITE_API_BASE_URL=` 显式设空 |
| 4. Smoke 测试 admin 密码限制 | ✅ | 使用 400/403/401 替代验证 |
| 5. GitHub 镜像同步 | ✅ | 部署后同步，两边 main 一致 |
| 6. 临时调试配置清理 | ✅ | SHOW_DETAILS=always 保留（用户决定） |
| 7. 幂等迁移设计 | N/A | 本次无新增迁移 |
| 8. systemctl sudo 权限 | ✅ | SYSTEMCTL_SUDO=true，jetty NOPASSWD sudo |
| 9. git.properties commit id | N/A | 不影响功能 |
| 10. 破坏性 schema 变更 | N/A | 本次无迁移 |
| 11. /tmp/migration-mysql/ 目录过时 | ✅ | 不影响 validate，仅 info 输出 |
| 12. rollback 脚本命名规范 | N/A | 本次无新增迁移 |
| 13. 前端目录权限 | ✅ | 部署成功，无权限问题 |
| 14. macOS `._*` 残留文件 | ✅ | package-release.sh 已处理 |
| 15. Flyway 防护体系 | ✅ | 全部生效 |
| 16. Mac HTTP_PROXY 502 | ✅ | 使用 `--noproxy '*'` 绕过 |
| 17. SentryAppender crash-loop | N/A | 本次未涉及 logback 配置变更 |

---

## 十一、风险提示

1. **Readiness 延迟恢复（已知行为）**：本次健康检查 88 次 attempts（约 3 分钟）才通过，根因是 `OrganizationEventSdkKafkaStarter` 阻塞主线程。若后续部署需要更快的 readiness 恢复，可考虑将 `onApplicationReady()` 改为 `@Async` 或独立线程池执行。
2. **SHOW_DETAILS=always 保留**：运维监控需要，已连续多次决定保留。如需收紧安全，可改为 `never` 并重启后端。
3. **GitHub 镜像已同步**：本次部署后立即同步，两边 main 完全一致。

---

## 十二、部署确认清单

- [x] 早操三连（dev-env.sh + sync-env.sh + check-git-wrapper.sh）
- [x] 基线确认（HEAD = adb09dec1，GitHub 镜像落后 16 → 同步后一致）
- [x] 服务器现状（deployed-release.json = e8f1a36c3，health UP）
- [x] Flyway 预检 3 步法（validate OK + DB V1136 + remote-deploy 内置 validate）
- [x] 本地打包（RELEASE_ID + VITE_API_BASE_URL= + package-release.sh，BUILD SUCCESS）
- [x] 产物校验（199 个 V*.sql 无重复 + 前端 index.html 存在）
- [x] 上传 + 部署（scp + remote-deploy.sh，SYSTEMCTL_SUDO=true）
- [x] 健康检查（health 200 UP + readiness 200 UP）
- [x] Smoke 测试（7/7 通过：health/readiness/3 接口/前端 2 页面/前端入口）
- [x] GitHub 镜像同步（两边 main 一致）
- [x] 配置清理检查（SHOW_DETAILS=always 保留，已确认）
- [x] 部署报告生成

---

## 十三、部署后清理

部署报告 PR 合入 main 后，需执行：

```bash
git checkout agent/trae-init
git pull origin main
git branch -D agent/trae/deploy-48th
# 远端分支删除（如已 push）
git push origin --delete agent/trae/deploy-48th
```

---

**部署完成**：第 48 次生产部署成功，所有验证通过，回滚未触发。
