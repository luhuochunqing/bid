package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.kafka.KafkaProcessor;
import com.ehsy.eventlibrary.clientsdk.service.component.CacheBeanComponent;
import com.ehsy.eventlibrary.clientsdk.service.component.ClientRegisterComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bootstraps the entire SDK initialization chain because the SDK's own
 * {@code StartCallback} does not reliably fire.
 *
 * <p>Execution order:
 * <ol>
 *   <li>Register with the event bus ({@code /eventbus/register})</li>
 *   <li>Scan Spring beans for {@code @AcceptEvent} annotations</li>
 *   <li>Start the Kafka consumer threads</li>
 * </ol>
 */
@Component
@ConditionalOnClass(name = "com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent")
@ConditionalOnProperty(
        prefix = "xiyu.integrations.organization.event-sdk",
        name = "enabled",
        havingValue = "true"
)
@Slf4j
public class OrganizationEventSdkKafkaStarter {

    @Autowired
    private ApplicationContext ctx;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void onApplicationReady() {
        log.info("[org-event-sdk-kafka] === bootstrapping SDK initialization ===");

        try {
            ClientRegisterComponent registerComponent = ctx.getBean(ClientRegisterComponent.class);
            log.info("[org-event-sdk-kafka] calling ClientRegisterComponent.register()");
            registerComponent.register();
        } catch (RuntimeException e) {
            log.error("[org-event-sdk-kafka] registration failed: {}", e.getMessage(), e);
            return;
        }

        try {
            CacheBeanComponent cacheBean = ctx.getBean(CacheBeanComponent.class);
            log.info("[org-event-sdk-kafka] calling CacheBeanComponent.initCacheBean()");
            cacheBean.initCacheBean();
        } catch (RuntimeException e) {
            log.error("[org-event-sdk-kafka] bean scan failed: {}", e.getMessage(), e);
            return;
        }

        try {
            KafkaProcessor kp = ctx.getBean(KafkaProcessor.class);
            log.info("[org-event-sdk-kafka] calling KafkaProcessor.start()");
            kp.start();
            log.info("[org-event-sdk-kafka] Kafka consumer started successfully");
        } catch (RuntimeException e) {
            log.error("[org-event-sdk-kafka] Kafka start failed: {}", e.getMessage(), e);
        }
    }
}
