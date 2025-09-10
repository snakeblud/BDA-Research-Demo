# Fast Data Acquisition for Real-time Analytics

## Project Overview
This project implements a real-time data analytics platform for REDACTED that enables immediate analysis of core banking transactions. The system captures changes from transactional databases in real-time, processes them through a streaming pipeline, and presents insights through an interactive dashboard.

### Key Features
The platform provides real-time monitoring and analytics capabilities including:
- Real-time CDC ingestion (Postgres → Debezium → Kafka)
- Kafka consumption & metrics: processed messages, success/failure counts, amount totals
- Stream ELT: Hazelcast classifies transactions (e.g., high-value >1000 vs normal) downstream after loading
- Service health & latency dashboards (Prometheus/Grafana/Blackbox)
- [Roadmap] Streaming analytics in Hazelcast (fraud detection, rolling KPIs)

## Technical Architecture

### Components
The system consists of several interconnected components:

1. **Source Database (PostgreSQL)**
    - Stores core banking transactions
    - Handles high-volume transactional workloads

2. **Change Data Capture (Debezium)**
    - Captures database changes in real-time
    - Supports all CRUD operations
    - Handles schema changes automatically

3. **Message Streaming (Apache Kafka)**
    - Ensures reliable message delivery
    - Provides scalable data streaming
    - Manages backpressure

4. **Stream Processing (Hazelcast)**
    - Reads from Kafka, applies ELT logic (e.g., parsing, filtering, enrichment)
    - Separates **high-value transactions (>1000)** from normal ones
    - Forwards processed data into a downstream Kafka topic (`powerbi-stream`)

5. **Metrics Bridge (Spring Boot)**
    - Consumes Kafka events and exposes Prometheus metrics (Micrometer + Actuator)
    - Metrics include:
        - Total transactions (success/failure)
        - High-value transactions
        - Amount sums
    - Endpoint: `/actuator/prometheus`

6. **Observability (Prometheus, Grafana, Alertmanager, Blackbox)**
    - Prometheus scrapes the Spring Boot metrics and Blackbox probes
    - Grafana renders dashboards
    - Alertmanager routes alerts from Prometheus rules

## Getting Started

### Prerequisites
- Docker and Docker Compose
- Java 22 or later
- Node.js 22 or later
- Maven/Gradle
- Git

### Demo Instructions

To run and demo the pipeline end-to-end:

1. **Start the pipeline**
   ```bash
   docker compose up --build
   ```

2. **Insert mock transactions**
    - Run the mock data generator script inside the Postgres container:
      ```bash
      docker exec -it transactions-database /usr/local/bin/tbank_cleaned.sh 1000
      ```
    - Or insert weighted mock transactions directly via SQL (ensures mix of high/normal):
      ```bash
      docker exec -it transactions-database psql -U auth_user -d transactions -c "
      INSERT INTO tbank_cleaned (
        TRANSACTIONID, ACCOUNTFROM, ACCOUNTTO, BANKIDFROM, BANKIDTO,
        TRANSACTIONAMOUNT, EXCHANGERATE, TRANSACTIONDATE, TRANSACTIONTYPE,
        INTERIMBALANCE, ACCOUNTTO_INTERIMBALANCE, CURRENCY, QUOTECURRENCY,
        PAYMENTMODE, OVERRIDEFLAG, NARRATIVE
      )
      SELECT
        (extract(epoch from clock_timestamp())::bigint * 100000 + gs)::bigint AS transactionid,
        (random() * 10000)::int,
        (random() * 10000)::int,
        (random() * 100)::int,
        (random() * 100)::int,
        CASE WHEN random() < 0.2
             THEN round((1000 + random() * 9000)::numeric, 2)
             ELSE round((0.01 + random() * 999.99)::numeric, 2)
        END,
        1.0,
        now() - ((random() * 30 * 24 * 3600)::int || ' seconds')::interval,
        (random() * 500)::int,
        round((100 + random() * 9900)::numeric, 2),
        round((100 + random() * 9900)::numeric, 2),
        'SGD','SGD',
        CASE WHEN random() < 0.5 THEN 'Cash' ELSE 'Card' END,
        (random() < 0.5),
        'weighted_gen'
      FROM generate_series(1, 10000) gs
      ON CONFLICT (transactionid) DO NOTHING;
      "
      ```

3. **Verify Kafka messages**
   ```bash
   docker exec -it pipeline-kafka-1      kafka-console-consumer --bootstrap-server kafka:9092      --topic is484.public.tbank_cleaned --from-beginning --max-messages 5
   ```

4. **Check metrics endpoints**
    - Spring Boot app (kpb_bridge): http://localhost:42069/actuator/prometheus
    - Prometheus UI: http://localhost:9090
    - Grafana UI: http://localhost:3000 (user: `admin`, password: `admin`)

5. **Explore dashboards**
    - Login to Grafana and open the **Kafka Stack Dashboard**.
    - Watch metrics such as:
        - Transaction throughput
        - Success vs failure counts
        - High-value vs normal transactions
        - Kafka messages processed

---

### Demo Flow (What to Observe)

- Insert transactions into Postgres → Debezium captures CDC → Events land in Kafka.
- Hazelcast Jet consumes from Kafka → Filters/classifies (high-value vs normal) → Publishes to new Kafka topic.
- Spring Boot consumes messages → Exposes counters & gauges via Prometheus.
- Prometheus scrapes metrics → Grafana dashboards update live.

---

### Notes & Roadmap

- This pipeline follows an **ELT pattern**:  
  - **Extract & Load** → Debezium + Kafka move raw changes into Kafka.  
  - **Transform** → Hazelcast Jet and the Spring Boot consumer apply classification and aggregations downstream.  
- Hazelcast is now performing **ELT-style stream processing** (classification, filtering).  
- This can be extended for real-time fraud detection, aggregations, or complex event processing.  
- This setup simulates a real-time banking analytics pipeline but can be expanded for production use.

---
