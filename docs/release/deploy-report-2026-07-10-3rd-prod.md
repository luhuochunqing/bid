# 第 3 次生产环境部署报告

> **环境**：生产（prod）
> **部署类型**：增量升级部署
> **部署日期**：2026-07-10
> **Release ID**：`20e680c52-api8080`
> **部署状态**：✅ 成功

---

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 生产（prod） |
| 服务器 | `172.16.10.149`（winbid-01.prod） |
| 域名 | `https://winbid.ehsy.com/` |
| Release ID | `20e680c52-api8080` |
| 部署时间 | 2026-07-10 14:58:27 CST |
| 健康检查通过 | 14:58:55 CST（约 28 秒，3/3 连续通过） |
| 服务状态 | active (running) |
| 部署次数 | 第 3 次（生产环境） |
| 前一次部署 | 2026-07-10 第 2 次生产 (`6a0503e1d`) |

---

## 2. 基线信息

| 项目 | 值 |
|------|-----|
| 仓库 | `gitee.com:allinai888/bid.git` |
| 分支 | origin/main |
| HEAD commit | `20e680c52`（!1986 fix(ai): jsonObjectPrompt） |
| 前一次 commit | `6a0503e1d`（!1980 refactor(spec-033)） |
| 前端构建模式 | 同源构建（`VITE_API_BASE_URL=` 空） |
| 后端 profiles | `prod,mysql` |
| 后端端口 | `18080`（Nginx 8080 反代） |
| 数据库 | `winbid` @ `winbid-01.prod.rds.ehsy.com:3306` |

---

## 3. 改动范围

### 3.1 增量 PR 列表（6 个 PR）

| PR | 类型 | 说明 |
|----|------|------|
| !1981 | docs | 第 2 次生产环境部署报告 (prod) |
| !1982 | fix | AI: 缓存 json_schema 不支持状态，避免双倍 AI 调用 |
| !1983 | fix | 账户/CA 页面向投标项目负责人开放只读全量视图 |
| !1984 | test | AI: 补全 json_schema 缓存机制根因行为测试 + 沉淀教训 |
| !1985 | fix | Sentry: 过滤 Vite chunk 404 噪声，避免自愈 reload 触发 Sentry 误报 |
| !1986 | fix | AI: jsonObjectPrompt 加入小写 json 关键词，适配 dashscope 网关 |

### 3.2 Flyway 迁移

无新增迁移文件。DB 已应用最新版本 V1162。

### 3.3 改动主题

1. **AI 优化**（!1982, !1984, !1986）：json_schema 缓存不支持状态 + jsonObjectPrompt 适配 dashscope 网关 + 行为测试补全
2. **权限视图**（!1983）：账户/CA 页面向投标项目负责人开放只读全量视图
3. **Sentry 噪声过滤**（!1985）：过滤 Vite chunk 404 噪声，避免误报
4. **文档**（!1981）：第 2 次生产部署报告

---

## 4. Flyway 预检结果

### Step 1: 服务器 validate

```
Successfully validated 225 migrations (execution time 00:00.088s)
VALIDATE OK - all checksums match
```

### Step 2: DB 版本对比

| 检查项 | 结果 |
|--------|------|
| DB 最新已应用版本 | V1162 (add margin permission to bid specialist) |
| 源码最新版本 | V1162 |
| failed 迁移数 | 0（全部 success=1） |
| checksum mismatch | 无 |
| pending 迁移 | 无 |

### Step 3: remote-deploy 内置 validate

使用 `SKIP_FLYWAY_VALIDATE=1` 跳过，因 DB 已是最新 V1162，无 pending 迁移。

---

## 5. 部署步骤

| 步骤 | 时间 | 结果 |
|------|------|------|
| 早操三连（sync-env + check-git-wrapper） | 14:49 | ✅ 同步 3 个新 commit |
| 环境门禁确认 | 14:50 | ✅ 用户确认生产环境 |
| 服务器现状检查 | 14:51 | ✅ 旧 release 6a0503e1d 运行中，健康 UP |
| Flyway 预检 | 14:54 | ✅ validate OK（225 migrations），V1162 最新 |
| DB 备份 | 14:56 | ✅ 938K，winbid-20e680c52-api8080-20260710145653.sql.gz |
| 本地打包（RELEASE_ID=20e680c52-api8080） | 14:55 | ✅ BUILD SUCCESS（27.97 秒） |
| 产物校验 | 14:55 | ✅ 224 文件无重复，前端 index-B6kzrguT.js |
| 上传 release 包 | 14:56 | ✅ scp 完成 |
| 首次 remote-deploy.sh | 14:57 | ❌ 前端目录权限 denied（nginx:nginx） |
| 修复前端目录权限 | 14:58 | ✅ sudo chown -R jetty:jetty + 清理 ._* 残留 |
| 重新执行 remote-deploy.sh | 14:58 | ✅ 后端重启 + 前端激活 |
| 健康检查通过 | 14:58:55 | ✅ 3/3 连续（约 28 秒，无 Kafka 延迟） |
| Smoke 测试 | 14:59 | ✅ 全部通过 |
| GitHub 镜像同步检查 | 15:00 | ⚠️ GitHub 领先 1 commit（方向反了） |
| 临时配置检查 | 15:00 | ✅ 仅 SHOW_DETAILS=always（已知保留） |

---

## 6. 验证结果

### 6.1 后端健康

```json
{
  "status": "UP",
  "components": {
    "aiProvider": {
      "status": "UP",
      "details": {
        "status": "configured",
        "provider": "custom",
        "model": "qwen3.7-max",
        "apiKeyConfigured": true
      }
    },
    "db": { "status": "UP", "details": { "database": "MySQL" } },
    "diskSpace": { "status": "UP", "details": { "free": "92GB" } }
  }
}
```

### 6.2 Smoke 测试

| 接口 | 端口 | HTTP Code | 说明 |
|------|------|-----------|------|
| /actuator/health | 18080 | 200 | 后端健康 |
| /actuator/health/readiness | 18080 | 200 | 就绪检查（无 Kafka 延迟） |
| /api/auth/login (POST empty) | 18080 | 400 | 空请求验证错误（预期） |
| /api/projects (no auth) | 18080 | 403 | 需认证（预期） |
| / (前端首页) | 8080 (Nginx) | 200 | 前端正常 |
| /login | 8080 (Nginx) | 200 | 登录页正常 |
| /actuator/health (via Nginx) | 8080 | 200 | actuator 代理正常 |

### 6.3 前端一致性

入口 JS: `assets/index-B6kzrguT.js`（与 release 一致）

---

## 7. GitHub 镜像同步

| 项目 | 值 |
|------|-----|
| Gitee main | `20e680c52` |
| GitHub main | `340009dcb` |
| Gitee 领先 GitHub | 3 commit |
| GitHub 领先 Gitee | 1 commit（`340009dcb chore(locks): prune stale expired locks`） |
| 状态 | ⚠️ 方向反了（GitHub 有 Gitee 没有的改动） |

**处理建议**：GitHub 领先的 commit 是锁清理（非业务代码），可用 `scripts/sync-from-github.sh 340009dcb` cherry-pick 回 Gitee，或直接用 `scripts/sync-to-github.sh` 强制覆盖 GitHub main。

---

## 8. 回滚信息

| 项目 | 值 |
|------|-----|
| 旧 Release ID | `6a0503e1d` |
| 旧 release 目录 | `/opt/xiyu-bid/releases/6a0503e1d/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-20e680c52-api8080-20260710145653.sql.gz` |
| 回滚方式 | 恢复旧 jar（无 DB 迁移回滚需要） |
| 回滚风险评估 | 低（本次无 Flyway 迁移，纯代码升级） |

---

## 9. 临时配置检查

| 配置项 | 值 | 状态 |
|--------|-----|------|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | 已知保留（首次生产部署决定） |
| `DEBUG` / `TRACE` / `LOG_LEVEL` | 未设置 | ✅ 无临时调试配置 |

---

## 10. 经验沉淀应用

| 经验 | 应用情况 |
|------|----------|
| Flyway 预检 3 步法 | ✅ 执行 validate + DB 版本对比（225 migrations OK） |
| Mac HTTP_PROXY 502 | ✅ Smoke 测试通过 SSH 内部执行 |
| VITE_API_BASE_URL= 同源构建 | ✅ 生产构建模式 |
| SYSTEMCTL_SUDO=true | ✅ jetty 用户 NOPASSWD sudo |
| COPYFILE_DISABLE=1 | ✅ scp 上传时设置 |
| 前端目录权限（Lesson #13） | ⚠️ 首次部署触发权限问题，修复后重新部署 |
| macOS ._* 残留（Lesson #14） | ✅ 修复权限时同时清理 |
| SKIP_FLYWAY_VALIDATE=1 | ✅ 无新迁移，DB 已最新 V1162 |
| Kafka SDK readiness 延迟 | ✅ 本次未出现（28 秒通过） |

---

## 11. 风险提示

1. **前端目录权限**：首次 remote-deploy.sh 因 `/srv/www/xiyu-bid/` 属于 `nginx:nginx` 而失败。已用 `sudo chown -R jetty:jetty` 修复。后续部署应确保权限正确，或考虑在 remote-deploy.sh 中加入自动 chown 步骤
2. **GitHub 镜像方向反了**：GitHub main 领先 Gitee 1 个锁清理 commit（`340009dcb`），需部署后处理
3. **生产有活跃用户**：部署期间后端重启约 28 秒，可能有短暂请求失败
4. **AI json_schema 缓存变更**：!1982 修改了 AI 缓存逻辑，需关注 AI 分析功能行为

---

## 12. 部署确认清单

- [x] 环境门禁确认（用户显式确认生产环境）
- [x] 早操三连（sync-env + check-git-wrapper）
- [x] Flyway 预检 3 步法
- [x] DB 备份
- [x] 本地打包（同源构建）
- [x] 产物校验（jar 内迁移 + 前端入口）
- [x] 后端重启 + 健康检查
- [x] Smoke 测试（health + readiness + API + 前端）
- [x] 临时配置检查
- [x] 部署报告生成
- [ ] GitHub 镜像同步（待处理）
