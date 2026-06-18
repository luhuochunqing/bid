# Data Model: OSS Menu Permission Sync

## OSS Menu Tree Node (external)

Represents one node from `GET /sysMenuUrl/getUserMenuTree` response.

| Attribute | Type | Description |
|---|---|---|
| id | Long | Node id |
| menuCode | String | Menu code (primary mapping key) |
| menuName | String | Display name |
| menuType | String | `M` directory, `C` menu, `F` button |
| parentId | Long | Parent node id |
| orderNum | Integer | Display order |
| reqUrl | String | Menu API address |
| iconUrl | String | Icon URL |
| systemUrl | String | System address |
| isSso | Integer | SSO flag |
| visible | Boolean | Visible flag |
| children | List<OssMenuTreeNode> | Nested children |
| component | String | Component path |
| path | String | Route path |
| menuAliasName | String | Menu alias |
| serviceId | Long | Service id |
| systemId | Long | System id |
| serviceMenuId | Long | Service menu id |
| serviceName | String | Service name |
| serviceMenuParentId | String | Service parent id |
| serviceMenuParentName | String | Service parent name |
| sysMenuCode | String | System menu code |

## Internal RoleProfile (existing)

| Attribute | Type | Description |
|---|---|---|
| id | Long | Internal role id |
| code | String | Unique role code |
| name | String | Role name |
| menuPermissionsValue | String | Comma-separated menu permission keys |

## Mapping Rule

| Attribute | Type | Description |
|---|---|---|
| ossMenuCodeNormalized | String | Lowercase/trimmed OSS menu code |
| internalPermissionKey | String | Platform permission key |

Configuration stored in `OrganizationIntegrationProperties.Directory.menuCodeToPermissionKeyMappings`.
