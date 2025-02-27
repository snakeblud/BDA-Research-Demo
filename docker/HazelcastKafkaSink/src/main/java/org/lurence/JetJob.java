package org.lurence;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.kafka.KafkaSources;
import com.hazelcast.jet.pipeline.*;
import com.hazelcast.sql.SqlResult;
import com.hazelcast.sql.SqlService;
import org.apache.kafka.common.serialization.*;

import java.time.format.DateTimeFormatter;
import java.util.Properties;

public class JetJob {
    static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss:SSS");

    public static void main(String[] args) {
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
        p.readFrom(KafkaSources.kafka(kafkaProps(), "is484.public.roles"))
                .withNativeTimestamps(0)
                .writeTo(Sinks.map("roles_map"));

        JobConfig cfg = new JobConfig()
                .setName("kafka-traffic-monitor")
                .addClass(JetJob.class);

        hz.getJet().newJob(p, cfg);

        System.out.println("Job Config Complete!");
    }

    private static Properties kafkaProps() {
        Properties props = new Properties();
        props.setProperty("bootstrap.servers", "kafka:9092");
        props.setProperty("key.deserializer", StringDeserializer.class.getCanonicalName());
        props.setProperty("value.deserializer", StringDeserializer.class.getCanonicalName());
        props.setProperty("auto.offset.reset", "earliest");

        return props;
    }
}