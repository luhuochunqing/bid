# Change: align-performance-validation

## Why
对齐标讯中心“4.5 业绩管理”的剩余 PRD 细微校验细节，确保前值/后值的合法性，阻断无效的档案保存，并在编辑敏感字段时触发相应的警示弹窗，提高台账数据的准确度与流程的严密性。

## What Changes
- **后端规则校验**：在 `CreatePerformanceAppService` 和 `UpdatePerformanceAppService` 的纯核心（Domain Validator）中实现核心业务逻辑校验，包括：
  - 截止日期晚于签约日期校验（"截止日期必须晚于签约日期"）。
  - 总截止日期若填写则需大于等于截止日期校验（"总截止日期需晚于截止日期"）。
  - 联系方式格式（手机、固话、邮箱三合一正则）。
  - 客户类型为央企时强制名录截图（`SOE_DIRECTORY`）和层级关系证明（`RELATIONSHIP_PROOF`）必须上传。
  - 中标通知书开关为是时，中标通知书附件（`BID_NOTICE`）必须上传。
- **前端警示弹窗拦截**：
  - 在 `PerformanceFormDialog.vue` 中修改签约日期（`signingDate`）或客户类型（`customerType`）时，保存前拦截并弹出警示确认（如“修改客户类型将影响到期提醒规则，请确认必要性”、“修改签约日期将影响业绩归档时间”）。
  - 完善前端必填项校验及联系方式格式校验。

## Impact
- 影响后端应用服务：`CreatePerformanceAppService.java`, `UpdatePerformanceAppService.java`
- 新增后端纯核心验证：`PerformanceValidator.java`
- 影响前端视图组件：`src/views/Knowledge/components/PerformanceFormDialog.vue`
