# Implementation Plan: 商机预测实时收件人与历史补发

**Branch**: `agent/codex/opportunity-reminder-live-recipients` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/063-opportunity-reminder-live-recipients/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

把商机预测提醒的接收人事实源从本地单角色缓存改为 OSS 批量实时结果：一次扫描先用本地用户缓存取得工号与站内用户 ID，再批量查询 OSS，按实时在职状态和全部活动角色解析项目经理、投标管理员、投标组长。正常预测、每日调度、每项目一次日志和事件投递保持原样。历史补发分两步执行：修复后的正常扫描补齐从未提醒项目；已有经理提醒项目通过一次性脚本复用既有通知内容，只给缺失的实时核心角色创建通知。生产写入前先以只读查询核对固定截止日范围。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21；一次性运维脚本使用仓库既有 Bash/Python 运行环境

**Primary Dependencies**: Spring Boot 3.2、Spring Data JPA、现有 `OrganizationDirectoryGateway`、现有通知应用服务与企微异步投递

**Storage**: MySQL 8.0 既有 `users`、`project`、`opportunity_prediction_reminder_log`、`notification`、`user_notification`；无 schema 变更

**Testing**: JUnit 5、AssertJ、Mockito 仅隔离真实外部 OSS/数据库边界；Spring MockRestServiceServer 验证真实 HTTP 映射

**Target Platform**: Linux 生产服务器，现有 Spring Boot 单实例服务

**Project Type**: 既有 Web 服务的后台定时任务与一次性生产数据操作

**Performance Goals**: 在约 8,600 个本地身份、约 600 个项目规模下完整扫描 60 秒内结束；OSS 查询仅走批量接口

**Constraints**: OSS 返回才视为实时有效；失败关闭；接收人按用户 ID 去重；不删除提醒日志；历史补发重复执行新增为 0；生产发送前必须 dry-run

**Scale/Scope**: 当前生产基线 8,593 个有工号缓存用户、242 个截止日历史项目、3 名实时核心角色；不扩散到其他通知类型

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I FP-Java**: PASS。实时状态和角色判断放入无 Spring 依赖的纯策略；Repository/Gateway 调用留在 resolver shell。
- **II Real-API Only**: PASS。生产代码只调用真实 `OrganizationDirectoryGateway`，不新增 mock/fallback 数据路径。
- **III TDD**: PASS。先以 resolver/mapper/服务测试复现本地角色为 0 与离职经理误判，再实现。
- **IV Split-First**: PASS。现有 `OpportunityPredictionReminderService` 已接近软上限，只替换协作者；新 resolver 与纯策略各自保持小文件。
- **V OSS Integration**: PASS。全量工号一次交给既有批量网关；角色匹配大小写安全；输出请求数、返回数与命中数。
- **VI Authorization**: PASS。最终业务代码不新增公开接口；若生产补跑需要临时入口，只放在 `/api/admin/**`、`isAuthenticated()`，并在补发后删除再部署。
- **VII Defensive Collection**: PASS。所有非唯一工号/角色集合显式 merge 或集合去重；部分 OSS 响应按返回子集降级，不抛垮整轮扫描。
- **VIII Boring Patterns**: PASS。复用现有网关、消息链路和日志，不引入缓存、队列、迁移或新框架。

**Post-design re-check**: PASS。设计未引入 constitution 例外；无需 Complexity Tracking 记录。

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
backend/src/main/java/com/xiyu/bid/
├── integration/organization/
│   ├── dto/OssUserJobAndRoleDto.java
│   └── infrastructure/client/OrganizationDirectoryJsonMapper.java
└── workbench/
    ├── domain/OpportunityPredictionRecipientPolicy.java
    └── service/
        ├── OpportunityPredictionRecipientResolver.java
        └── OpportunityPredictionReminderService.java

backend/src/test/java/com/xiyu/bid/
├── integration/organization/infrastructure/client/OrganizationDirectoryHttpGatewayTest.java
└── workbench/
    ├── domain/OpportunityPredictionRecipientPolicyTest.java
    └── service/
        ├── OpportunityPredictionRecipientResolverTest.java
        └── OpportunityPredictionReminderServiceTest.java

scripts/operations/
└── backfill-opportunity-prediction-reminders.py
```

**Structure Decision**: 保持既有 workbench 分层。纯状态/角色判断进入 `domain`，一次扫描一次 I/O 的组合进入 `service`，OSS JSON 只在既有 mapper 修复“活动角色事实丢失”。一次性历史补发脚本放入 `scripts/operations`，先 dry-run；它只调用已有通知 HTTP API并做只读数据库核对，不直接写通知表。临时手动扫描入口若无法通过现有运行设施调用，则作为部署中间提交存在，执行后立即删除，不进入最终代码差异。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 无 | 无 | 无 |
