# Feature Specification: Tender Detail Share

**Feature Branch**: `010-tender-share`

**Created**: 2026-05-27

**Status**: Draft

**Input**: User description: "给标讯详情页加一个分享功能。用户点击分享按钮，弹出一个对话框，里面有复制链接按钮和扫码二维码。这是一个纯前端功能，不需要后端改动。"

## User Scenarios & Testing

### User Story 1 - Share Tender via Link Copy (Priority: P1)

As a user viewing a tender detail page, I want to share the current tender with
colleagues by copying its direct link to my clipboard, so that I can quickly
distribute the tender URL through messaging or email.

**Why this priority**: Link sharing is the most common and universally-supported
sharing method across all communication channels. It requires no additional
infrastructure and delivers immediate value.

**Independent Test**: Can be fully tested by opening a tender detail page,
clicking the share button, and confirming the copy link action copies the
correct URL to the clipboard.

**Acceptance Scenarios**:

1. **Given** the user is viewing a tender detail page, **When** they click the
   share button, **Then** a share dialog opens with a "Copy Link" button and
   a QR code section.
2. **Given** the share dialog is open, **When** the user clicks "Copy Link",
   **Then** the current page URL is copied to the clipboard and a success toast
   notification is displayed.
3. **Given** the share dialog is open, **When** the user clicks outside the
   dialog or clicks a close button, **Then** the dialog closes without any
   action taken.

---

### User Story 2 - Share Tender via QR Code (Priority: P1)

As a user viewing a tender detail page, I want to share the current tender by
showing a QR code that others can scan with their phone, so that colleagues can
quickly open the tender on their mobile devices.

**Why this priority**: QR code sharing enables cross-device sharing and is
particularly useful in meetings or when users are at their desktop and want to
view on mobile. It is specified as a core requirement.

**Independent Test**: Can be tested independently by opening the share dialog
and verifying that a QR code image is displayed that encodes the current page
URL.

**Acceptance Scenarios**:

1. **Given** the share dialog is open, **Then** a QR code is displayed that
   encodes the current page URL.
2. **Given** the QR code is displayed, **When** scanned with a QR code reader,
   **Then** the reader navigates to the correct tender detail page URL.

---

### User Story 3 - Share Button Visibility (Priority: P2)

As a user, I want the share button to be clearly visible on the tender detail
page, so that I can discover the sharing functionality without hunting for it.

**Why this priority**: A hidden share button provides no value, but the button
placement can be refined based on layout constraints.

**Independent Test**: Can be tested by verifying the share button is rendered
and visible within the detail page header or action area.

**Acceptance Scenarios**:

1. **Given** the user is on a tender detail page, **Then** a share button/icon
   is visible in the page header or action toolbar area.

### Edge Cases

- What happens when the browser does not support the Clipboard API? (Fallback
  to a text selection / manual copy instruction)
- What happens when the page URL is very long? (QR code should still encode the
  full URL)
- What happens when the share dialog is opened on a tender that has not fully
  loaded? (Button should only be active when page content is ready)
- What happens when the user copies the link multiple times? (Each copy should
  show a toast; duplicate toasts should be prevented)

## Requirements

### Functional Requirements

- **FR-001**: System MUST render a share button/icon on the tender detail page
- **FR-002**: System MUST open a modal dialog when the share button is clicked
- **FR-003**: The share dialog MUST contain a "Copy Link" button
- **FR-004**: The "Copy Link" button MUST copy the current page URL to the
  clipboard when clicked
- **FR-005**: The system MUST display a success toast notification after the
  URL is copied
- **FR-006**: The share dialog MUST display a QR code that encodes the current
  page URL
- **FR-007**: The share dialog MUST be closable via a close button or by
  clicking the overlay backdrop
- **FR-008**: The share button MUST only appear when the tender detail data
  has loaded successfully
- **FR-009**: The system MUST gracefully degrade if the Clipboard API is
  unavailable (fallback: display the URL in a selectable text field)

### Key Entities

This feature involves no new data entities. It is a pure UI enhancement to the
existing tender detail page. The only data used is the current page URL,
obtained from the browser's current location.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can share any tender detail page in 2 clicks or fewer
  (click share button -> click copy link)
- **SC-002**: The share dialog renders within 200ms of clicking the share
  button
- **SC-003**: The QR code renders correctly and is scannable across all modern
  browsers (Chrome, Firefox, Safari, Edge)
- **SC-004**: No errors are thrown in the console during share dialog open,
  copy, and close operations

## Assumptions

- Users have modern browsers that support the Clipboard API
- A lightweight QR code generation library will be used for QR code rendering
- The feature is built on top of the existing Element Plus UI component library
  (already a project dependency)
- No backend API changes are needed -- all data comes from the frontend
  environment (current URL)
- The share button is placed in the detail page's action toolbar / header area
- Toast notifications use the existing project notification system (Element
  Plus ElMessage)
