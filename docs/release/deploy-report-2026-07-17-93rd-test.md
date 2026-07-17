# 第 93 次测试环境部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 环境 | 测试环境 (test) |
| 主机 | winbid-01 (172.16.38.78) |
| Release ID | `a33c1339d-api8080` |
| 上一版本 | `0a79f0d68-api8080`（第 92 次，2026-07-16 20:13） |
| 部署时间 | 2026-07-17 16:17 CST |
| 增量 | 21 commit（仓库 spec 039 系列修复 + 工作台/性能/CI 改进） |
| 新增迁移 | 无 |
| 部署结果 | ✅ 成功（Kafka SDK readiness 延迟约 4 分钟自恢复） |
| 回滚 | 未需要 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | /Users/user/xiyu/worktrees/trae |
| 分支 | agent/trae/deploy-test-93rd（基于 origin/main） |
| HEAD commit | `a33c1339daa51f6d905ed6d76f0f2e2ade8d9b2c` |
| GitHub 镜像 | 完全一致（a33c1339d） |
| git wrapper | ✅ 安全检查通过 |
| Flyway validate | ✅ 通过（231 migrations, all checksums match） |
| DB 已应用最新版本 | V1168（tender reminder default 72h） |
| 源码最新迁移版本 | V1168（与 DB 一致，无新增） |

## 增量 PR 列表

| PR | 类型 | 标题 |
|---|---|---|
| !2113 | fix(warehouse) | 附件文件缺失时输出 WARN 日志（数据完整性诊断） |
| !2112 | fix(warehouse) | 修复 Word 合订本附件内容丢失（macOS SSV + 绝对路径默认值） |
| !2111 | fix(warehouse) | 修复仓库导入 @Async 自调用失效 — 提取独立 Bean (spec 039) |
| !2110 | fix(warehouse) | 039 修复仓库导出全量合订本创建失败 — 提取 @Async 方法到独立 Bean (spec 039) |
| !2108 | feat(performance) | 合同协议附件设为导入必填字段并加 * 号 (CO-586) |
| !2107 | fix(workbench) | 固定待办/截止时间列宽，省略号生效 |
| !2106 | docs(release) | 第 92 次测试环境部署报告 |
| !2104 | chore(ci)+docs(lessons) | 教训 #61 schema 覆盖式迁移门禁 — 文档软约束升级为 CI 硬约束 |

## 改动范围

本次部署涉及多模块修复与改进：

### 1. 仓库模块（spec 039 系列，4 个 PR）
- 修复仓库导入 @Async 自调用失效（Spring AOP 自调用陷阱，提取独立 Bean）
- 修复仓库导出全量合订本创建失败（@Async 方法提取到独立 Bean）
- 修复 Word 合订本附件内容丢失（macOS SSV + 绝对路径默认值问题）
- 附件文件缺失时输出 WARN 日志，便于数据完整性诊断

### 2. 工作台 UI 修复（1 个 PR）
- 固定待办/截止时间列宽，使省略号样式生效

### 3. 性能模块（1 个 PR）
- 合同协议附件设为导入必填字段，前端加 * 号标识（CO-586）

### 4. 招标模块
- 招标文件不利项限制 500 字
- 计划入围供应商数量最大值改为 255

### 5. CI 改进（1 个 PR）
- 教训 #61：自定义表单 schema 迁移从"文档软约束"升级为"CI 硬约束"
- 精简 check-form-schema-migration.sh，删除死代码和过度抽象

### 6. 文档
- 第 92 次测试环境部署报告合入

**无 Flyway 迁移文件变更，无数据库 schema 变更。**

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（231 migrations） |
| Step 2: DB 已应用版本 | V1166/V1167/V1168（第 91 次部署已应用，本次无新增） |
| Step 3: remote-deploy 内置 | ✅ Flyway validate 通过（仅 pending 新迁移为预期状态） |

## 部署步骤

1. ✅ 环境门禁：用户确认部署到测试环境 172.16.38.78
2. ✅ 任务分支创建：`agent/trae/deploy-test-93rd`（基于 origin/main）
3. ✅ 早操三连：source dev-env.sh + sync-env.sh + check-git-wrapper.sh
4. ✅ 服务器现状检查：deployed-release.json（0a79f0d68-api8080）+ health UP
5. ✅ 增量分析：21 commits，无新迁移文件
6. ✅ Flyway 预检 3 步法：validate + DB 版本对比 + remote-deploy 内置
7. ✅ 本地打包：RELEASE_ID=a33c1339d-api8080 + VITE_API_BASE_URL= + VITE_OBS_ENABLED=true + COPYFILE_DISABLE=1（28.7s）
8. ✅ 产物校验：jar 内 230 迁移文件无重复 + OBS obsEnabled=true + Detail chunk .upload( 调用数=2
9. ✅ 上传 + 部署：scp archive + remote-deploy.sh（SYSTEMCTL_SUDO=true）
10. ⚠️ 健康检查：remote-deploy.sh 内置检查失败（120 次重试），但服务实际已运行
11. ✅ Kafka readiness 延迟自恢复：约 4 分钟后所有组件 UP（已知行为，第 8/9/10/13/15 次均出现）
12. ✅ Smoke 测试 5 项全通过
13. ✅ 前端资源保留：从 0a79f0d68-api8080 cp -rn assets 到 /srv/www/xiyu-bid/assets/
14. ✅ GitHub 镜像同步：两边 main 完全一致
15. ✅ 临时调试配置检查：SHOW_DETAILS=always 用户决定保留

## 验证结果

### 后端健康检查

```
status: UP
  aiProvider: UP
  db: UP
  diskSpace: UP
  jwt: UP
  livenessState: UP
  ping: UP
  readinessState: UP
  redis: UP
  sidecar: UP
```

### Smoke 测试

| 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | HTTP 200 | ✅ |
| `/actuator/health/readiness` | 200 UP | HTTP 200 | ✅ |
| `/api/auth/login` (POST {}) | 400 | HTTP 400 | ✅ |
| `/api/projects` | 403 | HTTP 403 | ✅ |
| `/api/integration/crm/health` | 401 | HTTP 401 | ✅ |

### 前端页面验证

| 路径 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `/` | 200 | HTTP 200 | ✅ |
| `/login` | 200 | HTTP 200 | ✅ |
| index.js hash | 与 release 一致 | `assets/index-DJPJUdSo.js` | ✅ |

### 业务请求验证

部署后约 1 分钟内观察到实际业务请求正常处理（journalctl 日志）：
- OSS 用户登录正常（user=06234, user=11484，roleCode=/bidAdmin）
- `/api/notifications/unread-count` 返回 200
- 业务请求 `elapsed` 均在 20ms 以内

## Kafka SDK Readiness 延迟分析

**现象**：`remote-deploy.sh` 内置健康检查失败（120 次重试，每次 2 秒，共约 4 分钟），但服务实际已运行。

**根因**：`OrganizationEventSdkKafkaStarter` 使用 `@EventListener(ApplicationReadyEvent.class) @Order(LOWEST_PRECEDENCE)`。Kafka 初始化阻塞主线程导致 `AvailabilityChangeEvent` 延迟处理，readinessState 长时间停留在 OUT_OF_SERVICE（HTTP 503）。

**恢复**：约 4 分钟后自恢复，所有组件 UP。

**历史出现**：第 8、9、10、13、15、93 次均出现，已沉淀为已知行为，无需回滚。

**改进方向**：考虑将 `onApplicationReady()` 改为 `@Async` 或独立线程池执行，避免阻塞主线程。

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| Gitee main | `a33c1339daa51f6d905ed6d76f0f2e2ade8d9b2c` |
| GitHub main | `a33c1339daa51f6d905ed6d76f0f2e2ade8d9b2c` |
| 同步前落后 | 3 commits |
| 状态 | ✅ 完全一致 |

## 回滚信息

- **回滚状态**：未需要
- **回滚方式**（如需）：恢复上一版本 jar + 前端资源
  - 上一版本 release 目录：`/opt/xiyu-bid/releases/0a79f0d68-api8080`
  - 上一版本 jar：`/opt/xiyu-bid/releases/0a79f0d68-api8080/backend/app.jar`
  - 回滚命令：`sudo cp /opt/xiyu-bid/releases/0a79f0d68-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend`

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 已执行（validate + DB 版本对比 + remote-deploy 内置） |
| OBS 直传双保险 | ✅ VITE_OBS_ENABLED=true 显式传入 + 产物校验 obsEnabled=true |
| 同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| macOS `._*` 残留 | ✅ COPYFILE_DISABLE=1 |
| systemctl sudo | ✅ SYSTEMCTL_SUDO=true |
| Mac HTTP_PROXY 502 | ✅ curl --noproxy '*' |
| 前端资源保留 | ✅ 从 0a79f0d68-api8080 手动 cp -rn assets（脚本自动取 PREV 失败，已手动指定） |
| Kafka SDK readiness 延迟 | ⚠️ 本次出现（约 4 分钟自恢复），已知行为，未回滚 |
| SHOW_DETAILS=always | ✅ 用户决定保留 |

## 风险提示

1. **前端资源保留脚本缺陷**：`remote-deploy.sh` 写入新 `deployed-release.json` 后，`PREV` 变量取到的是当前版本而非上一版本。本次手动从 `0a79f0d68-api8080` 复制旧 assets 解决。建议后续优化脚本，在覆盖 `deployed-release.json` 前先备份上一版本信息。
2. **Kafka SDK readiness 延迟**：第 8/9/10/13/15/93 次出现，约 4 分钟自恢复。建议后续将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为异步执行。
3. **remote-deploy.sh 健康检查超时**：内置 120 次重试（约 4 分钟）对 Kafka readiness 延迟场景不够。建议增加重试次数或延长超时时间到 6 分钟。

## 部署确认清单

- [x] 环境门禁确认（测试环境 172.16.38.78）
- [x] 任务分支创建（agent/trae/deploy-test-93rd）
- [x] 早操三连执行（sync-env + check-git-wrapper）
- [x] 服务器现状检查（deployed-release.json + health UP）
- [x] 增量分析（21 commits，无新迁移）
- [x] Flyway 预检 3 步法
- [x] 本地打包（同源构建 + OBS 直传）
- [x] 产物校验（jar 无重复迁移 + OBS 直传已启用）
- [x] 上传 + 部署（remote-deploy.sh）
- [x] 健康检查通过（Kafka readiness 延迟约 4 分钟后自恢复）
- [x] Smoke 测试 5 项全通过
- [x] 前端页面验证通过
- [x] 前端资源保留（手动从 0a79f0d68-api8080 cp -rn assets）
- [x] GitHub 镜像同步
- [x] 临时调试配置检查（SHOW_DETAILS=always 用户决定保留）
- [x] 部署报告生成

## 主要功能变更

本次部署主要修复仓库模块 spec 039 系列问题，并改进工作台/性能/CI：

### 1. 仓库模块（spec 039，核心修复）
- **@Async 自调用失效修复**：Spring AOP 自调用陷阱导致 @Async 方法在同类内调用时失效，提取独立 Bean 解决
- **Word 合订本附件内容丢失修复**：macOS SSV + 绝对路径默认值导致附件内容丢失
- **全量合订本创建失败修复**：@Async 方法提取到独立 Bean
- **附件缺失诊断**：附件文件缺失时输出 WARN 日志，便于数据完整性诊断

### 2. 工作台 UI
- 固定待办/截止时间列宽，使省略号样式生效

### 3. 性能模块（CO-586）
- 合同协议附件设为导入必填字段，前端加 * 号标识

### 4. 招标模块
- 招标文件不利项限制 500 字
- 计划入围供应商数量最大值改为 255

### 5. CI 改进（教训 #61）
- 自定义表单 schema 迁移从"文档软约束"升级为"CI 硬约束"
- 精简 check-form-schema-migration.sh
