package org.lurence.kafka_power_bi_bridge.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.connect.json.JsonDeserializer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "power_bi_consumer_group");

        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getCanonicalName());
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class.getCanonicalName());

        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
