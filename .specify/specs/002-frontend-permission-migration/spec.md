# Feature Specification: Frontend Permission Migration

**Feature Branch**: `002-frontend-permission-migration`

**Created**: 2026-05-17

**Status**: Draft

**Input**: User description: "Migrate frontend from hardcoded role checks to menuPermissions-driven access control"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Route and Menu Access for New Roles (Priority: P1)

As a user with the `bid_admin` role, I want to access the system settings page so that I can perform administrative tasks.

**Why this priority**: Currently, `bid_admin`, `bid_lead`, `bid_specialist`, and `admin_staff` users are incorrectly blocked by route guards because the frontend only recognizes legacy roles (`admin`, `manager`, `staff`). This is a production blocker.

**Independent Test**: A `bid_admin` user logs in and navigates to `/settings`. The page should load successfully, not redirect to `/dashboard`.

**Acceptance Scenarios**:

1. **Given** a user with `bid_admin` role and `settings` permission, **When** they navigate to `/settings`, **Then** the page loads without redirection.
2. **Given** a user with `bid_lead` role and `project` permission, **When** they navigate to `/project`, **Then** the page loads and project functionality is accessible.
3. **Given** a user with `admin_staff` role, **When** they view the sidebar, **Then** they only see menus matching their `menuPermissions` (e.g., qualification management).

---

### User Story 2 - Page-Level Feature Visibility (Priority: P2)

As a user with any valid role, I want page buttons and actions to respect my permissions rather than my role name so that I can perform authorized actions regardless of how my role is named.

**Why this priority**: Even if users can access pages, buttons like "Create Project", "Audit Expense", or "Manage Qualification" are hidden based on hardcoded `role === 'admin'` checks. This limits functionality for new roles.

**Independent Test**: A `bid_admin` user opens the Project List page and sees the "Create Project" button because they have the `project:create` permission, not because their role name is exactly `"manager"`.

**Acceptance Scenarios**:

1. **Given** a `bid_admin` user on the Project List page, **When** the page loads, **Then** the "Create Project" button is visible.
2. **Given** a `bid_specialist` user on the Bidding page, **When** they view tender details, **Then** action buttons match their permissions (e.g., follow-up but not delete).
3. **Given** an `admin_staff` user in the Knowledge Base, **When** they view the Qualification section, **Then** management actions are available if they have the corresponding permissions.

---

### User Story 3 - Legacy Role Cleanup (Priority: P3)

As a developer, I want the codebase to use a single permission mechanism so that future role additions require zero frontend changes.

**Why this priority**: Dual mechanisms (`meta.roles` + `meta.permissionKeys`) create maintenance overhead and confusion. A single source of truth reduces bugs when adding new roles.

**Independent Test**: After cleanup, searching the frontend codebase for `meta.roles` and `role ===` returns zero business-logic results (only tests or backward-compatibility shims remain).

**Acceptance Scenarios**:

1. **Given** a codebase search for `meta.roles`, **When** reviewing results, **Then** no route or sidebar item relies solely on `meta.roles` for access control.
2. **Given** a new role `quality_inspector` is added to the backend with appropriate `menuPermissions`, **When** a user with that role logs in, **Then** they can access permitted pages without any frontend code changes.

---

### Edge Cases

- What happens when a user has no `menuPermissions` array (legacy login or backend downgrade)?
- How does the system handle `permissionKeys` that are not defined in `sidebar-menu.js`?
- What if `hasMenuAccessForRole()` settings cache is out of sync with backend permissions?
- How should mixed environments behave during rollout (some users on old auth, some on new)?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The route guard MUST evaluate access based on `meta.permissionKeys` when `meta.roles` does not match the user's role.
- **FR-002**: The sidebar MUST display menu items if the user has any of the required `permissionKeys`, regardless of their role name.
- **FR-003**: Page-level permission helpers (e.g., `buildPermissionFlags`) MUST accept `menuPermissions` array instead of role string.
- **FR-004**: All new permission checks MUST use `userStore.hasPermission(permissionKey)` or equivalent utility.
- **FR-005**: The system MUST gracefully degrade when `menuPermissions` is missing or empty (default to allowing access to prevent lockouts).

### Key Entities *(include if feature involves data)*

- **User Auth State**: Contains `role` (RoleProfile code string), `menuPermissions` (string array), `roleName` (display string)
- **Route Meta**: Contains `roles` (legacy string array, to be deprecated) and `permissionKeys` (modern string array)
- **Sidebar Menu Item**: Contains `meta.roles` and `meta.permissionKeys` alongside path and component references

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users with new roles (`bid_admin`, `bid_lead`, `bid_specialist`, `admin_staff`) can access all pages their `menuPermissions` authorize within 2 seconds of login.
- **SC-002**: Zero hardcoded `role === 'admin'` or `role === 'manager'` checks remain in production page components (allowing up to 5 in transitional utility files with TODO comments).
- **SC-003**: Adding a new backend role with `menuPermissions` requires zero frontend code changes for page access.
- **SC-004**: All existing e2e tests for `admin`, `manager`, and `staff` roles continue to pass without modification.

## Assumptions

- The backend `AuthResponse` already returns a complete `menuPermissions` array for all users.
- `sidebar-menu.js` already has `meta.permissionKeys` configured for most routes (some may need addition).
- The `userStore.hasPermission()` Pinia store method is reliable and reactive.
- Legacy role names (`admin`, `manager`, `staff`) will continue to work during the transition period.
- Rollout can be done incrementally: route guards first (unblocks users), then page components (restores full functionality).
