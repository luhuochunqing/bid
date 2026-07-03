# 第 38 次生产部署报告

**部署时间**：2026-07-03 15:04 - 15:10 (CST)
**部署人**：AI Agent (Trae)
**服务器**：winbid-01.test (172.16.38.78)
**Release ID**：`a57c1bc58-api8080`
**结果**：✅ 成功（含 main 分支编译修复 + 健康检查延迟恢复，Kafka SDK 已知行为）

---

## 1. 部署概览

| 项目 | 值 |
|---|---|
| 目标服务器 | `172.16.38.78` (winbid-01.test) |
| SSH 用户 | `jetty` |
| App Root | `/opt/xiyu-bid` |
| Backend Port | `8080` |
| DB Name | `xiyu_bid_main` |
| Release ID | `a57c1bc58-api8080` |
| Commit | `a57c1bc58` + 编译修复（commit `7872df94e`） |
| 前端构建模式 | 同源构建 (`VITE_API_BASE_URL=`) |
| 新增迁移 | 无 |
| 上一部署 | `5f730424f-api8080`（2026-07-03 10:46 CST，第 36 次） |
| 增量 commit | 61 个（PR !1592-!1618） |
| 部署耗时 | ~6 分钟（含 3 分钟健康检查等待） |

> **⚠️ 序号说明**：7月2日已有第 37 次部署（`665dd3abb`），7月3日上午的第 36 次部署报告序号标错（应为 38th）。本次部署按时间顺序为第 38 次。

---

## 2. 基线信息

### 2.1 Git 状态

- **早操三连**：source dev-env.sh + sync-env.sh + check-git-wrapper.sh ✅
- **当前分支**：`agent/trae/bid-import-template-dropdown`（HEAD = origin/main `a57c1bc58`）
- **基线**：HEAD = `a57c1bc58` = origin/main（部署时）
- **GitHub 镜像**：部署前落后 Gitee 59 个 commit；部署后同步被 ArchitectureTest 阻塞，仍落后 67 个 commit
- **本地门禁**：7/7 通过

### 2.2 服务器部署前状态

| 项目 | 值 |
|---|---|
| 已部署 Release | `5f730424f-api8080` |
| 激活时间 | 2026-07-03T02:46:46Z (10:46 CST) |
| 健康状态 | UP（所有组件正常） |
| DB 最新迁移 | V1130 (personnel education start date nullable) |

---

## 3. ⚠️ main 分支编译修复（部署前紧急处理）

### 3.1 问题

打包时发现 main 分支编译失败：
```
ProjectDocumentUploadWorkflowService.java:[54,28] cannot find symbol
  symbol:   method getUploaderName()
  location: variable created of type com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO
```

### 3.2 根因

- `ProjectDocumentDTO` 字段为 `uploader`（非 `uploaderName`）
- `ProjectDocumentViewAssembler` 将 entity.uploaderName → dto.uploader
- `ProjectDocumentUploadWorkflowService.java:54` 调用了不存在的 `created.getUploaderName()`
- 该问题由 CO-488 相关 PR 合入时遗漏字段名适配导致

### 3.3 修复

```java
// 修复前（第 54 行）
created.getUploaderName()

// 修复后
created.getUploader()
```

- 修复已由自动化流程提交为 commit `7872df94e fix(projectworkflow): 修复 ProjectDocumentUploadWorkflowService 预存编译错误`
- **风险提示**：main 分支编译失败说明该 PR 合入时未执行本地编译验证，CI 可能未跑或被跳过

---

## 4. PR 列表（!1592-!1618，共 27 个 PR）

| PR | 描述 | 类型 |
|---|---|---|
| !1618 | 权限治理战役总结（50 契约测试 + 2 越权修复 + 4 业务确认） | 审计/修复 |
| !1617 | CO-488 项目档案详情抽屉上传人列显示不正确且列宽不足 | 修复 |
| !1616 | fix(project-detail) 403 导致页面崩溃 [Spec Kit 026] | 修复 |
| !1615 | CA信息管理新增证书状态筛选 | 功能 |
| !1614 | CO-481 项目导出权限修复 | 修复 |
| !1613 | fix(qualification) ZIP 附件重名 ZipException (Sentry) | 修复 |
| !1612 | fix(personnel) PersonnelValidator NPE (Sentry) | 修复 |
| !1610 | 项目 2.4-2.7 评标/结果/复盘/结项审计 | 测试 |
| !1609 | 排查任务分配消息 Bug | 修复 |
| !1608 | 批量下载资质证书附件报错 | 修复 |
| !1607 | 项目 2.3 标书制作审计 | 测试 |
| !1606 | CO-479 CA信息管理关联平台字段 | 修复 |
| !1605 | fix(warehouse) 批量导入日期格式修复 | 修复 |
| !1604 | 项目 2.2 立项契约测试 | 测试 |
| !1603 | fix(rate-limit) CO-478 切换菜单页请求频繁 | 修复 |
| !1602 | fix(co-469) 异步任务异常捕获 | 修复 |
| !1601 | fix(margin) CO-480 保证金项目负责人字段 | 修复 |
| !1600 | CO-481 保证金缴纳任务支持修改执行人 | 功能 |
| !1599 | 项目 2.1 列表审计 | 测试 |
| !1598 | 2.4 补充功能契约测试 | 测试 |
| !1597 | fix(project) 结项时校验任务完成状态 | 修复 |
| !1596 | Gap 4 修复：确认/放弃投标统一权限 | 修复 |
| !1595 | 任务审核通知 - 小铃铛通知 | 功能 |
| !1592 | 2.3 标讯评估契约测试 | 测试 |

---

## 5. 改动范围

- **改动文件**：109 files changed, 5901 insertions(+), 140 deletions(-)
- **Flyway 迁移**：无新增（DB 已应用至 V1130）
- **rollback 脚本**：无新增
- **主要模块**：projectworkflow、personnel、qualification、warehouse、ca、margin、tender、rate-limit

---

## 6. Flyway 预检结果

### Step 1: validate
```
VALIDATE OK - all checksums match
Successfully validated 194 migrations
```

### Step 2: DB 已应用最新版本
| version | description | success | installed_on |
|---|---|---|---|
| 1130 | personnel education start date nullable | 1 | 2026-07-03 10:48:24 |
| 1129 | ca seal type multiselect | 1 | 2026-07-03 10:48:24 |
| 1128 | add applicant employee number | 1 | 2026-07-02 20:46:54 |

### Step 3: remote-deploy 内置 validate
```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

---

## 7. 部署步骤

1. ✅ 早操三连（dev-env + sync-env + check-git-wrapper）
2. ✅ 确认基线（HEAD = origin/main `a57c1bc58`）
3. ✅ 服务器现状查询（deployed-release.json + health）
4. ✅ Flyway 预检 3 步（validate + DB 版本对比 + remote-deploy 内置）
5. ✅ 本地打包（RELEASE_ID=a57c1bc58-api8080, VITE_API_BASE_URL=）
   - ⚠️ 编译失败，修复 ProjectDocumentUploadWorkflowService.java:54 后重新打包成功
6. ✅ 产物校验（193 个 V*.sql 无重复，前端入口 `assets/index-kh6-5QCH.js`）
7. ✅ 上传 + 部署（scp + remote-deploy.sh，SYSTEMCTL_SUDO=true）
8. ✅ 健康检查（90 次尝试，约 3 分钟，Kafka SDK 延迟）
9. ✅ 前端一致性验证（`assets/index-kh6-5QCH.js` 匹配）

---

## 8. 验证结果

### 8.1 健康检查
- **health**: HTTP 200 UP ✅
- **readiness**: HTTP 200 UP ✅（Kafka SDK 延迟约 3 分钟，已知行为）

### 8.2 Smoke 测试（SSH 内部访问，绕过 Mac HTTP_PROXY）

| 端点 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 400 参数校验失败 | ✅ |
| `GET /api/projects` | 403 | 403 | ✅ |
| `GET /api/integration/crm/health` | 401 | 401 | ✅ |
| `GET /` | 200 | 200 | ✅ |
| `GET /login` | 200 | 200 | ✅ |
| 前端入口 JS | 与 release 一致 | `assets/index-kh6-5QCH.js` | ✅ |

---

## 9. GitHub 镜像同步

- **状态**：❌ 同步失败（被 ArchitectureTest 阻塞）
- **失败原因**：`✗ ArchitectureTest — Controller 可能直接依赖了 Repository 或 Entity`
- **Gitee main 领先 GitHub main**：67 个 commit
- **影响**：GitHub 镜像过时，不影响生产服务
- **后续**：需修复 ArchitectureTest 后重新同步（`bash scripts/sync-to-github.sh`）

---

## 10. 配置清理检查

- `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always`：保留（第 13/14/15/36 次连续决定保留，运维监控需要）
- 无其他临时调试配置（DEBUG/TRACE）

---

## 11. 回滚信息

| 项目 | 值 |
|---|---|
| 回滚 Release | `5f730424f-api8080` |
| 回滚 jar | `/opt/xiyu-bid/releases/5f730424f-api8080/backend/app.jar` |
| 回滚前端 | `/opt/xiyu-bid/releases/5f730424f-api8080/frontend/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-a57c1bc58-*.sql.gz` |
| 回滚命令 | 手动恢复旧 jar + 前端 + `sudo systemctl restart xiyu-bid-backend` |
| 回滚需求 | 不需要（部署成功） |

---

## 12. 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| #1 Flyway 预检 3 步法 | ✅ 执行（validate + DB 版本对比 + remote-deploy 内置） |
| #2 Kafka SDK readiness 延迟 | ✅ 已知行为，等待 3 分钟自恢复 |
| #3 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| #4 Smoke 测试 400/403/401 | ✅ admin 密码未知，用替代验证 |
| #5 GitHub 镜像同步 | ⚠️ 被 ArchitectureTest 阻塞 |
| #6 SHOW_DETAILS 保留 | ✅ 沿用历史决定 |
| #8 systemctl sudo | ✅ SYSTEMCTL_SUDO=true（PR !1324 后默认） |
| #16 Mac HTTP_PROXY 502 | ✅ 通过 SSH 内部访问绕过 |

---

## 13. 风险提示

1. **main 分支编译失败**：`ProjectDocumentUploadWorkflowService.java:54` 引用不存在的 `getUploaderName()`，说明相关 PR 合入时未做本地编译验证。建议后续 PR 增加 `mvn compile` 预检。
2. **ArchitectureTest 失败**：main 分支 ArchitectureTest 未通过（Controller 直接依赖 Repository 或 Entity），导致 GitHub 镜像同步阻塞。需尽快修复。
3. **GitHub 镜像落后 67 commit**：镜像备份过时，如需灾备恢复会丢失近期改动。
4. **部署序号混乱**：7月3日上午部署报告标为 36th（实际应为 38th），本次报告纠正为 38th。

---

## 14. 部署确认清单

- [x] 早操三连通过
- [x] 基线确认（HEAD = origin/main）
- [x] Flyway 预检 3 步通过
- [x] 本地打包成功（含编译修复）
- [x] 产物校验通过
- [x] 上传 + 部署成功
- [x] 健康检查通过（health + readiness UP）
- [x] Smoke 测试全绿
- [x] 前端一致性验证通过
- [x] 配置清理检查完成
- [x] 部署报告生成
- [ ] GitHub 镜像同步（被 ArchitectureTest 阻塞，待修复）
