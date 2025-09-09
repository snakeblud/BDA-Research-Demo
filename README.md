# Fast Data Acquisition for Real-time Analytics

## Project Overview
This project implements a real-time data analytics platform for REDACTED that enables immediate analysis of core banking transactions. The system captures changes from transactional databases in real-time, processes them through a streaming pipeline, and presents insights through an interactive dashboard.

### Key Features
The platform provides real-time monitoring and analytics capabilities including:
- Real-time CDC ingestion (Postgres → Debezium → Kafka)
- Kafka consumption & metrics: processed messages, success/failure counts, amount totals
- Service health & latency dashboards (Prometheus/Grafana/Blackbox)
- [Roadmap] Streaming analytics in Hazelcast (anomaly detection, rolling KPIs)

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
   - Reads from Kafka and can apply real-time transforms (filtering, enrichment, window/windowed aggregations)
   - Currently configured as a Kafka→Kafka pass-through

5. **Metrics Bridge (Spring Boot)**
   - Consumes Kafka events and exposes Prometheus metrics (Micrometer + Actuator)
   - Endpoint: /actuator/prometheus

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
   docker compose up --build

2. **Insert mock transactions**
   Run the mock data generator script inside the Postgres container:
   docker exec -it transactions-database /usr/local/bin/tbank_cleaned.sh 1000
   Replace `1000` with the number of rows you want to insert. You can run this multiple times to simulate live traffic.

3. **Verify Kafka messages**
   Check that Debezium is streaming changes into Kafka:
   docker exec -it pipeline-kafka-1      kafka-console-consumer --bootstrap-server kafka:9092      --topic is484.public.tbank_cleaned --from-beginning --max-messages 5

4. **Check metrics endpoints**
   - Spring Boot app (kpb_bridge): http://localhost:42069/actuator/prometheus
   - Prometheus UI: http://localhost:9090
   - Grafana UI: http://localhost:3000 (user: `admin`, password: `admin`)

5. **Explore dashboards**
   - Login to Grafana and open the **Kafka Stack Dashboard**.
   - Watch metrics such as:
     - Transaction throughput
     - Success vs failure counts
     - Kafka messages processed

---

### Demo Flow (What to Observe)

- Insert transactions into Postgres → Debezium captures CDC → Events land in Kafka.  
- Spring Boot consumes messages → Exposes counters & gauges via Prometheus.  
- Prometheus scrapes metrics → Grafana dashboards update live.  

---

### Notes & Roadmap

- Hazelcast is currently set up mainly as a **Kafka → Kafka pass-through**.  
- It can be extended for ETL-style filtering, real-time fraud detection, or complex event processing.  
- This setup simulates a real-time banking analytics pipeline but can be expanded for production use.

---

