//package org.lurence.kafka_power_bi_bridge.power_bi;
//
//import org.lurence.kafka_power_bi_bridge.kafka.MessageConsumer;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.*;
//
//@RestController
//@RequestMapping("/api/v1/data")
//public class DataController {
//    private final MessageConsumer messageConsumer;
//
//    @Autowired
//    public DataController(MessageConsumer messageConsumer) {
//        this.messageConsumer = messageConsumer;
//    }
//
//    @GetMapping("/transactions")
//    public ResponseEntity<List<Map<String, Object>>> getTransactions() {
//        return ResponseEntity.ok(messageConsumer.getRecentTransactions());
//    }
//
//    /**
//     * Enhanced endpoint that returns a flattened tabular structure better suited for Power BI
//     * This mimics the format of a direct CSV import
//     */
//    @GetMapping("/transactions/powerbi")
//    public ResponseEntity<List<Map<String, Object>>> getTransactionsForPowerBI() {
//        List<Map<String, Object>> transactions = messageConsumer.getRecentTransactions();
//
//        // Standardize the keys across all records
//        // This ensures all records have the same fields, making it look like a CSV table
//        Set<String> allKeys = new HashSet<>();
//        for (Map<String, Object> transaction : transactions) {
//            allKeys.addAll(transaction.keySet());
//        }
//
//        // Create a standardized list of records with consistent fields
//        List<Map<String, Object>> standardizedTransactions = new ArrayList<>();
//        for (Map<String, Object> transaction : transactions) {
//            Map<String, Object> standardizedTransaction = new HashMap<>();
//
//            // Make sure each record has all fields, even if null
//            for (String key : allKeys) {
//                // Convert keys to uppercase to match the format in the screenshot
//                String upperCaseKey = key.toUpperCase();
//                standardizedTransaction.put(upperCaseKey, transaction.getOrDefault(key, null));
//            }
//
//            standardizedTransactions.add(standardizedTransaction);
//        }
//
//        return ResponseEntity.ok(standardizedTransactions);
//    }
//
//    /**
//     * Alternate format with a tabular structure
//     */
//    @GetMapping("/transactions/table")
//    public ResponseEntity<Map<String, Object>> getTransactionsTable() {
//        List<Map<String, Object>> transactions = messageConsumer.getRecentTransactions();
//
//        // Return in a structured format better for Power BI
//        Map<String, Object> response = new HashMap<>();
//        response.put("data", transactions);
//        response.put("count", transactions.size());
//        response.put("columns", getColumnInfo(transactions));
//
//        return ResponseEntity.ok(response);
//    }
//
//    /**
//     * Helper method to get column information
//     */
//    private List<Map<String, String>> getColumnInfo(List<Map<String, Object>> transactions) {
//        Set<String> uniqueKeys = new HashSet<>();
//
//        // Collect all unique keys
//        for (Map<String, Object> transaction : transactions) {
//            uniqueKeys.addAll(transaction.keySet());
//        }
//
//        // Create column info
//        List<Map<String, String>> columns = new ArrayList<>();
//        for (String key : uniqueKeys) {
//            Map<String, String> column = new HashMap<>();
//            column.put("name", key);
//            column.put("type", guessType(transactions, key));
//            columns.add(column);
//        }
//
//        return columns;
//    }
//
//    /**
//     * Helper method to guess data types
//     */
//    private String guessType(List<Map<String, Object>> transactions, String key) {
//        for (Map<String, Object> transaction : transactions) {
//            if (transaction.containsKey(key) && transaction.get(key) != null) {
//                Object value = transaction.get(key);
//                if (value instanceof Number) return "number";
//                if (value instanceof Boolean) return "boolean";
//                if (value instanceof Date) return "datetime";
//                return "string";
//            }
//        }
//        return "string"; // default
//    }
//}


package org.lurence.kafka_power_bi_bridge.power_bi;

import org.lurence.kafka_power_bi_bridge.kafka.MessageConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Enhanced endpoint for Power BI that properly handles Struct format messages
     */
    @GetMapping("/powerbi")
    public ResponseEntity<List<Map<String, Object>>> getPowerBITransactions() {
        // Get the original transaction data
        List<Map<String, Object>> transactions = messageConsumer.getRecentTransactions();

        // Process each transaction, especially Struct formats
        List<Map<String, Object>> processedTransactions = new ArrayList<>();

        for (Map<String, Object> transaction : transactions) {
            Map<String, Object> processedTransaction = new HashMap<>();

            // Process each field in the transaction
            for (Map.Entry<String, Object> entry : transaction.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // Handle Struct format
                if (value instanceof String && ((String) value).startsWith("Struct{")) {
                    String structString = (String) value;

                    // Extract transactionid
                    Pattern transactionIdPattern = Pattern.compile("transactionid=(\\d+)");
                    Matcher matcher = transactionIdPattern.matcher(structString);
                    if (matcher.find()) {
                        processedTransaction.put("TRANSACTIONID", matcher.group(1));
                    }

                    // Extract and format transactiondate if present
                    Pattern datePattern = Pattern.compile("transactiondate=([\\d\\.E\\+]+)");
                    matcher = datePattern.matcher(structString);
                    if (matcher.find()) {
                        try {
                            String dateStr = matcher.group(1);
                            double timestamp = Double.parseDouble(dateStr);
                            Date date = new Date((long)timestamp);
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            processedTransaction.put("TRANSACTIONDATE", sdf.format(date));
                        } catch (Exception e) {
                            processedTransaction.put("TRANSACTIONDATE", matcher.group(1));
                        }
                    }

                    // Extract other common fields
                    extractField(structString, "accountfrom", processedTransaction);
                    extractField(structString, "accountto", processedTransaction);
                    extractField(structString, "bankidfrom", processedTransaction);
                    extractField(structString, "bankidto", processedTransaction);
                    extractField(structString, "currency", processedTransaction);
                    extractField(structString, "narrative", processedTransaction);
                    extractField(structString, "paymentmode", processedTransaction);
                    extractField(structString, "transactionamount", processedTransaction);
                } else if (value instanceof String && ((String) value).matches("\\d+\\.\\d+E\\+\\d+")) {
                    // Handle scientific notation for dates
                    try {
                        String dateStr = (String) value;
                        double timestamp = Double.parseDouble(dateStr);
                        Date date = new Date((long)timestamp);
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        processedTransaction.put(key.toUpperCase(), sdf.format(date));
                    } catch (Exception e) {
                        processedTransaction.put(key.toUpperCase(), value);
                    }
                } else {
                    // Just standardize the key to uppercase
                    processedTransaction.put(key.toUpperCase(), value);
                }
            }

            // Only add if we have data
            if (!processedTransaction.isEmpty()) {
                processedTransactions.add(processedTransaction);
            }
        }

        return ResponseEntity.ok(processedTransactions);
    }

    /**
     * The original powerbi endpoint that wasn't working well
     * Left for backward compatibility but marked as deprecated
     */
    @Deprecated
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

    /**
     * Helper method to extract a field from a Struct string
     */
    private void extractField(String structString, String fieldName, Map<String, Object> result) {
        Pattern pattern = Pattern.compile(fieldName + "=([^,}]+)");
        Matcher matcher = pattern.matcher(structString.toLowerCase());
        if (matcher.find()) {
            String value = matcher.group(1).trim();
            // Remove quotes if present
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            // Try to convert to number if possible
            try {
                if (value.contains(".")) {
                    result.put(fieldName.toUpperCase(), Double.parseDouble(value));
                } else {
                    result.put(fieldName.toUpperCase(), Long.parseLong(value));
                }
            } catch (NumberFormatException e) {
                result.put(fieldName.toUpperCase(), value);
            }
        }
    }
}