-- ================================================================
-- V1166: 对齐 tender.entry 动态表单 schema 与 fallback 表单字段
-- 背景：V1007 schema 字段 key (tenderAgency/contactName/contactPhone 等)
--       与业务页 ManualTenderDialog fallback 表单使用的字段 key
--       (purchaser/contact/phone 等) 不一致，导致 DynamicFormRenderer
--       渲染时数据无法正确映射到 buildManualTenderPayload，造成
--       招标主体/联系人等关键字段数据丢失。
-- 修复方向：业务页 fallback → DB schema（业务页是字段真相源）
-- 主要变更：
--   1. 字段 key 改名对齐 fallback form.xxx：
--      tenderAgency    → purchaser
--      contactName     → contact
--      contactPhone    → phone
--      contactLandline → landline
--      contactMail     → mail
--      contactName2    → contact2
--      contactPhone2   → phone2
--      contactLandline2→ landline2
--      contactMail2    → mail2
--   2. customerType 选项：7 个 → 5 个（政府机关/事业单位/高校 合并为一项）
--   3. projectType required：false → true（对齐 fallback MANUAL_FORM_RULES）
--   4. tenderInfo maxLength：5000 → 20000（对齐 fallback 校验规则）
--   5. 删除 sourcePlatform（payload 中 source 硬编码为 MANUAL_SOURCE_LABEL）
--   6. 删除 budget（fallback 表单无此字段，payload 不读 form.budget）
--   7. 删除 crmOpportunityId（fallback 表单无 UI，payload 不读此字段）
-- 说明：使用 UPDATE 而非 ALTER TABLE，仅修改 JSON 字段内容
-- 回滚：U1166__align_tender_entry_schema_with_fallback.sql 恢复 V1007 状态
-- ================================================================

-- ----------------------------------------------------------
-- 1. 验证当前 schema 版本（预期 version=2，V1007 设置）
-- ----------------------------------------------------------
SELECT id, scope, scope_label, version FROM form_definition_registry WHERE scope = 'tender.entry';

-- ----------------------------------------------------------
-- 2. 更新 tender.entry schema 对齐 fallback 表单字段
-- ----------------------------------------------------------
UPDATE form_definition_registry
SET
    version = 3,
    schema_json = '{
  "fields": [
    {"key": "title", "label": "项目名称", "type": "TEXT", "required": true, "placeholder": "请输入项目名称", "maxLength": 200},
    {"key": "purchaser", "label": "招标主体", "type": "TEXT", "required": true, "placeholder": "请输入招标主体", "maxLength": 200},
    {"key": "region", "label": "总部所在地", "type": "SELECT", "required": true, "options": [
      {"label": "北京", "value": "北京"}, {"label": "天津", "value": "天津"}, {"label": "河北", "value": "河北"},
      {"label": "山西", "value": "山西"}, {"label": "内蒙古", "value": "内蒙古"}, {"label": "辽宁", "value": "辽宁"},
      {"label": "吉林", "value": "吉林"}, {"label": "黑龙江", "value": "黑龙江"}, {"label": "上海", "value": "上海"},
      {"label": "江苏", "value": "江苏"}, {"label": "浙江", "value": "浙江"}, {"label": "安徽", "value": "安徽"},
      {"label": "福建", "value": "福建"}, {"label": "江西", "value": "江西"}, {"label": "山东", "value": "山东"},
      {"label": "河南", "value": "河南"}, {"label": "湖北", "value": "湖北"}, {"label": "湖南", "value": "湖南"},
      {"label": "广东", "value": "广东"}, {"label": "广西", "value": "广西"}, {"label": "海南", "value": "海南"},
      {"label": "重庆", "value": "重庆"}, {"label": "四川", "value": "四川"}, {"label": "贵州", "value": "贵州"},
      {"label": "云南", "value": "云南"}, {"label": "西藏", "value": "西藏"}, {"label": "陕西", "value": "陕西"},
      {"label": "甘肃", "value": "甘肃"}, {"label": "青海", "value": "青海"}, {"label": "宁夏", "value": "宁夏"},
      {"label": "新疆", "value": "新疆"}, {"label": "台湾", "value": "台湾"}, {"label": "香港", "value": "香港"},
      {"label": "澳门", "value": "澳门"}
    ]},
    {"key": "deadline", "label": "报名截止时间", "type": "DATETIME", "required": true},
    {"key": "bidOpeningTime", "label": "开标时间", "type": "DATETIME", "required": true},
    {"key": "customerType", "label": "客户类型", "type": "SELECT", "required": true, "options": [
      {"label": "政府机关/事业单位/高校", "value": "政府机关/事业单位/高校"},
      {"label": "央企", "value": "央企"},
      {"label": "地方国企", "value": "地方国企"},
      {"label": "民企", "value": "民企"},
      {"label": "港澳台及外企", "value": "港澳台及外企"}
    ]},
    {"key": "priority", "label": "优先级", "type": "SELECT", "required": true, "options": [
      {"label": "S 级 · 战略级高价值客户", "value": "S"},
      {"label": "A 级 · 高价值重点客户", "value": "A"},
      {"label": "B 级 · 重要潜力客户", "value": "B"},
      {"label": "C 级 · 潜力客户", "value": "C"}
    ]},
    {"key": "projectType", "label": "项目类型", "type": "SELECT", "required": true, "options": [
      {"label": "工业品", "value": "工业品"}, {"label": "办公", "value": "办公"},
      {"label": "综合", "value": "综合"}, {"label": "集采", "value": "集采"},
      {"label": "其他", "value": "其他"}
    ]},
    {"key": "contact", "label": "联系人1", "type": "TEXT", "required": false, "placeholder": "联系人姓名"},
    {"key": "phone", "label": "联系人1手机号", "type": "TEXT", "required": false, "placeholder": "手机号"},
    {"key": "landline", "label": "联系人1座机", "type": "TEXT", "required": false, "placeholder": "座机（如 010-12345678）"},
    {"key": "mail", "label": "联系人1邮箱", "type": "TEXT", "required": false, "placeholder": "邮箱"},
    {"key": "contact2", "label": "联系人2", "type": "TEXT", "required": false, "placeholder": "联系人姓名（选填）"},
    {"key": "phone2", "label": "联系人2手机号", "type": "TEXT", "required": false, "placeholder": "手机号（选填）"},
    {"key": "landline2", "label": "联系人2座机", "type": "TEXT", "required": false, "placeholder": "座机（选填）"},
    {"key": "mail2", "label": "联系人2邮箱", "type": "TEXT", "required": false, "placeholder": "邮箱（选填）"},
    {"key": "description", "label": "标讯描述", "type": "TEXTAREA", "required": false, "rows": 3, "maxLength": 5000},
    {"key": "tenderInfo", "label": "标讯信息", "type": "TEXTAREA", "required": false, "rows": 3, "maxLength": 20000}
  ]
}'
WHERE scope = 'tender.entry' AND org_id IS NULL;
