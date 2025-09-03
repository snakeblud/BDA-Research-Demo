FROM debezium/postgres:17-alpine

COPY 01-replace.sh /docker-entrypoint-initdb.d/
COPY *.sql /docker-entrypoint-initdb.d/
COPY *.csv /

RUN chown -R postgres:postgres /docker-entrypoint-initdb.d/

# Manual data generator (kept separate from init scripts)
COPY tbank_cleaned.sh /usr/local/bin/tbank_cleaned.sh
RUN chmod +x /usr/local/bin/tbank_cleaned.sh