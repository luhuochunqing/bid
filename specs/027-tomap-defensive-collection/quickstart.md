# Quickstart: 防御性 Collection 与优雅降级治理

**Date**: 2026-07-03

## 验证步骤

### 1. 单元测试验证

```bash
cd /Users/user/xiyu/worktrees/qoder/backend

# 验证 toMap 修复（含 PR #1640 的 2 个回归测试）
mvn test -Dtest=TenderQueryServiceTest

# 验证 ArchUnit 守卫规则
mvn test -Dtest=ArchitectureTest

# 验证 handler 诊断
mvn test -Dtest=GlobalExceptionHandlerTest

# 全量架构测试
mvn test -Dtest='ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest'
```

### 2. pre-push gate 验证

```bash
cd /Users/user/xiyu/worktrees/qoder

# 手动运行 toMap 检查脚本
node scripts/check-tomap-no-merge-function.mjs

# 应输出：✓ All Collectors.toMap calls have merge function (or are in exemption list)
```

### 3. 边界数据验证（需测试环境）

在测试系统构造以下边界数据，验证模块不崩溃：

```sql
-- 构造 tenderId 关联多 Project（二次招标场景）
-- 确认标讯列表页正常加载
```

### 4. 异常诊断验证

触发一个 5xx 异常，验证：
- 后端日志有 error 级堆栈 + Payload
- Sentry 收到事件（如配置 DSN）
- 前端收到通用错误信息

## 完成标准

- [ ] 31 处 toMap 修复完成（豁免清单清空或仅剩主键 key）
- [ ] 装饰性 enrichment 方法加 try-catch 降级
- [ ] 5xx handler 对齐诊断标准
- [ ] ArchUnit 守卫规则生效
- [ ] pre-push gate 脚本生效
- [ ] 所有测试通过
