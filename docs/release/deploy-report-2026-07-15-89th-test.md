# 第 89 次测试环境部署报告

> **环境**：测试环境（test）
> **部署时间**：2026-07-15 12:14 CST
> **Release ID**：`fa11e2105-api8080`
> **操作人**：AI Agent（trae worktree）

## 一、部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 目标主机 | `winbid-01` (`172.16.38.78`) |
| Release ID | `fa11e2105-api8080` |
| 基线 commit | `fa11e21051d5b4dd7b42e2a970d78efea7950dd9` |
| 上一版本 | `59d3763cd-api8080`（2026-07-14 11:52 激活） |
| 增量 commit 数 | 8 |
| 新增迁移文件 | 无 |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |

## 二、基线信息

- **当前分支**：`agent/trae-init`（锚点分支）
- **HEAD = origin/main**：`fa11e2105`（CO-583 业绩管理列表分组与总截止日期聚合）
- **工作区状态**：干净
- **GitHub 镜像**：部署前落后 8 个 commit，部署后已同步

## 三、增量 PR 与改动范围

### 3.1 增量 commit（59d3763cd..fa11e2105）

| commit | PR | 类型 | 说明 |
|---|---|---|---|
| `fa11e2105` | !2082 | feat | CO-583 业绩管理列表分组与总截止日期聚合 |
| `bd5f18dda` | - | feat | CO-583 业绩管理列表分组与总截止日期聚合 |
| `13f23ccf6` | !2081 | docs | 新增 API 接口参考手册（130+ Controllers, 700+ 接口） |
| `d02eba0e3` | - | docs | 新增 API 接口参考手册（130+ Controllers, 700+ 接口） |
| `9f23b0029` | !2080 | docs | 第 87/88 次测试环境部署报告 |
| `572bda12a` | - | docs | 第 87/88 次测试环境部署报告 |
| `dc42133a1` | !2079 | docs | 第 11 次生产环境部署报告 |
| `b29bc48c4` | - | docs | 第 11 次生产环境部署报告 |

### 3.2 改动范围统计

| 模块 | 文件数 | 增/删行 |
|---|---|---|
| 后端 | 16 | +674 / -52 |
| 前端 | 12 | +406 / -32 |
| 迁移文件 | 0 | 0 |

### 3.3 主要功能变更

- **CO-583 业绩管理列表分组与总截止日期聚合**：业绩管理列表新增分组展示和总截止日期聚合功能
- **API 接口参考手册**：新增自动生成的 API 文档（130+ Controllers, 700+ 接口）
- **部署报告**：补齐第 87/88 次测试 + 第 11 次生产部署报告

## 四、Flyway 预检结果

### Step 1: 服务器 validate

```
VALIDATE OK - all checksums match
Successfully validated 228 migrations (execution time 00:00.092s)
```

### Step 2: DB 已应用版本（最近 5 条 success=1）

| version | description | installed_on |
|---|---|---|
| 1165 | add bid system admin role | 2026-07-11 16:43:52 |
| 1164 | lock oss user local passwords | 2026-07-10 21:13:25 |
| 1163 | add operator username to webhook delivery tasks | 2026-07-10 18:23:46 |
| 1162 | add margin permission to bid specialist | 2026-07-10 12:22:43 |
| 1161 | ca related platforms text | 2026-07-09 18:15:12 |

### Step 3: remote-deploy.sh 内置 validate

部署时自动执行，通过。

## 五、部署步骤

### 5.1 本地打包

```bash
RELEASE_ID="fa11e2105-api8080" \
VITE_API_BASE_URL= \
VITE_OBS_ENABLED=true \
COPYFILE_DISABLE=1 \
bash scripts/release/package-release.sh
```

- 构建结果：BUILD SUCCESS（26.7s）
- jar：`bid-platform-1.0.3.jar`
- 前端入口：`assets/index-BRObHQp5.js`

### 5.2 产物校验

- ✅ jar 内 Flyway 迁移版本无重复（227 个 V*.sql）
- ✅ OBS 直传已启用（Detail chunk .upload( 调用数=2）
- ✅ `release-metadata.json`：`obsEnabled=true`
- ✅ 前端 index.html 入口：`assets/index-BRObHQp5.js`

### 5.3 上传 + 部署

```bash
scp .release/xiyu-bid-release-fa11e2105-api8080.tar.gz \
    scripts/release/remote-deploy.sh \
    jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 '... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- DB 备份：`/opt/xiyu-bid/db-backups/winbid-fa11e2105-api8080-*.sql.gz`
- Flyway validate：通过
- jar 覆盖：`/opt/xiyu-bid/shared/backend/app.jar`
- 服务重启：`xiyu-bid-backend.service` active running

### 5.4 前端资源保留

```bash
sudo cp -rn /opt/xiyu-bid/releases/59d3763cd-api8080/frontend/assets/* \
            /srv/www/xiyu-bid/assets/
```

- assets 文件数：177 → 254（保留 77 个旧 hash 资源，防跨版本 404）

## 六、验证结果

### 6.1 健康检查

> **已知行为**：remote-deploy.sh 的 120 次健康检查窗口（4 分钟）刚好没等到 Kafka SDK readiness 恢复，返回 503。但服务实际已正常运行，业务接口可访问。手动复检后健康检查全部 UP。

| 端点 | 状态 | 说明 |
|---|---|---|
| `/actuator/health` | UP | 所有组件正常（db/redis/jwt/sidecar/aiProvider） |
| `/actuator/health/readiness` | UP | readinessState 正常 |
| `/actuator/health/liveness` | UP | livenessState 正常 |

### 6.2 Smoke 测试

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 | 200 | ✅ |
| `/actuator/health/readiness` | 200 | 200 | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| `GET /api/integration/crm/health`（无认证） | 401 | 401 | ✅ |
| `GET /` | 200 | 200 | ✅ |
| `GET /login` | 200 | 200 | ✅ |

前端入口：`assets/index-BRObHQp5.js`（与本地打包一致）✅

## 七、GitHub 镜像同步

- 部署前：GitHub main 落后 Gitee main 8 个 commit
- 部署后：执行 `bash scripts/sync-to-github.sh`
- 结果：✅ 两边 main 完全一致（`fa11e2105`）

## 八、回滚信息

- **回滚 jar**：`/opt/xiyu-bid/releases/59d3763cd-api8080/backend/app.jar`
- **回滚命令**：
  ```bash
  ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/59d3763cd-api8080/backend/app.jar \
    /opt/xiyu-bid/shared/backend/app.jar && \
    sudo systemctl restart xiyu-bid-backend'
  ```
- **DB 备份**：`/opt/xiyu-bid/db-backups/winbid-fa11e2105-api8080-*.sql.gz`
- **回滚状态**：未需要

## 九、经验沉淀应用情况

| 经验 | 本次应用 |
|---|---|
| Flyway 预检 3 步法 | ✅ 全部执行，validate 通过 |
| Kafka SDK readiness 延迟（第 8/9/10/13/15 次出现） | ✅ 已识别，手动复检后已恢复 UP |
| 生产前端同源构建（baseURL=""） | ✅ `VITE_API_BASE_URL=` 显式设空 |
| OBS 直传双保险 | ✅ `VITE_OBS_ENABLED=true` 显式传入 + 产物校验 |
| macOS `._*` 残留防护 | ✅ `COPYFILE_DISABLE=1` |
| 前端 hash 资源跨版本 404 | ✅ 从上一版本 release 目录 cp -rn 旧 assets |
| systemctl sudo 权限 | ✅ `SYSTEMCTL_SUDO=true` |
| Mac HTTP_PROXY 502 绕过 | ✅ curl 加 `--noproxy '*'` |

## 十、风险提示

1. **Kafka SDK readiness 延迟**：本次再现 remote-deploy.sh 健康检查窗口未等到 readiness 恢复的已知行为。服务实际正常运行，无需回滚。建议后续优化 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池。
2. **无新迁移**：本次无 Flyway 迁移变更，DB 风险极低。

## 十一、部署确认清单

- [x] 环境门禁通过（用户确认测试环境 172.16.38.78）
- [x] 早操三连执行（sync-env.sh + check-git-wrapper.sh）
- [x] 基线确认（HEAD = origin/main = fa11e2105）
- [x] 服务器现状检查（上一版本 59d3763cd，health UP）
- [x] Flyway 预检 3 步全部通过
- [x] 本地打包成功（OBS 直传启用）
- [x] 产物校验通过（jar 内无重复迁移，前端入口一致）
- [x] 上传 + 部署成功（jar 覆盖 + 服务重启）
- [x] 前端资源保留（77 个旧 hash 资源）
- [x] 健康检查 UP（容忍 Kafka SDK 延迟后恢复）
- [x] Smoke 测试全部通过（5 个接口 + 2 个前端页面）
- [x] GitHub 镜像同步完成
- [x] 配置清理检查（`SHOW_DETAILS=always` 为用户历史决定保留）
- [x] 部署报告生成
