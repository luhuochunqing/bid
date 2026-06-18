# CRM 集成经验

> 记录与 CRM 系统对接过程中遇到的字段映射、接口契约和踩坑经验。

---

## 1. 客户信息字段名：接口文档与前端矩阵必须对齐

**来源**: CO-266 / CO-267 排查，2026-06-18

### 问题

CRM 按接口文档 `docs/integration-tender-api-v3.1.md` 推送客户信息时使用字段名：
- `CONTACT`
- `EVALUATION_BASIS`

前端客户信息矩阵 `src/views/Bidding/detail/components/customerInfoMatrixConfig.js` 使用的字段名：
- `CONTACT_INFO`
- `INFO_TENDENCY_BASIS`

后端 `TenderIntegrationService.saveEvaluationInternal()` 原样把 CRM 字段名存入 `tender_evaluation_customer_info.info_key`，导致前端按标准字段名读取时匹配不到值，单元格显示为空。

### 教训

1. **接口文档字段名必须与前端矩阵字段名保持一致**。如果外部系统已经按旧字段名对接，后端应提供兼容映射，而不是让前端或外部系统分别适配。
2. **后端是收敛字段名的最佳位置**。在 `TenderIntegrationService` 入口层统一标准化，可以同时覆盖 push / update 两个路径，避免前后端重复映射。
3. **新接入外部系统前，先用真实字段名跑端到端测试**。现有测试使用 `attitude` / `position` 等占位字段，掩盖了真实字段名不一致的问题。

### 修复参考

- `backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationService.java`
- `backend/src/main/java/com/xiyu/bid/tender/core/TenderEvaluationCustomerInfoPolicy.java`
- `docs/integration-tender-api-v3.1.md`
- `docs/lessons/root-cause-analysis-co-266-co-267.md`

---

## 2. 评估表 `evaluation` 字段不是简单透传，需要入口层处理

**来源**: CO-267 历史修复 `!805`，2026-06-18

### 问题

早期 `TenderPushRequest` 缺少 `evaluation` 字段，CRM 推送时评估表客户信息完全丢失。`!805` 在 DTO 中补了 `evaluation` 字段，并在 `pushTender()` 创建路径调用 `saveEvaluation()`。

但补完字段后，如果字段名不统一，数据仍然无法正确展示。

### 教训

- 增加字段只是第一步，字段名、数据格式、存储模型之间的映射必须同步验证。
- 对 `List<Map<String, Object>>` 这类弱类型字段要特别小心：后端不能假设外部系统传的 key 与内部模型一致。
