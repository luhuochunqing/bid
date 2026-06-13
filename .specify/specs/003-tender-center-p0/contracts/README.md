# API Contracts: 标讯中心 P0

## POST /api/tenders/{id}/transfer

Transfer a tender to a new project owner.

**Request** (bid_admin or bid_lead only):
```json
{
  "newOwnerId": "user_abc123"
}
```

**Response 200**:
```json
{
  "tenderId": "123",
  "oldOwnerId": "user_xyz789",
  "newOwnerId": "user_abc123",
  "department": "投标一部",
  "status": "TRACKING"
}
```

**Errors**:
- 403: Not authorized (non-admin/lead)
- 400: Cannot transfer to current owner
- 400: Tender not in TRACKING or EVALUATED status
- 404: New owner not found or inactive

## POST /api/tenders/{id}/evaluation/review

Approve a re-edited evaluation (requires_review=true → false).

**Request** (bid_admin or bid_lead only):
```json
{
  "action": "APPROVE"
}
```

**Response 200**:
```json
{
  "evaluationId": "456",
  "requiresReview": false,
  "status": "EVALUATED"
}
```

**Errors**:
- 403: Not authorized
- 400: Evaluation already approved (requires_review already false)

## GET /api/tender-sources/config

Get current source configuration. Returns the only config row (id=1).

**Response 200**:
```json
{
  "platforms": ["中国政府采购网", "第三方商机服务"],
  "apiEndpoint": null,
  "keywords": "电商,采购",
  "regions": ["全国"],
  "budgetMin": 0,
  "budgetMax": 1000,
  "autoSync": false,
  "autoDedupe": true
}
```

**Errors**:
- 403: Non-admin role

## PUT /api/tender-sources/config

Save source configuration.

**Request** (bid_admin only):
```json
{
  "platforms": ["中国政府采购网"],
  "apiEndpoint": "https://api.example.com",
  "apiKey": "secret123",
  "keywords": "电商",
  "regions": ["全国"],
  "budgetMin": 0,
  "budgetMax": 500,
  "autoSync": true,
  "syncIntervalMinutes": 1440,
  "autoDedupe": true
}
```

**Response 200**: Updated config (apiKey omitted)

## POST /api/tender-sources/test-connection

Existing endpoint, unchanged.
