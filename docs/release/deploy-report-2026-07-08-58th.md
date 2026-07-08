# 第 58 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署序号 | 第 58 次 |
| 部署日期 | 2026-07-08 |
| Release ID | `aa3208f53-api8080` |
| 部署时间 | 2026-07-08 15:44:19 CST |
| 前置 Release | `bd1d4e122-api8080`（第 57 次，被干扰后覆盖） |
| 部署结果 | ✅ 成功 |
| 新增 Flyway 迁移 | 无 |
| 回滚状态 | 未需回滚 |
| 部署性质 | **干扰后重新部署**（生产 jar 和前端被旧代码覆盖后恢复） |

## 部署背景（生产干扰事故）

第 57 次部署（`bd1d4e122-api8080`）于 15:01 完成后，生产环境遭遇两次未授权覆盖：

| 时间 | 事件 | 影响 |
|---|---|---|
| 15:01 | 第 57 次部署完成（`bd1d4e122`） | ✅ 正常 |
| 15:04 | 前端 `/srv/www/xiyu-bid/` 被从 macOS scp 的旧前端覆盖 | 前端入口从 `index-BwYlg-jV.js` 变为 `index-C02E1GT4.js`，且残留 181 个 `._*` 文件 |
| 15:12 | 后端 jar 被旧 commit `efa695a` 打包的 jar 覆盖 | jar md5 从 `2badcbcc...` 变为 `bbbabf38...`，缺失 PR !1861-!1874（含 P0 修复 !1873） |
| 15:14 | 服务被重启 | 生产运行在旧代码上 |
| 15:44 | 第 58 次部署完成（`aa3208f53`）重新覆盖回最新版本 | ✅ 恢复 |

**干扰代码缺失的关键修复**：PR !1873（User.roleProfile EAGER 回滚）是 P0 修复，缺失会导致所有本地用户登录后请求因 `LazyInitializationException` 认证失败。

## 基线信息

| 项目 | 值 |
|---|---|
| 仓库 | `/Users/user/xiyu/worktrees/trae` |
| 分支 | `agent/trae-init`（锚点分支，ff-only 同步到 origin/main） |
| 部署 commit | `aa3208f53` |
| 前置 commit | `bd1d4e122`（第 57 次） |
| 增量 commit 数 | 19（含 merge commit，实际 9 个 PR） |
| GitHub 镜像 | 落后 Gitee 69 commit，领先 1 commit（锁清理，暂不处理） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080（nginx 反代到 18080） |

## PR 列表

本次部署涵盖从 PR !1865 到 PR !1874 的增量改动：

| PR | 描述 | 类型 |
|---|---|---|
| !1865 | fix(project-export): CO-553 项目列表导出表格与系统显示一致 | fix |
| !1867 | fix(CO-501): CRM token 缓存 TTL 用 JWT 真实 exp，不再写死 24h | fix |
| !1868 | fix(bidding-list): CO-547 标讯列表导出/批量操作/全选三项优化 | fix |
| !1869 | fix(CO-501): CRM check-tender-subject 间歇性"不存在"问题增加 detail 接口 fallback | fix |
| !1870 | fix(security): specs/032 修复 OSS 用户权限扩散导致越权看所有菜单 | fix |
| !1871 | docs(release): 第 57 次部署报告 | docs |
| !1872 | refactor(cache): 显式化 @Cacheable TTL 配置（L-08） | refactor |
| !1873 | **fix(P0): User.roleProfile 改回 EAGER 修复 LazyInitializationException 生产事故** | fix(P0) |
| !1874 | feat(warehouse): CO-556 附件管理上传类型选择器增加"上传类型"标签 | feat |

### P0 修复详情（PR !1873）

第 57 次部署中 PR !1842 把 `User.roleProfile` 从 EAGER 改为 LAZY（消除 N+1 查询），但引发生产事故：
- `JwtAuthenticationFilter` 在非事务 Filter 中调用 `EffectiveRoleResolver.resolveRoleCode(user)` → `user.getRoleCode()` → `roleProfile.getCode()` 触发 `LazyInitializationException`
- 所有本地用户登录后请求均认证失败（traceId=5560ee68... 等共 8 次 ERROR）
- PR !1873 将 `User.java` 中 `roleProfile` 改回 `@ManyToOne(fetch = FetchType.EAGER)` 并加注释说明根因

## 改动范围

- **后端**：Java 代码修改（entity P0 回滚、CRM token 缓存、OSS 权限修复、缓存 TTL 配置等）
- **前端**：Vue 组件修改（标讯列表导出、项目导出、仓库附件管理等）
- **数据库**：无新增 Flyway 迁移（DB 已应用至 V1153）

## Flyway 预检结果

### Step 1: Flyway validate
```
VALIDATE OK - all checksums match
```

### Step 2: DB 已应用版本
```
version  description                                              success  installed_on
1153     create tender import task                                1        2026-07-08 09:02:42
1152     add last review reminded at                              1        2026-07-08 09:02:42
1151     rename performance project type centralized to collective 1        2026-07-08 09:02:42
```

### Step 3: remote-deploy.sh 内置 validate
```
VALIDATE OK - all checksums match
✅ Flyway validate 通过
```

## 部署步骤

1. ✅ 早操三连（sync-env.sh ff-only 同步 + check-git-wrapper.sh）
2. ✅ 确认基线：HEAD = `aa3208f53`（PR !1874 已合入）
3. ✅ 服务器现状检查：发现生产 jar md5 ≠ 第 57 次 release jar md5（被干扰）
4. ✅ Flyway 预检 3 步法全绿
5. ✅ 本地打包：`RELEASE_ID=aa3208f53-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`
6. ✅ 产物校验：jar 内迁移文件无重复，前端入口 `assets/index-B4rGFg18.js`
7. ✅ 上传 + 部署：scp + remote-deploy.sh（SYSTEMCTL_SUDO=true，强制覆盖被干扰的 jar）
8. ✅ 健康检查：UP（9 组件全 UP，readinessState UP，无 Kafka 延迟）
9. ✅ Smoke 测试全绿
10. ✅ jar 完整性验证：生产 jar md5 = 本地 release jar md5 = `1350a4e893d837b99e38f0a19dd9bfd3`
11. ✅ P0 修复验证：jar 内 User.class 包含 "EAGER" 字符串（确认 !1873 已包含）
12. ✅ 前端入口验证：生产 = `assets/index-B4rGFg18.js` = release 入口

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
| 登录页 | 200 | 200 | ✅ |

### jar 完整性验证（关键：确认未被干扰）

| 项 | 值 |
|---|---|
| 本地 release jar md5 | `1350a4e893d837b99e38f0a19dd9bfd3` |
| 生产 jar md5 | `1350a4e893d837b99e38f0a19dd9bfd3` |
| 一致性 | ✅ 完全一致 |
| P0 修复验证 | ✅ User.class 含 "EAGER" 字符串（!1873 已包含） |

### 前端一致性

| 项 | 值 |
|---|---|
| 打包入口 | `assets/index-B4rGFg18.js` |
| 服务器入口 | `assets/index-B4rGFg18.js` |
| 一致性 | ✅ 完全一致 |

## 生产干扰事故记录

### 事故时间线

| 时间 (CST) | 事件 | 证据 |
|---|---|---|
| 15:01:57 | 第 57 次部署完成（`bd1d4e122`） | deployed-release.json |
| 15:04 | 前端被 macOS scp 覆盖 | index.html 时间戳 15:04，181 个 `._*` 残留文件 |
| 15:12 | jar 被旧 commit `efa695a` 覆盖 | 生产 jar md5 `bbbabf38...` ≠ release md5 `2badcbcc...`，jar 时间戳 15:12 |
| 15:14:16 | 服务被重启 | systemd 日志 |
| 15:44:19 | 第 58 次部署完成（`aa3208f53`）恢复 | deployed-release.json |

### 干扰代码缺失的 PR

干扰 jar 基于 commit `efa695a`，缺失第 57 次部署的 PR !1861-!1866 和第 58 次的 PR !1865, !1867-!1874，共 11 个 PR，其中最关键的是：
- **!1873 P0 修复**：User.roleProfile EAGER 回滚（缺失会导致所有本地用户认证失败）
- !1870 安全修复：OSS 用户权限扩散导致越权看所有菜单
- !1869 CRM 修复：check-tender-subject 间歇性"不存在"
- !1867 CRM 修复：token 缓存 TTL 用 JWT 真实 exp

### 恢复措施

1. 识别干扰：通过 md5 对比发现生产 jar ≠ release jar
2. 重新打包：基于最新 commit `aa3208f53` 重新打包
3. 强制覆盖：remote-deploy.sh 强制覆盖被干扰的 jar
4. 验证恢复：md5 一致 + P0 修复确认 + Smoke 测试全绿
5. 清理残留：清理 181 个 macOS `._*` 残留文件

### 根因待查

15:04 和 15:12 的两次 scp 操作来源未确定。可能是其他 agent 或手动操作。需加强生产服务器访问审计。

## GitHub 同步状态

| 项目 | 状态 |
|---|---|
| Gitee main（origin） | `aa3208f53`（最新） |
| GitHub main | 落后 69 commit，领先 1 commit（锁清理） |
| 同步操作 | 暂不处理（用户决定） |

## 配置清理检查

| 配置项 | 状态 | 说明 |
|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | 保留 | 第 13-15 次用户决定保留（运维监控需要） |
| macOS `._*` 残留文件 | 已清理 | 181 个残留文件已全部删除（2026-07-08 15:58 CST） |

## 回滚信息

| 回滚项 | 位置 |
|---|---|
| 前置 release | `/opt/xiyu-bid/releases/bd1d4e122-api8080/`（第 57 次） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-aa3208f53-*.sql.gz` |
| 回滚方式 | `cp /opt/xiyu-bid/releases/bd1d4e122-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend` |

## 经验沉淀应用

- ✅ 第 1 条：Flyway 预检 3 步法（全绿）
- ✅ 第 3 条：生产前端同源构建（VITE_API_BASE_URL=）
- ✅ 第 4 条：Smoke 测试 400/403/401 替代验证
- ✅ 第 6 条：SHOW_DETAILS=always 保留（用户决定）
- ✅ 第 8 条：SYSTEMCTL_SUDO=true（remote-deploy.sh 默认）
- ✅ 第 9 条：git.properties commit id 不准确（用 md5 + class 内容验证替代）
- ✅ 第 14 条：macOS `._*` 残留文件清理（部署后清理 181 个）
- ✅ 第 16 条：Mac HTTP_PROXY 502（使用 --noproxy '*' 或 SSH 内部访问绕过）

### 新增经验（第 58 次沉淀）

**第 18 条：生产干扰检测与恢复**

- **现象**：部署完成后，生产 jar md5 与 release jar md5 不一致
- **根因**：有人未经协调从 macOS scp 旧 commit 打包的 jar 和前端到生产服务器
- **检测方法**：部署后必须对比 `md5sum /opt/xiyu-bid/shared/backend/app.jar` 与本地 release jar md5
- **恢复方法**：重新部署最新 commit，强制覆盖被干扰的 jar
- **预防措施**：
  1. 部署后立即验证 md5 一致性（标准化为 SOP 步骤）
  2. 加强生产服务器 SSH 访问审计
  3. 考虑对 `/opt/xiyu-bid/shared/backend/` 和 `/srv/www/xiyu-bid/` 设置更严格的文件权限

## 风险提示

1. **干扰源未查**：15:04 和 15:12 的两次 scp 操作来源未确定，可能再次发生
2. **GitHub 镜像落后 69 commit**：暂不同步，后续需处理
3. **GitHub 领先 1 commit**（锁清理）：暂不处理
4. **P0 修复依赖 EAGER**：User.roleProfile 保持 EAGER 直到 Filter 链中不再访问 roleProfile，或改用 join-fetch 仓库方法

## 部署确认清单

- [x] 早操三连完成
- [x] 基线确认（HEAD = aa3208f53）
- [x] 服务器现状检查（发现干扰）
- [x] Flyway 预检 3 步法全绿
- [x] 本地打包成功（jar + 前端）
- [x] 产物校验通过
- [x] remote-deploy.sh 部署成功（强制覆盖干扰 jar）
- [x] 健康检查通过（UP）
- [x] Readiness 通过（UP，无 Kafka 延迟）
- [x] Smoke 测试全绿（health + readiness + 400 + 403 + 401 + 前端 200）
- [x] jar 完整性验证（md5 一致）
- [x] P0 修复验证（User.class 含 EAGER）
- [x] 前端一致性验证通过
- [x] macOS `._*` 残留清理（181 → 0）
- [x] 配置清理检查完成（SHOW_DETAILS 保留）
- [x] 回滚就绪
- [x] 部署报告生成
