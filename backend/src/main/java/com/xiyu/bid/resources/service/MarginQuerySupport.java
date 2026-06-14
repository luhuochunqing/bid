package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.MarginDTO;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

/** SQL builders and row mapping for margin ledger queries. */
final class MarginQuerySupport {

    /** Column index constants for list query result mapping. */
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

    private MarginQuerySupport() {
    }

    /** Build summary statistics SQL. */
    static StringBuilder summaryBase() {
        return new StringBuilder(
                "SELECT"
                + "  COALESCE(SUM(f.amount), 0),"
                + "  COALESCE(SUM(CASE WHEN f.status"
                + "    NOT IN ('RETURNED','CANCELLED')"
                + "    THEN f.amount ELSE 0 END), 0),"
                + "  COUNT(CASE WHEN f.status"
                + "    NOT IN ('RETURNED','CANCELLED') THEN 1 END),"
                + "  COALESCE(SUM(CASE WHEN f.status"
                + "    NOT IN ('RETURNED','CANCELLED')"
                + "    AND f.fee_date < NOW()"
                + "    THEN f.amount ELSE 0 END), 0),"
                + "  COUNT(CASE WHEN f.status"
                + "    NOT IN ('RETURNED','CANCELLED')"
                + "    AND f.fee_date < NOW() THEN 1 END)"
                + " FROM fees f"
                + " JOIN projects p ON p.id = f.project_id"
                + " LEFT JOIN project_initiation_details pid"
                + " ON pid.project_id = f.project_id"
                + " WHERE f.fee_type = 'BID_BOND'");
    }

    /** Build list query SQL. */
    static StringBuilder listBase() {
        return new StringBuilder(
                "SELECT"
                + " f.id, f.project_id, p.name, pid.owner_unit,"
                + " pid.project_leader_name, pid.bidding_leader_name,"
                + " f.amount, f.payment_date, pid.deposit_payment_method,"
                + " f.return_to, NULL,"
                + " f.fee_date,"
                + " CASE WHEN f.status='RETURNED'"
                + "   THEN f.amount ELSE NULL END,"
                + " NULL, f.return_date, f.status"
                + " FROM fees f"
                + " JOIN projects p ON p.id = f.project_id"
                + " LEFT JOIN project_initiation_details pid"
                + " ON pid.project_id = f.project_id"
                + " WHERE f.fee_type = 'BID_BOND'");
    }

    /** Build count query SQL. */
    static StringBuilder countBase() {
        return new StringBuilder(
                "SELECT COUNT(*) FROM fees f"
                + " JOIN projects p ON p.id = f.project_id"
                + " LEFT JOIN project_initiation_details pid"
                + " ON pid.project_id = f.project_id"
                + " WHERE f.fee_type = 'BID_BOND'");
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
            sql.append(" AND p.name LIKE :pName");
        }
        if (has(f, "ownerUnit")) {
            sql.append(" AND pid.owner_unit LIKE :oUnit");
        }
        if (has(f, "projectLeaderName")) {
            sql.append(" AND pid.project_leader_name = :pLead");
        }
        if (has(f, "biddingLeaderName")) {
            sql.append(" AND pid.bidding_leader_name = :bLead");
        }
        if (f.get("paymentDateStart") != null) {
            sql.append(" AND f.payment_date >= :pdS");
        }
        if (f.get("paymentDateEnd") != null) {
            sql.append(" AND f.payment_date <= :pdE");
        }
        if (f.get("expectedReturnDateStart") != null) {
            sql.append(" AND f.fee_date >= :edS");
        }
        if (f.get("expectedReturnDateEnd") != null) {
            sql.append(" AND f.fee_date <= :edE");
        }
        if (has(f, "status")) {
            switch (f.get("status")) {
                case "RETURNED":
                    sql.append(" AND f.status = 'RETURNED'");
                    break;
                case "OVERDUE":
                    sql.append(" AND f.status"
                            + " NOT IN ('RETURNED','CANCELLED')"
                            + " AND f.fee_date < NOW()");
                    break;
                case "PENDING":
                    sql.append(" AND f.status"
                            + " NOT IN ('RETURNED','CANCELLED')"
                            + " AND f.fee_date >= NOW()");
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
