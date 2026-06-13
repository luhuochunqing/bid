package com.xiyu.bid.casework.domain.policy;

import com.xiyu.bid.casework.domain.model.CaseExportCriteria;
import com.xiyu.bid.casework.domain.model.CaseExportRecord;
import com.xiyu.bid.casework.domain.model.KnowledgeCaseReadModel;

import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CaseExportFilterPolicy {

    private static final int RESPONSE_SUMMARY_MAX_LENGTH = 200;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private CaseExportFilterPolicy() {}

    public static List<KnowledgeCaseReadModel> filterCases(
            List<? extends KnowledgeCaseReadModel> cases,
            CaseExportCriteria criteria) {
        if (cases == null || cases.isEmpty()) {
            return List.of();
        }
        return cases.stream()
                .filter(c -> matchesCriteria(c, criteria))
                .map(c -> (KnowledgeCaseReadModel) c)
                .toList();
    }

    private static boolean matchesCriteria(KnowledgeCaseReadModel c, CaseExportCriteria criteria) {
        if (c == null) return false;
        if (!"ACTIVE".equals(c.getStatus())) return false;
        if (criteria == null) return true;

        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            String kw = criteria.keyword().toLowerCase();
            String title = safeLower(c.getScoringPointTitle());
            String req = safeLower(c.getRequirementRaw());
            String resp = safeLower(c.getResponseText());
            if (!title.contains(kw) && !req.contains(kw) && !resp.contains(kw)) {
                return false;
            }
        }
        if (criteria.scoringCategory() != null && !criteria.scoringCategory().isBlank()) {
            if (!criteria.scoringCategory().equals(c.getScoringCategory())) {
                return false;
            }
        }
        if (criteria.customerType() != null && !criteria.customerType().isBlank()) {
            if (!criteria.customerType().equals(c.getCustomerType())) {
                return false;
            }
        }
        if (criteria.projectTypes() != null && !criteria.projectTypes().isEmpty()) {
            if (!criteria.projectTypes().contains(c.getProjectType())) {
                return false;
            }
        }
        return true;
    }

    public static CaseExportRecord toExportRecord(KnowledgeCaseReadModel c) {
        if (c == null) {
            return new CaseExportRecord(
                    "", "", "", "", "", "", 0, "", ""
            );
        }
        return new CaseExportRecord(
                safeStr(c.getScoringPointTitle()),
                safeStr(c.getSourceProjectName()),
                safeStr(c.getProjectType()),
                safeStr(c.getCustomerType()),
                safeStr(c.getScoringCategory()),
                safeStr(c.getBidResult()),
                c.getReuseCount() != null ? c.getReuseCount() : 0,
                formatCreatedAt(c),
                summarizeResponse(c.getResponseText())
        );
    }

    private static String summarizeResponse(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String trimmed = text.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= RESPONSE_SUMMARY_MAX_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, RESPONSE_SUMMARY_MAX_LENGTH) + "...";
    }

    private static String formatCreatedAt(KnowledgeCaseReadModel c) {
        if (c.getCreatedAt() == null) {
            return "";
        }
        return c.getCreatedAt().format(DATE_FORMATTER);
    }

    private static String safeLower(String s) {
        return s != null ? s.toLowerCase() : "";
    }

    private static String safeStr(String s) {
        return s != null ? s : "";
    }
}
