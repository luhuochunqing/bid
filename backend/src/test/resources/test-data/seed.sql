-- ============================================================
-- 西域投标平台 — 全状态覆盖测试数据补充
-- 
-- 用途：在 demo-data.sql 已导入的基础上，补充项目各阶段所需
-- 的 project_result（结果确认）、project_closure（结项）等
-- 关联数据，覆盖所有状态组合 UI 差异，方便对账测试。
--
-- 导入方式（在 demo-data.sql 之后执行）：
--   docker exec -i xiyu-bid-local-mysql mysql -u xiyu_user -pXiyuDB!2026 \
--     --default-character-set=utf8mb4 xiyu_bid_codex < test-data-seed.sql
--   docker exec -i xiyu-bid-local-mysql mysql -u xiyu_user -pXiyuDB!2026 \
--     --default-character-set=utf8mb4 xiyu_bid_main < test-data-seed.sql
-- ============================================================

-- ====================================================================
-- 一、项目-标讯关联修复
-- ====================================================================
-- demo-data.sql 中 tenders(1-9) 的 project_id 列均为 NULL，
-- 但 projects 表已通过 tender_id 指向对应 tenders。
-- 如果后续需要 tender→project 反向查找，执行以下更新。
-- （默认不启用，因为 tender 的 project_id 是由项目创建时写入的，
--   仅标讯列表的"已关联项目"标识需要此列。）
-- UPDATE tenders t
--   JOIN projects p ON p.tender_id = t.id
--   SET t.project_id = p.id
--   WHERE t.project_id IS NULL;

-- ====================================================================
-- 二、项目立项补充（使现有项目可推进到下一阶段）
-- ====================================================================
-- 项目 10（智慧园区MRO，stage=DRAFTING）→ needDeposit=YES，已有立项数据
-- 项目 11（华南电力，stage=EVALUATING）→ needDeposit=NO，已有立项数据
-- 项目 12（西部云数据中心，stage=RESULT_PENDING）→ needDeposit=YES
-- 项目 13（MES系统，stage=RESULT_PENDING）→ needDeposit=NO
-- 项目 14（省政府框架协议，stage=CLOSED）→ needDeposit=NO，已中标
-- 项目 15（轨道交通，stage=CLOSED）→ needDeposit=YES，未中标
-- 项目 16（新能源汽车，stage=CLOSED）→ needDeposit=YES，已放弃
-- 项目 17（HIS系统，stage=INITIATED）→ needDeposit=YES
-- 项目 18（智慧城市，stage=RESULT_PENDING）→ needDeposit=NO

-- ====================================================================
-- 三、project_result 结果确认数据（覆盖 4 种结果类型）
-- ====================================================================
-- 注意：project_result 与 project 是 1:1 关系（UK约束），
-- 已存在旧数据的项目不可重复插入；我们用 REPLACE / ON DUPLICATE KEY UPDATE。

-- 3.1 项目 12（西部云数据中心，stage=RESULT_PENDING）→ 中标（WON）
-- 业务背景：西部云计算数据中心项目，已出评标结果，等待复盘
INSERT INTO project_result (project_id, result_type, award_amount, contract_start_date, contract_end_date, evidence_doc_ids, summary, registered_at, created_at, updated_at)
VALUES (12, 'WON', 11800000.00, '2026-07-01', '2028-06-30', '[]', '评标结果：我方综合评分第一，成功中标。中标金额1180万元，合同期2年。', NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE result_type=VALUES(result_type), award_amount=VALUES(award_amount), contract_start_date=VALUES(contract_start_date), contract_end_date=VALUES(contract_end_date), summary=VALUES(summary);

-- 3.2 项目 13（MES制造执行系统，stage=RESULT_PENDING）→ 未中标（LOST）
-- 业务背景：比亚迪MES系统采购，价格评分不占优势
INSERT INTO project_result (project_id, result_type, award_amount, evidence_doc_ids, summary, registered_at, created_at, updated_at)
VALUES (13, 'LOST', NULL, '[]', '评标结果：技术方案评分接近，但价格不占优势，最终排名第二。', NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE result_type=VALUES(result_type), summary=VALUES(summary);

-- 3.3 项目 14（省政府框架协议，stage=CLOSED）→ 中标（WON）— 已有，用已有数据
-- 项目 14 已在 project_retrospective 中有 WON 记录，需要补齐 project_result
INSERT INTO project_result (project_id, result_type, award_amount, contract_start_date, contract_end_date, evidence_doc_ids, summary, registered_at, created_at, updated_at)
VALUES (14, 'WON', 950000.00, '2026-05-01', '2027-04-30', '[]', '框架协议项目，成功入围，金额95万元。已结项。', NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE result_type=VALUES(result_type);

-- 3.4 项目 15（轨道交通信号系统，stage=CLOSED）→ 已流标（FAILED）
-- 业务背景：武汉地铁CBTC升级，投标供应商不足3家流标
INSERT INTO project_result (project_id, result_type, evidence_doc_ids, summary, registered_at, created_at, updated_at)
VALUES (15, 'FAILED', '[]', '开标时投标供应商不足3家，依法流标。已办理保证金退回。', NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE result_type=VALUES(result_type), summary=VALUES(summary);

-- 3.5 项目 16（新能源汽车零部件，stage=CLOSED）→ 弃标（ABANDONED）
-- 已在 project_retrospective 中有 ABANDONED 记录
INSERT INTO project_result (project_id, result_type, evidence_doc_ids, summary, registered_at, created_at, updated_at)
VALUES (16, 'ABANDONED', '[]', '供应商资质审核阶段，经评估我方不满足技术指标要求，主动放弃投标。', NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE result_type=VALUES(result_type), summary=VALUES(summary);

-- 3.6 补充：项目 18（智慧城市物联网平台，stage=RESULT_PENDING）
-- 项目 18 当前是 RESULT_PENDING 状态但没有结果数据。做 未中标（LOST）覆盖
INSERT INTO project_result (project_id, result_type, evidence_doc_ids, summary, registered_at, created_at, updated_at)
VALUES (18, 'LOST', '[]', '综合评分排名第三，未入围。竞争对手价格优势明显。', NOW(), NOW(), NOW())
ON DUPLICATE KEY UPDATE result_type=VALUES(result_type), summary=VALUES(summary);

-- ====================================================================
-- 四、project_result_competitor 竞争对手明细（V1036 表）
-- ====================================================================
-- （仅当结果确认时填写了竞争对手信息才需要；demo 数据目前不强制要求）

-- ====================================================================
-- 五、project_closure 结项数据（覆盖 5 种保证金状态 × 3 种审核状态）
-- ====================================================================
-- 结项表是 1:1 关系（UK约束），每个项目最多一条。
-- 现状：已有数据中，项目14/15/16 stage=CLOSED，但可能没有 closure 行。

-- 5.1 项目 14（省政府框架协议，stage=CLOSED）
-- → hasDeposit=NO，无需处理保证金；reviewStatus=APPROVED，已结项
INSERT INTO project_closure (project_id, deposit_return_status, deposit_return_date, deposit_return_evidence_id, transfer_amount, returned_amount, archive_location, stage_locked, review_status, reviewed_by, reviewed_at, project_summary, closed_at, closed_by, created_at, updated_at)
VALUES (14, 'NA', NULL, NULL, NULL, NULL, '档案柜A-2026-杭州-框架协议-01', TRUE, 'APPROVED', 3, NOW(), '浙江省省级单位办公用品框架协议，中标金额95万元，已履约完成。无保证金。', NOW(), 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  review_status=VALUES(review_status),
  project_summary=VALUES(project_summary),
  stage_locked=VALUES(stage_locked);

-- 5.2 项目 15（轨道交通信号系统，stage=CLOSED）
-- → hasDeposit=YES，保证金未退回（NOT_RETURNED），审核已驳回需处理
-- reviewStatus=REJECTED（可以让测试看到驳回状态和驳回原因的UI）
INSERT INTO project_closure (project_id, deposit_return_status, deposit_return_date, deposit_return_evidence_id, transfer_amount, returned_amount, archive_location, stage_locked, review_status, reviewed_by, reviewed_at, project_summary, rejection_reason, closed_at, closed_by, created_at, updated_at)
VALUES (15, 'NOT_RETURNED', NULL, NULL, NULL, NULL, '档案柜B-2026-武汉-轨道交通-02', TRUE, 'REJECTED', 3, NOW(), '武汉地铁信号系统CBTC升级改造，因投标供应商不足3家流标。', '保证金¥170,000.00未退回，请确认保证金退回状态后再提交结项。', NOW(), 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  review_status=VALUES(review_status),
  rejection_reason=VALUES(rejection_reason),
  stage_locked=VALUES(stage_locked);

-- 5.3 项目 16（新能源汽车零部件，stage=CLOSED）
-- → hasDeposit=YES，保证金"全部退回"（FULLY_RETURNED），审核通过
INSERT INTO project_closure (project_id, deposit_return_status, deposit_return_date, deposit_return_evidence_id, transfer_amount, returned_amount, archive_location, stage_locked, review_status, reviewed_by, reviewed_at, project_summary, closed_at, closed_by, created_at, updated_at)
VALUES (16, 'FULLY_RETURNED', '2026-06-02 15:00:00', 99901, NULL, NULL, '档案柜C-2026-合肥-新能源汽车-03', TRUE, 'APPROVED', 3, NOW(), '蔚来汽车新能源汽车零部件采购项目，因资质不达标主动放弃。保证金已全额退回。', NOW(), 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  deposit_return_date=VALUES(deposit_return_date),
  deposit_return_evidence_id=VALUES(deposit_return_evidence_id),
  review_status=VALUES(review_status);

-- ====================================================================
-- 六、新增 5 个测试项目，覆盖结项页面全部保证金变体
-- ====================================================================
-- 为了覆盖结项页面各种保证金状态组合，我们在现有9个项目基础上，
-- 再创建5个已完成的项目（stage=CLOSED），每个有不同的保证金处理状态。

-- 6.1 先补标讯（tenders）：标讯20-24
INSERT INTO tenders (id, title, source, status, region, industry, purchaser_name, customer_type, priority, source_type, budget, deadline, publish_date, created_at, updated_at, description)
VALUES
(20, '华东政务云安全加固项目', '上海政府采购网', 'WON', '上海', '政务云', '上海市大数据中心', '政府', 'A', 'EXTERNAL_PLATFORM', 4500000.00, '2026-05-20 09:00:00', '2026-03-01', NOW(), NOW(), '政务云平台安全加固及等保测评服务'),
(21, '西南医院信息化二期项目', '四川招标投标网', 'LOST', '成都', '医疗', '华西医院', '事业单位', 'A', 'EXTERNAL_PLATFORM', 2800000.00, '2026-04-10 10:00:00', '2026-02-15', NOW(), NOW(), 'HIS/EMR系统升级及数据中心建设'),
(22, '华东电网调度系统改造', '国家电网采购平台', 'ABANDONED', '南京', '电力能源', '国家电网江苏省公司', '央企', 'S', 'EXTERNAL_PLATFORM', 9200000.00, '2026-03-15 14:00:00', '2026-01-10', NOW(), NOW(), '调度自动化系统升级改造'),
(23, '华南机场安检设备采购', '民航专业工程招标网', 'WON', '广州', '交通', '广州白云国际机场', '国企', 'A', 'EXTERNAL_PLATFORM', 6800000.00, '2026-06-01 09:00:00', '2026-03-20', NOW(), NOW(), '安检通道设备更新及智能安检系统'),
(24, '西北高校智慧教室项目', '陕西省政府采购网', 'ABANDONED', '西安', '教育', '西安交通大学', '事业单位', 'B', 'EXTERNAL_PLATFORM', 1650000.00, '2026-04-20 10:00:00', '2026-02-01', NOW(), NOW(), '智慧教室多媒体设备及教学平台采购');

-- 6.2 创建关联项目：projects 20-24
INSERT INTO projects (id, name, tender_id, manager_id, status, stage, customer_type, industry, region, customer, budget, description, start_date, end_date, created_at, updated_at, initiated_at, evaluating_at, closed_at)
VALUES
(20, '华东政务云安全加固项目', 20, 3, 'WON', 'CLOSED', '政府', '政务云', '上海', '上海市大数据中心', 4500000.00, '政务云平台安全加固及等保测评服务', '2026-04-01 00:00:00', '2026-06-30 00:00:00', NOW(), NOW(), '2026-04-01 00:00:00', '2026-05-01 00:00:00', NOW()),
(21, '西南医院信息化二期项目', 21, 5, 'LOST', 'CLOSED', '事业单位', '医疗', '成都', '华西医院', 2800000.00, 'HIS/EMR系统升级及数据中心建设', '2026-03-01 00:00:00', '2026-05-30 00:00:00', NOW(), NOW(), '2026-03-01 00:00:00', '2026-04-01 00:00:00', NOW()),
(22, '华东电网调度系统改造', 22, 6, 'ABANDONED', 'CLOSED', '央企', '电力能源', '南京', '国家电网江苏省公司', 9200000.00, '调度自动化系统升级改造', '2026-02-01 00:00:00', '2026-04-30 00:00:00', NOW(), NOW(), '2026-02-01 00:00:00', '2026-03-01 00:00:00', NOW()),
(23, '华南机场安检设备采购', 23, 3, 'WON', 'CLOSED', '国企', '交通', '广州', '广州白云国际机场', 6800000.00, '安检通道设备更新及智能安检系统', '2026-04-15 00:00:00', '2026-07-31 00:00:00', NOW(), NOW(), '2026-04-15 00:00:00', '2026-05-15 00:00:00', NOW()),
(24, '西北高校智慧教室项目', 24, 5, 'ABANDONED', 'CLOSED', '事业单位', '教育', '西安', '西安交通大学', 1650000.00, '智慧教室多媒体设备及教学平台采购', '2026-03-01 00:00:00', '2026-05-31 00:00:00', NOW(), NOW(), '2026-03-01 00:00:00', '2026-04-01 00:00:00', NOW());

-- 6.3 为新增5个项目补充立项详情（必须含 need_deposit，结项页面据此判断 hasDeposit）
INSERT INTO project_initiation_details (project_id, customer_type, bid_open_time, bid_month, owner_unit, project_type, bid_status, bidding_leader_name, bidding_platform, project_leader_name, leader_department, need_deposit, deposit_amount, deposit_payment_method, review_status, locked, created_at, updated_at)
VALUES
(20, '政府', '2026-05-20 09:30:00', '2026-05', '上海市大数据中心', '系统集成', 'WON', '李主管', '上海政府采购网', '张经理', '华东事业部', 'NO', 0, '', 'APPROVED', 1, NOW(), NOW()),
(21, '事业单位', '2026-04-10 10:30:00', '2026-04', '华西医院', '信息化', 'LOST', '陈主管', '四川招标投标网', '刘经理', '西部事业部', 'YES', 56000.00, 'WIRE', 'APPROVED', 1, NOW(), NOW()),
(22, '央企', '2026-03-15 14:30:00', '2026-03', '国家电网江苏省公司', '技术改造', 'ABANDONED', '周主管', '国家电网采购平台', '陈经理', '华东事业部', 'YES', 184000.00, 'GUARANTEE', 'APPROVED', 1, NOW(), NOW()),
(23, '国企', '2026-06-01 09:30:00', '2026-06', '广州白云国际机场', '设备采购', 'WON', '赵主管', '民航专业工程招标网', '王经理', '华南事业部', 'YES', 136000.00, 'WIRE', 'APPROVED', 1, NOW(), NOW()),
(24, '事业单位', '2026-04-20 10:30:00', '2026-04', '西安交通大学', '信息化', 'FAILED', '李主管', '陕西省政府采购网', '张经理', '西北事业部', 'NO', 0, '', 'APPROVED', 1, NOW(), NOW());

-- 6.4 project_result（5个新项目的结果确认）
INSERT INTO project_result (project_id, result_type, award_amount, contract_start_date, contract_end_date, evidence_doc_ids, summary, registered_at, created_at, updated_at)
VALUES
(20, 'WON', 4350000.00, '2026-06-01', '2026-12-31', '[]', '政务云安全加固项目，中标金额435万元。合同期7个月。', NOW(), NOW(), NOW()),
(21, 'LOST', NULL, NULL, NULL, '[]', '排名第二，有相关案例不足，技术评分略低导致失标。', NOW(), NOW(), NOW()),
(22, 'ABANDONED', NULL, NULL, NULL, '[]', '因电网调度系统技术方案变更，我方核心产品不匹配新需求，主动放弃投标。', NOW(), NOW(), NOW()),
(23, 'WON', 6500000.00, '2026-06-15', '2028-06-14', '[]', '安检设备更新项目，中标金额650万元，合同期2年。', NOW(), NOW(), NOW()),
(24, 'FAILED', NULL, NULL, NULL, '[]', '报名供应商不足3家，招标失败。已通知采购人重新招标。', NOW(), NOW(), NOW());

-- ====================================================================
-- 七、结项数据（覆盖 5 种保证金状态 × 审核状态组合）
-- ====================================================================
-- ★★★ 核心测试矩阵 ★★★

-- 7.1 项目 20（政务云安全加固，hasDeposit=NO）→ depositReturnStatus=NA
-- → 结项页：无保证金，不显示任何保证金字段，可直接结项
INSERT INTO project_closure (project_id, deposit_return_status, stage_locked, review_status, reviewed_by, reviewed_at, project_summary, closed_at, closed_by, created_at, updated_at)
VALUES (20, 'NA', TRUE, 'APPROVED', 3, NOW(), '政务云安全加固项目已完工验收，不需要保证金。总结：项目顺利交付。', NOW(), 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  review_status=VALUES(review_status),
  stage_locked=VALUES(stage_locked);

-- 7.2 项目 11（华南电力集采，stage=EVALUATING，needDeposit=NO）
-- → 为了结项测试，补充该项目的结项预览支持。但项目 11 stage=EVALUATING，
-- 当前还不能结项。这里仅为项目21（LOST, 有保证金）做结项：
--
-- 项目 21（西南医院信息化，hasDeposit=YES，有保证金）
-- → depositReturnStatus=TRANSFERRED_TO_FEE（转平台服务费）
-- → reviewStatus=APPROVED，已结项
INSERT INTO project_closure (project_id, deposit_return_status, deposit_return_date, deposit_return_evidence_id, transfer_amount, returned_amount, archive_location, stage_locked, review_status, reviewed_by, reviewed_at, project_summary, closed_at, closed_by, created_at, updated_at)
VALUES (21, 'TRANSFERRED_TO_FEE', NULL, 99902, 56000.00, NULL, '档案柜D-2026-成都-医疗-04', TRUE, 'APPROVED', 3, NOW(), '华西医院信息化二期项目。未中标，保证金¥56,000元已转为平台服务费。', NOW(), 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  deposit_return_evidence_id=VALUES(deposit_return_evidence_id),
  transfer_amount=VALUES(transfer_amount),
  review_status=VALUES(review_status),
  stage_locked=VALUES(stage_locked);

-- 7.3 项目 22（电网调度系统改造，hasDeposit=YES，保险/保函方式）
-- → depositReturnStatus=PARTIAL_RETURN_PARTIAL_TRANSFER（部分退回+部分转服务费）
-- → reviewStatus=APPROVED，已结项
INSERT INTO project_closure (project_id, deposit_return_status, deposit_return_date, deposit_return_evidence_id, transfer_amount, returned_amount, archive_location, stage_locked, review_status, reviewed_by, reviewed_at, project_summary, closed_at, closed_by, created_at, updated_at)
VALUES (22, 'PARTIAL_RETURN_PARTIAL_TRANSFER', '2026-04-25 16:00:00', 99903, 40000.00, 144000.00, '档案柜E-2026-南京-电力-05', TRUE, 'APPROVED', 3, NOW(), '国家电网江苏省公司调度系统改造项目，因技术方案变更弃标。保证金缴纳方式为保险/保函，其中¥40,000元转平台服务费，¥144,000元退回。', NOW(), 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  deposit_return_date=VALUES(deposit_return_date),
  deposit_return_evidence_id=VALUES(deposit_return_evidence_id),
  transfer_amount=VALUES(transfer_amount),
  returned_amount=VALUES(returned_amount),
  review_status=VALUES(review_status),
  stage_locked=VALUES(stage_locked);

-- 7.4 项目 23（华南机场安检设备，hasDeposit=YES，电汇方式）
-- → depositReturnStatus=FULLY_RETURNED（全部退回），有退回日期+凭证
-- → reviewStatus=APPROVED，已结项
INSERT INTO project_closure (project_id, deposit_return_status, deposit_return_date, deposit_return_evidence_id, transfer_amount, returned_amount, archive_location, stage_locked, review_status, reviewed_by, reviewed_at, project_summary, closed_at, closed_by, created_at, updated_at)
VALUES (23, 'FULLY_RETURNED', '2026-07-15 14:00:00', 99904, NULL, NULL, '档案柜F-2026-广州-交通-06', TRUE, 'APPROVED', 3, NOW(), '广州白云国际机场安检设备采购项目，中标金额650万元。保证金¥136,000元已全额退回。合同正在履行中。', NOW(), 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  deposit_return_date=VALUES(deposit_return_date),
  deposit_return_evidence_id=VALUES(deposit_return_evidence_id),
  review_status=VALUES(review_status),
  stage_locked=VALUES(stage_locked);

-- 7.5 项目 24（西北高校智慧教室，hasDeposit=NO）
-- → depositReturnStatus=NA，无保证金
-- → reviewStatus=APPROVED，已结项
INSERT INTO project_closure (project_id, deposit_return_status, stage_locked, review_status, reviewed_by, reviewed_at, project_summary, closed_at, closed_by, created_at, updated_at)
VALUES (24, 'NA', TRUE, 'APPROVED', 3, NOW(), '西安交通大学智慧教室项目，因报名供应商不足流标。不需要保证金。', NOW(), 3, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  review_status=VALUES(review_status),
  stage_locked=VALUES(stage_locked);

-- 7.6 项目 18（智慧城市物联网平台，stage=RESULT_PENDING，needDeposit=NO）
-- → 用于测试"结果确认后待结项/待复盘"的过渡状态
-- → 不插入结项数据——该项目的 stage 仍然是 RESULT_PENDING，
-- 尚未进入 RETROSPECTIVE 或 CLOSED，所以 project_closure 行不存在
-- 是正常情况。结项预览接口应返回 canClose=false + blockingReasons 说明。

-- 7.7 项目 12（西部云数据中心，stage=RESULT_PENDING，needDeposit=YES，已中标）
-- → 已有 project_result（WON），需要插入结项数据演示"保证金未退回无法结项"
-- → depositReturnStatus=NOT_RETURNED，reviewStatus=DRAFT（草稿未提交审核）
INSERT INTO project_closure (project_id, deposit_return_status, stage_locked, review_status, project_summary, created_at, updated_at)
VALUES (12, 'NOT_RETURNED', FALSE, 'DRAFT', '西部云数据中心项目已中标，保证金¥240,000尚未退回，暂不能结项。', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  stage_locked=VALUES(stage_locked),
  review_status=VALUES(review_status);

-- 7.8 项目 13（MES制造执行系统，stage=RESULT_PENDING，needDeposit=NO，未中标）
-- → 已有 project_result（LOST），无保证金，待进入复盘阶段
-- → depositReturnStatus=NA，reviewStatus=DRAFT
INSERT INTO project_closure (project_id, deposit_return_status, stage_locked, review_status, project_summary, created_at, updated_at)
VALUES (13, 'NA', FALSE, 'DRAFT', '比亚迪MES项目，未中标。不需要保证金，可直接进入复盘。', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  deposit_return_status=VALUES(deposit_return_status),
  stage_locked=VALUES(stage_locked),
  review_status=VALUES(review_status);

-- ====================================================================
-- 八、复盘数据补充
-- ====================================================================

-- 8.1 项目 14（省政府框架协议-WON）— 补充复盘详情（中标分析）
UPDATE project_retrospective SET
  win_factors = '1. 公司在政府框架协议领域品牌认知度高；2. 报价合理，服务方案细致；3. 历史履约记录良好。',
  process_highlights = '标书制作规范，响应文件完整，提前2天完成递交。',
  meeting_time = '2026-05-20 14:00:00',
  meeting_type = 'OFFLINE',
  participants = '张经理,李主管,王销售,刘商务',
  review_status = 'APPROVED'
WHERE project_id = 14;

-- 8.2 项目 15（轨道交通信号系统-FAILED→流标）— 补充复盘
UPDATE project_retrospective SET
  process_issues = '1. 招标文件发布到开标时间过短（仅20天），部分潜在供应商来不及准备；2. 资质要求较高，符合条件供应商有限。',
  meeting_time = '2026-04-10 10:00:00',
  meeting_type = 'ONLINE',
  participants = '刘经理,陈主管,王工程师',
  review_status = 'APPROVED'
WHERE project_id = 15;

-- 8.3 项目 16（新能源汽车零部件-ABANDONED→弃标）— 补充复盘
UPDATE project_retrospective SET
  meeting_time = '2026-05-10 15:00:00',
  meeting_type = 'ONLINE',
  participants = '陈经理,周主管,李技术',
  review_status = 'APPROVED'
WHERE project_id = 16;

-- 8.4 为新项目20、21、23创建复盘记录（中标/未中标需复盘）
INSERT INTO project_retrospective (project_id, result_type, summary, win_factors, process_highlights, meeting_time, meeting_type, participants, review_status, created_at, updated_at)
VALUES
(20, 'WON', '政务云安全加固项目中标回顾', '1. 公司在政务云安全领域案例丰富；2. 技术方案针对性强的分高；3. 价格适中。', '方案书编写规范，现场演示效果好。', NOW(), 'OFFLINE', '张经理,李主管,赵技术', 'PENDING_REVIEW', NOW(), NOW()),
(21, 'LOST', '华西医院信息化项目丢标分析', NULL, NULL, NOW(), 'ONLINE', '刘经理,陈主管', 'PENDING_REVIEW', NOW(), NOW()),
(23, 'WON', '白云机场安检设备项目中标总结', '1. 机场行业安检方案经验丰富；2. 产品资质齐全；3. 售后服务承诺有竞争力。', '技术标评分第一，商务谈判顺利。', NOW(), 'OFFLINE', '王经理,赵主管,钱商务', 'APPROVED', NOW(), NOW());

-- 8.5 项目22（ABANDONED→弃标）和项目24（FAILED→流标）不需要复盘
-- 根据PRD，弃标和流标直接进入结项，跳过复盘阶段。

-- ====================================================================
-- 九、fees 表补充（保证金记录，供结项闸门查询）
-- ====================================================================
-- fees 表是项目保证金记录的来源，ProjectDepositSnapshot 从 fees 表
-- 查询 BID_BOND 类型、未 CANCELLED 的记录来判断 hasDeposit。
-- 如果 fees 表没有对应记录，hasDeposit 将返回 false，结项闸门会跳过。

-- 9.1 项目12（西部云数据中心，hasDeposit=YES）
INSERT INTO fees (project_id, fee_type, amount, status, fee_date, created_at, updated_at, remarks)
VALUES (12, 'BID_BOND', 240000.00, 'PAID', '2026-05-15 00:00:00', NOW(), NOW(), '西部云数据中心投标保证金');
-- 9.2 项目15（轨道交通信号系统，hasDeposit=YES）
INSERT INTO fees (project_id, fee_type, amount, status, fee_date, created_at, updated_at, remarks)
VALUES (15, 'BID_BOND', 170000.00, 'PAID', '2026-02-20 00:00:00', NOW(), NOW(), '武汉地铁投标保证金');
-- 9.3 项目16（新能源汽车零部件，hasDeposit=YES — 已退回）
INSERT INTO fees (project_id, fee_type, amount, status, fee_date, return_date, created_at, updated_at, remarks)
VALUES (16, 'BID_BOND', 84000.00, 'RETURNED', '2026-04-10 00:00:00', '2026-06-02 15:00:00', NOW(), NOW(), '蔚来汽车投标保证金（已退回）');
-- 9.4 项目21（西南医院信息化，hasDeposit=YES — 已转服务费）
INSERT INTO fees (project_id, fee_type, amount, status, fee_date, created_at, updated_at, remarks)
VALUES (21, 'BID_BOND', 56000.00, 'PAID', '2026-03-10 00:00:00', NOW(), NOW(), '华西医院投标保证金');
-- 9.5 项目22（电网调度系统改造，hasDeposit=YES — 保险/保函，部分退回）
INSERT INTO fees (project_id, fee_type, amount, status, fee_date, return_date, created_at, updated_at, remarks)
VALUES (22, 'BID_BOND', 184000.00, 'RETURNED', '2026-02-05 00:00:00', '2026-04-25 16:00:00', NOW(), NOW(), '国家电网投标保证金（保函形式，部分退回）');
-- 9.6 项目23（华南机场安检设备，hasDeposit=YES — 已全额退回）
INSERT INTO fees (project_id, fee_type, amount, status, fee_date, return_date, created_at, updated_at, remarks)
VALUES (23, 'BID_BOND', 136000.00, 'RETURNED', '2026-05-01 00:00:00', '2026-07-15 14:00:00', NOW(), NOW(), '白云机场投标保证金（已退回）');
-- ====================================================================
-- 十、验证查询
-- ====================================================================
-- 执行以下查询确认数据完整性：
-- 
-- -- 查看所有项目的阶段和结果
-- SELECT p.id, p.name, p.stage, p.status, pr.result_type, 
--        pc.review_status, pc.deposit_return_status,
--        pid.need_deposit
-- FROM projects p
-- LEFT JOIN project_result pr ON pr.project_id = p.id
-- LEFT JOIN project_closure pc ON pc.project_id = p.id
-- LEFT JOIN project_initiation_details pid ON pid.project_id = p.id
-- ORDER BY p.id;
-- 
-- -- 查看结项保证金状态分布
-- SELECT pc.deposit_return_status, COUNT(*) as cnt
-- FROM project_closure pc
-- GROUP BY pc.deposit_return_status;

