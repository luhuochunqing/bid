create table contract_borrow_applications (
    id bigint not null auto_increment,
    contract_no varchar(100) not null,
    contract_name varchar(255) not null,
    source_name varchar(255),
    borrower_name varchar(100) not null,
    borrower_dept varchar(100),
    customer_name varchar(255),
    purpose text,
    borrow_type varchar(100),
    expected_return_date date,
    submitted_at datetime(6) not null,
    approver_name varchar(100),
    approved_at datetime(6),
    rejection_reason text,
    rejected_at datetime(6),
    return_remark text,
    returned_at datetime(6),
    cancel_reason text,
    cancelled_at datetime(6),
    last_comment text,
    status enum ('PENDING_APPROVAL','APPROVED','REJECTED','BORROWED','RETURNED','CANCELLED') not null,
    created_at datetime(6) not null,
    updated_at datetime(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table contract_borrow_events (
    id bigint not null auto_increment,
    application_id bigint not null,
    event_type enum ('SUBMITTED','APPROVED','REJECTED','RETURNED','CANCELLED') not null,
    status_after enum ('PENDING_APPROVAL','APPROVED','REJECTED','BORROWED','RETURNED','CANCELLED') not null,
    actor_name varchar(100),
    comment text,
    created_at datetime(6) not null,
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_contract_borrow_status
    on contract_borrow_applications (status);

create index idx_contract_borrow_expected_return
    on contract_borrow_applications (expected_return_date);

create index idx_contract_borrow_borrower
    on contract_borrow_applications (borrower_name);

create index idx_contract_borrow_events_application
    on contract_borrow_events (application_id, created_at);
