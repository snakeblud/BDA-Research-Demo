FROM debezium/postgres:17-alpine

COPY . /docker-entrypoint-initdb.d/

RUN chown -R postgres:postgres /docker-entrypoint-initdb.d/