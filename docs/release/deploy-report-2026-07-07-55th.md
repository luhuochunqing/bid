# 第 55 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-07 16:05 CST |
| Release ID | `f2e8f8f0e-api8080` |
| 上一版本 | `dd4f79fae-api8080`（2026-07-07 13:35 CST 部署，第 54 次） |
| 部署类型 | 增量部署 + P0 事故修复（28 个 commit，无新 DB 迁移） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 79 次） |
| Readiness | ✅ UP（Kafka SDK 延迟约 2 分 38 秒后恢复，已知行为） |
| 部署耗时 | 约 3 分钟 |
| 特殊说明 | **本次部署包含第 36 次部署的 P0 事故修复** |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae-init` → `agent/trae/deploy-55th-report`（报告分支） |
| HEAD commit | `f2e8f8f0e` |
| 工作区状态 | 干净 |
| origin/main 同步 | ✅ HEAD = origin/main |
| GitHub 镜像 | ✅ 部署后已同步（两边 main = `f2e8f8f0e`） |

## P0 事故经过（第 36 次部署事故）

### 事故时间线

| 时间 (CST) | 事件 |
|---|---|
| 15:43 | 早操三连完成，sync-env.sh 确认基线 `077e6494b`（24 个 commit 待部署） |
| 15:44 | Flyway 预检 3 步法通过（validate OK, DB 已应用 V1145） |
| 15:45 | 本地打包成功（`077e6494b-api8080`，32.4s） |
| 15:46 | 上传 + remote-deploy.sh 开始执行 |
| 15:47:42 | 后端服务重启 |
| 15:47:49 | V1146 迁移应用到 DB（crash-loop 第一次启动时） |
| 15:51 | **crash-loop 开始**：ClassNotFoundException: com.alibaba.fastjson.JSONObject |
| 15:52 | remote-deploy.sh 健康检查 120 次失败，提示 crash-loop |
| 15:52 | **紧急回滚**到 `dd4f79fae-api8080`（恢复 jar + 重启） |
| 15:55 | 回滚后健康检查通过（第 77 次，约 2 分 34 秒） |
| 15:56 | Smoke 测试全绿，服务完全恢复 |
| 16:00 | PR !1812 创建（fastjson 依赖恢复修复） |
| 16:01 | PR !1812 合并 |
| 16:05 | **第 55 次部署成功**（`f2e8f8f0e-api8080`） |

### 事故根因

**PR !1802（commit `24f23ac50`）误删 fastjson 1.2.83 依赖**

PR !1802 标题：`chore(security): remove ghost dependency fastjson 1.2.83 [P2]`

PR !1802 的分析错误：
1. **源码 grep 看不到外部 jar 内部的 fastjson 使用** — `grep 'import com.alibaba.fastjson'` 只扫本仓源码，外部 jar 内部类不会出现
2. **mvn dependency:tree 未显示 eventlibrary 的传递依赖** — 分析结论"仅直接依赖,无传递"错误
3. **本地 dev profile 不加载 SDKClientConfiguration** — `mvn test` 全量通过 ≠ 生产能启动

实际依赖链：
```
com.ehsy.eventlibrary:ClientSDK:release_0.0.2
  └─ com.ehsy.eventlibrary.clientsdk.service.component.ClientRegisterComponent
      └─ 使用 com.alibaba.fastjson.JSONObject（fastjson 1.2.83）
```

prod profile 加载 `SDKClientConfiguration` → 实例化 `ClientRegisterComponent` → `ClassNotFoundException` → crash-loop。

### 事故影响

- 生产 crash-loop 约 5 分钟（15:51-15:55）
- 用户感知：服务 502 Bad Gateway
- 数据影响：无（V1146 是 ADD COLUMN NULL，向后兼容）
- 回滚措施：恢复上一版本 jar，服务恢复

### 修复措施

**PR !1812**：恢复 `com.alibaba:fastjson:1.2.83` 直接依赖，并加注释说明不可删除的原因和长期治理方向。

```xml
<!-- fastjson 1.2.83 是 com.ehsy.eventlibrary:clientsdk 的传递依赖（ClientRegisterComponent 使用 com.alibaba.fastjson.JSONObject）。
     PR !1802 误将其作为 ghost dependency 删除，导致生产 prod profile 启动 crash-loop（ClassNotFoundException）。
     本地 dev profile 不加载 SDKClientConfiguration，因此 mvn test 无法暴露此问题。
     删除前必须先升级 eventlibrary 到不依赖 fastjson 的版本，或在本仓提供 fastjson2 兼容类。 -->
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
    <version>1.2.83</version>
</dependency>
```

## 增量 PR 列表（28 个 commit，`dd4f79fae..f2e8f8f0e`）

| Commit | PR | 描述 |
|---|---|---|
| `f2e8f8f0e` | !1812 | fix(pom): 恢复 fastjson 1.2.83 依赖——PR !1802 误删导致生产 crash-loop [P0 事故修复] ★ |
| `c24ff7848` | - | fix(pom): 恢复 fastjson 1.2.83 依赖——PR !1802 误删导致生产 crash-loop |
| `a88b1d84c` | !1810 | fix(naming): L-01 构件命名收敛 POC 为正式命名（artifactId: bid-poc → bid-platform） |
| `077e6494b` | !1811 | chore(CO-464): 补 U1146 回滚脚本 source header |
| `7216a5a19` | - | chore(CO-464): 补 U1146 回滚脚本 source header |
| `52c43ea30` | - | fix(CO-501): 修复 Code Review 发现的 4 个问题 |
| `5d4cda1c3` | - | feat(CO-464,CO-500,CO-501): Tender 新增 purchaserId + 关联商机两步校验 |
| `f97d9603c` | !1808 | fix(build): L-02 统一包管理器为 pnpm，删除 package-lock.json |
| `811cd3c3b` | !1807 | fix(CO-526): CRM 返回多个同 position 对接人导致 uk_eval_role_info 唯一约束冲突 [Sentry XIYU-X] |
| `bb2941a09` | - | fix(build): L-02 统一包管理器为 pnpm |
| `631796ed3` | !1804 | test(account): CO-534 补充我的审批列表排序回归测试 |
| `1ad7cd435` | - | fix(CO-526): CRM 同 position 多对接人冲突修复 |
| `1495e3344` | !1806 | fix(security): L-03 重命名 .env.api 为 .env.example 并补 .gitignore |
| `870408984` | - | refactor(test): CO-534 测试上移到 Service 层端到端验证 |
| `4f1666daf` | - | fix(security): L-03 重命名 .env.api 为 .env.example |
| `ff529aa56` | !1803 | fix(account): CO-522 编辑表单不主动提交未修改的密码字段 |
| `50b34b3fc` | !1800 | fix(CA): CO-515 CA详情页借用记录空数据展示表头 + 操作日志表格化 + 字段diff详情 |
| `fb28d3a1b` | !1801 | docs(release): 第 54 次部署报告 |
| `c53deef0f` | !1805 | fix(margin): Sentry XIYU-T ClassCastException——mapRow 日期列禁止强转 Timestamp |
| `24504e3ba` | !1802 | chore(security): remove ghost dependency fastjson 1.2.83 [P2] ❌ **事故根因** |
| `642bb1074` | - | fix(margin): Sentry XIYU-T ClassCastException 修复 |
| `4f18dfa4d` | - | test(account): CO-534 补充 Repository 排序回归测试 |
| `0d17ab4e6` | - | fix(CA): CO-515 操作日志详情按"字段名：旧值 -> 新值"格式展示 |
| `ee760ee74` | - | fix(account): CO-522 编辑表单不主动提交未修改密码 |
| `24f23ac50` | - | chore(security): remove ghost dependency fastjson 1.2.83 ❌ **事故根因** |
| `d6d326173` | - | docs(release): 第 54 次部署报告 |
| `bb946d47f` | - | fix(CA): CO-515 借用记录空数据展示表头 + 操作日志改为表格形式 |

## 改动范围

**核心业务变更**（7 个功能模块）：

### 1. fastjson 依赖恢复（!1812）★ P0 事故修复
- **问题**：PR !1802 误删 fastjson 1.2.83，导致生产 crash-loop
- **修复**：恢复依赖 + 注释说明不可删除原因
- **影响文件**：`backend/pom.xml`

### 2. 构件命名收敛（!1810，L-01）
- **变更**：`artifactId` 从 `bid-poc` 改为 `bid-platform`
- **影响**：jar 名从 `bid-poc-1.0.3.jar` 变为 `bid-platform-1.0.3.jar`
- **部署脚本兼容**：`remote-deploy.sh` 用固定名 `app.jar`，不受影响

### 3. Tender 招标主体字段（CO-464/CO-500/CO-501）
- **变更**：Tender 新增 `purchaserId` + 关联商机两步校验
- **迁移**：V1146（ADD COLUMN purchaser_id BIGINT NULL，已在第 36 次部署时应用到 DB）
- **影响文件**：`V1146__add_tender_purchaser_id.sql`、`U1146__add_tender_purchaser_id.sql`、`Tender.java`、`TenderCommandService.java`、`TenderCrmLinkPersistService.java`、`CrmTenderSubjectChecker.java`、`TenderSubjectConsistencyPolicy.java`

### 4. CRM 同 position 多对接人冲突修复（!1807，CO-526，Sentry XIYU-X）
- **问题**：CRM 返回多个同 position 对接人导致 `uk_eval_role_info` 唯一约束冲突
- **修复**：`CrmEvaluationMapper` 去重处理

### 5. Margin ClassCastException 修复（!1805，Sentry XIYU-T）
- **问题**：`MarginQuerySupport.mapRow` 日期列强转 Timestamp 失败
- **修复**：使用防御性 `toLdt()` 方法
- **测试**：新增 `MarginSqlDateCoercionContractTest`

### 6. 平台账号模块（!1803，CO-522）
- **问题**：编辑表单不主动提交未修改的密码字段
- **修复**：`AccountFormDialog.vue` 改用 `passwordChanged` 标志

### 7. CA 详情页（!1800，CO-515）
- **变更**：借用记录空数据展示表头 + 操作日志表格化 + 字段 diff 详情
- **新增**：`CaFieldDiffCalculator.java`（纯核心计算字段差异）

### 8. 工程治理
- **!1808（L-02）**：统一包管理器为 pnpm，删除 package-lock.json
- **!1806（L-03）**：重命名 `.env.api` 为 `.env.example` 并补 `.gitignore`
- **!1804（CO-534）**：补充我的审批列表排序回归测试

## Flyway 预检结果

| 步骤 | 结果 |
|---|---|
| Step 1: validate | ✅ VALIDATE OK - all checksums match（210 migrations） |
| Step 2: DB 版本对比 | ✅ DB 已应用 V1145（第 54 次）+ V1146（第 36 次事故时应用） |
| Step 3: remote-deploy 内置 | ✅ VALIDATE OK - all checksums match（210 migrations） |

**无新迁移需应用**（V1146 已在第 36 次部署 crash-loop 期间应用到 DB，向后兼容）。

## 部署步骤

1. ✅ 早操三连（sync-env.sh + check-git-wrapper.sh）
2. ✅ 确认基线（HEAD = `f2e8f8f0e`，git status 干净）
3. ✅ 服务器现状检查（deployed-release.json = `dd4f79fae-api8080`，health UP）
4. ✅ Flyway 预检 3 步法
5. ✅ 本地打包（`RELEASE_ID=f2e8f8f0e-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`，29.2s）
6. ✅ 产物校验（jar 内 fastjson-1.2.83.jar 存在，ClientSDK-release_0.0.2.jar 存在，Flyway 迁移无重复）
7. ✅ 上传 + 部署（remote-deploy.sh，SYSTEMCTL_SUDO=true）
8. ✅ 健康检查（79 次通过，约 2 分 38 秒）
9. ✅ 前端一致性验证（`assets/index-DUpDchEp.js`）
10. ✅ Smoke 测试
11. ✅ GitHub 镜像同步（两边 main = `f2e8f8f0e`）
12. ✅ 本地任务分支清理

## 验证结果

### 健康检查

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

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 UP | 200 UP | ✅ |
| `GET /actuator/health/readiness` | 200 | 200 | ✅ |
| `POST /api/auth/login` (空 body) | 400 | 400 | ✅ |
| `GET /api/projects` | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| `GET /` (前端) | 200 | 200 | ✅ |
| `GET /login` (前端) | 200 | 200 | ✅ |

### deployed-release.json

```json
{
  "releaseId": "f2e8f8f0e-api8080",
  "activatedAt": "2026-07-07T08:05:47Z",
  "packageMetadata": {
    "jarName": "bid-platform-1.0.3.jar",
    "sentryEnabled": false
  }
}
```

## GitHub 同步

| 项目 | 值 |
|---|---|
| Gitee main | `f2e8f8f0e` |
| GitHub main | `f2e8f8f0e` |
| 状态 | ✅ 完全一致 |

## 回滚信息

| 项目 | 值 |
|---|---|
| 回滚状态 | ✅ Ready（无需执行） |
| 上一版本 jar | `/opt/xiyu-bid/releases/dd4f79fae-api8080/backend/app.jar` |
| 上一版本 release ID | `dd4f79fae-api8080` |
| 回滚命令 | `cp /opt/xiyu-bid/releases/dd4f79fae-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |
| DB 回滚 | 不需要（V1146 是 ADD COLUMN NULL，向后兼容） |

## 经验沉淀应用情况

本次部署应用了以下历史经验：

1. **Flyway 预检 3 步法**（第 6 次事故沉淀）— ✅ 部署前执行，通过
2. **Kafka SDK readiness 延迟**（第 8/9/10/13/15 次沉淀）— ✅ 2 分 38 秒恢复，已知行为
3. **Smoke 测试 400/403/401 替代验证**（第 6-15 次沉淀）— ✅ 全绿
4. **GitHub 镜像同步检查**（第 13 次起标准步骤）— ✅ 部署后同步
5. **生产前端同源构建**（`VITE_API_BASE_URL=`）— ✅ 打包正确
6. **SYSTEMCTL_SUDO=true**（第 15 次事故沉淀）— ✅ 服务重启成功
7. **Mac HTTP_PROXY 502 绕过**（第 19/23 次沉淀）— ✅ 使用 `--noproxy '*'`
8. **紧急回滚纪律**（第 34 次 SentryAppender 事故沉淀）— ✅ 5 分钟内回滚恢复

## 新增经验沉淀（第 18 条）

### 18. 删除 pom.xml 依赖必须验证传递依赖使用（第 36 次事故，PR !1802 引入）

删除 `pom.xml` 中任何依赖前，必须验证：

1. **`mvn dependency:tree -Dincludes=<groupId>:<artifactId>`** 确认无传递依赖
2. **检查外部 jar 内部使用**：`unzip -l <external-jar> | grep -i fastjson` 或 `javap -classpath <external-jar> <class-name>`
3. **prod profile 启动验证**：本地用 prod profile 启动测试（即使 DB 连接失败，logback 应能加载）

**PR !1802 的教训**：
- "源码 grep 0 处" ≠ "无使用"（外部 jar 内部类不会出现在源码 grep 中）
- `mvn dependency:tree` 可能不显示外部 jar 的传递依赖
- 本地 dev profile 不加载所有 bean（`SDKClientConfiguration` 仅在 prod profile 激活）
- **"ghost dependency" 判定必须验证运行时类加载，不能只看编译时依赖分析**

**防复发措施**：
1. 删除 `pom.xml` 依赖时，必须在 prod profile 下验证启动（本地 dev profile 不够）
2. 删除前用 `unzip -l <jar> 'BOOT-INF/lib/*.jar'` 确认 jar 内依赖关系
3. 外部 SDK jar（`eventlibrary`、`ehsy-*`）的传递依赖要特别警惕
4. 新增 pre-push 门禁建议：删除 `pom.xml` 依赖时强制 prod profile 启动测试

**历史出现**：第 36 次（首次发现，crash-loop 5 分钟，已回滚）→ 第 55 次（修复后部署成功）。

## 风险提示

1. **fastjson 1.2.83 已知 CVE**：本次恢复是临时修复，长期需升级 eventlibrary 或迁移到 fastjson2
2. **V1146 已应用但代码未使用**：`purchaser_id` 列已在 DB 中，但旧 jar 不引用该列；新 jar 已包含使用逻辑
3. **artifactId 改名**：`bid-poc` → `bid-platform`，`deployed-release.json` 中 `jarName` 已更新，不影响功能

## 部署确认清单

- [x] 早操三连完成
- [x] 基线确认（HEAD = origin/main）
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功
- [x] 产物校验（fastjson 依赖恢复确认）
- [x] 上传 + 部署成功
- [x] 健康检查通过
- [x] Smoke 测试全绿
- [x] GitHub 镜像同步
- [x] 本地任务分支清理
- [x] 部署报告生成

## 备注

本次部署是第 36 次部署事故的修复部署。第 36 次部署（`077e6494b-api8080`）因 PR !1802 误删 fastjson 依赖导致 crash-loop，已紧急回滚到 `dd4f79fae-api8080`。PR !1812 恢复 fastjson 依赖后，本次第 55 次部署（`f2e8f8f0e-api8080`）成功上线，包含第 36 次部署的所有业务变更（28 个 commit）+ fastjson 修复。
