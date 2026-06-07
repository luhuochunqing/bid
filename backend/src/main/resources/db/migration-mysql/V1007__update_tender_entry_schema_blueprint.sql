-- ================================================================
-- V1007: 更新 tender.entry 动态表单 schema（对齐蓝图 2.3.1 标讯创建字段）
-- 背景：V140 种子数据中 tender.entry schema 已过时，新增蓝图字段并更新版本
-- 说明：使用 UPDATE 而非 ALTER TABLE，仅修改 JSON 字段内容
-- ================================================================

-- ----------------------------------------------------------
-- 1. 验证当前 schema 版本
-- ----------------------------------------------------------
SELECT id, scope, scope_label, version FROM form_definition_registry WHERE scope = 'tender.entry';

-- ----------------------------------------------------------
-- 2. 更新 tender.entry schema 为蓝图对齐版本
-- ----------------------------------------------------------
UPDATE form_definition_registry
SET
    version = 2,
    schema_json = '{
  "fields": [
    {"key": "title", "label": "项目名称", "type": "TEXT", "required": true, "placeholder": "请输入项目名称", "maxLength": 200},
    {"key": "tenderAgency", "label": "招标主体", "type": "TEXT", "required": true, "placeholder": "请输入招标主体", "maxLength": 200},
    {"key": "budget", "label": "预算金额", "type": "CURRENCY", "required": false, "validation": {"min": 0, "precision": 2}},
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
    {"key": "contactName", "label": "联系人1", "type": "TEXT", "required": false, "placeholder": "联系人姓名"},
    {"key": "contactPhone", "label": "联系人1手机号", "type": "TEXT", "required": false, "placeholder": "手机号"},
    {"key": "contactLandline", "label": "联系人1座机", "type": "TEXT", "required": false, "placeholder": "座机（如 010-12345678）"},
    {"key": "contactMail", "label": "联系人1邮箱", "type": "TEXT", "required": false, "placeholder": "邮箱"},
    {"key": "contactName2", "label": "联系人2", "type": "TEXT", "required": false, "placeholder": "联系人姓名（选填）"},
    {"key": "contactPhone2", "label": "联系人2手机号", "type": "TEXT", "required": false, "placeholder": "手机号（选填）"},
    {"key": "contactLandline2", "label": "联系人2座机", "type": "TEXT", "required": false, "placeholder": "座机（选填）"},
    {"key": "contactMail2", "label": "联系人2邮箱", "type": "TEXT", "required": false, "placeholder": "邮箱（选填）"},
    {"key": "customerType", "label": "客户类型", "type": "SELECT", "required": true, "options": [
      {"label": "政府机关", "value": "政府机关"}, {"label": "事业单位", "value": "事业单位"},
      {"label": "高校", "value": "高校"}, {"label": "央企", "value": "央企"},
      {"label": "地方国企", "value": "地方国企"}, {"label": "民企", "value": "民企"},
      {"label": "港澳台及外企", "value": "港澳台及外企"}
    ]},
    {"key": "priority", "label": "优先级", "type": "SELECT", "required": true, "options": [
      {"label": "S 级 · 战略级高价值客户", "value": "S"},
      {"label": "A 级 · 高价值重点客户", "value": "A"},
      {"label": "B 级 · 重要潜力客户", "value": "B"},
      {"label": "C 级 · 潜力客户", "value": "C"}
    ]},
    {"key": "projectType", "label": "项目类型", "type": "SELECT", "required": false, "options": [
      {"label": "工业品", "value": "工业品"}, {"label": "办公", "value": "办公"},
      {"label": "综合", "value": "综合"}, {"label": "集采", "value": "集采"},
      {"label": "其他", "value": "其他"}
    ]},
    {"key": "sourcePlatform", "label": "来源平台", "type": "SELECT", "required": false, "options": [
      {"label": "人工录入", "value": "人工录入"}, {"label": "中国政府采购网", "value": "中国政府采购网"},
      {"label": "各省招标网", "value": "各省招标网"}, {"label": "第三方商机服务", "value": "第三方商机服务"},
      {"label": "企业招标平台", "value": "企业招标平台"}
    ], "defaultValue": "人工录入"},
    {"key": "description", "label": "标讯描述", "type": "TEXTAREA", "required": false, "rows": 3, "maxLength": 5000},
    {"key": "tenderInfo", "label": "标讯信息", "type": "TEXTAREA", "required": false, "rows": 3, "maxLength": 5000},
    {"key": "crmOpportunityId", "label": "CRM商机", "type": "TEXT", "required": false, "placeholder": "点击选择CRM商机（跟踪中状态后必填）"}
  ]
}'
WHERE scope = 'tender.entry' AND org_id IS NULL;
