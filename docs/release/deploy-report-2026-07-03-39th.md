# 第 39 次生产部署报告

**部署时间**：2026-07-03 16:43 - 16:55 (CST)
**部署人**：AI Agent (Trae)
**服务器**：winbid-01.test (172.16.38.78)
**Release ID**：`f99686823-api8080`
**结果**：✅ 成功（含 V1131 破坏性 schema 变更，健康检查 88 次尝试后通过）

---

## 1. 部署概览

| 项目 | 值 |
|---|---|
| 目标服务器 | `172.16.38.78` (winbid-01.test) |
| SSH 用户 | `jetty` |
| App Root | `/opt/xiyu-bid` |
| Backend Port | `8080` |
| DB Name | `xiyu_bid_main` |
| Release ID | `f99686823-api8080` |
| Commit | `f99686823` (!1633 CO-482: 标讯详情阶段按钮文案修改) |
| 前端构建模式 | 同源构建 (`VITE_API_BASE_URL=`) |
| 新增迁移 | V1131 (platform_account_registration_fields，含 DROP COLUMN) |
| 上一部署 | `a57c1bc58-api8080`（2026-07-03 15:04 CST，第 38 次） |
| 增量 commit | 41 个（PR !1626-!1635） |
| 改动文件 | 82 files, +3358/-300 |
| 部署耗时 | ~5 分钟（含 ~2 分钟健康检查等待，Kafka SDK 已知行为） |

---

## 2. 基线信息

### 2.1 Git 状态

- **早操三连**：source dev-env.sh + sync-env.sh + check-git-wrapper.sh ✅
- **当前分支**：`agent/trae-init`（锚点分支，ff-only 同步到 main 最新）
- **基线**：HEAD = `f99686823` = origin/main（部署时）
- **GitHub 镜像**：部署前落后 Gitee 100 个 commit；部署后已同步，两边 main 完全一致
- **本地门禁**：7/7 通过

### 2.2 服务器部署前状态

| 项目 | 值 |
|---|---|
| 已部署 Release | `a57c1bc58-api8080` |
| 激活时间 | 2026-07-03T07:04:15Z (15:04 CST) |
| 健康状态 | UP（所有组件正常） |
| DB 最新迁移 | V1130 (personnel education start date nullable) |

---

## 3. PR 列表与改动范围

### 3.1 增量 PR（!1626-!1635）

| PR | 描述 | 类型 |
|---|---|---|
| !1626 | docs(release): 第38次生产部署报告 + main 编译修复 + ArchitectureTest 基线修正 | docs |
| !1627 | feat(performance): 业绩附件 ZIP 导出支持按类型筛选 + UX 优化 | feat |
| !1628 | fix(margin): CO-490 保证金 500 错误 — 空字符串日期触发 CAST 异常 | fix |
| !1629 | CO-468: 修复转入标书制作阶段后保证金待办任务未自动显示 | fix |
| !1630 | feat(CO-474): 账户管理模块字段新增与修改 | feat |
| !1631 | fix(integration): CRM 商机负责人优先覆盖 XIYU_CONTACT 字段（/bidding/931 复发修复） | fix |
| !1632 | fix(project): 修复项目列表/详情页客户类型展示为英文枚举名 | fix |
| !1633 | CO-482: 标讯详情阶段按钮文案修改 | fix |
| !1634 | chore(scripts): 新增 check-vue-enum-direct-render.mjs 防止枚举字段直接渲染复发 | chore |
| !1635 | fix(tender): CO-441 修复孤儿 manager_id 引发 NPE 导致 /api/tenders 500 | fix |

### 3.2 重点变更说明

- **CO-490 保证金 500 错误修复**（!1628）：空字符串日期触发 CAST 异常，影响保证金管理核心功能
- **CO-474 账户管理字段修改**（!1630）：新增 V1131 迁移，破坏性 DROP COLUMN（contact_phone/contact_email）
- **CRM XIYU_CONTACT 字段覆盖修复**（!1631）：/bidding/931 复发 bug 修复，CRM 商机负责人优先覆盖
- **CO-441 /api/tenders 500 修复**（!1635）：孤儿 manager_id 引发 NPE，影响标讯列表接口

### 3.3 改动文件统计

```
82 files changed, 3358 insertions(+), 300 deletions(-)
```

迁移文件变更：`backend/src/main/resources/db/migration-mysql/V1131__platform_account_registration_fields.sql`

---

## 4. Flyway 预检结果（3 步法）

### Step 1: 服务器 validate

```bash
ssh jetty@172.16.38.78 'bash /opt/xiyu-bid/bin/flyway-repair-runner.sh validate'
```

**结果**：✅ VALIDATE OK - all checksums match（194 migrations validated，execution time 0.076s）

### Step 2: DB 已应用版本 vs 源码最新版本

| 项目 | 值 |
|---|---|
| DB 已应用最新版本 | V1130 (personnel education start date nullable, 2026-07-03 10:48:24) |
| 源码最新版本 | V1131 (platform_account_registration_fields) |
| 差异 | 1 个新迁移待部署 |

### Step 3: remote-deploy.sh 内置 validate

**结果**：✅ Flyway validate 通过（仅 pending 新迁移为预期状态）

### V1131 迁移内容

```sql
-- V1131: 账户表新增注册人/注册手机/注册邮箱字段，删除绑定手机/绑定邮箱字段
-- CO-474 #1/1 账户管理模块 — 字段新增与修改

ALTER TABLE platform_accounts
  ADD COLUMN registrant VARCHAR(100) DEFAULT NULL COMMENT '注册人' AFTER remarks,
  ADD COLUMN register_phone VARCHAR(20) DEFAULT NULL COMMENT '注册手机' AFTER registrant,
  ADD COLUMN register_email VARCHAR(200) DEFAULT NULL COMMENT '注册邮箱' AFTER register_phone;

ALTER TABLE platform_accounts
  DROP COLUMN contact_phone,
  DROP COLUMN contact_email;
```

**⚠️ 破坏性变更说明**：
- DROP COLUMN `contact_phone`、`contact_email` — 旧数据将丢失，无法通过 rollback 恢复
- U1131 rollback 脚本仅恢复列结构，数据需从 DB 备份恢复整张表
- 业务确认数据可丢弃（CO-474 已合入 main，业务已确认）

---

## 5. 部署步骤

### 5.1 本地打包

```bash
RELEASE_ID="f99686823-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

**结果**：
- ✅ BUILD SUCCESS（Total time: 25.955 s）
- ✅ jar 内 Flyway 迁移版本无重复
- ✅ V1131 在 jar 内（194 个迁移文件）
- ✅ 前端入口：`assets/index-vVxjLe-3.js`
- ✅ Release archive: 138M

### 5.2 上传 + 部署

```bash
scp .release/xiyu-bid-release-f99686823-api8080.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 '... SYSTEMCTL_SUDO=true ... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

**关键参数**：
- `SYSTEMCTL_SUDO=true`（jetty 用户 NOPASSWD sudo，避免 Interactive authentication required）
- `DB_BACKUP_COMMAND` 含 mysqldump gzip 备份

**部署日志摘要**：
1. ✅ Flyway validate 通过（194 migrations，checksums 全匹配）
2. ✅ Backend artifact 已更新
3. ✅ deployed-release.json 已写入
4. ✅ 服务重启（PID 11599，16:52:23 CST 启动）
5. ✅ Health check passed（consecutive 3/3, total attempts: 88）
6. ✅ Frontend matches release（`assets/index-vVxjLe-3.js`）

---

## 6. 验证结果

### 6.1 健康检查

| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ UP（所有组件：aiProvider/db/diskSpace/jwt/livenessState/ping/readinessState/redis/sidecar） |
| `/actuator/health/readiness` | ✅ UP（无 Kafka SDK 延迟问题） |
| 健康检查尝试次数 | 88 次（Kafka SDK 已知行为，第 8/9/10/13/15 次均出现） |

### 6.2 V1131 迁移应用验证

```sql
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version="1131";
```

| version | description | success | installed_on |
|---|---|---|---|
| 1131 | platform account registration fields | 1 | 2026-07-03 16:52:30 |

✅ V1131 已成功应用

### 6.3 API Smoke 测试

> Admin 密码未知，采用 400/403/401 替代验证策略（第 6 条经验）

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects` | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |

### 6.4 前端验证

| 检查项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `GET /` | 200 | 200 | ✅ |
| `GET /login` | 200 | 200 | ✅ |
| 前端入口一致性 | `assets/index-vVxjLe-3.js` | `assets/index-vVxjLe-3.js` | ✅ |

---

## 7. GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前同步状态 | 落后 Gitee 100 个 commit |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ 两边 main 完全一致（`f99686823`） |
| Gitee main | `f99686823b36901e3b68def8a6b28f0d9d86cca0` |
| GitHub main | `f99686823b36901e3b68def8a6b28f0d9d86cca0` |

---

## 8. 回滚信息

### 8.1 回滚姿态

| 项目 | 值 |
|---|---|
| 回滚姿态 | ✅ Ready（未需要） |
| 上一 Release | `a57c1bc58-api8080` |
| 上一 Release 目录 | `/opt/xiyu-bid/releases/a57c1bc58-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-f99686823-api8080-*.sql.gz` |

### 8.2 V1131 回滚限制

- **U1131 rollback 脚本**：`backend/src/main/resources/db/rollback/migration-mysql/U1131__platform_account_registration_fields.sql`
- **回滚限制**：DROP COLUMN 已删除 `contact_phone`、`contact_email` 数据，U1131 仅恢复列结构，数据需从 DB 备份恢复整张表
- **回滚命令**（如需）：
  ```bash
  ssh jetty@172.16.38.78 'cd /opt/xiyu-bid/releases/a57c1bc58-api8080 && sudo cp backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'
  ```

---

## 9. 配置清理检查

| 检查项 | 结果 |
|---|---|
| `SHOW_DETAILS` | `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（保留，用户连续 3 次决定保留，运维监控需要） |
| `DEBUG` / `TRACE` | 无 |

> **历史**：第 8 次引入 `SHOW_DETAILS=always`，第 13、14、15 次用户连续三次决定保留。如后续需收紧安全，可改为 `never` 并重启后端。

---

## 10. 经验沉淀应用情况

| 经验条目 | 本次应用情况 |
|---|---|
| 1. Flyway 预检 3 步法 | ✅ 执行 3 步预检，validate OK |
| 2. Kafka SDK readiness 延迟 | ✅ 88 次尝试后通过，已知行为，未急于回滚 |
| 3. 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| 4. Smoke 测试限制 | ✅ Admin 密码未知，用 400/403/401 替代验证 |
| 5. GitHub 镜像同步 | ✅ 部署后同步，两边 main 一致 |
| 6. 临时调试配置清理 | ✅ SHOW_DETAILS=always 保留（用户决定） |
| 7. 幂等迁移设计 | ⚠️ V1131 非幂等（DROP+ADD），生产靠 Flyway 版本号防重复 |
| 10. 破坏性 schema 变更 | ✅ V1131 DROP COLUMN 已确认业务可丢弃，DB 备份已完成 |
| 12. rollback 脚本命名 | ✅ U1131 已配（前缀 U，非 RV） |
| 16. Mac HTTP_PROXY 502 | ✅ 全程用 SSH 内部访问，未触发代理问题 |

---

## 11. 风险提示

1. **V1131 破坏性变更**：`contact_phone`、`contact_email` 列数据已丢失，如需恢复需从 DB 备份恢复整张表
2. **Kafka SDK 延迟**：88 次健康检查尝试（约 2 分钟），属已知行为，未影响最终可用性
3. **GitHub 远程仓库迁移提示**：推送时出现 "This repository moved. Please use the new location: git@github.com:luhuochunqing/bid.git" — 当前仍通过 `github-bid` SSH host 别名指向 `yzcynk5vtp-ship-it/bid.git`，功能正常，但 GitHub 账户名变更需关注

---

## 12. 部署确认清单

- [x] 早操三连执行（dev-env + sync-env + check-git-wrapper）
- [x] 基线确认（HEAD = origin/main，工作树干净）
- [x] 服务器现状核查（上一部署状态 + 增量 commit + 迁移文件变更）
- [x] Flyway 预检 3 步法（validate + DB 版本对比 + remote-deploy 内置）
- [x] 本地打包（BUILD SUCCESS + 产物校验）
- [x] 上传 + 部署（SYSTEMCTL_SUDO=true + DB 备份）
- [x] 健康检查（UP + readiness UP）
- [x] V1131 迁移应用验证（success=1）
- [x] API Smoke 测试（400/403/401 + 前端 200/200）
- [x] GitHub 镜像同步（两边 main 一致）
- [x] 配置清理检查（SHOW_DETAILS=always 保留，用户决定）
- [x] 部署报告生成

---

## 13. 部署总结

第 39 次生产部署成功完成。本次部署包含 41 个增量 commit（PR !1626-!1635），1 个破坏性 schema 迁移 V1131（DROP COLUMN）。重点修复了 CO-490 保证金 500 错误、CO-441 /api/tenders NPE、CRM XIYU_CONTACT 字段覆盖复发等业务问题。

部署过程平稳，Flyway 预检 3 步法全部通过，健康检查在 88 次尝试后通过（Kafka SDK 已知延迟行为），所有 Smoke 测试符合预期。GitHub 镜像已同步至与 Gitee main 完全一致。

**回滚姿态**：Ready（未需要），U1131 rollback 脚本已配，DB 备份已生成。
