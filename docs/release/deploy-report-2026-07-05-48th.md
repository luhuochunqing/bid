# 第 48 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-05 22:06 CST |
| Release ID | `42c41c736-api8080` |
| 上一版本 | `adb09dec1-api8080`（2026-07-05 16:34 CST 部署，第 47 次） |
| 部署类型 | 增量部署（33 个 commit，含平台账户分页/导出白名单、仓库模块修复、CO-484 驳回重提修复、日期显示修复、品牌授权重构等，2 个 DB 迁移） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 88 次） |
| Readiness | ✅ UP（88 次尝试说明有 Kafka SDK 延迟，属已知行为） |
| 部署耗时 | 约 2 分钟（22:03 打包完成 → 22:06 服务重启 → 健康检查通过） |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae/deploy-48th` |
| HEAD commit | `42c41c736` |
| 工作区状态 | 干净 |
| origin/main 同步 | ✅ HEAD = origin/main |
| GitHub 镜像 | ✅ 同步完成（部署前落后 33 个 commit，部署后已同步） |

## 增量 PR 列表（33 个 commit，`adb09dec1..42c41c736`）

| Commit | PR | 描述 |
|---|---|---|
| `42c41c736` | !1729 | refactor(brand-auth): 设计Review识别问题修复（P0附件类型+N+1+重复抽象） |
| `83484b41f` | !1728 | fix(bid-review): CO-484 驳回重提报"系统繁忙"——deleteByReviewId 改 @Modifying bulk DELETE |
| `58d5973cf` | !1727 | refactor(warehouse): 统一异步任务对话框模式 + 抽取 useAsyncTask composable |
| `0bec446f8` | !1725 | fix(platform-account): getPassword 异常类型改用 AccessDeniedException 避免 Sentry 误报 |
| `c6b9e07cf` | !1726 | fix: 仓库信息第二个导出台账按钮打开正确对话框 + 勾选感知 |
| `31f350e81` | !1722 | feat: 账户管理与 CA 管理列表增加分页 |
| `979d49c24` | !1720 | fix(detail): 修复项目转移按钮无法点击（v-model .value 误用） |
| `b5598a299` | !1723 | fix(warehouse): 修复仓库信息筛选不生效 + 导入模板枚举列加下拉框 |
| `a84fc53ad` | !1724 | CO-472 [UI] 日期 T 分隔符显示问题：抽取 DateTimeDisplay 公共组件 |
| `954f33abe` | !1721 | feat(platform-account): 导出功能增加白名单授权（用户00444） |
| `fe57a1c1f` | - | fix(CO-507): 保证金管理表格增加投标负责人列 |
| `bf09fcc81` | - | fix(resource): CO-506 CA证书印章类型多选显示和导入模板修复 |
| `adb09dec1` | !1708 | CO-490 fix(margin): INIT 分支 JOIN tasks/pc + 缴纳方式翻译 + 项目负责人兜底 |
| `95a7bcf4d` | !1710 | fix(CO-503): 仓库信息模块导出按钮名称规范化 |
| `59133c40d` | !1711 | fix(project): CO-504 流标/弃标不再跳过结项审核，统一走结项申请流程 |
| `398387d06` | !1714 | feat(platform-account): 补全平台账户台账导出功能 |
| `20d9d48e9` | !1716 | feat(common): CO-505 批量导入模板日期格式统一兼容 |
| `99770504c` | !1713 | fix(platform-account): 批量导入接口权限与类级对齐，解决 bid-Team 用户 403 |
| `ba7a0e153` | - | fix(project-doc): CO-487 结项项目删除附件应返回友好提示 |
| `e87f586e1` | - | fix(CO-503): 仓库信息模块导出按钮名称规范化 |
| `e33a9b997` | - | fix(project): CO-504 流标/弃标不再跳过结项审核 |
| `6ada001b2` | - | CO-490 fix(margin): INIT 分支 JOIN + 缴纳方式翻译 |

## 改动范围

**核心业务变更**（8 个功能模块）：

### 1. 平台账户模块增强（feat(platform-account)）
- 账户管理与 CA 管理列表增加分页（!1722）
- 导出功能增加白名单授权（用户00444）（!1721）
- 补全平台账户台账导出功能（!1714）
- 批量导入接口权限与类级对齐，解决 bid-Team 用户 403（!1713）
- getPassword 异常类型改用 AccessDeniedException 避免 Sentry 误报（!1725）
- 新增迁移：V1137（seed platform account export whitelist）

### 2. 仓库信息模块修复（fix(warehouse) / refactor(warehouse)）
- 修复仓库信息筛选不生效 + 导入模板枚举列加下拉框（!1723）
- 统一异步任务对话框模式 + 抽取 useAsyncTask composable（!1727）
- 修复第二个导出台账按钮打开正确对话框 + 勾选感知（!1726）
- 导出按钮名称规范化（!1710 / CO-503）

### 3. CO-484 驳回重提修复（fix(bid-review)）
- 驳回重提报"系统繁忙"修复：deleteByReviewId 改 @Modifying bulk DELETE（!1728）

### 4. CO-472 日期 T 分隔符显示修复（fix(ui)）
- 抽取 DateTimeDisplay 公共组件，修复 15 处日期 T 分隔符显示问题（!1724）

### 5. 品牌授权库重构（refactor(brand-auth)）
- 设计 Review 识别问题修复：P0 附件类型 + N+1 + 重复抽象（!1729）
- 新增迁移：V1138（expand brand auth attachment enum）

### 6. 保证金管理增强（fix(margin)）
- CO-490：INIT 分支 JOIN tasks/pc + 缴纳方式翻译 + 项目负责人兜底（!1708）
- CO-507：保证金管理表格增加投标负责人列，取值关联 ProjectLeadAssignment

### 7. 项目流程修复（fix(project)）
- CO-504：流标/弃标不再跳过结项审核，统一走结项申请流程（!1711）
- 修复项目转移按钮无法点击（v-model .value 误用）（!1720）
- CO-487：结项项目删除附件应返回友好提示而非"系统状态冲突"

### 8. CA 证书与资源模块修复（fix(resource)）
- CO-506：CA 证书印章类型多选显示和导入模板修复
- 批量导入模板日期格式统一兼容（CO-505 / !1716）

**其他变更**：
- 白名单 Store 通用基类抽取消除重复代码
- 思维链 Review 修复 3 个设计问题
- Spec Kit 规划文档：029-fix-account-password-403

## Flyway 预检结果（3 步法）

| 步骤 | 结果 | 备注 |
|---|---|---|
| Step 1: validate | ✅ OK | 200 migrations, all checksums match（execution time 0.089s） |
| Step 2: DB 版本对比 | ✅ 已应用 V1136 | DB V1136（2026-07-05 14:34:32 应用）→ 源码 V1138（待应用 V1137, V1138） |
| Step 3: remote-deploy 内置 validate | ✅ 通过 | 部署时自动执行，VALIDATE OK - all checksums match |

**结论**：2 个新迁移（V1137、V1138）需应用，已在部署后验证成功应用。

**回滚脚本检查**：
- V1137 → U1137：✅ 存在
- V1138 → U1138：✅ 存在

## 部署步骤

### 1. 早操三连

```bash
source scripts/dev-env.sh
bash scripts/sync-env.sh .          # ✅ 同步到 origin/main 42c41c736
bash scripts/check-git-wrapper.sh   # ✅ 7/7 通过
```

### 2. 本地打包（生产同源构建模式）

```bash
RELEASE_ID="42c41c736-api8080" VITE_API_BASE_URL= bash scripts/release/package-release.sh
```

- ✅ BUILD SUCCESS（后端 26.630 s，前端构建正常）
- ✅ jar 内 Flyway 迁移版本无重复
- ✅ 产物：`.release/xiyu-bid-release-42c41c736-api8080.tar.gz`（138M）
- ✅ 前端入口：`assets/index-Dv-iBKcL.js`（同源构建，无 IP 字面量）

### 3. 上传 + 部署

```bash
scp .release/xiyu-bid-release-42c41c736-api8080.tar.gz scripts/release/remote-deploy.sh \
    jetty@172.16.38.78:/opt/xiyu-bid/incoming/

ssh jetty@172.16.38.78 'RELEASE_ARCHIVE=... RELEASE_ID=42c41c736-api8080 \
    SYSTEMCTL_SUDO=true ... bash /opt/xiyu-bid/incoming/remote-deploy.sh'
```

- ✅ Flyway validate 通过（200 migrations，0.082s）
- ✅ DB 备份完成（`/opt/xiyu-bid/db-backups/winbid-42c41c736-api8080-*.sql.gz`）
- ✅ 后端服务重启（PID 29234，2026-07-05 22:06:07 CST）
- ✅ 健康检查通过（consecutive 3/3，总尝试 88 次）
- ✅ 前端一致性验证通过（`index-Dv-iBKcL.js` 与 release 一致）

## 验证结果

### 健康检查

| 端点 | 状态 | 备注 |
|---|---|---|
| `/actuator/health` | ✅ UP | 全组件 UP（aiProvider doubao / db / diskSpace / jwt / livenessState / ping / readinessState / redis / sidecar） |
| `/actuator/health/readiness` | ✅ UP | readinessState UP（88 次尝试说明有 Kafka SDK 延迟，属已知行为） |

### Smoke 测试（admin 密码未知，用 400/403/401 验证接口路由）

| 接口 | HTTP | 预期 | 结果 |
|---|---|---|---|
| `GET /actuator/health` | 200 | UP | ✅ |
| `GET /actuator/health/readiness` | 200 | UP | ✅ |
| `POST /api/auth/login`（空 body） | 400 | 验证错误 | ✅ |
| `GET /api/projects`（无认证） | 403 | 需认证 | ✅ |
| `GET /api/integration/crm/health` | 401 | 需认证 | ✅ |
| `GET /`（前端首页） | 200 | OK | ✅ |
| `GET /login`（前端登录页） | 200 | OK | ✅ |

### Flyway 迁移应用验证

| 版本 | 描述 | 状态 | 应用时间 |
|---|---|---|---|
| V1137 | seed platform account export whitelist | ✅ success=1 | 2026-07-05 22:06:14 |
| V1138 | expand brand auth attachment enum | ✅ success=1 | 2026-07-05 22:06:14 |

### 前端验证

| 项目 | 结果 | 备注 |
|---|---|---|
| 首页 HTTP 200 | ✅ | - |
| 登录页 HTTP 200 | ✅ | - |
| index.js hash 匹配 | ✅ | `assets/index-Dv-iBKcL.js` 与 release 一致 |

## GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 部署前状态 | 落后 33 个 commit |
| 同步命令 | `bash scripts/sync-to-github.sh` |
| 同步后状态 | ✅ 完全一致 |
| Gitee main | `42c41c736c760b5559c902afd8c9e650cebba4ee` |
| GitHub main | `42c41c736c760b5559c902afd8c9e650cebba4ee` |

## 临时配置清理检查

| 配置项 | 当前值 | 状态 | 备注 |
|---|---|---|---|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS` | `always` | ℹ️ 保留 | 用户连续三次决定保留（运维监控需要），非本次部署引入 |

## 回滚信息

**回滚预案**（如遇问题）：

1. **数据库回滚**：从备份恢复
   - 备份文件：`/opt/xiyu-bid/db-backups/winbid-42c41c736-api8080-*.sql.gz`
   - 注意：V1137（白名单数据）和 V1138（枚举扩展）均为增量/扩列操作，回滚风险低

2. **应用回滚**：切换回上一版本 jar
   - 上一版本：`adb09dec1-api8080`
   - 上一版本目录：`/opt/xiyu-bid/releases/adb09dec1-api8080/`
   - 操作：`cp /opt/xiyu-bid/releases/adb09dec1-api8080/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend`

3. **前端回滚**：切换回上一版本前端
   - 上一版本前端在 releases 目录中保留

**风险评估**：低风险。本次变更以 bug 修复和功能增强为主，2 个迁移均为数据/枚举扩展类，无破坏性 schema 变更。

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 应用，validate + DB 版本对比 + remote-deploy 内置 |
| 生产前端同源构建（baseURL=""） | ✅ 应用，VITE_API_BASE_URL= 显式设空 |
| Smoke 测试 admin 密码限制（400/403/401 替代） | ✅ 应用 |
| Kafka SDK readiness 延迟 | ✅ 已知行为，88 次尝试符合预期 |
| systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true 默认生效 |
| Mac HTTP_PROXY 502 | ✅ 绕过，所有 curl 加 --noproxy '*' |
| SentryAppender crash-loop 教训 | ✅ 本次部署无 logback 配置变更 |

## 风险提示

1. **V1137 白名单数据迁移**：仅插入数据，无 schema 变更，回滚简单
2. **V1138 品牌授权附件类型枚举扩展**：仅扩展枚举值范围（从原有类型扩种），不破坏旧数据
3. **Kafka SDK 启动延迟**：已知行为，readiness 最终会恢复 UP，不影响业务
4. **SHOW_DETAILS=always**：运维监控保留，非本次部署引入

## 部署确认清单

- [x] 早操三连通过
- [x] 基线同步到 origin/main
- [x] Flyway 预检 3 步法通过
- [x] 本地打包成功（clean + 无重复迁移）
- [x] DB 备份完成
- [x] 后端服务重启成功
- [x] 健康检查 UP（consecutive 3/3）
- [x] Readiness UP
- [x] Flyway 迁移应用成功（V1137, V1138）
- [x] API Smoke 测试通过（5/5）
- [x] 前端页面正常（2/2）
- [x] 前端 hash 匹配
- [x] GitHub 镜像同步完成
- [x] 部署报告生成
