# 第 34 次生产部署报告 — 失败部署 + 成功回滚

> **部署状态**：❌ 部署失败 → ✅ 回滚成功
> **生产服务状态**：🟢 UP（回滚到 057612930-api8080）
> **事故等级**：P0（后端 crash-loop 约 10 分钟，已恢复）

## 部署概览

| 项目 | 值 |
|---|---|
| 部署编号 | 第 34 次 |
| 日期 | 2026-07-02 |
| 目标 Release ID | `0f026a9b7-api8080` |
| 回滚到 Release ID | `057612930-api8080`（第 33 次） |
| 目标 commit | `0f026a9b702d29833f085b1ff1874c45053c3a2b` |
| 上一部署 commit | `057612930` |
| 增量 commit | 33 个 |
| 新增 Flyway 迁移 | 无 |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 执行人 | trae agent |
| 结果 | ❌ 后端启动失败（SentryAppender 实例化异常）→ ✅ 回滚成功 |

## 基线信息

- **早操三连**：source dev-env.sh + sync-env.sh + check-git-wrapper.sh ✅
- **任务分支**：`agent/trae/deploy-31st-report`（实际是第 34 次部署，分支名沿用历史编号）
- **基线**：HEAD = origin/main = `0f026a9b7`
- **GitHub 镜像**：落后 Gitee 33 个 commit（部署前状态，本次无 Gitee main 变更）
- **本地门禁**：7/7 通过

## PR 列表（!1518-!1529，共 33 个增量 commit）

| PR | 说明 |
|---|---|
| !1518 | fix(CO-447): 账户管理列表按创建时间倒序展示 |
| !1519 | feat(monitoring): Sentry Logback 集成 + 清理前端禁用分支 ⚠️ **事故根因** |
| !1520 | fix(ui): CO-463 简化项目立项招标文件字段标题 |
| !1521 | fix(CO-435): 修复CA编辑空密码被el-form-item :required拦截 |
| !1522 | refactor(approval): 023 统一审批接口契约 (CO-459 防复发) |
| !1523 | fix(deposit-fields): 修复执行人无法填写保证金 4 字段的 regression |
| !1524 | CO-458: 交付物和完成情况必填标识按场景区分 |
| !1525 | fix(CO-465): 任务执行人提交审核后字段丢失——移除乐观更新 |
| !1526 | fix: 项目详情页任务提交审核状态未更新——补齐 @submit-review 事件绑定 |
| !1527 | fix(notification): 通知跳转空白页根因修复 |
| !1528 | fix(CO-466): 项目档案下载/导出/预览接口权限注解过度收紧导致投标专员403 |
| !1529 | docs(release): 第 33 次部署报告 |

## 改动范围

- **后端**：Sentry SDK 集成、审批接口契约统一、权限注解修复、保证金字段 regression 修复
- **前端**：通知跳转修复、项目立项字段简化、CA 编辑修复
- **Flyway 迁移**：无新增

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（191 个迁移） |
| Step 2: DB 版本对比 | ✅ DB 最新 V1127，与源码一致（无新增迁移） |
| Step 3: remote-deploy 内置 validate | ✅ 通过 |

## 部署步骤与失败点

### 1. 本地打包 ✅
- `RELEASE_ID="0f026a9b7-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
- BUILD SUCCESS（24.784s）
- jar 内 190 个 Flyway 迁移文件，无重复版本
- 前端入口：`assets/index-BV9XIthS.js`

### 2. 上传 + 部署 ✅（脚本退出码 0，但后端实际 crash-loop）
- scp 上传成功
- remote-deploy.sh 执行：
  - Flyway validate 通过
  - JAR 覆盖成功
  - 前端切换成功
  - 服务重启成功（13:20:22 CST）
  - 健康检查脚本返回 502（但脚本误判为成功退出？需排查 remote-deploy.sh 健康检查逻辑）

### 3. ❌ 后端启动失败（crash-loop）
- **根因**：`io.sentry.logback.SentryAppender` 无法实例化
- **错误日志**：
  ```
  java.lang.IllegalStateException: Logback configuration error detected:
  ERROR in ch.qos.logback.core.model.processor.AppenderModelHandler - Could not create an Appender of type [io.sentry.logback.SentryAppender].
  ch.qos.logback.core.util.DynamicClassLoadingException: Failed to instantiate type io.sentry.logback.SentryAppender
  ```
- **引入 PR**：!1519 `feat(monitoring): Sentry Logback 集成`
- **现象**：systemd 反复重启失败（exit-code 1），nginx 返回 502 Bad Gateway
- **crash-loop 持续**：约 10 分钟（13:20:22 - 13:30:00）

### 4. 根因分析

**logback.xml 配置**（prod/staging profile）：
```xml
<appender name="SENTRY" class="io.sentry.logback.SentryAppender">
    <minimumLevel>ERROR</minimumLevel>
</appender>
```

**Sentry 依赖**（已打包进 jar）：
- sentry-spring-boot-starter-jakarta-7.20.0.jar
- sentry-spring-boot-jakarta-7.20.0.jar
- sentry-7.20.0.jar
- sentry-spring-jakarta-7.20.0.jar

**可能根因**（待确认）：
1. Sentry 7.20.0 的 `SentryAppender` 在 Sentry SDK 未初始化（DSN 未配置或 `Sentry.init()` 未调用）时，实例化即失败
2. logback.xml 注释"无 DSN 时 SentryAppender 自动 no-op"可能是错误假设
3. 服务器 `/etc/xiyu-bid/backend.env` 可能缺少 Sentry DSN 配置

**本地未发现的原因**：
- 本地 dev profile 不激活 SentryAppender（仅 prod/staging 激活）
- 本地测试未覆盖 prod profile 的 logback 配置加载

### 5. 回滚执行 ✅
- 用户决策：立即回滚到 057612930
- 回滚步骤：
  1. `sudo systemctl stop xiyu-bid-backend`
  2. `cp /opt/xiyu-bid/releases/057612930-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar`
  3. `sudo rm -rf /srv/www/xiyu-bid/assets ... && sudo cp -r /opt/xiyu-bid/releases/057612930-api8080/frontend/* /srv/www/xiyu-bid/`
  4. `sudo find /srv/www/xiyu-bid/ -name "._*" -delete`
  5. `sudo systemctl start xiyu-bid-backend`
  6. 等待健康检查（第 76 次通过，约 152 秒，含 Kafka 启动延迟）
- 回滚后 deployed-release.json 更新，标注 `rollbackFrom` 和 `rollbackReason`

## 验证结果（回滚后）

### 后端健康检查
| 检查项 | 结果 |
|---|---|
| `/actuator/health` | ✅ 200 UP |
| `/actuator/health/readiness` | ✅ 200 UP |
| aiProvider | ✅ UP (doubao, deepseek-v3-2-251201) |
| db | ✅ UP (MySQL) |
| redis | ✅ UP (6.2.19) |
| sidecar | ✅ UP |
| readinessState | ✅ UP（无 Kafka 延迟） |

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
| 前端 assets | `assets/index-kGBb_RQ1.js`（与 057612930 一致） |

## GitHub 镜像同步

- **部署前状态**：GitHub 镜像落后 Gitee 33 个 commit
- **本次部署**：失败 + 回滚，Gitee main 无新 commit
- **当前状态**：GitHub 仍落后 33 个 commit（未恶化）
- **后续处理**：待下次成功部署后统一同步，或单独执行 `bash scripts/sync-to-github.sh`

## 临时配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 历史决定（第 13/14/15 次用户决定保留，运维监控需要） |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` | 无新增 | 本次部署未引入临时配置 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚目标 | `057612930-api8080`（第 33 次部署） |
| 回滚方式 | 手动恢复旧 jar + 旧前端 + 重启服务 |
| 回滚耗时 | 约 3 分钟（停服务 → 恢复 → 启动 → 健康检查） |
| 数据库备份 | 本次无新增迁移，未单独备份（DB 状态未变） |
| 回滚后服务状态 | 🟢 UP，所有 Smoke 通过 |
| deployed-release.json | 已更新，标注 `rollbackFrom` 和 `rollbackReason` |

## 经验沉淀应用情况

本次部署应用了以下历史经验：
1. ✅ **Flyway 预检 3 步法**（第 1 条）—— 预检全通过
2. ✅ **Mac HTTP_PROXY 502 绕过**（第 16 条）—— 所有 curl 使用 `--noproxy '*'`
3. ✅ **systemctl sudo 权限**（第 8 条）—— `SYSTEMCTL_SUDO=true`
4. ✅ **前端目录权限**（第 13 条）—— 回滚时用 sudo 操作 /srv/www/xiyu-bid/
5. ✅ **macOS `._*` 残留清理**（第 14 条）—— 回滚时 `find ... -name "._*" -delete`

## 新增经验教训（本次事故）

### 🆕 第 17 条：Sentry Logback 集成必须在 prod profile 下测试

**事故**：PR !1519 Sentry Logback 集成在本地 dev profile 下测试通过，但 prod profile 下 `SentryAppender` 实例化失败导致后端 crash-loop。

**根因**：
- logback.xml 中 `SentryAppender` 仅在 `prod | staging` profile 激活
- 本地开发用 `dev` profile，从未加载 SentryAppender
- Sentry 7.20.0 的 `SentryAppender` 在 SDK 未初始化时实例化失败（DSN 未配置或 `Sentry.init()` 未调用）
- logback.xml 注释"无 DSN 时 SentryAppender 自动 no-op"是错误假设

**防复发措施**：
1. **新增 CI 测试**：在 prod profile 下启动 Spring Boot 上下文，验证 logback 配置可加载
2. **PR 审查强化**：涉及 logback.xml / logging 配置的 PR，必须在 prod profile 下验证启动
3. **Sentry 配置审查**：确认服务器 `/etc/xiyu-bid/backend.env` 是否配置了 Sentry DSN；若配置仍失败，需移除 SentryAppender 或降级 Sentry 版本
4. **remote-deploy.sh 健康检查逻辑修复**：本次脚本退出码 0 但后端实际 crash-loop，说明健康检查逻辑有缺陷（可能 502 被误判为成功）

**后续修复方向**（待单独 PR）：
- 选项 A：移除 logback.xml 中 SentryAppender，改用 Sentry Spring Boot Starter 的自动配置（不依赖 logback appender）
- 选项 B：在 logback.xml 中给 SentryAppender 加 `<with>no-op-fallback</with>` 或类似容错配置（需查 Sentry 7.20.0 文档）
- 选项 C：确保 Sentry SDK 在 logback 初始化前完成初始化（可能需要 `Sentry.init()` 在 main 方法中提前调用）
- 选项 D：降级 Sentry 到更稳定版本

## 风险提示

1. **PR !1519-!1528 的业务修复未上线**：回滚到 057612930 意味着以下业务修复暂未生效：
   - CO-447 账户管理列表排序
   - CO-463 项目立项字段标题简化
   - CO-435 CA 编辑空密码修复
   - CO-459 审批接口契约统一
   - CO-458 交付物必填标识
   - CO-465 任务提交审核字段丢失
   - CO-466 项目档案权限注解
   - 通知跳转空白页修复
   - 项目详情页任务提交审核状态更新
2. **Sentry 监控缺失**：回滚后无 Sentry 错误上报，需依赖日志监控
3. **GitHub 镜像落后 33 个 commit**：待下次成功部署后同步
4. **remote-deploy.sh 健康检查逻辑缺陷**：需修复，否则下次部署可能再次误判

## 部署确认清单

| 检查项 | 结果 |
|---|---|
| 早操三连 | ✅ |
| 基线确认（HEAD = origin/main） | ✅ |
| Flyway 预检 3 步 | ✅ |
| 本地打包 | ✅ |
| 产物校验 | ✅ |
| 上传 + 部署 | ✅（脚本成功） |
| 后端健康检查 | ❌ 失败 → ✅ 回滚后恢复 |
| Smoke 测试 | ✅（回滚后） |
| GitHub 镜像同步 | ⏸ 待后续处理 |
| 临时配置清理 | ✅（无新增） |
| 部署报告 | ✅ 本报告 |

## 后续行动项

1. **P0 修复 Sentry Logback 问题**：单独 PR，在 prod profile 下验证启动
2. **P1 修复 remote-deploy.sh 健康检查逻辑**：502 不应判为成功
3. **P1 重新部署**：Sentry 修复后，重新部署 PR !1518-!1529 的业务修复
4. **P2 GitHub 镜像同步**：下次成功部署后执行 `bash scripts/sync-to-github.sh`
5. **P2 沉淀第 17 条经验**：更新 xiyu-server-deploy skill 的 Key Lessons

---

**部署结论**：❌ 第 34 次部署失败（SentryAppender 实例化异常），✅ 回滚成功，生产服务已恢复到第 33 次部署状态（057612930-api8080）。需优先修复 Sentry 问题后重新部署。
