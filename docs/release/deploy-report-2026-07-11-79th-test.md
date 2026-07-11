# 第 79 次测试环境部署报告

> **环境**：测试（test）
> **部署类型**：增量升级部署
> **部署日期**：2026-07-11
> **Release ID**：`dfcfe5545-api8080`
> **部署状态**：✅ 成功

---

## 1. 部署概览

| 项目 | 值 |
|------|-----|
| 环境 | 测试（test） |
| 服务器 | `172.16.38.78`（winbid-01） |
| Release ID | `dfcfe5545-api8080` |
| 部署时间 | 2026-07-11 13:24:45 CST |
| 健康检查通过 | 2026-07-11 13:28 CST（Kafka SDK readiness 延迟约 4 分钟，已知行为） |
| 服务状态 | active (running) |
| 部署次数 | 第 79 次（测试环境） |
| 前一次部署 | `e857e37ef-api8080`（2026-07-11 03:25 UTC，!2016 半关联修复） |

---

## 2. 基线信息

| 项目 | 值 |
|------|-----|
| 仓库 | `gitee.com:allinai888/bid.git` |
| 分支 | `origin/main`（部署基线 `dfcfe5545`） |
| HEAD commit | `dfcfe5545`（!2018 fix(crm): 修复 PR !2011 字段分离回归导致去重校验失效（CO-277 纯数字 id 误存为 code）） |
| 前端构建模式 | 同源构建（`VITE_API_BASE_URL=` 空） |
| 后端 profiles | `prod,mysql` |
| 后端端口 | `8080`（Nginx 反代） |
| 数据库 | `xiyu_bid_main` @ `winbid-01.test.rds.ehsy.com:3306` |
| 增量 commit 数 | 2 |
| 增量 PR 数 | 1（!2018） |

---

## 3. 改动范围

### 3.1 增量 PR 列表（1 个 PR）

| PR | 类型 | 说明 |
|----|------|------|
| !2018 | fix | 修复 PR !2011 字段分离回归导致去重校验失效（CO-277 纯数字 id 误存为 code） |

### 3.2 改动主题

**PR !2011 字段分离回归修复（!2018）**：

- **根因**：PR !2011 字段分离重构时，新增 `tender.setCrmOpportunityId(crmOpportunityCode)` 直接存入 code，遗漏了 CO-277 的"id 反查 code"语义——CRM 推送方把商机主键 id（纯数字如 21364）放在 `crmOpportunityId` 字段传输，不是 CC 格式编号。导致同一商机的两条标讯 `crm_opportunity_id` 格式不一致（纯数字 id vs CC 格式 code），去重校验 `findByCrmOpportunityId("CC...")` 查不到纯数字 id 的记录，去重失效。
- **决定性证据**：tender 1646（CRM 推送创建）`crm_opportunity_id=21364`（纯数字 id），tender 1648（"关联标讯"按钮创建）`crm_opportunity_id=CC20260711739`（CC 格式 code），同一商机去重失效。
- **三道防线**：
  1. **入口层拦截**：`CrmTenderLinkService.applyCrmLinkAndAssignment` + `TenderIntegrationCommandSupport.applyCrmFallback` — 纯数字 code 不直接存入，用 chanceId 反查 CC 编号后落库；已有 CC 编号不被纯数字覆盖
  2. **监控告警**：`TenderCrmOccupancyChecker` — 检测到纯数字 `crmOpportunityId` 时记录 WARN 日志，便于及时发现 CRM 推送字段语义回归
  3. **自动化测试**：6 个新回归测试覆盖纯数字 id 场景（反查成功/反查失败不存入/API 异常不存入 + 已有 CC 编号不被覆盖）
- **知识沉淀**：`docs/lessons/crm-integration-lessons.md` 新增第 8 节，记录"字段分离重构必须还原原有语义的完整链路"教训；`CrmTenderLinkService` 类注释补强 CO-277 字段语义警告

### 3.3 Flyway 迁移

无新增迁移（本次 PR 无 DB schema 变更）。DB 最新版本 V1164 与源码一致。

### 3.4 改动文件清单

| 文件 | 类型 | 说明 |
|------|------|------|
| `CrmTenderLinkService.java` | fix | 纯数字 code 不直接存入，反查 CC 编号；新增 `isCcFormatCode`/`isNumericId` 辅助方法 |
| `TenderIntegrationCommandSupport.java` | fix | `applyCrmFallback` 同步加纯数字校验 |
| `TenderCrmOccupancyChecker.java` | fix | 增加纯数字 `crmOpportunityId` WARN 日志监控 |
| `CrmTenderLinkServiceTest.java` | test | 3 个新测试覆盖纯数字 id 场景 |
| `TenderIntegrationCommandSupportTest.java` | test | 2 个新测试覆盖 `applyCrmFallback` |
| `TenderCrmOccupancyCheckerTest.java` | test | 1 个新测试验证纯数字值仍执行查询便于监控 |
| `docs/lessons/crm-integration-lessons.md` | docs | 新增第 8 节知识沉淀 |

---

## 4. Flyway 预检

### 4.1 服务器 Flyway validate

```
VALIDATE OK - all checksums match
Successfully validated 227 migrations (execution time 00:00.091s)
```

### 4.2 DB 已应用版本（最新 5 个）

| version | description | success | installed_on |
|---------|-------------|---------|--------------|
| 1164 | lock oss user local passwords | 1 | 2026-07-10 21:13:25 |
| 1163 | add operator username to webhook delivery tasks | 1 | 2026-07-10 18:23:46 |
| 1162 | add margin permission to bid specialist | 1 | 2026-07-10 12:22:43 |
| 1161 | ca related platforms text | 1 | 2026-07-09 18:15:12 |
| 1160 | platform account password nullable | 1 | 2026-07-09 17:37:20 |

### 4.3 remote-deploy 内置 validate

部署过程中 `remote-deploy.sh` 自动执行 Flyway validate，通过后才激活新 jar。

---

## 5. 部署步骤

1. ✅ 环境门禁确认：测试环境 `172.16.38.78`
2. ✅ 早操三连：`dev-env.sh` + `sync-env.sh` + `check-git-wrapper.sh`
3. ✅ 锚点同步：`agent/trae-init` ff-only 到 `dfcfe5545`（origin/main）
4. ✅ 服务器现状探测：前一次部署 `e857e37ef-api8080`，health UP
5. ✅ Flyway 预检 3 步：validate OK + DB 版本对比一致 + remote-deploy 内置 validate
6. ✅ 本地打包：`RELEASE_ID="dfcfe5545-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh`
7. ✅ 产物校验：226 个迁移文件无重复版本，最新 V1164，前端入口 `assets/index-CiPPyhC3.js`
8. ✅ 上传 + 部署：scp archive + `remote-deploy.sh`（`SYSTEMCTL_SUDO=true`）
9. ✅ 健康检查：等待 4 分钟后 UP（Kafka SDK readiness 延迟，已知行为）
10. ✅ Smoke 测试：全通过
11. ✅ GitHub 镜像同步：两边 main 完全一致 `dfcfe5545`
12. ✅ 配置清理检查：仅 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`（历史决定保留）

---

## 6. 验证结果

### 6.1 健康检查

```
health: HTTP 200 (UP)
readiness: HTTP 200 (UP)
```

后端所有组件 UP：aiProvider、db、diskSpace、jwt、livenessState、ping、readinessState、redis、sidecar。

### 6.2 Smoke 测试

| 接口 | HTTP Code | 预期 |
|------|-----------|------|
| `/actuator/health` | 200 | UP |
| `/actuator/health/readiness` | 200 | UP |
| `POST /api/auth/login`（空 body） | 400 | 验证错误 |
| `GET /api/projects` | 403 | 需认证 |
| `GET /api/integration/crm/health` | 401 | 需认证 |
| 前端 `/` | 200 | - |
| 前端 `/login` | 200 | - |

前端入口 `assets/index-CiPPyhC3.js` 与 release 一致。

### 6.3 健康检查延迟

- 后端重启时间：13:24:45 CST
- 健康检查通过：约 13:28 CST
- 延迟约 4 分钟：`OrganizationEventSdkKafkaStarter` 初始化阻塞主线程（已知行为，第 8/9/10/13/15 次均出现）
- `remote-deploy.sh` 120 次健康检查未通过，但服务实际已正常，Kafka broker 可达后自恢复

---

## 7. GitHub 镜像同步

| 仓库 | HEAD |
|------|------|
| Gitee main | `dfcfe5545647e4f432137123f7227adbc7efd7c8` |
| GitHub main | `dfcfe5545647e4f432137123f7227adbc7efd7c8` |

两边 main 完全一致。

---

## 8. 回滚信息

| 项目 | 值 |
|------|-----|
| 回滚方式 | 恢复前一次 jar |
| 前一次 release | `e857e37ef-api8080` |
| 前一次 jar | `/opt/xiyu-bid/releases/e857e37ef-api8080/backend/app.jar` |
| 回滚命令 | `cp /opt/xiyu-bid/releases/e857e37ef-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| 数据库回滚 | 本次无 DB schema 变更，无需数据库回滚 |

---

## 9. 经验沉淀应用情况

### 9.1 本次应用的经验

| 经验 | 应用 |
|------|------|
| Flyway 预检 3 步法（经验 1） | ✅ 服务器 validate + DB 版本对比 + remote-deploy 内置 |
| Readiness 延迟恢复（经验 2） | ✅ Kafka SDK 延迟 4 分钟，未急于回滚，自恢复 |
| 生产前端同源构建（经验 3） | ✅ `VITE_API_BASE_URL=` 显式设空 |
| Smoke 测试限制（经验 4） | ✅ Admin 密码未知，用 400/403/401 替代验证 |
| GitHub 镜像同步（经验 5） | ✅ 部署后同步，两边 main 一致 |
| Mac HTTP_PROXY 502（经验 16） | ✅ curl 统一加 `--noproxy '*'` |

### 9.2 本次新增经验

**CRM 字段语义分离回归教训**（已沉淀到 `docs/lessons/crm-integration-lessons.md` 第 8 节）：

- 字段分离重构必须还原原有语义的完整链路
- CO-277 的"id 反查 code"语义是隐式契约，PR !2011 字段分离时遗漏了这层语义
- 防复发三道防线：入口层拦截 + 监控告警 + 自动化测试
- 与第 6 节（CO-276 字段名不匹配）和第 7 节（CO-277 字段语义不匹配）形成"同一字段三道独立断点"知识链

---

## 10. 风险提示

1. **Kafka SDK readiness 延迟**：后端重启后约 4 分钟 readiness 才恢复 UP。这是已知行为，不影响业务功能。若需根治，考虑将 `OrganizationEventSdkKafkaStarter.onApplicationReady()` 改为 `@Async` 或独立线程池执行。
2. **`remote-deploy.sh` 120 次健康检查未通过**：由于 Kafka SDK 延迟，部署脚本的健康检查未在 120 次（4 分钟）内通过。但服务实际已正常，手动确认 health UP 后可忽略。后续可考虑增加健康检查重试次数或调整 Kafka SDK 初始化逻辑。
3. **历史数据未修复**：tender 1646 的 `crm_opportunity_id=21364`（纯数字 id）仍保留在数据库中，本次修复仅影响新数据。如需清理历史数据，需单独执行 SQL 修复（用户已决定不修复历史数据）。
4. **`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 保留**：测试环境保留此配置便于排障（第 13/14/15 次决定保留）。生产环境应关闭。

---

## 11. 部署确认清单

- [x] 环境门禁确认（测试环境）
- [x] 早操三连执行
- [x] 锚点 ff-only 同步到 origin/main
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（release `dfcfe5545-api8080`）
- [x] 产物校验通过（226 迁移无重复，前端入口一致）
- [x] 上传 + 部署成功
- [x] 健康检查 UP（延迟 4 分钟，已知行为）
- [x] Smoke 测试全通过
- [x] GitHub 镜像同步完成
- [x] 配置清理检查通过
- [x] 部署报告生成

---

## 12. 部署元数据

| 项目 | 值 |
|------|-----|
| 部署开始 | 2026-07-11 13:13:57 CST（PR !2018 创建） |
| 部署完成 | 2026-07-11 13:28:00 CST（health UP） |
| 总耗时 | 约 14 分钟（含 PR 合并 + 打包 + 上传 + 部署 + 健康检查） |
| 部署人 | Trae Agent（自动化） |
| 验证人 | Trae Agent（自动化 smoke） |
