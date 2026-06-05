# 遗留 Mock 直连清理倒计时清单

参考总规则：`docs/FRONTEND_REAL_DATA_GOVERNANCE.md`  

**警告**：本仓库已全面拉起最后 2 个月的产品上线交付计划。因此：原本作为长线妥协的 `src/api/mock.js` 以及所有 Mock Adapter 将被**强制废离**。
前端再无“支持 Demo 演示”和“保留 Mock 回退”这两种合法状态。

## 当前残留的高风险污染清单

以下区域如果尚未转入真实的 API 网络请求链路，将被视为当前迭代一号优先级的技术清债阻塞点：

| 所在范围 | 历史遗留罪状 | 清盘行动方案 |
| --- | --- | --- |
| `src/views/Analytics/Dashboard.vue` | 脱离后端的闭门自造数据 | 无论后端指标出齐与否，全部接入真实 `dashboardApi`（缺失图表暂作空白）。 |
| `src/views/Project/Detail.vue` | 任务模块或文档模块兜底假数据 | 全部强制接入 `projectsApi` 走纯后端编排，未支持字段直接抛出真实数据错误。 |
| `src/views/Resource/Expense.vue` | 假账本模拟操作假象 | 拆除虚壳，强硬对接 `feesApi`。 |
| `src/views/System/Settings.vue` | 硬编码系统设置说明 | 全量依赖真端契约控制，无接口坚决直接占位不渲染。 |
| **All Stores** | Pinia 中的降级方案 | 从 `user.js`到`bidding.js`，找出并彻底连根拔起任何因为网络异常而自动填充 `mockList` 的防爆假象处理。 |

## 清理验收死线要求

- 以上清单被标记的内容如若在接下来的合并请求中发现依旧在访问 `mock.js` 或试图挂载兜底逻辑，合并将被直接挂起。
- 我们宁愿功能界面报错甚至空置，也绝不允许功能界面上出现其实没有落库的“假繁荣数据”。
- 尽早推动全站通过无脏文件的 `check-front-data-boundaries` 门禁。
