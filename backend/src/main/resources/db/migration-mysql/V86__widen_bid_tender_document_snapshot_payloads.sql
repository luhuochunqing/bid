alter table bid_tender_document_snapshots
    modify column extracted_text longtext not null,
    modify column profile_json longtext not null;
