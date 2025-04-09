package org.lurence.kafka_power_bi_bridge.kafka;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced utilities for parsing Struct messages from Kafka
 */
public class StructMessageParser {

    /**
     * Parse a Struct message into a well-formatted Map
     */
    public static Map<String, Object> parseStructMessage(String structMessage) {
        Map<String, Object> result = new HashMap<>();

        // Extract transaction ID using regex
        Pattern transactionIdPattern = Pattern.compile("Struct\\{transactionid=(\\d+)");
        Matcher matcher = transactionIdPattern.matcher(structMessage);
        if (matcher.find()) {
            result.put("TRANSACTIONID", matcher.group(1));
        }

        // Parse other fields
        try {
            // Extract content between Struct{ and }
            int startIndex = structMessage.indexOf("{");
            int endIndex = structMessage.lastIndexOf("}");

            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                String content = structMessage.substring(startIndex + 1, endIndex);

                // Split by commas, but be careful about commas inside quotes
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

                // Add the last part
                if (currentPart.length() > 0) {
                    parts.add(currentPart.toString());
                }

                // Parse each field=value pair
                for (String part : parts) {
                    String[] keyValue = part.split("=", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().toUpperCase(); // Convert to uppercase
                        String value = keyValue[1].trim();

                        // Handle specific field types
                        if (key.equals("TRANSACTIONDATE")) {
                            // Try to parse the timestamp and format it nicely
                            try {
                                long timestamp = Long.parseLong(value);
                                Date date = new Date(timestamp);
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                result.put(key, sdf.format(date));
                            } catch (NumberFormatException e) {
                                // If parsing fails, just use the original value
                                result.put(key, value);
                            }
                        } else {
                            // Remove quotes from value if present
                            if (value.startsWith("\"") && value.endsWith("\"")) {
                                value = value.substring(1, value.length() - 1);
                            }

                            // Try to parse numeric values
                            try {
                                if (value.contains(".")) {
                                    result.put(key, Double.parseDouble(value));
                                } else {
                                    result.put(key, Long.parseLong(value));
                                }
                            } catch (NumberFormatException e) {
                                result.put(key, value);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback - store the raw message
            result.put("RAW_MESSAGE", structMessage);
        }

        return result;
    }

    /**
     * Format data for Power BI consumption
     */
    public static List<Map<String, Object>> formatTransactionsForPowerBI(List<Map<String, Object>> transactions) {
        List<Map<String, Object>> formattedTransactions = new ArrayList<>();

        for (Map<String, Object> transaction : transactions) {
            Map<String, Object> formattedTransaction = new HashMap<>();

            // Process each field in the transaction
            for (Map.Entry<String, Object> entry : transaction.entrySet()) {
                String key = entry.getKey().toUpperCase(); // Standardize to uppercase
                Object value = entry.getValue();

                // Special handling for Struct objects
                if (value instanceof String && ((String) value).startsWith("Struct{")) {
                    // Parse the nested Struct
                    Map<String, Object> parsedStruct = parseStructMessage((String) value);

                    // Add all parsed fields to the main transaction
                    for (Map.Entry<String, Object> structField : parsedStruct.entrySet()) {
                        formattedTransaction.put(structField.getKey(), structField.getValue());
                    }
                } else {
                    formattedTransaction.put(key, value);
                }
            }

            formattedTransactions.add(formattedTransaction);
        }

        return formattedTransactions;
    }
}