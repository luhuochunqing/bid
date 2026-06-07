一旦我所属的文件夹有所变化，请更新我。

# businessqualification

商务资质库真实业务子域。
资质借阅记录可能关联项目；项目数据权限不在本纯业务子域内另起体系，而由上层 `qualification` 兼容服务复用 `ProjectAccessScopeService` 和 `ProjectLinkedRecordVisibilityPolicy` 统一收口。按 recordId 归还时，应用服务必须直接校验并归还目标借阅记录，避免校验记录与实际写入记录错位。

## 文件清单

| 文件 | 功能 |
|------|------|
| `domain/` | 资质有效期、借还状态、提醒策略等纯业务决策 |
| `application/` | 创建、更新、借阅、归还、扫描提醒等用例编排 |
| `infrastructure/` | JPA 实体、仓储适配、附件与借阅记录持久化 |
| `../qualification/` | 旧 `/api/knowledge/qualifications` 的兼容协议适配层 |

## 提醒

- 资质到期提醒由 `ScanExpiringQualificationsAppService` 扫描产生
- `AlertSchedulerService` 只负责按规则触发该扫描器
- `relatedId` 协议固定为 `Qualification:{qualificationId}:{expiryDate}`

## 兼容说明

- 现有页面路由与 `/api/knowledge/qualifications` 地址保持不变
- 新 UI 的借阅申请入口已改为先提交 `workflowform` 的 `QUALIFICATION_BORROW` 表单实例，经 OA 通过后才调用本模块借阅应用服务
- 兼容借还两套入口：
  - `POST /api/knowledge/qualifications/{id}/return`
  - `POST /api/knowledge/qualifications/borrow-records/{id}/return`
