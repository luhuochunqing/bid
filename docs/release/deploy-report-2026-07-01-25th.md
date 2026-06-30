# 第 25 次部署报告

> **部署日期**：2026-07-01
> **Release ID**：`261830696-api8080`
> **部署类型**：常规部署（无 Flyway 迁移）
> **部署结果**：✅ 成功

## 一、部署概览

| 项目 | 值 |
|---|---|
| Release ID | `261830696-api8080` |
| 激活时间 | 2026-07-01 07:04:42 CST |
| 上一次部署 | `2b6d7e7f3-api8080`（第 24 次，2026-06-30 23:17:22 CST） |
| 增量 commit | 25 个（含 PR !1425-!1435） |
| 新增迁移 | 无 |
| 部署耗时 | 约 5 分钟（含打包 23s + 上传 + 部署 + 验证） |
| 健康检查 | 第 1 次通过（无 Kafka readiness 延迟） |
| 回滚状态 | 未需要 |

## 二、基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 任务分支 | `agent/trae-init`（锚点分支，部署前 ff-only 到 origin/main） |
| 部署 commit | `26183069698bfd925d44e50a3fabb3e801ec3621` |
| GitHub 镜像 | ✅ 已同步（部署时 Gitee/GitHub main 已完全一致） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080 |

## 三、PR 列表

| PR | 描述 |
|---|---|
| !1425 | fix-brand-auth-import-oplog: Automation skill-progression-map update |
| !1426 | docs(release): 第 24 次部署报告 |
| !1427 | fix(crm): CO-431 按 CRM position 字段映射对接人角色，不按下标强行分配 |
| !1428 | fix(ca): CO-435 修复CA信息编辑页面颁发机构/持有人/备注字段丢失 |
| !1429 | fix(ca): CO-432 修复CA借用申请校验caCertificateId不能为空的bug |
| !1430 | fix(casework): CO-430 修复项目档案文件下载"文件路径越界"400错误 |
| !1431 | fix(ca): CO-436 修复CA信息编辑后保存报错 |
| !1432 | fix(excel): 统一 ExcelAutoSizeHelper 防御 autoSizeColumn 字体缺失异常 |
| !1433 | fix(ca): CO-437 修复CA证书/平台账户批量导入模板下载文件损坏bug |
| !1434 | fix(excel): CO-438 三层防御修复 Fontconfig head is null |
| !1435 | fix(deploy): 补全生产部署 headless 配置缺口（CO-438 防复发） |

## 四、改动范围

- **多个文件变更**（涉及后端、前端、文档）
- **无 Flyway 迁移**（纯业务修复+改进）

### 重点修复

- **CO-430 项目档案文件下载**：修复"文件路径越界"400 错误，补充路径安全校验
- **CO-431 CRM 对接人映射**：按 CRM position 字段映射对接人角色，不按下标强行分配；兜底改用 `EXTERNAL_ROLE_N` 根除撞车丢人
- **CO-432 CA 借用申请**：修复校验 `caCertificateId` 不能为空的 bug
- **CO-435 CA 编辑页面**：修复颁发机构/持有人/备注字段丢失
- **CO-436 CA 编辑保存**：修复编辑后保存报错（密码不回填脱敏值）
- **CO-437 CA 批量导入模板**：修复证书/平台账户批量导入模板下载文件损坏 bug
- **CO-438 Excel Fontconfig**：三层防御修复 headless 环境 `Fontconfig head is null`（生产部署 headless 配置缺口补齐，ExcelAutoSizeHelper 防御增强）
- **品牌授权导入操作日志**：details 改为中文可读格式
- **操作日志字段名**：变更摘要字段名展示为中文

## 五、Flyway 预检结果

### Step 1: 服务器 validate（部署前）

```
07:02:19.458 [main] INFO org.flywaydb.core.internal.command.DbValidate -- Successfully validated 187 migrations (execution time 00:00.089s)
VALIDATE OK - all checksums match
```

### Step 2: DB 已应用版本 vs 源码最新版本

| 维度 | 版本 |
|---|---|
| DB 已应用最新 | V1123（add qualification manage permission） |
| 源码最新 | V1123（无新增） |
| 待应用 | 无 |

### Step 3: remote-deploy.sh 内置 validate

```
Successfully validated 187 migrations (execution time 00:00.067s)
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

### 迁移文件安全性

无新迁移，无需安全性评估。

## 六、部署步骤

| 步骤 | 结果 | 备注 |
|---|---|---|
| 1. 早操三连 | ✅ | dev-env.sh（CHAT_ONLY 模式） + sync-env.sh（ff-only） + check-git-wrapper.sh 全通过 |
| 2. 基线确认 | ✅ | trae 锚点 HEAD `261830696`，与 origin/main 一致，25 个增量 commit |
| 3. 服务器现状 | ✅ | 上一版本 2b6d7e7f3-api8080，health UP |
| 4. Flyway 预检 3 步 | ✅ | validate OK + DB 版本对比（无新迁移）+ remote-deploy 内置 |
| 5. 本地打包 | ✅ | RELEASE_ID=261830696-api8080，VITE_API_BASE_URL= 同源构建，backend BUILD SUCCESS 22.969s |
| 6. 产物校验 | ✅ | jar 内 Flyway 迁移版本无重复（186 个 V*.sql + B73 = 187），最新 V1123 |
| 7. 上传 + 部署 | ✅ | scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） |
| 8. 后端重启 | ✅ | 2026-07-01 07:04:43 CST active (running) |
| 9. 健康检查 | ✅ | remote-deploy.sh 内置健康检查第 1 次通过 |
| 10. 迁移应用验证 | ✅ | 无新迁移，DB 仍为 V1123 |
| 11. Smoke 测试 | ✅ | health 200, readiness 200, 400/403/401 路由验证 |
| 12. 前端验证 | ✅ | / 200, /login 200, 入口 assets/index-k38w2-3_.js 匹配 |
| 13. GitHub 同步检查 | ✅ | 0 落后（部署前已完全一致） |
| 14. 配置清理检查 | ✅ | SHOW_DETAILS=always 保留（历史三次用户决定：运维监控需要） |

## 七、验证结果

### 健康检查

```
status: UP
components:
  aiProvider: UP (doubao, deepseek-v3-2-251201, apiKeyConfigured: true)
  db: UP (MySQL, isValid())
  diskSpace: UP (free: 45104799744)
  jwt: UP (HMAC-SHA256, secretLength: 64, STRONG)
  livenessState: UP
  ping: UP
  readinessState: UP
  redis: UP (6.2.19)
  sidecar: UP (http://localhost:8000, reachable)
```

### Readiness 状态

```
status: UP
readinessState: UP
```

**注意**：本次未出现 Kafka SDK readiness 延迟（第 8/9/10/13/15 次历史问题），第 1 次直接通过。

### Smoke 测试（400/403/401 替代验证）

> Admin 密码未授予，使用 HTTP 状态码替代验证接口路由（第 6 次起固化策略）。

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| GET /actuator/health | 200 UP | 200 UP | ✅ |
| GET /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| POST /api/auth/login (空 body) | 400 | 400 | ✅ |
| GET /api/projects | 403 | 403 | ✅ |
| GET /api/integration/crm/health | 401 | 401 | ✅ |
| GET / | 200 | 200 | ✅ |
| GET /login | 200 | 200 | ✅ |

## 八、GitHub 镜像同步

| 维度 | 值 |
|---|---|
| 部署前 | 0 落后（Gitee CI 已自动同步） |
| 部署后 | 0 落后（完全一致） |

```
╔══════════════════════════════════════════════════════════════╗
║  ✅ Gitee ↔ GitHub 镜像已完全一致                            ║
╠══════════════════════════════════════════════════════════════╣
║  Gitee main:  26183069698bfd925d44e50a3fabb3e801ec3621      ║
║  GitHub main: 26183069698bfd925d44e50a3fabb3e801ec3621      ║
║  状态: 完全一致                                              ║
╚══════════════════════════════════════════════════════════════╝
```

## 九、回滚信息

| 项目 | 值 |
|---|---|
| 回滚方式 | `scp + remote-deploy.sh` 重新部署旧版本 |
| 旧版本 artifact | `/opt/xiyu-bid/releases/2b6d7e7f3-api8080/` |
| DB 备份 | 自动备份在 `/opt/xiyu-bid/db-backups/` |
| 回滚触发条件 | Smoke 失败（P0 接口不可用）或启动 4 分钟内 health 不 UP |
| 回滚owner | 部署执行人 |

## 十、经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| 经验 1 Flyway 预检 3 步 | ✅ Step 1 服务器 validate + Step 2 DB 版本对比 + Step 3 remote-deploy 内置 |
| 经验 3 同源构建 VITE_API_BASE_URL= | ✅ `RELEASE_ID=261830696-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh` |
| 经验 4 Smoke 400/403/401 替代 | ✅ 7/7 接口路由验证全绿 |
| 经验 5 GitHub 镜像同步 | ✅ 部署后确认同步状态，Gitee/GitHub 完全一致 |
| 经验 6 SHOW_DETAILS 保留决策 | ✅ 检查确认保留，运维监控需要 |
| 经验 8 SYSTEMCTL_SUDO=true | ✅ remote-deploy.sh 默认 true，服务正常重启 |
| 经验 11 服务器 /tmp/migration-mysql/ | ✅ validate 通过（不依赖 info 输出），DB 版本通过 SQL 查询确认 |
| 经验 16 Mac HTTP_PROXY 绕过 | ✅ SSH 内部 curl 加 `--noproxy "*"`，直接访问内网 IP |
| 经验 16 本次教训延伸 | ✅ CO-438 生产 headless 配置缺口补全，Fontconfig 三层防御 |

## 十一、风险提示

| 风险 | 评估 | 处置 |
|---|---|---|
| Kafka SDK readiness 延迟 | 低 | 本次第 1 次即通过，无延迟 |
| Flyway checksum mismatch | 低 | 服务器 validate OK，DB 版本对齐 |
| 前端目录权限（nginx:nginx） | 低 | remote-deploy.sh 自动处理 |
| Excel Fontconfig 生产环境 | 已修复 | CO-438 三层防御：headless 配置 + autoSize 防御 + 字体兜底 |

## 十二、部署确认清单

- [x] 健康检查全部 UP
- [x] Smoke 测试 7/7 通过
- [x] 无 Flyway 迁移，无需验证 DB 迁移应用
- [x] 前端入口 JS 与 release 一致
- [x] GitHub 镜像完全同步
- [x] 锚点分支与 origin/main 一致
- [x] 已生成部署报告

---

**执行人**：trae agent
**部署时间**：2026-07-01 07:00 ~ 07:05 CST
