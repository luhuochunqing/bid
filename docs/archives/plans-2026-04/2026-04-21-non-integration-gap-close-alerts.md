# Alerts 子任务单

- 前端：
  - 注册 `AlertRules`、`AlertHistory` 路由
  - 工作台日历动作改为真实跳转
  - 修正 `alertHistoryApi.acknowledge`
- 后端：
  - 新增 `PATCH /api/alerts/history/{id}/acknowledge`
  - 新增 `GET /api/workbench/schedule-overview`
  - 拆分 lifecycle/query/app service/assembler
- 测试：
  - alert history API normalize
  - lifecycle/acknowledge controller
  - workbench action 跳转
