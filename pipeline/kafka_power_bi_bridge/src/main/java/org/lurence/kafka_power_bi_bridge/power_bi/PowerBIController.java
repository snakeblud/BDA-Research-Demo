package org.lurence.kafka_power_bi_bridge.power_bi;

import org.lurence.kafka_power_bi_bridge.kafka.MessageConsumer;
import org.lurence.kafka_power_bi_bridge.kafka.StructMessageParser;
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
@RequestMapping("/api/v1/powerbi")
public class PowerBIController {
    private final MessageConsumer messageConsumer;

    @Autowired
    public PowerBIController(MessageConsumer messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    /**
     * Endpoint specifically formatted for Power BI consumption
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<Map<String, Object>>> getFormattedTransactions() {
        List<Map<String, Object>> rawTransactions = messageConsumer.getRecentTransactions();
        List<Map<String, Object>> formattedTransactions = new ArrayList<>();

        for (Map<String, Object> transaction : rawTransactions) {
            Map<String, Object> formattedTransaction = new HashMap<>();

            for (Map.Entry<String, Object> entry : transaction.entrySet()) {
                String key = entry.getKey().toUpperCase();
                Object value = entry.getValue();

                // Special handling for different value types
                if (value instanceof String) {
                    String stringValue = (String) value;

                    // Handle Struct format
                    if (stringValue.startsWith("Struct{")) {
                        // Extract transaction ID
                        Pattern transactionIdPattern = Pattern.compile("transactionid=(\\d+)");
                        Matcher matcher = transactionIdPattern.matcher(stringValue);
                        if (matcher.find()) {
                            formattedTransaction.put("TRANSACTIONID", matcher.group(1));
                        }

                        // Extract transaction date if present
                        Pattern datePattern = Pattern.compile("transactiondate=(\\d+)");
                        matcher = datePattern.matcher(stringValue);
                        if (matcher.find()) {
                            try {
                                long timestamp = Long.parseLong(matcher.group(1));
                                Date date = new Date(timestamp);
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                formattedTransaction.put("TRANSACTIONDATE", sdf.format(date));
                            } catch (Exception e) {
                                formattedTransaction.put("TRANSACTIONDATE", matcher.group(1));
                            }
                        }

                        // Add other fields by parsing the struct
                        Map<String, Object> parsedStruct = parseSimpleStruct(stringValue);
                        for (Map.Entry<String, Object> structField : parsedStruct.entrySet()) {
                            if (!formattedTransaction.containsKey(structField.getKey())) {
                                formattedTransaction.put(structField.getKey(), structField.getValue());
                            }
                        }
                    } else if (stringValue.matches("\\d+\\.\\d+E\\+\\d+")) {
                        // Handle scientific notation timestamps
                        try {
                            double timestamp = Double.parseDouble(stringValue);
                            Date date = new Date((long)timestamp);
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                            formattedTransaction.put(key, sdf.format(date));
                        } catch (Exception e) {
                            formattedTransaction.put(key, value);
                        }
                    } else {
                        formattedTransaction.put(key, value);
                    }
                } else {
                    formattedTransaction.put(key, value);
                }
            }

            if (!formattedTransaction.isEmpty()) {
                formattedTransactions.add(formattedTransaction);
            }
        }

        return ResponseEntity.ok(formattedTransactions);
    }

    /**
     * Simpler struct parser for specific fields we need
     */
    private Map<String, Object> parseSimpleStruct(String structString) {
        Map<String, Object> result = new HashMap<>();

        // Extract key fields we're interested in
        String[] fieldsToExtract = {
                "accountfrom", "accountto", "currency", "narrative",
                "paymentmode", "transactionamount", "bankidfrom", "bankidto"
        };

        for (String field : fieldsToExtract) {
            Pattern pattern = Pattern.compile(field + "=([^,}]+)");
            Matcher matcher = pattern.matcher(structString.toLowerCase());
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                result.put(field.toUpperCase(), value);
            }
        }

        return result;
    }
}