# 西域数智化投标管理平台 — 接口参考手册

> 自动生成于 2026-07-12，基于 `backend/src/main/java/**/*Controller.java` 全量扫描。
> 格式对齐飞书文档 v2.0：模块 | 方法 | 路径 | 功能说明 | 权限。

---

## 一、认证与用户

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 认证 | POST | `/api/auth/register` | 用户注册 | `permitAll()` |
| 认证 | POST | `/api/auth/login` | 用户登录（返回 access/refresh cookie） | `permitAll()` |
| 认证 | GET | `/api/auth/me` | 获取当前登录用户信息 | `isAuthenticated()` |
| 认证 | POST | `/api/auth/logout` | 用户登出（清除 cookie） | `permitAll()` |
| 认证 | POST | `/api/auth/refresh` | 刷新 access/refresh token | `permitAll()` |
| 认证 | POST | `/api/auth/forgot-password` | 忘记密码（发送重置链接） | `permitAll()` |
| 认证 | POST | `/api/auth/reset-password` | 重置密码 | `permitAll()` |
| 认证 | GET | `/api/auth/sessions` | 获取当前用户会话列表 | `isAuthenticated()` |
| 认证 | DELETE | `/api/auth/sessions/{id}` | 撤销指定会话 | `isAuthenticated()` |
| 认证 | DELETE | `/api/auth/sessions` | 撤销所有会话 | `isAuthenticated()` |
| 认证 | POST | `/api/auth/verify-email` | 请求邮箱验证 | `isAuthenticated()` |
| 认证 | GET | `/api/auth/verify-email/{token}` | 验证邮箱 | `permitAll()` |
| SSO | POST | `/api/auth/home-sso` | Home SSO登录（token换JWT+cookie） | `permitAll()` |
| 企微OAuth | GET | `/api/auth/wecom/authorize-params` | 获取企业微信授权参数 | `permitAll()` |
| 企微OAuth | GET | `/api/auth/wecom/callback` | 企业微信OAuth2回调 | `permitAll()` |

## 二、用户与角色管理

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 用户管理 | GET | `/api/admin/users` | 获取全部用户列表 | `hasAuthority('SYSTEM_ADMIN')` |
| 用户管理 | GET | `/api/admin/users/page` | 分页查询用户 | `hasAuthority('SYSTEM_ADMIN')` |
| 用户管理 | POST | `/api/admin/users` | 创建用户 | `hasAuthority('SYSTEM_ADMIN')` |
| 用户管理 | PUT | `/api/admin/users/{id}` | 更新用户信息 | `hasAuthority('SYSTEM_ADMIN')` |
| 用户管理 | PATCH | `/api/admin/users/{id}/status` | 更新用户启用/禁用状态 | `hasAuthority('SYSTEM_ADMIN')` |
| 用户管理 | PUT | `/api/admin/users/{id}/organization` | 更新用户组织归属 | `hasAuthority('SYSTEM_ADMIN')` |
| 用户管理 | DELETE | `/api/admin/users/{id}` | 删除用户 | `hasAuthority('SYSTEM_ADMIN')` |
| 角色管理 | GET | `/api/admin/roles` | 获取全部角色列表 | `hasAuthority('SYSTEM_ADMIN')` |
| 角色管理 | POST | `/api/admin/roles` | 创建角色 | `hasAuthority('SYSTEM_ADMIN')` |
| 角色管理 | PUT | `/api/admin/roles/{id}` | 更新角色 | `hasAuthority('SYSTEM_ADMIN')` |
| 角色管理 | PATCH | `/api/admin/roles/{id}/status` | 更新角色启用/禁用状态 | `hasAuthority('SYSTEM_ADMIN')` |
| 角色管理 | POST | `/api/admin/roles/{id}/reset-default` | 重置角色为默认配置 | `hasAuthority('SYSTEM_ADMIN')` |
| 角色管理 | DELETE | `/api/admin/roles/{id}` | 删除角色 | `hasAuthority('SYSTEM_ADMIN')` |
| 用户查询 | GET | `/api/users/assignable-candidates` | 查询可分配候选人 | `isAuthenticated()` |
| 用户查询 | GET | `/api/users/search` | 搜索用户（@提及用） | `isAuthenticated()` |
| 用户查询 | GET | `/api/users/batch` | 批量按ID查询用户 | `isAuthenticated()` |
| 企微绑定 | GET | `/api/admin/users/{userId}/wecom-binding` | 查询用户企微绑定 | `hasAuthority('bid-SystemAdmin')` |
| 企微绑定 | PUT | `/api/admin/users/{userId}/wecom-binding` | 绑定用户企微 | `hasAuthority('bid-SystemAdmin')` |
| 企微绑定 | DELETE | `/api/admin/users/{userId}/wecom-binding` | 解绑用户企微 | `hasAuthority('bid-SystemAdmin')` |

## 三、系统管理与配置

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 管理设置 | GET | `/api/admin/settings/data-scope` | 获取数据范围配置 | `isAuthenticated()` |
| 管理设置 | PUT | `/api/admin/settings/data-scope` | 保存数据范围配置 | `isAuthenticated()` |
| 管理设置 | PUT | `/api/admin/settings/departments` | 保存部门树 | `isAuthenticated()` |
| 项目分组 | GET | `/api/admin/project-groups` | 获取项目分组列表 | `isAuthenticated()` |
| 项目分组 | POST | `/api/admin/project-groups` | 创建项目分组 | `isAuthenticated()` |
| 项目分组 | PUT | `/api/admin/project-groups` | 批量保存项目分组 | `isAuthenticated()` |
| 项目分组 | PATCH | `/api/admin/project-groups/{id}` | 更新单个项目分组 | `isAuthenticated()` |
| 项目分组 | DELETE | `/api/admin/project-groups/{id}` | 删除项目分组 | `isAuthenticated()` |
| 权限管理 | GET | `/api/admin/permissions/endpoints` | 获取端点权限矩阵列表 | `isAuthenticated()` |
| API Key | GET | `/api/admin/api-keys` | 获取所有API Key列表 | `hasAuthority('SYSTEM_ADMIN')` |
| API Key | POST | `/api/admin/api-keys` | 创建API Key（返回一次性secret） | `hasAuthority('SYSTEM_ADMIN')` |
| API Key | POST | `/api/admin/api-keys/{id}/disable` | 禁用API Key | `hasAuthority('SYSTEM_ADMIN')` |
| API Key | POST | `/api/admin/api-keys/{id}/enable` | 启用API Key | `hasAuthority('SYSTEM_ADMIN')` |
| API Key | DELETE | `/api/admin/api-keys/{id}` | 删除API Key | `hasAuthority('SYSTEM_ADMIN')` |
| 系统设置 | GET | `/api/settings` | 获取系统设置 | `hasAuthority('bid-SystemAdmin')` |
| 系统设置 | PUT | `/api/settings` | 更新系统设置 | `hasAuthority('bid-SystemAdmin')` |
| 系统设置 | POST | `/api/settings/ai-models/test` | 测试AI模型连接 | `hasAuthority('bid-SystemAdmin')` |
| 系统设置 | GET | `/api/settings/runtime-permissions` | 获取当前用户运行时权限信息 | `isAuthenticated()` |
| 系统设置 | GET | `/api/settings/system-info` | 获取系统信息 | `hasAuthority('bid-SystemAdmin')` |
| 枚举 | GET | `/api/enums/metadata` | 获取所有DisplayableEnum的值-标签映射 | `isAuthenticated()` |
| 外部系统菜单 | GET | `/api/systems/external/menus` | 获取本系统菜单树（供统一组织架构系统拉取） | `permitAll()` |
| 运行时模式 | GET | `/api/system/runtime-mode` | 获取运行时模式 | `permitAll()` |
| 前端日志 | POST | `/api/logs/report` | 前端全局异常与日志收集上报 | `permitAll()` |

## 四、标讯管理

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 标讯管理 | GET | `/api/tenders` | 标讯列表查询（分页） | 类级：`hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM')` |
| 标讯管理 | GET | `/api/tenders/{id}` | 标讯详情查询 | 类级继承 |
| 标讯管理 | POST | `/api/tenders` | 创建标讯 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM','SALES')` |
| 标讯管理 | PUT | `/api/tenders/{id}` | 修改标讯 | `hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','SALES')` |
| 标讯管理 | PATCH | `/api/tenders/{id}/crm-opportunity` | 标讯关联CRM商机 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM','SALES')` |
| 标讯管理 | DELETE | `/api/tenders/{id}` | 删除标讯 | `hasAnyRole('ADMIN','MANAGER','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','SALES')` |
| 标讯管理 | GET | `/api/tenders/{id}/audit-logs` | 标讯审计日志 | 类级继承 |
| 标讯管理 | POST | `/api/tenders/{id}/participate` | 投标决策 | `hasAnyAuthority('bidding.manage','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN')` |
| 标讯管理 | POST | `/api/tenders/{id}/abandon` | 弃标决策 | `hasAnyAuthority('bidding.manage','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN')` |
| 标讯管理 | GET | `/api/tenders/import-template` | 下载标讯批量导入模板 | `hasAnyAuthority('bidding','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN','ROLE_BID_TEAM')` |
| 标讯管理 | POST | `/api/tenders/import` | 批量导入标讯（异步） | `hasAnyAuthority('bidding','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN','ROLE_BID_TEAM')` |
| 标讯管理 | GET | `/api/tenders/import/{taskId}/progress` | 查询标讯批量导入任务进度 | `hasAnyAuthority('bidding','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN','ROLE_BID_TEAM')` |
| 标讯管理 | GET | `/api/tenders/status/{status}` | 按状态筛选标讯 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯管理 | GET | `/api/tenders/source/{source}` | 按来源筛选标讯 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯管理 | GET | `/api/tenders/statistics` | 标讯统计 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯AI分析 | GET | `/api/tenders/{id}/ai-analysis` | 查询标讯AI分析结果 | `isAuthenticated()` |
| 标讯AI分析 | POST | `/api/tenders/{id}/ai-analysis` | 触发标讯AI分析 | `isAuthenticated()` |
| 标讯AI分析 | POST | `/api/tenders/{id}/analyze` | 触发标讯AI分析（旧接口） | `isAuthenticated()` |
| 标讯评估 | GET | `/api/tenders/{tenderId}/evaluation` | 获取标讯评估详情 | `isAuthenticated()` |
| 标讯评估 | GET | `/api/tenders/{tenderId}/evaluation/ai-risk` | AI风险等级评估 | `isAuthenticated()` |
| 标讯评估 | PUT | `/api/tenders/{tenderId}/evaluation` | 保存评估草稿 | `isAuthenticated()` |
| 标讯评估 | POST | `/api/tenders/{tenderId}/evaluation/submit` | 提交评估表 | `isAuthenticated()` |
| 标讯审核 | POST | `/api/tenders/{tenderId}/review` | 决策标讯（投标/弃标） | `hasAnyAuthority('bidding.manage','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN')` |
| 标讯审核 | POST | `/api/tenders/{tenderId}/bid` | 投标立项（创建项目） | `hasAnyAuthority('bidding.manage','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN')` |
| 标讯审核 | POST | `/api/tenders/{evaluationId}/evaluation/review` | 确认审核评估表 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯评估附件 | POST | `/api/tenders/{tenderId}/evaluation/documents` | 上传评估表附件 | `isAuthenticated()` |
| 标讯评估附件 | GET | `/api/tenders/{tenderId}/evaluation/documents` | 获取评估表附件列表 | `isAuthenticated()` |
| 标讯评估附件 | DELETE | `/api/tenders/{tenderId}/evaluation/documents/{documentId}` | 删除评估表附件 | `hasAnyAuthority('system.admin','ROLE_ADMIN','evaluation.manage','ROLE_BID_PROJECTLEADER','ROLE_BID_TEAMLEADER')` |
| 标讯转派 | POST | `/api/tenders/{id}/transfer` | 转派标讯 | `hasAnyAuthority('bidding.manage','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN')` |
| 标讯收藏 | POST | `/api/tender-favorites/{tenderId}` | 切换收藏状态 | `isAuthenticated()` |
| 标讯收藏 | GET | `/api/tender-favorites/ids` | 获取收藏标讯ID列表 | `isAuthenticated()` |
| 标讯收藏 | GET | `/api/tender-favorites` | 分页获取收藏标讯列表 | `isAuthenticated()` |
| 标讯收藏 | DELETE | `/api/tender-favorites/{tenderId}` | 取消收藏 | `isAuthenticated()` |
| 标讯收藏 | GET | `/api/tender-favorites/check/{tenderId}` | 检查是否已收藏 | `isAuthenticated()` |
| 关键词订阅 | POST | `/api/tender-keyword-subscriptions` | 创建关键词订阅 | `isAuthenticated()` |
| 关键词订阅 | GET | `/api/tender-keyword-subscriptions` | 查询我的订阅列表 | `isAuthenticated()` |
| 关键词订阅 | GET | `/api/tender-keyword-subscriptions/{id}` | 查询单个订阅详情 | `isAuthenticated()` |
| 关键词订阅 | PUT | `/api/tender-keyword-subscriptions/{id}` | 更新订阅 | `isAuthenticated()` |
| 关键词订阅 | DELETE | `/api/tender-keyword-subscriptions/{id}` | 删除订阅 | `isAuthenticated()` |
| 关键词订阅 | PATCH | `/api/tender-keyword-subscriptions/{id}/toggle` | 切换订阅状态 | `isAuthenticated()` |
| 关键词订阅 | GET | `/api/tender-keyword-subscriptions/match-results` | 查询匹配结果 | `isAuthenticated()` |
| 关键词订阅 | GET | `/api/tender-keyword-subscriptions/{id}/match-results` | 查询指定订阅匹配结果 | `isAuthenticated()` |
| 标讯提醒 | GET | `/api/tenders/{tenderId}/reminders` | 获取标讯提醒设置 | `isAuthenticated()` |
| 标讯提醒 | GET | `/api/tenders/{tenderId}/reminders/{reminderId}` | 获取单个提醒详情 | `isAuthenticated()` |
| 标讯提醒 | POST | `/api/tenders/{tenderId}/reminders` | 创建提醒设置 | `isAuthenticated()` |
| 标讯提醒 | PUT | `/api/tenders/{tenderId}/reminders/{reminderId}` | 更新提醒设置 | `isAuthenticated()` |
| 标讯提醒 | DELETE | `/api/tenders/{tenderId}/reminders/{reminderId}` | 删除提醒设置 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯提醒 | POST | `/api/tenders/{tenderId}/reminders/{reminderId}/toggle` | 切换提醒启用状态 | `isAuthenticated()` |
| 标讯源配置 | GET | `/api/tender-sources/config` | 获取标讯源配置 | `isAuthenticated()` |
| 标讯源配置 | PUT | `/api/tender-sources/config` | 保存标讯源配置 | `isAuthenticated()` |
| 标讯源配置 | POST | `/api/tender-sources/test-connection` | 测试标讯源连接 | `isAuthenticated()` |
| 标讯上传 | POST | `/api/tenders/upload-init` | 初始化上传会话 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯上传 | POST | `/api/tenders/upload-complete` | 完成上传并排队处理 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯上传 | GET | `/api/tenders/tasks/{taskId}` | 查询上传任务状态 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯分配查询 | GET | `/api/tenders/{id}/assignment` | 获取标讯分配信息 | `isAuthenticated()` |
| 标讯分配查询 | GET | `/api/tenders/assignment-candidates` | 获取标讯分配候选人（已废弃） | `isAuthenticated()` |

## 五、投标匹配

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 匹配评分 | POST | `/api/tenders/{tenderId}/match-score/evaluate` | 执行标讯匹配评分 | `isAuthenticated()` |
| 匹配评分 | GET | `/api/tenders/{tenderId}/match-score/latest` | 获取最新匹配评分 | `isAuthenticated()` |
| 匹配评分 | GET | `/api/tenders/{tenderId}/match-score/history` | 获取匹配评分历史 | `isAuthenticated()` |
| 匹配模型 | GET | `/api/bid-match/models` | 获取匹配评分模型列表 | `isAuthenticated()` |
| 匹配模型 | POST | `/api/bid-match/models` | 创建匹配评分模型 | `hasAnyRole('ADMIN','MANAGER')` |
| 匹配模型 | PUT | `/api/bid-match/models` | 更新匹配评分模型 | `hasAnyRole('ADMIN','MANAGER')` |
| 匹配模型 | POST | `/api/bid-match/models/{id}/activate` | 激活匹配评分模型 | `hasAnyRole('ADMIN','MANAGER')` |
| 匹配评估 | GET | `/api/bid-match/evaluations/{id}` | 获取匹配评估详情 | `isAuthenticated()` |

## 六、批量操作

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 批量标讯 | POST | `/api/batch/tenders/claim` | 批量认领标讯 | `isAuthenticated()` |
| 批量标讯 | PATCH | `/api/batch/tenders/status` | 批量更新标讯状态 | SpEL自定义鉴权 |
| 批量标讯 | POST | `/api/batch/tenders/assign` | 批量分配标讯 | `isAuthenticated()` |
| 批量操作 | POST | `/api/batch/tasks/assign` | 批量分配任务 | `hasAnyRole('ADMIN','MANAGER')` |
| 批量操作 | DELETE | `/api/batch/projects` | 批量删除项目 | `hasAnyRole('ADMIN','MANAGER')` |
| 批量操作 | DELETE | `/api/batch/{type}` | 批量删除指定类型项目 | SpEL鉴权 |
| 批量操作 | GET | `/api/batch/status/{operationId}` | 查询批量操作状态 | `isAuthenticated()` |
| 批量操作 | GET | `/api/batch/history` | 获取批量操作历史 | `hasAnyRole('ADMIN','MANAGER')` |
| 批量操作 | PATCH | `/api/batch/projects` | 批量更新项目 | `hasAnyRole('ADMIN','MANAGER')` |
| 批量操作 | POST | `/api/batch/fees/approve` | 批量审批费用 | `hasAnyRole('ADMIN','MANAGER')` |

## 七、项目管理

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 项目列表 | GET | `/api/projects` | 获取所有项目（多条件筛选+分页） | `isAuthenticated()` |
| 项目列表 | GET | `/api/projects/{id}` | 获取项目详情 | `isAuthenticated()` |
| 项目列表 | POST | `/api/projects` | 创建新项目 | `hasAnyRole('ADMIN','MANAGER')` |
| 项目列表 | POST | `/api/projects/import` | 历史档案批量导入 | `hasAnyRole('ADMIN','BIDADMIN')` |
| 项目列表 | PUT | `/api/projects/{id}` | 更新项目信息 | `hasAnyRole('ADMIN','MANAGER')` |
| 项目列表 | DELETE | `/api/projects/{id}` | 删除项目 | `hasAuthority('SYSTEM_ADMIN')` |
| 项目列表 | PUT | `/api/projects/{id}/status` | 更新项目状态 | `hasAnyRole('ADMIN','MANAGER')` |
| 项目列表 | PUT | `/api/projects/{id}/team` | 更新项目团队 | `hasAnyRole('ADMIN','MANAGER')` |
| 项目列表 | GET | `/api/projects/search` | 按名称搜索项目 | `hasAnyRole('ADMIN','MANAGER')` |
| 项目列表 | GET | `/api/projects/statistics` | 获取项目统计信息 | `hasAnyRole('ADMIN','MANAGER')` |
| 项目列表 | GET | `/api/projects/export` | 导出项目列表Excel | `isAuthenticated()` |
| 项目列表 | GET | `/api/projects/active` | 获取活跃项目列表 | `hasAnyRole('ADMIN','MANAGER')` |
| 项目转移 | POST | `/api/projects/{projectId}/transfer` | 转移项目给新负责人 | `hasAnyAuthority('bidding.manage','ROLE_ADMIN','ROLE_BIDADMIN')` |
| 项目成员 | GET | `/api/projects/{projectId}/members` | 获取项目成员列表 | `isAuthenticated()` |
| 项目成员 | POST | `/api/projects/{projectId}/members` | 添加项目成员 | `hasAnyRole('ADMIN','MANAGER')` |
| 项目成员 | DELETE | `/api/projects/{projectId}/members/{userId}` | 移除项目成员 | `hasAnyRole('ADMIN','MANAGER')` |
| 标讯映射 | GET | `/api/project/tender-init-mapping` | 标讯→立项枚举值映射查询 | `isAuthenticated()` |

## 八、项目生命周期

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 立项 | POST | `/api/projects/{projectId}/initiation` | 提交立项 | `hasAnyRole('ADMIN','BID_PROJECTLEADER','BID_TEAMLEADER')` |
| 立项 | PATCH | `/api/projects/{projectId}/initiation` | 更新立项 | `hasAnyRole('ADMIN','BID_PROJECTLEADER','BID_TEAMLEADER')` |
| 立项 | GET | `/api/projects/{projectId}/initiation` | 获取立项详情 | `isAuthenticated()` |
| 立项 | POST | `/api/projects/{projectId}/initiation/approve` | 审核通过立项 | `hasAnyAuthority('task.review','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN')` |
| 立项 | POST | `/api/projects/{projectId}/initiation/reject` | 审核驳回立项 | `hasAnyAuthority('task.review','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN')` |
| 立项 | POST | `/api/projects/{projectId}/initiation/ai-risk-assessment` | AI风险评估 | `hasAnyRole('ADMIN','BID_PROJECTLEADER')` |
| 阶段 | GET | `/api/projects/{projectId}/stage` | 获取当前阶段+候选阶段 | `isAuthenticated()` |
| 标书制作 | PATCH | `/api/projects/{projectId}/drafting/leads` | 分配投标团队 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN')` |
| 标书制作 | POST | `/api/projects/{projectId}/drafting/advance` | DRAFTING→EVALUATING推进 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` |
| 标书制作 | POST | `/api/projects/{projectId}/drafting/submit-bid` | 提交投标 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM','SALES')` |
| 标书制作 | POST | `/api/projects/{projectId}/drafting/submit-review` | 提交标书审核 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM','SALES')` |
| 标书制作 | POST | `/api/projects/{projectId}/drafting/approve` | 审核通过 | `isAuthenticated()` |
| 标书制作 | POST | `/api/projects/{projectId}/drafting/reject` | 驳回 | `isAuthenticated()` |
| 标书制作 | GET | `/api/projects/{projectId}/drafting` | 获取标书制作视图 | `isAuthenticated()` |
| 评标 | PATCH | `/api/projects/{projectId}/evaluation/sub-stage` | 切换评标子状态 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` |
| 评标 | POST | `/api/projects/{projectId}/evaluation/advance` | 推进到结果确认 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` |
| 评标 | POST | `/api/projects/{projectId}/evaluation/evidence` | 附加评标证据 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` |
| 评标 | PATCH | `/api/projects/{projectId}/evaluation/form` | 填写项目评估表单 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` |
| 评标 | GET | `/api/projects/{projectId}/evaluation` | 获取评标详情 | `isAuthenticated()` |
| 评标 | POST | `/api/projects/{projectId}/evaluation/abandon` | 弃标申请 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_TEAM')` |
| 结果登记 | POST | `/api/projects/{projectId}/result` | 登记结果 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM')` |
| 结果登记 | GET | `/api/projects/{projectId}/result` | 获取已登记结果 | `isAuthenticated()` |
| 复盘 | POST | `/api/projects/{projectId}/retrospective` | 提交复盘 | `hasAuthority('retrospective.submit')` |
| 复盘 | GET | `/api/projects/{projectId}/retrospective` | 查询复盘 | `isAuthenticated()` |
| 结项 | GET | `/api/projects/{projectId}/closure/preview` | 结项预览 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM')` |
| 结项 | POST | `/api/projects/{projectId}/closure` | 提交结项申请 | `hasAnyRole('ADMIN','BID_PROJECTLEADER')` |
| 结项 | POST | `/api/projects/{projectId}/closure/approve` | 审核通过结项 | `hasAnyAuthority('closure.review','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN','ROLE_BID_TEAM')` |
| 结项 | POST | `/api/projects/{projectId}/closure/reject` | 审核驳回结项 | `hasAnyAuthority('closure.review','ROLE_ADMIN','ROLE_BID_TEAMLEADER','ROLE_BIDADMIN','ROLE_BID_TEAM')` |
| 结项 | POST | `/api/projects/{projectId}/closure/export-documents` | 一键导出项目文档 | `hasAnyRole('ADMIN','BID_TEAMLEADER','BIDADMIN','BID_PROJECTLEADER','BID_TEAM')` |

## 九、任务与工作流

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 任务管理 | POST | `/api/tasks` | 创建新任务 | `isAuthenticated()` |
| 任务管理 | GET | `/api/tasks` | 获取所有任务 | `isAuthenticated()` |
| 任务管理 | GET | `/api/tasks/{id}` | 根据ID获取任务 | `isAuthenticated()` |
| 任务管理 | PUT | `/api/tasks/{id}` | 更新任务 | `isAuthenticated()` |
| 任务管理 | DELETE | `/api/tasks/{id}` | 删除任务 | `isAuthenticated()` |
| 任务管理 | GET | `/api/tasks/project/{projectId}` | 根据项目ID获取任务列表 | `isAuthenticated()` |
| 任务管理 | GET | `/api/tasks/my` | 获取我的任务 | `isAuthenticated()` |
| 任务管理 | PATCH | `/api/tasks/{id}/status` | 更新任务状态 | `isAuthenticated()` |
| 任务管理 | GET | `/api/tasks/{id}/activity` | 获取任务动态 | `isAuthenticated()` |
| 任务管理 | POST | `/api/tasks/{id}/comments` | 创建任务评论 | `isAuthenticated()` |
| 任务管理 | PATCH | `/api/tasks/{id}/assign` | 分配任务 | `isAuthenticated()` |
| 任务管理 | GET | `/api/tasks/team-workload` | 获取团队任务负载 | `isAuthenticated()` |
| 任务管理 | GET | `/api/tasks/upcoming` | 获取即将到期的任务 | `isAuthenticated()` |
| 任务管理 | GET | `/api/tasks/overdue` | 获取已过期任务 | `hasRole('ADMIN')` |
| 任务看板 | GET | `/api/task-board/items` | 获取任务看板条目 | `isAuthenticated()` |
| 任务交付物 | GET | `/api/projects/{projectId}/tasks/{taskId}/deliverables` | 获取交付物列表 | `isAuthenticated()` |
| 任务交付物 | POST | `/api/projects/{projectId}/tasks/{taskId}/deliverables` | 上传交付物 | `isAuthenticated()` |
| 任务交付物 | GET | `/api/projects/{projectId}/tasks/{taskId}/deliverables/{deliverableId}/download` | 下载交付物 | `isAuthenticated()` |
| 任务交付物 | DELETE | `/api/projects/{projectId}/tasks/{taskId}/deliverables/{deliverableId}` | 删除交付物 | `isAuthenticated()` |
| 任务交付物 | GET | `/api/projects/{projectId}/tasks/{taskId}/deliverables/coverage` | 获取交付物覆盖情况 | `isAuthenticated()` |
| 任务扩展字段 | GET | `/api/task-extended-fields` | 获取启用中的扩展字段schema | `isAuthenticated()` |
| 任务扩展字段管理 | GET | `/api/admin/task-extended-fields` | 列出全部扩展字段 | `isAuthenticated()` |
| 任务扩展字段管理 | POST | `/api/admin/task-extended-fields` | 新建扩展字段 | `isAuthenticated()` |
| 任务扩展字段管理 | PUT | `/api/admin/task-extended-fields/{key}` | 更新扩展字段 | `isAuthenticated()` |
| 任务扩展字段管理 | PATCH | `/api/admin/task-extended-fields/{key}/disable` | 停用扩展字段 | `isAuthenticated()` |
| 任务扩展字段管理 | PATCH | `/api/admin/task-extended-fields/{key}/enable` | 启用扩展字段 | `isAuthenticated()` |
| 任务扩展字段管理 | PATCH | `/api/admin/task-extended-fields/reorder` | 批量重排扩展字段 | `isAuthenticated()` |
| 任务状态字典 | GET | `/api/task-status-dict` | 获取启用中的状态字典 | `isAuthenticated()` |
| 任务状态字典管理 | GET | `/api/admin/task-status-dict` | 列出全部状态字典项 | `isAuthenticated()` |
| 任务状态字典管理 | POST | `/api/admin/task-status-dict` | 新建状态字典项 | `isAuthenticated()` |
| 任务状态字典管理 | PUT | `/api/admin/task-status-dict/{code}` | 更新状态字典项 | `isAuthenticated()` |
| 任务状态字典管理 | PATCH | `/api/admin/task-status-dict/{code}/disable` | 停用状态字典项 | `isAuthenticated()` |
| 任务状态字典管理 | PATCH | `/api/admin/task-status-dict/{code}/enable` | 启用状态字典项 | `isAuthenticated()` |
| 任务状态字典管理 | PATCH | `/api/admin/task-status-dict/reorder` | 批量重排状态字典项 | `isAuthenticated()` |

## 十、工作台

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 工作台日程 | GET | `/api/workbench/schedule-overview` | 获取日程概览 | `isAuthenticated()` |
| 工作台截止 | GET | `/api/workbench/deadline-stats` | 获取截止日期统计 | `isAuthenticated()` |
| 工作台布局 | GET | `/api/dashboard/layout/my` | 获取当前用户工作台布局 | `isAuthenticated()` |

## 十一、审批流程

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 审批 | POST | `/api/approvals/submit` | 提交审批 | `isAuthenticated()` |
| 审批 | POST | `/api/approvals/{id}/approve` | 审批通过 | `isAuthenticated()` |
| 审批 | POST | `/api/approvals/{id}/reject` | 审批驳回 | `isAuthenticated()` |
| 审批 | DELETE | `/api/approvals/{id}` | 取消审批 | `isAuthenticated()` |
| 审批 | GET | `/api/approvals/pending` | 获取待审批列表 | `isAuthenticated()` |
| 审批 | GET | `/api/approvals/statistics` | 获取审批统计数据 | `hasAnyRole('ADMIN','MANAGER')` |
| 审批 | GET | `/api/approvals/{id}` | 获取审批详情 | `isAuthenticated()` |
| 审批 | PUT | `/api/approvals/{id}/read` | 标记审批为已读 | `isAuthenticated()` |
| 审批 | GET | `/api/approvals/my` | 获取我的审批列表 | `isAuthenticated()` |
| 审批 | POST | `/api/approvals/{id}/resubmit` | 重新提交审批 | `isAuthenticated()` |
| 批量审批 | POST | `/api/approvals/batch/approve` | 批量审批通过 | `isAuthenticated()` |
| 批量审批 | POST | `/api/approvals/batch/reject` | 批量驳回 | `isAuthenticated()` |

## 十二、项目工作流

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 项目任务 | GET | `/api/projects/{projectId}/tasks` | 获取项目任务列表 | `isAuthenticated()` |
| 项目任务 | POST | `/api/projects/{projectId}/tasks` | 创建项目任务 | `isAuthenticated()` |
| 项目任务 | POST | `/api/projects/{projectId}/tasks/decompose` | 从标书分解生成任务 | `isAuthenticated()` |
| 项目任务 | PATCH | `/api/projects/{projectId}/tasks/{taskId}/status` | 更新项目任务状态 | `isAuthenticated()` |
| 项目提醒 | GET | `/api/projects/{projectId}/reminders` | 获取项目提醒列表 | `isAuthenticated()` |
| 项目提醒 | POST | `/api/projects/{projectId}/reminders` | 创建项目提醒 | `isAuthenticated()` |
| 分享链接 | GET | `/api/projects/{projectId}/share-links` | 获取项目分享链接列表 | `isAuthenticated()` |
| 分享链接 | POST | `/api/projects/{projectId}/share-links` | 创建项目分享链接 | `isAuthenticated()` |
| 评分草稿 | POST | `/api/projects/{projectId}/score-drafts/parse` | 解析评分草稿文件 | `isAuthenticated()` |
| 评分草稿 | GET | `/api/projects/{projectId}/score-drafts` | 获取评分草稿列表 | `isAuthenticated()` |
| 评分草稿 | PATCH | `/api/projects/{projectId}/score-drafts/{draftId}` | 更新评分草稿 | `isAuthenticated()` |
| 评分草稿 | POST | `/api/projects/{projectId}/score-drafts/generate-tasks` | 从评分草稿生成任务 | `isAuthenticated()` |
| 评分草稿 | DELETE | `/api/projects/{projectId}/score-drafts` | 清除非生成状态评分草稿 | `isAuthenticated()` |
| 标书文档 | POST | `/api/projects/{projectId}/submit-to-bid-document` | 提交至标书文档 | `hasAnyRole('ADMIN','MANAGER')` |
| 标书文档 | GET | `/api/projects/{projectId}/bid-process-status` | 获取标书流程状态 | `isAuthenticated()` |
| 项目文档 | GET | `/api/projects/{projectId}/documents` | 获取项目文档列表 | `isAuthenticated()` |
| 项目文档 | GET | `/api/projects/{projectId}/documents/{documentId}/download` | 下载项目文档 | `isAuthenticated()` |
| 项目文档 | POST | `/api/projects/{projectId}/documents` | 创建项目文档 | `isAuthenticated()` |
| 项目文档 | POST | `/api/projects/{projectId}/documents` | 上传项目文档文件（multipart） | `isAuthenticated()` |
| 项目文档 | DELETE | `/api/projects/{projectId}/documents/{documentId}` | 删除项目文档 | `isAuthenticated()` |

## 十三、流程表单

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 流程表单 | GET | `/api/workflow-forms/templates/{templateCode}/active` | 获取激活的流程表单模板 | `isAuthenticated()` |
| 流程表单 | POST | `/api/workflow-forms/instances` | 提交流程表单实例 | `isAuthenticated()` |
| 流程表单 | POST | `/api/workflow-forms/attachments` | 上传流程表单附件 | `isAuthenticated()` |
| 表单管理 | GET | `/api/admin/workflow-forms/business-types` | 获取业务类型枚举列表 | `isAuthenticated()` |
| 表单管理 | GET | `/api/admin/workflow-forms/templates` | 获取模板列表 | `isAuthenticated()` |
| 表单管理 | GET | `/api/admin/workflow-forms/templates/{templateCode}/versions` | 获取模板版本列表 | `isAuthenticated()` |
| 表单管理 | POST | `/api/admin/workflow-forms/templates` | 创建模板草稿 | `isAuthenticated()` |
| 表单管理 | PUT | `/api/admin/workflow-forms/templates/{templateCode}/draft` | 更新模板草稿 | `isAuthenticated()` |
| 表单管理 | POST | `/api/admin/workflow-forms/templates/{templateCode}/publish` | 发布模板 | `isAuthenticated()` |
| 表单管理 | POST | `/api/admin/workflow-forms/templates/{templateCode}/versions/{version}/rollback` | 回滚模板 | `isAuthenticated()` |
| 表单管理 | PUT | `/api/admin/workflow-forms/templates/{templateCode}/oa-binding` | 保存OA绑定配置 | `isAuthenticated()` |
| 表单管理 | POST | `/api/admin/workflow-forms/templates/{templateCode}/oa/test-submit` | OA试提交测试 | `isAuthenticated()` |
| OA回调 | POST | `/api/integrations/oa/weaver/callback` | 接收OA（泛微）审批回调 | `permitAll()` |

## 十四、质量管理

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 质量检查 | POST | `/api/projects/{projectId}/quality-checks` | 执行质量检查 | `isAuthenticated()` |
| 质量检查 | GET | `/api/projects/{projectId}/quality-checks/latest` | 获取最新质量检查结果 | `isAuthenticated()` |
| 质量检查 | POST | `/api/projects/{projectId}/quality-checks/{checkId}/issues/{issueId}/adopt` | 采纳质量建议 | `isAuthenticated()` |
| 质量检查 | POST | `/api/projects/{projectId}/quality-checks/{checkId}/issues/{issueId}/ignore` | 忽略质量建议 | `isAuthenticated()` |

## 十五、招标文件拆解

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 文件拆解 | GET | `/api/projects/{projectId}/tender-breakdown/readiness` | 获取拆解就绪状态 | `isAuthenticated()` |
| 文件拆解 | GET | `/api/projects/{projectId}/tender-breakdown/latest` | 获取最新解析结果 | `isAuthenticated()` |
| 文件拆解 | POST | `/api/projects/{projectId}/tender-breakdown/reuse-uploaded` | 复用已上传招标文件拆解 | `isAuthenticated()` |
| 文件拆解 | POST | `/api/projects/{projectId}/tender-breakdown` | 上传并解析招标文件 | `isAuthenticated()` |

## 十六、订阅

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 订阅 | POST | `/api/subscriptions` | 订阅实体 | `isAuthenticated()` |
| 订阅 | DELETE | `/api/subscriptions` | 取消订阅 | `isAuthenticated()` |
| 订阅 | GET | `/api/subscriptions/me` | 获取我的订阅列表 | `isAuthenticated()` |
| 订阅 | GET | `/api/entities/{entityType}/{entityId}/subscription` | 检查是否已订阅 | `isAuthenticated()` |

## 十七、告警与通知

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 告警规则 | POST | `/api/alerts/rules` | 创建告警规则 | `hasAnyAuthority('settings-alerts','ROLE_ADMIN','ROLE_BIDADMIN','ROLE_BID_TEAMLEADER')` |
| 告警规则 | GET | `/api/alerts/rules/{id}` | 获取告警规则 | 类级继承 |
| 告警规则 | GET | `/api/alerts/rules` | 获取所有告警规则 | 类级继承 |
| 告警规则 | GET | `/api/alerts/rules/enabled` | 获取已启用告警规则 | 类级继承 |
| 告警规则 | GET | `/api/alerts/rules/type/{type}` | 按类型获取告警规则 | 类级继承 |
| 告警规则 | PUT | `/api/alerts/rules/{id}` | 更新告警规则 | 类级继承 |
| 告警规则 | DELETE | `/api/alerts/rules/{id}` | 删除告警规则 | 类级继承 |
| 告警规则 | PATCH | `/api/alerts/rules/{id}/toggle` | 切换告警规则启用状态 | 类级继承 |
| 告警历史 | POST | `/api/alerts/history` | 创建告警历史记录 | `hasAnyAuthority('settings-alerts','ROLE_ADMIN','ROLE_BIDADMIN','ROLE_BID_TEAMLEADER')` |
| 告警历史 | GET | `/api/alerts/history` | 分页查询告警历史 | 类级继承 |
| 告警历史 | GET | `/api/alerts/history/{id}` | 获取告警历史详情 | 类级继承 |
| 告警历史 | GET | `/api/alerts/history/unresolved` | 获取未解决告警 | 类级继承 |
| 告警历史 | PATCH | `/api/alerts/history/{id}/acknowledge` | 确认告警 | 类级继承 |
| 告警历史 | POST | `/api/alerts/history/{id}/resolve` | 解决告警 | 类级继承 |
| 告警历史 | GET | `/api/alerts/history/statistics` | 获取告警统计 | 类级继承 |
| 通知 | GET | `/api/notifications` | 分页获取通知列表 | `isAuthenticated()` |
| 通知 | GET | `/api/notifications/unread-count` | 获取未读通知数 | `isAuthenticated()` |
| 通知 | POST | `/api/notifications/{notificationId}/read` | 标记单条通知已读 | `isAuthenticated()` |
| 通知 | POST | `/api/notifications/read-all` | 标记全部通知已读 | `isAuthenticated()` |
| 通知 | POST | `/api/admin/notifications` | 管理员创建通知 | `hasAuthority('bid-SystemAdmin')` |

## 十八、工作台分析

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 工作台分析 | GET | `/api/analytics/overview` | 获取工作台完整概览 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/summary` | 获取汇总统计 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/trends` | 获取趋势分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/competitors` | 获取竞争对手分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/regions` | 获取区域分布分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/product-lines` | 获取产品线表现分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/drill-down` | 通用下钻分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/drilldown/revenue` | 收入下钻分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/drilldown/win-rate` | 中标率下钻分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/drilldown/team` | 团队下钻分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/drilldown/projects` | 项目下钻分析 | `hasAuthority('dashboard')` |
| 工作台分析 | GET | `/api/analytics/status-distribution` | 获取状态分布统计 | `hasAuthority('dashboard')` |
| 工作台分析 | POST | `/api/analytics/cache/clear` | 清除工作台缓存 | `hasAuthority('dashboard')` |
| 客户类型分析 | GET | `/api/analytics/customer-types` | 获取客户类型分析 | `isAuthenticated()` |
| 客户类型分析 | GET | `/api/analytics/drilldown/customer-type` | 客户类型下钻分析 | `isAuthenticated()` |

## 十九、资源管理 — CA证书

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| CA证书 | GET | `/api/ca-certificates` | 分页列表查询 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/overview` | 证书概览统计 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/{id}` | 证书详情 | `hasAuthority('resource')` |
| CA证书 | POST | `/api/ca-certificates` | 新增CA证书 | `hasAuthority('resource')` |
| CA证书 | PUT | `/api/ca-certificates/{id}` | 编辑CA证书 | `hasAuthority('resource')` |
| CA证书 | DELETE | `/api/ca-certificates/{id}` | 下架CA证书 | `hasAuthority('resource')` |
| CA证书 | POST | `/api/ca-certificates/commitment-letter/upload` | 上传承诺书 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/commitment-letter/files/{filename}` | 获取承诺书文件 | `hasAuthority('resource')` |
| CA证书 | POST | `/api/ca-certificates/{id}/borrow` | 发起CA借用申请 | `hasAuthority('resource')` |
| CA证书 | POST | `/api/ca-certificates/borrow-applications/{applicationId}/approve` | 审批通过借用 | `hasAuthority('resource')` |
| CA证书 | POST | `/api/ca-certificates/borrow-applications/{applicationId}/reject` | 驳回借用 | `hasAuthority('resource')` |
| CA证书 | POST | `/api/ca-certificates/borrow-applications/{applicationId}/return` | 登记CA归还 | `hasAuthority('resource')` |
| CA证书 | POST | `/api/ca-certificates/borrow-applications/{applicationId}/cancel` | 取消借用申请 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/{id}/password` | 查看明文密码 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/{id}/borrow-applications` | 查询借用记录 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/borrow-applications/{applicationId}/events` | 查询借用事件流水 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/{id}/audit-logs` | CA证书生命周期操作日志 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/my-borrow-applications` | 我的借用申请 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/my-approvals` | 我的审批 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/pending-approvals` | 待审批列表 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/template` | 下载导入模板 | `hasAuthority('resource')` |
| CA证书 | POST | `/api/ca-certificates/import` | 触发批量导入 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/import/tasks/{taskId}` | 查询导入任务状态 | `hasAuthority('resource')` |
| CA证书 | GET | `/api/ca-certificates/import/tasks` | 查询导入任务历史 | `hasAuthority('resource')` |
| CA证书导出 | GET | `/api/ca-certificates/export` | 批量导出CA证书台账Excel | `hasAuthority('resource')` |

## 二十、资源管理 — 平台账户

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 平台账户 | POST | `/api/platform/accounts` | 创建平台账户 | `hasAuthority('resource')` |
| 平台账户 | GET | `/api/platform/accounts` | 列表查询 | `hasAuthority('resource')` |
| 平台账户 | GET | `/api/platform/accounts/{id}` | 账户详情 | `hasAuthority('resource')` |
| 平台账户 | PUT | `/api/platform/accounts/{id}` | 更新账户 | `hasAuthority('resource')` |
| 平台账户 | DELETE | `/api/platform/accounts/{id}` | 删除账户 | `hasAuthority('system.admin')` |
| 平台账户 | POST | `/api/platform/accounts/{id}/borrow` | 借用账户 | `hasAnyRole('ADMIN','MANAGER')` |
| 平台账户 | POST | `/api/platform/accounts/{id}/return` | 归还账户 | `hasAuthority('resource')` |
| 平台账户 | GET | `/api/platform/accounts/{id}/password` | 查看明文密码 | `hasAuthority('resource')` |
| 平台账户 | GET | `/api/platform/accounts/statistics` | 账户统计 | `hasAnyRole('ADMIN','MANAGER')` |
| 平台账户 | GET | `/api/platform/accounts/overdue` | 查询逾期账户 | `hasAnyRole('ADMIN','MANAGER')` |
| 平台账户 | POST | `/api/platform/accounts/{id}/return-with-password` | 归还并更新密码 | `hasAuthority('resource')` |
| 平台账户 | GET | `/api/platform/accounts/template` | 下载导入模板 | `hasAuthority('resource')` |
| 平台账户 | POST | `/api/platform/accounts/import` | 触发批量导入 | `hasAuthority('resource')` |
| 平台账户 | GET | `/api/platform/accounts/import/tasks/{taskId}` | 查询导入任务状态 | `hasAuthority('resource')` |
| 平台账户 | GET | `/api/platform/accounts/import/tasks` | 查询导入任务历史 | `hasAuthority('resource')` |
| 平台账户借用 | POST | `/api/platform/accounts/{accountId}/borrow-applications` | 提交借用申请 | `isAuthenticated()` |
| 平台账户借用 | GET | `/api/platform/accounts/{accountId}/borrow-applications` | 查询借用记录 | `isAuthenticated()` |
| 平台账户借用 | GET | `/api/borrow-applications/my-applications` | 我的借用申请 | `isAuthenticated()` |
| 平台账户借用 | GET | `/api/borrow-applications/my-approvals` | 我的审批 | `isAuthenticated()` |
| 平台账户借用 | POST | `/api/borrow-applications/{id}/approve` | 审批通过 | `isAuthenticated()` |
| 平台账户借用 | POST | `/api/borrow-applications/{id}/reject` | 拒绝申请 | `isAuthenticated()` |
| 平台账户借用 | POST | `/api/borrow-applications/{id}/cancel` | 撤销申请 | `isAuthenticated()` |
| 平台账户借用 | POST | `/api/borrow-applications/{id}/return` | 归还账号 | `isAuthenticated()` |
| 平台账户审计 | GET | `/api/platform/accounts/{id}/audit-logs` | 操作日志 | `hasAuthority('resource')` |
| 平台账户导出 | GET | `/api/platform/accounts/export` | 批量导出台账Excel | `hasAuthority('resource')` |

## 二十一、资源管理 — 账户/保证金/费用/BAR资产

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 账户管理 | POST | `/api/resources/accounts` | 创建账户记录 | `isAuthenticated()` |
| 账户管理 | GET | `/api/resources/accounts/{id}` | 获取账户详情 | `isAuthenticated()` |
| 账户管理 | GET | `/api/resources/accounts` | 分页列表查询 | `isAuthenticated()` |
| 账户管理 | GET | `/api/resources/accounts/type/{type}` | 按类型筛选 | `isAuthenticated()` |
| 账户管理 | GET | `/api/resources/accounts/industry/{industry}` | 按行业筛选 | `isAuthenticated()` |
| 账户管理 | GET | `/api/resources/accounts/region/{region}` | 按地区筛选 | `isAuthenticated()` |
| 账户管理 | GET | `/api/resources/accounts/credit-level/{creditLevel}` | 按信用等级筛选 | `isAuthenticated()` |
| 账户管理 | GET | `/api/resources/accounts/search` | 关键词搜索 | `isAuthenticated()` |
| 账户管理 | PUT | `/api/resources/accounts/{id}` | 更新账户记录 | `hasAnyRole('ADMIN','MANAGER')` |
| 账户管理 | DELETE | `/api/resources/accounts/{id}` | 删除账户记录 | `hasAnyRole('ADMIN','MANAGER')` |
| 账户管理 | GET | `/api/resources/accounts/statistics` | 账户统计信息 | `isAuthenticated()` |
| 保证金 | GET | `/api/resource/margin/summary` | 保证金汇总统计 | `isAuthenticated()` |
| 保证金 | GET | `/api/resource/margin/list` | 保证金台账分页列表 | `isAuthenticated()` |
| 保证金 | GET | `/api/resource/margin/export` | 导出保证金台账Excel | `isAuthenticated()` |
| 费用支出 | POST | `/api/resources/expenses` | 创建费用记录 | `isAuthenticated()` |
| 费用支出 | GET | `/api/resources/expenses/{id}` | 获取费用详情 | `isAuthenticated()` |
| 费用支出 | GET | `/api/resources/expenses` | 分页列表查询 | `isAuthenticated()` |
| 费用支出 | GET | `/api/resources/expenses/ledger` | 费用台账多维查询 | `isAuthenticated()` |
| 费用支出 | GET | `/api/resources/expenses/project/{projectId}` | 按项目ID查询费用 | `isAuthenticated()` |
| 费用支出 | PUT | `/api/resources/expenses/{id}` | 更新费用记录 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用支出 | DELETE | `/api/resources/expenses/{id}` | 删除费用记录 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用支出 | GET | `/api/resources/expenses/project/{projectId}/total` | 项目费用总额 | `isAuthenticated()` |
| 费用支出 | GET | `/api/resources/expenses/project/{projectId}/statistics` | 项目费用统计 | `isAuthenticated()` |
| 费用支出 | POST | `/api/resources/expenses/{id}/approve` | 审批费用 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用支出 | POST | `/api/resources/expenses/{id}/return-request` | 申请费用退还 | `isAuthenticated()` |
| 费用支出 | POST | `/api/resources/expenses/{id}/confirm-return` | 确认费用退还 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用支出 | POST | `/api/resources/expenses/{id}/payments` | 登记费用付款 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用支出 | GET | `/api/resources/expenses/{id}/payments` | 查询付款记录 | `isAuthenticated()` |
| 费用支出 | POST | `/api/resources/expenses/{id}/return-reminder` | 发送退还提醒 | `isAuthenticated()` |
| BAR资产 | POST | `/api/resources/bar-assets` | 创建BAR资产 | `isAuthenticated()` |
| BAR资产 | GET | `/api/resources/bar-assets/{id}` | 获取资产详情 | `isAuthenticated()` |
| BAR资产 | GET | `/api/resources/bar-assets` | 分页列表查询 | `isAuthenticated()` |
| BAR资产 | GET | `/api/resources/bar-assets/type/{type}` | 按类型筛选 | `isAuthenticated()` |
| BAR资产 | GET | `/api/resources/bar-assets/status/{status}` | 按状态筛选 | `isAuthenticated()` |
| BAR资产 | GET | `/api/resources/bar-assets/value-range` | 按价值范围筛选 | `isAuthenticated()` |
| BAR资产 | GET | `/api/resources/bar-assets/search` | 关键词搜索 | `isAuthenticated()` |
| BAR资产 | PUT | `/api/resources/bar-assets/{id}` | 更新资产 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR资产 | DELETE | `/api/resources/bar-assets/{id}` | 删除资产 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR资产 | GET | `/api/resources/bar-assets/total-value` | 资产总价值 | `isAuthenticated()` |
| BAR资产 | GET | `/api/resources/bar-assets/statistics` | 资产统计 | `isAuthenticated()` |
| BAR证书 | GET | `/api/resources/bar-assets/{assetId}/certificates` | 获取证书列表 | `isAuthenticated()` |
| BAR证书 | POST | `/api/resources/bar-assets/{assetId}/certificates` | 创建证书 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR证书 | PUT | `/api/resources/bar-assets/{assetId}/certificates/{certificateId}` | 更新证书 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR证书 | DELETE | `/api/resources/bar-assets/{assetId}/certificates/{certificateId}` | 删除证书 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR证书 | POST | `/api/resources/bar-assets/{assetId}/certificates/{certificateId}/borrow` | 借用证书 | `isAuthenticated()` |
| BAR证书 | POST | `/api/resources/bar-assets/{assetId}/certificates/{certificateId}/return` | 归还证书 | `isAuthenticated()` |
| BAR证书 | GET | `/api/resources/bar-assets/{assetId}/certificates/{certificateId}/borrow-records` | 查询借用记录 | `isAuthenticated()` |
| BAR站点子资源 | GET | `/api/resources/bar-assets/{assetId}/accounts` | 获取站点账号列表 | `isAuthenticated()` |
| BAR站点子资源 | POST | `/api/resources/bar-assets/{assetId}/accounts` | 创建站点账号 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR站点子资源 | PUT | `/api/resources/bar-assets/{assetId}/accounts/{accountId}` | 更新站点账号 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR站点子资源 | DELETE | `/api/resources/bar-assets/{assetId}/accounts/{accountId}` | 删除站点账号 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR站点子资源 | PATCH | `/api/resources/bar-assets/{assetId}/status` | 更新站点状态 | `hasAnyRole('ADMIN','MANAGER')` |
| BAR站点子资源 | POST | `/api/resources/bar-assets/{assetId}/verify` | 验证站点 | `isAuthenticated()` |
| BAR站点子资源 | GET | `/api/resources/bar-assets/{assetId}/verification-records` | 查询验证记录 | `isAuthenticated()` |
| BAR站点子资源 | GET | `/api/resources/bar-assets/{assetId}/sop` | 获取SOP | `isAuthenticated()` |
| BAR站点子资源 | PUT | `/api/resources/bar-assets/{assetId}/sop` | 更新SOP | `hasAnyRole('ADMIN','MANAGER')` |
| BAR站点子资源 | GET | `/api/resources/bar-assets/{assetId}/attachments` | 获取附件列表 | `isAuthenticated()` |
| BAR站点子资源 | POST | `/api/resources/bar-assets/{assetId}/attachments` | 创建附件 | `isAuthenticated()` |
| BAR站点子资源 | DELETE | `/api/resources/bar-assets/{assetId}/attachments/{attachmentId}` | 删除附件 | `hasAnyRole('ADMIN','MANAGER')` |

## 二十二、费用管理

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 费用管理 | POST | `/api/fees` | 创建费用 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用管理 | GET | `/api/fees` | 分页列表查询 | `isAuthenticated()` |
| 费用管理 | GET | `/api/fees/{id}` | 费用详情 | `isAuthenticated()` |
| 费用管理 | GET | `/api/fees/project/{projectId}` | 按项目ID查询费用 | `isAuthenticated()` |
| 费用管理 | PUT | `/api/fees/{id}` | 更新费用 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用管理 | DELETE | `/api/fees/{id}` | 删除费用 | `hasAuthority('system.admin')` |
| 费用管理 | POST | `/api/fees/{id}/pay` | 标记已支付 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用管理 | POST | `/api/fees/{id}/return` | 标记已退还 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用管理 | POST | `/api/fees/{id}/cancel` | 取消费用 | `hasAnyRole('ADMIN','MANAGER')` |
| 费用管理 | GET | `/api/fees/statistics` | 项目费用统计 | `hasAnyRole('ADMIN','MANAGER')` |

## 二十三、知识库 — 资质管理

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 资质 | POST | `/api/knowledge/qualifications` | 创建资质 | `hasAuthority('qualification.manage')` |
| 资质 | POST | `/api/knowledge/qualifications/upload-parse` | AI解析资质证书 | `hasAuthority('qualification.manage')` |
| 资质 | POST | `/api/knowledge/qualifications/{id}/upload` | 上传资质附件 | `hasAuthority('qualification.manage')` |
| 资质 | PUT | `/api/knowledge/qualifications/{id}/attachments/{attachmentId}/replace` | 替换资质附件 | `hasAuthority('qualification.manage')` |
| 资质 | DELETE | `/api/knowledge/qualifications/{id}/attachments/{attachmentId}` | 删除资质附件 | `hasAuthority('qualification.manage')` |
| 资质 | GET | `/api/knowledge/qualifications` | 分页列表查询 | `hasAuthority('qualification.view')` |
| 资质 | GET | `/api/knowledge/qualifications/{id}` | 资质详情 | `hasAuthority('qualification.view')` |
| 资质 | PUT | `/api/knowledge/qualifications/{id}` | 更新资质 | `hasAuthority('qualification.manage')` |
| 资质 | DELETE | `/api/knowledge/qualifications/{id}` | 删除资质 | `hasAuthority('qualification.manage')` |
| 资质 | GET | `/api/knowledge/qualifications/overview` | 资质概览统计 | `hasAuthority('qualification.view')` |
| 资质 | POST | `/api/knowledge/qualifications/scan-expiring` | 扫描即将到期资质 | `hasAuthority('qualification.manage')` |
| 资质 | POST | `/api/knowledge/qualifications/{id}/retire` | 下架资质证书 | `hasAuthority('qualification.manage')` |
| 资质 | POST | `/api/knowledge/qualifications/{id}/restore` | 恢复资质证书 | `hasAuthority('qualification.manage')` |
| 资质 | GET | `/api/knowledge/qualifications/{id}/attachments/{attachmentId}` | 下载附件 | `hasAuthority('qualification.view')` |
| 资质 | POST | `/api/knowledge/qualifications/{id}/audit-log/upload` | 上传审核日志附件 | `hasAuthority('qualification.manage')` |
| 资质 | GET | `/api/knowledge/qualifications/{id}/audit-log/download` | 下载审核日志附件 | `hasAuthority('qualification.view')` |
| 资质审计 | GET | `/api/qualifications/{id}/audit-logs` | 资质操作日志 | `isAuthenticated()` |
| 资质导出 | GET | `/api/knowledge/qualifications/export` | 导出资质台账Excel | `hasAuthority('qualification.manage')` |
| 资质导出 | GET | `/api/knowledge/qualifications/template` | 下载导入模板 | `hasAuthority('qualification.manage')` |
| 资质导出 | POST | `/api/knowledge/qualifications/batch-export` | 批量导出 | `hasAuthority('qualification.manage')` |
| 资质导出 | POST | `/api/knowledge/qualifications/batch-download` | 批量下载附件ZIP | `hasAuthority('qualification.manage')` |
| 资质导出 | POST | `/api/knowledge/qualifications/import` | 导入资质台账 | `hasAuthority('qualification.manage')` |
| 资质导出 | POST | `/api/knowledge/qualifications/import-combined` | 导入资质并关联附件 | `hasAuthority('qualification.manage')` |
| 资质导出 | POST | `/api/knowledge/qualifications/batch-attach` | 批量关联附件 | `hasAuthority('qualification.manage')` |
| 告警配置 | GET | `/api/qualifications/alert-config` | 获取资质到期提醒配置 | `isAuthenticated()` |
| 告警配置 | PUT | `/api/qualifications/alert-config` | 更新资质到期提醒配置 | `isAuthenticated()` |
| 保证金追踪 | GET | `/api/knowledge/deposit/summary` | 保证金汇总 | `isAuthenticated()` |
| 保证金追踪 | GET | `/api/knowledge/deposit/list` | 保证金列表 | `isAuthenticated()` |
| 保证金追踪 | POST | `/api/knowledge/deposit/return/{id}` | 标记保证金已退还 | `isAuthenticated()` |

## 二十四、知识库 — 仓库/人员/业绩

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 仓库 | GET | `/api/knowledge/warehouses` | 仓库列表 | `hasAuthority('warehouse.manage')` |
| 仓库 | GET | `/api/knowledge/warehouses/{id}` | 仓库详情 | `hasAuthority('warehouse.manage')` |
| 仓库 | GET | `/api/knowledge/warehouses/{id}/logs` | 仓库操作日志 | `hasAuthority('warehouse.manage')` |
| 仓库 | POST | `/api/knowledge/warehouses` | 创建仓库 | `hasAuthority('warehouse.manage')` |
| 仓库 | PUT | `/api/knowledge/warehouses/{id}` | 编辑仓库 | `hasAuthority('warehouse.manage')` |
| 仓库 | POST | `/api/knowledge/warehouses/{id}/close` | 关仓 | `hasAuthority('warehouse.manage')` |
| 仓库 | POST | `/api/knowledge/warehouses/{id}/restore` | 恢复仓库 | `hasAuthority('warehouse.manage')` |
| 仓库导出 | POST | `/api/knowledge/warehouses/export` | 触发导出 | `hasAuthority('warehouse.manage')` |
| 仓库导出 | POST | `/api/knowledge/warehouses/export/ledger` | 触发台账导出 | `hasAuthority('warehouse.manage')` |
| 仓库导出 | GET | `/api/knowledge/warehouses/export/tasks` | 导出任务列表 | `hasAuthority('warehouse.manage')` |
| 仓库导出 | GET | `/api/knowledge/warehouses/export/tasks/{taskId}/status` | 导出任务状态 | `hasAuthority('warehouse.manage')` |
| 仓库导出 | GET | `/api/knowledge/warehouses/export/tasks/{taskId}/download` | 下载导出文件 | `hasAuthority('warehouse.manage')` |
| 仓库导入 | GET | `/api/knowledge/warehouses/import/template` | 下载导入模板 | `hasAuthority('warehouse.manage')` |
| 仓库导入 | POST | `/api/knowledge/warehouses/import` | 触发批量导入 | `hasAuthority('warehouse.manage')` |
| 仓库导入 | GET | `/api/knowledge/warehouses/import/tasks` | 导入任务列表 | `hasAuthority('warehouse.manage')` |
| 仓库导入 | GET | `/api/knowledge/warehouses/import/tasks/{taskId}` | 导入任务详情 | `hasAuthority('warehouse.manage')` |
| 仓库附件 | POST | `/api/knowledge/warehouses/{id}/attachments` | 上传附件 | `hasAuthority('warehouse.manage')` |
| 仓库附件 | GET | `/api/knowledge/warehouses/{id}/attachments` | 附件列表 | `hasAuthority('warehouse.manage')` |
| 仓库附件 | DELETE | `/api/knowledge/warehouses/{id}/attachments/{attachmentId}` | 删除附件 | `hasAuthority('warehouse.manage')` |
| 仓库附件 | GET | `/api/knowledge/warehouses/{id}/attachments/{attachmentId}/download` | 下载附件 | `hasAuthority('warehouse.manage')` |
| 人员 | POST | `/api/knowledge/personnel` | 创建人员 | `hasAuthority('personnel.manage')` |
| 人员 | GET | `/api/knowledge/personnel` | 人员列表 | `hasAuthority('personnel.view')` |
| 人员 | GET | `/api/knowledge/personnel/{id}` | 人员详情 | `hasAuthority('personnel.view')` |
| 人员 | PUT | `/api/knowledge/personnel/{id}` | 更新人员 | `hasAuthority('personnel.manage')` |
| 人员 | DELETE | `/api/knowledge/personnel/{id}` | 删除人员 | `hasAuthority('personnel.manage')` |
| 人员 | POST | `/api/knowledge/personnel/{id}/restore` | 恢复已停用人员 | `hasAuthority('personnel.manage')` |
| 人员 | POST | `/api/knowledge/personnel/{personnelId}/certificates/{certId}/attachment` | 上传/替换证书附件 | `hasAuthority('personnel.manage')` |
| 人员 | GET | `/api/knowledge/personnel/{id}/operation-logs` | 人员操作日志 | `hasAuthority('personnel.view')` |
| 人员 | GET | `/api/knowledge/personnel/attachments/{personnelId}/{filename}` | 下载证书附件 | `hasAuthority('personnel.view')` |
| 人员导入 | POST | `/api/knowledge/personnel/import` | 开始批量导入 | `hasAuthority('personnel.manage')` |
| 人员导入 | GET | `/api/knowledge/personnel/import/{taskId}` | 导入进度查询 | `hasAuthority('personnel.manage')` |
| 人员导入 | GET | `/api/knowledge/personnel/import/{taskId}/report` | 下载错误报告 | `hasAuthority('personnel.manage')` |
| 人员导入 | GET | `/api/knowledge/personnel/import/template` | 下载导入模板 | `hasAuthority('personnel.manage')` |
| 人员导出 | POST | `/api/knowledge/personnel/export` | 开始批量导出 | `hasAuthority('personnel.manage')` |
| 人员导出 | GET | `/api/knowledge/personnel/export/{taskId}` | 导出进度查询 | `hasAuthority('personnel.manage')` |
| 人员导出 | GET | `/api/knowledge/personnel/export/{taskId}/download` | 下载导出文件 | `hasAuthority('personnel.manage')` |
| 人员附件 | POST | `/api/knowledge/personnel/attachments/batch-upload` | 批量上传证书附件 | `hasAuthority('personnel.manage')` |
| 业绩 | POST | `/api/knowledge/performance` | 创建业绩 | `hasAuthority('performance.manage')` |
| 业绩 | GET | `/api/knowledge/performance` | 业绩列表 | `hasAuthority('performance.manage')` |
| 业绩 | GET | `/api/knowledge/performance/{id}` | 业绩详情 | `hasAuthority('performance.manage')` |
| 业绩 | PUT | `/api/knowledge/performance/{id}` | 更新业绩 | `hasAuthority('performance.manage')` |
| 业绩 | DELETE | `/api/knowledge/performance/{id}` | 删除业绩 | `hasAuthority('performance.manage')` |
| 业绩 | GET | `/api/knowledge/performance/template` | 下载导入模板 | `hasAuthority('performance.manage')` |
| 业绩 | POST | `/api/knowledge/performance/import` | 批量导入业绩 | `hasAuthority('performance.manage')` |
| 业绩 | GET | `/api/knowledge/performance/export` | 批量导出Excel | `hasAuthority('performance.manage')` |
| 业绩 | GET | `/api/knowledge/performance/export-zip` | ZIP导出（含附件） | `hasAuthority('performance.manage')` |
| 业绩附件 | POST | `/api/knowledge/performance/attachments/upload` | 上传业绩附件 | `hasAuthority('performance.manage')` |
| 业绩附件 | GET | `/api/knowledge/performance/attachments/{id}/download` | 下载业绩附件 | `hasAuthority('performance.manage')` |
| 业绩审计 | GET | `/api/knowledge/performance/{id}/audit-logs` | 业绩操作日志 | `hasAuthority('performance.manage')` |
| 业绩提醒 | GET | `/api/knowledge/performance/alert-config` | 获取合同到期提醒配置 | `isAuthenticated()` |
| 业绩提醒 | PUT | `/api/knowledge/performance/alert-config` | 更新合同到期提醒配置 | `isAuthenticated()` |

## 二十五、文档系统

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 文档编辑器 | GET | `/api/documents/{projectId}/editor/structure` | 获取文档结构 | `isAuthenticated()` |
| 文档编辑器 | POST | `/api/documents/{projectId}/editor/structure` | 创建文档结构 | `hasAnyRole('ADMIN','MANAGER')` |
| 文档编辑器 | GET | `/api/documents/{projectId}/editor/sections/tree` | 获取章节树 | `isAuthenticated()` |
| 文档编辑器 | POST | `/api/documents/{projectId}/editor/draft-tree` | 批量导入草稿树 | `isAuthenticated()` |
| 文档编辑器 | POST | `/api/documents/{projectId}/editor/sections` | 添加章节 | `isAuthenticated()` |
| 文档编辑器 | PUT | `/api/documents/{projectId}/editor/sections/{id}` | 更新章节 | `isAuthenticated()` |
| 文档编辑器 | POST | `/api/documents/{projectId}/editor/assignments` | 分配章节 | `isAuthenticated()` |
| 文档编辑器 | POST | `/api/documents/{projectId}/editor/locks` | 更新章节锁定 | `isAuthenticated()` |
| 文档编辑器 | POST | `/api/documents/{projectId}/editor/reminders` | 创建提醒 | `isAuthenticated()` |
| 文档编辑器 | DELETE | `/api/documents/{projectId}/editor/sections/{id}` | 删除章节 | `hasAnyRole('ADMIN','MANAGER')` |
| 文档编辑器 | PUT | `/api/documents/{projectId}/editor/sections/reorder` | 重新排序章节 | `isAuthenticated()` |
| 文档组装 | GET | `/api/documents/assembly/templates` | 获取模板列表 | `isAuthenticated()` |
| 文档组装 | POST | `/api/documents/assembly/templates` | 创建新模板 | `hasAnyRole('ADMIN','MANAGER')` |
| 文档组装 | GET | `/api/documents/assembly/{projectId}` | 获取组装记录 | `isAuthenticated()` |
| 文档组装 | POST | `/api/documents/assembly/{projectId}/assemble` | 组装新文档 | `isAuthenticated()` |
| 文档组装 | PUT | `/api/documents/assembly/{id}/regenerate` | 重新生成文档 | `isAuthenticated()` |
| 文档导出 | GET | `/api/documents/{projectId}/exports` | 导出记录列表 | `isAuthenticated()` |
| 文档导出 | POST | `/api/documents/{projectId}/exports` | 创建文档导出 | `isAuthenticated()` |
| 文档导出 | GET | `/api/documents/{projectId}/archive-records` | 归档记录列表 | `isAuthenticated()` |
| 文档导出 | GET | `/api/documents/{projectId}/case-snapshot` | 获取案例快照 | `isAuthenticated()` |
| 文档导出 | POST | `/api/documents/{projectId}/archive` | 归档文档 | `isAuthenticated()` |
| 文档智能 | POST | `/api/doc-insight/parse` | 上传并解析文档 | `isAuthenticated()` |
| 文档智能 | POST | `/api/doc-insight/store` | 仅存储文件 | `isAuthenticated()` |
| 文档智能 | POST | `/api/doc-insight/parse-existing` | 解析已存储文件 | `isAuthenticated()` |
| 文档智能 | GET | `/api/doc-insight/download` | 下载文件 | `isAuthenticated()` |
| 文档版本 | GET | `/api/documents/{projectId}/versions` | 获取所有版本 | `hasAnyRole('ADMIN','MANAGER')` |
| 文档版本 | GET | `/api/documents/{projectId}/versions/latest` | 获取最新版本 | `hasAnyRole('ADMIN','MANAGER')` |
| 文档版本 | GET | `/api/documents/{projectId}/versions/{versionId}` | 获取指定版本 | `hasAnyRole('ADMIN','MANAGER')` |
| 文档版本 | POST | `/api/documents/{projectId}/versions` | 创建新版本 | `hasAnyRole('ADMIN','MANAGER')` |
| 文档版本 | GET | `/api/documents/{projectId}/versions/{v1}/compare/{v2}` | 比较版本差异 | `hasAnyRole('ADMIN','MANAGER')` |
| 文档版本 | POST | `/api/documents/{projectId}/versions/{versionId}/rollback` | 回滚到指定版本 | `hasAnyRole('ADMIN','MANAGER')` |

## 二十六、标书生成Agent

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 标书Agent | POST | `/api/projects/{projectId}/bid-agent/tender-documents` | 导入招标文件并解析 | `isAuthenticated()` |
| 标书Agent | POST | `/api/projects/{projectId}/bid-agent/runs` | 创建草稿运行 | `isAuthenticated()` |
| 标书Agent | GET | `/api/projects/{projectId}/bid-agent/runs/{runId}` | 获取草稿运行详情 | `isAuthenticated()` |
| 标书Agent | POST | `/api/projects/{projectId}/bid-agent/runs/{runId}/apply` | 应用草稿产物 | `isAuthenticated()` |
| 标书Agent | POST | `/api/projects/{projectId}/bid-agent/reviews` | 审阅当前草稿 | `isAuthenticated()` |
| 标书Agent | POST | `/api/projects/{projectId}/bid-agent/runs/{runId}/reviews` | 审阅指定运行草稿 | `isAuthenticated()` |
| 标书Agent | GET | `/api/projects/{projectId}/bid-agent/qualification-match` | 资质匹配 | `isAuthenticated()` |
| 标书Agent | GET | `/api/projects/{projectId}/bid-agent/technical-requirements` | 技术要点分类 | `isAuthenticated()` |
| 标书Agent | GET | `/api/projects/{projectId}/bid-agent/commercial-requirements` | 商务条款分类 | `isAuthenticated()` |
| 标书Agent | GET | `/api/projects/{projectId}/bid-agent/risk-classification` | 风险分类 | `isAuthenticated()` |
| 标书Agent | GET | `/api/projects/{projectId}/bid-agent/scoring-criteria` | 评分标准解析 | `isAuthenticated()` |
| 标书Agent | GET | `/api/projects/{projectId}/bid-agent/knowledge-base-match` | 四库联动匹配 | `isAuthenticated()` |
| 标书Agent | GET | `/api/projects/{projectId}/bid-agent/full-analysis` | 全维度分析 | `isAuthenticated()` |

## 二十七、投标结果

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 投标结果 | GET | `/api/bid-results/overview` | 获取概览 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | GET | `/api/bid-results/fetch-results` | 获取抓取结果列表 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | GET | `/api/bid-results/reminders` | 获取提醒列表 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | GET | `/api/bid-results/competitor-report` | 获取竞争对手报表 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | GET | `/api/bid-results/{id}` | 获取详情 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/sync` | 同步投标结果 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/fetch` | 拉取公开投标结果 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/register` | 登记投标结果 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/{id}/update` | 更新投标结果 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/fetch-results/{id}/confirm-with-data` | 确认并补充数据 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/fetch-results/{id}/ignore` | 忽略记录 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/fetch-results/confirm-batch` | 批量确认 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/{resultId}/attachments/bind` | 绑定附件 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/reminders/send` | 发送提醒 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/reminders/send-batch` | 批量发送提醒 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/reminders/{reminderId}/mark-uploaded` | 标记已上传 | `hasAnyRole('ADMIN','MANAGER')` |
| 投标结果 | POST | `/api/bid-results/competitor-wins` | 登记竞争对手中标 | `hasAnyRole('ADMIN','MANAGER')` |

## 二十八、品牌授权

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 品牌授权 | GET | `/api/knowledge/brand-auth` | 品牌授权列表 | `hasAuthority(BRAND_AUTH_VIEW)` |
| 品牌授权 | GET | `/api/knowledge/brand-auth/{id}` | 品牌授权详情 | `hasAuthority(BRAND_AUTH_VIEW)` |
| 品牌授权 | POST | `/api/knowledge/brand-auth` | 创建品牌授权 | `hasAuthority(BRAND_AUTH_CREATE)` |
| 品牌授权 | POST | `/api/knowledge/brand-auth/attachments/upload` | 上传附件 | `hasAuthority(BRAND_AUTH_CREATE)` |
| 品牌授权 | PUT | `/api/knowledge/brand-auth/{id}` | 更新品牌授权 | `hasAuthority(BRAND_AUTH_EDIT)` |
| 品牌授权 | POST | `/api/knowledge/brand-auth/{id}/revoke` | 撤销品牌授权 | `hasAuthority(BRAND_AUTH_REVOKE)` |
| 品牌授权 | GET | `/api/knowledge/brand-auth/{id}/logs` | 获取操作日志 | `hasAuthority(BRAND_AUTH_VIEW)` |
| 品牌授权 | GET | `/api/knowledge/brand-auth/export` | 导出Excel台账 | `hasAuthority(BRAND_AUTH_VIEW)` |
| 品牌授权 | GET | `/api/knowledge/brand-auth/template` | 下载导入模板 | `hasAuthority(BRAND_AUTH_VIEW)` |
| 品牌授权 | POST | `/api/knowledge/brand-auth/import` | 批量导入Excel | `hasAuthority(BRAND_AUTH_CREATE)` |
| 品牌授权 | GET | `/api/knowledge/brand-auth/export-zip` | 导出ZIP | `hasAuthority(BRAND_AUTH_VIEW)` |

## 二十九、合同借阅

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 合同借阅 | GET | `/api/contract-borrows/overview` | 借阅概览 | `isAuthenticated()` |
| 合同借阅 | GET | `/api/contract-borrows` | 借阅列表 | `isAuthenticated()` |
| 合同借阅 | GET | `/api/contract-borrows/{id}` | 借阅详情 | `isAuthenticated()` |
| 合同借阅 | POST | `/api/contract-borrows` | 创建借阅申请 | `isAuthenticated()` |
| 合同借阅 | POST | `/api/contract-borrows/{id}/approve` | 审批通过 | `hasAnyRole('ADMIN','MANAGER')` |
| 合同借阅 | POST | `/api/contract-borrows/{id}/reject` | 审批拒绝 | `hasAnyRole('ADMIN','MANAGER')` |
| 合同借阅 | POST | `/api/contract-borrows/{id}/return` | 归还 | `isAuthenticated()` |
| 合同借阅 | POST | `/api/contract-borrows/{id}/cancel` | 取消借阅 | `isAuthenticated()` |
| 合同借阅 | GET | `/api/contract-borrows/{id}/events` | 操作事件日志 | `isAuthenticated()` |

## 三十、合规检查

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 合规检查 | POST | `/api/compliance/check/project/{projectId}` | 检查项目合规性 | `isAuthenticated()` |
| 合规检查 | POST | `/api/compliance/check/tender/{tenderId}` | 检查标书合规性 | `isAuthenticated()` |
| 合规检查 | GET | `/api/compliance/results/{resultId}` | 获取合规检查结果详情 | `isAuthenticated()` |
| 合规检查 | GET | `/api/compliance/project/{projectId}/results` | 获取项目所有合规检查结果 | `isAuthenticated()` |
| 合规检查 | GET | `/api/compliance/assess-risk/{projectId}` | 评估项目风险 | `isAuthenticated()` |
| 合规检查 | POST | `/api/compliance/bid-document/check/{projectId}` | 标书文档质量核查 | `isAuthenticated()` |
| 合规检查 | GET | `/api/compliance/bid-document/results/{projectId}` | 获取最新质量核查结果 | `isAuthenticated()` |

## 三十一、AI分析

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 竞争情报 | GET | `/api/ai/competition/competitors` | 获取所有竞争对手 | `isAuthenticated()` |
| 竞争情报 | POST | `/api/ai/competition/competitors` | 创建竞争对手 | `hasAnyRole('ADMIN','MANAGER')` |
| 竞争情报 | GET | `/api/ai/competition/project/{projectId}` | 获取项目竞争分析 | `isAuthenticated()` |
| 竞争情报 | POST | `/api/ai/competition/project/{projectId}/analyze` | 分析项目竞争情况 | `hasAnyRole('ADMIN','MANAGER')` |
| 竞争情报 | POST | `/api/ai/competition/analysis` | 创建竞争分析 | `hasAnyRole('ADMIN','MANAGER')` |
| 竞争情报 | GET | `/api/ai/competition/competitor/{id}/history` | 获取竞争对手历史表现 | `isAuthenticated()` |
| 评分分析 | GET | `/api/ai/score-analysis/project/{projectId}` | 获取项目评分分析 | `isAuthenticated()` |
| 评分分析 | GET | `/api/ai/score-analysis/project/{projectId}/history` | 获取历史分析记录 | `isAuthenticated()` |
| 评分分析 | POST | `/api/ai/score-analysis` | 创建评分分析 | `hasAnyRole('ADMIN','MANAGER')` |
| 评分分析 | GET | `/api/ai/score-analysis/compare/{id1}/{id2}` | 比较两个项目评分 | `isAuthenticated()` |
| ROI分析 | GET | `/api/ai/roi/project/{projectId}` | 获取项目ROI分析 | `isAuthenticated()` |
| ROI分析 | POST | `/api/ai/roi` | 创建ROI分析 | `hasAnyRole('ADMIN','MANAGER')` |
| ROI分析 | POST | `/api/ai/roi/project/{projectId}/calculate` | 计算项目ROI | `hasAnyRole('ADMIN','MANAGER')` |
| ROI分析 | POST | `/api/ai/roi/sensitivity` | 执行敏感性分析 | `isAuthenticated()` |
| 项目AI | POST | `/api/projects/score-preview` | 生成项目评分预览 | `isAuthenticated()` |
| 项目AI | GET | `/api/projects/{projectId}/ai-cards` | 获取项目AI卡片信息 | `isAuthenticated()` |

## 三十二、市场洞察

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 市场洞察 | GET | `/api/market-insight/insight` | 获取市场洞察聚合数据 | `isAuthenticated()` |
| 客户商机 | GET | `/api/customer-opportunities/insights` | 获取客户洞察列表 | `isAuthenticated()` |
| 客户商机 | GET | `/api/customer-opportunities/{purchaserHash}/purchases` | 获取客户采购记录 | `isAuthenticated()` |
| 客户商机 | GET | `/api/customer-opportunities/{purchaserHash}/predictions` | 获取客户预测 | `isAuthenticated()` |
| 客户商机 | POST | `/api/customer-opportunities/refresh` | 刷新客户洞察 | `hasAnyRole('ADMIN','MANAGER')` |
| 客户商机 | PUT | `/api/customer-opportunities/predictions/{id}/status` | 更新预测状态 | `hasAnyRole('ADMIN','MANAGER')` |
| 客户商机 | PUT | `/api/customer-opportunities/predictions/{id}/convert` | 转化预测 | `hasAnyRole('ADMIN','MANAGER')` |
| 商机预测 | GET | `/api/market-prediction/{purchaserHash}` | 获取单个业主商机预测 | `hasAnyRole('ADMIN','MANAGER')` |
| 商机预测 | POST | `/api/market-prediction/batch` | 批量获取商机预测 | `hasAnyRole('ADMIN','MANAGER')` |
| 商机预测 | GET | `/api/market-prediction/config/min-count` | 获取预测最少历史数据条数 | `hasAnyRole('ADMIN','MANAGER')` |

## 三十三、日历与协作

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 日历 | GET | `/api/calendar` | 获取日期范围内事件 | `isAuthenticated()` |
| 日历 | GET | `/api/calendar/month/{year}/{month}` | 获取指定月份事件 | `isAuthenticated()` |
| 日历 | GET | `/api/calendar/project/{projectId}` | 根据项目获取事件 | `isAuthenticated()` |
| 日历 | GET | `/api/calendar/urgent` | 获取所有紧急事件 | `isAuthenticated()` |
| 日历 | POST | `/api/calendar` | 创建日历事件 | `hasAnyRole('ADMIN','MANAGER')` |
| 日历 | PUT | `/api/calendar/{id}` | 更新日历事件 | `hasAnyRole('ADMIN','MANAGER')` |
| 日历 | DELETE | `/api/calendar/{id}` | 删除日历事件 | `hasAnyRole('ADMIN','MANAGER')` |
| 协作 | GET | `/api/collaboration/threads` | 获取讨论线程列表 | `isAuthenticated()` |
| 协作 | GET | `/api/collaboration/threads/{id}` | 获取讨论线程详情 | `isAuthenticated()` |
| 协作 | POST | `/api/collaboration/threads` | 创建讨论线程 | `hasAnyRole('ADMIN','MANAGER')` |
| 协作 | PUT | `/api/collaboration/threads/{id}/status` | 更新线程状态 | `hasAnyRole('ADMIN','MANAGER')` |
| 协作 | POST | `/api/collaboration/threads/{id}/comments` | 添加评论 | `isAuthenticated()` |
| 协作 | PUT | `/api/collaboration/comments/{id}` | 更新评论 | `isAuthenticated()` |
| 协作 | DELETE | `/api/collaboration/comments/{id}` | 删除评论 | `isAuthenticated()` |
| 协作 | GET | `/api/collaboration/mentions` | 获取@提及评论 | `isAuthenticated()` |
| @提及 | POST | `/api/mentions` | 创建@提及 | `isAuthenticated()` |

## 三十四、案例知识库

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 案例 | POST | `/api/knowledge/cases` | 创建案例 | `hasAnyRole('ADMIN','MANAGER')` |
| 案例 | GET | `/api/knowledge/cases` | 获取案例分页列表 | `isAuthenticated()` |
| 案例 | GET | `/api/knowledge/cases/{id}` | 获取案例详情 | `isAuthenticated()` |
| 案例 | PUT | `/api/knowledge/cases/{id}` | 更新案例 | `hasAnyRole('ADMIN','MANAGER')` |
| 案例 | DELETE | `/api/knowledge/cases/{id}` | 删除案例 | `hasAnyRole('ADMIN','MANAGER')` |
| 案例 | GET | `/api/knowledge/cases/industry/{industry}` | 按行业获取案例 | `isAuthenticated()` |
| 案例 | GET | `/api/knowledge/cases/outcome/{outcome}` | 按结果获取案例 | `isAuthenticated()` |
| 案例 | GET | `/api/knowledge/cases/search/options` | 获取搜索选项 | `isAuthenticated()` |
| 案例 | GET | `/api/knowledge/cases/{id}/related` | 获取相关推荐案例 | `isAuthenticated()` |
| 案例 | POST | `/api/knowledge/cases/promote-from-project` | 从项目快照晋升案例 | `hasAnyRole('ADMIN','MANAGER')` |
| 案例 | GET | `/api/knowledge/cases/{id}/share-records` | 获取分享记录 | `isAuthenticated()` |
| 案例 | POST | `/api/knowledge/cases/{id}/share-records` | 创建分享记录 | `isAuthenticated()` |
| 案例 | GET | `/api/knowledge/cases/{id}/references` | 获取引用记录 | `isAuthenticated()` |
| 案例 | POST | `/api/knowledge/cases/{id}/references` | 创建引用记录 | `isAuthenticated()` |
| 知识案例 | GET | `/api/cases` | 查询知识案例 | `hasAuthority('project')` |
| 知识案例 | GET | `/api/cases/recommend` | 推荐案例 | `isAuthenticated()` |
| 知识案例 | GET | `/api/cases/recommend/project` | 推荐案例（按项目） | `isAuthenticated()` |
| 知识案例 | GET | `/api/cases/{id}` | 获取案例详情 | `isAuthenticated()` |
| 知识案例 | POST | `/api/cases/{id}/reuse` | 复用案例 | `isAuthenticated()` |
| 知识案例 | POST | `/api/cases/{id}/off-shelf` | 下架案例 | `hasAuthority('project')` |
| 知识案例 | POST | `/api/cases/{id}/pin` | 置顶案例 | `hasAuthority('project')` |
| 知识案例 | POST | `/api/cases/{id}/unpin` | 取消置顶 | `hasAuthority('project')` |
| 知识案例 | GET | `/api/cases/precipitation-readiness` | 案例沉淀就绪检查 | `isAuthenticated()` |
| 知识案例 | POST | `/api/cases/precipitate` | 触发案例沉淀 | `hasAuthority('project')` |
| 知识案例 | GET | `/api/cases/{id}/references` | 获取引用记录 | `isAuthenticated()` |
| 知识案例 | POST | `/api/cases/export-excel` | 导出Excel | `isAuthenticated()` |
| 知识案例 | POST | `/api/cases/export-zip` | 导出ZIP文件包 | `isAuthenticated()` |
| 项目档案 | GET | `/api/archive` | 查询档案列表 | `hasAuthority('project')` |
| 项目档案 | GET | `/api/archive/files` | 查询档案文件列表 | `hasAuthority('project')` |
| 项目档案 | GET | `/api/archive/stats` | 获取档案统计 | `hasAuthority('project')` |
| 项目档案 | GET | `/api/archive/{id}` | 获取档案详情 | `hasAuthority('project')` |
| 项目档案 | GET | `/api/archive/files/{fileId}/preview` | 预览文件 | `hasAuthority('project')` |
| 项目档案 | GET | `/api/archive/files/{fileId}/download` | 下载文件 | `hasAuthority('project')` |
| 项目档案 | POST | `/api/archive/export-excel` | 导出Excel台账 | `hasAuthority('project')` |
| 项目档案 | GET | `/api/archive/export-zip/{projectId}` | 导出单项目ZIP | `hasAuthority('project')` |
| 项目档案 | POST | `/api/archive/export-zip` | 导出ZIP文件包 | `hasAuthority('project')` |
| 案例切片 | GET | `/api/case-slices/recommend` | 按评分项推荐切片 | `isAuthenticated()` |
| 案例切片 | GET | `/api/case-slices/recommend/by-query` | 按查询文本推荐 | `hasAuthority(SYSTEM_ADMIN)` |
| 案例切片 | GET | `/api/case-slices/{id}` | 获取切片详情 | `isAuthenticated()` |
| 案例切片 | POST | `/api/case-slices/admin/batch-embed` | 批量嵌入向量 | `hasAuthority(SYSTEM_ADMIN)` |
| 案例切片 | POST | `/api/case-slices/admin/import` | 从JSONL导入切片 | `hasAuthority(SYSTEM_ADMIN)` |
| 案例切片 | POST | `/api/case-slices/admin/import/slice` | 导入单个切片 | `hasAuthority(SYSTEM_ADMIN)` |
| 案例切片 | GET | `/api/case-slices/admin/stats` | 统计信息 | `hasAuthority(SYSTEM_ADMIN)` |
| 案例切片 | DELETE | `/api/case-slices/admin/{id}` | 删除切片 | `hasAuthority(SYSTEM_ADMIN)` |

## 三十五、模板管理

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 模板 | POST | `/api/knowledge/templates` | 创建模板 | `isAuthenticated()` |
| 模板 | GET | `/api/knowledge/templates` | 获取所有模板 | `isAuthenticated()` |
| 模板 | GET | `/api/knowledge/templates/{id}` | 获取模板详情 | `isAuthenticated()` |
| 模板 | PUT | `/api/knowledge/templates/{id}` | 更新模板 | `isAuthenticated()` |
| 模板 | DELETE | `/api/knowledge/templates/{id}` | 删除模板 | `isAuthenticated()` |
| 模板 | GET | `/api/knowledge/templates/category/{category}` | 按类别获取模板 | `isAuthenticated()` |
| 模板 | POST | `/api/knowledge/templates/{id}/copy` | 复制模板 | `isAuthenticated()` |
| 模板 | GET | `/api/knowledge/templates/{id}/versions` | 获取模板版本历史 | `isAuthenticated()` |
| 模板 | POST | `/api/knowledge/templates/{id}/use-records` | 记录模板使用 | `isAuthenticated()` |
| 模板 | POST | `/api/knowledge/templates/{id}/downloads` | 记录模板下载 | `isAuthenticated()` |

## 三十六、数据导出

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 数据导出 | POST | `/api/export/excel` | 导出数据到Excel | `hasAnyRole('ADMIN','MANAGER')` |
| 数据导出 | POST | `/api/export/excel/download` | 导出并直接下载Excel | `hasAnyRole('ADMIN','MANAGER')` |
| 数据导出 | GET | `/api/export/types` | 获取支持的导出类型列表 | `hasAnyRole('ADMIN','MANAGER')` |
| 数据导出 | GET | `/api/export/config` | 获取导出配置 | `hasAnyRole('ADMIN','MANAGER')` |

## 三十七、文件上传

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 文件上传 | POST | `/api/files/upload-token` | 获取OBS上传临时凭证 | `isAuthenticated()` |
| 文件上传 | POST | `/api/files/{uploadId}/completed` | 上传完成回调通知 | `isAuthenticated()` |
| 文件上传 | GET | `/api/files/{uploadId}/download-url` | 获取文件下载链接 | `isAuthenticated()` |

## 三十八、表单引擎

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 表单运行时 | GET | `/api/form-definitions/{scope}/active` | 获取激活的表单定义 | `isAuthenticated()` |
| 表单运行时 | POST | `/api/form-definitions/{scope}/validate` | 验证表单数据 | `isAuthenticated()` |
| 表单运行时 | POST | `/api/form-definitions/{scope}/submit` | 提交表单数据 | `isAuthenticated()` |
| 表单定义管理 | GET | `/api/admin/form-definitions` | 分页查询表单定义列表 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | GET | `/api/admin/form-definitions/{id}` | 查询表单定义详情 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | POST | `/api/admin/form-definitions` | 创建表单定义 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | PUT | `/api/admin/form-definitions/{id}` | 更新表单定义 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | DELETE | `/api/admin/form-definitions/{id}` | 删除（禁用）表单定义 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | POST | `/api/admin/form-definitions/{id}/publish` | 发布表单定义 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | POST | `/api/admin/form-definitions/{id}/visibility` | 保存字段可见性规则 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | POST | `/api/admin/form-definitions/{id}/conditions` | 保存字段条件规则 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | GET | `/api/admin/form-definitions/{id}/visibility` | 获取字段可见性规则 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | GET | `/api/admin/form-definitions/{id}/conditions` | 获取字段条件规则 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | GET | `/api/admin/form-definitions/{id}/cross-field-rules` | 获取跨字段验证规则 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | POST | `/api/admin/form-definitions/{id}/cross-field-rules` | 保存跨字段验证规则 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | GET | `/api/admin/form-definitions/{id}/tenant-overrides` | 获取租户字段覆盖 | `hasAuthority('bid-SystemAdmin')` |
| 表单定义管理 | POST | `/api/admin/form-definitions/{id}/tenant-overrides` | 保存租户字段覆盖 | `hasAuthority('bid-SystemAdmin')` |

## 三十九、审计日志

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 审计日志 | GET | `/api/audit` | 查询审计日志 | `hasAnyAuthority('operation-logs','ROLE_ADMIN')` |
| 审计日志 | GET | `/api/audit/my` | 查询当前用户操作日志 | `isAuthenticated()` |
| 审计日志 | GET | `/api/audit/project/{projectId}` | 查询项目动态操作日志 | `isAuthenticated()` |

## 四十、外部集成接口

### 40.1 标讯同步（外部API v2.0，X-API-Key认证）

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 标讯同步 | GET | `/api/integration/tenders` | 标讯列表查询 | `hasRole('EXTERNAL_API')` |
| 标讯同步 | POST | `/api/integration/tenders/push` | 标讯创建（幂等推送） | `hasRole('EXTERNAL_API')` |
| 标讯同步 | PUT | `/api/integration/tenders/{sourceSystem}/{sourceId}` | 标讯修改 | `hasRole('EXTERNAL_API')` |
| 标讯同步 | GET | `/api/integration/tenders/{sourceSystem}/{sourceId}` | 标讯详情 | `hasRole('EXTERNAL_API')` |
| 附件下载 | GET | `/api/integration/tenders/attachments/download` | CRM跨系统附件下载 | `permitAll()` |

### 40.2 外部同步API（scope认证）

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 标讯同步 | GET | `/api/external/tenders` | 增量拉取标讯列表 | `SCOPE_TENDER_READ` / `SCOPE_TENDER_WRITE` |
| 标讯同步 | GET | `/api/external/tenders/{id}` | 获取单条标讯详情 | `SCOPE_TENDER_READ` / `SCOPE_TENDER_WRITE` |
| 标讯同步 | POST | `/api/external/tenders` | CRM推送商机→创建标讯 | `SCOPE_TENDER_WRITE` |
| 项目同步 | GET | `/api/external/projects` | 增量拉取项目列表 | `SCOPE_PROJECT_READ` |
| 项目同步 | GET | `/api/external/projects/{id}` | 获取单个项目详情 | `SCOPE_PROJECT_READ` |

### 40.3 企业微信集成

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 企微配置 | GET | `/api/admin/integrations/wecom` | 获取企业微信集成配置 | `hasAuthority('system.admin')` |
| 企微配置 | PUT | `/api/admin/integrations/wecom` | 保存企业微信集成配置 | `hasAuthority('system.admin')` |
| 企微配置 | POST | `/api/admin/integrations/wecom/test` | 测试企业微信连通性 | `hasAuthority('system.admin')` |
| 企微配置 | POST | `/api/admin/integrations/wecom/send-test` | 发送测试消息 | `hasAuthority('system.admin')` |

### 40.4 组织架构

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| 组织架构 | GET | `/api/admin/organization/departments` | 查询启用的部门列表 | `hasAnyRole('ADMIN','MANAGER')` |
| 组织架构 | GET | `/api/integrations/organization/operations/status` | 同步状态查询 | `isAuthenticated()` |
| 组织架构 | POST | `/api/integrations/organization/operations/dead-letters/{eventKey}/replay` | 重放死信事件 | `isAuthenticated()` |
| 组织架构 | POST | `/api/integrations/organization/sync-runs` | 启动同步运行 | `isAuthenticated()` |
| 组织架构 | POST | `/api/integrations/organization/resync/users/{userId}` | 手动重新同步用户 | `isAuthenticated()` |
| 组织架构 | POST | `/api/integrations/organization/resync/departments/{deptId}` | 手动重新同步部门 | `isAuthenticated()` |
| 角色同步 | POST | `/api/admin/roles/{id}/sync-oss-menu-permissions` | 从OSS同步角色菜单权限 | `hasAuthority('system.admin')` |

### 40.5 CRM集成

| 模块 | 方法 | 路径 | 功能说明 | 权限 |
|------|------|------|----------|------|
| CRM | GET | `/api/xiyu/crm/customers` | 搜索CRM客户 | `isAuthenticated()` |
| CRM | GET | `/api/xiyu/crm/customers/{customerId}/contacts` | 获取客户联系人 | `isAuthenticated()` |
| CRM | GET | `/api/xiyu/crm/menus` | 获取菜单树 | `isAuthenticated()` |
| CRM | GET | `/api/xiyu/crm/employees/{token}` | 获取员工信息 | `isAuthenticated()` |
| CRM | POST | `/api/xiyu/crm/messages` | 发送消息 | `isAuthenticated()` |
| CRM | GET | `/api/xiyu/crm/permissions` | 获取用户权限 | `isAuthenticated()` |
| CRM | POST | `/api/xiyu/crm/auth/logout` | 登出 | `isAuthenticated()` |
| CRM商机 | POST | `/api/xiyu/crm/chances/page-list` | 商机列表 | `hasAnyRole('ADMIN','MANAGER')` |
| CRM商机 | POST | `/api/xiyu/crm/chances/search-by-tender` | 按标讯查询商机 | `hasAnyRole('ADMIN','MANAGER')` |
| CRM商机 | POST | `/api/xiyu/crm/chances/bid-info-sync` | 标讯回传 | `hasAnyRole('ADMIN','MANAGER')` |
| CRM商机 | POST | `/api/xiyu/crm/chances/contact-persons` | 对接人列表 | `hasAnyRole('ADMIN','MANAGER')` |
| CRM Webhook | POST | `/api/webhooks/crm/permissions` | 同步CRM权限 | `permitAll()` |
| OSS诊断 | GET | `/api/admin/oss-permission/diagnosis/{username}` | OSS权限缓存诊断 | `hasAuthority('system.admin')` |
| OSS诊断 | DELETE | `/api/admin/oss-permission/cache/{username}` | 清除OSS权限缓存 | `hasAuthority('system.admin')` |

---

> **说明**：
> - 本文档基于 `backend/src/main/java/**/*Controller.java` 全量扫描生成，覆盖 130+ Controller、700+ 接口。
> - 权限列中标注"类级继承"表示该方法未标注独立 `@PreAuthorize`，继承类级注解。
> - `hasRole('EXTERNAL_API')` 为外部API Key认证角色，由 `ApiKeyAuthenticationFilter` 注入。
> - `SYSTEM_ADMIN` 是 `RoleProfileCatalog.SYSTEM_ADMIN_PERMISSION` 常量对应的权限标识。
> - TestController 和 CrmTestController 仅 `@Profile("dev")` 生效，生产不可用，未列入。
> - 如有疑问，以对应 Controller 源代码中的 `@RequestMapping` 和 `@PreAuthorize` 为唯一真相来源。