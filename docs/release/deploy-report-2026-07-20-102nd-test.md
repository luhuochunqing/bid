# 第 102 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `ff93698a7` |
| 上一版本 | `d229bd6`（第 101 次，2026-07-20 15:38） |
| 部署时间 | 2026-07-20 18:28 CST |
| 增量 | 19 个 commit（CO-595 客户营收回归修复 + 企微工作台 state 入口 + 项目负责人工号显示 + OBS CORS 清单） |
| 新增迁移 | V1172（仅修改列 COMMENT，无 schema 结构变更） |
| 部署结果 | ✅ 成功（健康检查脚本 120 次失败 4 分钟，但服务实际在 18:32:51 已恢复 UP，属 Kafka SDK readiness 延迟已知行为） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用 |

## 部署原因

本次部署合入 19 个增量 commit，覆盖 4 项核心改动 + 1 项文档/教训沉淀：

1. **CO-595 客户营收字段映射错乱（!564 回归修复）**（PR !2156）：
   - V147 创建 `customer_revenue` 时 COMMENT 写"客户营收（万）"，但前端 BasicFieldsSection.vue 与项目列表 List.vue 列标签均为"客户营收（亿）"
   - 单位歧义导致 `d1994a3fa` 误把 `det.annualEcommerceAmount`（流水金额）当成客户营收赋给 `dto.revenue`
   - 本次修复：统一代码-数据库-前端三层单位为"亿"
   - 配套迁移 V1172 仅修改列 COMMENT，不动数据类型/精度/nullable

2. **企微工作台应用主页固定 state 入口**（PR !2158）：
   - 工作台 state 改为白名单校验，防止非法 state 注入
   - 支持企微工作台应用主页固定 state 入口（白名单内可直达指定路由）

3. **CC2026072071 项目负责人显示补工号**（PR !2154）：
   - 项目负责人显示格式改为"姓名 (工号)"，避免重名场景下识别困难

4. **OBS CORS 配置清单补全**（PR !2152）：
   - 补全 OBS CORS 配置清单
   - 新增 lessons §74（winbid-test 下载 preflight 失败案例）

5. **第 99/100/101 次部署报告 + lessons §74/§75**：
   - 文档归档与教训沉淀（CC2026072071 双层根因治理）

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae-init（锚点分支，ff-only 同步到 origin/main） |
| HEAD commit | `ff93698a7`（!2158 fix(integration): 支持企微工作台应用主页固定 state 入口） |
| origin/main | `ff93698a7`（与 HEAD 一致） |
| GitHub 镜像 | ⚠️ 落后 58 个 commit（部署前未同步，建议部署后执行 sync-to-github.sh） |
| git wrapper | ✅ 生效（scripts/git） |
| Flyway validate | ✅ 通过（234 migrations, all checksums match） |
| DB 已应用最新版本（部署前） | V1171（第 101 次部署应用） |
| 源码最新迁移版本 | V1172（新增 1 个） |

## 增量 commit 列表

```
ff93698a7 !2158 fix(integration): 支持企微工作台应用主页固定 state 入口
526d4bbee !2156 fix(revenue): 修复 !564 回归 - 客户营收字段映射错乱（CO-595）
68bfbaba7 fix(integration): 工作台入口 state 改为白名单校验
fc7f28aeb fix(integration): 支持企微工作台应用主页固定 state 入口
592466269 refactor(revenue-mapping): 修复设计 review 识别的 5 处弯路
d8fa2d989 docs(lessons): 追加 §74 教训 + 对齐客户营收字段单位注释为"亿"（!564 回归）
637e6cfd8 fix(initiation-stage): 修复详情页"客户营收"输入框加载时值丢失
cf5f4d9d9 fix(project-list): 修复客户营收列显示 MRO 流水金额的字段映射错误（!564 回归）
08a985231 !2157 docs(lessons): §75 CC2026072071 双层根因治理 + spec 037 T025 历史数据订正任务
835d39e44 docs(lessons): §75 CC2026072071 双层根因治理 + spec 037 T025 历史数据订正任务
efff5277f !2154 fix(project): CC2026072071 项目负责人显示补工号 (姓名 (工号)) 格式
25632c32f !2155 docs(release): 第 101 次测试环境部署报告
5f9ff1f3d !2152 docs(obs): 补全 OBS CORS 配置清单 + 新增 lessons §74（winbid-test 下载 preflight 失败）
44d69aad5 docs(release): 第 101 次测试环境部署报告
837854b6b !2151 docs(release): 第 99/100 次测试环境部署报告 Merge pull request !2151
8bfefb307 fix(project): CC2026072071 项目负责人显示补工号 (姓名 (工号)) 格式
f2d709930 docs(lessons): §74 修正 PR 编号引用 !2156 → !2152（实际创建的 PR 编号）
c942cb12e docs(obs): 补全 OBS CORS 配置清单 + 新增 lessons §74（winbid-test 下载 preflight 失败）
1af9458f9 docs(release): 第 99/100 次测试环境部署报告
```

## 改动范围

### CO-595 客户营收字段映射修复（!2156 + 配套）

**后端 Java 代码**：
- 客户营收字段映射逻辑修复（避免误用 `det.annualEcommerceAmount` 流水金额）
- 详情页"客户营收"输入框加载时值丢失修复
- 设计 review 识别的 5 处弯路 refactor

**数据库迁移**：
- `backend/src/main/resources/db/migration-mysql/V1172__align_customer_revenue_column_comment.sql`
  - 仅 `ALTER TABLE tender_evaluation_basics MODIFY COLUMN customer_revenue DECIMAL(15,2) DEFAULT NULL COMMENT '客户营收（亿）'`
  - 不改动数据类型、精度、nullable 等结构属性

**文档**：
- `docs/lessons/lessons-learned.md` 追加 §74 教训

### 企微工作台 state 入口白名单（!2158）

**后端 Java 代码**：
- 工作台入口 state 改为白名单校验
- 支持企微工作台应用主页固定 state 入口

### 项目负责人工号显示（!2154）

**后端 Java 代码**：
- 项目负责人显示格式改为"姓名 (工号)"

### OBS CORS 配置补全（!2152）

**文档**：
- 补全 OBS CORS 配置清单
- 新增 lessons §74（winbid-test 下载 preflight 失败案例）

## Flyway 预检 3 步法

| 步骤 | 结果 | 说明 |
|---|---|---|
| Step 1: 服务器 validate | ✅ | `VALIDATE OK - all checksums match`（234 migrations validated） |
| Step 2: DB 版本对比 | ✅ | DB 已应用 V1171，源码最新 V1172，1 个 pending 迁移（V1172 仅 COMMENT 变更，低风险） |
| Step 3: remote-deploy 内置 | ✅ | `remote-deploy.sh` 在激活新 jar 前自动 validate，通过 |

## 部署步骤

### Step 5: 本地打包

```bash
RELEASE_ID="ff93698a7" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

打包结果：
- ✅ 前端同源构建（`check:frontend-api-base` 通过）
- ✅ 后端打包（`mvn clean -DskipTests package`）成功，jar = `bid-platform-1.0.3.jar`（26.345s）
- ✅ jar 内 Flyway 迁移版本无重复
- ✅ OBS 直传已启用（Detail chunk `.upload(` 调用数=2）
- ✅ `release-metadata.json` 中 `obsEnabled=true`、`apiBaseUrl=""`（同源构建）

### Step 6: 产物校验

- Release archive: `.release/xiyu-bid-release-ff93698a7.tar.gz`（160M）
- Release directory: `.release/ff93698a7/`（175M）
- 前端入口 chunk: `assets/index-CPAc_moO.js` + `assets/index-Cmq0rLNS.css`
- jar 内 V1172 迁移文件存在（807 bytes）
- DB 当前已应用 V1171 → 部署后应用 V1172

### Step 7: 上传 + 部署

```bash
scp .release/xiyu-bid-release-ff93698a7.tar.gz scripts/release/remote-deploy.sh \
  jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'set -e; cd /opt/xiyu-bid/incoming && \
  source /etc/xiyu-bid/backend.env && \
  RELEASE_ARCHIVE=/opt/xiyu-bid/incoming/xiyu-bid-release-ff93698a7.tar.gz \
  APP_ROOT=/opt/xiyu-bid \
  FRONTEND_PUBLIC_DIR=/srv/www/xiyu-bid \
  BACKEND_SERVICE_NAME=xiyu-bid-backend \
  HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health \
  RELEASE_ID=ff93698a7 \
  FLYWAY_REPAIR_RUNNER=/opt/xiyu-bid/bin/flyway-repair-runner.sh \
  SYSTEMCTL_SUDO=true \
  DB_BACKUP_COMMAND="source /etc/xiyu-bid/backend.env && mysqldump ... | gzip > /opt/xiyu-bid/db-backups/winbid-ff93698a7-$(date +%Y%m%d%H%M%S).sql.gz" \
  bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

部署日志关键节点：
- `18:28:26` Flyway validate 通过（234 migrations, all checksums match）
- `18:28:29` 后端服务启动（PID 5518）
- `18:28:29 - 18:32:51` 健康检查脚本 120 次失败（约 4 分 22 秒）
- `18:31:55 - 18:32:47` 期间后端已开始处理业务请求（`/api/notifications/unread-count 200`，userId=2556 roleCode=/bidAdmin + userId=1 roleCode=admin）
- `18:32:49` `OrganizationEventSdkKafkaStarter` 开始 Kafka SDK bootstrap
- `18:32:49` `Kafka consumer started successfully`
- `18:32:51` 健康检查 status=200 ✅ 全面恢复 UP

### Step 7.5: 前端资源保留（防跨版本 404）

```bash
ssh jetty@172.16.38.78 'sudo cp -rn /opt/xiyu-bid/releases/d229bd6/frontend/assets/* \
  /srv/www/xiyu-bid/assets/ 2>/dev/null && echo "✅ 已保留上一版本(d229bd6) assets"'
```

- 当前 `/srv/www/xiyu-bid/assets/` 文件数：258
- 注意：`deployed-release.json` 此时已更新为 `ff93698a7`，需手动指定上一版本目录 `d229bd6`，不能从 json 读取

## 验证结果

### 健康检查（部署后 4 分 22 秒恢复）

```json
{
  "status": "UP",
  "components": {
    "aiProvider": {"status": "UP", "provider": "custom", "model": "qwen3.7-max"},
    "db": {"status": "UP", "database": "MySQL"},
    "diskSpace": {"status": "UP", "free": "14720118784"},
    "jwt": {"status": "UP", "strength": "STRONG", "secretLength": 64},
    "livenessState": {"status": "UP"},
    "ping": {"status": "UP"},
    "readinessState": {"status": "UP"},
    "redis": {"status": "UP", "version": "6.2.19"},
    "sidecar": {"status": "UP", "url": "http://localhost:8000"}
  }
}
```

### Smoke 测试（经 Nginx 8080 代理）

| # | 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | `GET /actuator/health` | 200 UP | HTTP 200 | ✅ |
| 2 | `GET /actuator/health/readiness` | 200 UP | HTTP 200 | ✅ |
| 3 | `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| 4 | `GET /api/projects`（无认证） | 403 | HTTP 403 | ✅ |
| 5 | `GET /api/integration/crm/health`（无认证） | 401 | HTTP 401 | ✅ |
| 6 | `GET /` | 200 | HTTP 200 | ✅ |
| 7 | `GET /login` | 200 | HTTP 200 | ✅ |
| 8 | 前端入口 chunk | 与 release 一致 | `assets/index-CPAc_moO.js` + `assets/index-Cmq0rLNS.css` | ✅ |

### 迁移应用验证

| 版本 | 描述 | success | installed_on |
|---|---|---|---|
| V1172 | align customer revenue column comment | 1 | 2026-07-20 18:28:37 |

列 COMMENT 实际存储为"客户营收（亿）"（utf8mb4，验证通过）。

## GitHub 镜像同步

| 检查项 | 结果 |
|---|---|
| 部署前 Gitee vs GitHub | GitHub 落后 58 个 commit |
| 部署后 Gitee vs GitHub | GitHub 落后 58 个 commit（本次部署未涉及代码 push，仅 jar 部署） |
| 同步操作 | ⚠️ 未同步，建议执行 `bash scripts/sync-to-github.sh` |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | ⚠️ 保留 | 历史决定保留（第 13/14/15 次用户决定），非临时配置 |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` 其他 | ✅ 无 | 无其他临时调试配置 |

## 经验沉淀应用情况

| 经验 | 是否应用 | 说明 |
|---|---|---|
| Flyway 预检 3 步法 | ✅ | 部署前主动执行 validate + DB 版本对比 |
| Kafka SDK readiness 延迟 | ✅ | 第 8/9/10/13/15/100/101 次后再次出现，4 分 22 秒恢复，属已知行为，未回滚 |
| 同源构建（`VITE_API_BASE_URL=`） | ✅ | 显式设空触发同源构建 |
| OBS 直传双保险（`VITE_OBS_ENABLED=true`） | ✅ | 显式传入 + 产物校验 `obsEnabled=true` |
| macOS `._*` 残留文件 | ✅ | `COPYFILE_DISABLE=1` 预防 |
| `SYSTEMCTL_SUDO=true` | ✅ | jetty 用户已配置 NOPASSWD sudo |
| 前端 hash 资源跨版本 404 | ✅ | 从上一版本 `d229bd6/frontend/assets/` 拷贝旧 hash 文件保留 24h |
| jar 内 Flyway 迁移版本无重复校验 | ✅ | package-release.sh 内置门禁通过 |

## 风险提示

1. **GitHub 镜像落后 58 个 commit**：建议尽快执行 `bash scripts/sync-to-github.sh` 同步镜像，保持双远程一致性
2. **Kafka SDK readiness 延迟**：本次部署再次出现（4 分 22 秒恢复），虽属已知行为但用户体验不佳。建议后续考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行，避免阻塞主线程
3. **V1172 列 COMMENT 单位治理**：本次仅修改 DB COMMENT，但前端/代码/DB 三层单位对齐仍需在后续迭代中持续观察，避免再次出现 `annualEcommerceAmount`（流水金额）误赋给 `customerRevenue`（客户营收）的语义错配
4. **本次部署在锚点分支 `agent/trae-init` 上打包**：未创建任务分支，因为部署不涉及代码改动，仅打包已合入 main 的 commit。如需提交部署报告，建议创建专门的任务分支提 PR

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操同步（sync-env.sh + rebase origin/main）
- [x] Flyway 预检 3 步法全部通过
- [x] 本地打包 + 产物校验（OBS 启用、同源构建、无重复迁移）
- [x] 上传 + remote-deploy.sh 部署（含 DB 备份）
- [x] 前端资源保留（d229bd6 assets 拷贝）
- [x] 健康检查通过（4 分 22 秒恢复，readinessState UP）
- [x] Smoke 测试 8 项全绿
- [x] 迁移应用验证（V1172 已应用，COMMENT utf8mb4 存储正确）
- [x] 配置清理检查（仅保留用户决定项）
- [x] 部署报告生成
- [ ] GitHub 镜像同步（待执行）
- [ ] 部署报告提 PR 合入 main（待执行）

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚姿态 | 未需要 |
| 上一版本 release | `/opt/xiyu-bid/releases/d229bd6/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/d229bd6/backend/app.jar` |
| 上一版本前端 | `/opt/xiyu-bid/releases/d229bd6/frontend/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-ff93698a7-<timestamp>.sql.gz` |
| 回滚命令 | `scp /opt/xiyu-bid/releases/d229bd6/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| 注意事项 | V1172 仅修改列 COMMENT，回滚 jar 后 DB COMMENT 仍为"亿"（无副作用，因为本次 COMMENT 仅为单位注释对齐） |
