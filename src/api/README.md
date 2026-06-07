一旦我所属的文件夹有所变化，请更新我。

# API 数据层

**位置**: `src/api/`

**功能**: 封装真实后端 API 调用，提供统一的数据通信链路抽象。

## 文件清单

| 文件 | 类型 | 功能 |
|------|------|------|
| `client.js` | HTTP 客户端 | Axios 封装，请求/响应拦截器 |
| `client.spec.js` | 单元测试 | HTTP 客户端响应错误提示边界测试 |
| `config.js` | 配置 | API 基础配置和拦截器防腐 |
| `index.js` | 导出入口 | 统一导出所有 API 模块 |
| `authNormalizer.js` | 纯 DTO 工具 | 认证响应用户信息归一化 |
| `authStoreBridge.js` | 桥接模块 | HTTP 客户端与 Pinia 认证状态同步 |
| `modules/` | 目录 | 按业务模块拆分的 API 调用函数 |
| `trendradar.js` | 趋势雷达 | 趋势雷达相关 API |

## modules/ 目录

| 文件 | 功能 |
|------|------|
| `auth.js` | 认证授权 |
| `settings.js` | 系统设置中的数据权限与组织树 |
| `systemIntegration.js` | 系统集成配置与组织架构运维 API |
| `projectGroups.js` | 项目组正式领域模型配置与删除 |
| `qualification.js` | 资质 CRUD 与借阅合同式接线 |
| `tenders.js` | 标讯管理 |
| `projects.js` | 项目管理 |
| `tasks.js` | 任务管理 |
| `fees.js` | 费用管理 |
| `ai.js` | AI 智能分析 |
| `bidAgent.js` | 标书写作 Agent 的运行、状态、写入和审查调用 |
| `bidMatchScoring.js` | 自定义投标匹配评分模型和标讯评分结果 |
| ... | 其他业务模块 |

## 治理底线

- 业务页面、组件、store **绝对不得使用任何本地 Mock 演示数据兜底逻辑**。
- 系统已全面进入交付期开发态，所有数据往来只能严格通过按模块调取真实微服务。
- 目录和文件维护规则见 `docs/DOCUMENTATION_GOVERNANCE.md`
- 默认由全局响应拦截器展示请求失败信息；需要页面展示更准确业务错误时，请求 config 可传 `silentError: true` 或 `skipGlobalErrorMessage: true`，避免同一错误重复弹出。

## 更新记录

- 2026-03-18: 认证会话快照开始承载 `allowedProjectIds`，用于项目级数据权限恢复
- 2026-03-19: 新增 `settings.js`，用于读取/保存真实数据权限配置，并让会话快照同时承载 `allowedDepts`
- 2026-03-19: 新增 `projectGroups.js`，把项目组配置从数据权限配置中拆出，改走正式领域 API
- 2026-04-19: 新增 `qualification.js` 并在 `index.js` 暴露独立 `qualificationsApi`，供资质页 store 与知识模块共享
- 2026-04-21: `tenders.js` 改为透传服务端检索参数，并仅做标讯返回字段规范化
- 2026-04-22: API 聚合入口移除重复动态 import，认证刷新通过 `authNormalizer.js` 与 `authStoreBridge.js` 消除 Vite 动静态导入警告
- 2026-04-22: 新增 `bidAgent.js` 并在 `index.js` 暴露 `bidAgentApi`，接入项目标书写作 Agent 的真实 API 单一路径
- 2026-04-24: 新增 `bidMatchScoring.js` 并在 `index.js` 暴露 `bidMatchScoringApi`，接入自定义投标匹配评分真实 API。
- 2026-04-28: `client.js` 支持 `silentError` / `skipGlobalErrorMessage`，供项目任务拆解等页面自管业务错误展示。
- 2026-04-30: 退役死代码 —— 删除零调用者的 `mock-adapters/` 目录和 `examples.js` 文档文件，随带清理 `.env.mock` 模板（`config.js` 早已硬编码 API 模式，不读 `VITE_API_MODE`）。
- 2026-05-15: `systemIntegration.js` 增加组织架构 operations/status、窗口同步、单用户/单部门重同步与死信事件重放真实 API 封装。
- 2026-05-16: 组织架构 operations/status 保留 `failedCount` 兼容字段，语义限定为遗留 `FAILED` 事件计数。
