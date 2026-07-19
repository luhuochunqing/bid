# Implementation Plan: OBS 直传项目文档同步归档到项目档案

**Branch**: `agent/trae2/archive-oss-direct-upload-sync` | **Date**: 2026-07-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/039-archive-oss-direct-upload-sync/spec.md`

## Summary

将项目文档归档逻辑从 multipart 上传专用路径（`ProjectDocumentUploadWorkflowService`）上提到统一入口（`ProjectDocumentWorkflowService.createProjectDocument`），使 JSON API 创建路径（OBS 直传场景）也能触发归档。配套提供 Flyway V1168 迁移脚本回填历史数据。改动后端 2 个 Java 文件 + 1 个 Flyway 迁移 + 1 个回滚迁移 + 2 个测试文件。前端无改动。

## Technical Context

**Language/Version**: Java 21（后端）

**Primary Dependencies**: Spring Boot 3.2 + Spring Data JPA + Flyway + Lombok

**Storage**: MySQL 8.0（archive_file 表，本次新增 V1168 数据修复迁移，不修改 schema）

**Testing**: JUnit 5 + Mockito（后端单元测试，`ProjectDocumentWorkflowServiceTest` 和 `ProjectDocumentUploadWorkflowServiceTest`）

**Target Platform**: Linux server

**Project Type**: Web service

**Performance Goals**: 沿用现有（归档副作用在文档创建链路内同步执行，单次归档 < 10ms，不影响上传吞吐）

**Constraints**: 沿用现有约束。不引入新依赖，不修改前端，不修改 schema

**Scale/Scope**: 改动 4 个文件（2 Java + 2 SQL）+ 2 个测试文件更新。新增 0 个 Java 文件。总改动行数约 60-100 行

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 状态 | 说明 |
|---|---|---|
| I. FP-Java Architecture | ✅ 通过 | 归档副作用在 Imperative Shell（`ProjectDocumentWorkflowService`）层执行，不污染纯核心。`DocumentCategoryNormalizer` 是纯核心，未修改 |
| II. Real-API Only | ✅ 通过 | 沿用真实 JPA repository，无 Mock |
| III. Test-Driven Development | ✅ 通过 | 先在 `ProjectDocumentWorkflowServiceTest` 加 Red 用例（"createProjectDocument 后 archive_file 有记录"），再改实现（Green） |
| IV. Split-First & Simplicity | ✅ 通过 | 不新增文件，仅修改现有文件。改动行数 < 100 |
| V. OSS Integration | ✅ 通过 | 本次修复核心就是补齐 OBS 直传场景的归档副作用 |
| VI. Authorization Unification | ✅ 通过 | 不涉及权限变更，归档副作用在已通过权限校验的 `createProjectDocument` 内执行 |
| VII. Defensive Collection & Graceful Degradation | ✅ 通过 | 归档失败 try-catch 包裹，log.warn 不抛出，主流程降级处理 |
| VIII. Boring Proven Patterns | ✅ 通过 | 复用现有 `attachFileToArchive` 方法，不引入新模式 |

**结论**：无违规，无需 Complexity Tracking 表。

## Project Structure

### Documentation (this feature)

```text
specs/039-archive-oss-direct-upload-sync/
├── plan.md              # 本文件
├── research.md          # Phase 0 研究输出
├── data-model.md        # Phase 1 数据模型变更（无 schema 变更，仅数据修复）
├── checklists/
│   └── requirements.md  # spec 质量检查清单
└── tasks.md             # Phase 2 输出
```

### Source Code (repository root)

```text
backend/src/main/java/com/xiyu/bid/projectworkflow/service/
├── ProjectDocumentWorkflowService.java       # 修改：在 createProjectDocument 末尾调用 attachFileToArchive
└── ProjectDocumentUploadWorkflowService.java # 修改：删除末尾的 attachFileToArchive 调用，避免双写

backend/src/main/resources/db/migration-mysql/
└── V1168__backfill_archive_files_for_obs_direct_uploads.sql  # 新增：数据修复迁移

backend/src/main/resources/db/rollback/migration-mysql/
└── U1168__backfill_archive_files_for_obs_direct_uploads.sql  # 新增：回滚脚本

backend/src/test/java/com/xiyu/bid/projectworkflow/service/
├── ProjectDocumentWorkflowServiceTest.java        # 更新：新增"createProjectDocument 后 archive_file 有记录"用例
└── ProjectDocumentUploadWorkflowServiceTest.java  # 更新：调整验证 attachFileToArchive 不再被 uploadWorkflowService 直接调用
```

**Structure Decision**: 沿用现有 projectworkflow/casework 模块结构，不新增包或文件（迁移脚本除外）。归档副作用上提到 `ProjectDocumentWorkflowService.createProjectDocument` 统一入口，删除 `ProjectDocumentUploadWorkflowService` 中的重复调用。

## Complexity Tracking

> 无 Constitution 违规，本表为空。
