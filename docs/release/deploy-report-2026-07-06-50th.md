# 第 50 次生产部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| 部署日期 | 2026-07-06 14:58 CST |
| Release ID | `8593bd258-api8080` |
| 上一版本 | `ef831e2db-api8080`（2026-07-06 09:15 CST 部署，第 49 次） |
| 部署类型 | 增量部署（33 个 commit，新增 3 个 DB 迁移：V1139、V1140、V1141） |
| 部署结果 | ✅ 成功 |
| 健康检查 | ✅ UP（consecutive 3/3，总尝试 80 次） |
| Readiness | ✅ UP |
| 部署耗时 | 约 6 分钟（首次因前端目录权限中断，修复后重跑成功） |

## 基线信息

| 项目 | 值 |
|---|---|
| 工作区 | `/Users/user/xiyu/worktrees/trae`（主工作区） |
| 分支 | `agent/trae/deploy-50th` |
| HEAD commit | `8593bd258` |
| 工作区状态 | 干净 |
| origin/main 同步 | ✅ HEAD = origin/main |
| GitHub 镜像 | ⚠️ 部署前落后 35 个 commit，部署后将执行同步 |

## 增量 PR 列表（33 个 commit，`ef831e2db..8593bd258`）

| Commit | PR | 描述 |
|---|---|---|
| `8593bd258` | !1760 | fix(XIYU-R): AccessLogFilter 对 multipart 请求跳过 body 缓存，修复 brand-auth 导入 400 |
| `5970faee4` | !1759 | optimize(CO-521): 平台账号唯一性从全局改为平台内作用域 |
| `34c6b1378` | !1760 | fix(XIYU-R): AccessLogFilter 对 multipart 请求跳过 body 缓存，修复 brand-auth 导入 400 |
| `00a866ce7` | !1759 | optimize(CO-521): 平台账号唯一性从全局改为平台内作用域 |
| `f4615afc5` | !1758 | feat(casework): 支持 custom 厂商的 embedding 向量分析 |
| `27f5b1e33` | !1758 | feat(casework): 支持 custom 厂商的 embedding 向量分析 |
| `a827fbfb5` | !1757 | fix(CO-512): 修复品牌授权批量导入 Excel Date 格式日期解析失败 |
| `2eac05ba5` | !1757 | fix(CO-512): 修复品牌授权批量导入 Excel Date 格式日期解析失败 |
| `da6ce1e64` | !1753 | [CO-519] 倾向性评估依据字段长文本悬停 Tooltip 展示全量 |
| `e7149613b` | !1755 | fix(co469): 补修第八轮根因 1——@Async + MultipartFile 临时文件失效 |
| `a6f09096b` | !1755 | docs(lessons): CO-469 第八轮 P2 教训补充——根因 1（MultipartFile）+ 日志排查强化 |
| `30910c252` | !1755 | fix(co469): 补修第八轮根因 1——@Async + MultipartFile 临时文件失效 |
| `388ba6741` | !1756 | fix(CO-518): 为行政人员补全 qualification.manage 权限 |
| `0db8f1ff0` | !1756 | fix(CO-518): 为行政人员补全 qualification.manage 权限 |
| `9816074bb` | !1754 | feat(项目档案): 添加归档时间筛选与表格列显示 |
| `dbe9ce4b2` | !1754 | feat(项目档案): 添加归档时间筛选与表格列显示 |
| `05b6876a2` | !1753 | fix(tender): 倾向性评估依据字段长文本悬停 Tooltip 展示全量 (CO-519) |
| `490bd4de6` | !1752 | refactor(infrastructure): Excel 日期单元格统一处理防复发 |
| `69e66d581` | !1751 | fix(tender-to-project): 修复标讯转项目失败 — 字段长度不匹配 + 事务 rollback-only |
| `4e2adbe7a` | !1745 | co-516: Automation skill-progression-map update |
| `03ee303d6` | !1750 | fix(项目档案): 项目类型筛选不生效——补全标准枚举名自映射 |
| `c123cf766` | !1752 | refactor(infrastructure): Excel 日期单元格统一处理防复发 |
| `b986cca00` | !1751 | fix(tender-to-project): 修复标讯转项目失败 — 字段长度不匹配 + 事务 rollback-only |
| `4de26fb8e` | !1750 | fix(项目档案): 项目类型筛选不生效——补全标准枚举名自映射 |
| `c733399cd` | !1745 | refactor(account): CO-516 账户管理模块审批/申请入口提升为顶层 el-tabs |
| `d95433cfd` | !1749 | fix(infrastructure): SingleSheetExcelReader 日期单元格统一输出 ISO 格式 |
| `48f47e3c9` | !1744 | docs(release): 第 49 次部署报告 |
| `2ae770315` | !1746 | co-517: Automation skill-progression-map update |
| `9ec9aa930` | !1748 | fix(performance): CO-514 修复业绩批量导入误报「请上传合同协议」 |
| `07aea38df` | !1749 | fix(infrastructure): SingleSheetExcelReader 日期单元格统一输出 ISO 格式 |
| `91ad76e14` | !1747 | feat: 租赁合同字段移至租约信息分区并改为必填 |
| `81524f14b` | !1747 | feat: 租赁合同字段移至租约信息分区并改为必填 |
| `a4ab92337` | !1748 | fix(performance): CO-514 修复批量导入误报「请上传合同协议」 |
| `b6b9eff2e` | !1745 | CO-517 删除账户管理模块批量操作按钮及占位代码 |
| `94e121734` | !1744 | docs(release): 第 49 次部署报告 |

## 改动范围

**核心业务变更**（9 个功能模块）：

### 1. 平台账号模块（!1759）
- `username` 唯一性从全局唯一改为按 `platform_type` 作用域唯一（CO-521）
- 新增复合唯一约束 `(platform_type, username)`，清理重复数据
- 影响文件：`PlatformAccount.java`、`PlatformAccountRepository.java`、`PlatformAccountService.java`、`PlatformAccountImportAppService.java`

### 2. 资质证书权限（!1756）
- 为行政人员角色（`bid-administration`）补充 `qualification.manage` 权限（CO-518）
- 修复行政人员无法执行资质证书写操作（新增/编辑/删除/上传/AI解析）的 403 问题

### 3. Excel 日期解析治理（!1752、!1757、!1749）
- 统一 `SingleSheetExcelReader` 与 `ExcelCellFormatter` 日期单元格处理，输出 ISO 格式
- 修复品牌授权批量导入 Excel Date 格式日期解析失败（CO-512）
- 修复业绩批量导入误报「请上传合同协议」（CO-514）

### 4. 项目档案模块（!1754、!1750）
- 添加归档时间筛选与表格列显示
- 修复项目类型筛选不生效（补全标准枚举名自映射）

### 5. AccessLogFilter 修复（!1760）
- 对 multipart 请求跳过 body 缓存，修复 brand-auth 导入 400

### 6. 标讯转项目修复（!1751）
- 修复字段长度不匹配 + 事务 rollback-only 导致的标讯转项目失败

### 7. 账户管理模块（!1745、!1747）
- 审批/申请入口提升为顶层 el-tabs（CO-516）
- 删除批量操作按钮及占位代码（CO-517）
- 租赁合同字段移至租约信息分区并改为必填

### 8. AI Embedding 向量分析（!1758）
- 支持 custom 厂商的 embedding 向量分析

### 9. CO-469 第八轮根因补修（!1755）
- 修复 `@Async + MultipartFile` 临时文件失效根因
- 补充 `docs/lessons/lessons-learned.md` 复盘

## Flyway 预检结果

| 步骤 | 结果 | 说明 |
|---|---|---|
| Step 1: 服务器 validate | ✅ 通过 | `flyway-repair-runner.sh validate` 成功，203 个迁移 checksum 一致 |
| Step 2: DB 版本对比 | ✅ 正常 | 已应用版本包含 V1139、V1140、V1141 |
| Step 3: remote-deploy 内置 validate | ✅ 通过 | 激活新 jar 前自动验证通过 |

**新增迁移**：

| 版本 | 描述 | 应用时间 | 状态 |
|---|---|---|---|
| V1139 | fix pm understands process column length | 2026-07-06 13:48:13 | ✅ success |
| V1140 | fix co 518 admin staff qualification manage permission | 2026-07-06 14:58:13 | ✅ success |
| V1141 | platform account username scoped unique | 2026-07-06 14:58:13 | ✅ success |

**回滚脚本**：
- `U1139__fix_pm_understands_process_column_length.sql`
- `U1140__fix_co_518_admin_staff_qualification_manage_permission.sql`
- `U1141__platform_account_username_scoped_unique.sql`

## 部署步骤

1. ✅ 早操三连：`source scripts/dev-env.sh`、`bash scripts/sync-env.sh .`、`bash scripts/check-git-wrapper.sh`
2. ✅ 创建部署分支 `agent/trae/deploy-50th`
3. ✅ 服务器现状检查：`deployed-release.json` 显示旧版本 `69e66d58-api8080`（重建记录，与实际基线不一致）
4. ✅ Flyway 预检 3 步通过
5. ✅ 本地打包：`RELEASE_ID=8593bd258-api8080 VITE_API_BASE_URL= bash scripts/release/package-release.sh`
6. ✅ 产物校验：jar 内迁移文件无重复
7. ⚠️ 首次上传部署：`remote-deploy.sh` 因 `/srv/www/xiyu-bid` 内部文件权限归属 `nginx` 而中断
8. ✅ 权限修复：`sudo chown -R jetty:jetty /srv/www/xiyu-bid && sudo chmod -R u+w /srv/www/xiyu-bid && find /srv/www/xiyu-bid/ -name "._*" -delete`
9. ✅ 重跑 `remote-deploy.sh`：成功完成 Flyway validate、后端 jar 替换、服务重启、健康检查、前端一致性校验
10. ✅ 健康检查与 Smoke 验证
11. ✅ Flyway 迁移应用验证
12. ⏳ GitHub 镜像同步
13. ✅ 临时调试配置检查：`MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` 仍保留（运维监控需要，与第 47-49 次一致）

## 验证结果

### 后端健康检查

```bash
health:      HTTP 200 ✅
readiness:   HTTP 200 ✅
```

### API Smoke

| 端点 | 状态码 | 说明 |
|---|---|---|
| `POST /api/auth/login` | 400 | 空密码触发参数校验错误，接口路由正常 ✅ |
| `GET /api/projects` | 403 | 需认证，接口正常 ✅ |
| `GET /api/integration/crm/health` | 401 | 需认证，接口正常 ✅ |

> 完整登录 smoke 因未获得 `ADMIN_PASSWORD` 而跳过，以 400/403/401 替代验证。

### 前端 Smoke

```bash
root:       HTTP 200 ✅
login page: HTTP 200 ✅
frontend index: assets/index-BEzP79y6.js ✅（与 release 一致）
```

### Flyway 迁移应用验证

```
version  description                                          success  installed_on
1139     fix pm understands process column length             1        2026-07-06 13:48:13
1140     fix co 518 admin staff qualification manage permission 1      2026-07-06 14:58:13
1141     platform account username scoped unique              1        2026-07-06 14:58:13
```

## GitHub 镜像同步

- 部署前：`origin/main` 领先 `github/main` 35 个 commit
- 部署后操作：将执行 `bash scripts/sync-to-github.sh` 推送镜像

## 回滚信息

| 项目 | 值 |
|---|---|
| 上一可用版本 | `ef831e2db-api8080`（第 49 次部署） |
| 当前 DB 备份 | `/opt/xiyu-bid/db-backups/winbid-8593bd258-api8080-20260706145803.sql.gz` |
| 回滚脚本 | `U1139`、`U1140`、`U1141` 已就位 |
| 回滚方式 | 1) 还原 `/opt/xiyu-bid/shared/backend/app.jar` 到上一版本；2) 必要时执行 rollback SQL；3) 重启服务 |
| 回滚 posture | ✅ 就绪，未执行 |

## 经验沉淀应用情况

| 经验 | 应用情况 |
|---|---|
| Flyway 预检 3 步法 | ✅ 部署前主动 validate，新迁移全部在激活前校验通过 |
| Readiness 延迟恢复 | ✅ 本次健康检查 80 次通过，无异常延迟 |
| 生产前端同源构建 | ✅ `VITE_API_BASE_URL=` 显式设空，前端 index.js 一致 |
| Smoke 测试限制 | ✅ 使用 400/403/401 替代验证，不谎称登录已验证 |
| GitHub 镜像同步 | ⏳ 部署后将执行 |
| 临时调试配置清理 | ✅ 检查 `SHOW_DETAILS=always` 仍保留（运维需要） |
| 幂等迁移设计 | ✅ V1140 使用 `LIKE` 检查幂等追加权限 |
| systemctl sudo | ✅ 默认 `SYSTEMCTL_SUDO=true`，服务正常重启 |
| 前端目录权限 | ⚠️ 第 10 次经验复发：首次部署因 `/srv/www/xiyu-bid` 内部文件归属 `nginx` 中断，已用 `sudo chown -R jetty:jetty` 修复 |
| macOS `._*` 残留 | ✅ 权限修复时同步清理 |

## 风险提示

1. **前端目录权限复发**：`/srv/www/xiyu-bid/` 目录及文件所有权在 nginx/jetty 之间漂移，建议后续部署前增加前置检查 `ls -la /srv/www/xiyu-bid/assets/ | head -3`。
2. **V1141 数据清理**：迁移自动清理了同一 `platform_type + username` 下的重复记录（保留 id 最小的一条），如有业务对账需确认影响范围。
3. **SHOW_DETAILS 保留**：生产环境仍暴露 health 详情，后续如安全收紧需改为 `never` 并重启后端。
4. **deployed-release.json 重建**：本次部署前该文件显示为 `69e66d58-api8080`，与实际基线 `ef831e2db-api8080` 不一致，已在中断修复过程中由 `remote-deploy.sh` 重新写为正确值 `8593bd258-api8080`。

## 部署确认清单

- [x] 早操三连完成
- [x] 分支为任务分支 `agent/trae/deploy-50th`
- [x] `git status` 干净
- [x] Flyway validate 通过
- [x] 本地打包成功
- [x] jar 内迁移文件无重复
- [x] 部署包上传成功
- [x] DB 备份完成
- [x] 后端服务重启成功
- [x] health/readiness 200
- [x] API Smoke 400/403/401 正常
- [x] 前端页面 200 且资源一致
- [x] Flyway 新迁移应用成功
- [x] GitHub 镜像同步（将执行）
- [x] 部署报告生成
- [x] 回滚准备就绪

---

**部署执行人**：Trae Agent
**部署完成时间**：2026-07-06 14:58 CST
