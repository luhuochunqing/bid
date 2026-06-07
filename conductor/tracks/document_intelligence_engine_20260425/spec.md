# Document Intelligence Engine (DocInsight)

## Objective
Extract the "Evidence-Driven Parsing" capability from the `biddraftagent` module and repackage it as a universal, system-wide service. This engine will provide high-fidelity conversion, structural chunking, and evidence-anchored extraction for various document types (tenders, contracts, cases, etc.).

## Background
The parsing logic developed for tender documents (Phase 2 of `evidence_driven_conversion_20260425`) has proven highly effective. It successfully converts legacy formats (.doc, .pdf) to structured Markdown, slices the document into logical sections, and prompts AI to extract data while preserving the source context (the "evidence link"). However, this logic is currently tightly coupled with the `biddraftagent` application service. To maximize its business value, it must be abstracted into a core platform capability.

## Key Goals
1.  **Backend Abstraction**: Create a `com.xiyu.bid.docinsight` module.
2.  **Universal Conversion Service**: Standardize the interface for the Python sidecar and local file storage.
3.  **Generic Extraction Pipeline**: Provide a generic interface for structural chunking and AI prompt injection, allowing callers to define their own extraction schemas.
4.  **Frontend Componentization**: Extract `TenderConversionWorkbench.vue` into a highly reusable, schema-driven `DocVerificationWorkbench.vue` component.

## Technical Architecture
*   **Infrastructure**: Python FastAPI Sidecar (MarkItDown, textutil), Local File Storage.
*   **Core Domain**: `DocumentIntelligenceService` (Coordinates conversion, chunking, and AI analysis).
*   **Interfaces**: Generic REST endpoints for document processing and analysis retrieval.
*   **Frontend**: Reusable Vue 3 component with integrated Markdown highlighting and evidence linking.

## Non-Goals
*   Changing the underlying parsing logic (MarkItDown, Structural Chunker) – we are purely refactoring and repackaging.
*   Implementing new AI models or modifying existing business logic outside of the extraction pipeline.
