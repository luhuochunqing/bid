# Implementation Plan: High-Fidelity Tender-to-Project Conversion Engine

## Phase 1: Markdown Sidecar & Pipeline Integration
- [x] Task: Develop minimal Python Sidecar using `MarkItDown` + FastAPI (returning rich structure JSON).
- [x] Task: Integrate Sidecar as a new `TenderDocumentTextExtractor` adapter in the `tenderupload` module.
- [x] Task: Write Tests (Verify PDF -> Sidecar -> Java rich metadata flow, with fallback to legacy extractor).
- [x] Task: Conductor - User Manual Verification 'Phase 1: Pipeline established' (Protocol in workflow.md)

## Phase 2: Structural Chunking & Evidence Anchoring
- [x] Task: Implement Java "Structural Chunker" using Sidecar headings and character offsets.
- [x] Task: Update AI Extraction Logic to save "Source Excerpts" and "Section Paths" for every field.
- [ ] Task: Write Tests (Evaluate extraction quality improvements via Markdown structure).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Evidence capture' (Protocol in workflow.md)

## Phase 3: Pure-Java Business Guardrails
- [x] Task: Implement domain-level validation rules (Budget Sanity, Timeline conflicts) in Java.
- [x] Task: Implement Qualification Matcher (Cross-referencing extracted requirements vs internal pool).
- [x] Task: Finalize "Evidence-Linked" Conversion View in the frontend.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Business Closure' (Protocol in workflow.md)
