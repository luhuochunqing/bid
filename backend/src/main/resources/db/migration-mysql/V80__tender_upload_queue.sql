create table if not exists tender_file (
    id bigint primary key auto_increment,
    upload_id varchar(64) not null,
    user_id bigint not null,
    file_path varchar(1000) not null,
    file_sha256 varchar(64),
    file_size bigint,
    page_count int,
    upload_status varchar(20) not null default 'INITIATED',
    created_at datetime(6) not null default current_timestamp(6),
    updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint uk_tender_file_upload_id unique (upload_id),
    constraint uk_tender_file_user_sha256 unique (user_id, file_sha256),
    constraint fk_tender_file_user foreign key (user_id) references users(id)
);

create index idx_tender_file_user_status_created on tender_file(user_id, upload_status, created_at);

create table if not exists tender_task (
    id bigint primary key auto_increment,
    file_id bigint not null,
    status varchar(20) not null,
    priority int not null default 5,
    attempts int not null default 0,
    available_at datetime(6) not null default current_timestamp(6),
    locked_by varchar(100),
    locked_at datetime(6),
    error_code varchar(100),
    error_message varchar(1000),
    started_at datetime(6),
    finished_at datetime(6),
    created_at datetime(6) not null default current_timestamp(6),
    updated_at datetime(6) not null default current_timestamp(6) on update current_timestamp(6),
    constraint uk_tender_task_file_id unique (file_id),
    constraint fk_tender_task_file foreign key (file_id) references tender_file(id)
);

create index idx_tender_task_status_available_priority on tender_task(status, available_at, priority, created_at);
create index idx_tender_task_locked_status on tender_task(locked_by, status);

create table if not exists tender_task_dlq (
    id bigint primary key auto_increment,
    task_id bigint not null,
    file_id bigint not null,
    failed_at datetime(6) not null default current_timestamp(6),
    error_code varchar(100),
    error_message varchar(1000),
    payload text,
    created_at datetime(6) not null default current_timestamp(6),
    constraint fk_tender_task_dlq_task foreign key (task_id) references tender_task(id),
    constraint fk_tender_task_dlq_file foreign key (file_id) references tender_file(id)
);

create index idx_tender_task_dlq_failed_at on tender_task_dlq(failed_at);
