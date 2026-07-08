# 第 57 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 57 次 |
| 部署日期 | 2026-07-08 |
| Release ID | `bd1d4e122-api8080` |
| 部署时间 | 2026-07-08 15:01:57 CST |
| 前置 Release | `beab87a5e-api8080`（第 56 次） |
| 部署结果 | ✅ 成功 |
| 新增 Flyway 迁移 | 无 |
| 回滚状态 | 未需回滚 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步到 origin/main） |
| 部署 commit | `bd1d4e122` |
| 前置 commit | `beab87a5e` |
| 增量 commit 数 | 50+ |
| GitHub 镜像 | 落后 Gitee 50 commit，领先 1 commit（锁清理，暂不处理） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080（nginx 反代到 18080） |

## PR 列表

本次部署涵盖从 PR !1842 到 PR !1866 的增量改动：

| PR | 描述 | 类型 |
|---|---|---|
| !1842 | fix(entity): P0 修复 User.roleProfile EAGER→LAZY 消除 N+1 查询 | fix |
| !1843 | fix(qualification): 批量下载附件为空时给出明确错误提示 | fix |
| !1840 | feat: 业绩管理：合同附件上传校验 | feat |
| !1841 | feat(retrospective): 复盘报告上传支持 Excel | feat |
| !1845 | feat: 项目档案导出文件包 | feat |
| !1846 | fix(task-board): 修复跨部门执行人任务看板交付物上传路径 | fix |
| !1847 | feat: 修复仓库到期无提醒 Bug | fix |
| !1848 | feat: 业绩导入附件失败根因排查 | fix |
| !1849 | feat: 移动项目状态列 | feat |
| !1850 | feat: 投标人员未收到项目提醒根因排查 | fix |
| !1853 | fix(alerts): 修复告警规则模块全部代码质量问题 + 前端功能补全 | fix |
| !1854 | fix(permission): 保证金缴纳任务执行人编辑权限改用 isBidManager 放行 admin (CO-481) | fix |
| !1855 | fix(tender-import): 保留 Tomcat 超时兜底 + 增强前端 504/超时弹窗处理 (CO-524) | fix |
| !1857 | fix(ai+case-slice): 修复 embedding 模型默认值 + RoutingAiProvider 参数顺序 + batch-embed NPE | fix |
| !1858 | feat(project): 项目列表增加投标辅助人员列 (CO-551) | feat |
| !1859 | fix(alerts): 打破 DEPOSIT_RETURN 规则触发链死锁 | fix |
| !1860 | feat(initiation): 保证金缴纳截止日期不能早于当前日期 (CO-540) | feat |
| !1861 | fix(evaluation): CO-550 评标文件字段改名为开标一览表并取消必填 | fix |
| !1862 | feat(project-export): 投标项目导出表格增加投标状态中文化与辅助人员列 | feat |
| !1863 | feat: 项目文档列表添加序号前缀与前端分页（5条/页） | feat |
| !1864 | fix(frontend): 修复前端单元测试 NPE 和 clearSelection 报错 | fix |
| !1866 | fix(notification): Review P0-1 + P1-2 修复 | fix |
| !1859 | feat(notification): 补齐文档变更通知（蓝图 §消息中心-系统通知 序号 5） | feat |

## 改动范围

- **后端**：Java 代码修改（entity 优化、权限修复、告警规则、通知补齐等）
- **前端**：Vue 组件修改（项目列表、导出、文档分页、通知等）
- **数据库**：无新增 Flyway 迁移（DB 已应用至 V1153）

## Flyway 预检结果

### Step 1: Flyway validate
```
VALIDATE OK - all checksums match
Successfully validated 216 migrations (execution time 00:00.089s)
```

### Step 2: DB 已应用版本
```
version  description                                              success  installed_on
1153     create tender import task                                1        2026-07-08 09:02:42
1152     add last review reminded at                              1        2026-07-08 09:02:42
1151     rename performance project type centralized to collective 1        2026-07-08 09:02:42
1150     backfill project leader department                        1        2026-07-08 09:02:42
1149     qualification audit log date fields                       1        2026-07-08 09:02:42
```

### Step 3: remote-deploy.sh 内置 validate
```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

## 部署步骤

1. ✅ 早操三连（sync-env.sh + ff-only 同步 + check-git-wrapper.sh）
2. ✅ 确认基线：HEAD = `bd1d4e122`（PR !1866 已合入）
3. ✅ 服务器现状：`beab87a5e-api8080` 健康状态 UP
4. ✅ Flyway 预检 3 步法全绿
5. ✅ 本地打包：`RELEASE_ID=bd1d4e122-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`
6. ✅ 产物校验：215 个迁移文件无重复，前端入口 `assets/index-BwYlg-jV.js`
7. ✅ 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true）
8. ✅ 健康检查：84 次尝试，连续 3/3 通过
9. ⚠️ 前端覆盖事故：部署后 3 分钟（15:04）被另一份从 macOS scp 的前端覆盖，已重新部署前端
10. ✅ Smoke 测试全绿

## 验证结果

### 健康检查
```
{"status":"UP","components":{"aiProvider":{"status":"UP"},"db":{"status":"UP"},"diskSpace":{"status":"UP"},"jwt":{"status":"UP"},"livenessState":{"status":"UP"},"ping":{"status":"UP"},"readinessState":{"status":"UP"},"redis":{"status":"UP"},"sidecar":{"status":"UP"}}}
```

### Readiness
```
{"status":"UP","components":{"db":{"status":"UP"},"readinessState":{"status":"UP"}}}
```
（无 Kafka SDK readiness 延迟）

### Smoke 测试

| 测试项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `/api/auth/login` (POST {}) | 400 | 400 | ✅ |
| `/api/projects` (GET) | 403 | 403 | ✅ |
| `/api/integration/crm/health` | 401 | 401 | ✅ |
| 前端首页 | 200 | 200 | ✅ |
| 前端 JS 加载 | 200 | 200 | ✅ |
| 登录页 | 200 | 200 | ✅ |

### 前端一致性
- 打包入口：`assets/index-BwYlg-jV.js`
- 服务器入口：`assets/index-BwYlg-jV.js`（重新部署后一致）

## 前端覆盖事故记录

**现象**：部署完成（15:01）后 3 分钟（15:04），/srv/www/xiyu-bid/ 被另一份从 macOS scp 的前端文件覆盖。index.html 从 `assets/index-BwYlg-jV.js` 变为 `assets/index-C02E1GT4.js`。

**发现**：Smoke 测试时发现前端入口不一致，服务器上的 index.html 时间戳 15:04（部署后 3 分钟），且有 macOS `._*` 残留文件。

**处理**：用户选择重新部署前端。从 release 目录 `/opt/xiyu-bid/releases/bd1d4e122-api8080/frontend/` 复制到 `/srv/www/xiyu-bid/`，清理 macOS 残留文件。重新部署后前端一致性验证通过。

**根因待查**：15:04 时有另一来源的 macOS scp 操作覆盖了前端目录。可能是其他 agent 或手动操作。

## GitHub 同步状态

| 项目 | 状态 |
|---|---|
| Gitee main（origin） | `bd1d4e122`（最新） |
| GitHub main | 落后 50 commit，领先 1 commit（锁清理 `bed2b7728`） |
| 同步操作 | 暂不处理（用户之前决定） |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 第 13-15 次用户决定保留（运维监控需要） |

## 回滚信息

| 回滚项 | 位置 |
|---|---|
| 前置 release | `/opt/xiyu-bid/releases/beab87a5e-api8080/` |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-bd1d4e122-*.sql.gz` |
| 回滚方式 | `cp /opt/xiyu-bid/releases/beab87a5e-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用

- ✅ 第 1 条：Flyway 预检 3 步法（全绿）
- ✅ 第 3 条：生产前端同源构建（VITE_API_BASE_URL=）
- ✅ 第 4 条：Smoke 测试 400/403/401 替代验证
- ✅ 第 6 条：SHOW_DETAILS=always 保留（用户决定）
- ✅ 第 8 条：SYSTEMCTL_SUDO=true（remote-deploy.sh 默认）
- ⚠️ 第 14 条：macOS `._*` 残留文件（前端覆盖事故中发现并清理）

## 风险提示

1. **前端覆盖根因未查**：15:04 的覆盖来源未确定，如再次发生可能导致前端版本不一致
2. **GitHub 镜像落后 50 commit**：暂不同步，后续需处理
3. **GitHub 领先 1 commit**（锁清理）：暂不处理

## 部署确认清单

- [x] 早操三连完成
- [x] 基线确认（HEAD = bd1d4e122）
- [x] Flyway 预检 3 步法全绿
- [x] 本地打包成功（jar + 前端）
- [x] 产物校验通过
- [x] remote-deploy.sh 部署成功
- [x] 健康检查通过（UP）
- [x] Readiness 通过（UP，无 Kafka 延迟）
- [x] Smoke 测试全绿（health + readiness + 400 + 403 + 401 + 前端 200）
- [x] 前端一致性验证通过（重新部署后）
- [x] 配置清理检查完成（SHOW_DETAILS 保留）
- [x] 回滚就绪
- [x] 部署报告生成
