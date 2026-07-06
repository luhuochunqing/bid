package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.MarginDTO;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/** SQL builders and row mapping for margin ledger queries. */
final class MarginQuerySupport {

    private static final int C_FEE_ID = 0;
    private static final int C_PROJ_ID = 1;
    private static final int C_PROJ_NAME = 2;
    private static final int C_OWNER = 3;
    private static final int C_PROJ_LEAD = 4;
    private static final int C_BID_LEAD = 5;
    private static final int C_AMT = 6;
    private static final int C_PAY_DATE = 7;
    private static final int C_PAY_METHOD = 8;
    private static final int C_PAYEE = 9;
    private static final int C_PAYEE_ACCT = 10;
    private static final int C_EXP_RETURN = 11;
    private static final int C_RET_AMT = 12;
    private static final int C_SVC_FEE = 13;
    private static final int C_ACT_RETURN = 14;
    private static final int C_STATUS = 15;

    private static final String INIT_ONLY_WHERE =
            "pid.need_deposit = 'YES'"
          + " AND pid.deposit_amount IS NOT NULL"
          + " AND pid.deposit_amount > 0"
          + " AND NOT EXISTS ("
          + "   SELECT 1 FROM fees f2"
          + "   WHERE f2.project_id = pid.project_id"
          + "     AND f2.fee_type = 'BID_BOND'"
          + "     AND f2.status != 'CANCELLED'"
          + " )";

    private static final String FEES_JOIN =
            " FROM fees f"
          + " JOIN projects p ON p.id = f.project_id"
          + " LEFT JOIN project_initiation_details pid"
          + "   ON pid.project_id = f.project_id"
          + " LEFT JOIN tenders t ON t.id = p.tender_id"
          + " LEFT JOIN users u ON u.id = p.manager_id"
          + " LEFT JOIN project_lead_assignment pla ON pla.project_id = f.project_id"
          + " LEFT JOIN users u_lead ON u_lead.id = pla.primary_lead_user_id"
          + " LEFT JOIN tasks dt"
          + "   ON dt.project_id = f.project_id"
          + "   AND JSON_EXTRACT(dt.extended_fields_json, '$._taskType') = 'deposit-payment'"
          + " LEFT JOIN project_closure pc"
          + "   ON pc.project_id = f.project_id"
          + " WHERE f.fee_type = 'BID_BOND'";

    private static final String INIT_JOIN =
            " FROM project_initiation_details pid"
          + " JOIN projects p ON p.id = pid.project_id"
          + " LEFT JOIN tenders t ON t.id = p.tender_id"
          + " LEFT JOIN users u ON u.id = p.manager_id"
          + " LEFT JOIN project_lead_assignment pla ON pla.project_id = pid.project_id"
          + " LEFT JOIN users u_lead ON u_lead.id = pla.primary_lead_user_id"
          + " LEFT JOIN tasks dt"
          + "   ON dt.project_id = pid.project_id"
          + "   AND JSON_EXTRACT(dt.extended_fields_json, '$._taskType') = 'deposit-payment'"
          + " LEFT JOIN project_closure pc"
          + "   ON pc.project_id = pid.project_id"
          + " WHERE ";

    private MarginQuerySupport() {
    }

    private static String initOnlyFragment(final String roleFragment) {
        return INIT_JOIN + INIT_ONLY_WHERE + roleFragment;
    }

    static StringBuilder summaryBase(final MarginQueryRole policy) {
        String rf = policy.apply("p", "pid");
        // CO-508: "已退回"按金额判定（规则3），派生表加 returned_amount / service_fee_amount
        // 列，让外层聚合与 label() / appendFilters() 同一份语义。
        String notReturned =
                "COALESCE(m.returned_amount, 0)"
              + " + COALESCE(m.service_fee_amount, 0) != m.amount";
        return new StringBuilder(
                "SELECT"
              + "  COALESCE(SUM(m.amount), 0),"
              + "  COALESCE(SUM(CASE WHEN " + notReturned
              + "    THEN m.amount ELSE 0 END), 0),"
              + "  COUNT(CASE WHEN " + notReturned + " THEN 1 END),"
              + "  COALESCE(SUM(CASE WHEN " + notReturned
              + "    AND m.exp_return_date IS NOT NULL"
              + "    AND m.exp_return_date < NOW()"
              + "    THEN m.amount ELSE 0 END), 0),"
              + "  COUNT(CASE WHEN " + notReturned
              + "    AND m.exp_return_date IS NOT NULL"
              + "    AND m.exp_return_date < NOW() THEN 1 END)"
              + " FROM ("
              + "   SELECT f.amount as amount, f.status as status,"
              + "     COALESCE(STR_TO_DATE(NULLIF(SUBSTRING(JSON_UNQUOTE(JSON_EXTRACT(dt.extended_fields_json, '$.expectedRefundDate')), 1, 10), ''), '%Y-%m-%d'), NULLIF(f.fee_date, '0000-00-00 00:00:00')) as exp_return_date,"
              + "     " + MarginDerivedTableColumns.returnedAmountExpr("f.amount") + " as returned_amount,"
              + "     " + MarginDerivedTableColumns.serviceFeeAmountExpr() + " as service_fee_amount"
              + FEES_JOIN + rf
              + "   UNION ALL"
              + "   SELECT pid.deposit_amount as amount, 'PENDING' as status,"
              + "     NULL as exp_return_date,"
              + "     " + MarginDerivedTableColumns.returnedAmountExpr("pid.deposit_amount") + " as returned_amount,"
              + "     " + MarginDerivedTableColumns.serviceFeeAmountExpr() + " as service_fee_amount"
              + initOnlyFragment(rf)
              + " ) m WHERE 1=1");
    }

    static StringBuilder listBase(final MarginQueryRole policy) {
        String rf = policy.apply("p", "pid");
        return new StringBuilder(
                "SELECT m.fee_id, m.project_id, m.project_name, m.owner_unit,"
              + " m.project_leader_name, m.bidding_leader_name,"
              + " m.amount, m.payment_date, m.deposit_payment_method,"
              + " m.payee_name, m.payee_account,"
              + " m.exp_return_date, m.returned_amount,"
              + " m.service_fee_amount, m.actual_return_date, m.status"
              + " FROM ("
              + MarginDerivedTableColumns.DERIVED_SELECT_FEES
              + FEES_JOIN + rf
              + "   UNION ALL"
              + MarginDerivedTableColumns.DERIVED_SELECT_INIT
              + initOnlyFragment(rf)
              + " ) m WHERE 1=1");
    }

    static StringBuilder countBase(final MarginQueryRole policy) {
        String rf = policy.apply("p", "pid");
        // 复用 listBase 的派生表 SELECT 列定义，保证派生表列与 appendFilters
        // 引用的列严格对齐，避免再次出现 "Unknown column 'm.status'"。
        // 多出的列（如 returned_amount / actual_return_date）对 COUNT(*)
        // 无业务影响，但保证列契约单一来源、零漂移。
        return new StringBuilder(
                "SELECT COUNT(*) FROM ("
              + MarginDerivedTableColumns.DERIVED_SELECT_FEES
              + FEES_JOIN + rf
              + "   UNION ALL"
              + MarginDerivedTableColumns.DERIVED_SELECT_INIT
              + initOnlyFragment(rf)
              + " ) m WHERE 1=1");
    }

    /** Append role-based data visibility filter. */
    static void appendRole(
            final StringBuilder sql, final Long uid, final String role,
            final String pa, final String pi) {
        if (role == null) {
            return;
        }
        sql.append(MarginQueryRole.from(role).apply(pa, pi));
    }

    /** Map a native query result row to a MarginDTO. */
    static MarginDTO mapRow(final Object[] r) {
        String feeStatus = (String) r[C_STATUS];
        return MarginDTO.builder()
                .feeId(toLong(r[C_FEE_ID]))
                .projectId(toLong(r[C_PROJ_ID]))
                .projectName((String) r[C_PROJ_NAME])
                .ownerUnit((String) r[C_OWNER])
                .projectLeaderName((String) r[C_PROJ_LEAD])
                .biddingLeaderName((String) r[C_BID_LEAD])
                .depositAmount((BigDecimal) r[C_AMT])
                .paymentDate(toLdt(r[C_PAY_DATE]))
                .depositPaymentMethod((String) r[C_PAY_METHOD])
                .payeeName((String) r[C_PAYEE])
                .payeeAccount((String) r[C_PAYEE_ACCT])
                .expectedReturnDate(toLdt(r[C_EXP_RETURN]))
                .returnedAmount((BigDecimal) r[C_RET_AMT])
                .serviceFeeAmount((BigDecimal) r[C_SVC_FEE])
                .actualReturnDate(toLdt(r[C_ACT_RETURN]))
                .status(feeStatus)
                .statusLabel(label((Timestamp) r[C_EXP_RETURN],
                        (BigDecimal) r[C_AMT],
                        (BigDecimal) r[C_RET_AMT],
                        (BigDecimal) r[C_SVC_FEE]))
                .build();
    }

    /**
     * 按 CO-508 状态计算规则推导状态标签（规则3 &gt; 规则2 &gt; 规则1）。
     *
     * <p>规则3：退回金额 + 服务费金额 = 保证金金额 → 「已退回」（最高优先级，
     * 不依赖 fee.status，纯按 project_closure 推导的金额判定）；
     * 规则2：当前日期 &gt; 应退日期 → 「已超期」；
     * 规则1：当前日期 ≤ 应退日期 → 「未到期」。
     *
     * @param exp        应退日期（init 占位行为 null）
     * @param depositAmt 保证金金额（f.amount 或 pid.deposit_amount）
     * @param returnedAmt 退回金额（来自 project_closure，可能为 null）
     * @param svcFeeAmt   服务费金额（来自 project_closure.transfer_amount，可能为 null）
     */
    private static String label(final Timestamp exp,
                                final BigDecimal depositAmt,
                                final BigDecimal returnedAmt,
                                final BigDecimal svcFeeAmt) {
        // 规则3：退回金额 + 服务费金额 = 保证金金额 → 已退回
        if (depositAmt != null
                && depositAmt.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal ret = returnedAmt != null ? returnedAmt : BigDecimal.ZERO;
            BigDecimal svc = svcFeeAmt != null ? svcFeeAmt : BigDecimal.ZERO;
            if (ret.add(svc).compareTo(depositAmt) == 0) {
                return "已退回";
            }
        }
        // 规则2：当前日期 > 应退日期 → 已超期
        if (exp != null
                && exp.toLocalDateTime().isBefore(LocalDateTime.now())) {
            return "已超期";
        }
        // 规则1：当前日期 ≤ 应退日期 → 未到期
        return "未到期";
    }

    private static Long toLong(final Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private static LocalDateTime toLdt(final Object v) {
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }
}
