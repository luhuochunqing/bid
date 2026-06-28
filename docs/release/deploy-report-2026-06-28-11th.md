# 第十一次部署报告

## 部署概览

| 项目 | 值 |
|---|---|
| **部署时间** | 2026-06-28 19:30 CST (重新部署) |
| **部署人** | Trae AI Agent |
| **目标服务器** | 172.16.38.78 (winbid-01.test) |
| **应用端口** | 8080 |
| **部署状态** | ✅ 成功 |

## 基线信息

| 项目 | 值 |
|---|---|
| **部署 commit** | `e261db3bd` (!1291) |
| **第十次部署基线** | `92b7dadb3` |
| **新增 commit 数** | 32 个 |
| **新增迁移数** | 0 个（V1106 在第十次部署热修复中已执行） |

## 第十次部署后合入的 PR（14 个）

| PR 号 | 主题 | 分类 |
|---|---|---|
| !1277 | CO-302 CRM 反查路径改造为 25338→25259 两步链路 | CRM 集成 |
| !1278 | CO-362 修复 OssPermissionCache 双构造歧义导致 Redis 持久化失效 | 权限修复 |
| !1279 | 防复发 - Flyway 迁移目录守卫 + jar 内重复版本校验（P0） | Flyway 防护 |
| !1280 | CO-381 rework 选中市级后弹窗不关闭 | 前端修复 |
| !1281 | 防复发 P1 - Flyway 配置守卫 + DB 同步检查 | Flyway 防护 |
| !1282 | task-attachment-remove-before-save: skill 更新 | 自动化 |
| !1283 | 防复发 P2 - Flyway 语法守卫 + 逃生阀追踪 | Flyway 防护 |
| !1284 | bid-attachment-remove: skill 更新 | 自动化 |
| !1285 | 第10次部署报告追加事故复盘与防复发措施 | 文档 |
| !1286 | CO-375 终态项目允许上传文档（复盘阶段 409 修复） | 工作流修复 |
| !1287 | CO-379 标讯创建事务回滚污染修复 + 任务扩展字段权限补齐 | 事务/权限修复 |
| !1288 | CO-373 标书制作阶段投标负责人/辅助人员选择审核人权限修复 | 权限修复 |
| !1289 | 修复 10 个 @WebMvcTest 的 ApplicationContext 加载失败 | 测试修复 |
| !1290 | start-frontend.sh 默认使用 localhost 避免 SameSite cookie | 开发环境 |
| !1291 | 优化表格显示 - 内容行自动换行、移除 show-overflow-tooltip | UI 优化 |

## Flyway 预检与处置

### 发现的问题

| # | 问题 | 状态 | 处置 |
|---|---|---|---|
| 1 | **V1103 checksum=NULL**（手动 INSERT 遗留） | ✅ 已修复 | `flyway repair` 对齐 checksum → `-1061033907` |
| 2 | **V1106 checksum=NULL**（第十次部署热修复手动执行） | ✅ 已修复 | `flyway repair` 对齐 checksum → `1299524665` |

### 修复后验证
- ✅ Flyway validate 通过：170 个迁移（含 1 个 baseline）全部 checksum match
- ✅ `tasks.created_by` 列已存在：V1106 迁移实际已生效
- ✅ repair 备份已生成：`/opt/xiyu-bid/backups/flyway-history/flyway_schema_history-20260628-190211.sql`

## 部署步骤

### 第一次部署（19:10，有问题）
1. 打包时工作目录在 `origin/main` detached HEAD
2. 前端构建正确（基于最新代码）
3. 后端 JAR 构建正确，但 git.properties 元数据因 worktree .git 指向问题显示旧 commit
4. **误判**：曾怀疑代码版本不正确，实际 class 文件已基于最新源码编译

### 第二次部署（19:30，最终部署）
1. **打包**：基于 `agent/trae-init` 锚点分支（已 rebase 到 `2531bd5c1`）
   - 前端构建：`index-CKjwtG6a.js`
   - 后端构建：22s，class 文件编译时间 19:28
   - JAR 内迁移文件：169 个 V*.sql + 1 个 B73 baseline

2. **上传**：JAR + 前端文件分别上传到 `/opt/xiyu-bid/releases/e261db3bd-api8080/`

3. **激活**：
   - 前端切换：`/srv/www/xiyu-bid` → 新版本
   - JAR 更新：`/opt/xiyu-bid/shared/backend/app.jar`
   - 写入部署记录

4. **重启**：后端服务重启

5. **健康检查**：
   - 等待 Kafka SDK 初始化（约 2-3 分钟）
   - 最终状态：所有组件 UP

### 代码版本验证
| 验证项 | 结果 | 说明 |
|---|---|---|
| JAR 内 OssPermissionCache.class | ✅ 包含 StringRedisTemplate | !1278 CO-362 修复已包含 |
| JAR 内 TenderCommandService.class | ✅ 存在，编译时间 19:28 | !1287 CO-379 修复已包含 |
| 前端 index.html | ✅ index-CKjwtG6a.js | 最新前端构建 |
| JAR 内迁移文件数 | ✅ 170 个（169 V + 1 B73） | 与生产 DB 一致 |

## 验证结果

### 后端健康检查
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "jwt": { "status": "UP" },
    "livenessState": { "status": "UP" },
    "ping": { "status": "UP" },
    "readinessState": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

### API Smoke 测试
| 接口 | 结果 | 说明 |
|---|---|---|
| `/api/auth/login` | ✅ 401 | 密码错误是预期（接口正常） |
| `/api/projects` | ✅ 403 | 需要认证（接口正常） |
| `/api/integration/crm/health` | ✅ 401 | 需要认证（接口正常） |

### 前端验证
| 端点 | 结果 |
|---|---|
| `/` (首页) | ✅ 200 |
| `/assets/index-*.js` | ✅ 200 |
| `/login` | ✅ 200 |

## 回滚信息

| 项目 | 值 |
|---|---|
| **回滚版本** | `92b7dadb-v1106fix-api8080` |
| **回滚命令** | `sudo systemctl restart xiyu-bid-backend` + 前端切换 |
| **Flyway 历史备份** | `/opt/xiyu-bid/backups/flyway-history/` |

## 风险提示

1. **Kafka SDK 初始化延迟**：后端重启后 readiness 状态会短暂 OUT_OF_SERVICE（~3 分钟），这是已知行为，不需要干预。

2. **第十次部署后旧版本清理**：部分旧版本（root 用户创建）因权限问题无法删除，可后续手动清理。

## 部署确认

- [x] Flyway 预检通过
- [x] 打包验证通过
- [x] 上传完成
- [x] 部署执行完成
- [x] 后端健康 UP
- [x] API smoke 通过
- [x] 前端验证通过
- [x] 部署报告生成
