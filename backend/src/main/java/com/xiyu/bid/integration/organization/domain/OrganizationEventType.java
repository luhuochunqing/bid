package com.xiyu.bid.integration.organization.domain;

public enum OrganizationEventType {
    DEPARTMENT_NOTICE("BaseOssDept", "DEPARTMENT", "deptId"),
    USER_NOTICE("BaseOssUser", "USER", "userId"),
    JOB_NOTICE("BaseOssJob", "JOB", "jobId");

    private final String topic;
    private final String entityType;
    private final String dataIdField;

    OrganizationEventType(String topic, String entityType, String dataIdField) {
        this.topic = topic;
        this.entityType = entityType;
        this.dataIdField = dataIdField;
    }

    public String topic() {
        return topic;
    }

    public String entityType() {
        return entityType;
    }

    public String dataIdField() {
        return dataIdField;
    }
}
