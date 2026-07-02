package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.MarginDTO;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

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
          + " WHERE f.fee_type = 'BID_BOND'";

    private static final String INIT_JOIN =
            " FROM project_initiation_details pid"
          + " JOIN projects p ON p.id = pid.project_id"
          + " WHERE ";

    private MarginQuerySupport() {
    }

    private static String initOnlyFragment(final String roleFragment) {
        return INIT_JOIN + INIT_ONLY_WHERE + roleFragment;
    }

    static StringBuilder summaryBase(final MarginQueryRole policy) {
        String rf = policy.apply("p", "pid");
        return new StringBuilder(
                "SELECT"
              + "  COALESCE(SUM(m.amount), 0),"
              + "  COALESCE(SUM(CASE WHEN m.status"
              + "    NOT IN ('RETURNED','CANCELLED')"
              + "    THEN m.amount ELSE 0 END), 0),"
              + "  COUNT(CASE WHEN m.status"
              + "    NOT IN ('RETURNED','CANCELLED') THEN 1 END),"
              + "  COALESCE(SUM(CASE WHEN m.status"
              + "    NOT IN ('RETURNED','CANCELLED')"
              + "    AND m.exp_return_date < NOW()"
              + "    THEN m.amount ELSE 0 END), 0),"
              + "  COUNT(CASE WHEN m.status"
              + "    NOT IN ('RETURNED','CANCELLED')"
              + "    AND m.exp_return_date < NOW() THEN 1 END)"
              + " FROM ("
              + "   SELECT f.amount as amount, f.status as status,"
              + "     f.fee_date as exp_return_date"
              + FEES_JOIN + rf
              + "   UNION ALL"
              + "   SELECT pid.deposit_amount as amount, 'PENDING' as status,"
              + "     NULL as exp_return_date"
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

    /** Append search filter conditions. */
    static void appendFilters(final StringBuilder sql,
                               final Map<String, String> f) {
        if (f == null) {
            return;
        }
        if (has(f, "projectName")) {
            sql.append(" AND m.project_name LIKE :pName");
        }
        if (has(f, "ownerUnit")) {
            sql.append(" AND m.owner_unit LIKE :oUnit");
        }
        if (has(f, "projectLeaderName")) {
            sql.append(" AND m.project_leader_name = :pLead");
        }
        if (has(f, "biddingLeaderName")) {
            sql.append(" AND m.bidding_leader_name = :bLead");
        }
        if (f.get("paymentDateStart") != null) {
            sql.append(" AND m.payment_date >= :pdS");
        }
        if (f.get("paymentDateEnd") != null) {
            sql.append(" AND m.payment_date <= :pdE");
        }
        if (f.get("expectedReturnDateStart") != null) {
            sql.append(" AND m.exp_return_date >= :edS");
        }
        if (f.get("expectedReturnDateEnd") != null) {
            sql.append(" AND m.exp_return_date <= :edE");
        }
        if (has(f, "status")) {
            switch (f.get("status")) {
                case "RETURNED":
                    // label() 把 'RETURNED' 和 'CANCELLED' 都标为"已退回"，
                    // filter 语义必须与 label 对齐：两者都应被 RETURNED 筛选命中。
                    sql.append(" AND m.status IN ('RETURNED','CANCELLED')");
                    break;
                case "OVERDUE":
                    sql.append(" AND m.status"
                            + " NOT IN ('RETURNED','CANCELLED')"
                            + " AND m.exp_return_date < NOW()");
                    break;
                case "PENDING":
                    // 派生表 init 分支（立项未缴占位行）的 exp_return_date 为 NULL，
                    // MySQL 语义下 `NULL >= NOW()` 求值为 NULL（falsy）会漏掉这些行，
                    // 因此显式加 `IS NULL` 把 init 分支行纳入 PENDING 筛选。
                    sql.append(" AND m.status"
                            + " NOT IN ('RETURNED','CANCELLED')"
                            + " AND (m.exp_return_date IS NULL"
                            + " OR m.exp_return_date >= NOW())");
                    break;
                default:
                    break;
            }
        }
    }

    /** Bind filter parameters to query. */
    static void setParams(final Query query, final Map<String, String> f) {
        if (f == null) {
            return;
        }
        if (has(f, "projectName")) {
            query.setParameter("pName", "%" + f.get("projectName") + "%");
        }
        if (has(f, "ownerUnit")) {
            query.setParameter("oUnit", "%" + f.get("ownerUnit") + "%");
        }
        if (has(f, "projectLeaderName")) {
            query.setParameter("pLead", f.get("projectLeaderName"));
        }
        if (has(f, "biddingLeaderName")) {
            query.setParameter("bLead", f.get("biddingLeaderName"));
        }
        if (f.get("paymentDateStart") != null) {
            query.setParameter("pdS",
                    LocalDateTime.parse(
                            f.get("paymentDateStart") + "T00:00:00"));
        }
        if (f.get("paymentDateEnd") != null) {
            query.setParameter("pdE",
                    LocalDateTime.parse(
                            f.get("paymentDateEnd") + "T23:59:59"));
        }
        if (f.get("expectedReturnDateStart") != null) {
            query.setParameter("edS",
                    LocalDateTime.parse(
                            f.get("expectedReturnDateStart") + "T00:00:00"));
        }
        if (f.get("expectedReturnDateEnd") != null) {
            query.setParameter("edE",
                    LocalDateTime.parse(
                            f.get("expectedReturnDateEnd") + "T23:59:59"));
        }
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
                .statusLabel(label(feeStatus, (Timestamp) r[C_EXP_RETURN]))
                .build();
    }

    private static boolean has(final Map<String, String> m, final String k) {
        String v = m.get(k);
        return v != null && !v.isBlank();
    }

    private static String label(final String st, final Timestamp exp) {
        if ("RETURNED".equals(st) || "CANCELLED".equals(st)) {
            return "已退回";
        }
        if (exp != null
                && exp.toLocalDateTime().isBefore(LocalDateTime.now())) {
            return "已超期";
        }
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
