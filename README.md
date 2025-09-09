# Fast Data Acquisition for Real-time Analytics

## Project Overview
This project implements a real-time data analytics platform for REDACTED that enables immediate analysis of core banking transactions. The system captures changes from transactional databases in real-time, processes them through a streaming pipeline, and presents insights through an interactive dashboard.

### Key Features
The platform provides real-time monitoring and analytics capabilities including:
- Immediate transaction anomaly detection
- Real-time feed status monitoring
- Transaction volume metrics
- Customer activity pattern analysis
- Failed payment tracking
- System health indicators

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

4. **Operational Data Store (Hazelcast)**
   - Optimized for analytical queries
   - Enables real-time data analysis
   - Supports complex search operations

5. **Backend Service (Spring Boot)**
   - Processes analytical queries
   - Implements business logic
   - Provides REST APIs and Server-Sent Events

6. **Frontend Dashboard (Grafana)**
   - Displays almost real-time analytics
   - Provides interactive visualizations
   - Supports monitoring capabilities

## Getting Started

### Prerequisites
- Docker and Docker Compose
- Java 22 or later
- Node.js 22 or later
- Maven/Gradle
- Git

### Development Setup

1. Clone the repository:
   ```bash
   git clone [repository-url]
   cd IS484
