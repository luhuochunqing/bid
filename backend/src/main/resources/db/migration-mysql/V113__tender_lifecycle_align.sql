-- V113: align project_result with ProjectResult entity
-- V108 created project_result without `summary` and `evidence_doc_ids`,
-- but ProjectResult.java declares both columns. Add them here.
-- (Audit also covered project_initiation_details / project_evaluation /
--  project_retrospective / project_closure / project_lead_assignment;
--  no other column gaps remain after V108+V109+V110+V111+V112.)

ALTER TABLE project_result
    ADD COLUMN summary VARCHAR(2048) NULL,
    ADD COLUMN evidence_doc_ids VARCHAR(1024) NULL;
