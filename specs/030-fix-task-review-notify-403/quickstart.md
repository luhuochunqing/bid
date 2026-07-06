# Quickstart: 修复任务审核通知接收人广播 403

**Feature**: 030-fix-task-review-notify-403
**Date**: 2026-07-06

## 本地验证步骤（开发完成后按此顺序执行）

### 1. 后端单元测试

```bash
cd /Users/user/xiyu/worktrees/zcode/backend

# 跑新增的纯函数单测
mvn test -Dtest=NotificationRecipientFilterTest -q

# 跑修改后的 Service 单测（含新增用例）
mvn test -Dtest=TaskReviewNotificationServiceTest -q

# 全量回归（确保不破坏既有）
mvn test -q
```

**预期**: 全绿。重点核对新增用例：
- `bid-Team` 用户被广播到无权项目时不出现
- admin 用户始终通过
- 过滤后列表为空时安全跳过

### 2. Constitution 守卫检查

```bash
cd /Users/user/xiyu/worktrees/zcode/backend
# ArchUnit 守卫（Constitution I/VI/VII）
mvn -Pjava-quality,java-quality-spotbugs,quality-strict checkstyle:check pmd:check spotbugs:check -q
```

**预期**: 全绿。`NotificationRecipientFilter` 不应触发新增 `Collectors.toMap` 2 参数版本警告（Constitution VII）。

### 3. 前端单测（如本期实现 targetUrl 降级）

```bash
cd /Users/user/xiyu/worktrees/zcode
npm run test:unit -- --filter NotificationPanel 2>&1 | tail -20
# 或直接跑全量
npm run test:unit -q
```

### 4. 端到端手动验证（连接测试服务器 + DB）

> ⚠️ 仅在主工作区 `/Users/user/xiyu/worktrees/trae` 启动开发环境；其他 worktree 不启动。

部署到测试环境后，按以下步骤复现 + 验证修复：

```bash
# 1) SSH 连接测试服务器，确认版本
ssh jetty@172.16.38.78 'sudo systemctl status xiyu-bid-backend | head -5'

# 2) 用 06131 (王晓莉, bid-Team) 登录，观察通知列表
#    预期：收到的所有通知点击后都能正常打开
#    预期：不再有 /api/projects/{162,171,172} 的 403 WARN 日志

# 3) 用另一个 bid-Team 用户（在项目 P 可见范围内）触发任务审核提交
#    预期：06131 不收到该通知（DB 验证）
ssh jetty@172.16.38.78 'bash -s' << 'EOF'
set -a; source <(sudo cat /etc/xiyu-bid/backend.env); set +a
mysql -h winbid-01.test.rds.ehsy.com -P 3306 -u ea_bid --password=$DB_PASSWORD --database=winbid --default-character-set=utf8mb4 -e "
SELECT n.id, n.source_entity_id, n.title, un.user_id, un.created_at
FROM notification n
LEFT JOIN user_notification un ON un.notification_id=n.id AND un.user_id=1471
WHERE n.source_entity_type='PROJECT' AND n.created_at > NOW() - INTERVAL 10 MINUTE
ORDER BY n.id DESC LIMIT 10;"
EOF
```

**预期**：06131 (user_id=1471) 不应该再出现在她无权访问项目的通知接收人列表里。

### 5. 日志验证（按 §23 SOP Layer 2）

修复后再次观察 `/var/log/xiyu-bid/application.json.log`：

```bash
ssh jetty@172.16.38.78 'sudo grep "权限不足 - URI: /api/projects" /var/log/xiyu-bid/application.json.log | grep "User: 06131" | tail -5'
```

**预期**：修复部署时间点之后**不再有** 06131 的项目详情 403 记录。

同时确认过滤逻辑生效（INFO 日志）：

```bash
ssh jetty@172.16.38.78 'sudo grep "TaskReview notification skipped - no accessible recipients" /var/log/xiyu-bid/application.json.log | tail -5'
```

**预期**：当某次任务审核提交的所有候选接收人都对该项目无访问权时，会看到这条 INFO 日志（虽然实际很少发生，因为提交人自己通常有访问权，且排除逻辑只排除提交人本人）。

### 6. Constitution Re-check（编码完成后）

完成编码后回到 `plan.md` 的 Constitution Check 表，重新核对每项：

- [ ] **I. FP-Java**：`NotificationRecipientFilter` 是纯函数（无 Spring 注解、无字段）
- [ ] **III. TDD**：先写测试再写实现（git log 应显示 test commit 在 impl commit 之前）
- [ ] **IV. Split-First**：新增文件行数 < 100；修改后 `TaskReviewNotificationService` < 200 行
- [ ] **VI. Authorization Unification**：未新增 `@PreAuthorize` 白名单
- [ ] **VII. Defensive Collection**：未新增 `Collectors.toMap` 2 参数版本；try-catch 降级保留

### 7. PR 准备

```bash
# 完成所有提交后，推送分支
git push origin agent/zcode/fix-task-review-notify-403

# 用统一脚本创建 PR
bash scripts/pr-create.sh

# PR 描述包含：
# - 根因证据链（§23 SOP 输出）
# - 修复前后日志对比
# - 单测证据
# - spec/plan/tasks 三件套链接
```

---

## 验证完成的标志

完成以下全部检查后方可报告"任务完成"：

- [ ] 后端全量 `mvn test` 通过
- [ ] ArchUnit 守卫全绿
- [ ] 前端单测通过（如本期改前端）
- [ ] 测试环境部署后，06131 通知点击不再 403
- [ ] DB 验证 06131 不再收到无权项目的任务审核通知
- [ ] 日志无新增 403 记录
- [ ] lessons-learned.md §44 已追加
- [ ] tech-debt-tracker.md 已登记审视清单
- [ ] PR 已创建并通过 CI 门禁
