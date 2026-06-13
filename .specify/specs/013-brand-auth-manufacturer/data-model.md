# Data Model: 品牌授权 §4.6a — 原厂授权

## Entity: ManufacturerAuthorization

New table `manufacturer_authorization` replacing old `brand_authorization`.

| # | Field | SQL Type | Java Type | Required | Notes |
|---|-------|----------|-----------|:--:|-------|
| 1 | id | BIGINT AUTO_INCREMENT | Long | PK | |
| 2 | product_line | VARCHAR(50) | ProductLine enum | ✓ | 39-item enum, NOT NULL |
| 3 | brand_id | VARCHAR(100) | String | ✓ | Internal brand code |
| 4 | brand_name | VARCHAR(200) | String | ✓ | Brand display name |
| 5 | import_domestic | VARCHAR(10) | String | ✓ | '进口' or '国产' |
| 6 | manufacturer_name | VARCHAR(200) | String | ✓ | Legal entity name |
| 7 | auth_start_date | DATE | LocalDate | ✓ | Authorization valid from |
| 8 | auth_end_date | DATE | LocalDate | ✓ | Must be > start_date |
| 9 | remarks | VARCHAR(1000) | String | — | Only optional field |
| 10 | status | VARCHAR(20) | AuthStatus enum | system | DRAFT/ACTIVE/EXPIRING_SOON/EXPIRED/REVOKED |
| 11 | revoke_reason | VARCHAR(500) | String | conditional | Required when status=REVOKED |
| 12 | created_by | BIGINT | Long | system | User ID who created |
| 13 | created_at | DATETIME | LocalDateTime | system | |
| 14 | updated_at | DATETIME | LocalDateTime | system | ON UPDATE CURRENT_TIMESTAMP |
| 15 | version | INT | Integer | system | Optimistic locking |

**Indexes**:
- `idx_ma_brand_id` (brand_id) — for brand dictionary lookup
- `idx_ma_product_line` (product_line) — for filtering
- `idx_ma_status` (status) — for status filtering
- `idx_ma_end_date` (auth_end_date) — for expiry scanning
- `UNIQUE idx_ma_dedup` (brand_id, manufacturer_name, product_line, status) — duplicate detection for ACTIVE/EXPIRING_SOON

## Entity: BrandAuthAttachment

New table `brand_auth_attachment` for file storage.

| # | Field | SQL Type | Java Type | Notes |
|---|-------|----------|-----------|-------|
| 1 | id | BIGINT AUTO_INCREMENT | Long | PK |
| 2 | authorization_id | BIGINT | Long | FK → manufacturer_authorization.id |
| 3 | attachment_type | VARCHAR(20) | AttachmentType enum | AUTH_DOC / SUPPLEMENTARY |
| 4 | file_name | VARCHAR(255) | String | Original filename |
| 5 | file_url | VARCHAR(500) | String | Storage path/URL |
| 6 | file_size | BIGINT | Long | Bytes |
| 7 | file_type | VARCHAR(100) | String | MIME type |
| 8 | created_at | DATETIME | LocalDateTime | |

**Index**: `idx_baa_auth_id` (authorization_id)

## Enum: ProductLine

```java
public enum ProductLine {
    TOOLS("工具"), TOOL_CONSUMABLES("工具耗材"), CUTTING_TOOLS("刀具"),
    MEASURING_TOOLS("量具"), WELDING("焊接"), MACHINE_TOOLS("机床"),
    ABRASIVES("磨具"), LUBRICATION("润滑"), ADHESIVES("胶粘"),
    WORKSHOP_CHEMICALS("车间化学品"), LABOR_PROTECTION("劳保"),
    SAFETY("安全"), FIRE_PROTECTION("消防"), HANDLING("搬运"),
    STORAGE("存储"), WORKSTATION("工位"), PACKAGING("包材"),
    CLEANING("清洁"), OFFICE("办公"), REFRIGERATION("制冷"),
    HVAC("暖通"), INDUSTRIAL_CONTROL("工控"), LOW_VOLTAGE("低压"),
    ELECTRICAL("电工"), LIGHTING("照明"), BEARINGS("轴承"),
    BELTS("皮带"), MACHINERY("机械"), PNEUMATIC("气动"),
    HYDRAULIC("液压"), PIPE_VALVES("管阀"), PUMPS("泵"),
    FASTENERS("紧固"), SEALS("密封"), INDUSTRIAL_TESTING("工业检测"),
    LAB_PRODUCTS("实验室产品"), CORPORATE_WELFARE("企业福礼"),
    EMERGENCY_RESCUE("紧急救护"), CONSTRUCTION_MATERIALS("建工材料");
}
```

## Enum: AuthStatus

```java
public enum AuthStatus {
    DRAFT("草稿"),
    ACTIVE("生效中"),
    EXPIRING_SOON("即将到期"),  // end_date within 90 days
    EXPIRED("已失效"),           // end_date < today
    REVOKED("已作废");           // manually revoked
}
```

**State transitions**:
```
DRAFT → ACTIVE (on save)
ACTIVE → EXPIRING_SOON (auto, when end_date ≤ 90 days away)
EXPIRING_SOON → ACTIVE (auto, when end_date > 90 days after renewal)
ACTIVE/EXPIRING_SOON → EXPIRED (auto, when end_date < today)
ACTIVE/EXPIRING_SOON/EXPIRED → REVOKED (manual, requires reason)
REVOKED is terminal — cannot transition to any other state
```

## Relations

```
ManufacturerAuthorization 1 ──── * BrandAuthAttachment
ManufacturerAuthorization * ──── * AuditLog (via entity_type='BRAND_AUTH')
```

## Migration Strategy

- **V147**: `ALTER TABLE brand_authorization RENAME TO brand_authorization_deprecated` — preserves old data
- **V147**: `CREATE TABLE manufacturer_authorization` — new schema
- **V147**: `CREATE TABLE brand_auth_attachment` — file storage
- **V147**: Insert `knowledge-brand-auth.*` permissions into RoleProfileCatalog seed data
- **U147**: Reverse all above operations
