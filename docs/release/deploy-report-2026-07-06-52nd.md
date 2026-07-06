# 第 52 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-06 17:25 CST |
| Release ID | `f1fdc9239-api8080` |
| 上一版本 | `8593bd258-api8080`（2026-07-06 14:58 CST 部署，第 50 次） |
| 部署类型 | 增量部署（23 个 commit，无新增 DB 迁移） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 89 次） |
| Readiness | ✅ UP |
| 部署耗时 | 约 5 分钟 |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae/deploy-52nd` |
| HEAD commit | `f1fdc9239` |
| 工作区状态 | 干净 |
| origin/main 同步 | ✅ HEAD = origin/main |
| GitHub 镜像 | ✅ 已同步（`github/main` 与 `origin/main` 无差异） |

## 增量 PR 列表（23 个 commit，`8593bd258..f1fdc9239`）

| Commit | PR | 描述 |
|---|---|---|
| `f1fdc9239` | !1770 | fix(casework): custom 厂商 embedding 模型 fallback 修正 |
| `5db80bcf0` | !1769 | fix(CO-521): 平台账号重复时返回中文文案并阻止前端跳转错误页面 |
| `8759199ba` | !1770 | fix(casework): custom 厂商 embedding 模型 fallback 修正 |
| `8fb0655f2` | !1769 | fix(CO-521): 平台账号重复时返回中文文案并阻止前端跳转错误页面 |
| `d73b51743` | !1768 | co-523-knowledge-resource-expiry-warning: Automation skill-progression-map update |
| `132777091` | !1767 | feat(account): CO-522 账户详情抽屉新增操作日志 Tab |
| `4aff4195e` | !1766 | co-516-ui-tweak: Automation skill-progression-map update |
| `8d3a49b33` | !1765 | fix(CA): CO-515 修复 CA 详情页借用记录和操作日志 Tab 无信息 |
| `bc736d0c8` | !1761 | feat(account): CO-524 借用人窗口期内可查看密码 |
| `a121100e5` | !1768 | feat(platform): CO-523 平台账号借用到期/逾期/待审批提醒 |
| `267cb9867` | !1768 | fix(arch): CO-522 把 PlatformAccountAuditRecorder 移到 audit 包 |
| `c33d21378` | !1767 | feat(ui): CO-522 账户详情抽屉新增操作日志 Tab |
| `badc796b3` | !1767 | feat(audit): CO-522 新增平台账号专属审计日志查询端点 |
| `9706277b1` | !1766 | refactor(account): CO-516 我的申请/审批入口改为标题右侧链接切换 |
| `eebf14a6e` | !1767 | feat(audit): CO-522 编辑平台账号写入字段级 diff 审计日志 |
| `10d0108d5` | !1764 | fix(XIYU-P): 保证金派生表对 zero date 数据库列加 NULLIF 兜底，防止 DataException |
| `299f2b389` | !1764 | fix(scripts): gitee-pr-helper create_pr 从 commit message 提取标题和描述 |
| `d6392d4e1` | !1765 | fix(CA): CO-515 修复 CA 详情页借用记录和操作日志 Tab 无信息 |
| `782fea96a` | !1764 | fix(XIYU-P): 保证金派生表对 zero date 数据库列加 NULLIF 兜底，防止 DataException |
| `2ea9df20a` | !1764 | fix(XIYU-Q): bid_review_assignment 并发提交用 INSERT IGNORE 防重 |
| `0e12b6a5e` | !1764 | docs(release): 第 50 次部署报告 |
| `aa79a752d` | !1764 | fix(db): 修复 U1141 回滚脚本 source header 笔误 |
| `f4f56fc4b` | !1761 | feat(account): CO-524 借用人窗口期内可查看密码 |

## 改动范围

**核心业务变更**（7 个功能模块）：

### 1. 平台账号模块（!1767、!1768、!1769、!1770）
- CO-522：账户详情抽屉新增操作日志 Tab，编辑平台账号写入字段级 diff 审计日志
- CO-523：平台账号借用到期/逾期/待审批提醒
- CO-521：平台账号重复时返回中文文案并阻止前端跳转错误页面
- 新增：`PlatformAccountAuditRecorder`、`PlatformAccountAuditController`、`PlatformAccountBorrowExpiryScanService` 等

### 2. CA/资源模块（!1761、!1765）
- CO-524：借用人窗口期内可查看密码
- CO-515：修复 CA 详情页借用记录和操作日志 Tab 无信息
- 新增：`CABorrowRecordsTable.vue`，调整 `Account.vue`、`CADetailDialog.vue` 等

### 3. 保证金模块（!1764）
- XIYU-P：保证金派生表对 zero date 数据库列加 NULLIF 兜底，防止 DataException
- 影响文件：`MarginDerivedTableColumns.java`、`MarginQuerySupport.java`

### 4. 项目评审指派（!1764）
- XIYU-Q：`bid_review_assignment` 并发提交用 INSERT IGNORE 防重
- 影响文件：`BidReviewAssignmentEntity.java`、`BidReviewAssignmentRepository.java`、`BidReviewAppService.java`

### 5. AI 案例切片（!1770）
- custom 厂商 embedding 模型 fallback 修正
- 影响文件：`QwenEmbeddingClient.java`

### 6. UI 调整（!1766）
- CO-516：我的申请/审批入口改为标题右侧链接切换

### 7. 工具与文档（!1764）
- 修复 `gitee-pr-helper.sh` 从 commit message 提取标题和描述
- 修复 `U1141` 回滚脚本 source header 笔误
- 第 50 次部署报告

## Flyway 预检结果

### Step 1: 服务器 validate
```
VALIDATE OK - all checksums match
Successfully validated 205 migrations
```

### Step 2: DB 已应用版本
```
version  description                                          success  installed_on
1141     platform account username scoped unique            1        2026-07-06 14:58:13
1140     fix co 518 admin staff qualification manage permission 1    2026-07-06 14:58:13
1139     fix pm understands process column length           1        2026-07-06 13:48:13
```

### Step 3: 新迁移扫描
- 本次增量**无新增 `V*.sql` 迁移文件**
- 仅修改回滚脚本 `U1141__platform_account_username_scoped_unique.sql`
- 新 jar 激活前 remote-deploy.sh 内置 validate 通过

## 部署步骤

1. ✅ 早操三连（`dev-env.sh`、`sync-env.sh`、`check-git-wrapper.sh`）
2. ✅ 创建任务分支 `agent/trae/deploy-52nd`
3. ✅ 服务器现状确认（`8593bd258-api8080`，health UP）
4. ✅ Flyway 预检 3 步法（validate OK，DB 版本 V1141）
5. ✅ 本地打包：`RELEASE_ID="f1fdc9239-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
6. ✅ 产物校验：jar 内 204 个迁移文件，无重复版本
7. ✅ 上传部署包与 `remote-deploy.sh`
8. ⚠️ 执行 `remote-deploy.sh`：自动生成的 DB 备份文件为 20 字节空文件（见风险提示）
9. ✅ 补做有效 DB 备份：`winbid-f1fdc9239-api8080-20260706172948-manual.sql.gz`（3.3M，gunzip 测试通过）
10. ✅ 清理空备份文件
11. ✅ 后端服务重启并通过健康检查（89 次尝试，连续 3 次成功）
12. ✅ 前端产物一致性校验通过

## 验证结果

### 后端健康检查

```bash
health:      HTTP 200 ✅
readiness:   HTTP 200 ✅
```

### API Smoke

| 端点 | 状态码 | 说明 |
|---|---|---|
| `POST /api/auth/login` | 400 | 空密码触发参数校验错误，接口路由正常 ✅ |
| `GET /api/projects` | 403 | 需认证，接口正常 ✅ |
| `GET /api/integration/crm/health` | 401 | 需认证，接口正常 ✅ |

> 完整登录 smoke 因未获得 `ADMIN_PASSWORD` 而跳过，以 400/403/401 替代验证。

### 前端 Smoke

```bash
root:       HTTP 200 ✅
login page: HTTP 200 ✅
frontend index: assets/index-CAAuEwJg.js ✅（与 release 一致）
```

### Flyway 迁移应用验证

本次无新增迁移，DB 版本保持 V1141。

## GitHub 镜像同步

- 部署前：`origin/main` 与 `github/main` 无差异（0 commit）
- 部署后：无需额外同步

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一可用版本 | `8593bd258-api8080`（第 50 次部署） |
| 当前 DB 备份 | `/opt/xiyu-bid/db-backups/winbid-f1fdc9239-api8080-20260706172948-manual.sql.gz`（3.3M，有效） |
| 回滚脚本 | 本次无新迁移，无需 rollback SQL |
| 回滚方式 | 1) 还原 `/opt/xiyu-bid/shared/backend/app.jar` 到上一版本；2) 重启服务 |
| 回滚 posture | ✅ 就绪，未执行 |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 部署前主动 validate，无新迁移，校验通过 |
| Readiness 延迟恢复 | ✅ 本次健康检查 89 次通过，在 4 分钟窗口内完成 |
| 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，前端 index.js 一致 |
| Smoke 测试限制 | ✅ 使用 400/403/401 替代验证，不谎称登录已验证 |
| GitHub 镜像同步 | ✅ 部署前已同步，无需额外操作 |
| 临时调试配置清理 | ✅ 检查 `SHOW_DETAILS` 等临时配置未变更 |
| 幂等迁移设计 | ✅ 本次无新迁移 |
| systemctl sudo | ✅ 默认 `SYSTEMCTL_SUDO=true`，服务正常重启 |
| 前端目录权限 | ✅ 部署前 `/srv/www/xiyu-bid` 属主已为 `jetty:jetty`，无权限中断 |
| macOS `._*` 残留 | ✅ 无异常 |

## 风险提示

1. **DB 备份异常**：`remote-deploy.sh` 自动生成的备份 `winbid-f1fdc9239-api8080-20260706172331.sql.gz` 仅 20 字节（空 gzip）。已手动补做有效备份并清理空文件。建议后续检查 `DB_BACKUP_COMMAND` 在 remote-deploy.sh 中的执行环境或管道错误处理（可能缺 `set -o pipefail` 导致 mysqldump 失败未中断）。
2. **git.properties commit id**：已知 worktree 环境下 `git-commit-id-maven-plugin` 可能显示旧 commit id，不影响实际类文件，但版本追溯需以 class 文件/部署时间为准。
3. **SHOW_DETAILS 保留**：生产环境仍暴露 health 详情，后续如安全收紧需改为 `never` 并重启后端。

## 部署确认清单

- [x] 早操三连完成
- [x] 分支为任务分支 `agent/trae/deploy-52nd`
- [x] `git status` 干净
- [x] Flyway validate 通过
- [x] 本地打包成功
- [x] jar 内迁移文件无重复
- [x] 部署包上传成功
- [x] DB 备份完成（有效备份已补做）
- [x] 后端服务重启成功
- [x] health/readiness 200
- [x] API Smoke 400/403/401 正常
- [x] 前端页面 200 且资源一致
- [x] GitHub 镜像同步已确认
- [x] 部署报告生成
- [x] 回滚准备就绪

---

**部署执行人**：Trae Agent
**部署完成时间**：2026-07-06 17:25 CST
