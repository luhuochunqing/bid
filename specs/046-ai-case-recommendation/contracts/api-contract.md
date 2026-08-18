# API Contracts: AI 智能推荐案例（Feature 046）

## 1. 评分项列表查询

- **HTTP 接口**: `GET /api/projects/{projectId}/score-parse/items`
- **权限控制**: `isAuthenticated()`, 需校验当前用户对 `projectId` 的数据权限
- **响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 101,
      "code": "A1",
      "dim": "技术方案",
      "detail": "系统架构高可用设计，支持双活容灾与动态扩容能力...",
      "itemIndex": 1,
      "targetChapter": "第3章 技术方案"
    }
  ]
}
```

---

## 2. 案例推荐检索

- **HTTP 接口**: `POST /api/projects/{projectId}/cases/recommend`
- **权限控制**: `isAuthenticated()`, 需校验当前用户对 `projectId` 的访问权限
- **请求体**:
```json
{
  "scoreItemId": 101,
  "keyword": "双活容灾",
  "page": 0,
  "size": 10
}
```
- **响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 5,
    "items": [
      {
        "id": 8802,
        "scoringPointTitle": "高可用双活架构设计",
        "scoringCategory": "技术方案",
        "matchScore": 95,
        "matchLabel": "优质",
        "matchReasons": ["评分项条款高度重合", "中标案例"],
        "matchedKeywords": ["双活容灾", "动态扩容", "高可用"],
        "requirementRaw": "投标人须提供微服务架构双活部署方案...",
        "responseText": "本方案采用异地双活架构...",
        "sourceProjectId": 521,
        "sourceProjectName": "2025省级电网数智化升级项目",
        "bidResult": "WON",
        "reuseCount": 12
      }
    ]
  }
}
```

---

## 3. 案例详情查询

- **HTTP 接口**: `GET /api/cases/{id}`
- **权限控制**: `isAuthenticated()`
- **响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 8802,
    "sourceProjectId": 521,
    "sourceProjectName": "2025省级电网数智化升级项目",
    "scoringPointTitle": "高可用双活架构设计",
    "scoringCategory": "技术方案",
    "requirementRaw": "投标人须提供微服务架构双活部署方案...",
    "responseText": "本方案采用异地双活架构...",
    "reuseCount": 12,
    "status": "ACTIVE",
    "customerType": "STATE_OWNED",
    "projectType": "IT_SERVICE",
    "bidResult": "WON"
  }
}
```

---

## 4. 案例复用记录与原子自增

- **HTTP 接口**: `POST /api/cases/{id}/reuse`
- **权限控制**: `isAuthenticated()`, 需校验当前用户对 `projectId` 的访问权限
- **请求体**:
```json
{
  "projectId": 302,
  "scoreItemId": 101,
  "targetChapter": "第3章 技术方案/3.2 架构设计",
  "reuseMode": "APPEND"
}
```
- **响应示例**:
```json
{
  "code": 200,
  "message": "Case reused successfully",
  "data": {
    "caseId": 8802,
    "newReuseCount": 13,
    "reusedAt": "2026-08-18T12:00:00Z"
  }
}
```
