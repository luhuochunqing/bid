package com.xiyu.bid.integration.organization.infrastructure.sdk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Startup probe to make the EHSY ClientSDK wiring observable in production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationEventSdkStartupProbe implements ApplicationRunner {

    private final Environment env;

    @Override
    public void run(ApplicationArguments args) {
        var sdkEnabled = env.getProperty("xiyu.integrations.organization.event-sdk.enabled");
        var consumerGroup = env.getProperty("xiyu.integrations.organization.event-sdk.consumer-group");

        var serviceName = env.getProperty("client.register.serviceName");
        var serverRegisterUrl = env.getProperty("client.register.serverRegisterUrl");
        var enableRegister = env.getProperty("client.register.enableRegister");
        var initialDelay = env.getProperty("client.renewal.initialDelay");
        var period = env.getProperty("client.renewal.period");
        var renewalDuration = env.getProperty("client.renewal.renewalDuration");

        var brokerServerList = env.getProperty("broker.configure.serverList");
        var zkServers = env.getProperty("broker.configure.zkServers");
        var brokerEnv = env.getProperty("broker.configure.env");

        log.info(
                "[org-event-sdk-probe] props: eventSdkEnabled={}, consumerGroup={}, serviceName={}, serverRegisterUrl={}, enableRegister={}, renewal(initialDelay={}, period={}, renewalDuration={}), broker(serverList={}, zkServers={}, env={})",
                sdkEnabled,
                safe(consumerGroup),
                safe(serviceName),
                safe(serverRegisterUrl),
                enableRegister,
                initialDelay,
                period,
                renewalDuration,
                safe(brokerServerList),
                safe(zkServers),
                safe(brokerEnv)
        );

        probeClass("com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent");
        probeClass("com.ehsy.eventlibrary.clientsdk.common.resp.SendEventRespDto");
        probeClass("com.ehsy.eventlibrary.clientsdk.config.SDKClientConfiguration");
        probeClass("com.ehsy.eventlibrary.clientsdk.service.component.EventConsumerComponent");

        probeSpringFactoryAutoConfig(
                "com.ehsy.eventlibrary.clientsdk.config.SDKClientConfiguration",
                "com.ehsy.eventlibrary.clientsdk.config.BusinessConfiguration"
        );

        // Also prove whether our adapter bean class is even loadable.
        probeClass("com.xiyu.bid.integration.organization.infrastructure.sdk.OrganizationEventSdkConsumerAdapter");
    }

    private void probeClass(String name) {
        try {
            Class.forName(name);
            log.info("[org-event-sdk-probe] classPresent: {}", name);
        } catch (Throwable ex) {
            log.warn("[org-event-sdk-probe] classMissing: {} ({})", name, ex.toString());
        }
    }

    private void probeSpringFactoryAutoConfig(String... classNames) {
        for (String name : classNames) {
            try {
                var clazz = Class.forName(name);
                var loader = clazz.getClassLoader();
                var resources = loader.getResources("META-INF/spring.factories");
                var count = 0;
                while (resources.hasMoreElements()) {
                    resources.nextElement();
                    count++;
                }
                log.info("[org-event-sdk-probe] springFactoriesVisible: class={}, resourcesCount={}", name, count);
            } catch (Throwable ex) {
                log.warn("[org-event-sdk-probe] springFactoriesProbeFailed: class={} ({})", name, ex.toString());
            }
        }
    }

    private static String safe(String v) {
        if (v == null || v.isBlank()) {
            return "<blank>";
        }
        return v;
    }
}
