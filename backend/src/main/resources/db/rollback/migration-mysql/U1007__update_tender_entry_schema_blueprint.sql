-- Input: V1007__update_tender_entry_schema_blueprint.sql
-- Rollback for V1007__update_tender_entry_schema_blueprint.sql


UPDATE form_definition_registry
SET
    version = 1,
    schema_json = '{"fields":[{"key":"title","label":"标讯标题","type":"TEXT","required":true,"placeholder":"请输入标讯标题"},{"key":"source","label":"信息来源","type":"SELECT","required":false,"options":[{"label":"招标公告","value":"bidding"},{"label":"比选公告","value":"selection"},{"label":"竞争性谈判","value":"negotiation"},{"label":"单一来源","value":"single_source"},{"label":"其他","value":"other"}]},{"key":"budget","label":"预算金额","type":"CURRENCY","required":false,"validation":{"min":0,"precision":2}},{"key":"region","label":"项目地区","type":"ADDRESS","required":false},{"key":"publishDate","label":"发布日期","type":"DATE","required":false},{"key":"deadline","label":"截止日期","type":"DATE","required":true},{"key":"contactName","label":"联系人","type":"TEXT","required":false},{"key":"contactPhone","label":"联系电话","type":"PHONE","required":false},{"key":"description","label":"标讯描述","type":"TEXTAREA","required":false,"rows":4}]}'
WHERE scope = 'tender.entry' AND org_id IS NULL;
