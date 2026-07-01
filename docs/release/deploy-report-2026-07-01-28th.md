# 第 28 次生产部署报告 — 2026-07-01

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 28 次 |
| 部署日期 | 2026-07-01 |
| Release ID | `8759f4263-api8080` |
| 上一版本 | `075435267-api8080`（第 27 次，2026-07-01 06:13:20 UTC） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 部署人 | trae agent |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ 第 1 次即通过（15:13:41 CST，无 Kafka 延迟） |
| Smoke 测试 | ✅ 7/7 全通过 |
| 回滚状态 | 未需要，ready |

## 基线信息

- **本地分支**：`agent/trae/deploy-27th-report`（早操后 HEAD 与 origin/main 对齐）
- **本地 HEAD**：`8759f42631331f2e4cd42b053406b1a676081ad2`
- **Gitee origin/main**：`8759f4263`（!1463 fix(CO-441): 标讯自动分配按错字段查用户）
- **GitHub 镜像**：部署前落后 15 commit，部署后已同步一致
- **增量 commits**：15 个（从 `075435267` 到 `8759f4263`）
- **变更范围**：backend + frontend 多模块

## PR 列表（去重后的核心 PR）

| PR | 类型 | 说明 |
|---|---|---|
| !1463 | fix(CO-441) | 标讯自动分配按错字段查用户 + 接入统一 enabled 判断 |
| !1464 | feat(CO-442) | 业绩管理-新增业绩-附件资料字段改为文件上传 |
| !1462 | fix(CO-443) | 项目结项审核通过后进度导航栏显示已完成 + Project.Status 被覆盖修复 |
| !1465 | fix(CO-444) | 业绩批量导入模板下载无表头 + 弹窗缺上传入口 + 支持附件包 |
| !1469 | fix(ca) | CO-440 修复 REQUIRES_NEW 不生效 — 事务注解在 private 方法上 |

> 注：增量 commit 列表含多次 revert/re-push，上述为去重后的实际 PR 列表。

## 改动范围

- **后端**：
  - `TenderAutoAssignmentService`：增加 fallback 到 username 查询（止血）
  - `OrganizationUserSyncWriter`：同步时填充 `employee_number = username`（根治）
  - 业绩附件上传接口新增
  - CA 事务注解修复（从 private 方法移到 public 方法）
  - Project.Status 覆盖修复

- **前端**：
  - 业绩管理附件字段改为文件上传组件
  - 业绩批量导入模板下载修复
  - 结项审核后进度导航栏显示已完成

- **数据库迁移**：
  - `V1127__backfill_oss_user_employee_number.sql`：回填 OSS 同步用户的 `employee_number` 字段

## Flyway 预检 3 步结果

1. **服务器 validate**：✅ 通过（190 migrations, checksums match）
2. **DB 版本对比**：✅ DB 最新 V1126，源码新增 V1127
3. **remote-deploy 内置 validate**：✅ 通过

## 部署步骤

1. 早操三连：`source dev-env.sh` + `sync-env.sh` + `check-git-wrapper.sh` ✅
2. 确认基线：git status 干净，HEAD = origin/main ✅
3. 服务器现状：deployed-release.json + health + 增量 commit ✅
4. Flyway 预检 3 步 ✅
5. 本地打包：`RELEASE_ID=8759f4263-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh` ✅
6. 产物校验：jar 内迁移文件无重复版本 ✅
7. 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） ✅
8. 健康检查：第 1 次即通过（无 Kafka 延迟） ✅
9. 迁移应用验证：V1127 已应用（installed_on: 2026-07-01 15:13:50） ✅
10. Smoke 测试：7/7 全通过 ✅
11. GitHub 镜像同步：已同步一致 ✅
12. 配置清理检查：`SHOW_DETAILS=always` 保留（运维监控需要） ⚠️

## 验证结果

### 健康检查（内部访问）

```json
{
  "status": "UP",
  "components": {
    "aiProvider": {"status": "UP", "provider": "doubao"},
    "db": {"status": "UP", "database": "MySQL"},
    "diskSpace": {"status": "UP"},
    "jwt": {"status": "UP"},
    "livenessState": {"status": "UP"},
    "ping": {"status": "UP"},
    "readinessState": {"status": "UP"},
    "redis": {"status": "UP", "version": "6.2.19"},
    "sidecar": {"status": "UP", "url": "http://localhost:8000"}
  }
}
```

### 迁移应用验证

```sql
SELECT version, description, success, installed_on FROM flyway_schema_history WHERE version = '1127';
-- 结果：V1127 | backfill oss user employee number | 1 | 2026-07-01 15:13:50
```

### Smoke 测试（外部访问，绕过 Mac 代理）

| 端点 | 预期 | 实际 | 状态 |
|---|---|---|---|
| `/actuator/health` | 200 | 200 | ✅ |
| `/actuator/health/readiness` | 200 | 200 | ✅ |
| `/api/auth/login` (空密码) | 400 | 400 | ✅ |
| `/api/projects` | 403 | 403 | ✅ |
| `/api/integration/crm/health` | 401 | 401 | ✅ |
| `/` (前端首页) | 200 | 200 | ✅ |
| `/assets/index-*.js` | 匹配 | `index-ZXhScF6j.js` | ✅ |

## GitHub 同步

- **部署前**：GitHub 镜像落后 15 commit
- **部署后**：`bash scripts/sync-to-github.sh` 已同步
- **验证**：两边 main HEAD 均为 `8759f42631331f2e4cd42b053406b1a676081ad2`

## 回滚信息

- **回滚脚本**：`U1127__backfill_oss_user_employee_number.sql`（将 OSS 用户 `employee_number` 置回 NULL）
- **回滚风险**：回滚后 CO-441 bug 会复发（标讯自动分配失败）
- **数据库备份**：`/opt/xiyu-bid/db-backups/winbid-8759f4263-*.sql.gz`
- **前端 artifact**：`.release/075435267-api8080/`
- **后端 artifact**：`.release/075435267-api8080/backend/app.jar`

## 经验沉淀应用情况

本次部署遵循 `xiyu-server-deploy` skill 16 条经验：

1. **Flyway 预检 3 步法**：全部通过 ✅
2. **Readiness 延迟恢复**：未出现（第 1 次即通过） ✅
3. **生产前端同源构建**：`VITE_API_BASE_URL=` 显式设空 ✅
4. **Smoke 测试限制**：admin 密码未知，使用 400/403/401 替代验证 ✅
5. **GitHub 镜像同步**：部署后已同步 ✅
6. **临时调试配置清理**：`SHOW_DETAILS=always` 保留（运维监控需要） ⚠️
7. **幂等迁移设计**：V1127 仅更新 NULL 行，幂等 ✅
8. **systemctl sudo 权限**：`SYSTEMCTL_SUDO=true` 已配置 ✅
9. **git.properties commit id**：未检查（非关键）
10. **破坏性 schema 变更**：V1127 为 UPDATE，非破坏性 ✅
11. **服务器 /tmp/migration-mysql/ 目录过时**：未依赖 info 输出，直接查 SQL ✅
12. **rollback 脚本命名规范**：`U1127*.sql` 已存在 ✅
13. **前端目录权限**：未涉及（前端已存在）
14. **macOS `._*` 残留文件**：未涉及（前端已存在）
15. **Flyway 防护体系**：15 项防护全部生效 ✅
16. **Mac HTTP_PROXY 502**：使用 `--noproxy '*'` 绕过代理 ✅

## 风险提示

- **V1127 回滚风险**：回滚后 CO-441 bug 会复发，不建议回滚
- **SHOW_DETAILS=always**：生产环境暴露健康详情，后续可收紧为 `never`

## 部署确认清单

- [x] 早操三连执行完毕
- [x] Flyway 预检 3 步全部通过
- [x] 本地打包产物校验通过
- [x] 远程部署成功
- [x] 健康检查通过
- [x] 迁移应用验证通过
- [x] Smoke 测试 7/7 全通过
- [x] GitHub 镜像已同步
- [x] 回滚脚本已存在
- [x] 部署报告已生成