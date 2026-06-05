package com.xiyu.bid.workflowform.infrastructure.oa;

import com.xiyu.bid.workflowform.application.port.OaStartCommand;
import com.xiyu.bid.workflowform.application.port.OaStartResult;
import com.xiyu.bid.workflowform.application.port.OaWorkflowGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "oa.workflow.mode", havingValue = "weaver", matchIfMissing = true)
public class WeaverOaWorkflowGateway implements OaWorkflowGateway {

    @Override
    public OaStartResult startProcess(OaStartCommand command) {
        return new OaStartResult(false, null, "泛微 OA HTTP 适配器等待客户接口资料后启用");
    }
}
