#!/bin/bash

# Wait for Kafka Connect to be available
echo "Waiting for Kafka Connect to start listening on localhost:8083 ⏳"
while [ "$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/connectors)" -ne 200 ] ; do
    echo -e "$(date)" " Kafka Connect listener HTTP state: " "$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8083/connectors)" " (waiting for 200)"
    sleep 5
done

# Configuration JSON
CONFIG='{
    "name": "'"$CONNECT_NAME"'",
    "connector.class": "'"$CONNECT_CONNECTOR_CLASS"'",
    "topic.prefix": "'"$CONNECT_TOPIC_PREFIX"'",
    "database.hostname": "'"$DB_HOST"'",
    "database.port": "'"$DB_PORT"'",
    "database.user": "'"$DB_USER"'",
    "database.password": "'"$DB_PASS"'",
    "database.dbname": "'"$DB_NAME"'",
    "include.schema.changes": "'"$CONNECT_INCLUDE_SCHEMA_CHANGES"'",
    "plugin.name": "'"$CONNECT_PLUGIN_NAME"'",
    "database.tcpKeepAlive": "'"$CONNECT_DATABASE_TCPKEEPALIVE"'",
    "tombstones.on.delete": "'"$CONNECT_TOMBSTONES_ON_DELETE"'",
    "provide.transaction.metadata": "'"$CONNECT_PROVIDE_TRANSACTION_METADATA"'",
    "slot.drop.on.stop": "'"$CONNECT_SLOT_DROP_ON_STOP"'",
    "snapshot.mode": "'"$CONNECT_SNAPSHOT_MODE"'",
    "publication.name": "'"$CONNECT_PUBLICATION_NAME"'"
}'

# Create/Update the connector
echo "Creating/Updating connector"
curl -i -X PUT \
    -H "Accept:application/json" \
    -H "Content-Type:application/json" \
    http://localhost:8083/connectors/"${CONNECT_NAME}"/config \
    -d "$CONFIG"

# Check the status
echo -e "\nChecking connector status:"
curl -s http://localhost:8083/connectors/"${CONNECT_NAME}"/status | grep '"state"' | sed -E 's/.*"state": ?"([^"]+).*/\1/'