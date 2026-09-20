# Multimodal Score Facts Contract

单次模型响应：

```json
{
  "declaredTotal": 100,
  "facts": [{
    "sectionTitle": "商务部分",
    "code": "2.1",
    "parentCode": "2",
    "dimension": "响应报价",
    "weight": 30,
    "ruleText": "原文完整规则",
    "scoreType": "OBJECTIVE",
    "pageNumber": 18,
    "tableId": "table-7",
    "sourceCellIds": ["r3c1", "r3c2"],
    "quotedText": "原始单元格中的逐字文本",
    "confidence": 0.98
  }]
}
```

模型不得输出摘要替代 `quotedText`。验证错误响应增量返回 `validationViolations`，但保持现有 items/review/status 字段兼容。

