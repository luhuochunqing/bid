# Feature Specification: Workbench Quick Entry Enhancement

**Feature Branch**: `006-workbench-quick-entry`

**Created**: 2026-05-23

**Status**: Draft

**Input**: User description: "完善工作台快捷入口功能，修复 staff banner 路由缺失、处理待办的页面滚动行为、dialog 提交后路由跳转"

## User Scenarios & Testing

### User Story 1 - Staff User Can Navigate from Banner Buttons (Priority: P1)

As a staff user, I want to click "我的任务" and "日程" buttons in the welcome banner so that I can quickly navigate to relevant pages.

**Why this priority**: This is a broken feature — clicking buttons on the banner currently does nothing for staff users, which is a direct usability bug.

**Independent Test**: Login as a staff user, click each banner button, and verify the correct page loads.

**Acceptance Scenarios**:

1. **Given** I am logged in as a staff user, **When** I click the "我的任务" banner button, **Then** I am navigated to the project list page (`/project`)
2. **Given** I am logged in as a staff user, **When** I click the "日程" banner button, **Then** I am navigated to the dashboard schedule view (`/dashboard?tab=schedule`)
3. **Given** I am logged in as an admin or manager, **When** I view the banner, **Then** the banner still shows their respective role-specific buttons unchanged

---

### User Story 2 - User Can Navigate to Todos Page (Priority: P2)

As any user, I want clicking "处理待办" to navigate to a dedicated task/todo page, so that I can view and manage my pending items properly, rather than just scrolling the page.

**Why this priority**: The current behavior (scrolling to bottom of dashboard) provides minimal utility. A proper navigation would give users a dedicated view.

**Independent Test**: Click "处理待办" and verify the browser navigates to the project page with a todo filter.

**Acceptance Scenarios**:

1. **Given** I am on the dashboard, **When** I click "处理待办", **Then** I am navigated to `/project?tab=todo` (or equivalent task list view)
2. **Given** I am a staff user who only sees their own tasks, **When** I click "处理待办", **Then** I see only my assigned tasks

---

### User Story 3 - User is Redirected After Dialog Submission (Priority: P3)

As any user, after submitting a quick action form (标书支持申请 / 资质借阅 / 费用申请) from the dashboard, I want to be redirected to the relevant tracking page so that I can monitor the status of my submission.

**Why this priority**: Currently, after submission the dialog simply closes with a success message but no onward path. This is a mild UX friction.

**Independent Test**: Submit each quick action form and verify navigation to the appropriate tracking page.

**Acceptance Scenarios**:

1. **Given** I submit a 标书支持申请 dialog, **When** submission succeeds, **Then** I am navigated to the support requests list page (or `/project` if no dedicated page exists)
2. **Given** I submit a 资质借阅 dialog, **When** submission succeeds, **Then** I am navigated to the borrow records page (or `/knowledge/archive`)
3. **Given** I submit a 费用申请 dialog, **When** submission succeeds, **Then** I am navigated to the expense list page (or `/knowledge/deposit`)

### Edge Cases

- What if the user closes the dialog without submitting? → No navigation, stay on dashboard (unchanged behavior)
- What if the dialog submission fails? → Show error message, do NOT navigate. User stays to retry
- What about users without permission to view the target page? → Router will handle with existing auth guards

## Requirements

### Functional Requirements

- **FR-001**: System MUST provide valid route targets for staff role banner action buttons ("我的任务" and "日程")
- **FR-002**: "我的任务" button MUST navigate to `/project` (project list page)
- **FR-003**: "日程" button MUST navigate to `/dashboard?tab=schedule` (dashboard schedule section)
- **FR-004**: Admin and manager banner buttons MUST remain unchanged
- **FR-005**: "处理待办" button MUST navigate to a task/todo view via Vue Router, not use `window.scrollTo`
- **FR-006**: "处理待办" MUST navigate to `/project?tab=todo` (or equivalent project-based task list)
- **FR-007**: After successful dialog submission (标书支持/资质借阅/费用申请), user MUST be redirected to a relevant tracking page
- **FR-008**: If dialog submission fails, user MUST NOT be navigated; error must be displayed instead
- **FR-009**: All navigations MUST respect existing permission/role guards

### Key Entities

(No new entities. Feature modifies navigation behavior of existing UI components only.)

## Success Criteria

### Measurable Outcomes

- **SC-001**: Staff users can click both banner buttons and reach valid pages (was broken, now fixed)
- **SC-002**: Clicking "处理待办" results in page navigation within 1 second (was instant scroll)
- **SC-003**: Dialog submission flow provides clear onward path (was dead-end after success)
- **SC-004**: No regression: admin and manager banner behavior unchanged

## Assumptions

- Existing route paths (`/project`, `/dashboard`, `/knowledge/archive`, `/knowledge/deposit`) are correct and will remain stable
- The router already has permission guards; no additional auth logic needed for target routes
- Dialog submission is handled by existing API services; only the post-submit callback needs modification
- Mobile users will also benefit from proper navigation (scroll behavior was especially poor on mobile)
- The `/project` list view already supports a `tab` query parameter for todo/task filtering (or will be added separately if needed)
