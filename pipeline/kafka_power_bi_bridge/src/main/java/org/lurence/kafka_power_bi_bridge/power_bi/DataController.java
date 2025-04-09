package org.lurence.kafka_power_bi_bridge.power_bi;

import org.lurence.kafka_power_bi_bridge.kafka.MessageConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/v1/data")
public class DataController {
    private final MessageConsumer messageConsumer;

    @Autowired
    public DataController(MessageConsumer messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<Map<String, Object>>> getTransactions() {
        return ResponseEntity.ok(messageConsumer.getRecentTransactions());
    }

    /**
     * Enhanced endpoint that returns a flattened tabular structure better suited for Power BI
     * This mimics the format of a direct CSV import
     */
    @GetMapping("/transactions/powerbi")
    public ResponseEntity<List<Map<String, Object>>> getTransactionsForPowerBI() {
        List<Map<String, Object>> transactions = messageConsumer.getRecentTransactions();

        // Standardize the keys across all records
        // This ensures all records have the same fields, making it look like a CSV table
        Set<String> allKeys = new HashSet<>();
        for (Map<String, Object> transaction : transactions) {
            allKeys.addAll(transaction.keySet());
        }

        // Create a standardized list of records with consistent fields
        List<Map<String, Object>> standardizedTransactions = new ArrayList<>();
        for (Map<String, Object> transaction : transactions) {
            Map<String, Object> standardizedTransaction = new HashMap<>();

            // Make sure each record has all fields, even if null
            for (String key : allKeys) {
                // Convert keys to uppercase to match the format in the screenshot
                String upperCaseKey = key.toUpperCase();
                standardizedTransaction.put(upperCaseKey, transaction.getOrDefault(key, null));
            }

            standardizedTransactions.add(standardizedTransaction);
        }

        return ResponseEntity.ok(standardizedTransactions);
    }

    /**
     * Alternate format with a tabular structure
     */
    @GetMapping("/transactions/table")
    public ResponseEntity<Map<String, Object>> getTransactionsTable() {
        List<Map<String, Object>> transactions = messageConsumer.getRecentTransactions();

        // Return in a structured format better for Power BI
        Map<String, Object> response = new HashMap<>();
        response.put("data", transactions);
        response.put("count", transactions.size());
        response.put("columns", getColumnInfo(transactions));

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to get column information
     */
    private List<Map<String, String>> getColumnInfo(List<Map<String, Object>> transactions) {
        Set<String> uniqueKeys = new HashSet<>();

        // Collect all unique keys
        for (Map<String, Object> transaction : transactions) {
            uniqueKeys.addAll(transaction.keySet());
        }

        // Create column info
        List<Map<String, String>> columns = new ArrayList<>();
        for (String key : uniqueKeys) {
            Map<String, String> column = new HashMap<>();
            column.put("name", key);
            column.put("type", guessType(transactions, key));
            columns.add(column);
        }

        return columns;
    }

    /**
     * Helper method to guess data types
     */
    private String guessType(List<Map<String, Object>> transactions, String key) {
        for (Map<String, Object> transaction : transactions) {
            if (transaction.containsKey(key) && transaction.get(key) != null) {
                Object value = transaction.get(key);
                if (value instanceof Number) return "number";
                if (value instanceof Boolean) return "boolean";
                if (value instanceof Date) return "datetime";
                return "string";
            }
        }
        return "string"; // default
    }
}