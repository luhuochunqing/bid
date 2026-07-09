# 第 67 次部署报告 — 测试环境

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | test |
| 主机 | winbid-01 / 172.16.38.78 |
| Release ID | `7eafbe7f7-api8080` |
| 部署时间 | 2026-07-09 20:05:10 CST |
| 部署人 | trae agent |
| 特殊说明 | 第 66 次部署后的增量修复部署，无新增 Flyway 迁移 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支，仅同步不开发） |
| HEAD commit | `7eafbe7f7` |
| origin/main | `7eafbe7f7`（同步） |
| 上次部署 releaseId | `b1304462f-api8080`（第 66 次） |
| 增量 commit 数 | 11 |
| 增量 PR 数 | 5（!1953-!1958） |
| 新增 Flyway 迁移 | 无 |

## PR 列表

| PR | 标题 | 类型 |
|---|---|---|
| !1953 | docs(lessons): 追加 §49 CO-560 OSS 权限键全量盘点教训沉淀 | docs |
| !1954 | CO-490 fix(margin): CAST AS DATETIME + toLdt 解析 String，彻底修复日期丢失 | bugfix |
| !1956 | fix(config): 消除 SPRING_CONFIG_IMPORT 外部配置漂移，补全 jar 内 OSS 人员角色映射 | bugfix |
| !1957 | fix(permission): 展开白名单 OSS 用户的 'all' 权限键 | bugfix |
| !1958 | fix(obs): 自定义域名下载 URL 使用 CNAME 模式，匹配控制台分享链接格式 | bugfix |

## 改动范围

- **OSS 自定义域名 CNAME 模式**（PR !1958，本次核心修复）
  - 自定义域名下载 URL 切换为 CNAME 模式，匹配华为云 OBS 控制台分享链接格式
  - 修复 PR !1941 引入的 path-style/virtual-hosted-style 反复回退问题
- **配置漂移修复**（PR !1956）
  - 移除服务器 `backend.env` 中的 `SPRING_CONFIG_IMPORT` 外部配置覆盖
  - 补全 jar 内 `application.yml` 的 OSS 人员角色映射
  - 验证：部署后 `sudo grep SPRING_CONFIG_IMPORT /etc/xiyu-bid/backend.env` 无输出
- **保证金日期修复**（PR !1954 / CO-490）
  - `CAST AS DATETIME` + `toLdt` 解析 String，彻底修复日期丢失
- **OSS 权限键白名单**（PR !1957）
  - 展开白名单 OSS 用户的 `all` 权限键
- **教训沉淀**（PR !1953）
  - 追加 §49 CO-560 OSS 权限键全量盘点教训

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（224 migrations） |
| Step 2: DB 已应用版本 | V1161（最新，与源码一致，无 pending） |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK - all checksums match |

## 部署步骤

1. ✅ 环境门禁确认（test / 172.16.38.78）
2. ✅ 早操三连（dev-env.sh + sync-env.sh + check-git-wrapper.sh）
3. ✅ 基线确认（HEAD = origin/main = 7eafbe7f7，工作区干净）
4. ✅ 服务器现状检查（b1304462f 健康 UP，readinessState UP）
5. ✅ Flyway 预检 3 步法全部通过（无新增迁移）
6. ✅ 本地打包（RELEASE_ID=7eafbe7f7-api8080，VITE_API_BASE_URL= 同源构建）
7. ✅ 产物校验（jar 内 223 个 V*.sql 迁移文件无重复，前端入口 index-DOHcYYOZ.js）
8. ✅ 上传 + 部署（scp + remote-deploy.sh SYSTEMCTL_SUDO=true）
9. ✅ 健康检查通过（连续 3/3，总尝试 79 次，约 2 分 38 秒）
10. ✅ 前端一致性验证（`/assets/index-DOHcYYOZ.js` 与 release 一致）

## 验证结果

### 后端健康检查

| 组件 | 状态 |
|---|---|
| overall | UP |
| aiProvider | UP（provider=custom, model=qwen3.7-max） |
| db | UP（MySQL, isValid()） |
| diskSpace | UP（free 29.6GB / total 105.5GB） |
| jwt | UP（HMAC-SHA256, secretLength=64, STRONG） |
| livenessState | UP |
| readinessState | UP（无 Kafka SDK 延迟） |
| redis | UP（version 6.2.19） |
| sidecar | UP（http://localhost:8000, reachable） |

### Smoke 测试

| 接口 | 预期 | 实际 | 结果 |
|---|---|---|---|
| GET /actuator/health | 200 UP | 200 UP | ✅ |
| GET /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| POST /api/auth/login (空 body) | 400 | 400 | ✅ |
| GET /api/projects (无认证) | 403 | 403 | ✅ |
| GET /api/integration/crm/health | 401 | 401 | ✅ |

### 前端验证

| 检查项 | 结果 |
|---|---|
| 首页 / HTTP | 200 ✅ |
| 登录页 /login HTTP | 200 ✅ |
| 前端入口 | assets/index-DOHcYYOZ.js（与 release 一致）✅ |

### 部署记录

```json
{
  "releaseId": "7eafbe7f7-api8080",
  "activatedAt": "2026-07-09T12:05:10Z",
  "releaseDir": "/opt/xiyu-bid/releases/7eafbe7f7-api8080",
  "backendJarPath": "/opt/xiyu-bid/shared/backend/app.jar",
  "packageMetadata": {
    "releaseId": "7eafbe7f7-api8080",
    "apiBaseUrl": "",
    "jarName": "bid-platform-1.0.3.jar",
    "builtAt": "2026-07-09T12:04:10Z",
    "sentryEnabled": false
  }
}
```

## 配置清理检查

| 项目 | 结果 |
|---|---|
| `SPRING_CONFIG_IMPORT` | ✅ 已清理（PR !1956 修复配置漂移生效） |
| `SHOW_DETAILS=always` | ℹ️ 保留（用户已决定，非临时配置） |
| DEBUG/TRACE 临时配置 | ✅ 无 |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 11 commit |
| 同步后状态 | ✅ 完全一致（Gitee main = GitHub main = 7eafbe7f7） |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 releaseId | `b1304462f-api8080`（第 66 次） |
| 回滚 releaseDir | `/opt/xiyu-bid/releases/b1304462f-api8080` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-7eafbe7f7-api8080-*.sql.gz` |
| 回滚所需操作 | 1) 恢复旧 jar: `cp /opt/xiyu-bid/releases/b1304462f-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar`; 2) 恢复旧前端: `rsync -a /opt/xiyu-bid/releases/b1304462f-api8080/frontend/ /srv/www/xiyu-bid/`; 3) 重启服务: `sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 全部执行（无新增迁移，仍执行确认 DB 状态健康） |
| #3 生产前端同源构建（baseURL=""） | ✅ VITE_API_BASE_URL= 显式设空 |
| #6 临时调试配置清理 | ✅ 检查 backend.env，SPRING_CONFIG_IMPORT 已清理 |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #17 SentryAppender crash-loop | ✅ 未引入手动声明，sentryEnabled=false |

## 风险提示

- 本次部署无新增 Flyway 迁移，回滚不涉及 DB schema 回退（仅需恢复 jar + 前端）
- PR !1956 修复了服务器 `backend.env` 的 `SPRING_CONFIG_IMPORT` 配置漂移 — 这是重要修复，如果回滚到 b1304462f，该配置漂移会重新出现（不影响功能，但下一次部署需重新清理）
- OBS 自定义域名 CNAME 模式（PR !1958）依赖 DNS 解析 `widbid-obs.ehsy.com` 指向 OBS bucket，需确认 DNS 配置正确

## 部署确认清单

- [x] 环境门禁确认
- [x] 早操三连（sync-env + git-wrapper）
- [x] 基线确认（HEAD = origin/main）
- [x] 服务器现状检查
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功
- [x] 产物校验通过
- [x] 上传 + 部署成功
- [x] 健康检查通过
- [x] Smoke 测试通过
- [x] 前端一致性验证通过
- [x] 配置清理检查通过
- [x] GitHub 镜像同步
- [x] 部署报告生成
