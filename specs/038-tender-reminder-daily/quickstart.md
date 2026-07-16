# Quickstart: 投标关键节点提醒改造验证

**Date**: 2026-07-16
**Feature**: 038-tender-reminder-daily

## 前置条件

- 当前分支：`agent/claude/tender-reminder-daily-notify`
- 工作区：`/Users/user/xiyu/worktrees/claude`
- 主工作区（联调）：`/Users/user/xiyu/worktrees/trae`

## 验证步骤

### 1. 后端单元测试（Red → Green）

```bash
cd /Users/user/xiyu/worktrees/claude/backend
mvn test -Dtest=TenderReminderPolicyTest
```

**预期结果**：
- 所有测试用例通过
- 包含新增用例：
  - `shouldReturnFalseWhenLastNotifiedWithin24Hours`（距上次发送 < 24 小时）
  - `shouldReturnTrueWhenLastNotifiedAtLeast24HoursAgo`（距上次发送 ≥ 24 小时）
- 修改后的用例：
  - 原 `shouldReturnFalseWhenAlreadyNotified` → 改为验证 24 小时内不重复
- 默认值断言：`getDefaultRemindBeforeHours()` 返回 72

### 2. 后端架构测试（无回归）

```bash
cd /Users/user/xiyu/worktrees/claude/backend
mvn test -Dtest=ArchitectureTest
mvn test -Dtest=FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

**预期结果**：全绿，无新增违规

### 3. 后端全量测试（无回归）

```bash
cd /Users/user/xiyu/worktrees/claude/backend
mvn test
```

**预期结果**：全绿

### 4. 前端构建

```bash
cd /Users/user/xiyu/worktrees/claude
npm run build
```

**预期结果**：构建成功，无错误

### 5. 前端数据边界检查

```bash
cd /Users/user/xiyu/worktrees/claude
npm run check:front-data-boundaries
npm run check:doc-governance
npm run check:line-budgets
```

**预期结果**：全绿

### 6. 数据库迁移验证（在主工作区 trae）

> ⚠️ 仅主工作区可启动开发环境

```bash
cd /Users/user/xiyu/worktrees/trae
export XIYU_DEV_CONFIRMED=1
# 启动后端，Flyway 自动执行迁移
./scripts/start-backend.sh
```

**验证迁移生效**：
```sql
-- 检查 DEFAULT 值已变更
SHOW COLUMNS FROM tender_reminder_settings LIKE 'remind_before_hours';
-- 预期：Default 列为 '72'
```

### 7. 前端 UI 验证（在主工作区 trae）

```bash
cd /Users/user/xiyu/worktrees/trae
./scripts/start-frontend.sh
```

**验证步骤**：
1. 访问 `http://127.0.0.1:1323`
2. 登录（如 `admin / XiyuAdmin2026!`）
3. 进入"标讯管理"列表页
4. 选择一个标讯，点击"提醒设置"
5. 点击"添加提醒设置"
6. **验证**："提前提醒时间"下拉框默认显示"提前72小时（3天）"
7. 选择"报名截止提醒"，选择通知对象，保存
8. **验证**：数据库中该记录 `remind_before_hours = 72`

### 8. Git 状态确认

```bash
cd /Users/user/xiyu/worktrees/claude
git status
```

**预期修改文件**：
- `backend/src/main/java/com/xiyu/bid/tenderreminder/domain/TenderReminderPolicy.java`
- `backend/src/main/java/com/xiyu/bid/tenderreminder/entity/TenderReminderSetting.java`
- `backend/src/main/java/com/xiyu/bid/tenderreminder/dto/CreateReminderRequest.java`
- `backend/src/main/java/com/xiyu/bid/tenderreminder/job/TenderReminderJob.java`
- `backend/src/main/resources/db/migration-mysql/V<版本>__tender_reminder_default_72h.sql`（新增）
- `backend/src/main/resources/db/rollback/migration-mysql/U<版本>__tender_reminder_default_72h.sql`（新增）
- `backend/src/test/java/com/xiyu/bid/tenderreminder/domain/TenderReminderPolicyTest.java`
- `src/views/Bidding/list/components/useReminderSettings.js`
- `specs/038-tender-reminder-daily/*`（spec/plan/research/data-model/quickstart/tasks）

## 完成判定

所有上述验证步骤通过后，可提交 PR：
- 后端测试全绿
- 前端构建成功
- 数据库迁移生效
- UI 默认值显示正确
- Git 状态符合预期
