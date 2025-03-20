#!/bin/bash

# Create directory structure
mkdir -p monitoring/prometheus
mkdir -p monitoring/alertmanager
mkdir -p monitoring/grafana/provisioning/dashboards
mkdir -p monitoring/grafana/provisioning/datasources
mkdir -p monitoring/grafana/dashboards

# Create prometheus.yml
cat > monitoring/prometheus.yml << 'EOF'
global:
  scrape_interval: 15s
  evaluation_interval: 15s
  scrape_timeout: 10s

# Load alert rules
rule_files:
  - /etc/prometheus/alert_rules.yml

# Alertmanager configuration
alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093

# Define reusable scrape configs
scrape_configs:
  # Monitoring tools themselves
  - job_name: 'monitoring-tools'
    honor_labels: true
    static_configs:
      - targets: ['prometheus:9090']
        labels:
          service: 'prometheus'
          component: 'monitoring'
      - targets: ['grafana:3000']
        labels:
          service: 'grafana'
          component: 'monitoring'
      - targets: ['blackbox-exporter:9115']
        labels:
          service: 'blackbox-exporter'
          component: 'monitoring'
      - targets: ['alertmanager:9093']
        labels:
          service: 'alertmanager'
          component: 'monitoring'

  # Kafka ecosystem - HTTP endpoint checks
  - job_name: 'kafka-http-endpoints'
    metrics_path: /probe
    params:
      module: [http_2xx]
    static_configs:
      - targets:
        - 'kafka-connect:8083/connectors'
        labels:
          service: 'kafka-connect'
          component: 'kafka'
          endpoint: 'connectors'
      - targets:
        - 'kafka-rest:8082/topics'
        labels:
          service: 'kafka-rest'
          component: 'kafka'
          endpoint: 'topics'
      - targets:
        - 'schema-registry:8081/subjects'
        labels:
          service: 'schema-registry'
          component: 'kafka'
          endpoint: 'subjects'
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: blackbox-exporter:9115

  # Kafka ecosystem - TCP connectivity checks
  - job_name: 'kafka-tcp-checks'
    metrics_path: /probe
    params:
      module: [tcp_connect]
    static_configs:
      - targets:
        - 'kafka:9092'
        labels:
          service: 'kafka-broker'
          component: 'kafka'
      - targets:
        - 'zookeeper:2181'
        labels:
          service: 'zookeeper'
          component: 'kafka'
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: blackbox-exporter:9115

  # Application checks
  - job_name: 'application-health-tcp'
    metrics_path: /probe
    params:
      module: [tcp_connect]
    static_configs:
      - targets:
        - 'kpb_bridge:8080'
        labels:
          service: 'kafka-power-bi-bridge'
          component: 'application'
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: blackbox-exporter:9115

  # Database checks
  - job_name: 'database-connectivity'
    metrics_path: /probe
    params:
      module: [tcp_connect]
    static_configs:
      - targets:
        - 'transactions-database:5432'
        labels:
          service: 'postgresql'
          component: 'database'
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: blackbox-exporter:9115

  # Hazelcast checks
  - job_name: 'hazelcast-health'
    metrics_path: /probe
    params:
      module: [http_2xx]
    static_configs:
      - targets:
        - 'hazelcast-management-center:8080'
        labels:
          service: 'hazelcast-dashboard'
          component: 'hazelcast'
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: blackbox-exporter:9115

  - job_name: 'hazelcast-tcp'
    metrics_path: /probe
    params:
      module: [tcp_connect]
    static_configs:
      - targets:
        - 'hazelcast-kafka:5701'
        labels:
          service: 'hazelcast-kafka'
          component: 'hazelcast'
    relabel_configs:
      - source_labels: [__address__]
        target_label: __param_target
      - source_labels: [__param_target]
        target_label: instance
      - target_label: __address__
        replacement: blackbox-exporter:9115
EOF

# Create alert_rules.yml
cat > monitoring/prometheus/alert_rules.yml << 'EOF'
groups:
- name: kafka_alerts
  rules:
  - alert: ServiceDown
    expr: probe_success == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Service {{ $labels.service }} is down"
      description: "{{ $labels.service }} has been down for more than 1 minute."

  - alert: KafkaBrokerDown
    expr: probe_success{service="kafka-broker"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Kafka broker is down"
      description: "The Kafka broker has been down for more than 1 minute. Data ingestion is likely impacted."

  - alert: ZookeeperDown
    expr: probe_success{service="zookeeper"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Zookeeper is down"
      description: "Zookeeper has been down for more than 1 minute. Kafka coordination may be impacted."

  - alert: KafkaConnectDown
    expr: probe_success{service="kafka-connect"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Kafka Connect is down - Data Ingestion Halted"
      description: "Kafka Connect has been down for more than 1 minute. This will stop data ingestion from the source database."

  - alert: SchemaRegistryDown
    expr: probe_success{service="schema-registry"} == 0
    for: 1m
    labels:
      severity: high
    annotations:
      summary: "Schema Registry is down"
      description: "Schema Registry has been down for more than 1 minute. This may affect data serialization/deserialization."

  - alert: DatabaseDown
    expr: probe_success{service="postgresql"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "Database is down - Data Source Unavailable"
      description: "The PostgreSQL database has been down for more than 1 minute. This will prevent new data from being captured."

  - alert: PowerBIBridgeDown
    expr: probe_success{service="kafka-power-bi-bridge"} == 0
    for: 1m
    labels:
      severity: high
    annotations:
      summary: "Kafka Power BI Bridge is down"
      description: "The Kafka Power BI Bridge has been down for more than 1 minute. Data will not be available to Power BI."

  - alert: HazelcastKafkaDown
    expr: probe_success{service="hazelcast-kafka"} == 0
    for: 1m
    labels:
      severity: high
    annotations:
      summary: "Hazelcast-Kafka connection is down"
      description: "The Hazelcast to Kafka connection has been down for more than 1 minute. Real-time analytics may be affected."

  - alert: HazelcastDashboardDown
    expr: probe_success{service="hazelcast-dashboard"} == 0
    for: 1m
    labels:
      severity: warning
    annotations:
      summary: "Hazelcast Dashboard is down"
      description: "The Hazelcast Management Dashboard has been down for more than 1 minute."

  - alert: SlowResponseTime
    expr: probe_duration_seconds > 1
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Slow response from {{ $labels.service }}"
      description: "{{ $labels.service }} is responding slowly (>1s) for more than 5 minutes, which may indicate performance issues."

  - alert: DataPipelineBreak
    expr: (probe_success{service="kafka-broker"} == 1 and probe_success{service="kafka-connect"} == 0) or (probe_success{service="postgresql"} == 1 and probe_success{service="kafka-connect"} == 0)
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "Data Pipeline Break Detected"
      description: "A critical break in the data pipeline has been detected. Data from the database is not flowing to Kafka."
EOF

# Create Alertmanager config
cat > monitoring/alertmanager/config.yml << 'EOF'
global:
  resolve_timeout: 5m
  # Gmail SMTP settings - update these with your own email credentials
  smtp_smarthost: 'smtp.gmail.com:587'
  smtp_from: 'your-gmail@gmail.com'
  smtp_auth_username: 'your-gmail@gmail.com'
  smtp_auth_password: 'your-app-password'  # Generate an app password if you have 2FA enabled
  smtp_require_tls: true

route:
  group_by: ['alertname', 'component']
  group_wait: 10s
  group_interval: 1m
  repeat_interval: 1h
  receiver: 'email-notifications'

receivers:
- name: 'email-notifications'
  email_configs:
  - to: 'your-recipient-email@example.com'
    send_resolved: true

inhibit_rules:
  # Suppress warnings if there's already a critical alert for the same service
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['service']
EOF

# Create blackbox.yml
cat > monitoring/blackbox.yml << 'EOF'
modules:
  http_2xx:
    prober: http
    timeout: 5s
    http:
      preferred_ip_protocol: "ip4"
      valid_status_codes: [200, 201, 202, 203, 204]

  tcp_connect:
    prober: tcp
    timeout: 5s
    tcp:
      preferred_ip_protocol: "ip4"
EOF

# Create Grafana dashboard provisioning
cat > monitoring/grafana/provisioning/dashboards/dashboard.yml << 'EOF'
apiVersion: 1

providers:
  - name: 'Default'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    options:
      path: /var/lib/grafana/dashboards
EOF

# Create Grafana datasource provisioning
cat > monitoring/grafana/provisioning/datasources/prometheus.yml << 'EOF'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
EOF

echo "Monitoring configuration files created successfully!"
echo "You need to update the Alertmanager config with your email settings."
echo "To complete setup, add the monitoring services to your compose.yml file."