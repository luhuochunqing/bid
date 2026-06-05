# Tasks: 标讯创建/详情表单字段修正

**Input**: specs/004-bidding-form-fix/

## Phase 1: 常量修正

- [ ] T001 [US1][US2][US4] 修改 src/views/Bidding/list/constants.js：更新 CUSTOMER_TYPE_OPTIONS、createManualTenderForm()、MANUAL_FORM_RULES

## Phase 2: 创建表单修正

- [ ] T002 [P] [US1] 删除 ManualTenderDialog.vue 中预算金额字段
- [ ] T003 [P] [US2] 修改 ManualTenderDialog.vue 标讯标题 → 项目名称
- [ ] T004 [P] [US3] 给 ManualTenderDialog.vue 日期选择器添加 format 属性
- [ ] T005 [P] [US5] 确认 ManualTenderDialog.vue 无来源平台字段

## Phase 3: 详情页修正

- [ ] T006 [US2] 修改 DetailPage.vue 标题标签 → 项目名称
- [ ] T007 [US1] 删除 DetailPage.vue 预算金额行
- [ ] T008 [US3] DetailPage.vue 时间字段使用 formatTenderDateTime

## Phase 4: Payload 与辅助函数

- [ ] T009 [US1] helpers.js buildManualTenderPayload 移除 budget

## Phase 5: 测试与验证

- [ ] T010 更新 constants.spec.js 测试用例
- [ ] T011 运行 npm run build 确认编译通过
