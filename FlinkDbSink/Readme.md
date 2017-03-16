Runtime Configuration
=====================

For all configuration keys, values provided on command line ("Program Arguments" in Flink Management Console) take the
highest priority; if not provided on command line, the default value is used.

If no default value exists, and no value is explicitly provided, an exception is thrown.



Bootstrap-servers
-----------------

Command line: "--bootstrap-servers"

Default value: "localhost:9092"

Expected value: "server1:port1,server2:port2,..."

Docs: https://kafka.apache.org/documentation#producerconfigs



Input Kafka Topic
-----------------

Command line: "--input-kafka-topic"

Default value: "sensebox-measurements"

Expected value: any string (as supported by Apache Kafka)



DB Connection String
--------------------

Command line: "--db-connection-string"

Default value: "jdbc:postgresql:sbm"

Expected value: any valid JDBC connection string

Currently, only PostgreSQL JDBC driver is linked into job



DB User
-------

Command line: "--db-user"

Default value: null



DB Pass
-------

Command line: "--db-pass"

Default value: null
