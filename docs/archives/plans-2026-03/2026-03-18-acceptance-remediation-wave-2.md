# Acceptance Remediation Wave 2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Close the remaining P1 acceptance gaps by making session security meaningful, making settings durable and effective, and restoring a reliable API-backed E2E test baseline.

**Architecture:** Wave 2 addresses four coupled but separable tracks. Track A hardens auth/session semantics without pretending bearer tokens are revoked when they are not. Track B replaces process-local settings state with real persistence. Track C wires saved settings into runtime authorization behavior so the governance UI changes have operational effect. Track D restores the backend-backed Playwright baseline by making the test environment self-starting or explicitly gated, so acceptance evidence can be reproduced consistently.

**Tech Stack:** Vue 3, Vite, Pinia, Vue Router, Element Plus, Playwright, Spring Boot 3, Spring Security, JWT, JPA, Maven

---

### Task 1: Real Session Lifecycle Instead of Contract-Only Auth

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/service/AuthService.java`
- Modify: `backend/src/main/java/com/xiyu/bid/controller/AuthController.java`
- Modify: `backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java`
- Modify: `backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java`
- Modify: `src/api/modules/auth.js`
- Modify: `src/stores/user.js`
- Modify: `src/api/client.js`
- Test: `backend/src/test/java/com/xiyu/bid/controller/AuthControllerTest.java`
- Test: `e2e/auth-session-lifecycle.spec.js`

**Step 1: Write the failing tests**

Add backend and/or integration tests that prove:
- logout invalidates subsequent use of the same session credential
- refresh works according to a real lifecycle contract instead of requiring the same still-valid access token
- unauthorized and expired session behavior is explicit and testable

Add frontend/session regression to verify:
- logout fully ends the session
- refresh path is used only when contractually valid

**Step 2: Run tests to verify they fail**

Run:
- `mvn -f backend/pom.xml -Dtest=AuthControllerTest test`
- `npx playwright test e2e/auth-session-lifecycle.spec.js --config playwright.config.js`

Expected: FAIL under the current contract-only implementation.

**Step 3: Write minimal implementation**

Implement one coherent lifecycle, for example:
- access token + refresh token contract, or
- explicit short-lived access token + server-side revocation/denylist for logout

Also:
- align frontend auth module and store with the new lifecycle
- keep local/session cleanup centralized
- remove any misleading UX that implies stronger security than the backend provides

**Step 4: Run tests to verify they pass**

Run:
- `mvn -f backend/pom.xml -Dtest=AuthControllerTest test`
- `npx playwright test e2e/auth-session-lifecycle.spec.js --config playwright.config.js`

Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/service/AuthService.java backend/src/main/java/com/xiyu/bid/controller/AuthController.java backend/src/main/java/com/xiyu/bid/auth/JwtUtil.java backend/src/main/java/com/xiyu/bid/config/SecurityConfig.java backend/src/test/java/com/xiyu/bid/controller/AuthControllerTest.java src/api/modules/auth.js src/stores/user.js src/api/client.js e2e/auth-session-lifecycle.spec.js
git commit -m "fix: implement real auth session lifecycle"
```

### Task 2: Durable Settings Persistence

**Files:**
- Modify: `backend/src/main/java/com/xiyu/bid/settings/service/SettingsService.java`
- Modify: `backend/src/main/java/com/xiyu/bid/settings/controller/SettingsController.java`
- Create: `backend/src/main/java/com/xiyu/bid/settings/entity/SystemSetting.java`
- Create: `backend/src/main/java/com/xiyu/bid/settings/repository/SystemSettingRepository.java`
- Modify: `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java`
- Modify: `backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java`
- Test: `backend/src/test/java/com/xiyu/bid/settings/controller/SettingsControllerTest.java`
- Test: `backend/src/test/java/com/xiyu/bid/settings/service/SettingsServiceTest.java`

**Step 1: Write the failing tests**

Add tests that prove:
- saved settings survive a service reload/re-read
- settings values are loaded from persistence rather than static defaults after update
- concurrent reads see the persisted latest committed value

**Step 2: Run tests to verify they fail**

Run:
- `mvn -f backend/pom.xml -Dtest=SettingsControllerTest,SettingsServiceTest test`

Expected: FAIL while settings remain process-local memory only.

**Step 3: Write minimal implementation**

Implement durable settings storage:
- choose a persistence shape that fits the current codebase, likely one JSON-backed JPA row or explicit tables per section
- migrate `SettingsService` away from `AtomicReference`
- preserve current response contract where possible to minimize frontend churn

**Step 4: Run tests to verify they pass**

Run:
- `mvn -f backend/pom.xml -Dtest=SettingsControllerTest,SettingsServiceTest test`

Expected: PASS

**Step 5: Commit**

```bash
git add backend/src/main/java/com/xiyu/bid/settings/service/SettingsService.java backend/src/main/java/com/xiyu/bid/settings/controller/SettingsController.java backend/src/main/java/com/xiyu/bid/settings/entity/SystemSetting.java backend/src/main/java/com/xiyu/bid/settings/repository/SystemSettingRepository.java backend/src/main/java/com/xiyu/bid/settings/dto/SettingsResponse.java backend/src/main/java/com/xiyu/bid/settings/dto/SettingsUpdateRequest.java backend/src/test/java/com/xiyu/bid/settings/controller/SettingsControllerTest.java backend/src/test/java/com/xiyu/bid/settings/service/SettingsServiceTest.java
git commit -m "feat: persist settings durably"
```

### Task 3: Make Saved Permissions Actually Affect Runtime Access

**Files:**
- Modify: `src/router/index.js`
- Modify: `src/components/layout/Sidebar.vue`
- Modify: `src/components/layout/Header.vue`
- Modify: `src/stores/user.js`
- Modify: `src/views/System/Settings.vue`
- Modify: `src/api/modules/settings.js`
- Modify: `backend/src/main/java/com/xiyu/bid/settings/service/SettingsService.java`
- Test: `e2e/settings-permission-effect.spec.js`

**Step 1: Write the failing tests**

Add end-to-end coverage that proves:
- admin updates a role’s menu/route permission
- a user with that role logs in again
- the user’s menu visibility and route access reflect the saved configuration

**Step 2: Run tests to verify they fail**

Run:
- `npx playwright test e2e/settings-permission-effect.spec.js --config playwright.config.js`

Expected: FAIL because current runtime authorization still uses hardcoded role metadata only.

**Step 3: Write minimal implementation**

Implement:
- a clear contract for runtime-consumable permission settings
- frontend load path so auth/session state can consume current role permission policy
- menu filtering and route guard decisions based on effective permission model, not only hardcoded role names

Keep changes scoped to route/menu permissions first; do not over-expand into every data-scope use case unless needed to make the saved settings truthful.

**Step 4: Run tests to verify they pass**

Run:
- `npx playwright test e2e/settings-permission-effect.spec.js --config playwright.config.js`

Expected: PASS

**Step 5: Commit**

```bash
git add src/router/index.js src/components/layout/Sidebar.vue src/components/layout/Header.vue src/stores/user.js src/views/System/Settings.vue src/api/modules/settings.js backend/src/main/java/com/xiyu/bid/settings/service/SettingsService.java e2e/settings-permission-effect.spec.js
git commit -m "feat: enforce saved role permissions at runtime"
```

### Task 4: Restore Backend-Backed E2E Baseline

**Files:**
- Modify: `playwright.config.js`
- Modify: `package.json`
- Modify: `e2e/*.spec.js` as needed for shared session/bootstrap helpers
- Create: `e2e/helpers/session.js`
- Create: `scripts/test/start-api-e2e-stack.sh`
- Create: `scripts/test/stop-api-e2e-stack.sh`
- Modify: `README.md`
- Modify: `docs/UAT_PLAN.md`

**Step 1: Write the failing test expectation**

Capture the current failure mode explicitly:
- Playwright API-mode specs fail with `ECONNREFUSED 127.0.0.1:18080`

**Step 2: Run tests to verify the baseline is broken**

Run:
- `npx playwright test --config playwright.config.js`

Expected: FAIL due to missing backend stack or inconsistent setup.

**Step 3: Write minimal implementation**

Implement a reproducible E2E baseline:
- shared helper for API auth/session setup
- Playwright global setup or wrapper scripts that ensure required backend/frontend services are available
- explicit fast-fail message if the API stack is unavailable
- avoid scattering hardcoded environment assumptions across every spec

**Step 4: Run tests to verify it passes or cleanly gates**

Run:
- `npx playwright test --config playwright.config.js`

Expected:
- either PASS with services automatically brought up, or
- fail with one clear gated prerequisite message rather than many scattered `ECONNREFUSED` failures

**Step 5: Commit**

```bash
git add playwright.config.js package.json e2e/helpers/session.js scripts/test/start-api-e2e-stack.sh scripts/test/stop-api-e2e-stack.sh README.md docs/UAT_PLAN.md e2e
git commit -m "test: stabilize api-backed e2e baseline"
```
