# Data Model: Auth State & Route Meta

**Feature**: Frontend Permission Migration
**Date**: 2026-05-17

## User Auth State (Pinia Store)

```typescript
interface UserAuthState {
  // Existing fields (no changes)
  id: number
  username: string
  fullName: string
  email: string
  role: string           // RoleProfile code: 'admin' | 'bid_admin' | 'bid_lead' | ...
  roleName: string       // Display name: '管理员' | '投标管理员' | ...
  menuPermissions: string[]  // ['dashboard', 'bidding', 'settings', 'all']
  token: string
  // ... other fields
}
```

**Key invariant**: `menuPermissions` is the single source of truth for access control. `role` is retained for display purposes and legacy transition only.

## Route Meta Shape

```typescript
interface RouteMeta {
  title?: string
  icon?: string
  // Legacy (deprecated, to be removed in Phase 3)
  roles?: string[]        // ['admin'] — Legacy role name check
  // Modern (preferred)
  permissionKeys?: string[]  // ['settings'] — Permission key check
}
```

**Migration rule**: All routes MUST have `permissionKeys`. `roles` is optional and acts as a transitional fallback.

## Sidebar Menu Item Shape

```typescript
interface SidebarMenuItem {
  path: string
  name: string
  meta: RouteMeta
  children?: SidebarMenuItem[]
}
```

## Permission Utility Module (NEW)

```typescript
// src/utils/permission.js
export function hasAnyPermission(
  userPermissions: string[],
  requiredPermissions: string[]
): boolean

export function isAdminRole(roleCode: string): boolean

export function resolveAccessDecision(
  userRole: string,
  userPermissions: string[],
  routeMeta: RouteMeta
): 'allow' | 'deny' | 'fallback'
```

## State Transition: Access Decision Flow

```
User navigates to /settings
        |
        v
router.beforeEach intercepts
        |
        v
resolveAccessDecision()
  ├─ meta.roles includes userRole? ── YES → allow
  ├─ meta.permissionKeys any match? ─ YES → allow
  └─ neither? ─────────────────────── NO  → deny (redirect)
```
