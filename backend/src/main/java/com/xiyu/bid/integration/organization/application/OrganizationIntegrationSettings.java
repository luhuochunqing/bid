package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties.Directory;
import java.util.List;

public record OrganizationIntegrationSettings(
        boolean enabled,
        String webhookSecret,
        List<String> allowedSourceApps,
        String ipWhitelist,
        DirectoryConfig directory
) {
    public record DirectoryConfig(
            String baseUrl,
            String userDetailPath,
            String deptDetailPath,
            String userWindowPath,
            String deptWindowPath,
            boolean eventSdkEnabled,
            String eventConsumerGroup,
            String eventServerRegisterUrl
    ) {
        public static DirectoryConfig fromProperties(OrganizationIntegrationProperties.Directory props) {
            return new DirectoryConfig(
                    props.getBaseUrl(),
                    props.getUserDetailPath(),
                    props.getDepartmentDetailPath(),
                    props.getUserWindowPath(),
                    props.getDepartmentWindowPath(),
                    false,
                    "bid-org-consumer-test",
                    ""
            );
        }
    }

    public boolean sourceAllowed(String sourceApp) {
        return allowedSourceApps.stream().anyMatch(sourceApp::equals);
    }

    public boolean ipAllowed(String remoteAddress) {
        if (blank(ipWhitelist)) {
            return true;
        }
        if (blank(remoteAddress)) {
            return false;
        }
        String normalizedAddress = remoteAddress.trim();
        for (String item : ipWhitelist.split("[,;\\s]+")) {
            if (normalizedAddress.equals(item.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
