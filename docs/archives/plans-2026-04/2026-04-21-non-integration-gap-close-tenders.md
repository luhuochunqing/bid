# Tenders 子任务单

- 前端：
  - `List.vue` 批量领取/指派/状态更新走真实 API
  - 拆分 batch 相关 composable/API
- 后端：
  - 新增 `PATCH /api/batch/tenders/status`
  - 新增 `POST /api/batch/tenders/assign`
  - 新增 assignment/query 能力
- 测试：
  - 批量接口定向测试
  - 前端 batch action 定向测试
