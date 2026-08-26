# 第 144 次部署报告（test 环境）

**日期**：2026-08-26
**环境**：test（172.16.38.78 / winbid-01）
**Release ID**：`4dd9aa21a`
**部署时间**：19:29 - 19:42
**状态**：✅ 成功

## 基线信息

| 项目 | 值 |
|------|-----|
| 基线 commit | `4dd9aa21a`（main） |
| 前次部署 | `4d1f78b25-fix1`（2026-08-26 15:56，143rd + @Primary 热修复） |
| 增量 commit | 27 个 |
| 新增迁移 | 无（V1202/V1203 已随 143rd 应用） |
| 工作区状态 | 干净 |

## 改动范围（27 个增量 commit / 6 个 PR）

| PR | 描述 |
|----|------|
| !2423 | **fix(competitionintel): CompetitorStatus 显式 varchar 映射修复 schema validate 启动失败（本次部署 P0 拦截，见下方）** |
| !2422 | fix(tenderevent): 直调 SDK httpPost 落原始响应体日志，定位事件总线推送失败原因 |
| !2421 | fix(score-parse): 修复重新解析遮罩提示漂出可视区 |
| !2420 | fix(scoreparse): 多层纵深防御治理大数击穿数据库字段（防 Data truncation 异常） |
| !2419 | feat(data-analysis): M1/M4 筛选区竞品下拉接入动态字典口径（specs/056） |
| !2418 | fix(tenderevent): add @Primary to resolve ServiceRegisterProperties bean conflict（143rd 热修复合入 main） |

## 🚨 P0 拦截事件：CompetitorStatus varchar/enum 校验冲突

### 拦截时机

本次打包阶段（`package-release.sh` 内置 `FlywayMysqlContainerTest`，Testcontainers 全量回放 B73~V1203）拦截到启动失败，**避免了带病产物上服务器 crash-loop**（与 143rd 的 Bean 冲突事故形成对照：那次跳过了容器测试）。

### 根因

PR !2414 引入的 `CompetitorStatus` 枚举字段：

- V1202 迁移建列为 `varchar(20)`
- 实体 `@Enumerated(EnumType.STRING)` 但未显式声明列类型
- Hibernate 6.x MySQL 方言默认将 STRING 枚举校验为原生 `ENUM(...)` 列定义
- prod/e2e/flyway-mysql 三 profile 均 `ddl-auto: validate` → schema validate 失败 → 启动即崩

dev/H2 环境全绿、只在真实 MySQL + validate 下暴露。

### 修复

[Competitor.java](file:///Users/user/xiyu/worktrees/trae/backend/src/main/java/com/xiyu/bid/competitionintel/entity/Competitor.java) 显式对齐列类型：

```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
private CompetitorStatus status = CompetitorStatus.ACTIVE;
```

已发布迁移不可变，只能改实体侧对齐。验证：`FlywayMysqlContainerTest` 4/4 PASS。

### 知识沉淀

- lessons §165：`docs/lessons/lessons-learned.md`
- wiki 新页：`.wiki/pages/hibernate-string-enum-varchar-validate.md`（含存量排查建议：grep 所有 `@Enumerated(EnumType.STRING)` 字段核对迁移列类型）

## 打包

```bash
RELEASE_ID="4dd9aa21a" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
  bash scripts/release/package-release.sh
```

### 产物校验

| 校验项 | 结果 |
|---|---|
| `release-metadata.json` 中 `obsEnabled` | `true` ✅ |
| `release-metadata.json` 中 `apiBaseUrl` | `""`（同源构建）✅ |
| `release-metadata.json` 中 `jarName` | `bid-platform-1.0.3.jar` ✅ |
| jar 内 Flyway 迁移文件 | 无重复 ✅ |
| OBS 直传 Detail chunk `.upload(` 调用数 | 2（未被 tree-shake）✅ |
| 包大小 | 154M |

## 部署步骤

| 步骤 | 结果 | 备注 |
|------|------|------|
| 0. 环境门禁 | ✅ | 用户确认 test 环境 |
| 1. 早操三连 | ✅ | dev-env + sync-env + git-wrapper |
| 2. Flyway 预检 | ✅ | 本地无新增迁移；DB 已到 V1203 |
| 3. P0 修复闭环 | ✅ | hotfix 分支 → PR !2423 合入 main → 重新打包 |
| 4. 上传 | ✅ | 归档 + remote-deploy.sh → /opt/xiyu-bid/incoming/ |
| 5. remote-deploy.sh | ✅ | 内置 validate 264 migrations checksums 全匹配 |
| 6. 服务重启 | ✅ | 19:35:27 CST active (running)，PID 11830 |
| 7. 健康检查 | ✅ | consecutive 3/3，total attempts 80（约 2.7 分钟） |
| 8. Smoke 测试 | ✅ | 6/6 通过（见下表） |
| 9. 前端资源保留 | ✅ | 从 4d1f78b25-fix1 cp -rn，assets 共 273 条目 |
| 10. 配置清理检查 | ℹ️ | 仅 MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always（历史保留） |
| 11. GitHub 同步 | ✅ | Gitee main → GitHub main（17 commit 补齐） |

## 验证结果

### API Smoke（经 Nginx 8080）

| # | 接口 | 期望 | 实际 | 结果 |
|---|------|------|------|------|
| 1 | `GET /actuator/health` | 200 UP | HTTP 200 UP（db/redis/sidecar/jwt/diskSpace/aiProvider 全 UP） | ✅ |
| 2 | `POST /api/auth/login`（空 body） | 400 | HTTP 400 | ✅ |
| 3 | `GET /api/projects`（无认证） | 403 | HTTP 403 | ✅ |
| 4 | `GET /api/integration/crm/health`（无认证） | 401 | HTTP 401 | ✅ |

### 前端验证（经 Nginx 8080）

| 检查项 | 结果 |
|--------|------|
| `GET /` | 200 ✅ |
| `GET /login` | 200 ✅ |
| index.html JS 入口 | `assets/index-C_OsENUC.js`（与新 release 一致）✅ |

### DB 备份

`/opt/xiyu-bid/db-backups/winbid-4dd9aa21a-20260826193518.sql.gz`（11.6M）✅

> ⚠️ **顺带发现**：143rd 的备份文件 `winbid-4d1f78b25-fix1-20260826155558.sql.gz` 仅 **20 bytes**（空 gzip），说明当时 mysqldump 实际失败但脚本未拦截。建议后续给 remote-deploy.sh 增加"备份文件 < 1KB 视为失败"守卫。

## 回滚预案

```bash
ssh jetty@172.16.38.78 'sudo cp /opt/xiyu-bid/releases/4d1f78b25-fix1/backend/app.jar \
  /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend'
```

回滚风险评估：低（本次无 Flyway 迁移，纯代码回滚）。

## 完成清单

- [x] API Smoke 测试通过
- [x] 前端页面验证通过
- [x] Flyway validate 通过（264 migrations，无新增应用）
- [x] P0 修复 PR !2423 已合入 main 并随本次部署上线
- [x] GitHub 镜像同步完成
