# 第 61 次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 61 次 |
| 部署环境 | **测试环境**（test） |
| 部署日期 | 2026-07-09 |
| Release ID | `c15b54834-api8080` |
| 部署时间 | 2026-07-09 10:44:18 CST |
| 前置 Release | `34987692d-api8080`（2026-07-09 00:08 UTC 激活） |
| 部署结果 | ✅ 成功（remote-deploy.sh 健康检查因 Kafka SDK 延迟超时，服务后续自恢复） |
| 新增 Flyway 迁移 | V1158（清理重复角色码 + 加唯一约束）、V1159（删除重复唯一索引） |
| 回滚状态 | 未需回滚 |
| 部署性质 | 正常增量部署（含破坏性 schema 治理） |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae/drop-duplicate-roles-code-index`（HEAD = origin/main） |
| 部署 commit | `c15b54834`（!1918 资质列表下载按钮） |
| 前置 commit | `34987692d` |
| 增量 commit 数 | 27 |
| GitHub 镜像 | 部署前落后 31 commit；部署后已同步 |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080（nginx 反代到 18080） |

## PR 列表

本次部署涵盖 27 个增量 commit（关键 PR）：

| Commit | PR | 描述 | 类型 |
|---|---|---|---|
| `f6d661179` | !1912 | fix(migration): V1158 清理重复角色码 + 加 roles.code 唯一约束 | fix(db) |
| `d6c91aad4` | !1917 | fix(migration): V1159 删除 roles.code 重复唯一索引 uk_roles_code | fix(db) |
| `0b1ac8391` | !1910 | feat(obs): 标讯模块接入华为云 OBS 大文件直传 + 后端 FP-Java 重构 | feat |
| `a4d42cc1f` | !1910 | feat(obs): 标讯模块接入华为云 OBS 大文件直传 | feat |
| `a6281b93c` | !1911 | refactor(file/obs): 后端 FP-Java 重构（Policy/Gateway 拆分 + DTO record） | refactor |
| `1d79f9833` | - | fix(file/obs): D4-1 补救 — HuaweiObsMetadataGateway ObsClient 单例化 | fix |
| `56d29a967` | !1918 | fix(CO-554 v2): 资质列表下载按钮改为按附件判定 + 多附件打包 zip | fix |
| `3e320c715` | !1914 | fix(tender): 标讯列表部门兜底改用 Tender.projectManagerId | fix |
| `8a31a434e` | !1913 | fix(notification): CO-539 统一任务通知标题格式 | fix |
| `3b42bd49b` | !1916 | fix(perm): CO-551 修订 — 同步 specs/032 与代码注释，消除 system.admin 矛盾 | fix |

## 改动范围

- **数据库**：V1158 清理重复角色码 + 加 `uk_roles_code` 唯一约束；V1159 删除 V1158 新增的重复索引（B73 基线已有 `UK_ch1113horj4qr56f91omojv8`）
- **后端**：OBS 直传 + FP-Java 重构（Policy/Gateway 拆分）、CO-554 资质下载、CO-539 通知标题、CO-551 specs 修订
- **前端**：标讯模块 OBS 直传接入（ManualTenderDialog/TenderBasicInfoTab/ProjectDetailBidAgentTenderUpload/ProjectTenderBreakdownDialog）、资质列表下载按钮
- **经验沉淀**：本次 V1158+V1159 是 roles.code 重复索引治理的收尾，V1158 幂等设计确保无重复数据时步骤 1-2 为 no-op

## Flyway 预检结果

### Step 1: Flyway validate（部署前）
```
VALIDATE OK - all checksums match
Successfully validated 220 migrations (execution time 00:00.083s)
```

### Step 2: DB 已应用版本（部署前）
```
version  description                                              success  installed_on
1157     add unique index to warehouse name                       1        2026-07-09 08:08:53
1156     add alert history dedup index                            1        2026-07-09 08:08:53
1155     bid file table                                           1        2026-07-08 21:49:56
1154     drop unique constraint from platform account name        1        2026-07-08 20:02:41
```

### Step 2.1: roles 表预检（破坏性迁移安全确认）
- `SHOW INDEX FROM roles WHERE Column_name="code"`：B73 基线已有 `UK_ch1113horj4qr56f91omojv8`
- 重复角色码查询：**无重复**（V1158 步骤 1-2 为 no-op，仅步骤 3 加索引）

### Step 3: remote-deploy.sh 内置 validate
```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

## 部署步骤

1. **本地打包**（同源构建）
   ```bash
   RELEASE_ID="c15b54834-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
   ```
   - jar: `bid-platform-1.0.3.jar`（221 个迁移文件，V1158/V1159 已包含）
   - 前端入口: `assets/index-C4ujzl9P.js`
   - 产物大小: 153M

2. **产物校验**
   - jar 内 Flyway 迁移版本无重复 ✅
   - V1158 + V1159 存在性确认 ✅

3. **上传 + 部署**
   ```bash
   scp .release/xiyu-bid-release-c15b54834-api8080.tar.gz scripts/release/remote-deploy.sh jetty@172.16.38.78:/opt/xiyu-bid/incoming/
   ```
   - `SYSTEMCTL_SUDO=true`（jetty 用户 NOPASSWD sudo）
   - DB 备份: `winbid-c15b54834-<timestamp>.sql.gz`

4. **remote-deploy.sh 执行**
   - Flyway validate 通过 ✅
   - 后端停止 → jar 覆盖 → 服务启动 ✅
   - health check: ❌ 120 次尝试失败（Kafka SDK readiness 延迟，已知行为）

## 验证结果

### 健康检查（部署后手动验证）
```
port 8080  health: {"status":"UP"} ✅
port 18080 health: {"status":"UP"} ✅
readinessState: UP ✅
livenessState: UP ✅
db: UP ✅ | redis: UP ✅ | sidecar: UP ✅
```

### Flyway 迁移应用验证
```
version  description                                            success  installed_on
1158     cleanup duplicate roles add unique constraint          1        2026-07-09 10:44:25
1159     drop duplicate roles code index                        1        2026-07-09 10:44:25
```

### roles 表索引最终状态
- 仅 `UK_ch1113horj4qr56f91omojv8`（B73 基线唯一索引）
- V1158 新增的 `uk_roles_code` 已被 V1159 删除 ✅

### Smoke 测试
| 检查项 | 结果 | 说明 |
|---|---|---|
| `GET /actuator/health` | 200 UP | 后端健康 |
| `GET /actuator/health/readiness` | 200 UP | 就绪状态 |
| `POST /api/auth/login`（空 body） | 400 | 预期（空密码验证错误） |
| `GET /api/projects` | 403 | 预期（需认证） |
| `GET /api/integration/crm/health` | 401 | 预期（需认证） |
| `GET /`（前端） | 200 | 前端入口正常 |
| `GET /login`（前端） | 200 | 登录页正常 |
| 前端入口 `assets/index-C4ujzl9P.js` | 一致 | 与打包产物匹配 |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前落后 | 31 commit |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步结果 | ✅ 两边 main 完全一致（`c15b54834`） |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 第 13/14/15 次决定保留，非临时调试配置 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` | 无其他临时配置 | ✅ |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需回滚 |
| 前置 release | `/opt/xiyu-bid/releases/34987692d-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-c15b54834-<timestamp>.sql.gz` |
| 回滚命令 | 恢复前置 jar + `sudo systemctl restart xiyu-bid-backend`（V1158/V1159 为幂等 DDL，回滚 jar 即可） |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 部署前 validate + DB 版本对比 + roles 表预检 |
| #2 Kafka SDK readiness 延迟 | ✅ 已知行为，health check 超时后服务自恢复 |
| #3 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空 |
| #4 Smoke 测试 admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| #5 GitHub 镜像同步 | ✅ 部署后同步 |
| #6 临时调试配置清理 | ✅ 检查 SHOW_DETAILS=always（已知保留） |
| #7 幂等迁移设计 | ✅ V1158 幂等（无重复时 no-op） |
| #8 systemctl sudo 权限 | ✅ `SYSTEMCTL_SUDO=true` |
| #16 Mac HTTP_PROXY 502 | ✅ curl 统一 `--noproxy '*'` |

## 风险提示

1. **V1158 破坏性迁移**：已确认测试环境无重复角色码，步骤 1-2 为 no-op。生产环境部署前需执行相同的预检（`SELECT code, COUNT(*) FROM roles GROUP BY code HAVING COUNT(*) > 1`）。
2. **Kafka SDK readiness 延迟**：remote-deploy.sh health check 窗口（120 次 × 2s ≈ 4 分钟）可能不足，建议考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async`。
3. **GitHub 镜像同步**：本次落后 31 commit，已同步。后续部署建议每次都执行同步检查。

## 部署确认清单

- [x] 环境门禁确认（test 172.16.38.78）
- [x] 早操三连全通过
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功（同源构建）
- [x] 产物校验通过（jar 内迁移文件 + 前端入口）
- [x] 上传 + 部署完成
- [x] 健康检查通过（8080 + 18080 均 UP）
- [x] Flyway 迁移应用验证（V1158 + V1159 success=1）
- [x] Smoke 测试全通过
- [x] GitHub 镜像同步完成
- [x] 配置清理检查通过
- [x] 部署报告生成
