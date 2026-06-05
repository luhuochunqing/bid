package com.xiyu.bid.resources.service;

import com.xiyu.bid.resources.dto.MarginDTO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Margin deposit ledger service. */
@Service
@RequiredArgsConstructor
public class MarginService {

    /** JPA entity manager. */
    @PersistenceContext
    private final EntityManager em;

    /**
     * Get summary statistics for margin dashboard cards.
     *
     * @param uid  current user ID
     * @param role current user role
     * @return map with totalPaid, totalPending, etc.
     */
    public Map<String, Object> getSummary(final Long uid, final String role) {
        StringBuilder sql = MarginQuerySupport.summaryBase();
        MarginQuerySupport.appendRole(sql, uid, role, "p", "pid");
        Query query = em.createNativeQuery(sql.toString());
        Object[] row = (Object[]) query.getSingleResult();
        return Map.of(
                "totalPaid", toDecimal(row[0]),
                "totalPending", toDecimal(row[1]),
                "pendingCount", toLong(row[2]),
                "overdueAmount", toDecimal(row[3]),
                "overdueCount", toLong(row[4]));
    }

    /** Get paginated margin ledger rows. */
    @SuppressWarnings("unchecked")
    public List<MarginDTO> getList(
            final Long uid, final String role,
            final Map<String, String> f,
            final int page, final int size) {
        StringBuilder sql = MarginQuerySupport.listBase();
        MarginQuerySupport.appendRole(sql, uid, role, "p", "pid");
        MarginQuerySupport.appendFilters(sql, f);
        sql.append(" ORDER BY f.created_at DESC");
        Query query = em.createNativeQuery(sql.toString());
        MarginQuerySupport.setParams(query, f);
        query.setFirstResult((page - 1) * size);
        query.setMaxResults(size);
        return ((List<Object[]>) query.getResultList()).stream()
                .map(MarginQuerySupport::mapRow)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Get total count matching filters. */
    public long getCount(
            final Long uid, final String role,
            final Map<String, String> f) {
        StringBuilder sql = MarginQuerySupport.countBase();
        MarginQuerySupport.appendRole(sql, uid, role, "p", "pid");
        MarginQuerySupport.appendFilters(sql, f);
        Query query = em.createNativeQuery(sql.toString());
        MarginQuerySupport.setParams(query, f);
        return ((Number) query.getSingleResult()).longValue();
    }

    private static BigDecimal toDecimal(final Object v) {
        if (v instanceof BigDecimal bd) {
            return bd;
        }
        return v != null ? new BigDecimal(v.toString()) : BigDecimal.ZERO;
    }

    private static long toLong(final Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }
}
