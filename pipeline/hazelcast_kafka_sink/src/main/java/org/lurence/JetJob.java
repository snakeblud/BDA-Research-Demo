package org.lurence;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.kafka.KafkaSinks;
import com.hazelcast.jet.kafka.KafkaSources;
import com.hazelcast.jet.pipeline.*;
import com.hazelcast.sql.SqlResult;
import com.hazelcast.sql.SqlService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.connect.json.JsonSerializer;
import org.apache.kafka.connect.json.JsonDeserializer;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class JetJob {
    static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss:SSS");

    public static void main(String[] args) {
        addKafkaTopic();

        // Create Hazelcast configuration with cluster joining enabled
        Config config = new Config();
        config.setClusterName("analytics-cluster");
        config.getMetricsConfig().setEnabled(true);
        config.setProperty("hazelcast.memory.max.size", "1024");

        config.getJetConfig().setEnabled(true);
        config.getJetConfig().setResourceUploadEnabled(true);
        config.getJetConfig().setCooperativeThreadCount(4);

        // Multicast Join
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();
        joinConfig.getMulticastConfig().setEnabled(true);
        joinConfig.getTcpIpConfig().setEnabled(false);

        HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);

        SqlService sql = hz.getSql();
        try (SqlResult result = sql.execute("CREATE MAPPING roles_map " +
                "TYPE IMap " +
                "OPTIONS ( " +
                "'keyFormat' = 'varchar', " +
                "'valueFormat' = 'varchar'" +
                ")")) {
            // The statement has been executed, no need to process any result for DDL
            System.out.println("Mapping created successfully");
        }

        Pipeline p = Pipeline.create();

        StreamStage<Map.Entry<Object, Object>> stream = p.readFrom(
                    KafkaSources.kafka(kafkaProps(),
                    "is484.public.tbank_cleaned"
                ))
                        .withNativeTimestamps(0);

        Properties properties = kafkaSinkProps();

        stream.writeTo(Sinks.map("roles_map"));
        stream.writeTo(Sinks.logger());
        stream.writeTo(KafkaSinks.kafka(properties, "powerbi-stream"));

        JobConfig cfg = new JobConfig()
                .setName("kafka-traffic-monitor")
                .addClass(JetJob.class);

        hz.getJet().newJob(p, cfg);

        System.out.println("Job Config Complete!");
    }

    private static Properties kafkaSinkProps() {
        String bootstrapServers = "kafka:9092";

        Properties properties = new Properties();
        properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getCanonicalName());
        properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getCanonicalName());
        properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
        properties.setProperty(ProducerConfig.RETRIES_CONFIG, "3");
        properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "5");
        return properties;
    }

    private static void addKafkaTopic() {
        String bootstrapServers = "kafka:9092";

        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

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
        props.setProperty("bootstrap.servers", "kafka:9092");
        props.setProperty("key.deserializer", JsonDeserializer.class.getCanonicalName());
        props.setProperty("value.deserializer", JsonDeserializer.class.getCanonicalName());
        props.setProperty("auto.offset.reset", "earliest");

        return props;
    }
}