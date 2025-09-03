package org.lurence.kafka_power_bi_bridge.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.lurence.kafka_power_bi_bridge.metrics.KafkaMetricsConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MessageConsumer {
    private final CopyOnWriteArrayList<Map<String, Object>> recentMessages = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaMetricsConfig metricsConfig;

    private final static int MAX_TRANSACTIONS = 1000;

    // ✅ Micrometer counters
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter amountCounter;

    @Autowired
    public MessageConsumer(KafkaMetricsConfig metricsConfig, MeterRegistry registry) {
        this.metricsConfig = metricsConfig;

        // Micrometer counters
        this.successCounter = Counter.builder("transactions_total")
                .tag("status", "success")
                .description("Total successful transactions")
                .register(registry);

        this.failureCounter = Counter.builder("transactions_total")
                .tag("status", "failure")
                .description("Total failed transactions")
                .register(registry);

        this.amountCounter = Counter.builder("transactions_amount_sum")
                .description("Sum of transaction amounts")
                .register(registry);
    }

    @KafkaListener(topics = "powerbi-stream", groupId = "power_bi_consumer_group")
    public void listen(ConsumerRecord<String, String> record) {
        try {
            System.out.println("Received raw message: " + record.value());

            String value = record.value();
            if (value.startsWith("Struct")) {
                // Handle Struct format
                Map<String, Object> structData = parseStructMessage(value);
                recentMessages.add(structData);
                System.out.println("Processed Struct message: " + structData);

                // Increment metrics
                metricsConfig.incrementMessagesProcessed();
                successCounter.increment();
                if (structData.containsKey("amount")) {
                    try {
                        double amt = Double.parseDouble(structData.get("amount").toString());
                        amountCounter.increment(amt);
                    } catch (NumberFormatException ignored) {}
                }
                return;
            }

            try {
                JsonNode valueNode = objectMapper.readTree(value);

                if (valueNode.has("op")) {
                    String operationType = valueNode.get("op").asText();
                    JsonNode after = valueNode.get("after");

                    System.out.println("Operation type: " + operationType);

                    if (after != null && !after.isNull()) {
                        Map<String, Object> data = jsonNodeToMap(after);
                        recentMessages.add(data);
                        System.out.println("Processed message: " + data);

                        // ✅ Increment counters
                        successCounter.increment();
                        if (data.containsKey("amount")) {
                            try {
                                double amt = Double.parseDouble(data.get("amount").toString());
                                amountCounter.increment(amt);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                } else {
                    Map<String, Object> data = objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
                    recentMessages.add(data);
                    System.out.println("Processed JSON message: " + data);

                    // ✅ Increment counters
                    successCounter.increment();
                    if (data.containsKey("amount")) {
                        try {
                            double amt = Double.parseDouble(data.get("amount").toString());
                            amountCounter.increment(amt);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                metricsConfig.incrementMessagesProcessed();
            } catch (JsonProcessingException e) {
                System.err.println("Failed to parse as JSON, treating as plain text: " + e.getMessage());
                Map<String, Object> data = new HashMap<>();
                data.put("message", value);
                recentMessages.add(data);

                // ✅ Treat as failure
                failureCounter.increment();
                metricsConfig.incrementMessagesProcessed();
            }

            // Maintain max size
            while (recentMessages.size() > MAX_TRANSACTIONS) {
                recentMessages.remove(0);
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();

            // Increment error metrics
            metricsConfig.incrementErrors();
            failureCounter.increment();
        }
    }

    private Map<String, Object> parseStructMessage(String structMessage) {
        Map<String, Object> result = new HashMap<>();
        try {
            int startIndex = structMessage.indexOf("{");
            int endIndex = structMessage.lastIndexOf("}");

            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                String content = structMessage.substring(startIndex + 1, endIndex);

                boolean inQuotes = false;
                StringBuilder currentPart = new StringBuilder();
                List<String> parts = new ArrayList<>();

                for (char c : content.toCharArray()) {
                    if (c == '"') {
                        inQuotes = !inQuotes;
                    }

                    if (c == ',' && !inQuotes) {
                        parts.add(currentPart.toString());
                        currentPart = new StringBuilder();
                    } else {
                        currentPart.append(c);
                    }
                }

                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString());
                }

                for (String part : parts) {
                    String[] keyValue = part.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim();
                        String value = keyValue[1].trim();

                        if (value.startsWith("\"") && value.endsWith("\"")) {
                            value = value.substring(1, value.length() - 1);
                        }

                        result.put(key, value);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing Struct message: " + e.getMessage());
            result.put("raw_message", structMessage);
            metricsConfig.incrementErrors();
            failureCounter.increment();
        }
        return result;
    }

    private Map<String, Object> jsonNodeToMap(JsonNode jsonNode) {
        return objectMapper.convertValue(jsonNode, new TypeReference<>() {});
    }

    public List<Map<String, Object>> getRecentTransactions() {
        return new ArrayList<>(recentMessages);
    }
}