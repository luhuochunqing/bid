package com.xiyu.bid.testing;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class ShadowInspectorSchemaRegistry {

    private static final Map<String, String> AUDIT_ENTITY_TYPE_MAP = new HashMap<>();
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "collaboration_threads",
            "comments",
            "projects",
            "tasks",
            "tenders",
            "qualifications",
            "cases",
            "templates",
            "fees",
            "platform_accounts",
            "bar_assets",
            "compliance_check_results",
            "alert_rules",
            "alert_history",
            "calendar_events",
            "competitors",
            "competition_analyses",
            "score_analyses",
            "dimension_scores",
            "roi_analyses",
            "project_quality_checks",
            "project_quality_issues",
            "document_versions",
            "document_sections",
            "document_structures",
            "assembly_templates",
            "document_assemblies",
            "audit_logs"
    );

    static {
        AUDIT_ENTITY_TYPE_MAP.put("collaboration_threads", "CollaborationThread");
        AUDIT_ENTITY_TYPE_MAP.put("comments", "Comment");
        AUDIT_ENTITY_TYPE_MAP.put("projects", "Project");
        AUDIT_ENTITY_TYPE_MAP.put("tasks", "Task");
        AUDIT_ENTITY_TYPE_MAP.put("tenders", "Tender");
        AUDIT_ENTITY_TYPE_MAP.put("qualifications", "Qualification");
        AUDIT_ENTITY_TYPE_MAP.put("cases", "Case");
        AUDIT_ENTITY_TYPE_MAP.put("templates", "Template");
        AUDIT_ENTITY_TYPE_MAP.put("fees", "Fee");
        AUDIT_ENTITY_TYPE_MAP.put("platform_accounts", "PlatformAccount");
        AUDIT_ENTITY_TYPE_MAP.put("bar_assets", "BarAsset");
        AUDIT_ENTITY_TYPE_MAP.put("calendar_events", "CalendarEvent");
        AUDIT_ENTITY_TYPE_MAP.put("competitors", "Competitor");
        AUDIT_ENTITY_TYPE_MAP.put("competition_analyses", "CompetitionAnalysis");
        AUDIT_ENTITY_TYPE_MAP.put("score_analyses", "ScoreAnalysis");
        AUDIT_ENTITY_TYPE_MAP.put("roi_analyses", "ROIAnalysis");
        AUDIT_ENTITY_TYPE_MAP.put("project_quality_checks", "ProjectQualityCheck");
        AUDIT_ENTITY_TYPE_MAP.put("project_quality_issues", "ProjectQualityIssue");
        AUDIT_ENTITY_TYPE_MAP.put("document_versions", "DocumentVersion");
        AUDIT_ENTITY_TYPE_MAP.put("document_sections", "DocumentSection");
        AUDIT_ENTITY_TYPE_MAP.put("document_structures", "DocumentStructure");
        AUDIT_ENTITY_TYPE_MAP.put("document_assemblies", "DocumentAssembly");
    }

    private ShadowInspectorSchemaRegistry() {}

    static void validateTable(String tableName) {
        if (!ALLOWED_TABLES.contains(tableName)) {
            throw new IllegalArgumentException(
                    "Table not allowed: " + tableName + ". This table is not in the allowed list."
            );
        }
    }

    static String resolveAuditEntityType(String entityTypeOrTableName) {
        return AUDIT_ENTITY_TYPE_MAP.getOrDefault(entityTypeOrTableName, entityTypeOrTableName);
    }
}
