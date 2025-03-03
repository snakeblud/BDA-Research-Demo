package org.lurence.kafka_power_bi_bridge.power_bi;

import org.lurence.kafka_power_bi_bridge.kafka.MessageConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {
    private final MessageConsumer messageConsumer;

    @Autowired
    public DataController(MessageConsumer messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<String>> getTransactions() {
        return ResponseEntity.ok(messageConsumer.getRecentTransactions());
    }
}
