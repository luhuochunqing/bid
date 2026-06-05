alter table tenders
    add column source_normalized varchar(200);

alter table tenders
    add column region_normalized varchar(100);

alter table tenders
    add column industry_normalized varchar(100);

alter table tenders
    add column purchaser_hash_normalized varchar(64);

alter table tenders
    add column purchaser_name_normalized varchar(255);

alter table tenders
    add column search_text_normalized text;

update tenders
set source_normalized = lower(trim(coalesce(source, ''))),
    region_normalized = lower(trim(coalesce(region, ''))),
    industry_normalized = lower(trim(coalesce(industry, ''))),
    purchaser_hash_normalized = lower(trim(coalesce(purchaser_hash, ''))),
    purchaser_name_normalized = lower(trim(coalesce(purchaser_name, ''))),
    search_text_normalized = lower(concat(
        coalesce(title, ''), ' ',
        coalesce(description, ''), ' ',
        coalesce(purchaser_name, ''), ' ',
        coalesce(tags, ''), ' ',
        coalesce(region, ''), ' ',
        coalesce(industry, ''), ' ',
        coalesce(source, '')
    ));

create index idx_tender_source_normalized
    on tenders (source_normalized);

create index idx_tender_region_normalized
    on tenders (region_normalized);

create index idx_tender_industry_normalized
    on tenders (industry_normalized);

create index idx_tender_purchaser_hash_normalized
    on tenders (purchaser_hash_normalized);

create index idx_tender_status_region_industry_normalized
    on tenders (status, region_normalized, industry_normalized);
