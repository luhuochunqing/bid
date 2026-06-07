package com.xiyu.bid.workflowform.application.port;

public interface OaWorkflowGateway {
    OaStartResult startProcess(OaStartCommand command);
}
