# API Contracts: 品牌授权 — 原厂授权

Base path: `/api/knowledge/brand-auth`

## GET /api/knowledge/brand-auth

List manufacturer authorizations with filters.

**Query params**: productLine (multi), brandId (fuzzy), brandName (multi), importDomestic (single), manufacturerName (fuzzy), authStartFrom/authStartTo (date), authEndFrom/authEndTo (date), status (multi, default excludes REVOKED), createdBy, keyword, page (default 0), size (default 20)

**Response 200**:
```json
{
  "code": 200,
  "data": {
    "content": [{
      "id": 1,
      "productLine": "工具",
      "brandId": "BR001",
      "brandName": "3M",
      "importDomestic": "进口",
      "manufacturerName": "3M中国有限公司",
      "authStartDate": "2025-01-01",
      "authEndDate": "2026-12-31",
      "status": "ACTIVE",
      "remarks": null,
      "attachments": [
        { "id": 1, "attachmentType": "AUTH_DOC", "fileName": "授权书.pdf", "fileUrl": "/files/...", "fileSize": 2048000 },
        { "id": 2, "attachmentType": "SUPPLEMENTARY", "fileName": "补充协议.pdf", "fileUrl": "/files/...", "fileSize": 1024000 }
      ],
      "createdBy": 1,
      "createdAt": "2025-01-01T10:00:00",
      "updatedAt": "2025-06-01T14:00:00"
    }],
    "totalElements": 28,
    "totalPages": 2,
    "number": 0,
    "size": 20
  }
}
```

## GET /api/knowledge/brand-auth/{id}

Detail view.

**Response 200**: Single `ManufacturerAuthorizationDTO` with full attachment list + last 5 audit logs.

## POST /api/knowledge/brand-auth

Create new manufacturer authorization.

**Body**:
```json
{
  "productLine": "工具",
  "brandId": "BR001",
  "brandName": "3M",
  "importDomestic": "进口",
  "manufacturerName": "3M中国有限公司",
  "authStartDate": "2025-01-01",
  "authEndDate": "2026-12-31",
  "remarks": null
}
```

**Response 201**: Created `ManufacturerAuthorizationDTO` with id and ACTIVE status.

**Validation**:
- All 9 fields except `remarks` required → 400 if missing
- `authEndDate` ≤ `authStartDate` → 400 "结束时间须晚于开始时间"
- Duplicate (brandId + manufacturerName + productLine) on ACTIVE/EXPIRING_SOON → 409 with warning message + existing record id

## POST /api/knowledge/brand-auth/attachments/upload

Upload attachment files.

**Request**: `multipart/form-data`
- `files`: List<MultipartFile> (PDF/JPG/PNG, max 20MB each)
- `attachmentType`: AUTH_DOC or SUPPLEMENTARY

**Response 200**:
```json
{
  "code": 200,
  "data": [
    { "id": 1, "fileName": "授权书.pdf", "fileUrl": "/files/...", "fileSize": 2048000 }
  ]
}
```

## PUT /api/knowledge/brand-auth/{id}

Update authorization. Field availability gated by status.

**Response 200**: Updated DTO.

## POST /api/knowledge/brand-auth/{id}/revoke

Soft-delete (作废). Requires `knowledge-brand-auth.revoke` permission.

**Body**:
```json
{ "reason": "品牌授权已过期，不再续签" }
```

**Validation**: `reason` required, min 10 characters.

**Response 200**: Updated DTO with status=REVOKED.

## GET /api/knowledge/brand-auth/{id}/logs

Operation log for a specific authorization.

**Response 200**:
```json
{
  "code": 200,
  "data": [
    { "id": 101, "action": "UPDATE", "username": "张三", "description": "修改品牌原厂名称", "oldValue": "{...}", "newValue": "{...}", "timestamp": "2025-06-01T14:00:00" }
  ]
}
```

## DELETE (removed)

Hard delete endpoint removed. Use POST /{id}/revoke instead.
