package com.xiyu.bid.integration.organization.infrastructure.sdk;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationEventSdkSpringWiringProbe implements ApplicationRunner {

    private final ConfigurableApplicationContext ctx;
    private final Environment env;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[org-event-sdk-wiring] === begin ===");

        // 1) Show whether SDK auto-config classes are actually registered as beans.
        dumpBeanNamesOfType("SDKClientConfiguration", "com.ehsy.eventlibrary.clientsdk.config.SDKClientConfiguration");
        dumpBeanNamesOfType("BusinessConfiguration", "com.ehsy.eventlibrary.clientsdk.config.BusinessConfiguration");

        // 2) Show critical SDK components.
        dumpBeanNamesOfType("EventConsumerComponent", "com.ehsy.eventlibrary.clientsdk.service.component.EventConsumerComponent");
        dumpBeanNamesOfType("ClientRegisterComponent", "com.ehsy.eventlibrary.clientsdk.service.component.ClientRegisterComponent");
        dumpBeanNamesOfType("ServiceRenewalComponent", "com.ehsy.eventlibrary.clientsdk.service.component.ServiceRenewalComponent");
        dumpBeanNamesOfType("EventConfigurationCacheComponent", "com.ehsy.eventlibrary.clientsdk.service.component.EventConfigurationCacheComponent");
        dumpBeanNamesOfType("TransitServiceHandleComponent", "com.ehsy.eventlibrary.clientsdk.service.component.TransitServiceHandleComponent");

        // 3) Dump environment keys that are likely used by the SDK.
        // We don't know the exact @ConfigurationProperties prefix yet, but the doc suggests these.
        dumpProps(
                "client.register.serviceName",
                "client.register.serverRegisterUrl",
                "client.register.enableRegister",
                "client.renewal.initialDelay",
                "client.renewal.period",
                "client.renewal.renewalDuration",
                "broker.configure.serverList",
                "broker.configure.zkServers",
                "broker.configure.env"
        );

        log.info("[org-event-sdk-wiring] === end ===");
    }

    private void dumpBeanNamesOfType(String label, String className) {
        try {
            Class<?> clazz = Class.forName(className);
            String[] names = ctx.getBeanNamesForType(clazz);
            log.info("[org-event-sdk-wiring] beansOfType {} ({}): {}", label, className, String.join(",", names));
        } catch (Throwable ex) {
            log.warn("[org-event-sdk-wiring] beansOfType {} ({}): <failed> ({})", label, className, ex.toString());
        }
    }

    private void dumpProps(String... keys) {
        Map<String, String> map = new TreeMap<>();
        for (String k : keys) {
            map.put(k, String.valueOf(env.getProperty(k)));
        }
        String body = map.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        log.info("[org-event-sdk-wiring] env: {}", body);
    }
}
