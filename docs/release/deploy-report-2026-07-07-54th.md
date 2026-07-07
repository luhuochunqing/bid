# 第 54 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-07 13:35 CST |
| Release ID | `dd4f79fae-api8080` |
| 上一版本 | `b29b75bae-api8080`（2026-07-06 22:55 CST 部署，第 53 次） |
| 部署类型 | 增量部署（23 个 commit，1 个新 DB 迁移 V1145） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 89 次） |
| Readiness | ✅ UP（Kafka SDK 延迟约 2 分钟后恢复，已知行为） |
| 部署耗时 | 约 5 分钟 |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae/deploy-54th` |
| HEAD commit | `dd4f79fae` |
| 工作区状态 | 干净 |
| origin/main 同步 | ✅ HEAD = origin/main |
| GitHub 镜像 | ✅ 部署后已同步（两边 main = `dd4f79fae`） |

## 增量 PR 列表（23 个 commit，`b29b75bae..dd4f79fae`）

| Commit | PR | 描述 |
|---|---|---|
| `dd4f79fae` | !1796 | fix(account): CO-522 密码变更判断不再误报（改用 passwordChanged 标志） |
| `59e72aeda` | !1798 | refactor(account): CO-516 移除主 tab 内冗余的卡片标题和记录数 |
| `60cc004a0` | !1797 | fix(CO-529): 修复任务提交时报"交付物上传失败"但实际已上传，任务未流转 |
| `f306eac8b` | !1791 | feat(CO-526): 提交建议投标时同步 CRM 商机和对接人信息 |
| `e051e9b35` | !1799 | fix(alerts): 修复 alert_rules.type 枚举与 Java AlertType 不同步导致到期提醒失败 |
| `a91735469` | - | feat(CO-526): 提交建议投标时同步 CRM 商机和对接人信息 |
| `73d5bac91` | - | docs(lessons): 沉淀 §45 — Java 枚举与数据库 ENUM 不同步导致静默失败 |
| `6290baf81` | - | fix(alerts): 修复 alert_rules.type 枚举与 Java AlertType 不同步导致到期提醒失败 |
| `079633c78` | - | refactor(account): CO-516 移除主 tab 内冗余的卡片标题和记录数 |
| `f77b3d254` | - | fix(audit): CO-522 密码变更判断不再误报（改用 passwordChanged 标志） |
| `5fe1c72b7` | - | fix(CO-529): 附件上传失败不阻塞交付物上传和任务流转 |
| `638a4421f` | - | fix(CO-529): 重写父类 handleMissingServletRequestPart 替代无效 @ExceptionHandler |
| `50b3ef52c` | !1795 | fix(warehouse): 台账导出下载文件名后缀 .xlsx → .zip（WPS 打开报格式错误） |
| `dd724954c` | - | fix(warehouse): 台账导出下载文件名后缀从 .xlsx 改为 .zip |
| `d353ed308` | !1794 | fix(CO-512): 修复品牌授权批量导入 415 Unsupported Media Type |
| `233d06d85` | - | fix(CO-512): 修复品牌授权批量导入 415 Unsupported Media Type |
| `202407233` | !1790 | feat(CO-528): 人员证书批量导入计数改为人员维度 + 校验规则与新增对齐 |
| `3f3520639` | !1792 | fix(casework): 系统设置新增 embeddingModel 配置项 |
| `e3f374170` | !1793 | refactor(ai): 消除 embedding 设计弯路 — 合并 QwenEmbeddingClient 到 OpenAiCompatibleEmbeddingClient |
| `2f8dbeeac` | - | refactor(ai): 消除 embedding 设计弯路 — 合并 QwenEmbeddingClient 到 OpenAiCompatibleEmbeddingClient |
| `9b1a1f377` | - | fix(casework): 系统设置新增 embeddingModel 配置项 |
| `4498c29cb` | - | fix(casework): 系统设置新增 embeddingModel 配置项 |
| `6d32bc6d3` | - | feat(CO-528): 人员证书批量导入计数改为人员维度 + 校验规则与新增对齐 |
| `e4d19cf6f` | - | docs(release): 第 53 次部署报告（注：第 53 次报告文档为空，仅提交占位） |

## 改动范围

**核心业务变更**（7 个功能模块）：

### 1. 到期提醒修复（!1799，V1145 迁移）★ 关键修复
- **问题**：`alert_rules.type` 枚举与 Java `AlertType` 不同步，缺少 `PERFORMANCE_EXPIRY`/`CA_EXPIRY`/`CA_BORROW_OVERDUE` 三个枚举值，导致到期提醒静默失败
- **修复**：V1145 迁移补全 `alert_rules.type` ENUM 定义，同步 Java AlertType 新增项
- **教训沉淀**：`docs/lessons/lessons-learned.md` §45 — Java 枚举与数据库 ENUM 不同步导致静默失败
- **影响文件**：`V1145__add_alert_rule_types.sql`、`U1145__add_alert_rule_types.sql`

### 2. 任务交付物上传修复（!1797，CO-529）
- **问题**：任务提交时报"交付物上传失败"但实际已上传，任务未流转
- **修复**：附件上传失败不阻塞交付物上传和任务流转；重写父类 `handleMissingServletRequestPart` 替代无效 `@ExceptionHandler`

### 3. 平台账号模块（!1796，CO-522）
- **问题**：密码变更判断误报
- **修复**：改用 `passwordChanged` 标志判断，不再误报

### 4. CRM 同步（!1791，CO-526）
- 提交建议投标时同步 CRM 商机和对接人信息

### 5. 品牌授权批量导入（!1794，CO-512）
- 修复 415 Unsupported Media Type 错误

### 6. 人员证书批量导入（!1790，CO-528）
- 计数改为人员维度 + 校验规则与新增对齐

### 7. AI Embedding 重构（!1792、!1793）
- 系统设置新增 `embeddingModel` 配置项
- 消除 embedding 设计弯路，合并 `QwenEmbeddingClient` 到 `OpenAiCompatibleEmbeddingClient`

### 8. 仓库台账导出（!1795）
- 下载文件名后缀 `.xlsx` → `.zip`（WPS 打开报格式错误）

### 9. UI 调整（!1798，CO-516）
- 移除主 tab 内冗余的卡片标题和记录数

## Flyway 预检结果

### Step 1: 服务器 validate
```
Successfully validated 208 migrations (execution time 00:00.087s)
VALIDATE OK - all checksums match
```

### Step 2: DB 已应用版本（部署前）
```
version  description                                          success  installed_on
1144     qualification agency contact comment                  1        2026-07-07 06:57:16
1143     normalize empty task extended fields json             1        2026-07-07 06:57:16
1142     remove platform type from platform accounts           1        2026-07-07 06:57:16
1141     platform account username scoped unique               1        2026-07-06 14:58:13
1140     fix co 518 admin staff qualification manage permission 1       2026-07-06 14:58:13
```

### Step 3: 新迁移扫描
- 本次增量**1 个新迁移**：`V1145__add_alert_rule_types.sql`
  - 类型：`ALTER TABLE ... MODIFY COLUMN type enum(...)`（幂等 DDL，重复执行结果相同）
  - 内容：补全 `alert_rules.type` ENUM，新增 `PERFORMANCE_EXPIRY`/`CA_EXPIRY`/`CA_BORROW_OVERDUE` 三个枚举值
  - 回滚脚本：`U1145__add_alert_rule_types.sql` 已配套（移除三个枚举值，注意回滚前需清理已有数据）
- 新 jar 激活前 remote-deploy.sh 内置 validate 通过

## 部署步骤

1. ✅ 早操三连（`dev-env.sh`、`sync-env.sh`、`check-git-wrapper.sh`）
2. ✅ 创建任务分支 `agent/trae/deploy-54th`
3. ✅ 服务器现状确认（`b29b75bae-api8080`，health UP）
4. ✅ Flyway 预检 3 步法（validate OK，DB 版本 V1144，V1145 待应用）
5. ✅ 本地打包：`RELEASE_ID="dd4f79fae-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
   - 打包耗时：23.7 秒
   - jar 内 208 个迁移文件，无重复版本
6. ✅ 产物校验：V1145 在 jar 内，前端入口 `assets/index-DjI_n3Da.js`
7. ✅ 上传部署包与 `remote-deploy.sh`
8. ✅ 执行 `remote-deploy.sh`：
   - DB 备份：`winbid-dd4f79fae-api8080-20260707133028.sql.gz`（3.4M，有效）✅
   - Flyway validate 通过
   - Backend artifact 更新
   - 服务重启（13:32:03 CST）
9. ✅ 健康检查通过（89 次尝试，连续 3 次成功，Kafka SDK readiness 延迟约 2 分钟后恢复）
10. ✅ 前端产物一致性校验通过

## 验证结果

### 后端健康检查

```bash
health:      HTTP 200 ✅
readiness:   HTTP 200 ✅（Kafka SDK 延迟恢复后）
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
frontend index: assets/index-DjI_n3Da.js ✅（与 release 一致）
```

### Flyway 迁移应用验证

```
version  description          success  installed_on
1145     add alert rule types  1        2026-07-07 13:32:10
```

V1145 已成功应用。

## GitHub 镜像同步

- 部署前：`origin/main` 与 `github/main` 差异 23 个 commit（早操 rebase 后未同步镜像）
- 部署后：执行 `bash scripts/sync-to-github.sh`
- 结果：✅ 两边 main 完全一致（`dd4f79fae`）

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一可用版本 | `b29b75bae-api8080`（第 53 次部署） |
| 当前 DB 备份 | `/opt/xiyu-bid/db-backups/winbid-dd4f79fae-api8080-20260707133028.sql.gz`（3.4M，有效） |
| 回滚脚本 | `U1145__add_alert_rule_types.sql`（移除三个枚举值，需先清理已有数据） |
| 回滚方式 | 1) 还原 `/opt/xiyu-bid/shared/backend/app.jar` 到上一版本；2) 重启服务；3) 如需回滚 V1145，先清理 `alert_rules` 表中三个新枚举值的记录，再执行 U1145 |
| 回滚 posture | ✅ 就绪，未执行 |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 部署前主动 validate，V1145 待应用状态确认 |
| Readiness 延迟恢复 | ✅ 本次健康检查 89 次通过，Kafka SDK 延迟约 2 分钟后自恢复 |
| 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，前端 index.js 一致 |
| Smoke 测试限制 | ✅ 使用 400/403/401 替代验证，不谎称登录已验证 |
| GitHub 镜像同步 | ✅ 部署前落后 23 个 commit，部署后已同步 |
| 临时调试配置清理 | ✅ 检查 `SHOW_DETAILS=always` 保留（用户第 13/14/15 次连续决定保留，已知行为） |
| 幂等迁移设计 | ✅ V1145 为 `MODIFY COLUMN`（幂等 DDL），U1145 回滚脚本已配套 |
| systemctl sudo | ✅ 默认 `SYSTEMCTL_SUDO=true`，服务正常重启 |
| 前端目录权限 | ✅ 部署前 `/srv/www/xiyu-bid` 属主已为 `jetty:jetty`，无权限中断 |
| macOS `._*` 残留 | ✅ 无异常（tar 解压时 `LIBARCHIVE.xattr.com.apple.provenance` 警告可忽略） |
| DB 备份有效性 | ✅ 本次备份 3.4M 有效（第 52 次 20 字节空文件问题未复现） |
| Mac HTTP_PROXY 502 | ✅ 所有 Smoke 通过 SSH 内部访问，未受 Mac 代理影响 |

## 风险提示

1. **git.properties commit id**：已知 worktree 环境下 `git-commit-id-maven-plugin` 可能显示旧 commit id，不影响实际类文件，版本追溯以 class 文件/部署时间为准。
2. **SHOW_DETAILS 保留**：生产环境仍暴露 health 详情（`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`），后续如安全收紧需改为 `never` 并重启后端。
3. **V1145 回滚限制**：回滚 U1145 前**必须先清理** `alert_rules` 表中已存在的 `PERFORMANCE_EXPIRY`/`CA_EXPIRY`/`CA_BORROW_OVERDUE` 记录，否则 ALTER 会失败。
4. **第 53 次部署报告缺失**：`docs/release/deploy-report-2026-07-07-53rd.md` 文件为空（提交时未填写内容），本次部署未补写，建议后续如需追溯第 53 次部署详情可查询 git log 或 Gitee PR 记录。

## 部署确认清单

- [x] 早操三连完成
- [x] 分支为任务分支 `agent/trae/deploy-54th`
- [x] `git status` 干净
- [x] Flyway validate 通过
- [x] 本地打包成功
- [x] jar 内迁移文件无重复
- [x] 部署包上传成功
- [x] DB 备份完成（3.4M，有效）
- [x] 后端服务重启成功
- [x] health/readiness 200
- [x] API Smoke 400/403/401 正常
- [x] 前端页面 200 且资源一致
- [x] GitHub 镜像同步已确认
- [x] 部署报告生成
- [x] 回滚准备就绪

---

**部署执行人**：Trae Agent
**部署完成时间**：2026-07-07 13:35 CST
