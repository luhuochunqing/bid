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
 *   <li>summaryBase 只用 amount/status/exp_return_date/returned_amount/service_fee_amount
 *       五列（CO-508 起按金额判定"已退回"，需 returned_amount/service_fee_amount）</li>
 * </ul>
 * 为避免 listBase 与 countBase 列契约漂移，countBase 复用 listBase 的
 * 派生表 SELECT 列定义（多出的列不影响 COUNT(*) 性能，但保证对齐）。
 *
 * <p>CO-490 修复（上一轮）：
 * <ul>
 *   <li>INIT 分支也 JOIN tasks dt + project_closure pc，使两个分支字段取值对齐。
 *       修复前 INIT 分支硬编码 payee_name/payee_account/payment_date/exp_return_date
 *       为 NULL，导致"立项了但 fees 表无 BID_BOND 记录"的项目（如结项流程漏建 fee）
 *       即使保证金任务已填表，列表也取不到值。</li>
 *   <li>deposit_payment_method 改为 CASE WHEN 翻译（WIRE→电汇, GUARANTEE→保险/保函），
 *       修复前两个分支都直接透传枚举英文。</li>
 *   <li>payee 加 NULLIF(..., '') 包裹，与 actualPaymentDate/expectedRefundDate 一致，
 *       避免任务 JSON 空字符串导致 COALESCE 不回退。</li>
 *   <li>project_leader_name 加 user.full_name 兜底（通过 p.manager_id JOIN users），
 *       修复前 pid.project_leader_name 和 t.project_manager_name 都空时返回 NULL。</li>
 * </ul>
 *
 * <p>CO-508 修复（本轮）：
 * <ul>
 *   <li>抽取 {@link #returnedAmountExpr(String)} / {@link #serviceFeeAmountExpr()}
 *       共享方法，让 summaryBase 派生表也能复用同一份 CASE 逻辑，
 *       避免 listBase 与 summaryBase 对"退回金额/服务费金额"的判定语义漂移。</li>
 * </ul>
 *
 * <p>XIYU-P 修复（本轮）：
 * <ul>
 *   <li>历史 fees.payment_date / fees.fee_date / project_closure.deposit_return_date
 *       可能残留 '0000-00-00 00:00:00' 零日期（V1077 之前旧数据或异常写入）。
 *       JDBC 读到 zero date 会抛 DataException。在 SQL 层用
 *       {@code NULLIF(col, '0000-00-00 00:00:00')} 转成 NULL，让上层按"无日期"处理。</li>
 *   <li>tasks.extended_fields_json 可能残留空字符串 ''，MySQL
 *       {@code JSON_EXTRACT('', '$.x')} 会直接抛
 *       "Invalid JSON text: The document is empty"，导致保证金列表 500。
 *       在 SQL 层用 {@code NULLIF(dt.extended_fields_json, '')} 包裹
 *       JSON_EXTRACT 输入，把空字符串转 NULL；同时新增 V1142 迁移清洗历史数据，
 *       并在 Task 实体加 {@code @PrePersist/@PreUpdate} 写侧防御。</li>
 * </ul>
 */
final class MarginDerivedTableColumns {

    /**
     * 任务扩展字段 JSON 表达式。
     * <p>Sentry XIYU-P：tasks.extended_fields_json 可能残留空字符串 ''，
     * MySQL JSON_EXTRACT('', '$.x') 会抛 "Invalid JSON text: The document is empty"。
     * 用 NULLIF 把空字符串转 NULL，JSON_EXTRACT(NULL, ...) 返回 NULL，避免 500。
     */
    static final String TASK_EXT_JSON = "NULLIF(dt.extended_fields_json, '')";

    /** 缴纳方式翻译 CASE WHEN（FEES + INIT 共用）。
     *  WIRE=电汇, GUARANTEE=保险/保函, 其他原样透传（防御未知枚举）。 */
    private static final String DEPOSIT_PAYMENT_METHOD_CASE =
            "CASE pid.deposit_payment_method"
          + "  WHEN 'WIRE' THEN '电汇'"
          + "  WHEN 'GUARANTEE' THEN '保险/保函'"
          + "  ELSE pid.deposit_payment_method"
          + " END as deposit_payment_method";

    private MarginDerivedTableColumns() {
    }

    /**
     * returned_amount 表达式：根据 {@code project_closure.deposit_return_status}
     * 推导退回金额。
     *
     * <p>CO-508 抽取为共享方法，让 {@link MarginQuerySupport#summaryBase} 的派生表
     * 也能复用同一份 CASE 逻辑，避免 listBase 与 summaryBase 判定语义漂移。
     *
     * @param amountExpr 保证金金额表达式（fees 分支用 {@code f.amount}，
     *                   init 分支用 {@code pid.deposit_amount}）
     */
    static String returnedAmountExpr(final String amountExpr) {
        return "CASE WHEN pc.deposit_return_status = 'FULLY_RETURNED' THEN "
                + amountExpr
              + " WHEN pc.deposit_return_status = 'PARTIAL_RETURN_PARTIAL_TRANSFER'"
              + " THEN pc.returned_amount"
              + " ELSE NULL END";
    }

    /**
     * service_fee_amount 表达式：根据 {@code project_closure.deposit_return_status}
     * 推导转服务费金额（CO-508 共享）。
     */
    static String serviceFeeAmountExpr() {
        return "CASE WHEN pc.deposit_return_status = 'FULLY_RETURNED' THEN NULL"
              + " WHEN pc.deposit_return_status"
              + "   IN ('TRANSFERRED_TO_FEE', 'PARTIAL_RETURN_PARTIAL_TRANSFER')"
              + " THEN pc.transfer_amount"
              + " ELSE NULL END";
    }

    /** 派生表 fees 分支 SELECT 列（listBase + countBase 共用）。
     *  <p>缴纳日期/收款方/收款账号/应退日期 → 保证金缴纳任务 JSON
     *     退回金额/转服务费金额/退回日期 → project_closure 结项表
     *  <p>project_leader_name / bidding_leader_name 用 NULLIF 包裹空字符串，
     *     因为前端带入可能写入 ''（COALESCE 不跳过空字符串，需 NULLIF 转 NULL 才回退）。 */
    static final String DERIVED_SELECT_FEES =
            "   SELECT f.id as fee_id, f.project_id, p.name as project_name,"
          + "     pid.owner_unit,"
          + "     COALESCE(NULLIF(pid.project_leader_name, ''),"
          + "       NULLIF(t.project_manager_name, ''), u.full_name)"
          + "       COLLATE utf8mb4_unicode_ci as project_leader_name,"
          + "     COALESCE(NULLIF(u_lead.full_name, ''),"
          + "       NULLIF(pid.bidding_leader_name, ''),"
          + "       NULLIF(t.bidding_person_name, ''))"
          + "       COLLATE utf8mb4_unicode_ci as bidding_leader_name,"
          + "     f.amount,"
          + "     COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.actualPaymentDate')), 1, 10), ''), '%Y-%m-%d'), NULLIF(f.payment_date, '0000-00-00 00:00:00')) as payment_date,"
          + "     " + DEPOSIT_PAYMENT_METHOD_CASE + ","
          + "     COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.payee')), ''), f.return_to) as payee_name,"
          + "     JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.payeeAccount')) as payee_account,"
          + "     COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d'), NULLIF(f.fee_date, '0000-00-00 00:00:00')) as exp_return_date,"
          + "     " + returnedAmountExpr("f.amount") + " as returned_amount,"
          + "     " + serviceFeeAmountExpr() + " as service_fee_amount,"
          + "     NULLIF(pc.deposit_return_date, '0000-00-00 00:00:00') as actual_return_date,"
          + "     f.status, f.created_at";

    /** 派生表 pid 分支 SELECT 列（listBase + countBase 共用）。
     *  <p>CO-490 修复：INIT 分支也 JOIN tasks dt + project_closure pc，
     *     4 个原硬编码 NULL 字段改为从任务 JSON / 结项表取值，
     *     deposit_payment_method 加 CASE WHEN 翻译，
     *     project_leader_name 加 u.full_name 兜底。
     *  <p>注意：INIT 分支无 fees 记录，payment_date / exp_return_date 无 fees 列可回退，
     *     任务 JSON 为空时直接返回 NULL（符合"取任务实际缴纳日期/预计归还日期"语义）。
     *  <p>returned_amount 在 FULLY_RETURNED 时取 pid.deposit_amount
     *     （INIT 分支无 f.amount，立项金额即保证金金额）。 */
    static final String DERIVED_SELECT_INIT =
            "   SELECT -pid.project_id as fee_id, pid.project_id,"
          + "     p.name as project_name, pid.owner_unit,"
          + "     COALESCE(NULLIF(pid.project_leader_name, ''),"
          + "       NULLIF(t.project_manager_name, ''), u.full_name)"
          + "       COLLATE utf8mb4_unicode_ci as project_leader_name,"
          + "     COALESCE(NULLIF(u_lead.full_name, ''),"
          + "       NULLIF(pid.bidding_leader_name, ''),"
          + "       NULLIF(t.bidding_person_name, ''))"
          + "       COLLATE utf8mb4_unicode_ci as bidding_leader_name,"
          + "     pid.deposit_amount,"
          + "     STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.actualPaymentDate')), 1, 10), ''), '%Y-%m-%d') as payment_date,"
          + "     " + DEPOSIT_PAYMENT_METHOD_CASE + ","
          + "     NULLIF(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.payee')), '') as payee_name,"
          + "     JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.payeeAccount')) as payee_account,"
          + "     STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(NULLIF(dt.extended_fields_json, ''), '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d') as exp_return_date,"
          + "     " + returnedAmountExpr("pid.deposit_amount") + " as returned_amount,"
          + "     " + serviceFeeAmountExpr() + " as service_fee_amount,"
          + "     NULLIF(pc.deposit_return_date, '0000-00-00 00:00:00') as actual_return_date,"
          + "     'PENDING' as status,"
          + "     COALESCE(pid.created_at, p.created_at) as created_at";
}
