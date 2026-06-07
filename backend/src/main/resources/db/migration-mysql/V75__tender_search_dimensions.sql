alter table tenders
    add column region varchar(100);

alter table tenders
    add column industry varchar(100);

alter table tenders
    add column purchaser_name varchar(255);

alter table tenders
    add column purchaser_hash varchar(64);

alter table tenders
    add column publish_date date;

alter table tenders
    add column contact_name varchar(100);

alter table tenders
    add column contact_phone varchar(50);

alter table tenders
    add column description text;

alter table tenders
    add column tags text;

create index idx_tender_region
    on tenders (region);

create index idx_tender_industry
    on tenders (industry);

create index idx_tender_purchaser_hash
    on tenders (purchaser_hash);

create index idx_tender_status_region_industry
    on tenders (status, region, industry);
