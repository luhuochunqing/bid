create table if not exists bid_agent_runs (
    id bigint not null auto_increment,
    project_id bigint not null,
    tender_id bigint not null,
    project_name varchar(500) not null,
    tender_title varchar(500) not null,
    status enum ('DRAFTED','REVIEWED','READY_FOR_WRITER') not null default 'DRAFTED',
    snapshot_json text not null,
    requirement_classification_json text not null,
    material_match_score_json text not null,
    gap_check_json text not null,
    manual_confirmation_json text not null,
    write_coverage_json text not null,
    draft_text text not null,
    review_text text,
    generator_key varchar(100) not null,
    reviewed_at datetime(6),
    applied_at datetime(6),
    created_at datetime(6) not null default current_timestamp(6),
    updated_at datetime(6) not null default current_timestamp(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_bid_agent_runs_project
    on bid_agent_runs (project_id);

create index idx_bid_agent_runs_tender
    on bid_agent_runs (tender_id);

create index idx_bid_agent_runs_status
    on bid_agent_runs (status);

create table if not exists bid_agent_artifacts (
    id bigint not null auto_increment,
    run_id bigint not null,
    project_id bigint not null,
    artifact_type varchar(60) not null,
    title varchar(255) not null,
    content text not null,
    handoff_target varchar(100),
    status enum ('DRAFTED','READY_FOR_WRITER') not null default 'DRAFTED',
    applied_at datetime(6),
    created_at datetime(6) not null default current_timestamp(6),
    updated_at datetime(6) not null default current_timestamp(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_bid_agent_artifacts_run
    on bid_agent_artifacts (run_id);

create index idx_bid_agent_artifacts_type
    on bid_agent_artifacts (artifact_type);

create index idx_bid_agent_artifacts_status
    on bid_agent_artifacts (status);
