# 测试清单 — submitBid 权限校验修复

## 后端单元测试

- [ ] TEST-001: sales 用户提交投标 → 成功
- [ ] TEST-002: bid_admin 用户提交投标 → 成功
- [ ] TEST-003: bid_lead 用户提交投标 → 成功
- [ ] TEST-004: auditor 用户提交投标 → 403
- [ ] TEST-005: task_executor 用户提交投标 → 403
- [ ] TEST-006: roleProfile 为 null 的用户提交投标 → 403

## 前端验证

- [ ] TEST-007: auditor + reviewState='approved' → 完成投标区域不可见
- [ ] TEST-008: sales + reviewState='approved' → 完成投标区域可见
