# 第 30 次生产部署报告 — 2026-07-01

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 30 次 |
| 部署日期 | 2026-07-01 |
| Release ID | `b962dc953-api8080` |
| 上一版本 | `20a82ecb0-api8080`（第 29 次，2026-07-01 09:18:31 UTC） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 部署人 | trae agent |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ 第 1 次即通过（无 Kafka 延迟） |
| Smoke 测试 | ✅ 7/7 全通过 |
| 回滚状态 | 未需要，ready |

## 基线信息

- **本地分支**：`agent/trae-init`（锚点分支，ff-only 同步到最新 main）
- **本地 HEAD**：`b962dc953e83c0b6a7c5e69ff1ce7d4b8e1a7f2c`
- **Gitee origin/main**：`b962dc953`（!1490 feat(CO-447): 五个列表页默认按创建时间倒序展示）
- **GitHub 镜像**：部署前落后 22 commit，部署后已同步一致
- **增量 commits**：22 个（从 `20a82ecb0` 到 `b962dc953`）
- **变更范围**：backend + frontend 多模块

## PR 列表（去重后的核心 PR）

| PR | 类型 | 说明 |
|---|---|---|
| !1480 | fix(ca) | CO-435 修复CA编辑空密码保存失败 - 移除DTO @NotBlank，create方法手动校验密码非空 |
| !1482 | fix(archive) | CO-452 项目档案详情页权限注解不一致导致投标专员403 |
| !1483 | fix(archive) | CO-453 修复项目档案PDF预览报错 + 档案文件下载同类问题 |
| !1485 | fix(ca) | CO-454 电子CA密码字段改为非必填 |
| !1486 | fix(notification) | 补全任务分配通知缺失的 4 条路径 |
| !1487 | fix(permission) | CO-438 根治 - OSS 缓存命中时合并 DB RoleProfile 管理权限点 |
| !1488 | feat(ca) | CO-451 保管员字段优化 — 删除保管员ID + 显示"姓名（工号）" |
| !1489 | fix(ca) | CO-432 新增盖章承诺书上传接口解决404错误 |
| !1490 | feat(list) | CO-447 五个列表页默认按创建时间倒序展示 |

> 注：增量 commit 列表含多次 merge commit，上述为去重后的实际 PR 列表。

## 改动范围

- **后端**：
  - CA 证书管理：CO-435 编辑空密码保存失败修复、CO-454 密码字段改为非必填、CO-451 保管员字段优化、CO-432 新增盖章承诺书上传接口
  - 权限治理：CO-438 根治 - OSS 缓存命中时合并 DB RoleProfile 管理权限点
  - 项目档案：CO-452 详情页权限注解不一致导致投标专员403、CO-453 PDF预览报错修复
  - 通知系统：补全任务分配通知缺失的 4 条路径
  - 列表排序：五个列表页默认按创建时间倒序展示（CO-447）
  - CA 表单重构：提取 useCaPasswordReveal composable，保持 CAFormDialog < 300 行

- **前端**：
  - CA 证书：保管员字段显示优化（姓名+工号）、密码字段非必填、盖章承诺书上传
  - 项目档案：PDF预览和下载修复
  - 列表页：五个列表默认倒序展示
  - 权限显示：投标专员档案详情页权限修复

- **数据库迁移**：
  - 无新增迁移（DB 版本保持 V1127）

## Flyway 预检 3 步结果

1. **服务器 validate**：✅ 通过（191 migrations, checksums match）
2. **DB 版本对比**：✅ DB 最新 V1127，无新增迁移
3. **remote-deploy 内置 validate**：✅ 通过

## 部署步骤

1. 早操三连：`source dev-env.sh` + `sync-env.sh`（锚点分支手动 ff-only） + `check-git-wrapper.sh` ✅
2. 确认基线：git status 干净，HEAD = origin/main ✅
3. 服务器现状：deployed-release.json + health + 增量 commit ✅
4. Flyway 预检 3 步 ✅
5. 本地打包：`RELEASE_ID=b962dc953-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh` ✅
6. 产物校验：jar 内迁移文件无重复版本（190 个 V*.sql，最新 V1127） ✅
7. 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） ✅
8. 健康检查：第 1 次即通过（无 Kafka 延迟） ✅
9. Smoke 测试：7/7 全通过 ✅
10. GitHub 镜像同步：已同步一致 ✅
11. 配置清理检查：`SHOW_DETAILS=always` 保留（运维监控需要，历史决策延续） ⚠️

## 验证结果

### 健康检查（内部访问）

```json
{
  "status": "UP",
  "components": {
    "aiProvider": {"status": "UP", "provider": "doubao", "model": "deepseek-v3-2-251201"},
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

本次无新增迁移，DB 版本保持 V1127。

### Smoke 测试（外部访问，绕过 Mac 代理）

| 端点 | 预期 | 实际 | 状态 |
|---|---|---|---|
| `/actuator/health` | 200 | 200 | ✅ |
| `/actuator/health/readiness` | 200 | 200 | ✅ |
| `/api/auth/login` (空密码) | 400 | 400 | ✅ |
| `/api/projects` | 403 | 403 | ✅ |
| `/api/integration/crm/health` | 401 | 401 | ✅ |
| `/` (前端首页) | 200 | 200 | ✅ |
| `/assets/index-*.js` | 匹配 | `index-BLK5Ee2m.js` | ✅ |

## GitHub 同步

- **部署前**：GitHub 镜像落后 22 commit
- **部署后**：`bash scripts/sync-to-github.sh` 已同步
- **验证**：两边 main HEAD 均为 `b962dc953e83c0b6a7c5e69ff1ce7d4b8e1a7f2c`

## 回滚信息

- **回滚脚本**：无新增迁移，沿用 V1127 回滚脚本 `U1127__backfill_oss_user_employee_number.sql`
- **回滚风险**：回滚后本次修复的 9 个功能/修复会失效（CO-432/435/438/447/451/452/453/454 + 通知路径补全）
- **数据库备份**：`/opt/xiyu-bid/db-backups/winbid-b962dc953-api8080-*.sql.gz`
- **前端 artifact**：`.release/20a82ecb0-api8080/`
- **后端 artifact**：`.release/20a82ecb0-api8080/backend/app.jar`

## 经验沉淀应用情况

本次部署遵循 `xiyu-server-deploy` skill 16 条经验：

1. **Flyway 预检 3 步法**：全部通过 ✅
2. **Readiness 延迟恢复**：未出现（第 1 次即通过） ✅
3. **生产前端同源构建**：`VITE_API_BASE_URL=` 显式设空 ✅
4. **Smoke 测试限制**：admin 密码未知，使用 400/403/401 替代验证 ✅
5. **GitHub 镜像同步**：部署后已同步 ✅
6. **临时调试配置清理**：`SHOW_DETAILS=always` 保留（运维监控需要） ⚠️
7. **幂等迁移设计**：无新增迁移 ✅
8. **systemctl sudo 权限**：`SYSTEMCTL_SUDO=true` 已配置 ✅
9. **git.properties commit id**：未检查（非关键）
10. **破坏性 schema 变更**：无新增迁移 ✅
11. **服务器 /tmp/migration-mysql/ 目录过时**：未依赖 info 输出，直接查 SQL ✅
12. **rollback 脚本命名规范**：无新增迁移 ✅
13. **前端目录权限**：未涉及（前端已存在）
14. **macOS `._*` 残留文件**：`COPYFILE_DISABLE=1` 打包时已设置 ✅
15. **Flyway 防护体系**：15 项防护全部生效 ✅
16. **Mac HTTP_PROXY 502**：使用 `--noproxy '*'` 绕过代理 ✅

## 风险提示

- **SHOW_DETAILS=always**：生产环境暴露健康详情，后续可收紧为 `never`
- **CO-438 权限合并逻辑**：OSS 缓存命中时合并 DB RoleProfile 管理权限点，需观察是否存在权限误放风险
- **CA 密码非必填**：CO-454 将 CA 密码改为非必填，create 方法手动校验非空，需确认 edit 场景是否需要额外约束

## 部署确认清单

- [x] 早操三连执行完毕
- [x] Flyway 预检 3 步全部通过
- [x] 本地打包产物校验通过
- [x] 远程部署成功
- [x] 健康检查通过
- [x] Smoke 测试 7/7 全通过
- [x] GitHub 镜像已同步
- [x] 部署报告已生成
