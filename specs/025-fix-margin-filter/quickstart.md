# Quickstart: 保证金看板状态筛选修复

**Date**: 2026-07-02
**Feature**: [spec.md](./spec.md) | [plan.md](./plan.md)

## 前置条件

- 主工作区 `/Users/user/xiyu/worktrees/trae` 已启动开发环境（前端 1323 / 后端 18089 / MySQL）
- 当前任务分支：`agent/codex/fix-margin-filter-not-working`
- 已执行早操 sync-env.sh

## 开发验证步骤（TDD 顺序）

### Step 1: 先写失败测试（Red）

在 `backend/src/test/java/com/xiyu/bid/resources/service/MarginQuerySupportMysqlIntegrationTest.java` 中新增 3 个测试：

```java
@Test
void filterByStatusPending_shouldIncludeInitBranchRows_withNullExpReturnDate() {
    // 造数据：1 条 need_deposit=YES 但无 fees 的立项记录
    // 调用：filterByStatus("PENDING")
    // 断言：返回结果包含该 init 占位行
}

@Test
void filterByStatusReturned_shouldIncludeCancelledFees() {
    // 造数据：1 条 fees.status=CANCELLED, fee_type=BID_BOND
    // 调用：filterByStatus("RETURNED")
    // 断言：返回结果包含该 CANCELLED 行
}

@Test
void filterByStatusOverdue_shouldExcludeInitBranchRows_withNullExpReturnDate() {
    // 造数据：1 条 fees.status=PAID, fee_date < NOW() + 1 条 init 占位行
    // 调用：filterByStatus("OVERDUE")
    // 断言：只返回 fees 行，不返回 init 占位行
}
```

验证测试失败：
```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest#filterByStatusPending_shouldIncludeInitBranchRows_withNullExpReturnDate+filterByStatusReturned_shouldIncludeCancelledFees+filterByStatusOverdue_shouldExcludeInitBranchRows_withNullExpReturnDate
# 预期：3 个测试全部 FAIL（Red 阶段）
```

### Step 2: 修复代码（Green）

修改 `backend/src/main/java/com/xiyu/bid/resources/service/MarginQuerySupport.java` 的 `appendStatusFilter` 方法：

```java
// 修复前（Bug）
case "RETURNED":
    sql.append(" AND m.status = 'RETURNED'");
    break;
case "PENDING":
    sql.append(" AND m.status NOT IN ('RETURNED','CANCELLED')"
            + " AND m.exp_return_date >= NOW()");
    break;

// 修复后
case "RETURNED":
    sql.append(" AND m.status IN ('RETURNED','CANCELLED')");
    break;
case "PENDING":
    sql.append(" AND m.status NOT IN ('RETURNED','CANCELLED')"
            + " AND (m.exp_return_date IS NULL OR m.exp_return_date >= NOW())");
    break;
```

验证测试通过：
```bash
cd /Users/user/xiyu/worktrees/trae/backend
mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest#filterByStatusPending_shouldIncludeInitBranchRows_withNullExpReturnDate+filterByStatusReturned_shouldIncludeCancelledFees+filterByStatusOverdue_shouldExcludeInitBranchRows_withNullExpReturnDate
# 预期：3 个测试全部 PASS（Green 阶段）
```

### Step 3: 回归验证（Refactor / 完整验证）

```bash
cd /Users/user/xiyu/worktrees/trae/backend

# 1. 跑 margin 模块全部测试
mvn test -Dtest=MarginQuerySupportMysqlIntegrationTest,MarginServiceTest

# 2. 跑架构测试（确认未破坏 FP-Java 边界）
mvn test -Dtest=ArchitectureTest

# 3. 前端构建（确认无前端副作用）
cd /Users/user/xiyu/worktrees/trae
npm run build
```

## 生产环境验证（部署后）

### 验证 SQL（直接查数据库）

```sql
-- 验证根因 1：init 分支行被 PENDING filter 漏掉
-- 修复前：此查询返回的行在 ?status=PENDING 筛选下不会出现
-- 修复后：此查询返回的行在 ?status=PENDING 筛选下应出现
SELECT COUNT(*) FROM project_initiation_details pid
WHERE pid.need_deposit = 'YES'
  AND pid.deposit_amount > 0
  AND NOT EXISTS (
    SELECT 1 FROM fees f2
    WHERE f2.project_id = pid.project_id
      AND f2.fee_type = 'BID_BOND'
      AND f2.status != 'CANCELLED'
  );

-- 验证根因 2：CANCELLED BID_BOND 行存在
-- 修复前：此查询返回的行在 ?status=RETURNED 筛选下不会出现
-- 修复后：此查询返回的行在 ?status=RETURNED 筛选下应出现
SELECT COUNT(*) FROM fees f
WHERE f.fee_type = 'BID_BOND'
  AND f.status = 'CANCELLED';
```

### 验证 API（curl）

```bash
# 1. 登录获取 token
TOKEN=$(curl -s -X POST https://winbid-test.ehsy.com/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"xiaowang","password":"123456"}' | jq -r '.token')

# 2. 不带 status 筛选，记录各标签的行数
curl -s "https://winbid-test.ehsy.com/api/resource/margin?size=100" \
  -H "Authorization: Bearer $TOKEN" | jq '
  .content | group_by(.statusLabel) | map({label: .[0].statusLabel, count: length})
'
# 预期：看到 "未到期" / "已退回" / "已超期" 三个标签的计数

# 3. 筛选 status=PENDING，行数应等于未筛选时 "未到期" 的计数
curl -s "https://winbid-test.ehsy.com/api/resource/margin?size=100&status=PENDING" \
  -H "Authorization: Bearer $TOKEN" | jq '.totalElements'
# 预期：等于上一步 "未到期" 的计数（SC-001）

# 4. 筛选 status=RETURNED，行数应等于未筛选时 "已退回" 的计数
curl -s "https://winbid-test.ehsy.com/api/resource/margin?size=100&status=RETURNED" \
  -H "Authorization: Bearer $TOKEN" | jq '.totalElements'
# 预期：等于上一步 "已退回" 的计数（SC-002）

# 5. 筛选 status=OVERDUE，行数应等于未筛选时 "已超期" 的计数
curl -s "https://winbid-test.ehsy.com/api/resource/margin?size=100&status=OVERDUE" \
  -H "Authorization: Bearer $TOKEN" | jq '.totalElements'
# 预期：等于上一步 "已超期" 的计数（SC-003）
```

### 验证 UI（手动）

1. 访问 `https://winbid-test.ehsy.com/resource/margin`
2. 不筛选状态，记录表格中各"状态"标签的行数
3. 依次点击"未到期" / "已退回" / "已超期"筛选
4. 验证每个筛选结果行数 === 未筛选时该标签的行数

## 回滚条件

如果修复后出现以下任一情况，立即回滚：
- `?status=PENDING` 返回行数明显多于未筛选时"未到期"标签行数（可能 IS NULL 分支误伤其他行）
- `?status=RETURNED` 返回行数明显多于未筛选时"已退回"标签行数
- 其他筛选维度（项目名/时间/金额）行为异常
- 架构测试（ArchitectureTest）失败

回滚命令：
```bash
git revert <修复 commit hash>
git push origin agent/codex/fix-margin-filter-not-working
```
