# 第 27 次生产部署报告 — 2026-07-01

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 27 次 |
| 部署日期 | 2026-07-01 |
| Release ID | `075435267-api8080` |
| 上一版本 | `330d3241a-api8080`（第 26 次，2026-07-01 01:07 UTC） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 部署人 | trae agent |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ 第 1 次即通过（14:18:00 CST，无 Kafka 延迟） |
| Smoke 测试 | ✅ 7/7 全通过 |
| 回滚状态 | 未需要，ready |

## 基线信息

- **本地分支**：`agent/trae/fix-redis-doc-version`（HEAD 与 origin/main 对齐）
- **本地 HEAD**：`075435267ac3ae545ad39f27128f457b05fde4ea`
- **Gitee origin/main**：`075435267`（!1457 feat(crm): CRM token 按用户维度管理正式上线 CO-152）
- **GitHub 镜像**：部署前落后 7 commit，部署后已同步一致
- **增量 commits**：44 个（从 `330d3241a` 到 `075435267`）
- **变更范围**：116 文件，+2823 / -794

## PR 列表（!1442 ~ !1460）

| PR | 类型 | 说明 |
|---|---|---|
| !1442 | docs(release) | 第 26 次部署报告 |
| !1443 | fix(casework) | 项目档案导出台账状态中文映射 + 删除中标结果列 |
| !1444 | fix(ca) | CO-440 修复 CA 借用申请时 UnexpectedRollbackException |
| !1445 | refactor(ca) | Review 修复 — 提取 ExcelDropDownHelper + 统一枚举常量 + 电子账号条件必填 |
| !1446 | refactor | 设计评估优化 CA 通知系统 |
| !1447 | fix(permission) | 行政人员(bid-administration)访问资质证书读端点 403 问题 (CO-439) |
| !1448 | fix | 标讯列表项目负责人待分配 + 详情转派按钮逻辑修复 (CO-438) |
| !1449 | fix | AlertRule condition 列名加反引号 + RoleProfileCatalog Javadoc 压缩 |
| !1450 | feat(platform-account) | 新增平台账户创建权限白名单机制 |
| !1451 | chore | 清理 10 个僵尸锁文件 |
| !1452 | fix(ca-management) | 下架确认弹窗取消时未捕获 reject('cancel') 触发 ErrorBoundary (CO-441) |
| !1453 | fix(permission) | 投标专员(bid-Team)业绩/仓库/人员证书三模块功能缺失 (CO-438) |
| !1454 | fix(permission) | 补充行政人员前端导航权限 — 与 #1447 互补修复 (CO-439) |
| !1455 | fix(audit) | CO-440 投标专员查看业绩操作日志 403 |
| !1456 | docs(redis) | 修正 Redis 版本口径为 6.2（生产实际 6.2.19） |
| !1457 | feat(crm) | CRM token 按用户维度管理正式上线 (CO-152) |
| !1458 | fix(CO-442) | 修复结项阶段投标文件下载 400 + 业务逻辑一刀切 |
| !1459 | feat(ca) | CA 密码掩码展示 + 权限查看 (CO-435) |
| !1460 | fix(CO-442) | 前端下载错误提示透传后端业务消息 |

## Flyway 迁移

### 新增迁移（3 个，全部成功应用）

| 版本 | 描述 | 幂等性 | 破坏性 | 应用时间 |
|---|---|---|---|---|
| V1124 | seed platform account create whitelist | ✅（ON DUPLICATE KEY UPDATE） | 否 | 2026-07-01 14:14:57 |
| V1125 | fix co 439 admin staff navigation permissions | ✅（CASE WHEN LIKE） | 否 | 2026-07-01 14:14:57 |
| V1126 | add crm sales no to users | 非幂等（ADD COLUMN） | 否（只加列） | 2026-07-01 14:14:57 |

### Rollback 脚本（齐全）

- `U1124__seed_platform_account_create_whitelist.sql`
- `U1125__fix_co_439_admin_staff_navigation_permissions.sql`
- `U1126__add_crm_sales_no_to_users.sql`

## Flyway 预检 3 步法结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（187 migrations） |
| Step 2: DB 版本对比 | ✅ DB 最新 V1123，源码新增 V1124/V1125/V1126 |
| Step 3: remote-deploy 内置 validate | ✅ 通过，仅 pending 新迁移为预期状态 |

## 部署步骤

1. 早操三连：`source dev-env.sh` + `sync-env.sh` + `check-git-wrapper.sh` — 全部通过
2. 确认基线：HEAD = origin/main = `075435267`
3. 服务器现状查询：deployed-release.json + health UP
4. Flyway 预检 3 步法通过
5. 本地打包：`RELEASE_ID=075435267-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`
   - jar 内 Flyway 迁移版本无重复 ✅
   - 前端入口 `assets/index-BAOipuQN.js`
6. 上传 + 部署：`scp` archive + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）
   - Flyway validate 通过
   - Backend artifact 更新
   - 服务 14:14:50 重启
   - 前端验证一致
7. 健康检查：第 1 次即通过（14:18:00，无 Kafka 延迟）
8. 迁移应用验证：V1124/V1125/V1126 全部 success=1
9. Smoke 测试：7/7 全通过
10. GitHub 镜像同步：从落后 7 commit 到完全一致

## 验证结果

### 后端 Smoke（SSH 内部访问，避开 Mac HTTP_PROXY）

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP（无 Kafka 延迟） | ✅ |
| `/api/auth/login`（空 body） | 400 | 400 | ✅ |
| `/api/projects` | 403 | 403 | ✅ |
| `/api/integration/crm/health` | 401 | 401 | ✅ |
| 前端 `/` | 200 | 200 | ✅ |
| 前端 `/login` | 200 | 200 | ✅ |

### Readiness 详情

```json
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"MySQL","validationQuery":"isValid()"}},"readinessState":{"status":"UP"}}}
```

## GitHub 同步

- 部署前：GitHub main 落后 Gitee main 7 个 commit
- 执行 `bash scripts/sync-to-github.sh`
- 部署后：Gitee main = GitHub main = `075435267ac3ae545ad39f27128f457b05fde4ea` ✅

## 回滚信息

- **回滚脚本**：U1124 / U1125 / U1126 均存在
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-075435267-<timestamp>.sql.gz`
- **旧 Release**：`/opt/xiyu-bid/releases/330d3241a-api8080/`（第 26 次）
- **回滚方式**：回滚到第 26 次的 jar + 前端，再按需执行 U1124/U1125/U1126
- **V1126 注意**：ADD COLUMN 回滚仅删列，无数据损失（新列无数据）

## 经验沉淀应用情况

| 经验条目 | 本次应用 |
|---|---|
| 1. Flyway 预检 3 步法 | ✅ 全部执行 |
| 2. Kafka SDK readiness 延迟 | 本次未出现（第 1 次即通过） |
| 3. 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| 4. Smoke admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| 5. GitHub 镜像同步 | ✅ 检查 + 同步 |
| 6. 临时调试配置清理 | `SHOW_DETAILS=always` 历史已决定保留（运维监控） |
| 7. 幂等迁移设计 | ✅ V1124/V1125 幂等，V1126 靠版本号防重复 |
| 8. systemctl sudo | ✅ `SYSTEMCTL_SUDO=true` |
| 12. rollback 脚本命名 | ✅ U1124/U1125/U1126 齐全 |
| 16. Mac HTTP_PROXY 502 | ✅ Smoke 走 SSH 内部访问 |

## 风险提示

1. **V1126 非幂等**：ADD COLUMN 重复执行会报 Duplicate column，靠 Flyway 版本号机制防重复。如需手动修复 DB，注意不要重复执行。
2. **CRM token 按用户维度管理（CO-152）正式上线**：本次核心功能变更。配置了 `crm_sales_no` 的用户将使用专属 CRM JWT token，未配置的用户回退到全局共享 token（兼容存量行为）。需关注 CRM 调用是否正常。
3. **平台账户创建白名单（V1124）**：当前仅 `["00444"]` 在白名单内，需确认是否符合业务预期。
4. **`SHOW_DETAILS=always` 保留**：生产环境暴露健康详情，如后续需收紧安全可改为 `never`。

## 部署确认清单

- [x] 早操三连执行
- [x] 基线确认（HEAD = origin/main）
- [x] 服务器现状查询
- [x] Flyway 预检 3 步法
- [x] 本地打包 + 产物校验
- [x] 上传 + 部署
- [x] 健康检查通过
- [x] 迁移应用验证（V1124/V1125/V1126）
- [x] Smoke 测试 7/7
- [x] GitHub 镜像同步
- [x] 临时配置检查
- [x] 部署报告生成
