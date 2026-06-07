package com.xiyu.bid.workflowform.domain;

public enum FormFieldType {
    TEXT,
    TEXTAREA,
    NUMBER,
    DATE,
    SELECT,
    PERSON,
    PROJECT,
    QUALIFICATION,
    ATTACHMENT,
    INFO,
    SECTION,
    TABLE,

    // --- 扩展字段类型（M1 基础设施，V140 迁移） ---
    PHONE,
    EMAIL,
    URL,
    CURRENCY,
    PERCENT,
    ADDRESS,
    TENDER_SOURCE,
    PROJECT_STATUS,
    QUALIFICATION_TYPE,
    DIVIDER
}
