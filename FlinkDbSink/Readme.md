Runtime Configuration
=====================

For all configuration keys, values provided on command line ("Program Arguments" in Flink Management Console) take the
highest priority; if not provided on command line, the value from the corresponding environment variable is used, else
the default value is used.

If no default value exists, and no value is explicitly provided, an exception is thrown.



Bootstrap-servers
-----------------

Command line: "--bootstrap-servers"

Environment key: DBSINK_BOOTSTRAP_SERVERS

Default value: "localhost:9092"

Expected value: "server1:port1,server2:port2,..."

Docs: https://kafka.apache.org/documentation#producerconfigs



Input Kafka Topic
-----------------

Command line: "--input-kafka-topic"

Environment key: "DBSINK_INPUT_KAFKA_TOPIC"

Default value: "sensebox-measurements"

Expected value: any string (as supported by Apache Kafka)



DB Connection String
--------------------

Command line: "--db-connection-string"

Environment key: "DBSINK_DB_CONNECTION_STRING"

Default value: "jdbc:postgresql:sbm"

Expected value: any valid JDBC connection string

Currently, only PostgreSQL JDBC driver is linked into job



DB User
-------

Command line: "--db-user"

Environment key: "DBSINK_DB_USER"

Default value: null



DB Pass
-------

Command line: "--db-pass"

Environment key: "DBSINK_DB_PASS"

Default value: null
