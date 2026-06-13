## 1. Back-end Validation Implementation
- [x] 1.1 新增纯核心组件 `PerformanceValidator.java` 实现所有规则的业务验证
- [x] 1.2 在 `CreatePerformanceAppService.java` 中引入 `PerformanceValidator.validate()`
- [x] 1.3 在 `UpdatePerformanceAppService.java` 中引入 `PerformanceValidator.validate()`
- [x] 1.4 编写后端单元测试覆盖校验逻辑

## 2. Front-end Form Validation & Alert Dialog
- [x] 2.1 修改 `PerformanceFormDialog.vue`，补齐必填项以及联系方式正则格式校验
- [x] 2.2 在 `PerformanceFormDialog.vue` 中添加保存确认拦截弹窗逻辑（当客户类型或签约日期改变时提示警示文字）
- [x] 2.3 补充 `performance-management-flow.spec.js` 的 E2E 自动化测试用例，覆盖新引入的警示弹窗逻辑
