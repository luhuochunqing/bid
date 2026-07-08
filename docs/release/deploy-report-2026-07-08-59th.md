# 第 59 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 59 次 |
| 部署日期 | 2026-07-08 |
| Release ID | `7d188cb46-api8080` |
| 部署时间 | 2026-07-08 20:02:34 CST |
| 前置 Release | `aa3208f53-api8080`（第 58 次） |
| 部署结果 | ✅ 成功（remote-deploy.sh 健康检查因 Kafka SDK 延迟超时，服务后续自恢复） |
| 新增 Flyway 迁移 | V1154（移除 platform_account 名称唯一约束） |
| 回滚状态 | 未需回滚 |
| 部署性质 | 正常增量部署 |

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae/deploy-58th-report`（任务分支，已 rebase 到 origin/main） |
| 部署 commit | `7d188cb46` |
| 前置 commit | `aa3208f53`（第 58 次） |
| 增量 commit 数 | 50+（含 merge commit，实际 17 个 PR） |
| GitHub 镜像 | 落后 Gitee 129 commit，领先 1 commit（用户决定暂不处理） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080（nginx 反代到 18080） |

## PR 列表

本次部署涵盖从 PR !1875 到 PR !1891 的增量改动：

| PR | 描述 | 类型 |
|---|---|---|
| !1875 | fix(CO-554): 资质列表下载按钮缺失（uploadAttachment 未设 fileUrlExplicitlySet 旗标） | fix |
| !1876 | fix(personnel): CO-557 人员证书列表"即将到期"列名改为"即将到期数量" | fix |
| !1877 | docs(release): 第 58 次部署报告 | docs |
| !1878 | feat(upload): L-07 串行上传改并发上传（上限 3） | feat |
| !1879 | fix(CO-529): 任务提交时跳过已保存附件记录，避免误报'文件读取失败' | fix |
| !1880 | feat(casework): add default embedding model config and data generation script | feat |
| !1881 | fix(qualification): 批量下载附件时跳过缺失/非绝对 URL 附件，不再生成 .txt 占位 | fix |
| !1882 | fix(CO-559): 移除平台名称唯一性校验，允许同一平台名称注册多个账户 | fix |
| !1883 | feat(CO-529-followup): 移除任务看板的上传/提交入口，收敛到任务详情页 | feat |
| !1884 | feat(alerts): 告警规则阈值单位注释与前端提示 | feat |
| !1885 | fix(backend): 标讯/项目列表部门为空时从用户 department_name 兜底回填 | fix |
| !1886 | fix(performance): 补齐 XIYU-Y 修复遗漏的前端 CENTRALIZED 旧值 | fix |
| !1887 | fix(deploy): 先 stop 再替换 jar 再 start，防止部署期间 NoClassDefFoundError | fix |
| !1888 | feat(CO-558): 项目文档下载/删除按角色矩阵控制 + 投标文件审核状态删除守卫 | feat |
| !1889 | refactor(backend): 优化部门兜底回填实现（!1885 后续） | refactor |
| !1890 | fix(performance): XIYU-Y 后端兜底兼容 CENTRALIZED 旧值映射为 COLLECTIVE | fix |
| !1891 | CO-526: 保留 CRM 同 position 多个对接人，避免评估表提交丢失联系人 | fix |

## 改动范围

- **后端**：Java 代码修改（CRM 对接人、绩效 CENTRALIZED 兼容、部门兜底回填、项目文档权限、资质下载、平台账号唯一性、任务附件等）
- **前端**：Vue 组件修改（任务看板、项目文档、人员证书、绩效、资质、上传并发、告警规则等）
- **数据库**：新增 V1154 迁移（`drop unique constraint from platform_account_name`）

## Flyway 预检结果

### Step 1: Flyway validate
```
VALIDATE OK - all checksums match
```

### Step 2: DB 已应用版本（部署前）
```
version  description                                              success  installed_on
1153     create tender import task                                1        2026-07-08 09:02:42
1152     add last review reminded at                              1        2026-07-08 09:02:42
1151     rename performance project type centralized to collective 1        2026-07-08 09:02:42
```

### Step 3: remote-deploy.sh 内置 validate
```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

### 部署后 Flyway 状态
```
version  description                                         success  installed_on
1154     drop unique constraint from platform account name   1        2026-07-08 20:02:41
```

## 部署步骤

1. ✅ 早操三连（source dev-env.sh + sync-env.sh + check-git-wrapper.sh）
2. ✅ 确认基线：HEAD = `7d188cb46`（= origin/main）
3. ✅ 服务器现状检查：当前部署 `aa3208f53-api8080`，健康 UP
4. ✅ Flyway 预检 3 步法全绿
5. ✅ 本地打包：`RELEASE_ID=7d188cb46-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`
6. ✅ 产物校验：jar 内迁移文件无重复，前端入口 `assets/index-DhkttOa-.js`
7. ⚠️ 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true）
   - Flyway validate 通过、jar 替换成功、服务启动成功
   - **健康检查脚本因 Kafka SDK 启动延迟，在 120 次尝试后超时退出（exit code 1）**
   - 服务未回滚，继续在后台启动
8. ✅ 健康检查：约 4 分钟后 UP（20:06:54）
9. ✅ Readiness 通过：20:06:54 UP
10. ✅ Smoke 测试全绿
11. ✅ jar 完整性验证：生产 jar md5 = 本地 release jar md5 = `a4cbd705717abee53576ef4d4e76a0c1`
12. ✅ 前端一致性验证：生产 = `assets/index-DhkttOa-.js` = release 入口
13. ✅ V1154 迁移应用验证

## 验证结果

### 健康检查
```
{"status":"UP","components":{"aiProvider":{"status":"UP"},"db":{"status":"UP"},"diskSpace":{"status":"UP"},"jwt":{"status":"UP"},"livenessState":{"status":"UP"},"ping":{"status":"UP"},"readinessState":{"status":"UP"},"redis":{"status":"UP"},"sidecar":{"status":"UP"}}}
```

### Readiness
```
{"status":"UP","components":{"db":{"status":"UP"},"readinessState":{"status":"UP"}}}
```

### Smoke 测试

| 测试项 | 预期 | 实际 | 结果 |
|---|---|---|---|
| `/actuator/health` | 200 UP | 200 UP | ✅ |
| `/actuator/health/readiness` | 200 UP | 200 UP | ✅ |
| `/api/auth/login` (POST {}) | 400 | 400 | ✅ |
| `/api/projects` (GET) | 403 | 403 | ✅ |
| `/api/integration/crm/health` | 401 | 401 | ✅ |
| 前端首页 | 200 | 200 | ✅ |
| 登录页 | 200 | 200 | ✅ |

### jar 完整性验证

| 项 | 值 |
|---|---|
| 本地 release jar md5 | `a4cbd705717abee53576ef4d4e76a0c1` |
| 生产 jar md5 | `a4cbd705717abee53576ef4d4e76a0c1` |
| 一致性 | ✅ 完全一致 |

### 前端一致性

| 项 | 值 |
|---|---|
| 打包入口 | `assets/index-DhkttOa-.js` |
| 服务器入口 | `assets/index-DhkttOa-.js` |
| 一致性 | ✅ 完全一致 |

## Kafka SDK Readiness 延迟记录

本次部署出现 lesson #2 描述的 Kafka SDK 启动延迟：

- **现象**：remote-deploy.sh 启动服务后，/actuator/health 持续返回 503，脚本 120 次尝试后退出
- **根因**：`OrganizationEventSdkKafkaStarter` 在 `ApplicationReadyEvent` 中同步初始化 Kafka consumer，阻塞 readiness 状态切换
- **时间线**：
  - 20:02:34 服务启动
  - 20:06:53 `[org-event-sdk-kafka] === bootstrapping SDK initialization ===`
  - 20:06:54 Kafka consumer started successfully
  - 20:06:54 health/readiness 同时返回 200 UP
- **恢复**：自恢复，未需人工干预
- **建议**：remote-deploy.sh 健康检查应延长超时或单独容忍 Kafka SDK 延迟（参考 lesson #2）

## GitHub 同步状态

| 项目 | 状态 |
|---|---|
| Gitee main（origin） | `7d188cb46`（最新） |
| GitHub main | 落后 129 commit，领先 1 commit（`bed2b7728 chore(locks): prune stale expired locks`） |
| 同步操作 | 暂不处理（用户决定） |
| 风险 | GitHub 领先 commit 方向异常，后续 sync-to-github.sh 可能冲突 |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 第 13-15 次及第 58 次用户决定保留（运维监控需要） |

## 回滚信息

| 回滚项 | 位置 |
|---|---|
| 前置 release | `/opt/xiyu-bid/releases/aa3208f53-api8080/`（第 58 次） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-7d188cb46-api8080-20260708200225.sql.gz` |
| 回滚方式 | `cp /opt/xiyu-bid/releases/aa3208f53-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用

- ✅ 第 1 条：Flyway 预检 3 步法（全绿）
- ✅ 第 2 条：Kafka SDK readiness 延迟（本次出现，约 4 分钟自恢复）
- ✅ 第 3 条：生产前端同源构建（VITE_API_BASE_URL=）
- ✅ 第 4 条：Smoke 测试 400/403/401 替代验证
- ✅ 第 6 条：SHOW_DETAILS=always 保留（用户决定）
- ✅ 第 8 条：SYSTEMCTL_SUDO=true（remote-deploy.sh 默认）
- ✅ 第 16 条：Mac HTTP_PROXY 502（使用 --noproxy '*' 或 SSH 内部访问绕过）

## 风险提示

1. **remote-deploy.sh 健康检查超时**：当前脚本对 Kafka SDK 启动延迟的容忍度不足，可能误报失败。已确认服务实际可自恢复，但脚本退出码 1 会误导部署状态判断。
2. **GitHub 镜像方向异常**：GitHub main 领先 Gitee 1 commit，与"Gitee 唯一 source of truth"冲突，需后续处理。
3. **V1154 非幂等迁移**：该迁移 DROP UNIQUE CONSTRAINT，如重复执行会失败；正常 Flyway 版本机制可避免重复执行。

## 部署确认清单

- [x] 早操三连完成
- [x] 基线确认（HEAD = 7d188cb46）
- [x] 服务器现状检查
- [x] Flyway 预检 3 步法全绿
- [x] 本地打包成功（jar + 前端）
- [x] 产物校验通过
- [x] remote-deploy.sh 部署（jar 替换与服务启动成功，脚本因 Kafka 延迟超时）
- [x] 健康检查通过（UP，约 4 分钟后）
- [x] Readiness 通过（UP）
- [x] Smoke 测试全绿
- [x] jar 完整性验证（md5 一致）
- [x] 前端一致性验证通过
- [x] V1154 迁移应用验证
- [x] DB 备份已创建
- [x] 配置清理检查完成（SHOW_DETAILS 保留）
- [x] 回滚就绪
- [x] 部署报告生成
