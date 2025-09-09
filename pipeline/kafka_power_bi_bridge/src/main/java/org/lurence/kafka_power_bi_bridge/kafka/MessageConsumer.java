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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MessageConsumer {
    // NEW: high-value counter
    private final Counter highValueTransactions = Counter.builder("transactions_high_value_total")
            .description("Number of high-value transactions (>1000)")
            .register(Metrics.globalRegistry);

    private final CopyOnWriteArrayList<Map<String, Object>> recentMessages = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KafkaMetricsConfig metricsConfig;

    private static final int MAX_TRANSACTIONS = 1000;

    // Existing counters
    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter amountCounter;

    @Autowired
    public MessageConsumer(KafkaMetricsConfig metricsConfig, MeterRegistry registry) {
        this.metricsConfig = metricsConfig;

        this.successCounter = Counter.builder("transactions_total")
                .tag("status", "success")
                .description("Total successful transactions")
                .register(registry);

        this.failureCounter = Counter.builder("transactions_total")
                .tag("status", "failure")
                .description("Total failed transactions")
                .register(registry);

        this.amountCounter = Counter.builder("transactions_amount_sum_total")
                .description("Sum of transaction amounts")
                .register(registry);
    }

    @KafkaListener(topics = "powerbi-stream", groupId = "power_bi_consumer_group")
    public void listen(ConsumerRecord<String, String> record) {
        try {
            String value = record.value();
            System.out.println("Received raw message: " + value);

            // Case 1: Debezium/Hazelcast Struct string
            if (value.startsWith("Struct")) {
                Map<String, Object> data = parseStructAfterFields(value); // flatten AFTER fields
                recentMessages.add(data);
                System.out.println("Processed Struct 'after' message: " + data);

                recordMetrics(data);
                metricsConfig.incrementMessagesProcessed();
                trimBuffer();
                return;
            }

            // Case 2: JSON (either Debezium-style with before/after/op, or plain JSON)
            try {
                JsonNode root = objectMapper.readTree(value);

                if (root.has("op")) {
                    // Debezium JSON
                    JsonNode after = root.get("after");
                    if (after != null && !after.isNull()) {
                        Map<String, Object> data = jsonNodeToMap(after);
                        recentMessages.add(data);
                        System.out.println("Processed Debezium JSON message: " + data);

                        recordMetrics(data);
                    } else {
                        // message without 'after' – count as failure but still track
                        failureCounter.increment();
                    }
                } else {
                    // Plain JSON object
                    Map<String, Object> data = objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
                    recentMessages.add(data);
                    System.out.println("Processed JSON message: " + data);

                    recordMetrics(data);
                }

                metricsConfig.incrementMessagesProcessed();
            } catch (JsonProcessingException e) {
                // Case 3: treat as plain text
                System.err.println("Failed to parse as JSON, storing as text: " + e.getMessage());
                Map<String, Object> data = new HashMap<>();
                data.put("message", value);
                recentMessages.add(data);

                // failures are still "processed" from the consumer’s perspective
                failureCounter.increment();
                metricsConfig.incrementMessagesProcessed();
            }

            trimBuffer();
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
            metricsConfig.incrementErrors();
            failureCounter.increment();
        }
    }

    /** Record all counters based on parsed data (handles transactionamount/amount/TRANSACTIONAMOUNT). */
    private void recordMetrics(Map<String, Object> data) {
        successCounter.increment();

        Double amt = extractAmount(data);
        if (amt != null) {
            amountCounter.increment(amt);
            if (amt > 1000.0) {
                highValueTransactions.increment(); // <-- this is the metric your Grafana panel reads
            }
        }
    }

    /** Try to get the amount field regardless of casing/key variants. */
    private Double extractAmount(Map<String, Object> data) {
        Object v = null;
        if (data.containsKey("transactionamount")) v = data.get("transactionamount");
        else if (data.containsKey("amount")) v = data.get("amount");
        else if (data.containsKey("TRANSACTIONAMOUNT")) v = data.get("TRANSACTIONAMOUNT");

        if (v == null) return null;
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    /**
     * Parse the Debezium Struct string and return ONLY the flattened fields inside `after=Struct{...}`.
     * Example input:
     * Struct{after=Struct{transactionid=...,transactionamount=...},source=Struct{...},op=c,ts_ms=...}
     */
    private Map<String, Object> parseStructAfterFields(String structMessage) {
        Map<String, Object> result = new HashMap<>();
        try {
            int afterStart = structMessage.indexOf("after=Struct{");
            if (afterStart == -1) {
                // Fallback: parse top-level (older behavior)
                return parseStructKeyValues(structMessage);
            }
            int start = afterStart + "after=Struct{".length();

            // Try to find the end of the 'after' struct.
            int end = structMessage.indexOf("},source=", start);
            if (end == -1) {
                end = structMessage.lastIndexOf('}');
            }
            if (end > start) {
                String inner = structSafeSubstring(structMessage, start, end);
                result.putAll(parseKeyValues(inner));
            }
        } catch (Exception e) {
            System.err.println("Error parsing 'after' Struct: " + e.getMessage());
            metricsConfig.incrementErrors();
        }
        return result;
    }

    /** Fallback: parse a flat Struct{a=b,c=d} into a map (used if there's no 'after=Struct{...}'). */
    private Map<String, Object> parseStructKeyValues(String structMessage) {
        Map<String, Object> out = new HashMap<>();
        try {
            int l = structMessage.indexOf('{');
            int r = structMessage.lastIndexOf('}');
            if (l >= 0 && r > l) {
                String inner = structSafeSubstring(structMessage, l + 1, r);
                out.putAll(parseKeyValues(inner));
            }
        } catch (Exception e) {
            System.err.println("Error parsing Struct key/values: " + e.getMessage());
            metricsConfig.incrementErrors();
        }
        return out;
    }

    /** Parse comma-separated key=value pairs, respecting quoted values. */
    private Map<String, Object> parseKeyValues(String content) {
        Map<String, Object> result = new HashMap<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        List<String> parts = new ArrayList<>();

        for (char c : content.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());

        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String val = kv[1].trim();
                if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                    val = val.substring(1, val.length() - 1);
                }
                result.put(key.toLowerCase(), val); // normalize keys to lower-case
            }
        }
        return result;
    }

    private String structSafeSubstring(String s, int start, int end) {
        if (start < 0) start = 0;
        if (end > s.length()) end = s.length();
        if (end < start) end = start;
        return s.substring(start, end);
    }

    private Map<String, Object> jsonNodeToMap(JsonNode jsonNode) {
        return objectMapper.convertValue(jsonNode, new TypeReference<>() {});
    }

    private void trimBuffer() {
        while (recentMessages.size() > MAX_TRANSACTIONS) {
            recentMessages.remove(0);
        }
    }

    public List<Map<String, Object>> getRecentTransactions() {
        return new ArrayList<>(recentMessages);
    }
}