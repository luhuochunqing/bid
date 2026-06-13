# Research: Frontend Permission Patterns

**Feature**: Frontend Permission Migration
**Date**: 2026-05-17

## Decision: Dual-Check Guard with Permission Precedence

**Decision**: Route guards and sidebar filtering will use an OR logic:
- First check `meta.roles` (Legacy, for backward compatibility during transition)
- If no match, check `meta.permissionKeys` against `userStore.menuPermissions`
- This unblocks new roles immediately without breaking existing users

**Rationale**:
- `menuPermissions` infrastructure is already fully built (backend returns it, store exposes `hasPermission()`)
- `sidebar-menu.js` already has `permissionKeys` configured for most items
- OR logic is the safest migration path: existing `admin`/`manager`/`staff` users continue to work via `meta.roles`, while new roles (`bid_admin`, `bid_lead`, etc.) gain access via `permissionKeys`

**Alternatives considered**:
- **Replace `meta.roles` immediately**: Risk of breaking existing flows if any `permissionKeys` are misconfigured. Rejected for P0 delivery.
- **Map role code to legacy in authNormalizer**: Would make `bid_admin` appear as `"manager"`, but this hides the true role from the UI and creates confusion. Rejected.
- **Backend returns legacy role in separate field**: Requires backend changes. Rejected since backend already solved this with `menuPermissions`.

## Existing Infrastructure Audit

| Component | Status | Notes |
|---|---|---|
| `AuthResponse.menuPermissions` | Ready | Returns `['all']` for admin, specific keys for other roles |
| `userStore.hasPermission(key)` | Ready | Reactive getter, checks `menuPermissions.includes(key)` |
| `sidebar-menu.js meta.permissionKeys` | Mostly Ready | Present on most items; `/analytics/dashboard` and `/settings` need addition |
| `router/index.js meta.roles` | Blocking | Only checks `meta.roles`, no `permissionKeys` fallback |
| `hasMenuAccessForRole()` | Optional Fallback | Reads from cached settings.json; may be out of sync |

## Hardcoded Role Check Inventory (from codebase exploration)

**Critical (P1 - route/sidebar blocking)**:
- `src/router/index.js` - `hasRouteAccess()` only checks `meta.roles`
- `src/components/layout/Sidebar.vue` - `hasRoleAccess()` filters menus

**Page-level (P2 - feature visibility)**:
- `src/views/Bidding/list/helpers.js` - `buildPermissionFlags(role)`
- `src/views/Dashboard/Workbench.vue` - `role === 'staff'`
- `src/views/Project/stages/EvaluationStage.vue` - `role === 'manager'`
- `src/views/System/Settings.vue` - `role === 'admin'` / `['admin','auditor'].includes(role)`
- `src/components/layout/Header.vue` - `isAdmin` checks
- `src/views/Knowledge/Qualification/index.vue` - `role === 'admin'`
- `src/views/Resource/Expense/index.vue` - `role === 'manager'`
- `src/views/Project/ProjectList.vue` - `role === 'admin' || role === 'manager'`

## Permission Key Mapping (Backend → Frontend)

| Backend RoleProfile | menuPermissions (examples) |
|---|---|
| `admin` | `['all']` |
| `manager` | `['dashboard','bidding','project','knowledge','resource','analytics']` |
| `staff` | `['dashboard','bidding','project','knowledge','resource']` |
| `bid_admin` | `['dashboard','bidding','project','knowledge','resource','settings']` + dept-scope |
| `bid_lead` | `['dashboard','bidding','project','knowledge','resource','task.assign']` |
| `bid_specialist` | `['dashboard','bidding','project','knowledge','resource','task']` |
| `sales` | `['dashboard','bidding','project','knowledge','resource']` |
| `admin_staff` | `['dashboard','knowledge.qualification','resource']` |
| `auditor` | `['dashboard','audit']` |
| `task_executor` | `['dashboard','project','task']` |

> Note: Exact `menuPermissions` arrays are defined in `RoleProfileCatalog.java`. Frontend should not hardcode these; it receives them from `AuthResponse`.
