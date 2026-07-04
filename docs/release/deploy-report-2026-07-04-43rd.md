# 第 43 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署编号 | 第 43 次 |
| 日期 | 2026-07-04 |
| Release ID | `860e7aea8-api8080` |
| Commit | `860e7aea8` |
| 目标服务器 | `172.16.38.78`（winbid-01） |
| 部署类型 | 全量部署（前端 + 后端 + 数据库迁移） |

## 基线信息

| 项目 | 值 |
|---|---|
| 上一版本 | `481f2542c-api8080`（第 42 次部署） |
| 当前版本 | `860e7aea8-api8080` |
| 增量 commit | 12 个 |
| 新增 Flyway 迁移 | 1 个（V1134） |
| 新增 Rollback 脚本 | 1 个（U1134） |

## PR 列表

| PR | 标题 |
|---|---|
| !1674 | fix(tender): 删除标讯前检查关联项目，防止项目 tender_id 悬空 |
| !1671 | fix(permission): CO-499 投标文件上传权限收紧到 admin_lead + primaryLeadId 匹配 |
| !1670 | fix(ui): CO-498 项目立项客户信息表格与标评详情评估表 UI 一致 |
| !1673 | fix(ui): CO-497 复盘提交后确保跳转到结项阶段（try-catch-finally） |
| !1672 | fix(personnel): CO-469 第五轮，允许批量操作日志 personnel_id 为 NULL |
| !1669 | fix(project-detail): 恢复项目时间线到项目信息卡片下方 |

## 改动范围

### 前端

- **标讯管理**：删除标讯前检查关联项目，防止项目 tender_id 悬空
- **投标文件上传**：权限收紧到 admin_lead + primaryLeadId 匹配（CO-499）
- **项目立项页**：客户信息表格与标评详情评估表 UI 一致（CO-498）
- **复盘流程**：提交后确保跳转到结项阶段（try-catch-finally，CO-497）
- **项目详情页**：恢复项目时间线到项目信息卡片下方

### 后端

- **批量操作日志**：允许 personnel_id 为 NULL（CO-469 第五轮）
- **标讯删除**：删除前检查关联项目，防止 tender_id 悬空
- **投标文件上传**：权限校验收紧到 admin_lead + primaryLeadId 匹配

### 数据库

- **V1134__personnel_operation_log_allow_null_personnel_id.sql**：允许批量操作日志 personnel_id 为 NULL
- **U1134__personnel_operation_log_allow_null_personnel_id.sql**：对应回滚脚本

## Flyway 预检结果

| 步骤 | 状态 | 详情 |
|---|---|---|
| Step 1: validate | ✅ PASS | `VALIDATE OK - all checksums match` |
| Step 2: DB 版本对比 | ✅ PASS | DB 最新版本 V1133，源码新增 V1134 |
| Step 3: remote-deploy validate | ✅ PASS | 自动执行通过 |
| Step 4: 迁移应用验证 | ✅ PASS | V1134 已应用，success=1 |

## 部署步骤

1. ✅ 早操三连：`source dev-env.sh` + `bash scripts/sync-env.sh .` + `bash scripts/check-git-wrapper.sh`
2. ✅ 确认基线：git status 干净，HEAD = origin/main = `860e7aea8`
3. ✅ 服务器现状：健康检查 UP，当前版本 `481f2542c-api8080`
4. ✅ Flyway 预检 3 步全部通过
5. ✅ 本地打包：`RELEASE_ID="860e7aea8-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
6. ✅ 产物校验：jar 内迁移文件无重复，V1134 存在
7. ✅ 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true）
8. ✅ 健康检查：等待 89 次后通过
9. ✅ Smoke 测试：全部通过
10. ✅ GitHub 镜像同步：12 个 commit 已同步
11. ✅ 配置清理检查：SHOW_DETAILS=always 为历史保留项

## 验证结果

### 健康检查

```json
{
  "status": "UP",
  "components": {
    "aiProvider": "UP",
    "db": "UP",
    "diskSpace": "UP",
    "jwt": "UP",
    "livenessState": "UP",
    "ping": "UP",
    "readinessState": "UP",
    "redis": "UP",
    "sidecar": "UP"
  }
}
```

### Smoke 测试

| 测试项 | 预期 | 实际 | 状态 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `/api/auth/login`（空密码） | 400 | 400 | ✅ |
| `/api/projects`（未认证） | 403 | 403 | ✅ |
| `/api/integration/crm/health`（未认证） | 401 | 401 | ✅ |
| 首页 `/` | 200 | 200 | ✅ |
| 登录页 `/login` | 200 | 200 | ✅ |
| 前端 JS 指纹 | `assets/index-CmyeK3MX.js` | `assets/index-CmyeK3MX.js` | ✅ |

### 迁移应用验证

| 版本 | 描述 | 状态 | 应用时间 |
|---|---|---|---|
| V1134 | personnel operation log allow null personnel id | ✅ success=1 | 2026-07-04 15:51:45 |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 12 个 commit |
| 同步后状态 | ✅ Gitee 与 GitHub 完全一致 |
| Gitee main | `860e7aea82f9147df85d72a609775e669305b0e4` |
| GitHub main | `860e7aea82f9147df85d72a609775e669305b0e4` |

## 配置清理检查

| 项目 | 值 | 状态 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | ⚠️ 保留（运维监控需要，用户已确认） |
| DEBUG/TRACE 配置 | 无 | ✅ |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚目标 | `481f2542c-api8080`（第 42 次部署） |
| 回滚方式 | `remote-deploy.sh` 指定旧 release ID |
| 数据库回滚 | 执行 U1134 回滚脚本，恢复 personnel_id 非空约束 |
| 回滚状态 | 已就绪，未执行 |

## 经验沉淀应用情况

| 经验编号 | 应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 已应用，全部通过 |
| #2 Kafka SDK readiness 延迟 | ✅ 已观察，本次健康检查 89 次后通过，属正常范围 |
| #3 生产前端同源构建 | ✅ 已应用，`VITE_API_BASE_URL=` 显式设空 |
| #4 Smoke 测试 admin 密码限制 | ✅ 已应用，使用 400/403/401 替代验证 |
| #5 GitHub 镜像同步 | ✅ 已应用，部署后自动同步 |
| #7 幂等迁移设计 | ✅ V1134 为 ALTER TABLE MODIFY COLUMN 非幂等，依赖 Flyway 版本号机制 |
| #8 systemctl sudo 权限 | ✅ 已应用，`SYSTEMCTL_SUDO=true` |
| #10 破坏性 schema 变更 | ⚠️ V1134 修改列约束，已确认业务允许 |
| #15 Flyway 防护体系 | ✅ 已应用，所有门禁通过 |
| #16 Mac HTTP_PROXY 502 | ✅ 已应用，curl 统一加 `--noproxy '*'` |
| #17 SentryAppender crash-loop | ✅ 已确认 logback-spring.xml 无手动 SentryAppender 声明 |

## 风险提示

1. **V1134 迁移风险**：修改 `personnel_operation_log.personnel_id` 列约束为允许 NULL，属于 schema 变更。已确认业务允许批量操作日志 personnel_id 为 NULL，且已准备 U1134 回滚脚本。
2. **权限变更风险**：投标文件上传权限收紧到 admin_lead + primaryLeadId 匹配，需关注用户反馈。
3. **SHOW_DETAILS=always**：当前保留用于运维监控，如需收紧安全可改为 `never`。

## 部署确认清单

| 检查项 | 状态 |
|---|---|
| 早操三连完成 | ✅ |
| Git 基线确认 | ✅ |
| 服务器健康检查 | ✅ |
| Flyway 预检通过 | ✅ |
| 本地打包成功 | ✅ |
| 产物校验通过 | ✅ |
| 上传部署成功 | ✅ |
| 后端健康检查 | ✅ |
| Smoke 测试通过 | ✅ |
| 迁移应用验证 | ✅ |
| GitHub 镜像同步 | ✅ |
| 配置清理检查 | ✅ |
| 部署报告生成 | ✅ |
