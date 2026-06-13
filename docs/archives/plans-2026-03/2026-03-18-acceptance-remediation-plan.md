# Acceptance Remediation Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close the CIO acceptance gaps so the project can move from “暂不准入” to a credible re-inspection candidate.

**Architecture:** We will prioritize the highest-risk remediation items first: enforce frontend authorization boundaries, complete the authentication/session contract, replace fake-governance UI flows with real persisted settings endpoints, and then backfill API-mode evidence with tests and UAT artifacts. Work should minimize blast radius by keeping frontend and backend changes isolated per task where possible, with explicit regression coverage after each task.

**Tech Stack:** Vue 3, Vite, Pinia, Vue Router, Element Plus, Playwright, Spring Boot 3, Spring Security, JWT, JUnit, Maven

---

### Task 1: Frontend Role Guard and Access Regression

**Files:**
- Modify: `src/router/index.js`
- Modify: `src/components/layout/Sidebar.vue`
- Modify: `src/components/layout/Header.vue`
- Test: `e2e/auth-access-control.spec.js`

**Step 1: Write the failing test**

Add a Playwright spec that verifies:
- unauthenticated user is redirected to `/login`
- non-admin user cannot access `/settings`
- non-manager/non-admin user cannot access `/analytics/dashboard`
- admin user can access `/settings`

**Step 2: Run test to verify it fails**

Run: `npx playwright test e2e/auth-access-control.spec.js --config playwright.config.js`  
Expected: FAIL because current router only checks auth state, not `meta.roles`.

**Step 3: Write minimal implementation**

Implement:
- route guard role checks in `src/router/index.js`
- shared helper to compare `to.meta.roles` against current user role
- optional menu filtering in `Sidebar.vue` and `Header.vue` so unauthorized destinations are not presented as available navigation

**Step 4: Run test to verify it passes**

Run: `npx playwright test e2e/auth-access-control.spec.js --config playwright.config.js`  
Expected: PASS

**Step 5: Commit**

```bash
git add e2e/auth-access-control.spec.js src/router/index.js src/components/layout/Sidebar.vue src/components/layout/Header.vue
git commit -m "fix: enforce frontend role-based route access"
```

### Task 2: Authentication Session Contract Closure

**Files:**
- Modify: `src/api/modules/auth.js`
- Modify: `src/stores/user.js`
- Modify: `src/api/client.js`
- Modify: `src/views/Login.vue`
- Modify: `backend/src/main/java/com/xiyu/bid/controller/AuthController.java`
- Modify: `backend/src/main/java/com/xiyu/bid/service/AuthService.java`
- Create: `backend/src/test/java/com/xiyu/bid/controller/AuthControllerTest.java`

**Step 1: Write the failing tests**

Add backend tests for:
- authenticated user can call logout endpoint successfully
- refresh endpoint responds according to the defined contract
- unauthorized access to `/api/auth/me` is rejected

Add frontend expectations for:
- login page no longer exposes delivery-hostile default password hints in API mode
- logout clears all local session state

**Step 2: Run tests to verify they fail**

Run: `mvn -f backend/pom.xml -Dtest=AuthControllerTest test`  
Expected: FAIL because logout and refresh endpoints are not implemented.

**Step 3: Write minimal implementation**

Implement:
- backend `POST /api/auth/logout` endpoint returning a real success contract
- backend `POST /api/auth/refresh` endpoint or an explicit production-disabled contract if refresh is intentionally unsupported
- frontend `authApi.logout()` and `authApi.refreshToken()` aligned to backend
- remove “API 模式默认密码：123456” style hint from login page
- centralize session cleanup logic in store/client flow

**Step 4: Run tests to verify they pass**

Run: `mvn -f backend/pom.xml -Dtest=AuthControllerTest test`  
Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/controller/AuthController.java backend/src/main/java/com/xiyu/bid/service/AuthService.java backend/src/test/java/com/xiyu/bid/controller/AuthControllerTest.java src/api/modules/auth.js src/stores/user.js src/api/client.js src/views/Login.vue
git commit -m "fix: close auth session contract gaps"
```

### Task 3: Real Settings Persistence Instead of Fake Save

**Files:**
- Modify: `src/views/System/Settings.vue`
- Modify: `src/api/index.js`
- Create: `src/api/modules/settings.js`
- Create: `backend/src/main/java/com/xiyu/bid/settings/controller/SettingsController.java`
- Create: `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java`
- Create: `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java`
- Create: `backend/src/main/java/com/xiyu/bid/settings/service/SettingsService.java`
- Create: `backend/src/test/java/com/xiyu/bid/settings/controller/SettingsControllerTest.java`

**Step 1: Write the failing tests**

Add backend controller tests for:
- admin can read settings
- admin can update settings
- non-admin cannot update settings

Add a focused frontend interaction test or component-level regression proving save actions call API instead of only mutating local arrays.

**Step 2: Run tests to verify they fail**

Run: `mvn -f backend/pom.xml -Dtest=SettingsControllerTest test`  
Expected: FAIL because the settings endpoint does not exist.

**Step 3: Write minimal implementation**

Implement:
- backend settings read/update endpoints with admin-only protection
- frontend `settingsApi` module
- replace fake success-only handlers in `Settings.vue` for at least the highest-risk governance actions with real load/save calls and error handling

**Step 4: Run tests to verify they pass**

Run: `mvn -f backend/pom.xml -Dtest=SettingsControllerTest test`  
Expected: PASS

**Step 5: Commit**

```bash
git add src/views/System/Settings.vue src/api/index.js src/api/modules/settings.js backend/src/main/java/com/xiyu/bid/settings/controller/SettingsController.java backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java backend/src/main/java/com/xiyu/bid/settings/service/SettingsService.java backend/src/test/java/com/xiyu/bid/settings/controller/SettingsControllerTest.java
git commit -m "feat: persist system settings through real api"
```

### Task 4: API-Mode Dashboard/Todo Gap Closure

**Files:**
- Modify: `src/api/modules/dashboard.js`
- Modify: `src/views/Dashboard/Workbench.vue`
- Modify: `backend/src/main/java/com/xiyu/bid/controller/DashboardController.java`
- Modify: `backend/src/main/java/com/xiyu/bid/service/DashboardService.java`
- Create: `backend/src/test/java/com/xiyu/bid/controller/DashboardTodoContractTest.java`

**Step 1: Write the failing tests**

Add backend contract tests for:
- dashboard todo list endpoint returns real API-mode payload
- completing a todo updates status with valid response

Add frontend regression that API mode no longer receives the “not implemented” placeholder for todo data.

**Step 2: Run tests to verify they fail**

Run: `mvn -f backend/pom.xml -Dtest=DashboardTodoContractTest test`  
Expected: FAIL because current frontend contract reports backend todo endpoints as unimplemented.

**Step 3: Write minimal implementation**

Implement:
- concrete backend todo list/complete contract
- align frontend dashboard module with backend response
- ensure placeholder messaging only appears for explicitly out-of-scope features

**Step 4: Run tests to verify they pass**

Run: `mvn -f backend/pom.xml -Dtest=DashboardTodoContractTest test`  
Expected: PASS

**Step 5: Commit**

```bash
git add src/api/modules/dashboard.js src/views/Dashboard/Workbench.vue backend/src/main/java/com/xiyu/bid/controller/DashboardController.java backend/src/main/java/com/xiyu/bid/service/DashboardService.java backend/src/test/java/com/xiyu/bid/controller/DashboardTodoContractTest.java
git commit -m "feat: complete dashboard todo api contract"
```

### Task 5: Acceptance Evidence Refresh

**Files:**
- Modify: `docs/UAT_PLAN.md`
- Modify: `docs/GO_LIVE_CHECKLIST.md`
- Create: `docs/reports/remediation-uat-report-2026-03-18.md`
- Modify: `e2e/commercial-main-flow.spec.js`
- Modify: `playwright.config.js`

**Step 1: Write the failing test/report expectation**

Define acceptance evidence that must exist:
- role access regression
- auth session regression
- settings persistence regression
- API-mode main flow regression

**Step 2: Run verification to confirm gaps**

Run:
- `npm run build`
- `mvn -f backend/pom.xml -DskipTests compile`
- `npx playwright test --config playwright.config.js`

Expected: at least one failure before all remediation tasks complete.

**Step 3: Write minimal implementation**

Update docs and tests so the acceptance package reflects the remediated contract rather than pre-remediation assumptions.

**Step 4: Run verification to confirm it passes**

Run:
- `npm run build`
- `mvn -f backend/pom.xml -DskipTests compile`
- `npx playwright test --config playwright.config.js`

Expected: PASS

**Step 5: Commit**

```bash
git add docs/UAT_PLAN.md docs/GO_LIVE_CHECKLIST.md docs/reports/remediation-uat-report-2026-03-18.md e2e/commercial-main-flow.spec.js playwright.config.js
git commit -m "docs: refresh acceptance evidence for remediation"
```
