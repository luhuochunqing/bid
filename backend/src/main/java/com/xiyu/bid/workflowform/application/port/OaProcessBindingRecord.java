package com.xiyu.bid.workflowform.application.port;

import java.util.Map;

public record OaProcessBindingRecord(
        String templateCode,
        String provider,
        String workflowCode,
        Map<String, Object> fieldMapping,
        boolean enabled
) {
}
