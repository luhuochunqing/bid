# Tasks: Tender Detail Share

**Input**: Design documents from `/specs/010-tender-share/`

**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Tests**: Unit tests included for the composable and component logic.

**Organization**: Tasks are grouped by user story to enable independent
implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Frontend**: all source files under `src/` at repository root
- **Components**: `src/views/tender/detail/components/`
- **Composables**: `src/composables/`
- **Tests**: `src/__tests__/` or co-located `.spec.ts` files

---

## Phase 1: Setup

**Purpose**: Install new dependencies and prepare the project

- [ ] T001 Add `qrcode` npm package as a project dependency via `npm install qrcode`

---

## Phase 2: User Story 1 - Share Tender via Link Copy (Priority: P1) MVP

**Goal**: User clicks share button, dialog opens with copy-link functionality

**Independent Test**: Open a tender detail page, click share, click copy link,
verify URL is in clipboard and toast appears.

### Tests for User Story 1

- [ ] T002 [P] [US1] Write unit test for `useShare` composable's `copyLink`
      function in `src/composables/__tests__/useShare.spec.ts` -- verify it
      calls `navigator.clipboard.writeText` with the correct URL and returns
      success status
- [ ] T003 [P] [US1] Write unit test for `TenderShareDialog` component's
      visibility and copy button rendering in
      `src/views/tender/detail/components/__tests__/TenderShareDialog.spec.ts`

### Implementation for User Story 1

- [ ] T004 [US1] Create `useShare` composable in
      `src/composables/useShare.ts` with:
      - `copyLink()` method using `navigator.clipboard.writeText()`
      - Clipboard API feature detection and fallback to text selection
      - URL retrieval from `window.location.href`
      - Toast notification via Element Plus `ElMessage`
      - Debounce to prevent duplicate toasts on rapid clicks
- [ ] T005 [US1] Create `TenderShareDialog.vue` component in
      `src/views/tender/detail/components/TenderShareDialog.vue` with:
      - `ElDialog` as the modal container
      - Close button and backdrop click to dismiss
      - Props for visibility control (`modelValue`)
      - "Copy Link" button using `ElButton`
      - Slot/location for QR code (to be added in US2)
      - Clipboard fallback UI (selectable text input)

**Checkpoint**: At this point, the share dialog opens and the copy link
function works with toast notification.

---

## Phase 3: User Story 2 - Share Tender via QR Code (Priority: P1)

**Goal**: Share dialog also displays a scannable QR code

**Independent Test**: Open share dialog, verify QR code canvas is rendered,
scan it to confirm it encodes the correct URL.

### Tests for User Story 2

- [ ] T006 [P] [US2] Write unit test for QR code rendering in
      `TenderShareDialog` -- verify the QR canvas element is present in the DOM
      after the component mounts

### Implementation for User Story 2

- [ ] T007 [US2] Add QR code rendering to `TenderShareDialog.vue`:
      - Import and use `qrcode` library to generate QR on a `<canvas>` element
      - Encode the current page URL from `useShare` composable
      - Place QR code section below the copy link section in the dialog
      - Add appropriate styling (centered, with padding)

**Checkpoint**: At this point, both copy link and QR code features work within
the share dialog.

---

## Phase 4: User Story 3 - Share Button Visibility (Priority: P2)

**Goal**: Share button is visible on the tender detail page

**Independent Test**: Navigate to a tender detail page, verify the share icon
is visible in the action area.

### Implementation for User Story 3

- [ ] T008 [US3] Add share button to the tender detail page
      view in `src/views/tender/detail/`:
      - Place share icon/button in the page action toolbar
      - Import and wire up `TenderShareDialog` component
      - Button only appears after tender detail data has loaded
      - Use Element Plus icon for the share button (`ElIcon` with Share icon)
- [ ] T009 [US3] Integrate `TenderShareDialog` with the detail page's data
      loading state -- ensure share button is disabled/hidden until data is
      ready (FR-008 compliance)

**Checkpoint**: The complete share feature is integrated into the detail page.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Ensure quality, edge cases, and cleanup

- [ ] T010 [P] Add CSS styling for the share dialog -- ensure responsive layout,
      proper spacing between copy link section and QR code, and dark mode
      compatibility using project Design Tokens
- [ ] T011 Add E2E test for the full share flow in
      `e2e/specs/tender-detail-share.spec.ts` or integrate into existing detail
      page E2E tests: click share button -> verify dialog -> click copy link ->
      verify toast
- [ ] T012 Build verification: run `npm run build` and `npm run test:unit` to
      confirm no regressions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies -- can start immediately
- **US1 (Phase 2)**: Depends on Setup completion
- **US2 (Phase 3)**: Depends on US1 completion (shares the dialog component)
- **US3 (Phase 4)**: Depends on US2 completion (needs the full dialog component)
- **Polish (Phase 5)**: Depends on all user stories complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Setup -- No dependencies on other stories
- **User Story 2 (P1)**: Depends on US1 (uses the same dialog; QR section added to existing dialog)
- **User Story 3 (P2)**: Depends on US2 (share button triggers the dialog)

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- T002 and T003 can run in parallel (both are tests for different files)
- T006 is independent within Phase 3
- T010 is independent within Phase 5

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (install qrcode)
2. Complete Phase 2: User Story 1 (copy link dialog)
3. **STOP and VALIDATE**: Test US1 independently
4. Deploy/demo if ready

### Incremental Delivery

1. Add US1 (Link Copy) --> Test independently (MVP!)
2. Add US2 (QR Code) --> Test independently
3. Add US3 (Button on detail page) --> Test independently
4. Polish and cleanup

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- No backend API changes needed -- all data comes from browser
