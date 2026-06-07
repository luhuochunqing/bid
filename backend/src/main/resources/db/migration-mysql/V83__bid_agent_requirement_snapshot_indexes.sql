create index idx_bid_tender_doc_snap_project_created
    on bid_tender_document_snapshots (project_id, created_at desc, id desc);

create index idx_bid_requirement_items_project_document_created
    on bid_requirement_items (project_id, project_document_id, created_at desc);
