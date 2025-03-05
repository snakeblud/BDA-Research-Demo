package org.lurence.kafka_power_bi_bridge.kafka;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MessageConsumer {
    private final CopyOnWriteArrayList<Map<String, Object>> recentMessages = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final static int MAX_TRANSACTIONS = 1000;

    @KafkaListener(topics = "powerbi-stream", groupId = "power_bi_consumer_group")
    public void listen(ConsumerRecord<Object, Object> record) {
        try {
            // Parse the value as JSON
            JsonNode valueNode = objectMapper.readTree(record.value().toString());

            JsonNode before = valueNode.get("before");
            JsonNode after = valueNode.get("after");

            String operationType = valueNode.get("op").asText();

            System.out.println(before.toString());
            System.out.println(after.toString());

            switch (operationType) {
                case "c":
                    System.out.println("Created");
                    recentMessages.add(jsonNodeToMap(after));
                    break;
                case "r":
                    System.out.println("Read");
                    recentMessages.add(jsonNodeToMap(after));
                    break;
                case "u":
                    System.out.println("Updated");
                    break;
                case "d":
                    System.out.println("Deleted");
                    break;
                default:
                    System.out.println("Unknown operation");
            }

            while (recentMessages.size() > MAX_TRANSACTIONS) {
                recentMessages.removeLast();
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }

    private Map<String, Object> jsonNodeToMap(JsonNode jsonNode) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(jsonNode, new TypeReference<>() {});
    }

    public List<Map<String, Object>> getRecentTransactions() {
        return new ArrayList<>(recentMessages);
    }
}
