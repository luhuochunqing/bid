# 第 42 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署编号 | 第 42 次 |
| 日期 | 2026-07-04 |
| Release ID | `481f2542c-api8080` |
| Commit | `481f2542c` |
| 目标服务器 | `172.16.38.78`（winbid-01） |
| 部署类型 | 纯前端优化（无数据库迁移） |

## 基线信息

| 项目 | 值 |
|---|---|
| 上一版本 | `3dbec41cd-api8080`（第 41 次部署） |
| 当前版本 | `481f2542c-api8080` |
| 增量 commit | 14 个 |
| 新增 Flyway 迁移 | 0 个 |

## PR 列表

| PR | 标题 |
|---|---|
| !1668 | refactor(knowledge): 统一知识库 UI 骨架样式 + 操作按钮位置 + 移除业绩副标题 |
| !1666 | refactor(project-detail): 优化项目详情页顶部布局紧凑度 |
| !1665 | CO-468 根因修复: 阶段切换后重新拉取 tasks（保证金任务不显示） |
| !1664 | CO-495: 项目详情评标中阶段删除「评标中」选项 |
| !1667 | fix(retrospective): 复盘提交后阶段停在 RETROSPECTIVE，由结项审核流程驱动到 CLOSED |
| !1663 | docs(release): 第 41 次生产部署报告 |

## 改动范围

### 前端

- **知识库模块**：统一 UI 骨架样式、操作按钮位置到标题行、查询重置按钮样式、消除双层 padding、移除业绩管理页面副标题
- **项目详情页**：优化顶部布局紧凑度
- **保证金任务**：阶段切换后重新拉取 tasks（修复不显示问题）
- **评标中阶段**：删除「评标中」选项
- **复盘流程**：复盘提交后阶段停在 RETROSPECTIVE，由结项审核流程驱动到 CLOSED

### 后端

- 无代码变更

### 数据库

- 无迁移变更

## Flyway 预检结果

| 步骤 | 状态 | 详情 |
|---|---|---|
| Step 1: validate | ✅ PASS | `VALIDATE OK - all checksums match` |
| Step 2: DB 版本对比 | ✅ PASS | DB 最新版本 V1133，与源码一致 |
| Step 3: remote-deploy validate | ✅ PASS | 自动执行通过 |

## 部署步骤

1. ✅ 早操三连：`source dev-env.sh` + `bash scripts/sync-env.sh .` + `bash scripts/check-git-wrapper.sh`
2. ✅ 确认基线：git status 干净，HEAD = origin/main
3. ✅ 服务器现状：健康检查 UP，当前版本 `3dbec41cd-api8080`
4. ✅ Flyway 预检 3 步全部通过
5. ✅ 本地打包：`RELEASE_ID="481f2542c-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
6. ✅ 产物校验：jar 内迁移文件无重复
7. ✅ 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true）
8. ✅ 健康检查：等待 79 次后通过
9. ✅ Smoke 测试：全部通过
10. ✅ GitHub 镜像同步：16 个 commit 已同步

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

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 16 个 commit |
| 同步后状态 | ✅ Gitee 与 GitHub 完全一致 |
| Gitee main | `481f2542c7326cd79f070ec5c56e35bb3161d030` |
| GitHub main | `481f2542c7326cd79f070ec5c56e35bb3161d030` |

## 配置清理检查

| 项目 | 值 | 状态 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | ⚠️ 保留（运维监控需要，用户已确认） |
| DEBUG/TRACE 配置 | 无 | ✅ |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚目标 | `3dbec41cd-api8080`（第 41 次部署） |
| 回滚方式 | `remote-deploy.sh` 指定旧 release ID |
| 数据库回滚 | 本次无迁移变更，无需 DB 回滚 |

## 经验沉淀应用情况

| 经验编号 | 应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 已应用，全部通过 |
| #3 生产前端同源构建 | ✅ 已应用，`VITE_API_BASE_URL=` 显式设空 |
| #4 Smoke 测试 admin 密码限制 | ✅ 已应用，使用 400/403/401 替代验证 |
| #5 GitHub 镜像同步 | ✅ 已应用，部署后自动同步 |
| #8 systemctl sudo 权限 | ✅ 已应用，`SYSTEMCTL_SUDO=true` |
| #15 Flyway 防护体系 | ✅ 已应用，所有门禁通过 |

## 风险提示

1. **本次无风险**：纯前端优化，无数据库迁移，无后端逻辑变更
2. **SHOW_DETAILS=always**：当前保留用于运维监控，如需收紧安全可改为 `never`

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
| GitHub 镜像同步 | ✅ |
| 配置清理检查 | ✅ |
| 部署报告生成 | ✅ |