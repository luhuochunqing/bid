# Implementation Plan: CRM 商机关联补偿与认证解耦

**Branch**: `agent/trae/crm-link-compensation` | **Date**: 2026-07-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/037-crm-link-compensation/spec.md`

## Summary

修复 CRM 推送标讯时 PM 未登录导致关联永久失败的 Bug，根因有三层：
(1) `CrmTenderLinkService.linkByChanceIdIfPresent` 误把 `external_id` 中的标讯 ID（bidId）当成商机 ID（chanceId）去调 detail 接口；
(2) `OrganizationUserSyncWriter` OSS 同步时不填充 `crm_sales_no`，全表为 NULL；
(3) `CrmAuthService.fetchAndCacheUserToken` 强制要求 OSS access_token，但 `generateToken` 接口实测不校验 Authorization。

治本方案：修正 `linkByChanceIdIfPresent` 改用 page-list 按 bidId 反查商机；OSS 同步时用 OSS 工号填充 `crm_sales_no`；`generateToken` 去掉 OSS token 依赖，改用 `CrmHttpClient.postJson`（无 Authorization）。

技术路径：复用已有 `CrmChanceService.findByCode` 的 page-list 调用模式，新增 `findByBidId` 方法；`OrganizationUserSyncWriter.upsert` 加一行 `setCrmSalesNo`；`CrmAuthService.applyCrmTokenWithOssToken` 改名为 `applyCrmToken` 并改用 `postJson`。

## Technical Context

**Language/Version**: Java 21（`backend/pom.xml` 唯一源）

**Primary Dependencies**: Spring Boot 3.2 + JPA + MySQL 8.0 + Flyway；后端纯核心 FP-Java Profile（`record` / `final`）

**Storage**: MySQL 8.0（`xiyu_bid_main`），涉及表 `tenders`（`external_id` / `crm_opportunity_id` / `crm_opportunity_name`）、`users`（`username` / `employee_number` / `crm_sales_no` / `full_name`）；Redis（`oss:token:{username}` / `crm:token:{username}` 缓存）

**Testing**: JUnit 5 + Mockito（`@ExtendWith(MockitoExtension.class)`）+ MockMvc + Testcontainers；现有测试 `CrmTenderLinkServiceTest`（397 行，覆盖 linkIfPresent + linkByChanceIdIfPresent）、`CrmAuthServiceTest`（321 行，覆盖三步认证流程）

**Target Platform**: Linux server（生产 172.16.10.149）+ macOS 本地开发（主工作区 trae 1323/18089）

**Project Type**: web-service（Spring Boot REST API）

**Performance Goals**: CRM 推送的新标讯 5 秒内完成商机关联（SC-001）；补偿任务 100 条标讯 ≤30 秒（SC-006）

**Constraints**: 关联失败不阻塞主流程，响应时间增加 ≤200ms（SC-005）；CRM 接口 401 自动重试一次（FR-009）；单文件 ≤300 行（Constitution IV）

**Scale/Scope**: 改动 3 个生产文件 + 2 个测试文件 + 0 个数据库迁移（无 schema 变更，仅填充已有列 `crm_sales_no`）；影响所有 CRM 推送路径的标讯（当前 3 条历史未关联 + 未来所有新推送）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|---|---|---|
| **I. FP-Java Architecture** | ✅ PASS | 修改均在 Service 层（`CrmTenderLinkService` / `CrmAuthService` / `OrganizationUserSyncWriter`），不涉及 Pure Core → Shell 边界；`ProjectLeaderResult` 已是 `record`；无新增可变状态 |
| **II. Real-API Only** | ✅ PASS | 调研基于生产环境实测（generateToken 不校验 Authorization 已在测试 + 生产双环境验证）；不引入 Mock |
| **III. Test-Driven Development** | ✅ PASS | 现有 `CrmTenderLinkServiceTest`（18 个 case）+ `CrmAuthServiceTest`（11 个 case）作为回归基线；新增 bidId 反查 + 无 OSS token 换 JWT 两个场景的 Red 测试，再改实现 |
| **IV. Split-First & Simplicity** | ✅ PASS | 三个文件均 <220 行（CrmTenderLinkService 208 行、CrmAuthService 125 行、OrganizationUserSyncWriter 216 行），修改后不超 300 行硬上限；不新增类，YAGNI |
| **V. OSS Integration** | ✅ PASS | OSS 同步路径保持真实 API；`crm_sales_no` 填充用 OSS 工号（已验证 salesNo = OSS 工号）；不修改 OSS 接口调用，仅补充下游字段填充 |
| **VI. Authorization Unification** | ✅ PASS | 不涉及 Controller `@PreAuthorize`，不新增端点；修改均在 Service 层 |
| **VII. Defensive Collection & Graceful Degradation** | ✅ PASS | 关联失败 MUST 降级为 `crm_opportunity_id = NULL` + `log.warn`，不抛异常（spec FR-008/FR-010）；无新增 `Collectors.toMap`；CRM 接口异常 catch + 降级已有先例（CrmTenderLinkService L145-149） |
| **VIII. Boring Proven Patterns** | ✅ PASS | 复用已有 `CrmChanceService.findByCode` 的 page-list 调用模式；复用已有 `CrmHttpClient.postJson`（L150，无 Authorization）；不引入新依赖 |

**Gate Result**: PASS — 无违反项，无需 Complexity Tracking 记录。

## Project Structure

### Documentation (this feature)

```text
specs/037-crm-link-compensation/
├── spec.md              # 需求规格（已完成）
├── checklists/
│   └── requirements.md  # Spec 质量校验清单（已完成）
├── plan.md              # 本文件（实现计划）
├── research.md          # Phase 0：技术调研（CrmChanceService / CrmHttpClient / external_id 解析）
├── data-model.md        # Phase 1：数据模型（tenders / users / CRM 商机）
├── quickstart.md        # Phase 1：快速验证脚本
├── contracts/
│   └── crm-interfaces.md # Phase 1：CRM 接口契约（page-list / generateToken）
└── tasks.md             # Phase 2：任务清单（待 /speckit-tasks 生成）
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/xiyu/bid/
│   ├── crm/
│   │   ├── application/
│   │   │   ├── CrmAuthService.java          # 修改：applyCrmToken 去掉 OSS token 依赖
│   │   │   ├── CrmChanceService.java        # 新增 findByBidId 方法（page-list 按_bidId 查）
│   │   │   ├── CrmProjectLeaderService.java # 新增 findProjectLeaderByBidId 方法
│   │   │   ├── CrmChanceDetailService.java  # 不修改（保留 detail 接口供其他场景用）
│   │   │   └── ...
│   │   ├── infrastructure/
│   │   │   └── CrmHttpClient.java           # 不修改（复用已有 postJson L150）
│   │   └── config/
│   │       └── CrmProperties.java           # 不修改（已有 chance.pageListPath 等配置）
│   ├── integration/
│   │   ├── external/
│   │   │   ├── CrmTenderLinkService.java    # 修改：linkByChanceIdIfPresent 改用 bidId 反查
│   │   │   ├── TenderIntegrationCommandSupport.java  # 不修改（applyCrmFallback 调用方）
│   │   │   ├── TenderIntegrationCommandService.java  # 不修改（3 个 caller 保持不变）
│   │   │   ├── ExternalIdParser.java        # 不修改
│   │   │   └── ExternalSystemPrefix.java    # 不修改
│   │   └── organization/
│   │       └── application/
│   │           └── OrganizationUserSyncWriter.java  # 修改：upsert 时填充 crm_sales_no
│   └── entity/
│       ├── Tender.java                      # 不修改（已有 crm_opportunity_id 列）
│       └── User.java                        # 不修改（已有 crm_sales_no 列）
└── src/test/java/com/xiyu/bid/
    ├── crm/application/
    │   ├── CrmAuthServiceTest.java          # 修改：新增"无 OSS token 也能换 JWT" case
    │   └── CrmChanceServiceTest.java        # 新增：findByBidId 场景（如不存在则创建）
    └── integration/external/
        ├── CrmTenderLinkServiceTest.java    # 修改：新增"sourceId 是 bidId 不是 chanceId" case
        └── OrganizationUserSyncWriterTest.java  # 新增：crm_sales_no 填充验证（如不存在则创建）
```

**Structure Decision**: 修改 3 个生产文件 + 1 个生产辅助文件（`CrmChanceService` 新增 `findByBidId`）+ 1 个生产辅助文件（`CrmProjectLeaderService` 新增 `findProjectLeaderByBidId`）；测试侧修改 2 个现有测试 + 新增最多 2 个测试文件。不新增数据库迁移（`crm_sales_no` 列已存在，仅填充数据）。不新增包，所有修改在现有包结构内。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违反项，本表留空。
