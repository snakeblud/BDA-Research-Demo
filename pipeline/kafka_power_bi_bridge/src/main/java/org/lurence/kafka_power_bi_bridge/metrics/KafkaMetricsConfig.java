package org.lurence.kafka_power_bi_bridge.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.event.ListenerContainerIdleEvent;
import org.springframework.kafka.event.NonResponsiveConsumerEvent;
import org.springframework.context.event.EventListener;

/**
 * Configuration class for custom Kafka metrics
 */
@Configuration
public class KafkaMetricsConfig {

    private final MeterRegistry meterRegistry;
    private Counter messagesProcessedCounter;
    private Counter kafkaErrorCounter;

    @Autowired
    public KafkaMetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Create counters for Kafka-related metrics
        this.messagesProcessedCounter = Counter.builder("kafka.consumer.messages.processed")
                .description("Number of Kafka messages processed")
                .tag("application", "kafka-power-bi-bridge")
                .register(meterRegistry);

        this.kafkaErrorCounter = Counter.builder("kafka.consumer.errors")
                .description("Number of Kafka consumer errors")
                .tag("application", "kafka-power-bi-bridge")
                .register(meterRegistry);
    }

    /**
     * Method to increment the messages processed counter
     */
    public void incrementMessagesProcessed() {
        messagesProcessedCounter.increment();
    }

    /**
     * Method to increment the error counter
     */
    public void incrementErrors() {
        kafkaErrorCounter.increment();
    }

    /**
     * Event listener for Kafka consumer idle events
     */
    @EventListener
    public void handleIdleEvent(ListenerContainerIdleEvent event) {
        // You can add custom metrics for idle events
        meterRegistry.counter("kafka.consumer.idle.events").increment();
    }

    /**
     * Event listener for non-responsive Kafka consumer events
     */
    @EventListener
    public void handleNonResponsiveConsumer(NonResponsiveConsumerEvent event) {
        // You can add custom metrics for non-responsive events
        meterRegistry.counter("kafka.consumer.nonresponsive.events").increment();
    }
}