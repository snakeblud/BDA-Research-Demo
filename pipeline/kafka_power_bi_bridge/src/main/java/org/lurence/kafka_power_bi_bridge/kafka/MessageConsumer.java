package org.lurence.kafka_power_bi_bridge.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class MessageConsumer {
    private final CopyOnWriteArrayList<String> recentMessages = new CopyOnWriteArrayList<>();

    private final static int MAX_TRANSACTIONS = 1000;

    @KafkaListener(topics = "powerbi-stream", groupId = "power_bi_consumer_group")
    public void listen(String message) {
        System.out.println("Received message: " + message);

        try {
            recentMessages.add(message);

            while (recentMessages.size() > MAX_TRANSACTIONS) {
                recentMessages.removeLast();
            }
        } catch (Exception e) {
            System.err.println("Error processing message: " + e.getMessage());
        }
    }

    public List<String> getRecentTransactions() {
        return new ArrayList<>(recentMessages);
    }
}
