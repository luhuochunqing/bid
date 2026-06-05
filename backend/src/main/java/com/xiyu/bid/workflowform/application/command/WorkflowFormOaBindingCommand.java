package com.xiyu.bid.workflowform.application.command;

import java.util.Map;

public record WorkflowFormOaBindingCommand(
        String templateCode,
        String provider,
        String workflowCode,
        Map<String, Object> fieldMapping,
        boolean enabled
) {
}
