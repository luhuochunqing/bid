# Feature Specification: 标讯评估表字段带入项目立项

**Feature Branch**: `018-evaluation-to-initiation`

**Created**: 2026-06-07

**Status**: Draft

**Input**: Issue 6: 标讯转项目后，评估表字段未带入项目立项表单

## User Scenarios & Testing

### User Story 1 - 标讯转项目时评估表字段自动带入 (Priority: P1)

投标管理员在标讯中心点击"立即投标"并"转为项目"后，标讯"项目评估表"中已填写的字段值自动带入"项目立项"表单，无需重复填写。

**Why this priority**: 这是核心 Bug 修复，直接影响用户操作效率和体验，属于阻塞性问题。

**Independent Test**: 可在标讯评估表中填写完整评估数据后，执行 proceedToBid，验证 ProjectInitiationDetails 中的对应字段与评估表一致。

**Acceptance Scenarios**:

1. **Given** 标讯已有已提交的评估表（含基础信息段 9 字段），**When** 用户点击"转为项目"，**Then** 项目立项的投标信息段 9 个字段自动填充
2. **Given** 标讯评估表客户信息矩阵已填写，**When** 转为项目，**Then** 项目立项客户信息矩阵自动带入
3. **Given** 标讯评估表不存在或为空白草稿，**When** 转为项目，**Then** 项目立项保持默认空值（不报错）
4. **Given** 标讯已转为项目且再次操作，**When** 幂等检查，**Then** 不重复创建 ProjectInitiationDetails

### Edge Cases

- 评估表不存在时，proceedToBid 不报错，仅跳过评估数据带入
- 评估表部分字段为空时，只带入有值的字段
- 项目立项已存在时，评估数据应写入新创建的 ProjectInitiationDetails
- 标讯评估表基础信息 customerRevenue 字段映射到 ProjectInitiationDetails 的 annualRevenue（原字段）

## Requirements

### Functional Requirements

- **FR-001**: 后端 `TenderCommandService.proceedToBid()` 在创建 Project 后，必须同步从 `TenderEvaluation` 读取评估数据并创建/写入 `ProjectInitiationDetails`
- **FR-002**: 字段映射必须严格按照 Issue #6 映射表执行
- **FR-003**: 客户信息矩阵（EAV 行）必须序列化为 JSON 写入 `ProjectInitiationDetails.customerInfoJson`
- **FR-004**: 前端 `autoFillFromTender()` 在加载标讯详情后，必须额外调用评估接口获取评估数据并填充表单
- **FR-005**: 评估表不存在或为空白时，不阻塞 proceedToBid 流程

### Key Entities

- **TenderEvaluation**: 标讯评估实体，含 basic（TenderEvaluationBasic）、customerInfos（List<TenderEvaluationCustomerInfo>）
- **TenderEvaluationBasic**: 基础信息段 9 个评估字段
- **TenderEvaluationCustomerInfo**: 客户信息矩阵 EAV 行（角色 × 维度）
- **ProjectInitiationDetails**: 项目立项详情实体
- **Project**: 项目实体，proceedToBid 创建

## Success Criteria

### Measurable Outcomes

- **SC-001**: proceedToBid 后 ProjectInitiationDetails 的 9 个映射字段与评估表一致
- **SC-002**: 客户信息矩阵（EAV 行）正确序列化为 JSON 存储
- **SC-003**: 前端立项表单打开时自动显示评估表已填字段值
- **SC-004**: 无评估数据时不阻塞正常立项流程

## Assumptions

- 字段映射关系已由 Issue #6 确认，与前端映射表一致
- 客户信息矩阵为 EAV 结构，序列化为 JSON 后前端可解析
- customerRevenue（评估表）→ annualRevenue（项目立项）使用已存在的 @Deprecated 字段
- 评估表状态为 DRAFT 或 SUBMITTED 均可带入（只要字段有值）
