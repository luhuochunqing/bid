package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.config.BusinessConfiguration;
import com.ehsy.eventlibrary.clientsdk.config.SDKClientConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnClass(name = "com.ehsy.eventlibrary.clientsdk.config.SDKClientConfiguration")
@ConditionalOnProperty(prefix = "xiyu.integrations.organization.event-sdk", name = "enabled", havingValue = "true")
@Import({SDKClientConfiguration.class, BusinessConfiguration.class})
public class OrganizationEventSdkManualImportConfiguration {
}
