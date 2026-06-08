# 测试清单 — 标书制作阶段前端 Bug 修复

## ⚡ 需修复代码

### Bug 1: submitBidForReview 后 reviewerName 缺失
- [ ] 恢复 reviewerName 赋值: `const selected = reviewerOptions.value.find(u => u.id === Number(bidReviewerId.value)); reviewerName.value = selected?.name || ''`

## 🔬 前端测试（Vitest）

### Bug 1
- [ ] TEST-001: submitBidForReview 成功后 reviewerName 为选择的审核人姓名
- [ ] TEST-002: submitBidForReview 后 reviewerOptions 无匹配项时 reviewerName 为空字符串
- [ ] TEST-003: load() 时从后端数据恢复 reviewerName

### Bug 2（回归）
- [ ] TEST-004: sales 用户+reviewing 状态→审核按钮不可见
- [ ] TEST-005: auditor 用户+reviewing 状态→审核按钮可见

### Bug 3（回归）
- [ ] TEST-006: auditor 用户+approved 状态→完成投标区域不可见
- [ ] TEST-007: sales 用户+approved 状态→完成投标区域可见

## 🔧 后端 API 回归测试

- [ ] TEST-008: auditor 调 submitBid API → 403
- [ ] TEST-009: sales 调 submitBid API → 正常放行
