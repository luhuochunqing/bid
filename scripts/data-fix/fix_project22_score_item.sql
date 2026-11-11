START TRANSACTION;

-- 1. Idempotency check: verify whether 3.2.3(2)-1 already exists
SET @exists_count = (SELECT COUNT(*) FROM score_item WHERE project_id = 22 AND code = '3.2.3(2)-1');

-- If it doesn't exist, shift item_index >= 8 by +1
UPDATE score_item 
SET item_index = item_index + 1 
WHERE project_id = 22 AND item_index >= 8 AND @exists_count = 0;

-- 2. Insert 3.2.3(2)-1 with item_index = 8
INSERT INTO score_item (
    project_id,
    parse_task_id,
    item_index,
    code,
    dim,
    detail,
    weight,
    score_type,
    status_stage1,
    est_score,
    est_basis,
    kb_hit,
    context_note,
    source_text,
    location,
    source_cell_ids,
    weight_evidence,
    extraction_method,
    confidence,
    validation_status,
    parent_score_item_id,
    created_at,
    updated_at,
    source_fact_id,
    active_version_id,
    rule_fingerprint,
    estimate_input_fingerprint,
    estimated_at
)
SELECT 
    22,
    154,
    8,
    '3.2.3(2)-1',
    '针对办公用品的综合服务方案',
    '针对办公用品的\n综合服务方案\n（10分）\n1.综合考察供应商售前服务能力，须提供售前服务方案，包括\n但不限于商品咨询解答、商品上架管理品类及主流品牌覆盖管\n理、商品下架管理、售前库存管理、采购新增需求响应、定制\n品需求响应、商品议价响应、商品竞价响应、促销活动响应、\n供应商服务诚信等；\n2.综合考察供应商售中服务能力，须提供售中服务方案，包括\n但不限于商品销售、售中库存管理、订单运营管理（含供应商\n系统订单状态与商城系统订单状态的一致性管控）、出库配送\n保障（包括但不限于配送物流保障、配送商品与订单的一致\n性、物流异常处理、大件/易碎等特殊商品的物流保障等）等；\n3.综合考察供应商售后服务能力，须提供售后服务方案，包括\n但不限于标配售后安装服务保障、退换货响应、三包服务、热\n线服务、商品开票服务、收款确认、报修响应及上门服务流\n程、售后问题处理周期、普通纠纷解决方案及周期、重大物流\n配送纠纷解决方案及周期、重大质量纠纷解决方案及周期等。\n按照以上方案的完整、合理、可操作性、可实施性情况进行评\n分。\n1.评价为优的，得8-10分；\n2.评价为良的，得6-7分；\n3.评价为中的，得4-5分；\n4.评价为差的，得0-3分。',
    10.00,
    'SUBJECTIVE',
    'PENDING',
    NULL,
    '主观项不计分，待投标文件提交后人工评审确认',
    NULL,
    NULL,
    '针对办公用品的\n综合服务方案\n（10分）\n1.综合考察供应商售前服务能力，须提供售前服务方案，包括\n但不限于商品咨询解答、商品上架管理品类及主流品牌覆盖管\n理、商品下架管理、售前库存管理、采购新增需求响应、定制\n品需求响应、商品议价响应、商品竞价响应、促销活动响应、\n供应商服务诚信等；\n2.综合考察供应商售中服务能力，须提供售中服务方案，包括\n但不限于商品销售、售中库存管理、订单运营管理（含供应商\n系统订单状态与商城系统订单状态的一致性管控）、出库配送\n保障（包括但不限于配送物流保障、配送商品与订单的一致\n性、物流异常处理、大件/易碎等特殊商品的物流保障等）等；\n3.综合考察供应商售后服务能力，须提供售后服务方案，包括\n但不限于标配售后安装服务保障、退换货响应、三包服务、热\n线服务、商品开票服务、收款确认、报修响应及上门服务流\n程、售后问题处理周期、普通纠纷解决方案及周期、重大物流\n配送纠纷解决方案及周期、重大质量纠纷解决方案及周期等。\n按照以上方案的完整、合理、可操作性、可实施性情况进行评\n分。\n1.评价为优的，得8-10分；\n2.评价为良的，得6-7分；\n3.评价为中的，得4-5分；\n4.评价为差的，得0-3分。',
    'MULTIMODAL',
    '[\"doc0-p34-t0010-r8-c3\"]',
    '{\"kind\": \"EXPLICIT_WEIGHT_COLUMN\", \"cellId\": \"doc0-p34-t0010-r8-c3\"}',
    'MULTIMODAL',
    1.0000,
    'AUTO_APPROVED',
    NULL,
    NOW(),
    NOW(),
    'fact-60257cac-447c-34a9-bace-e8ed75da0007',
    NULL,
    NULL,
    NULL,
    NULL
WHERE @exists_count = 0;

-- 3. Update score_parse_fact for consistency
UPDATE score_parse_fact
SET validation_status = 'AUTO_APPROVED',
    dimension = '针对办公用品的综合服务方案',
    source_text = '针对办公用品的\n综合服务方案\n（10分）\n1.综合考察供应商售前服务能力，须提供售前服务方案，包括\n但不限于商品咨询解答、商品上架管理品类及主流品牌覆盖管\n理、商品下架管理、售前库存管理、采购新增需求响应、定制\n品需求响应、商品议价响应、商品竞价响应、促销活动响应、\n供应商服务诚信等；\n2.综合考察供应商售中服务能力，须提供售中服务方案，包括\n但不限于商品销售、售中库存管理、订单运营管理（含供应商\n系统订单状态与商城系统订单状态的一致性管控）、出库配送\n保障（包括但不限于配送物流保障、配送商品与订单的一致\n性、物流异常处理、大件/易碎等特殊商品的物流保障等）等；\n3.综合考察供应商售后服务能力，须提供售后服务方案，包括\n但不限于标配售后安装服务保障、退换货响应、三包服务、热\n线服务、商品开票服务、收款确认、报修响应及上门服务流\n程、售后问题处理周期、普通纠纷解决方案及周期、重大物流\n配送纠纷解决方案及周期、重大质量纠纷解决方案及周期等。\n按照以上方案的完整、合理、可操作性、可实施性情况进行评\n分。\n1.评价为优的，得8-10分；\n2.评价为良的，得6-7分；\n3.评价为中的，得4-5分；\n4.评价为差的，得0-3分。',
    updated_at = NOW()
WHERE id = 1591 AND parse_task_id = 154;

-- 4. Update score_parse_task run_summary for consistency
UPDATE score_parse_task
SET run_summary = REPLACE(run_summary, '\"items\":12,\"finalTotalWeight\":90.0', '\"items\":13,\"finalTotalWeight\":100.0'),
    updated_at = NOW()
WHERE id = 154 AND project_id = 22;

COMMIT;
