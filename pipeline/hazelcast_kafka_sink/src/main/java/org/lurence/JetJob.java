package org.lurence;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.config.ProcessingGuarantee;
import com.hazelcast.jet.kafka.KafkaSinks;
import com.hazelcast.jet.kafka.KafkaSources;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.sql.SqlResult;
import com.hazelcast.sql.SqlService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class JetJob {
    static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss:SSS");

    public static void main(String[] args) {
        addKafkaTopic();

        // Hazelcast config
        Config config = new Config();
        config.setClusterName("analytics-cluster");
        config.getMetricsConfig().setEnabled(true);
        config.setProperty("hazelcast.memory.max.size", "1024");

        config.getJetConfig().setEnabled(true);
        config.getJetConfig().setResourceUploadEnabled(true);
        config.getJetConfig().setCooperativeThreadCount(4);

        // Multicast join
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();
        joinConfig.getMulticastConfig().setEnabled(true);
        joinConfig.getTcpIpConfig().setEnabled(false);

        HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);

        // Optional: simple IMap mapping
        SqlService sql = hz.getSql();
        try (SqlResult ignored = sql.execute(
                "CREATE MAPPING roles_map TYPE IMap " +
                        "OPTIONS ('keyFormat'='varchar','valueFormat'='varchar')")) {
            System.out.println("Mapping created successfully");
        } catch (Exception e) {
            System.err.println("Error creating mapping: " + e.getMessage());
            e.printStackTrace();
        }

        Pipeline p = Pipeline.create();

        // Log consumer props
        Properties kafkaConsumerProps = kafkaProps();
        System.out.println("Kafka Consumer Properties:");
        kafkaConsumerProps.forEach((k, v) -> System.out.println(k + "=" + v));

        // ✅ PASS-THROUGH: read ALL Debezium events and forward as-is
        StreamStage<Map.Entry<String, String>> stream = p.readFrom(
                        KafkaSources.<String, String>kafka(kafkaProps(), "is484.public.tbank_cleaned")
                )
                .withoutTimestamps()
                .map(rec -> new AbstractMap.SimpleEntry<>(rec.getKey(), rec.getValue()));

        // Log producer props
        Properties kafkaProducerProps = kafkaSinkProps();
        System.out.println("Kafka Producer Properties:");
        kafkaProducerProps.forEach((k, v) -> System.out.println(k + "=" + v));

        // Sinks
        stream.writeTo(Sinks.map("roles_map"));
        stream.writeTo(Sinks.logger());
        stream.writeTo(KafkaSinks.kafka(kafkaProducerProps, "powerbi-stream"));

        JobConfig cfg = new JobConfig()
                .setName("kafka-traffic-monitor")
                .addClass(JetJob.class)
                .setProcessingGuarantee(ProcessingGuarantee.EXACTLY_ONCE)
                .setSnapshotIntervalMillis(10_000);

        try {
            hz.getJet().newJobIfAbsent(p, cfg);
            System.out.println("✅ Jet Job started and will stay running!");
        } catch (Exception e) {
            System.err.println("❌ Error starting Jet job: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Properties kafkaSinkProps() {
        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getCanonicalName());
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, "3");
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "5");
        return properties;
    }

    private static void addKafkaTopic() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");

        try (AdminClient adminClient = AdminClient.create(properties)) {
            String topicName = "powerbi-stream";
            int partitions = 3;
            short replicationFactor = 1;

            NewTopic newTopic = new NewTopic(topicName, partitions, replicationFactor);
            CreateTopicsResult result = adminClient.createTopics(Collections.singleton(newTopic));
            result.all().get();
            System.out.println("✅ Topic '" + topicName + "' created successfully!");
        } catch (ExecutionException | InterruptedException e) {
            System.err.println("⚠️ Failed to create topic: " + e.getMessage());
        }
    }

    private static Properties kafkaProps() {
        Properties props = new Properties();
        props.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
        props.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getCanonicalName());
        props.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getCanonicalName());
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "hazelcast-jet-consumer");
        props.setProperty(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        props.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return props;
    }
}