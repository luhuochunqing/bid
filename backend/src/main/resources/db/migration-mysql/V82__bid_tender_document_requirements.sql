create table if not exists bid_tender_document_snapshots (
    id bigint not null auto_increment,
    project_id bigint not null,
    tender_id bigint not null,
    project_document_id bigint not null,
    file_name varchar(500) not null,
    content_type varchar(120),
    file_url varchar(1000),
    storage_path varchar(1000),
    content_sha256 varchar(64),
    extracted_text text not null,
    profile_json text not null,
    extractor_key varchar(100) not null,
    analyzer_key varchar(100) not null,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_bid_tender_doc_snap_project
    on bid_tender_document_snapshots (project_id);

create index idx_bid_tender_doc_snap_tender
    on bid_tender_document_snapshots (tender_id);

create index idx_bid_tender_doc_snap_document
    on bid_tender_document_snapshots (project_document_id);

create table if not exists bid_requirement_items (
    id bigint not null auto_increment,
    project_id bigint not null,
    tender_id bigint not null,
    project_document_id bigint not null,
    category varchar(60) not null,
    title varchar(500) not null,
    content text,
    source_excerpt text,
    mandatory boolean not null default false,
    confidence integer,
    created_at datetime(6) not null default current_timestamp(6),
    primary key (id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create index idx_bid_requirement_items_project
    on bid_requirement_items (project_id);

create index idx_bid_requirement_items_tender
    on bid_requirement_items (tender_id);

create index idx_bid_requirement_items_document
    on bid_requirement_items (project_document_id);
