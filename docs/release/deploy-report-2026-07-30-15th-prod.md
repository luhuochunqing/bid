# 第 15 次生产环境部署报告 — 2026-07-30

## 部署环境

| 项目 | 值 |
|---|---|
| 环境 | **生产 (prod)** |
| 目标主机 | `winbid-01` |
| 目标 IP | `172.16.10.149` |
| 用途 | 正式环境、对外服务 |
| 部署序号 | 第 15 次（生产） |
| 部署时间 | 2026-07-30 22:09:25 CST |

## 部署概览

| 项目 | 值 |
|---|---|
| Release ID | `6cf4a07d1-prod` |
| 上一版本 Release | `bec8511b4-prod`（2026-07-21 23:02:36 CST，第 14 次生产部署） |
| 基线 commit | `6cf4a07d1`（origin/main） |
| 激活时间 | 2026-07-30T14:09:25Z |
| 部署结果 | ✅ 成功 |
| 回滚状态 | 未需要 |
| 新增 Flyway 迁移 | 6 个（V1174, V1177, V1178, V1179, V1180, V1181） |
| Smoke 测试 | 7 项全部通过 |
| GitHub 镜像 | ⚠️ 落后 3 commits（sync-to-github 需交互确认覆盖 GitHub 独有 commit，暂缓） |

## 基线信息

- 工作树：`/Users/user/xiyu/worktrees/trae`
- **基线纠偏（关键）**：任务开始时工作区处于 detached HEAD，位于功能分支 `agent/claude/workflow-forms-simplify`（`46c8a3b91`），领先 origin/main 4 个**未合入 main** 的 commit，其中包含**破坏性迁移 V1182**（DELETE knowledge.case 表单定义 + 5 张关联表级联删除）。
- **决策**：按"main 是唯一真值"铁律 + 用户确认，切回 `origin/main` = `6cf4a07d1` 作为部署基线，**排除未审查的 V1182**。
- 工作区状态：干净，HEAD = origin/main = `6cf4a07d1`

## 增量改动（bec8511b4 → 6cf4a07d1，99 个 commit）

### 改动范围聚合

| 目录 | 文件数 |
|---|---|
| backend/src/main | 61 |
| backend/src/test | 30 |
| src/views | 21 |
| src/utils / src/components | 4 |
| e2e / scripts / 配置 | 若干 |

### 高危文件改动

| 文件 | 说明 |
|---|---|
| entity/RoleProfile.java | 权限相关 |
| entity/RoleProfileAdminPermissions.java | 权限相关 |
| entity/RoleProfileCatalog.java | 角色目录 |
| resources/entity/CaCertificateEntity.java | CA 证书实体 |

- `application*.yml`：**未改动**（Flyway 配置稳定）
- `pom.xml`：**无依赖变化**（无 SentryAppender crash-loop 风险）
- `logback-spring.xml`：**未改动**

### 新增 Flyway 迁移（6 个，全部低风险数据类）

| 版本 | 描述 | 类型 | 风险 |
|---|---|---|---|
| V1174 | fix_quoted_menu_permissions | UPDATE roles（去引号） | 🟢 幂等 |
| V1177 | backfill_business_table_comments | ALTER COMMENT + MODIFY 注释（不改类型） | 🟢 |
| V1178 | add_knowledge_qualification_permission | UPDATE roles 追加权限 | 🟢 幂等 |
| V1179 | add_knowledge_personnel_permission | UPDATE roles 追加权限 | 🟢 幂等 |
| V1180 | add_knowledge_sub_permissions | UPDATE roles（FIND_IN_SET 检查） | 🟢 幂等 |
| V1181 | cleanup_audit_logs_project_id | UPDATE audit_logs 数据修正 | 🟢 |

- **无破坏性 DROP TABLE / DROP COLUMN 结构变更**
- 6 个迁移均有对应 rollback 脚本（U1174/U1177-U1181，V1181 前 5 个已核验）

## 回归风险评估结论

| 维度 | 结果 | 风险 |
|---|---|---|
| 增量规模 | 99 commit（9 天） | 🟡 中等跨度，无破坏性 |
| 新增迁移 | 6 个幂等数据类 | 🟢 |
| Flyway validate | VALIDATE OK（236 迁移校验通过） | 🟢 |
| application.yml / pom.xml / logback | 均未改动 | 🟢 |
| 高危代码 | entity/RoleProfile*（权限） | 🟡 需 Smoke 验证登录 |

**综合结论**：低-中风险，无破坏性迁移，已排除未审查的 V1182，可安全部署。

## Flyway 预检 3 步法

| 步骤 | 结果 |
|---|---|
| Step 1: 服务器 `flyway-repair-runner.sh validate` | ✅ VALIDATE OK - all checksums match（236 migrations） |
| Step 2: DB 已应用版本 vs 源码最新版本 | ✅ DB V1173，待应用 V1174/V1177-V1181 |
| Step 3: remote-deploy.sh 内置 validate | ✅ VALIDATE OK - all checksums match |

## 部署步骤

1. **基线纠偏**：从 detached 功能分支切回 origin/main（排除破坏性 V1182）
2. **环境门禁**：用户显式确认部署到生产 172.16.10.149
3. **服务器现状探测**：deployed-release.json（bec8511b4-prod，9 天前）+ 健康检查 UP
4. **Flyway 预检**：3 步法全部通过，DB V1173
5. **本地打包**：
   ```bash
   RELEASE_ID="6cf4a07d1-prod" VITE_API_BASE_URL= VITE_OBS_ENABLED=true COPYFILE_DISABLE=1 \
     bash scripts/release/package-release.sh
   ```
6. **产物校验**：
   - release-metadata.json: `obsEnabled=true`, `apiBaseUrl=""` ✅
   - jar 内迁移到 V1181，无重复版本 ✅
   - 前端入口: `assets/index-CmyqoKTB.js` ✅
   - OBS 直传 Detail chunk `.upload(` 调用数=2 ✅
7. **上传 + 部署**：
   ```bash
   scp .release/xiyu-bid-release-6cf4a07d1-prod.tar.gz scripts/release/remote-deploy.sh \
     jetty@172.16.10.149:/opt/xiyu-bid/incoming/
   ssh jetty@172.16.10.149 '... SYSTEMCTL_SUDO=true bash /opt/xiyu-bid/incoming/remote-deploy.sh'
   ```
   - Flyway validate 通过 ✅
   - 后端服务重启成功（active/running）✅
   - 健康检查通过（15 次尝试，3/3 连续成功）✅
   - 前端一致性验证通过（index-CmyqoKTB.js）✅
8. **前端资源保留**：从上一版本 `bec8511b4-prod` 复制 assets 到 `/srv/www/xiyu-bid/assets/`

## 验证结果

### 健康检查（服务器本地）

| 组件 | 状态 |
|---|---|
| overall | UP |
| aiProvider | UP |
| db | UP |
| diskSpace | UP |
| jwt | UP |
| livenessState | UP |
| ping | UP |
| readinessState | UP |
| redis | UP |
| sidecar | UP |

### 迁移应用验证

| 版本 | success | installed_on |
|---|---|---|
| V1174 | 1 | 2026-07-30 22:09:32 |
| V1177 | 1 | 2026-07-30 22:09:32 |
| V1178 | 1 | 2026-07-30 22:09:32 |
| V1179 | 1 | 2026-07-30 22:09:32 |
| V1180 | 1 | 2026-07-30 22:09:32 |
| V1181 | 1 | 2026-07-30 22:09:32 |

### Smoke 测试

| # | 接口 | 期望 | 实际 | 结果 |
|---|---|---|---|---|
| 1 | GET /actuator/health | 200 UP | HTTP 200 | ✅ |
| 2 | GET /actuator/health/readiness | 200 UP | HTTP 200 | ✅ |
| 3 | POST /api/auth/login（空 body） | 400 | HTTP 400 | ✅ |
| 4 | GET /api/projects（无认证） | 403 | HTTP 403 | ✅ |
| 5 | GET /api/integration/crm/health（无认证） | 401 | HTTP 401 | ✅ |
| 6 | GET /（前端首页） | 200 | HTTP 200 | ✅ |
| 7 | GET /login（登录页） | 200 | HTTP 200 | ✅ |
| 8 | 前端 index.html 入口 | assets/index-CmyqoKTB.js | assets/index-CmyqoKTB.js | ✅ |

### 后端服务状态

```
● xiyu-bid-backend.service - XiYu Smart Bidding Backend
   Loaded: loaded (/etc/systemd/system/xiyu-bid-backend.service; enabled; vendor preset: disabled)
   Active: active (running) since Thu 2026-07-30 22:09:25 CST
 Main PID: 14371 (java)
    Tasks: 71
   Memory: 1.1G
```

## GitHub 镜像同步

- 部署前状态：落后 3 commits（`6cf4a07d1` / `2188a854b` / `eeba9690a`，均为 origin/main 已有正常提交）
- sync-to-github.sh 检测到 GitHub 领先 Gitee 1 个 commit（`5ee045a0c chore(locks): prune stale expired locks`），进入交互确认（force 会覆盖该 GitHub 独有 commit）
- 决策：不擅自强推覆盖 GitHub 独有 commit，记录为待办，后续人工确认后同步

## 回滚信息

- 回滚锚点：`bec8511b4-prod`（上一版本 release 目录 `/opt/xiyu-bid/releases/bec8511b4-prod/` 完整保留）
- 回滚命令：
  ```bash
  ssh jetty@172.16.10.149 'sudo systemctl stop xiyu-bid-backend && \
    sudo cp /opt/xiyu-bid/releases/bec8511b4-prod/backend/app.jar /opt/xiyu-bid/shared/backend/app.jar && \
    sudo cp -rf /opt/xiyu-bid/releases/bec8511b4-prod/frontend/* /srv/www/xiyu-bid/ && \
    sudo systemctl start xiyu-bid-backend'
  ```
- DB 备份：`/opt/xiyu-bid/db-backups/winbid-6cf4a07d1-prod-20260730220917.sql.gz`（2.0M，remote-deploy.sh 自动生成）
- 回滚风险评估：低（6 个迁移均为幂等数据类 UPDATE，无 schema 破坏性变更；如需回滚代码即可，数据层可用 U1174/U1177-U1181 rollback 脚本）

## 经验沉淀应用情况

| # | 经验 | 应用情况 |
|---|---|---|
| 1 | Flyway 预检 3 步法 | ✅ 全部执行 |
| 2 | Kafka SDK readiness 延迟 | ✅ 本次未出现（readiness 直接 200） |
| 3 | 生产前端同源构建（baseURL=""） | ✅ VITE_API_BASE_URL= 显式设空 |
| 4 | Smoke 测试 admin 密码限制 | ✅ 用 400/403/401 替代验证 |
| 5 | GitHub 镜像同步 | ⚠️ 落后 3 commits，需人工确认覆盖 |
| 6 | 临时调试配置清理 | ✅ 仅 SHOW_DETAILS=always（用户历史决定保留） |
| 7 | 幂等迁移设计 | ✅ 6 个迁移均幂等（FIND_IN_SET 检查 / UPDATE） |
| 8 | systemctl sudo 权限 | ✅ SYSTEMCTL_SUDO=true |
| 10 | 破坏性 schema 变更 | ✅ 主动识别并排除未审查的破坏性 V1182 |
| 14 | macOS `._*` 残留文件 | ✅ COPYFILE_DISABLE=1 |
| 16 | Mac HTTP_PROXY 导致 502 | ✅ 服务器本地 curl 绕过 |
| 17 | SentryAppender crash-loop | ✅ pom.xml/logback 均未改动，无风险 |
| 18 | 前端 hash 资源跨版本 404 | ✅ 从 bec8511b4-prod 保留 assets |
| OBS | OBS 直传漏传 VITE_OBS_ENABLED | ✅ 显式传入 + 产物校验 obsEnabled=true |

## 风险提示

1. **权限相关变更**（entity/RoleProfile* + V1174/V1178/V1179/V1180 菜单权限）：本次含知识库权限补充（资质/人员/档案/案例）与菜单权限去引号修复，建议通知 /bidAdmin、bid-TeamLeader、bid-Team、bid-SystemAdmin 角色用户验证菜单显示是否正常。
2. **GitHub 镜像落后 3 commits**：不影响生产服务，需人工确认后用 force-with-lease 覆盖 GitHub 独有的 chore(locks) commit。
3. **基线纠偏提示**：破坏性迁移 V1182（DELETE knowledge.case）仍存在于功能分支 `agent/claude/workflow-forms-simplify`，未合入 main，本次未部署。如后续该分支走 PR 合入，需单独评估 V1182 的生产影响。

## 部署确认清单

- [x] 基线纠偏：排除未审查的破坏性 V1182，切回 origin/main
- [x] 环境门禁：用户显式确认部署到生产 172.16.10.149
- [x] 全面回归风险检查：99 commit / 6 迁移低风险 / 无高危配置变更
- [x] 基线确认：HEAD = origin/main = `6cf4a07d1`
- [x] 服务器现状：deployed-release.json + health UP
- [x] Flyway 预检 3 步法：全部通过
- [x] 本地打包：RELEASE_ID=6cf4a07d1-prod，OBS 启用
- [x] 产物校验：obsEnabled=true，无重复迁移版本，前端入口一致
- [x] 上传 + 部署：remote-deploy.sh 成功
- [x] 前端资源保留：从 bec8511b4-prod 复制 assets
- [x] 健康检查：全组件 UP，readiness 200 UP
- [x] 迁移应用验证：V1174/V1177-V1181 全部 success=1
- [x] Smoke 测试：全部通过
- [x] 配置清理检查：仅 SHOW_DETAILS=always（用户保留项）
- [ ] GitHub 镜像同步：暂缓（需人工确认覆盖）
- [x] 部署报告：本文档

## 部署后待办

1. **GitHub 镜像同步**：落后 3 commits，需人工确认后用 `force-with-lease` 覆盖 GitHub 独有 commit
2. **通知用户验证**：
   - 知识库权限相关角色验证菜单显示（V1174/V1178/V1179/V1180）
   - entity/RoleProfile* 改动相关的登录与权限入口

---

**部署完成时间**：2026-07-30 22:09:25 CST
**部署执行者**：Trae Agent（主工作区 `/Users/user/xiyu/worktrees/trae`）
**部署报告版本**：v1.0
