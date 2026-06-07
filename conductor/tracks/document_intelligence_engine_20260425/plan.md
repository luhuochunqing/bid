# Implementation Plan: Document Intelligence Engine (DocInsight)

## Phase 1: Backend Extraction & Modularization
- [x] Task: Create new package `com.xiyu.bid.docinsight`.
- [x] Task: Move `TenderDocumentStorage`, `StoredTenderDocument`, and related infrastructure (e.g., `LocalTenderDocumentStorage`) to the new module, renaming them generically (e.g., `DocumentStorage`, `StoredDocument`).
- [x] Task: Move `MarkItDownSidecarTextExtractor` and related DTOs (`ExtractedTenderDocument`) to the new module, renaming them generically.
- [x] Task: Move `StructuralDocumentChunker` and `TenderDocumentTextChunker` to the new module, generalizing their input/output if necessary.
- [x] Task: Refactor `BidTenderDocumentImportAppService` and `OpenAiTenderDocumentAnalyzer` to consume the new `docinsight` module services instead of local implementations.

## Phase 2: Generic AI Extraction Pipeline
- [x] Task: Define a generic `DocumentAnalysisInput` and `DocumentAnalysisResult` interface in `docinsight`.
- [x] Task: Create a base `OpenAiDocumentAnalyzer` that accepts a customizable schema and prompt template.
- [x] Task: Refactor the existing tender parsing logic (`OpenAiTenderDocumentAnalyzer`) to extend or utilize this new generic analyzer.
- [x] Task: Ensure existing tests pass after refactoring.

## Phase 3: Frontend Componentization
- [x] Task: Create `src/components/common/DocVerificationWorkbench.vue` based on `TenderConversionWorkbench.vue`.
- [x] Task: Make the new workbench accept generic schema definitions for the left-side form (dynamic rendering based on provided data structure).
- [x] Task: Replace the usage of `TenderConversionWorkbench.vue` in `ProjectCreate.vue` with the new generic `DocVerificationWorkbench.vue`.
- [x] Task: Ensure the "Evidence Highlight" feature works flawlessly with the generic component.

## Phase 4: Integration & Verification
- [x] Task: Create a generic API endpoint (e.g., `/api/docinsight/parse`) to test the standalone engine.
- [x] Task: Verify the entire tender parsing flow (import -> parse -> verify -> draft) works as expected using the newly abstracted engine.
