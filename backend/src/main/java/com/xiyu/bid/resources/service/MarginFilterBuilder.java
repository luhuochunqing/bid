package com.xiyu.bid.resources.service;

import jakarta.persistence.Query;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 保证金查询过滤条件拼接与参数绑定。
 *
 * <p>从 MarginQuerySupport 抽取，职责单一：根据前端传入的筛选条件 Map，
 * 往 StringBuilder 追加 WHERE 子句片段，并给 Query 绑定对应的命名参数。
 *
 * <p>CO-508 状态筛选规则：
 * <ul>
 *   <li>RETURNED = COALESCE(returned_amount,0) + COALESCE(service_fee_amount,0) = amount</li>
 *   <li>OVERDUE  = 未退回 AND exp_return_date &lt; NOW()</li>
 *   <li>PENDING  = 未退回 AND (exp_return_date IS NULL OR exp_return_date &gt;= NOW())</li>
 * </ul>
 */
final class MarginFilterBuilder {

    private MarginFilterBuilder() {
    }

    /** Append search filter conditions to SQL builder. */
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
            // CO-508: status 筛选按金额判定，与 label() 规则3对齐：
            // 已退回 = COALESCE(returned_amount,0) + COALESCE(service_fee_amount,0) = amount
            String returnedExpr = "(COALESCE(m.returned_amount, 0)"
                    + " + COALESCE(m.service_fee_amount, 0))";
            switch (f.get("status")) {
                case "RETURNED":
                    sql.append(" AND ").append(returnedExpr)
                            .append(" = m.amount");
                    break;
                case "OVERDUE":
                    sql.append(" AND ").append(returnedExpr)
                            .append(" != m.amount")
                            .append(" AND m.exp_return_date IS NOT NULL")
                            .append(" AND m.exp_return_date < NOW()");
                    break;
                case "PENDING":
                    // init 占位行 exp_return_date 为 NULL，NULL >= NOW() 为 NULL（falsy），
                    // 显式加 IS NULL 把 init 分支行纳入 PENDING 筛选。
                    sql.append(" AND ").append(returnedExpr)
                            .append(" != m.amount")
                            .append(" AND (m.exp_return_date IS NULL"
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

    private static boolean has(final Map<String, String> m, final String k) {
        String v = m.get(k);
        return v != null && !v.isBlank();
    }
}
