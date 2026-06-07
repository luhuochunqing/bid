alter table contract_borrow_applications
    add column version bigint not null default 0;

create index idx_project_customer_type_status
    on projects (customer_type, status);

create index idx_contract_borrow_status_expected_return
    on contract_borrow_applications (status, expected_return_date);

create index idx_contract_borrow_submitted_at
    on contract_borrow_applications (submitted_at desc);
