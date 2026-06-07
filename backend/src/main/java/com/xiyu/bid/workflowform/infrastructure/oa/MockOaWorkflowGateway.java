package com.xiyu.bid.workflowform.infrastructure.oa;

import com.xiyu.bid.workflowform.application.port.OaStartCommand;
import com.xiyu.bid.workflowform.application.port.OaStartResult;
import com.xiyu.bid.workflowform.application.port.OaWorkflowGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "oa.workflow.mode", havingValue = "mock")
public class MockOaWorkflowGateway implements OaWorkflowGateway {

    private OaStartCommand lastStartedCommand;

    @Override
    public OaStartResult startProcess(OaStartCommand command) {
        this.lastStartedCommand = command;
        String prefix = command.trial() ? "MOCK-TRIAL-OA-" : "MOCK-OA-";
        return new OaStartResult(true, prefix + UUID.randomUUID(), null);
    }

    public Optional<OaStartCommand> lastStartedCommand() {
        return Optional.ofNullable(lastStartedCommand);
    }
}
