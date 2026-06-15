-- Rollback for V1076: restore single-column primary key on department_code
-- Safe reversion: only run if the composite primary key exists.

ALTER TABLE organization_departments DROP PRIMARY KEY;
ALTER TABLE organization_departments ADD PRIMARY KEY (department_code);
