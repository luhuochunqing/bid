# Quickstart: Local Verification

**Feature**: Frontend Permission Migration
**Date**: 2026-05-17

## Prerequisites

```bash
# Ensure dev services are running
export XIYU_DEV_CONFIRMED=1
npm run dev:all
```

## P1 Verification (Route Guard Unblocking)

### Test 1: bid_admin can access Settings
1. Login as `xiaochen` / `123456` (e2e `bid_admin` account)
2. Navigate directly to `http://127.0.0.1:1314/settings`
3. **Expected**: Page loads, not redirected to `/dashboard`
4. **Before fix**: Redirected to `/dashboard`

### Test 2: bid_lead can access Analytics
1. Login as `xiaoliu` / `123456` (e2e `bid_lead` account)
2. Navigate to `http://127.0.0.1:1314/analytics/dashboard`
3. **Expected**: Page loads (if `bid_lead` has `analytics` permission)

### Test 3: admin_staff sees limited sidebar
1. Login as `xiaozheng` / `123456` (e2e `admin_staff` account)
2. **Expected**: Sidebar shows only qualification-related menus

## P2 Verification (Page-Level Buttons)

### Test 4: bid_admin sees Create Project button
1. Login as `xiaochen`
2. Go to `/project`
3. **Expected**: "Create Project" button is visible
4. **Before fix**: Button hidden because `role !== 'admin'` and `role !== 'manager'`

### Test 5: bid_specialist has appropriate bidding actions
1. Login as `xiaozhou` / `123456` (e2e `bid_specialist`)
2. Go to `/bidding`
3. Open a tender detail
4. **Expected**: Follow-up actions visible, delete/sync hidden

## Regression Tests

### Test R1: Legacy admin still works
1. Login as `lizong` / `123456` (e2e `admin`)
2. Verify all pages and menus accessible
3. Verify all admin buttons visible

### Test R2: Legacy manager still works
1. Login as `zhangjingli` / `123456` (e2e `manager`)
2. Verify project pages and approval buttons accessible

## Automated Test Commands

```bash
# Unit tests for new permission utility
npm run test:unit -- src/utils/permission.test.js

# E2E tests (run full suite)
npm run test:e2e

# Build verification
npm run build
```
