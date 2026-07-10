# 第 69 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `a73181b0c-api8080` |
| 部署时间 | 2026-07-10 10:36:15 CST |
| 部署人 | trae agent |
| 特殊说明 | Kafka SDK readiness 延迟导致 remote-deploy.sh 健康检查超时，手动验证后确认恢复 UP（已知行为） |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae/fix-oss-login-role-not-authorized`（任务分支，HEAD = origin/main） |
| HEAD commit | `a73181b0c` |
| origin/main | `a73181b0c`（同步） |
| 上次部署 releaseId | `dceae804e-api8080`（第 68 次） |
| 增量 commit 数 | 28 |
| 增量 PR 数 | 9（!1964-!1972） |
| Flyway 迁移变更 | V1092 修改（collation 修复，已应用且 checksum 已 repair，validate 通过） |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !1972 | fix(auth): OSS 密码登录失败时误抛 RoleNotAuthorizedException + 前端错误提示误导 | bugfix |
| !1971 | feat(permission): 投标专员可查看全量保证金，项目负责人只读账户/CA | feat |
| !1969 | fix: 跨部门协作人员首页 403 + wiki 工程知识沉淀 | bugfix |
| !1968 | fix: 修复人员同步白名单逻辑 + 首次生产部署文档 | bugfix |
| !1967 | fix(webhook): 弃标CRM回调remark字段置空 (CO-568) | bugfix |
| !1966 | fix(integration): 标讯集成更新路径补充 CRM 商机号占用校验 | bugfix |
| !1965 | docs(release): 第 68 次部署报告 (test) | docs |
| !1964 | fix(drafting): 修复前往评分解析按钮页面崩溃 | bugfix |

## 改动范围

- **OSS 密码登录修复**（PR !1972）
  - OSS 用户密码登录失败时误抛 RoleNotAuthorizedException，前端错误提示误导用户
- **保证金权限调整**（PR !1971）
  - 投标专员可查看全量保证金数据，项目负责人只读账户/CA
- **跨部门协作人员 403 修复**（PR !1969）
  - 跨部门协作人员首页 403 — 告警待办权限判断收窄到仅 `settings-alerts` 权限点
  - 7 个 wiki 页面工程知识沉淀
- **人员同步白名单修复**（PR !1968）
  - 修复 skipUnmappedUsers 配置声明但代码未使用导致首次部署人员同步异常
  - V1092 collation 修复 + 首次生产部署文档
- **弃标 CRM 回调修复**（PR !1967）
  - 弃标 CRM 回调 remark 字段置空 (CO-568)
- **标讯集成 CRM 商机号校验**（PR !1966）
  - 标讯集成更新路径补充 CRM 商机号占用校验
- **评分解析按钮崩溃修复**（PR !1964）
  - 修复前往评分解析按钮页面崩溃

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（224 migrations） |
| Step 2: DB 已应用版本 | V1161（最新，与源码一致，无 pending） |
| Step 2: V1092 状态 | 已应用（success=1, checksum=1842156770, installed 2026-06-24） |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK - all checksums match |

## 部署步骤

1. ✅ 环境门禁确认（test / 172.16.38.78）
2. ✅ 早操三连（dev-env.sh + sync-env.sh + check-git-wrapper.sh，门禁 7/7 通过）
3. ✅ 基线确认（HEAD = origin/main = a73181b0c，工作区干净）
4. ✅ 服务器现状检查（dceae804e 健康 UP，readinessState UP）
5. ✅ Flyway 预检 3 步法全部通过（V1092 修改已 repair，无新增迁移）
6. ✅ 本地打包（RELEASE_ID=a73181b0c-api8080，VITE_API_BASE_URL= 同源构建，28.4s）
7. ✅ 产物校验（jar 内 223 个 V*.sql 迁移文件无重复，前端入口 index-Cdl3qYxE.js，153M）
8. ✅ 上传 + 部署（scp + remote-deploy.sh SYSTEMCTL_SUDO=true）
9. ⚠️ 健康检查 — remote-deploy.sh 健康检查超时（120 次，Kafka readiness 延迟），手动验证后确认恢复 UP
10. ✅ Smoke 测试全通过
11. ✅ GitHub 镜像同步（落后 28 commit → 同步完成，两边一致）

## 验证结果

### 后端健康检查（手动验证）

| 组件 | 状态 |
|---|---|
| overall | UP |
| aiProvider | UP（provider=custom, model=qwen3.7-max） |
| db | UP（MySQL, isValid()） |
| diskSpace | UP（free 28.9GB / total 105.5GB） |
| jwt | UP（HMAC-SHA256, secretLength=64, STRONG） |
| livenessState | UP |
| ping | UP |
| readinessState | UP |
| redis | UP（version 6.2.19） |
| sidecar | UP（url=localhost:8000, reachable） |

### Smoke 测试

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 UP | 200 UP | ✅ |
| `GET /actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login` (empty body) | 400 | 400 | ✅ |
| `GET /api/projects` (no auth) | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| `GET /` (frontend) | 200 | 200 | ✅ |
| `GET /login` (frontend) | 200 | 200 | ✅ |
| 前端 JS 入口 | index-Cdl3qYxE.js | index-Cdl3qYxE.js | ✅ |

### 前端一致性验证

- 部署包前端入口：`assets/index-Cdl3qYxE.js`
- 服务器前端入口：`assets/index-Cdl3qYxE.js`
- ✅ 一致

## Kafka SDK Readiness 延迟（已知行为）

- **现象**：remote-deploy.sh 健康检查 120 次（240 秒）未通过，`/actuator/health` 返回 503
- **根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`，Kafka 初始化阻塞主线程导致 readiness 延迟
- **实际影响**：无 — 服务在 503 期间正常服务 API 请求（日志显示 200 状态码响应）
- **恢复**：约 3-4 分钟后自动恢复 UP
- **历史出现**：第 8、9、10、13、15、69 次（已沉淀为已知行为）

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 28 个 commit |
| 同步后状态 | ✅ 完全一致（a73181b0c） |
| 同步命令 | `bash scripts/sync-to-github.sh` |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 用户决定保留（第 13/14/15 次部署决定） |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` | 无临时配置 | ✅ |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚姿态 | 就绪（未需要） |
| 上一版本 releaseId | `dceae804e-api8080` |
| 上一版本 jar 路径 | `/opt/xiyu-bid/releases/dceae804e-api8080/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-a73181b0c-*.sql.gz` |
| 回滚命令 | 恢复旧 jar → `sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 执行（validate + DB 版本对比 + V1092 状态检查） |
| Mac HTTP_PROXY 502 绕过 | ✅ 所有 curl 加 `--noproxy '*'` |
| 同源构建（VITE_API_BASE_URL=） | ✅ 使用 |
| SYSTEMCTL_SUDO=true | ✅ 使用 |
| Kafka SDK readiness 延迟 | ✅ 识别并正确处理（不急于回滚） |
| GitHub 镜像同步 | ✅ 部署后同步 |
| 部署报告纪律 | ✅ 本次报告 |

## 风险提示

1. **Kafka SDK readiness 延迟**：remote-deploy.sh 健康检查超时但服务实际正常。建议后续优化 health check 策略（如检查 liveness 而非 overall health，或延长超时时间）
2. **V1092 迁移修改**：虽然 checksum 已 repair 且 validate 通过，但修改已应用迁移仍是风险操作 — 后续应尽量避免

## 部署确认清单

- [x] 环境门禁确认
- [x] 早操三连通过
- [x] 基线确认（HEAD = origin/main）
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功
- [x] 产物校验通过
- [x] 上传 + 部署完成
- [x] 健康检查 UP（手动验证）
- [x] Smoke 测试全通过
- [x] 前端一致性验证
- [x] GitHub 镜像同步
- [x] 配置清理检查
- [x] 部署报告生成
