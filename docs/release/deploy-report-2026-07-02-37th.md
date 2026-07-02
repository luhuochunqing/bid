# 第 37 次生产部署报告

> **部署状态**：✅ 部署成功
> **生产服务状态**：🟢 UP
> **特殊说明**：本次部署包含 41 个增量 commit（PR !1557-!1573），涵盖权限双轨制技术债消除、CO-448 保证金持久化、CA/资质/业绩/附件等多项业务修复，无新增 Flyway 迁移

## 部署概览

| 项目 | 值 |
|---|---|
| 部署编号 | 第 37 次 |
| 日期 | 2026-07-02 |
| Release ID | `665dd3abb-api8080` |
| commit | `665dd3abb`（PR !1573 附件下载报错） |
| 上一部署 Release | `03b6e2725-api8080`（2026-07-02T12:46:46Z 部署） |
| 增量 commit | 41 个（PR !1557-!1573） |
| 新增 Flyway 迁移 | 无 |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 执行人 | trae agent |
| 结果 | ✅ 部署成功，Smoke 全绿 |

## 基线信息

- **早操三连**：source dev-env.sh + sync-env.sh + check-git-wrapper.sh ✅
- **锚点分支**：`agent/trae-init`（ff-only 同步，非开发行为）
- **基线**：HEAD = `665dd3abb` = origin/main（部署时）
- **GitHub 镜像**：部署前落后 Gitee 29 个 commit；部署后 origin/main 已前进到 `15ef98528`（PR !1574），GitHub 已同步到 `15ef98528`
- **本地门禁**：7/7 通过（core.hooksPath、pre-commit/pre-push hook、pre-push-gate.sh 14 道门禁、git wrapper、agent-locks）

## PR 列表（!1557-!1573，共 17 个 PR）

| PR | 说明 | 分类 |
|---|---|---|
| !1557 | 消除 @PreAuthorize hasAnyRole 双轨制技术债（MVP：P1 止血 + P2 守卫） | 架构整改 |
| !1558 | fix(CO-448): 保证金任务提交审核时持久化 4 个执行人填写字段 | 功能修复 |
| !1559 | docs(lessons): 企微通知 SKIPPED 根因分析 + Spring Boot 松散绑定陷阱 | 文档 |
| !1560 | fix(qualification): 修复资质证书状态筛选无效问题 | 功能修复 |
| !1561 | fix(resource): 修复平台账户借用记录 tab 死代码——接入真实 API 数据 | 功能修复 |
| !1562 | fix: 账号借用提交失败导致页面崩溃（ErrorBoundary 误触发） | 功能修复 |
| !1563 | fix(brand-auth): 修复批量导出 Excel 内容为空 — findByStatus(null) 误用 | 功能修复 |
| !1564 | fix(CA): CO-477 修复 CA 状态显示陈旧（到期2天仍显示即将到期） | 功能修复 |
| !1565 | feat(bidding): 标讯中心项目类型设为必填并显示红色星号 | 功能改进 |
| !1566 | P3 批次 task/knowledge 收尾（EXPECTED 201→192，消除 9 处） | 架构整改 |
| !1567 | feat(resource): CA 证书管理增加批量导出功能 | 功能改进 |
| !1568 | fix(margin): 保证金看板状态筛选对齐 label() 语义 | 功能修复 |
| !1569 | docs(lessons): 沉淀 §33 — Spring Data JPA 派生查询方法传 null 不会变成无过滤条件 | 文档 |
| !1570 | fix(test): 修复 21 个测试失败 — 涵盖 403/503/BidResult/TenderCommand 等 | 测试修复 |
| !1571 | fix(project): 修复投标项目列表筛选客户类型(央企)筛不出数据的问题 | 功能修复 |
| !1572 | feat: 业绩管理批量导入优化 | 功能改进 |
| !1573 | fix: 附件下载报错 | 功能修复 |

## 改动范围

- **后端**：
  - 架构整改：消除 @PreAuthorize hasAnyRole 双轨制技术债（P1 删除白名单 + P2 ArchitectureTest 守卫）、P3 批次 task/knowledge 权限收尾（EXPECTED 201→192）
  - 功能修复：CO-448 保证金持久化、资质证书状态筛选、平台账户借用记录、账号借用 ErrorBoundary、批量导出 Excel 空、CA 状态陈旧、保证金看板筛选、项目列表客户类型筛选、附件下载
  - 测试修复：21 个测试失败（403/503/BidResult/TenderCommand 等）
- **前端**：
  - 功能改进：标讯中心项目类型必填红星、CA 证书批量导出、业绩管理批量导入优化
  - 功能修复：账号借用页面崩溃、附件下载报错
- **Flyway 迁移**：无新增
- **文档**：企微通知 SKIPPED 根因分析、Spring Data JPA 派生查询 null 陷阱

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（192 个迁移） |
| Step 2: DB 版本对比 | ✅ DB 最新 V1128（2026-07-02 20:46:54 已应用），源码无新迁移 |
| Step 3: remote-deploy 内置 validate | ✅ VALIDATE OK - all checksums match |

## 部署步骤

### 1. 本地打包 ✅
- `RELEASE_ID="665dd3abb-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
- BUILD SUCCESS（36.413s）
- jar 内 191 个 Flyway 迁移文件，无重复版本
- 前端同源构建：`apiBaseUrl: ""` ✅
- 前端入口：`assets/index-IcmG7leG.js`

### 2. 上传 + 部署 ✅
- scp 上传成功（release archive + remote-deploy.sh）
- remote-deploy.sh 执行：
  - DB 备份完成 ✅
  - Flyway validate 通过 ✅（192 migrations）
  - JAR 覆盖成功 ✅
  - 前端切换成功 ✅
  - 服务重启成功（23:06:27 CST）✅
  - 健康检查通过（89 次重试，consecutive 3/3）✅
  - 前端一致性验证通过（`src="/assets/index-IcmG7leG.js"`）✅

### 3. 健康检查 ✅
- remote-deploy 内置健康检查：89 次重试后通过（约 3 分钟，Kafka SDK readiness 延迟属已知行为）
- consecutive 3/3，服务 active/running

## 验证结果

### 后端健康检查
| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ 200 UP |
| `/actuator/health/readiness` | ✅ 200 UP |
| readinessState | ✅ UP |
| livenessState | ✅ UP |
| aiProvider | ✅ UP (doubao, deepseek-v3-2-251201) |
| db | ✅ UP (MySQL) |
| redis | ✅ UP (6.2.19) |
| jwt | ✅ UP (HMAC-SHA256, STRONG) |
| sidecar | ✅ UP (reachable) |

### Smoke 测试（API 路由验证）
| 接口 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 | 200 | ✅ |
| `GET /actuator/health/readiness` | 200 | 200 | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 | ✅ |
| `GET /api/projects`（无认证） | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |

### 前端验证
| 检查项 | 结果 |
|---|---|
| `GET /` | ✅ 200 |
| `GET /login` | ✅ 200 |
| 前端 assets | `assets/index-IcmG7leG.js`（与 release 一致） |

### Flyway 迁移应用验证
| 检查项 | 结果 |
|---|---|
| DB 最新版本 | V1128（2026-07-02 20:46:54 已应用，无新迁移） |
| 源码最新版本 | V1128（与 DB 一致） |
| 新增迁移应用 | 无（本次部署无新迁移） |

## GitHub 镜像同步

- **部署前状态**：GitHub 镜像落后 Gitee 29 个 commit
- **部署后状态**：origin/main 已前进到 `15ef98528`（PR !1574 合并），GitHub 已同步到 `15ef98528`
- **同步检查**：`git log --oneline github/main..origin/main` = 0，两边完全一致
- **本次部署基于 `665dd3abb`**，PR !1574（UserPicker value-key 修复）将在下次部署包含

## 临时配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 历史决定（第 13/14/15 次连续三次保留，运维监控需要） |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` | 无新增 | 本次部署未引入临时配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚策略 | 切换到上一 release 目录 + 旧 jar 重启 |
| 上一 Release ID | `03b6e2725-api8080` |
| 上一 release 目录 | `/opt/xiyu-bid/releases/03b6e2725-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-665dd3abb-api8080-*.sql.gz` |
| 回滚命令 | `RELEASE_ID=03b6e2725-api8080 bash /opt/xiyu-bid/releases/03b6e2725-api8080/rollback.sh`（如存在） |
| 回滚需求 | 未需要（部署成功，Smoke 全绿） |

## 经验沉淀应用情况

本次部署应用了以下历史经验：
1. ✅ **Flyway 预检 3 步法**（第 1 条）—— 预检全通过，192 migrations validate OK
2. ✅ **Readiness 延迟恢复**（第 2 条）—— 健康检查 89 次重试（约 3 分钟），Kafka SDK 延迟属已知行为，自恢复
3. ✅ **生产前端同源构建**（第 3 条）—— `VITE_API_BASE_URL=` 显式设空，`apiBaseUrl: ""`
4. ✅ **Smoke 测试限制**（第 4 条）—— admin 密码未知，用 400/403/401 替代验证
5. ✅ **GitHub 镜像同步**（第 5 条）—— 部署后检查并确认同步
6. ✅ **SHOW_DETAILS=always 保留**（第 6 条）—— 用户连续三次决定保留
7. ✅ **systemctl sudo 权限**（第 8 条）—— `SYSTEMCTL_SUDO=true`，jetty 用户已配置 NOPASSWD sudo
8. ✅ **Mac HTTP_PROXY 502 绕过**（第 16 条）—— 所有 curl 使用 `--noproxy '*'`
9. ✅ **SentryAppender crash-loop 防复发**（第 17 条）—— 本次部署 sentryEnabled=false，无 logback 配置问题

## 风险提示

1. **PR !1574 未包含**：本次部署基于 `665dd3abb`，PR !1574（UserPicker value-key 修复）已合并到 origin/main 但未包含在本次 release 中。下次部署需包含。
2. **Kafka SDK readiness 延迟**：89 次重试（约 3 分钟）属已知行为，但若超过 4 分钟需考虑回滚。
3. **服务器 /tmp/migration-mysql/ 目录可能过时**（第 11 条）：本次预检直接查 SQL 确认 DB 状态，未依赖 info 输出。
4. **GitHub 镜像同步状态**：部署后已确认两边一致，但后续若有新 PR 合并到 Gitee 需及时同步。

## 部署确认清单

| 检查项 | 结果 |
|---|---|
| 早操三连 | ✅ |
| 基线确认（HEAD = origin/main） | ✅ |
| 服务器现状（deployed-release.json + health） | ✅ |
| 增量 commit + 迁移文件变更 | ✅（41 commit，无新迁移） |
| Flyway 预检 3 步 | ✅ |
| 本地打包 | ✅ |
| 产物校验 | ✅（191 V*.sql 无重复，前端入口一致） |
| 上传 + 部署 | ✅ |
| 后端健康检查 | ✅ 9/9 组件 UP |
| Smoke 测试 | ✅ 5 项全绿 |
| 前端一致性 | ✅ |
| Flyway 迁移应用验证 | ✅（V1128 仍是最新） |
| GitHub 镜像同步 | ✅（两边完全一致） |
| 临时配置清理 | ✅（无新增，SHOW_DETAILS=always 保留） |
| 部署报告 | ✅ 本报告 |

---

**部署结论**：✅ 第 37 次部署成功。PR !1557-!1573 共 41 个增量 commit 上线，涵盖权限双轨制技术债消除、CO-448 保证金持久化、CA/资质/业绩/附件等多项业务修复。生产服务 UP，Smoke 全绿，Flyway 192 migrations validate OK，DB V1128 仍是最新。PR !1574（UserPicker value-key）将在下次部署包含。
