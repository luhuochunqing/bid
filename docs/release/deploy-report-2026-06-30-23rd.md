# 第 23 次部署报告

> **部署日期**：2026-06-30  
> **Release ID**：`c32d80aba-api8080`  
> **部署类型**：常规部署（含 Flyway 迁移）  
> **部署结果**：✅ 成功

## 一、部署概览

| 项目 | 值 |
|---|---|
| Release ID | `c32d80aba-api8080` |
| 激活时间 | 2026-06-30 21:29:30 CST |
| 上一次部署 | `326869f88-api8080`（第 22 次，2026-06-30 20:02:40 CST） |
| 增量 commit | 23 个（含 PR !1410-!1418） |
| 新增迁移 | V1121、V1122、V1123（3 个，均有 rollback U1121/U1122/U1123） |
| 部署耗时 | 约 6 分钟（含打包 22s + 上传 + 部署 + 验证） |
| 健康检查 | 第 1 次通过（无 Kafka readiness 延迟） |
| 回滚状态 | 未需要 |

## 二、基线信息

| 项目 | 值 |
|---|---|
| Worktree | `/Users/user/xiyu/worktrees/trae` |
| 任务分支 | `agent/trae/deploy-22nd-report` |
| 部署 commit | `c32d80aba5361fb190d90a7d716b1e66b349de1a` |
| GitHub 镜像 | 已同步（从落后 23 commit 到完全一致） |
| 服务器 | `172.16.38.78`（winbid-01.test） |
| 后端服务 | `xiyu-bid-backend`（systemd） |
| 后端端口 | 8080 |

## 三、PR 列表

| PR | 描述 |
|---|---|
| !1410 | fix(permission): CO-394 知识库5模块后端权限注解统一为 hasAuthority + RoleProfileCatalog 权限点 |
| !1411 | docs(release): 第 22 次部署报告 |
| !1412 | fix(CO-406): CA 信息详情从右侧抽屉改为居中弹窗 |
| !1413 | fix(casework): CO-428 项目档案下载/导出文件包修复 ZipException duplicate entry |
| !1414 | fix(casework): CO-429 项目档案详情抽屉下载文件包报错 |
| !1415 | fix(personnel-import): CO-419 修复批量导入模板无表头 + 模板字段与新增表单对齐 |
| !1416 | feat(CO-417): 人员证书模块操作日志结构化字段级 diff 整改 |
| !1417 | fix(initiation): 修正立项 AI 风险评估规则为基于客户信息倾向性判定 |
| !1418 | fix-brand-auth-upload-relative-path: 修复上传附件 500 - MultipartFile 相对路径陷阱 |

## 四、改动范围

- **多个文件变更**（涉及后端、前端、文档、迁移）
- **3 个新 Flyway 迁移**（V1121、V1122、V1123，均有配套 U1121/U1122/U1123 rollback）
- **权限治理 CO-394 系列整改**（核心改动）：
  - CO-394-A 品牌授权 Controller 权限注解切换为 hasAuthority + RoleProfileCatalog 常量
  - CO-394-B 人员证书 Controller 权限注解切换为 hasAuthority + personnel.manage 权限点
  - CO-394-C 业绩管理 Controller 权限注解切换为 hasAuthority + performance.manage 权限点
  - CO-394-D 资质证书 Controller 权限注解切换为 hasAuthority + qualification.manage 权限点
  - 知识库 5 模块后端权限注解统一为 hasAuthority + RoleProfileCatalog 权限点
- **3 个新权限点迁移**：
  - V1121 为 bid-TeamLeader、/bidAdmin、bid-Team 追加 `personnel.manage` 权限点
  - V1122 为 bid-TeamLeader、/bidAdmin、bid-Team 追加 `performance.manage` 权限点
  - V1123 为 bid-TeamLeader、/bidAdmin、bid-Team 追加 `qualification.manage` 权限点
  - 均采用 `CASE WHEN ... LIKE ... THEN ... ELSE CONCAT ...` 幂等模式，防重复追加
  - admin 角色拥有 'all' 权限，运行时动态展开，无需修改
- **业务修复**：
  - CO-406 CA 信息详情从右侧抽屉改为居中弹窗（!1412）
  - CO-428 项目档案下载/导出文件包修复 ZipException duplicate entry（!1413）
  - CO-429 项目档案详情抽屉下载文件包报错（!1414）
  - CO-419 人员批量导入模板无表头修复 + 模板字段与新增表单对齐（!1415）
  - CO-417 人员证书模块操作日志结构化字段级 diff 整改（!1416）
  - 立项 AI 风险评估规则修正为基于客户信息倾向性判定（!1417）
  - brand-auth 上传附件 500 修复 - MultipartFile 相对路径陷阱（!1418）
- **回滚脚本补齐**：b728ee241 补齐 V1121/V1122/V1123 回滚脚本 U1121/U1122/U1123

## 五、Flyway 预检结果

### Step 1: 服务器 validate（部署前）

```
VALIDATE OK - all checksums match
Successfully validated 184 migrations (execution time 00:00.084s)
```

### Step 2: DB 已应用版本 vs 源码最新版本

| 维度 | 版本 |
|---|---|
| DB 已应用最新 | V1120（expand tender info capacity to 20000） |
| 源码最新 | V1123（add qualification manage permission） |
| 待应用 | V1121、V1122、V1123 |

### Step 3: remote-deploy.sh 内置 validate

```
VALIDATE OK - all checksums match
✅ Flyway validate 通过（仅 pending 新迁移为预期状态）
```

### 迁移文件安全性

| 迁移 | 类型 | 幂等 | rollback |
|---|---|---|---|
| V1121 | UPDATE ... CASE WHEN LIKE | ✅ 幂等 | U1121 ✅ |
| V1122 | UPDATE ... CASE WHEN LIKE | ✅ 幂等 | U1122 ✅ |
| V1123 | UPDATE ... CASE WHEN LIKE | ✅ 幂等 | U1123 ✅ |

## 六、部署步骤

| 步骤 | 结果 | 备注 |
|---|---|---|
| 1. 早操三连 | ✅ | dev-env.sh + sync-env.sh + check-git-wrapper.sh 全通过 |
| 2. 基线确认 | ✅ | HEAD c32d80aba 与 origin/main 一致，工作区干净 |
| 3. 服务器现状 | ✅ | 上一版本 326869f88-api8080，health UP |
| 4. Flyway 预检 3 步 | ✅ | validate OK + DB 版本对比 + remote-deploy 内置 |
| 5. 本地打包 | ✅ | RELEASE_ID=c32d80aba-api8080，VITE_API_BASE_URL= 同源构建 |
| 6. 产物校验 | ✅ | 137M tar.gz，jar 内 186 迁移文件无重复，V1121/V1122/V1123 齐全 |
| 7. 上传 + 部署 | ✅ | scp + remote-deploy.sh（SYSTEMCTL_SUDO=true） |
| 8. 后端重启 | ✅ | 2026-06-30 21:29:30 CST active (running) |
| 9. 健康检查 | ✅ | remote-deploy.sh 内置健康检查通过 |
| 10. 迁移应用验证 | ✅ | V1121/V1122/V1123 success=1，21:29:36 应用 |
| 11. Smoke 测试 | ✅ | health 200, readiness 200, 400/403/401 路由验证 |
| 12. 前端验证 | ✅ | / 200, /login 200, 入口 assets/index-ZsVO5ACM.js 一致 |
| 13. GitHub 同步 | ✅ | 从落后 23 commit 到完全一致 |
| 14. 配置清理检查 | ⚠️ | MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always 延续保留 |

### 打包事故与恢复

- **首次打包失败**：`Failed to delete backend/target`（target 目录残留 jacoco.exec + surefire-reports 文件锁）
- **恢复方式**：手动 `rm -rf backend/target` 后重新打包成功
- **根因推测**：前序会话测试运行的 JaCoCo 残留文件锁，无 Java 进程占用
- **建议**：打包前可增加 `rm -rf backend/target` 预清理步骤

## 七、验证结果

### 健康检查

```
status: UP
components:
  aiProvider: UP (doubao, deepseek-v3-2-251201, apiKeyConfigured: true)
  db: UP (MySQL, isValid())
  diskSpace: UP (free: 45736783872)
  jwt: UP (HMAC-SHA256, secretLength: 64, STRONG)
  livenessState: UP
  ping: UP
  readinessState: UP
  redis: UP (6.2.19)
  sidecar: UP (http://localhost:8000, reachable)
```

### Readiness 状态

```
status: UP
readinessState: UP
```

**注意**：本次未出现 Kafka SDK readiness 延迟（第 8/9/10/13/15 次历史问题），第 1 次直接通过。

### 迁移应用验证

| version | description | success | installed_on |
|---|---|---|---|
| 1121 | add personnel manage permission | 1 | 2026-06-30 21:29:36 |
| 1122 | add performance manage permission | 1 | 2026-06-30 21:29:36 |
| 1123 | add qualification manage permission | 1 | 2026-06-30 21:29:36 |

### Smoke 测试（400/403/401 替代验证）

> Admin 密码未授予，使用 HTTP 状态码替代验证接口路由（第 6 次起固化策略）。

| 端点 | 期望 | 实际 | 结果 |
|---|---|---|---|
| GET /actuator/health | 200 UP | 200 UP | ✅ |
| GET /actuator/health/readiness | 200 UP | 200 UP | ✅ |
| POST /api/auth/login (空 body) | 400 | 400 | ✅ |
| GET /api/projects | 403 | 403 | ✅ |
| GET /api/integration/crm/health | 401 | 401 | ✅ |
| GET / | 200 | 200 | ✅ |
| GET /login | 200 | 200 | ✅ |

### 前端入口一致性

- 期望：`assets/index-ZsVO5ACM.js`
- 实际：`assets/index-ZsVO5ACM.js`
- ✅ 一致

## 八、GitHub 镜像同步

| 项目 | 值 |
|---|---|
| 同步前落后 | 23 个 commit |
| 同步后状态 | 完全一致 |
| Gitee main | `c32d80aba5361fb190d90a7d716b1e66b349de1a` |
| GitHub main | `c32d80aba5361fb190d90a7d716b1e66b349de1a` |
| 同步命令 | `bash scripts/sync-to-github.sh` |

## 九、回滚信息

| 项目 | 值 |
|---|---|
| 回滚目标 | `326869f88-api8080`（第 22 次） |
| 回滚脚本 | U1121、U1122、U1123（仅恢复权限点，无数据丢失风险） |
| DB 备份 | `/opt/xiyu-bid/db-backups/winbid-c32d80aba-*.sql.gz` |
| 前端旧产物 | `/opt/xiyu-bid/releases/326869f88-api8080/` |
| 回滚方式 | 切换 deployed-release.json + 重启服务 + 执行 U1121/U1122/U1123 |
| 回滚状态 | 就绪（未需要） |

## 十、经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| 1. Flyway 预检 3 步法 | ✅ 全部执行，validate OK |
| 2. Readiness 延迟容忍 | ✅ 容忍窗口预留，本次未延迟 |
| 3. 生产前端同源构建 | ✅ VITE_API_BASE_URL= 显式设空 |
| 4. Smoke 400/403/401 替代验证 | ✅ admin 密码未知，替代策略执行 |
| 5. GitHub 镜像同步 | ✅ 部署后同步 |
| 6. 临时调试配置清理 | ⚠️ SHOW_DETAILS=always 延续保留（第 13/14/15/23 次决定） |
| 7. 幂等迁移设计 | ✅ V1121/V1122/V1123 采用 CASE WHEN LIKE 幂等模式 |
| 8. systemctl sudo | ✅ SYSTEMCTL_SUDO=true 默认启用 |
| 12. rollback 脚本命名规范 | ✅ U1121/U1122/U1123 命名正确 |
| 16. Mac HTTP_PROXY 502（新沉淀） | ⚠️ 外部访问 172.16.38.78:8080 因代理返回 502，绕过后正常 |

## 十一、风险提示

1. **权限点迁移影响范围**：V1121/V1122/V1123 为 bid-TeamLeader、/bidAdmin、bid-Team 三个角色追加 manage 权限点。UAT 阶段需验证：
   - 投标专员（bid-Team）能否执行人员/业绩/资质管理写操作
   - 行政人员（bid-administration）是否仅保留 qualification.view 只读（不应有 manage）
   - 跨部门协同人员（bid-otherDept）是否不受影响
2. **CO-394 权限注解切换**：5 模块 Controller 从 hasAnyRole/hasAnyAuthority 切换为 hasAuthority + 权限点。需验证：
   - 旧 ROLE_ 前缀写法已全部替换
   - admin 角色 'all' 权限动态展开覆盖所有 manage 权限点
3. **MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always 延续保留**：生产健康端点暴露详情（含 db/jwt/sidecar 等组件详情）。如后续需收紧安全，可改为 `never` 并重启后端。
4. **Mac HTTP_PROXY 502 复发**：外部访问生产服务器时需绕过代理（`curl --noproxy '*'` 或 `unset HTTP_PROXY`），否则 502 误判。该问题在第 19 次后多次复发，建议沉淀为第 16 个教训。

## 十二、部署确认清单

| 检查项 | 结果 |
|---|---|
| 早操三连执行 | ✅ |
| 基线与 origin/main 一致 | ✅ |
| Flyway 预检 3 步通过 | ✅ |
| 新增迁移有配套 rollback | ✅ U1121/U1122/U1123 |
| 打包产物校验通过 | ✅ 186 迁移文件无重复 |
| remote-deploy.sh 部署成功 | ✅ |
| 健康检查 UP | ✅ |
| Readiness UP | ✅ |
| 迁移 V1121/V1122/V1123 应用 | ✅ |
| Smoke 测试通过 | ✅ 400/403/401 路由验证 |
| 前端入口一致 | ✅ assets/index-ZsVO5ACM.js |
| GitHub 镜像同步 | ✅ 完全一致 |
| 配置清理检查 | ⚠️ SHOW_DETAILS=always 延续保留 |
| 部署报告生成 | ✅ |

---

**部署完成时间**：2026-06-30 21:35 CST  
**部署执行者**：trae agent  
**回滚就绪**：是
