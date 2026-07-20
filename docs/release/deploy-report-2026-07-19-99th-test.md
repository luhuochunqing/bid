# 第 99 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `343ca660e` |
| 上一版本 | `6d95c5946`（第 98 次，2026-07-18 12:57） |
| 部署时间 | 2026-07-19 21:18 CST |
| 增量 | 22 commit（CO-590 合同信息模块 + CO-591 项目列表四列+导出 + CO-592 档案分类统一 + 企微 SSO 登录） |
| 新增迁移 | V1169 (CO-590 合同信息) + V1170 (CO-592 档案分类) |
| 部署结果 | ✅ 成功（健康检查 120 次尝试失败后自恢复 UP，约 4 分钟，Kafka SDK readiness 延迟已知行为） |
| 回滚 | 未需要 |
| JVM 配置 | `-Xmx2g` 沿用第 97 次配置 |

## 部署原因

本次部署合入三组业务功能与一项集成改造：

1. **CO-590 合同信息模块**：结果确认阶段新增合同信息（合同编号、合同金额、服务周期等）
2. **CO-591 投标项目列表四列+导出**：项目列表增加 4 列显示字段，并支持导出
3. **CO-592 项目档案文档分类统一**：将档案文档分类统一为 6 个中文选项
4. **企微 SSO 单点登录**：走 base-oss 换 token 完成自动登录 + 消息推送 URL 改造为 OAuth 授权链接

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae-init（锚点分支，部署不创建任务分支） |
| HEAD commit | `343ca660e6b18c132d634a8f9ae074f62114db09` |
| origin/main | `343ca660e`（完全一致） |
| GitHub 镜像 | ✅ 完全一致（github/main..origin/main = 0，早操已同步 13 个 commit） |
| git wrapper | ⚠️ 未激活（部署不涉及 git push，不影响） |
| Flyway validate | ✅ 通过（231 migrations, all checksums match） |
| DB 已应用最新版本 | V1168（tender reminder default 72h） |
| 源码最新迁移版本 | V1170（待应用：V1169 + V1170） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2142 | feat(archive) | 项目档案文档分类统一为 6 个中文选项 (CO-592) |
| !2141 | feat(project-list) | 投标项目列表增加四列及导出支持 (CO-591) |
| !2140 | docs(release) | 第 12 次生产环境部署报告 |
| !2139 | chore | 同步 GitHub cherry-pick + 刷新过期 wiki health_checked 日期 |
| !2138 | feat(project-result) | 结果确认阶段新增合同信息模块 (CO-590) |
| !2136 | feat(integration) | 企微 SSO 单点登录走 base-oss 换 token 完成自动登录 |
| (无 PR) | refactor(integration) | 企微 SSO 设计评审修复（P0+P1+P2） |
| (无 PR) | feat(notification) | 消息推送 URL 改造为 OAuth 授权链接实现企微免密登录 |
| (无 PR) | test(integration) | 补充 WeComSsoOssLoginService code 守卫测试用例 |

## 改动范围

### 1. CO-590 合同信息模块（!2138）
- **V1169 迁移**：`add_contract_info_to_project_result` 给 `project_result` 表新增合同信息字段（合同编号、合同金额、服务周期等）
- **结果确认阶段 UI**：新增合同信息模块展示
- **服务周期截止时间显示格式**：改为 YYYY-MM-DD

### 2. CO-591 投标项目列表四列+导出（!2141）
- **列表新增 4 列**：补充项目列表展示字段
- **ProjectListStageEnricher 单测**：补齐头注释约定句

### 3. CO-592 项目档案文档分类统一（!2142）
- **V1170 迁移**：`unify_archive_file_category` 将档案文档分类统一为 6 个中文选项
- **workbench-characterization.spec.js 本地失败**：登记为技术债

### 4. 企微 SSO 单点登录（!2136 + 多次迭代）
- **base-oss 换 token**：走 base-oss 完成自动登录
- **消息推送 URL 改造**：OAuth 授权链接实现企微免密登录
- **WeComSsoOssLoginService code 守卫测试**：补全测试用例
- **设计评审修复（P0+P1+P2）**：低复杂度项整改

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: flyway-repair-runner.sh validate | ✅ VALIDATE OK - all checksums match（231 migrations） |
| Step 2: DB 已应用版本 vs 源码版本 | ✅ DB 在 V1168，源码待应用 V1169 + V1170 |
| Step 3: remote-deploy.sh 内置 validate | ✅ 通过（231 migrations validated） |

## 部署步骤

| 步骤 | 操作 | 结果 |
|---|---|---|
| 1 | 早操三连（sync-env + git wrapper 检查） | ✅ sync-env 完成（rebase 13 commits + GitHub 镜像同步）；git wrapper 未激活但不影响部署 |
| 2 | 基线确认（HEAD=origin/main=343ca660e，GitHub 同步） | ✅ 干净 |
| 3 | 服务器现状检查 | ✅ 上一版本 6d95c5946，UP（含 readiness UP） |
| 4 | Flyway 预检 3 步法 | ✅ 全部通过 |
| 5 | 本地打包（RELEASE_ID=343ca660e, VITE_API_BASE_URL=, VITE_OBS_ENABLED=true, COPYFILE_DISABLE=1） | ✅ BUILD SUCCESS（后端 28.52s） |
| 6 | 产物校验（obsEnabled=true, jar 含 V1169+V1170, Detail .upload=2, 前端入口 index-BBjIVsiE.js） | ✅ 全部通过 |
| 7 | scp 上传（archive + remote-deploy.sh） | ✅ 完成 |
| 8 | remote-deploy.sh（SYSTEMCTL_SUDO=true） | ✅ 部署成功（Flyway validate 通过，jar 覆盖，服务重启） |
| 9 | 前端资源保留（从 6d95c5946 cp -rn 旧 assets） | ✅ 178 → 258 文件（80 个旧版保留） |

## 验证结果

### 健康检查

| 检查项 | 结果 |
|---|---|
| /actuator/health | 200 UP |
| /actuator/health/readiness | 200 UP |
| 健康检查通过 | remote-deploy.sh 内置 120 次尝试失败（4 分钟），手动验证已自恢复 UP（Kafka SDK readiness 延迟已知行为，与第 8/9/10/13/15 次一致） |
| 所有组件状态 | db UP, redis UP, aiProvider UP, diskSpace UP, jwt UP, ping UP, livenessState UP, readinessState UP, sidecar UP |

### Smoke 测试

| 测试项 | 期望 | 实际 | 结果 |
|---|---|---|---|
| /actuator/health | 200 UP | 200 UP | ✅ |
| /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| /api/auth/login（空密码） | 400 | 400 参数校验失败 | ✅ |
| /api/projects（需认证） | 403 | 403 | ✅ |
| /api/integration/crm/health（需认证） | 401 | 401 | ✅ |
| 前端首页 / | 200 | 200 | ✅ |
| 前端 /login | 200 | 200 | ✅ |
| 前端入口 JS | index-BBjIVsiE.js | index-BBjIVsiE.js | ✅ |

### GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | 343ca660e |
| GitHub main | 343ca660e（同步，0 落后） |
| 状态 | ✅ 完全一致 |

### 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always | 保留 | 用户已决定保留（第 13/14/15 次延续） |
| JAVA_OPTS=-Xmx2g | 保留 | 第 96 次添加，本次沿用 |
| DEBUG / TRACE 临时配置 | 无 | ✅ 干净 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | 未需要 |
| 上一版本 release | `/opt/xiyu-bid/releases/6d95c5946/` |
| 上一版本 jar | `/opt/xiyu-bid/releases/6d95c5946/backend/app.jar` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-343ca660e-*.sql.gz` |
| 回滚命令（代码） | `cp /opt/xiyu-bid/releases/6d95c5946/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| 回滚命令（DB V1169+V1170） | `mysql < U1169__add_contract_info_to_project_result.sql && mysql < U1170__unify_archive_file_category.sql` |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #2 Kafka SDK readiness 延迟 | ✅ 已知行为，120 次重试失败后自恢复 UP（约 4 分钟），手动验证通过 |
| #3 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| #8 systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| #10 OBS 直传漏传 | ✅ VITE_OBS_ENABLED=true + 产物校验 obsEnabled=true + Detail .upload=2 |
| #14 macOS ._* 残留 | ✅ COPYFILE_DISABLE=1 |
| #16 Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| #18 前端 hash 资源跨版本 404 | ✅ 从 6d95c5946 cp -rn 旧 assets（178 → 258 文件） |

## 风险提示

1. **Kafka SDK readiness 延迟**：本次 120 次重试失败（4 分钟）后自恢复 UP，与第 8/9/10/13/15 次一致。`remote-deploy.sh` 内置 120 次重试对 Kafka 延迟场景处于临界点，建议延长到 6 分钟（参考 skill 教训 #1）。后续可考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 避免阻塞主线程。
2. **前端资源保留脚本缺陷**：`deployed-release.json` 已被本次部署覆盖导致 `PREV` 变量取值失效（grep `releaseDir` 模式不匹配带空格的 JSON 格式），需手动从 `releases/` 目录定位上一版本（本次为 `6d95c5946`）。已记录在 skill 教训 #18，建议下次部署时修正 grep 模式以容忍冒号后空格。
3. **企微 SSO 联调**：本次部署含企微 SSO + 消息推送 URL 改造，建议 UAT 阶段重点验证：a) 企微点击消息推送链接的自动登录链路；b) SSO code 守卫测试覆盖；c) OAuth 授权链接在企微客户端的兼容性。
4. **CO-592 档案分类历史数据**：V1170 统一了档案文档分类为 6 个中文选项，已应用历史数据的迁移需 UAT 验证存量档案文档的分类映射是否正确。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 早操三连通过
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（obsEnabled=true）
- [x] 产物校验通过（V1169+V1170 in jar, Detail .upload=2）
- [x] 远程部署成功
- [x] JVM MaxHeapSize=2g 沿用生效
- [x] 健康检查 UP（120 次重试后自恢复）
- [x] Smoke 测试全通过（8/8）
- [x] 前端资源保留完成（178 → 258 文件）
- [x] GitHub 镜像同步一致（0 落后）
- [x] 配置清理检查（仅 SHOW_DETAILS=always 和 JAVA_OPTS=-Xmx2g 保留）
- [x] 部署报告已生成
