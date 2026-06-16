package com.xiyu.bid.integration.organization.infrastructure.sdk;

import com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import java.util.Properties;

/**
 * 覆盖 SDK 的 KafkaProducer Bean，提供与 Kafka 0.10.1.0 兼容的配置。
 *
 * <p>SDK 的原生 kafkaProducer() 只设置了 bootstrap.servers、
 * key.serializer 和 value.serializer 三个参数，
 * kafka-clients 3.6.1 的默认值（magic byte 2 等）在
 * Kafka 0.10.1.0 broker 上会触发
 * UnsupportedForMessageFormatException。
 *
 * <p>此类通过 @Primary 覆盖 SDK 的 Bean，显式设置
 * 与 0.10.x 兼容的 producer 配置。
 */
@Configuration
@ConditionalOnClass(name = "com.ehsy.eventlibrary.clientsdk.common.anno.AcceptEvent")
@ConditionalOnProperty(
        prefix = "xiyu.integrations.organization.event-sdk",
        name = "enabled",
        havingValue = "true"
)
public class OrganizationEventSdkKafkaProducerConfig {

    @Bean
    @Primary
    public KafkaProducer<String, String> kafkaProducer(Environment env) {
        String brokerList = env.getProperty("broker.configure.server-list", "");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerList);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        props.put(ProducerConfig.ACKS_CONFIG, "1");

        // 标准调优参数
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "16384");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "1");
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "1048576");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "30000");
        props.put(ProducerConfig.METADATA_MAX_AGE_CONFIG, "300000");
        props.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, "50");
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "100");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "xiyu-bid-org-consumer-producer");

        return new KafkaProducer<>(props);
    }
}
