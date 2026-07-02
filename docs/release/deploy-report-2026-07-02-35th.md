# 第 35 次生产部署报告

> **部署状态**：✅ 部署成功
> **生产服务状态**：🟢 UP
> **特殊说明**：本次部署包含第 34 次 P0 事故的修复（SentryAppender crash-loop）

## 部署概览

| 项目 | 值 |
|---|---|
| 部署编号 | 第 35 次 |
| 日期 | 2026-07-02 |
| Release ID | `0a057f757-api8080` |
| commit | `0a057f757d4a5e70ad4fedd9f80098c1c2ab127a` |
| 上一部署 Release | `057612930-api8080`（第 33 次，第 34 次回滚后状态） |
| 增量 commit | 35 个 |
| 新增 Flyway 迁移 | 无 |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 执行人 | trae agent |
| 结果 | ✅ 部署成功，Smoke 全绿 |

## 基线信息

- **早操三连**：source dev-env.sh + sync-env.sh + check-git-wrapper.sh ✅
- **任务分支**：`agent/trae/deploy-35th-report`
- **基线**：HEAD = origin/main = `0a057f757`
- **GitHub 镜像**：落后 Gitee（待同步）
- **本地门禁**：7/7 通过

## P0 修复说明

### 第 34 次事故回顾
- **事故**：PR !1519 Sentry Logback 集成导致后端 crash-loop 约 10 分钟
- **根因**：`logback-spring.xml` 手动声明 `io.sentry.logback.SentryAppender`，但项目未引入 `sentry-logback` 依赖（`sentry-spring-boot-starter-jakarta` 不包含此模块），logback 初始化时 `DynamicClassLoadingException`
- **处理**：回滚到 057612930-api8080

### 本次修复（PR !1533）
- **修复方式**：移除 `logback-spring.xml` 中 `SentryAppender` 手动声明（共 7 行删除）
- **Sentry 官方文档依据**：Spring Boot Starter 会自动配置 `SentryAppender`，不需要手动声明
- **验证**：本地 prod profile 启动，logback 配置加载成功，SentryAppender 错误消失
- **影响**：
  - ✅ Sentry SDK 仍通过 `sentry-spring-boot-starter-jakarta` 自动配置
  - ✅ `SentryConfig.beforeSendCallback` 仍正常工作（捕获未处理异常）
  - ⚠️ 丢失 ERROR 日志自动上报（后续可引入 `sentry-logback` 依赖恢复）

## PR 列表（!1518-!1533，共 35 个增量 commit）

| PR | 说明 | 备注 |
|---|---|---|
| !1518 | fix(CO-447): 账户管理列表按创建时间倒序展示 | |
| !1519 | feat(monitoring): Sentry Logback 集成 | ⚠️ 第 34 次事故根因 |
| !1520 | fix(ui): CO-463 简化项目立项招标文件字段标题 | |
| !1521 | fix(CO-435): 修复CA编辑空密码被el-form-item :required拦截 | |
| !1522 | refactor(approval): 023 统一审批接口契约 (CO-459 防复发) | |
| !1523 | fix(deposit-fields): 修复执行人无法填写保证金 4 字段的 regression | |
| !1524 | CO-458: 交付物和完成情况必填标识按场景区分 | |
| !1525 | fix(CO-465): 任务执行人提交审核后字段丢失——移除乐观更新 | |
| !1526 | fix: 项目详情页任务提交审核状态未更新 | |
| !1527 | fix(notification): 通知跳转空白页根因修复 | |
| !1528 | fix(CO-466): 项目档案下载/导出/预览接口权限注解过度收紧 | |
| !1529 | docs(release): 第 33 次部署报告 | |
| !1530 | fix(CO-443): 导航条结项「已完成」信号改为看 closure 审批状态 | |
| !1531 | fix(config): 统一生产环境配置占位符 | |
| !1532 | docs(release): 第 34 次部署报告 | |
| !1533 | fix(monitoring): P0 修复 SentryAppender crash-loop | ✅ 本次核心修复 |

## 改动范围

- **后端**：Sentry SDK 集成（修复）、审批接口契约统一、权限注解修复、配置占位符统一
- **前端**：通知跳转修复、项目立项字段简化、CA 编辑修复、导航条结项信号
- **Flyway 迁移**：无新增

## Flyway 预检结果（3 步法）

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 validate | ✅ VALIDATE OK - all checksums match（191 个迁移） |
| Step 2: DB 版本对比 | ✅ DB 最新 V1127，与源码一致（无新增迁移） |
| Step 3: remote-deploy 内置 validate | ✅ 通过 |

## 部署步骤

### 1. 本地打包 ✅
- `RELEASE_ID="0a057f757-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
- BUILD SUCCESS（25.566s）
- jar 内 190 个 Flyway 迁移文件，无重复版本

### 2. 上传 + 部署 ✅
- scp 上传成功
- remote-deploy.sh 执行：
  - Flyway validate 通过
  - JAR 覆盖成功
  - 前端切换：首次因 `/srv/www/xiyu-bid/` 权限失败（nginx:nginx），`sudo chown -R jetty:jetty` 修复后重试成功
  - 服务重启成功（13:54:09 CST）
  - 前端一致性验证通过（`assets/index-BV9XIthS.js`）

### 3. 健康检查 ✅
- **第 1 次检查通过**（无 Kafka 延迟，无 crash-loop）
- 证明 SentryAppender 修复有效

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
| jwt | ✅ UP (HMAC-SHA256, STRONG) |

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
| 前端 assets | `assets/index-BV9XIthS.js`（与 release 一致） |

## GitHub 镜像同步

- **部署前状态**：GitHub 镜像落后 Gitee
- **本次部署**：成功，Gitee main 有新 commit
- **后续处理**：待执行 `bash scripts/sync-to-github.sh` 同步镜像

## 临时配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 历史决定（运维监控需要） |
| `SHOW_DETAILS` / `DEBUG` / `TRACE` | 无新增 | 本次部署未引入临时配置 |

## 经验沉淀应用情况

本次部署应用了以下历史经验：
1. ✅ **Flyway 预检 3 步法**（第 1 条）—— 预检全通过
2. ✅ **Mac HTTP_PROXY 502 绕过**（第 16 条）—— 所有 curl 使用 `--noproxy '*'`
3. ✅ **systemctl sudo 权限**（第 8 条）—— `SYSTEMCTL_SUDO=true`
4. ✅ **前端目录权限**（第 13 条）—— 首次失败后 `sudo chown -R jetty:jetty /srv/www/xiyu-bid/` 修复
5. ✅ **Sentry Logback 集成修复**（第 17 条，本次新增）—— 移除 logback.xml 手动声明

## 新增经验教训（第 17 条，本次事故沉淀）

### Sentry Logback 集成必须在 prod profile 下测试

**事故**：PR !1519 Sentry Logback 集成在本地 dev profile 下测试通过，但 prod profile 下 `SentryAppender` 实例化失败导致后端 crash-loop。

**根因**：
- `logback-spring.xml` 手动声明 `SentryAppender`，但项目未引入 `sentry-logback` 依赖
- `sentry-spring-boot-starter-jakarta` **不包含** `sentry-logback` 模块
- logback 在 Spring 上下文之前加载，`SentryAppender` 类找不到，抛 `DynamicClassLoadingException`

**修复**：移除 logback.xml 中 SentryAppender 手动声明，依赖 sentry-spring-boot-starter-jakarta 自动配置。

**防复发措施**：
1. 涉及 logback.xml / logging 配置的 PR，必须在 prod profile 下验证启动
2. 引入新依赖时，确认依赖范围（是否包含所需模块）
3. 后续若需 ERROR 日志自动上报，单独引入 `sentry-logback` 依赖（Spring Boot Starter 会自动配置）

## 部署确认清单

| 检查项 | 结果 |
|---|---|
| 早操三连 | ✅ |
| 基线确认（HEAD = origin/main） | ✅ |
| Flyway 预检 3 步 | ✅ |
| 本地打包 | ✅ |
| 产物校验 | ✅ |
| 上传 + 部署 | ✅（首次权限失败，修复后成功） |
| 后端健康检查 | ✅ 第 1 次通过 |
| Smoke 测试 | ✅ 5 项全绿 |
| 前端一致性 | ✅ |
| GitHub 镜像同步 | ⏸ 待处理 |
| 临时配置清理 | ✅（无新增） |
| 部署报告 | ✅ 本报告 |

## 后续行动项

1. **P2 GitHub 镜像同步**：执行 `bash scripts/sync-to-github.sh`
2. **P2 沉淀第 17 条经验**：更新 xiyu-server-deploy skill 的 Key Lessons
3. **P3 评估引入 sentry-logback 依赖**：恢复 ERROR 日志自动上报（可选）

---

**部署结论**：✅ 第 35 次部署成功。SentryAppender crash-loop P0 事故已修复，PR !1518-!1533 的业务修复全部上线。生产服务 UP，Smoke 全绿。
