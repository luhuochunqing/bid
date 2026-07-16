-- Input: V1167__add_enabled_field_to_tender_entry_schema.sql
-- ================================================================
-- U1167: 回滚 V1167 — 恢复 tender.entry schema 到 V1166 状态
-- 说明：恢复 version=3 与 V1166 设置的 schema_json（18 个字段，无 enabled
--       字段，无 pastedText / attachments 扩展字段）
-- 警告：回滚后配置页"启用"开关与"粘贴识别"/"标讯文件"字段将不可用，
--       业务页 ManualTenderDialog 仍可正常工作（V1166 字段与 fallback 对齐）
-- ================================================================

-- ----------------------------------------------------------
-- 1. 验证当前 schema 版本（预期 version=4，V1167 设置）
-- ----------------------------------------------------------
SELECT id, scope, scope_label, version FROM form_definition_registry WHERE scope = 'tender.entry';

-- ----------------------------------------------------------
-- 2. 恢复 tender.entry schema 到 V1166 状态
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
