# 部署报告：第 9 次发布

**发布 ID**: `47a0e2bba-api8080`
**部署时间**: 2026-06-27 19:30 CST (11:30 UTC) — 全栈部署 + 后端重启
**部署者**: Trae AI Agent（主工作区 trae，基于 origin/main）
**部署类型**: 手动 SSH 部署（remote-deploy.sh）

---

## 发布范围

| 项目 | 值 |
|---|---|
| **Commit** | `47a0e2bba` — `!1234 fix(frontend): 修复任务看板标书审核任务附件下载失败` |
| **源码变更** | 20 个新 commit（从第 8 次 `3bb444139` 起） |
| **前端构建** | Vite + Vue 3，生产同源构建（`baseURL=""`） |
| **后端构建** | Maven + Spring Boot 3.2.0，`mvn clean package -DskipTests` |
| **JAR 大小** | ~148 MB（`bid-poc-1.0.3.jar`） |
| **迁移数量** | 168 个（JAR 内），**无新增迁移** |

### 主要变更内容（20 个 commit）

#### 角色权限与越权修复（P0 重点）
1. **CO-363 角色越权缺陷修复**（!1233）：修复角色越权缺陷，新增结项一键导出文档功能（P0-P2）
2. **!1232 revert: 回退 PR #1230**：角色名 `/bidAdmin` 才是有效 code，`bid_admin` 导致注册失败
3. **!1235 docs**: 统一修正 CLAUDE.md 角色定义与 RoleProfileCatalog 保持一致

#### UI/组件统一
1. **!1229 feat(ui)**: 统一选人组件为 UserPicker，统一显示「姓名（工号）」
2. **6a43d8e73**: 投标辅助人员选人控件移除 role-filter，与投标负责人保持一致

#### Bug 修复
1. **!1234 fix(frontend)**: 修复任务看板标书审核任务附件下载失败
2. **91f429b91**: 修复 finish-task 锁文件匹配——支持 `<taskSlug>.yml` 命名约定
3. **844547837**: 清理 CO-361 已合入任务的 stale 锁文件 + 修复 finish 脚本锁匹配

#### 文档与沉淀
1. **!1231 docs(lessons)**: 沉淀第 21 条——readiness 延迟恢复 UP 经验
2. **ce42e40b6 / ebbb5aad5**: 第 8 次部署报告

---

## 部署执行摘要

### 1. 发布准备
- **工作区**: `trae` 主工作区，已同步到 `origin/main` 最新（`47a0e2bba`）
- **打包**: `scripts/release/package-release.sh` → `.release/xiyu-bid-release-47a0e2bba-api8080.tar.gz`
- **JAR 验证**: 含 168 个迁移文件，无重复版本（`rm -rf backend/target` 前置清理）
- **发布包大小**: 136 MB

### 2. Flyway 预检（关键步骤）

#### 2.1 源码无重复版本
```bash
# JAR 内迁移版本检查
unzip -l backend/target/bid-poc-1.0.3.jar | grep "migration-mysql/V" | sed 's/.*\(V[0-9]*\)__.*/\1/' | sort | uniq -d
# 输出为空 → 无重复
```

#### 2.2 remote-deploy.sh 内置 validate
- **结果**: ✅ VALIDATE OK - all checksums match
- **验证范围**: 169 个迁移（含 baseline）
- **执行时机**: jar 覆盖前，旧 jar 仍在运行（服务不中断）

### 3. 部署执行
- **前端部署**: 19:30 CST，`/srv/www/xiyu-bid/`（nginx root，同源构建）
- **JAR 部署**: 19:30 CST，`/opt/xiyu-bid/shared/backend/app.jar`
- **后端重启**: 19:31:45 CST（PID 11570）
- **健康检查恢复**: 约 4 分钟内恢复（Kafka SDK 初始化时序符合预期）

---

## 验证结果

### 1. 后端健康检查
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "MySQL", "validationQuery": "isValid()"}},
    "diskSpace": {"status": "UP"},
    "jwt": {"status": "UP", "details": {"algorithm": "HMAC-SHA256", "secretLength": 64, "strength": "STRONG"}},
    "livenessState": {"status": "UP"},
    "ping": {"status": "UP"},
    "readinessState": {"status": "UP"},
    "redis": {"status": "UP", "details": {"version": "6.2.19"}}
  }
}
```

### 2. Readiness 检查
- ✅ `200 UP`（db + readinessState 均 UP）

### 3. 前端页面可访问性
```
GET http://172.16.38.78:8080/ → 200, 851 bytes
- HTML 正常返回，标题"西域MRO投标管理平台"
- 引用 assets: /assets/index-lZowvZ30.js（同源构建，无 CORS 风险）
```

### 4. API 响应验证
```
POST /api/auth/login (错误密码) → 401
{"success":false,"code":401,"msg":"AUTHENTICATION_FAILED: 用户名或密码错误"}
```
- ✅ API 正常响应
- ✅ nginx 反向代理正常

### 5. Flyway 迁移状态
- ✅ 无新增迁移（0 个新迁移执行）
- ✅ 最新迁移 V1105 保持 success=1
- ✅ checksum 全部匹配（validate 通过）

### 6. 前端一致性验证
- ✅ Frontend matches release（`src="/assets/index-lZowvZ30.js"`）

### 7. 跳过的检查
- **完整登录 smoke**: 跳过（生产 admin 密码未知，遗留问题）
- **CRM smoke**: 跳过（依赖登录态）
- **run-prod-smoke.mjs**: 跳过（需要 PROD_SMOKE_USERNAME/PASSWORD 环境变量）

---

## 回滚姿态

| 项目 | 状态 |
|---|---|
| **回滚锚点** | ✅ 就绪（前一次部署 `3bb444139` 的 jar 和前端产物保留在 releases 目录） |
| **数据库备份** | ✅ 就绪（部署前备份保留，且本次无新增迁移，DB schema 未变） |
| **回滚命令** | `RELEASE_ARCHIVE=/opt/xiyu-bid/releases/3bb444139-api8080/... bash /tmp/remote-deploy.sh` |
| **是否需要回滚** | ❌ 不需要（当前部署运行正常） |

---

## 部署总结

### 成功指标
- ✅ Flyway validate 前置通过（169 个迁移，all checksums match）
- ✅ 后端进程启动成功（PID 11570）
- ✅ 所有健康检查组件 UP
- ✅ readiness UP
- ✅ 前端页面可访问（HTTP 200）
- ✅ API 接口正常响应
- ✅ nginx 反向代理正常
- ✅ 前端一致性验证通过

### 注意事项
1. **无新增数据库迁移**: 本次部署为纯代码变更，DB schema 未改动，回滚风险低
2. **readiness 恢复时序**: Kafka SDK 初始化可能导致 readiness 延迟几分钟 UP，属已知行为（第 8 次部署已记录）
3. **admin 密码未知**: 完整登录 smoke 无法执行，需确认生产 `ADMIN_PASSWORD`
4. **生产临时配置**: `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍在 `/etc/xiyu-bid/backend.env` 中（第 8 次部署遗留，待清理）

### 本次部署经验沉淀（可复用）
1. **部署执行位置**: 从主工作区 `trae` 执行（因 xiyu-bid-poc 基准区不在允许操作路径）
2. **分支策略**: 在 `agent/trae-init` 分支上 `git reset --hard origin/main`，无需切换到 main（main 被基准区占用）
3. **0 迁移部署**: Flyway validate 依然要跑，确保 checksum 一致，避免启动时 validateOnMigrate 失败
4. **前置清理 target**: `rm -rf backend/target` 必须在打包前执行，防止旧迁移文件残留

---

## 参考文档

- [生产发布流水线](./PRODUCTION_RELEASE_PIPELINE.md)
- [生产部署 Runbook](./LIVE_SERVER_DEPLOYMENT_RUNBOOK.md)
- [回滚手册](./ROLLBACK_RUNBOOK.md)
- [第 8 次部署报告](./deploy-report-2026-06-27-8th.md)（前一次部署，`3bb444139`）
- [经验教训](../lessons/lessons-learned.md)
