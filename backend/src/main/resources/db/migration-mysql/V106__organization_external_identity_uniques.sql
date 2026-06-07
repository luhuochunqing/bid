CREATE UNIQUE INDEX ux_org_departments_source_external
  ON organization_departments(source_app, external_dept_id);

CREATE UNIQUE INDEX ux_users_external_org_user
  ON users(external_org_source_app, external_org_user_id);
