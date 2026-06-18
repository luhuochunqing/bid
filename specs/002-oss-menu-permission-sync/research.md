# Research: OSS Menu Permission Sync

## Decision: Use jobNumber as user identifier for the OSS menu tree call

**Rationale**: The existing integration already treats `jobNumber` as the primary user identifier (`OssUserJobAndRoleDto.jobNumber`). The menu tree interface documentation does not show a user query parameter, implying the OSS user is derived from the request's authentication context. We will pass the jobNumber through the lookup context and/or auth headers so the gateway can identify the representative user consistently with the rest of the OSS integration.

**Alternatives considered**:
- Use OSS `userId`: would require an extra lookup from jobNumber to userId; not needed if the auth context can carry jobNumber.
- Use username: same as jobNumber in this system.

## Decision: Extend `OrganizationDirectoryRestClient` with GET support rather than create a new client

**Rationale**: The existing client already owns auth headers, URL building, response parsing, and exception mapping. Adding a GET method keeps HTTP execution centralized and avoids duplication.

**Alternatives considered**:
- New `OrganizationDirectoryMenuTreeClient`: rejected because it would duplicate header/URL/response logic.

## Decision: Mapping behavior default = `IGNORE` unmapped menu codes

**Rationale**: OSS menu codes and internal permission keys may not be 1:1. Ignoring unmapped codes prevents polluting the role with invalid or unexpected permissions. Operators can explicitly configure mappings to include codes.

**Alternatives considered**:
- `USE_NORMALIZED_CODE` as default: rejected because it could create spurious permissions when OSS uses display names or external codes.

## Decision: Auto-sync default = `false`

**Rationale**: The menu tree endpoint is per-user and could generate many calls during a full org sync. Default off lets operators enable it after validating mapping rules.

## Decision: Manual sync endpoint under `/api/admin/roles/{id}/sync-oss-menu-permissions`

**Rationale**: Reuses existing ADMIN authorization and role management domain. Keeps the API intuitive and aligned with current role CRUD endpoints.
