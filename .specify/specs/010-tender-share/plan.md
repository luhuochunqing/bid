# Implementation Plan: Tender Detail Share

**Branch**: `010-tender-share` | **Date**: 2026-05-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/010-tender-share/spec.md`

## Summary

Add a share feature to the tender detail page. Users click a share button to
open a modal dialog containing a "Copy Link" button and a QR code. Pure
frontend feature using existing Element Plus components and the `qrcode` npm
package. No backend changes required.

## Technical Context

**Language/Version**: TypeScript 5.x, Vue 3 (Composition API with `<script setup>`)

**Primary Dependencies**:
- Element Plus (ElDialog, ElButton, ElMessage) -- already in project
- `qrcode` npm package -- to be added (lightweight QR generation, ~20KB gzipped)
- `qrcode.vue` or manual canvas rendering -- TBD during implementation

**Storage**: N/A (no data persistence needed; feature uses current page URL)

**Testing**: Vitest (unit tests for components), Playwright (E2E for dialog flow)

**Target Platform**: Modern browsers (Chrome, Firefox, Safari, Edge)

**Project Type**: Web application (Vue 3 SPA frontend)

**Performance Goals**: Share dialog opens within 200ms of button click

**Constraints**: No backend changes; must use existing project UI conventions
(Element Plus Design Tokens); QR library must be lightweight

**Scale/Scope**: Single feature on one page (tender detail view)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. FP-Java Architecture**: Not applicable -- pure frontend feature, no Java
  backend involvement
- **II. Real-API Only**: Compliant -- feature uses only browser APIs
  (`navigator.clipboard`, current URL via `window.location`), no mock data
- **III. TDD**: Will follow -- components will have unit tests before logic
  implementation
- **IV. Split-First & Simplicity**: Compliant -- single component, small scope
- **V. Boring Proven Patterns**: Compliant -- uses existing ElDialog and project
  patterns; QR library is a well-established pattern
- **Security**: No new API endpoints or data access; URL sharing is read-only
- **Multi-Agent SOP**: No cross-agent conflicts expected for this scope

**Result**: PASS -- no violations. Complexity Tracking is not needed.

## Project Structure

### Documentation (this feature)

```text
specs/010-tender-share/
├── spec.md              # Feature specification
├── plan.md              # This file (implementation plan)
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command) -- NOT NEEDED (no data entities)
├── contracts/           # Phase 1 output (/speckit-plan command) -- NOT NEEDED (pure frontend)
└── tasks.md             # Phase 2 output (/speckit-tasks command)
```

### Source Code (repository root)

```text
src/
├── views/
│   └── tender/
│       └── detail/
│           ├── TenderDetailPage.vue    # Existing detail page
│           └── components/
│               └── TenderShareDialog.vue  # NEW: Share dialog component
├── components/                          # Shared components (if moved here)
└── composables/
    └── useShare.ts                     # NEW: Share logic composable
```

**Structure Decision**: The feature follows the project's existing frontend
convention -- placing feature-specific components alongside the page view and
extracting reusable logic into composables.

## Architecture Decisions

1. **Share dialog as a separate component** (`TenderShareDialog.vue`) --
   Encapsulates dialog state, clipboard API calls, and QR rendering. Imported
   by the detail page component.

2. **QR code via `qrcode` npm package** -- Lightweight (~20KB gzipped),
   pure-JS QR generation. Renders to a `<canvas>` element. No framework-specific
   wrapper needed.

3. **Clipboard API with fallback** -- Primary: `navigator.clipboard.writeText()`.
   Fallback for older browsers: display URL in a read-only input field for
   manual selection and copy.

4. **Share button placement** -- In the detail page action toolbar, alongside
   other action buttons (edit, delete, etc.).

## Complexity Tracking

Not needed -- Constitution Check passes with no violations.
