# 第 22 次部署报告

> **部署日期**：2026-06-30  
> **Release ID**：`326869f88-api8080`  
> **部署类型**：常规部署（含 Flyway 迁移）  
> **部署结果**：✅ 成功

## 一、部署概览

| 项目 | 值 |
|---|---|
| Release ID | `326869f88-api8080` |
| 激活时间 | 2026-06-30 20:02:40 CST |
| 上一次部署 | `48c429c58-api8080`（第 21 次，2026-06-30 13:08:36 CST） |
| 增量 commit | 43 个（含 PR !1393-!1409） |
| 新增迁移 | V1119、V1120（2 个，均有 rollback U1119/U1120） |
| 部署耗时 | 约 5 分钟（含打包 24s + 上传 + 部署 + 验证） |
| 健康检查 | 第 1 次通过（无 Kafka 延迟） |
| 回滚状态 | 未需要 |

## 二、基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae` |
| 任务分支 | `agent/trae/deploy-22nd-report` |
| 部署 commit | `326869f885c24abdb09822f70047f2468bd19179` |
| GitHub 镜像 | 已同步（从落后 46 commit 到完全一致） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080 |

## 三、PR 列表

| PR | 描述 |
|---|---|
| !1393 | fix(brand-auth): 修复新增授权后列表不展示 |
| !1394 | docs(release): 第 21 次部署报告 |
| !1395 | refactor(brand-auth): 统一 Controller 契约为 ApiResponse |
| !1396 | fix(observability): GlobalExceptionHandler 手动触发 Sentry 上报 |
| !1397 | fix(organization): status 字段提升为用户启用判定的最高优先级 |
| !1398 | fix(permission): CO-409 CA 模块投标专员操作项权限矩阵对齐 |
| !1400 | feat(project): 缴纳保证金任务截止时间改为读取立项录入的真实截止日期 |
| !1401 | CO-418 project archive list sort by createdAt desc |
| !1402 | fix: 改善标讯批量导入失败提示（告知重复判定依据+已有标讯标题） |
| !1403 | chore(agent-locks): 清理 CO-409 孤儿锁 |
| !1404 | feat(tender-intake): AI 识别抽取完整招标公告原文到 tenderInfo + 字段容量提升到 20000 字 (CO-406) |
| !1405 | CO-420 archive document category mapping by stage |
| !1407 | feat(CO-422): remove archive stats cards and status tabs |
| !1408 | feat(CO-423): remove bid result column from project archive table |
| !1409 | fix: CO-421 项目档案投标负责人字段修复 - 改用 biddingLeaderName + 消除导出 N+1 |

## 四、改动范围

- **多个文件变更**（涉及后端、前端、文档、迁移）
- **2 个新 Flyway 迁移**（V1119、V1120，均有配套 U1119/U1120 rollback）
- **Sentry 错误诊断集成**：
  - 集成 Sentry SDK 自动捕获异常（codex 分支贡献）
  - GlobalExceptionHandler 手动触发 Sentry 上报，补齐 @ExceptionHandler(Exception.class) 拦截路径（!1396）
- **权限治理**：
  - CO-409 CA 模块投标专员操作项权限矩阵对齐（!1398）
  - organization status 字段提升为用户启用判定的最高优先级（!1397）
- **项目档案系列优化**：
  - CO-418 项目档案列表按归档日期倒序（!1401）
  - CO-420 项目档案文档分类归集规则（!1405）
  - CO-421 项目档案投标负责人字段修复 - 改用 biddingLeaderName 冗余字段 + 消除导出 N+1（!1409）
  - CO-422 项目档案页面移除顶部统计卡片和状态标签页（!1407）
  - CO-423 项目档案表格移除中标结果列（!1408）
- **业务修复**：
  - 缴纳保证金任务截止时间改为读取立项录入的真实截止日期（!1400）
  - 改善标讯批量导入失败提示（告知重复判定依据+已有标讯标题）（!1402）
  - 标讯信息输入框 maxlength 从 5000 提升到 20000（CO-406 配套）
- **标讯 intake 增强**：
  - CO-406 AI 识别抽取完整招标公告原文到 tenderInfo + 字段容量提升到 20000 字（!1404）
  - V1120 扩展 tender_info 容量 VARCHAR(5000)→TEXT + DTO 校验同步到 20000
- **brand-auth 修复**：
  - 修复新增授权后列表不展示（!1393）
  - 统一 Controller 契约为 ApiResponse（!1395）
- **任务表单优化**：
  - 校验错误改为字段级显示，不再堆在表单底部

## 五、Flyway 预检结果

### Step 1: 服务器 validate
```
VALIDATE OK - all checksums match（182 migrations）
```

### Step 2: DB 版本对比
- 部署前 DB 最新版本：V1118（2026-06-30 13:10:13）
- 源码最新版本：V1120
- V1119/V1120 待应用（预期）

### Step 3: remote-deploy.sh 内置 validate
```
VALIDATE OK - all checksums match（部署前再次验证）
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

### V1119 迁移评估

| 项 | 评估 |
|---|---|
| 迁移内容 | 为 project_initiation 表增加 deposit_due_date 字段（保证金截止日期） |
| 幂等性 | 标准 ADD COLUMN DDL，依赖 Flyway 版本号机制防重复执行 |
| 安全性 | 纯 DDL 变更，无数据迁移 |
| 回滚脚本 | ✅ U1119 存在 |
| 风险等级 | 低（仅新增列，不影响现有数据） |

### V1120 迁移评估

| 项 | 评估 |
|---|---|
| 迁移内容 | 扩展 tender_info 容量 VARCHAR(5000)→TEXT，支持 20000 字招标公告原文 |
| 幂等性 | 标准 ALTER COLUMN 类型变更，依赖 Flyway 版本号机制防重复执行 |
| 安全性 | 类型 widening（VARCHAR→TEXT），不丢失数据 |
| 回滚脚本 | ✅ U1120 存在（注意：回滚到 VARCHAR(5000) 会截断超长数据） |
| 风险等级 | 低（类型 widening 不丢数据；回滚需注意截断风险） |

## 六、部署步骤

1. **早操三连**（任务分支 `agent/trae/verify-sentry`）
   - `source scripts/dev-env.sh` → 环境检测通过（trae 主工作区）
   - `bash scripts/sync-env.sh .` → 已是最新，无需 rebase
   - `bash scripts/check-git-wrapper.sh` → 7 项门禁全部通过
2. **基线确认**：
   - 工作树干净，HEAD = `326869f88`（= origin/main 最新）
   - GitHub 镜像落后 Gitee 46 commit，且 GitHub 领先 1 commit（`478c18122 chore(locks): prune stale expired locks`，GitHub Actions 自动产物）
3. **服务器现状检查**：deployed-release.json（48c429c58）+ health（200 UP）+ 增量 commit（43 个）+ 迁移文件变更（V1119+V1120 + U1119+U1120）
4. **Flyway 预检 3 步法**：validate 通过（182 migrations）+ DB 版本对比（V1118→V1120）+ remote-deploy 内置
5. **本地打包**：
   ```bash
   RELEASE_ID="326869f88-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
   ```
   - 前端：7.94s 构建完成，同源 baseURL（`VITE_API_BASE_URL=` 显式设空）
   - 后端：23.997s BUILD SUCCESS，2572 源文件编译
   - jar 内 Flyway 迁移版本无重复（183 files）
6. **产物校验**：
   - jar 内 V1119（635 bytes）+ V1120（1764 bytes）存在 ✅
   - 前端入口 `assets/index-BAyFgAel.js` ✅
   - Release archive 137M
7. **上传 + 部署**：
   ```bash
   COPYFILE_DISABLE=1 scp .release/xiyu-bid-release-326869f88-api8080.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/
   ```
   - DB 备份完成 ✅
   - Flyway validate 预检通过 ✅
   - 激活新 jar + sudo systemctl restart ✅（`SYSTEMCTL_SUDO=true`）
   - 后端服务 20:02:40 CST active (running) ✅
   - 前端一致性验证 ✅（`/assets/index-BAyFgAel.js`）

## 七、验证结果

### 健康检查
| 检查项 | 结果 |
|---|---|
| health | HTTP 200 UP（9 个组件全部 UP：aiProvider/db/diskSpace/jwt/liveness/ping/readiness/redis/sidecar） |
| readiness | HTTP 200 UP ✅（**本次无 Kafka 延迟**，第 1 次通过） |
| 后端服务 | active ✅（PID 14815） |

### 迁移应用验证
```sql
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version IN ('1119','1120') ORDER BY version;
```
| version | description | success | installed_on |
|---|---|---|---|
| 1119 | add deposit due date to project initiation | 1 | 2026-06-30 20:02:47 |
| 1120 | expand tender info capacity to 20000 | 1 | 2026-06-30 20:02:47 |

V1119、V1120 均已成功应用 ✅

### Smoke 测试（Admin 密码未知，用 400/403/401 替代验证）

> ⚠️ **Mac HTTP_PROXY 502 现象**（第 19 次部署已记录的经验）：直接 `curl http://172.16.38.78:8080/...` 全部返回 502，但服务器内部 `curl http://127.0.0.1:8080/...` UP。需用 `curl --noproxy '*'` 跳过 macOS 代理才能拿到真实结果。

| 检查项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects` | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| `GET /`（前端） | 200 | 200 | ✅ |
| `GET /login`（前端） | 200 | 200 | ✅ |
| 前端 asset | index-BAyFgAel.js | index-BAyFgAel.js | ✅ 与 release 一致 |

> 登录 smoke 因 admin 密码未授予而跳过，使用 400/403/401 替代验证策略（第 6 次起固化）。

## 八、GitHub 镜像同步

| 项 | 值 |
|---|---|
| 部署前 | GitHub main 落后 Gitee main 46 commit |
| 部署前 | GitHub main 领先 Gitee main 1 commit（`478c18122 chore(locks): prune stale expired locks`，GitHub Actions 自动锁清理产物，仅删除 .agent-locks/ 下 5 个过期锁文件） |
| 同步命令 | `echo y \| bash scripts/sync-to-github.sh` |
| 同步策略 | Gitee → GitHub 单向 force-with-lease 镜像，覆盖 GitHub 独有 commit |
| 部署后 | Gitee main = GitHub main = `326869f885c24abdb09822f70047f2468bd19179` ✅ 完全一致 |

### 关于 GitHub 领先 commit 的处理

`478c18122 chore(locks): prune stale expired locks` 由 `github-actions[bot]` 在 2026-06-30 11:57:27 UTC 提交，仅删除 `.agent-locks/` 下 5 个过期锁文件：
- co-388-account-actions-role.yml
- co302-crm-customer-host-fix.yml
- co361-co373-finish.yml
- fix-v1118-role-profile-table-name.yml
- flyway-p2-syntax-guard.yml

这些锁文件本就属于待清理的孤儿锁，覆盖不会丢失业务代码。Gitee 上未应用此 commit，但 sync-to-github.sh 的 force-with-lease 会用 Gitee main 覆盖 GitHub main，效果等同。

## 九、回滚信息

| 项 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 回滚 posture | Ready |
| 前一版本 artifact | `/opt/xiyu-bid/releases/48c429c58-api8080/`（第 21 次部署） |
| 前一版本 jar | `bid-poc-1.0.3.jar`（commit 48c429c58） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-326869f88-api8080-<timestamp>.sql.gz` |
| V1119 回滚脚本 | `U1119__add_deposit_due_date_to_project_initiation.sql` |
| V1120 回滚脚本 | `U1120__expand_tender_info_capacity_to_20000.sql`（⚠️ 回滚会截断 > 5000 字数据） |
| 回滚步骤 | 1. 激活旧 jar + 重启；2. 执行 U1120 + U1119 回滚 SQL（注意 V1120 回滚数据截断风险） |

## 十、经验沉淀应用情况

本次部署应用了以下 15 条经验：

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ Step 1 validate（182 migrations） + Step 2 DB 版本对比（V1118→V1120） + Step 3 remote-deploy 内置 |
| 2 | Readiness Kafka SDK 延迟 | ✅ 容忍策略就绪（本次未出现延迟） |
| 3 | 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，触发 baseURL="" |
| 4 | Smoke 测试 400/403/401 | ✅ 替代验证策略执行（注意 HTTP_PROXY 502 现象需 --noproxy '*'） |
| 5 | GitHub 镜像同步 | ✅ 部署后执行 sync-to-github.sh，force-with-lease 覆盖 1 个 GitHub 独有 commit |
| 6 | 临时调试配置清理 | ✅ SHOW_DETAILS=always 保留（第 13/14/15 次用户决定保留） |
| 7 | 幂等迁移设计 | ✅ V1119/V1120 标准 DDL，依赖 Flyway 版本号机制防重复执行 |
| 8 | systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true 显式传参 |
| 9 | git.properties commit id 不准确 | ⚠️ worktree 环境下 git.properties 可能不准，不影响代码内容 |
| 11 | 服务器 /tmp/migration-mysql/ 目录过时 | ⚠️ 已知现象，validate 不受影响 |
| 12 | rollback 脚本命名规范 | ✅ U1119、U1120（U 前缀，非 RV） |
| 14 | macOS `._*` 残留文件 | ✅ 打包/上传时显式 `COPYFILE_DISABLE=1` |
| 15 | Flyway 防护体系 | ✅ 15 项防护全部生效（pre-commit + pre-push + package-release + remote-deploy） |

### Mac HTTP_PROXY 502 经验复用（第 19 次部署已沉淀）

**现象**：从 macOS 直接 `curl http://172.16.38.78:8080/...` 全部返回 502，但服务器内部 health UP。

**根因**：macOS 环境变量 `HTTP_PROXY`/`http_proxy` 将请求转发到本地代理，代理无法到达内网 IP 时返回 502。

**解决**：用 `curl --noproxy '*'` 跳过代理，或在 shell 中 `unset HTTP_PROXY http_proxy HTTPS_PROXY https_proxy`。

**历史**：第 19 次部署首次发现并记录，本次复用该经验顺利通过 Smoke 测试。

## 十一、风险提示

1. **V1120 回滚数据截断风险**：V1120 将 `tender_info` 从 VARCHAR(5000) 扩展到 TEXT，U1120 回滚会改回 VARCHAR(5000)，超过 5000 字的数据会被截断。回滚前需先确认是否有超长 tender_info 数据，必要时先备份。
2. **Sentry 上报依赖 DSN 配置**：本次集成了 Sentry SDK，但异常上报依赖 `SENTRY_DSN` 环境变量正确配置。若 DSN 未配置，SDK 会静默跳过，不会影响业务，但失去错误监控能力。建议运维确认 `/etc/xiyu-bid/backend.env` 中 SENTRY_DSN 已设置。
3. **SHOW_DETAILS=always 保留**：生产环境 health 端点暴露详情（第 13/14/15 次用户决定保留，运维监控需要）。
4. **GitHub 仓库迁移提示**：sync-to-github.sh 输出 `This repository moved. Please use the new location: git@github.com:luhuochunqing/bid.git`。当前 SSH config 仍用 `github-bid:yzcynk5vtp-ship-it/bid.git`，同步正常但建议后续确认 GitHub 仓库归属（与第 21 次部署相同提示，未处理）。
5. **GitHub Actions 自动锁清理 commit 被覆盖**：本次同步覆盖了 GitHub 上 `478c18122 chore(locks): prune stale expired locks`。如果该 GitHub Actions workflow 后续再次运行，可能再次产生类似 commit；建议在 Gitee CI 中复刻同样的锁清理逻辑，避免 GitHub 独有 commit 累积。

## 十二、部署确认清单

- [x] 早操三连完成（dev-env + sync-env + check-git-wrapper）
- [x] 基线确认（工作区干净，HEAD=326869f88=origin/main）
- [x] 服务器现状检查（deployed-release.json + health）
- [x] Flyway 预检 3 步法（validate + DB 版本对比 + remote-deploy 内置）
- [x] V1119/V1120 迁移评估（DDL 安全 + 回滚脚本齐备）
- [x] 本地打包（前端同源构建 + 后端 BUILD SUCCESS）
- [x] 产物校验（jar 内 V1119/V1120 + 前端入口一致）
- [x] 上传 + 部署（scp + remote-deploy.sh，SYSTEMCTL_SUDO=true，COPYFILE_DISABLE=1）
- [x] 健康检查（health UP + readiness UP，第 1 次通过，无 Kafka 延迟）
- [x] 迁移应用验证（V1119/V1120 success=1）
- [x] Smoke 测试（400/403/401 + 前端 200 + asset 一致，--noproxy '*' 跳过 HTTP_PROXY）
- [x] GitHub 镜像同步（force-with-lease 覆盖 1 个 GitHub 独有 commit，两边 main 完全一致）
- [x] 配置清理检查（SHOW_DETAILS=always 保留，已说明理由）
- [x] 部署报告生成

---

**部署完成时间**：2026-06-30 20:08 CST  
**部署执行者**：Trae Agent  
**回滚 readiness**：Ready（未需要）
