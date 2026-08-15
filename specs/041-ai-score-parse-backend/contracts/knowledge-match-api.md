# Contract: 知识库五类匹配 API

**Base**: `/api/knowledge` | **鉴权**: `@PreAuthorize("isAuthenticated()")` | **方法**: 全部 POST（匹配条件为结构化对象，非简单查询参数）

通用响应骨架：
```json
{
  "tier": "FULL | PARTIAL | NONE",
  "matchRatio": 80,
  "matched": [ { "id": 1, "...": "命中记录摘要字段" } ],
  "matchDetail": "命中说明（含降级/过期标注）"
}
```
- `matchRatio`：0-100 整数（命中证据数/要求证据数，或符合人数/要求人数，或实际数量/要求数量）
- `tier=FULL` 且全部命中证据在有效期内 → ratio=100；`NONE` → ratio=0

## 1. POST /cert/match（资质证书）

请求：
```json
{
  "certNameKeywords": ["ISO9001"],
  "requiredLevel": "三级",
  "requireValidUntil": "2026-12-31",
  "requiredCount": 1
}
```
匹配规则（FR-009）：`name` 包含任一关键词 AND（requiredLevel 为空 OR `level` 包含）AND（requireValidUntil 为空 OR `expiry_date >= 该日期` AND `status != RETIRED`）。

响应特有：命中记录含 `expired: true` 标记（`status=EXPIRED` 或 `expiry_date < 今天`，算命中但标记，FR-009/5.3）。

存储：`business_qualifications`（复用 `BusinessQualificationJpaRepository` + Specification 模糊查询）。

## 2. POST /person/match（人员）

请求：
```json
{
  "positionKeywords": ["项目经理"],
  "certNameKeywords": ["PMP"],
  "requiredCount": 5
}
```
匹配规则（FR-010）：人员 `status=ACTIVE` AND `technical_title` 命中岗位关键词 AND 证书子表存在未删除（`deleted_at IS NULL`）且名称命中、有效期内记录。单人员可持多证只计一次。

响应：`matched` 为人员摘要（name/employeeNumber/technicalTitle/命中证书名）；`matchRatio = 符合人数 / requiredCount`（上限 100）。

存储：`personnel` JOIN `personnel_certificate`（复用 `findByCriteriaFull` 模式或新 Specification）。

## 3. POST /project/match（项目业绩）

请求：
```json
{
  "projectTypeKeywords": ["信息化"],
  "signedAfter": "2023-01-01",
  "minContractAmount": 1000000,
  "requiredCount": 3
}
```
匹配规则（FR-010）：`project_type`/`industry` 命中 AND（signedAfter 为空 OR `signing_date >=`）AND（minContractAmount 为空 OR `contract_amount >=`，**contract_amount 为 NULL 的存量行跳过金额比对不因此失配**——research R7）。

响应：`matched` 为业绩摘要（contractName/projectType/signingDate/contractAmount）；`matchRatio = min(实际数量/requiredCount, 100)`。

存储：`performance_record`（V1188 后含 contract_amount）。

## 4. POST /warehouse/match（仓库）

请求：
```json
{
  "nameKeywords": ["华东"],
  "region": "华东",
  "minArea": 5000,
  "facilityKeywords": ["冷链"]
}
```
匹配规则：`status IN (IN_USE, EXPIRING)` AND 名称/区域/面积条件 AND（facilityKeywords 为空 OR `remarks` 文本包含——**设施降级匹配**，命中时 matchDetail 注明"基于备注文本匹配"）。

响应：同通用骨架；`tier` 判定：硬条件全中=FULL、仅降级字段部分命中=PARTIAL。

存储：`warehouse`（Specification 动态查询）。

## 5. POST /brand/match（品牌授权）

请求：
```json
{
  "brandNameKeywords": ["德力西"],
  "productLine": "TOOLS",
  "importDomestic": "国产",
  "requireValidUntil": "2026-12-31"
}
```
匹配规则：`status IN (ACTIVE, EXPIRING_SOON)` AND `brand_name` 命中 AND（productLine 为空 OR `product_line =`）AND（importDomestic 为空 OR `import_domestic =`）AND `auth_end_date >= requireValidUntil`。**授权范围降级**：productLine(38 枚举)+importDomestic 近似表达（research R7），matchDetail 注明。

响应：同通用骨架；命中含 `expireSoon: true` 标记（`auth_end_date` 90 天内）。

存储：`manufacturer_authorization`（新表；禁用 `brand_authorization_deprecated`）。

## 消费方约束

- match 接口为无状态确定性查询（不含 LLM 调用），单评分项五类匹配合计 ≤ 5 秒（SC-005）
- 调用方：`scoreparse` 阶段 1 预计得分服务（内部 Service 调用为主，REST 形态为 PRD §4.2 契约对齐与未来开放预留）
- 全部接口空结果返回 `tier=NONE, matchRatio=0, matched=[]`，不抛错（FR-024）
