# 标讯提醒模块

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
管理标讯关键时间节点（报名截止、开标）的提醒设置与触发。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `controller/TenderReminderController.java` | Controller | 提醒设置 CRUD 接口 |
| `service/TenderReminderService.java` | Service | 提醒设置编排 |
| `job/TenderReminderJob.java` | Job | 定时检查并发送提醒 |
| `domain/TenderReminderPolicy.java` | Pure Core | 提醒时间计算与发送判断 |
| `dto/*` | DTO | 提醒相关数据传输对象 |
| `entity/*` | Entity | 提醒设置与日志 JPA 实体 |
| `repository/*` | Repository | 数据持久化 |

## API 端点
- `GET /api/tenders/{tenderId}/reminders` - 获取提醒列表
- `POST /api/tenders/{tenderId}/reminders` - 创建提醒
- `PUT /api/tenders/{tenderId}/reminders/{reminderId}` - 更新提醒
- `DELETE /api/tenders/{tenderId}/reminders/{reminderId}` - 删除提醒

## 架构边界
- `domain/*` 满足 FP-Java Profile：`record` + `static` 方法，返回不可变值
- Job 负责定时任务编排，Policy 仅做纯逻辑计算
