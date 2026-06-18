# CRM 推送标讯客户信息未显示 根因分析

> Issues: CO-266 / CO-267
> 日期: 2026-06-18
> 排查者: kimi

---

## 现场还原

**症状素描**：
- CRM 创建标讯时传入 `evaluationCustomerInfos`，投标系统评估表不显示客户信息。
- 标讯修改接口传入 `evaluationCustomerInfos` 后，投标系统同样不显示客户信息。
- 历史修复 `!805`（补 `evaluation` 字段）、`!809`（修 500）已合并，但客户信息仍不显示。

**边界划定**：
- 标讯基本信息保存正常 ✅
- `/api/integration/tenders/{sourceSystem}/{sourceId}` 详情接口能返回 `evaluation` ✅
- 前端 `/api/tenders/{id}/evaluation` 也能拿到数据 ✅
- **只有"联系方式"和"倾向性评估依据"两列显示为空** ❌

**思维沙箱**：数据能保存、接口能返回，但前端展示为空。怀疑字段名在 CRM → 后端 → 前端链路中发生了"方言"不一致。

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口/参数层

CRM push / update 请求体示例（来自 `docs/integration-tender-api-v3.1.md`）：

```json
{
  "evaluationCustomerInfos": [{
    "roleKey": "PROJECT_HIGHEST_DECISION_MAKER",
    "NAME": "张三",
    "CONTACT": "13800138000",
    "POSITION": "总经理",
    "CONTACT_METHOD": "电话",
    "EVALUATION_BASIS": "长期合作",
    "TENDENCY": "支持"
  }]
}
```

### Layer 2 — 核心逻辑层

`TenderIntegrationService.saveEvaluationInternal()` 直接把 Map 的 key 作为 `infoKey` 入库：

```java
// backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationService.java:440-446
for (Map.Entry<String, Object> entry : roleData.entrySet()) {
    if ("roleKey".equals(entry.getKey()) || entry.getValue() == null) continue;
    TenderEvaluationCustomerInfo row = new TenderEvaluationCustomerInfo();
    row.setEvaluation(evalEntity);
    row.setRoleKey(roleKey);
    row.setInfoKey(entry.getKey());   // ← 原样入库
    row.setCellValue(entry.getValue().toString());
    ...
}
```

### Layer 3 — 数据层

后端 EAV 表 `tender_evaluation_customer_info` 中存的是：
- `infoKey = "CONTACT"`
- `infoKey = "EVALUATION_BASIS"`

前端客户信息矩阵 `src/views/Bidding/detail/components/customerInfoMatrixConfig.js` 读取的是：
- `CONTACT_INFO`
- `INFO_TENDENCY_BASIS`

`useTenderEvaluationForm.eavToFlat()` 按 `row.infoKey` 设置属性，表格按 `CUSTOMER_INFO_COLUMNS.key` 取值。字段名不匹配 → 取值 `undefined` → 单元格为空。

---

## 零号病人定位

**第一行错误**：

```
backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationService.java:445
row.setInfoKey(entry.getKey());
```

**必然性解释**：

```
CRM 传 CONTACT / EVALUATION_BASIS
              ↓
后端原样保存为 infoKey
              ↓
前端按 CONTACT_INFO / INFO_TENDENCY_BASIS 读取
              ↓
匹配失败 → 显示为空
```

---

## 验证与修复

### 修复 diff

```diff
// TenderIntegrationService.java
+ private static String normalizeCustomerInfoKey(String infoKey) {
+     if (infoKey == null) return null;
+     return switch (infoKey) {
+         case "CONTACT" -> "CONTACT_INFO";
+         case "EVALUATION_BASIS" -> "INFO_TENDENCY_BASIS";
+         default -> infoKey;
+     };
+ }

  for (Map.Entry<String, Object> entry : roleData.entrySet()) {
      if ("roleKey".equals(entry.getKey()) || entry.getValue() == null) continue;
+     String infoKey = normalizeCustomerInfoKey(entry.getKey());
      TenderEvaluationCustomerInfo row = new TenderEvaluationCustomerInfo();
      row.setEvaluation(evalEntity);
      row.setRoleKey(roleKey);
-     row.setInfoKey(entry.getKey());
+     row.setInfoKey(infoKey);
      ...
  }
```

```diff
// TenderEvaluationCustomerInfoPolicy.java
  VALID_INFO_KEYS: + "CONTACT_INFO"
  INFO_KEY_VALUE_TYPES: + "CONTACT_INFO" → "TEXT"
  INFO_DISPLAY_NAMES: + "CONTACT_INFO" → "联系方式"
```

```diff
// docs/integration-tender-api-v3.1.md
- CONTACT
+ CONTACT_INFO
- EVALUATION_BASIS
+ INFO_TENDENCY_BASIS
```

### 最小验证

1. 后端测试：
   ```bash
   cd backend
   mvn test -Dtest=TenderIntegrationServicePushEvaluationTest,TenderIntegrationServiceEvaluationTest
   ```
2. 本地真实链路：启动前后端，用 `api-tests/integration.http` 模拟 CRM push，在前端详情页确认"联系方式"和"倾向性评估依据"有值。

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `TenderIntegrationService.java:445` | ✅ |
| 必然性已证明 | 字段名不匹配导致前端匹配失败 | ✅ |
| 修复 diff 已提供 | 见上 | ✅ |
| 防复发测试已设计 | 新增 CRM 旧字段映射测试 | ✅ |
| 文档已同步 | `docs/integration-tender-api-v3.1.md` | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

- `TenderIntegrationServicePushEvaluationTest#pushTender_crmLegacyInfoKeys_shouldBeNormalizedBeforeSave`
- `TenderIntegrationServiceEvaluationTest#saveEvaluation_crmLegacyInfoKeys_shouldBeNormalizedBeforeSave`

---

## 关联技术债

- `docs/exec-plans/tech-debt-tracker.md`：客户信息字段名双轨制（`EVALUATION_BASIS` / `INFO_TENDENCY_BASIS`、`CONTACT` / `CONTACT_INFO`）。当前通过后端兼容映射缓解，建议未来统一收敛为一套标准 key。
