package com.xiyu.bid.workflowform.application;

public class WorkflowFormConfigException extends RuntimeException {

    public static final String ERROR_CODE = "WORKFLOW_FORM_CONFIG_INVALID";

    public WorkflowFormConfigException(String message) {
        super(message);
    }
}
