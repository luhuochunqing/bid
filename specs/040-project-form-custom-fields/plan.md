# Implementation Plan: 项目三表单已有字段锁定 + 自定义字段扩展

**Branch**: `agent/claude/co601-project-form-custom-fields` | **Date**: 2026-07-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/040-project-form-custom-fields/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

项目三个表单 scope（project.basic / project.initiation / project.detail）从"完全放开或整表只读"两个极端归位为"系统预置字段锁定 + 自定义字段可新增"。技术方案（详见 [research.md](./research.md)）：

1. **存储**：`projects` 与 `project_initiation_details` 各新增 `custom_fields` JSON 列（Flyway V+U 迁移），值按 scope 命名空间两级结构存放（`{"project.basic": {"budgetLevel": "重点客户"}}`）
2. **后端**：`ProjectRequest` / `InitiationDto` / `ProjectDTO` / `InitiationViewDto` 增加 `Map<String, Object> customFields`，Service/Mapper 用注入的 ObjectMapper 手工序列化（沿用 customerInfoJson 已验证模式），反序列化失败降级空 Map（Constitution VII）
3. **前端设计器**：`UNSUPPORTED_PROJECT_SCOPES` 整表只读移除，改为按 scope 的预置字段锁定清单 `PROJECT_LOCKED_FIELD_KEYS`（key/type 禁改、禁删），保存/发布解禁，key 冲突保存前阻断
4. **前端业务页**：新增 `useCustomFields` composable 统一"收集进 payload / 合并回 model"逻辑，接入创建向导（buildApiProjectPayload / loadProjectData）与立项页（buildPayload / load）

## Technical Context

**Language/Version**: Java 21（Spring Boot 3.2 + JPA）/ JavaScript ES2022+（Vue 3.4 + Vite 5）

**Primary Dependencies**: Spring Data JPA、Jackson（ObjectMapper Bean 注入复用）、Element Plus、Vue Composition API；表单引擎自研（formengine 模块 + workflow-form-designer）

**Storage**: MySQL 8.0 + Flyway（迁移目录 `backend/src/main/resources/db/migration-mysql/`，当前最大 V1182，新增必须用 `scripts/new-migration.sh` 取号；配套 U 回滚脚本放 `db/rollback/migration-mysql/`）

**Testing**: 后端 JUnit 5 + Mockito + ArchUnit（`ArchitectureTest` / `FPJavaArchitectureTest` / `MaintainabilityArchitectureTest` / `FlywayRollbackScriptCoverageTest`）；前端 Vitest；E2E Playwright

**Target Platform**: Linux server（后端）/ 现代浏览器（前端）

**Project Type**: web-service（前后端分离单体仓库）

**Performance Goals**: 无新增性能诉求；custom_fields JSON 列读写随既有项目创建/立项/详情链路，数据量 <10KB/行

**Constraints**:
- FP-Java：校验/收集逻辑为纯函数，Service 仅编排（Constitution I）
- 单文件 <300 行硬上限（Constitution IV + line-budget 门禁）
- ObjectMapper 必须构造注入复用 Bean，禁止 new（project_memory）
- 反序列化失败必须降级不炸主流程（Constitution VII）
- 每 V 迁移必有 U 回滚（FlywayRollbackScriptCoverageTest 门禁）
- 真实 API 唯一源，禁止 Mock（Constitution II）

**Scale/Scope**: 3 个表单 scope、2 张表加列、4 个 DTO 加字段、1 个新 composable、设计器 3 文件改动；自定义字段数量级预期每 scope <20 个

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 说明 |
|---|---|---|
| I. FP-Java Architecture | ✅ PASS | key 冲突校验（后端 `CustomFieldsSchemaPolicy` 纯函数）与前端收集/合并（`useCustomFields` 纯函数 composable）均无状态；Service 仅编排 |
| II. Real-API Only | ✅ PASS | 全链路真实 API，不引入任何 Mock 路径 |
| III. Test-Driven Development | ✅ PASS | tasks 按 Red→Green→Refactor 排布：先后端单测、前端单测，再实现，最后 E2E 全链路；ArchitectureTest 保持全绿 |
| IV. Split-First & Simplicity | ✅ PASS | 新增文件均 <300 行；设计器改动限定既有文件职责内；DTO 仅加一个字段不触发拆分 |
| V. OSS Integration | ✅ N/A | 本特性不涉及 OSS |
| VI. Authorization Unification | ✅ PASS | 设计器/业务接口权限沿用现状，不新增端点权限注解形态，不新增 hasAnyRole |
| VII. Defensive Collection & Graceful Degradation | ✅ PASS | JSON 反序列化 try-catch 降级空 Map + log.warn；不新增 `Collectors.toMap` 2 参数调用 |
| VIII. Boring Proven Patterns | ✅ PASS | 复用 customer_info_json JSON 列模式与 LOCKED_FIELD_KEYS 锁定机制，无新框架/新范式 |

**Gate 结论（Phase 0 前）**: 全部通过，无违规需记录。

**Gate 结论（Phase 1 设计后复查）**: 全部通过。data-model 未引入新实体仅加列；contracts 无新增端点与权限注解变更；Policy/composable 均为纯函数设计；序列化降级策略符合 Principle VII。无违规需记录。

## Project Structure

### Documentation (this feature)

```text
specs/040-project-form-custom-fields/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── project-custom-fields-api.md
├── checklists/
│   └── requirements.md  # /speckit-specify 质量门禁（已完成）
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/
│   ├── entity/Project.java                              # +customFields String 列（JSON）
│   ├── project/
│   │   ├── entity/ProjectInitiationDetails.java         # +customFields String 列（JSON）
│   │   ├── dto/ProjectRequest.java                      # +Map<String,Object> customFields
│   │   ├── dto/InitiationDto.java                       # +Map<String,Object> customFields
│   │   ├── dto/InitiationViewDto.java                   # +Map<String,Object> customFields
│   │   ├── dto/ProjectDTO.java                          # +Map<String,Object> customFields
│   │   └── service/（ProjectService/ProjectInitiationService/ProjectInitiationMapper）  # 序列化/反序列化编排
│   └── formengine/
│       └── domain/CustomFieldsSchemaPolicy.java         # 新增：项目三 scope key 冲突纯函数校验
├── src/main/resources/db/migration-mysql/
│   └── V####__add_custom_fields_to_project_tables.sql   # new-migration.sh 取号
├── src/main/resources/db/rollback/migration-mysql/
│   └── U####__add_custom_fields_to_project_tables.sql
└── src/test/java/com/xiyu/bid/                          # 对应单测（序列化/Policy/Mapper）

src/
├── composables/
│   └── useCustomFields.js                               # 新增：收集/合并纯函数 composable
├── views/Project/create/composables/
│   └── useProjectCreateModel.js                         # buildApiProjectPayload/loadProjectData 接入 customFields
├── views/Project/stages/
│   └── useInitiationStageActions.js                     # buildPayload/load 接入 customFields
├── views/System/
│   ├── WorkflowFormDesigner.vue                         # 移除 UNSUPPORTED_PROJECT_SCOPES 整表只读
│   └── workflow-form-designer/
│       ├── workflowFormDesignerCore.js                  # +PROJECT_LOCKED_FIELD_KEYS 按 scope 映射
│       └── components/DesignerFieldList.vue             # isLocked/isKeyLocked 加 scope 维度
└── tests/ 或 __tests__/                                  # 前端单测（composable/锁定逻辑）

e2e/
└── project-form-custom-fields.spec.js                   # 新增：设计器新增→填写→提交→回显全链路
```

**Structure Decision**: 沿用现有单体仓库前后端结构（前端 `src/`、后端 `backend/`），不新增顶层工程。后端改动分布在 `entity` / `project` / `formengine` 三个既有包，符合 ArchUnit 包边界；纯函数校验放 `formengine/domain`（schema 校验归属表单引擎域），自定义字段收集逻辑前端集中在 `src/composables/useCustomFields.js` 单一复用点（project_memory 硬约束）。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违规，无需记录。
