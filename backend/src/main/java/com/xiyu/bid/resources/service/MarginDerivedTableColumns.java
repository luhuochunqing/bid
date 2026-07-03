package com.xiyu.bid.resources.service;

/**
 * 派生表列契约（防 Sentry JAVA-C / issue 7589082793 复发）。
 *
 * <p>三个 base 方法（{@link MarginQuerySupport#summaryBase} /
 * {@link MarginQuerySupport#listBase} / {@link MarginQuerySupport#countBase}）
 * 各自构造派生表 m，但 {@link MarginQuerySupport#appendFilters} 会往外层
 * WHERE 拼接 m.status / m.exp_return_date / m.payment_date / m.project_name /
 * m.owner_unit / m.project_leader_name / m.bidding_leader_name 等条件引用。
 *
 * <p>之前三个方法各自手写 SELECT 列表，靠人肉保持一致，导致 countBase
 * 漏 SELECT status / exp_return_date 等列，触发 MySQL
 * "Unknown column 'm.status' in 'where clause'"（Sentry JAVA-C）。
 *
 * <p>现在把派生表的两个 UNION ALL 分支的 SELECT 列抽成共享常量，
 * 加列时一处改、三个方法（或至少 countBase 与 listBase）同步生效。
 *
 * <p>列对齐规则：
 * <ul>
 *   <li>countBase 只需要 filter 引用的列 + fee_id（COUNT(*) 不需要其他列）</li>
 *   <li>listBase 需要完整列（给 row mapping 用）</li>
 *   <li>summaryBase 只用 amount/status/exp_return_date 三列（独立）</li>
 * </ul>
 * 为避免 listBase 与 countBase 列契约漂移，countBase 复用 listBase 的
 * 派生表 SELECT 列定义（多出的列不影响 COUNT(*) 性能，但保证对齐）。
 */
final class MarginDerivedTableColumns {

    /** 派生表 fees 分支 SELECT 列（listBase + countBase 共用）。
     *  CO-490: 缴纳日期/收款方/收款账号/应退日期 → 保证金缴纳任务 JSON
     *          退回金额/转服务费金额/退回日期 → project_closure 结项表 */
    static final String DERIVED_SELECT_FEES =
            "   SELECT f.id as fee_id, f.project_id, p.name as project_name,"
          + "     pid.owner_unit,"
          + "     COALESCE(pid.project_leader_name, t.project_manager_name)"
          + "       as project_leader_name,"
          + "     COALESCE(pid.bidding_leader_name, t.bidding_person_name)"
          + "       as bidding_leader_name,"
          + "     f.amount,"
          + "     COALESCE(CAST(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json, '$.actualPaymentDate')), 1, 10) AS DATETIME), f.payment_date) as payment_date,"
          + "     pid.deposit_payment_method,"
          + "     COALESCE(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json, '$.payee')), f.return_to) as payee_name,"
          + "     JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json, '$.payeeAccount')) as payee_account,"
          + "     COALESCE(CAST(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json, '$.expectedRefundDate')), 1, 10) AS DATETIME), f.fee_date) as exp_return_date,"
          + "     CASE"
          + "       WHEN pc.deposit_return_status = 'FULLY_RETURNED' THEN f.amount"
          + "       WHEN pc.deposit_return_status = 'PARTIAL_RETURN_PARTIAL_TRANSFER' THEN pc.returned_amount"
          + "       ELSE NULL"
          + "     END as returned_amount,"
          + "     CASE"
          + "       WHEN pc.deposit_return_status = 'FULLY_RETURNED' THEN NULL"
          + "       WHEN pc.deposit_return_status IN ('TRANSFERRED_TO_FEE', 'PARTIAL_RETURN_PARTIAL_TRANSFER') THEN pc.transfer_amount"
          + "       ELSE NULL"
          + "     END as service_fee_amount,"
          + "     pc.deposit_return_date as actual_return_date,"
          + "     f.status, f.created_at";

    /** 派生表 pid 分支 SELECT 列（listBase + countBase 共用）。 */
    static final String DERIVED_SELECT_INIT =
            "   SELECT -pid.project_id as fee_id, pid.project_id,"
          + "     p.name as project_name, pid.owner_unit,"
          + "     COALESCE(pid.project_leader_name, t.project_manager_name)"
          + "       as project_leader_name,"
          + "     COALESCE(pid.bidding_leader_name, t.bidding_person_name)"
          + "       as bidding_leader_name,"
          + "     pid.deposit_amount, NULL as payment_date,"
          + "     pid.deposit_payment_method, NULL as payee_name,"
          + "     NULL as payee_account, NULL as exp_return_date,"
          + "     NULL as returned_amount, NULL as service_fee_amount,"
          + "     NULL as actual_return_date, 'PENDING' as status,"
          + "     COALESCE(pid.created_at, p.created_at) as created_at";

    private MarginDerivedTableColumns() {
    }
}
