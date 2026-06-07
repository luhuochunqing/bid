create table if not exists bid_match_scoring_models (
    id bigint not null auto_increment,
    name varchar(200) not null,
    description varchar(1000),
    status varchar(30) not null,
    draft_revision bigint not null default 1,
    model_json text not null,
    active_version_id bigint,
    active_version_no integer,
    created_at datetime(6) not null default current_timestamp(6),
    updated_at datetime(6) not null default current_timestamp(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_bid_match_model_status
    on bid_match_scoring_models (status);

create table if not exists bid_match_model_versions (
    id bigint not null auto_increment,
    model_id bigint not null,
    version_no integer not null,
    snapshot_json text not null,
    active_flag boolean not null default false,
    activated_at datetime(6) not null,
    activated_by varchar(100),
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_bid_match_version_model
    on bid_match_model_versions (model_id);

create index idx_bid_match_version_active
    on bid_match_model_versions (active_flag);

create unique index uk_bid_match_version_model_no
    on bid_match_model_versions (model_id, version_no);

create table if not exists bid_match_score_evaluations (
    id bigint not null auto_increment,
    tender_id bigint not null,
    model_id bigint not null,
    model_version_id bigint not null,
    model_version_no integer not null,
    total_score decimal(7, 2) not null,
    dimension_scores_json text not null,
    evidence_json text not null,
    evidence_fingerprint varchar(128) not null,
    model_snapshot_json text not null,
    evaluated_by varchar(100),
    evaluated_at datetime(6) not null default current_timestamp(6),
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_bid_match_eval_tender
    on bid_match_score_evaluations (tender_id);

create index idx_bid_match_eval_version
    on bid_match_score_evaluations (model_version_id);

create index idx_bid_match_eval_time
    on bid_match_score_evaluations (evaluated_at);
