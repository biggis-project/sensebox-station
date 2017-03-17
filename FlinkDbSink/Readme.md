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



Kafka Group ID
--------------

Command line: "--kafka-group-id"

Default value: "FlinkDbSink",

Expected value: any string (as supported by Apache Kafka)



Input Kafka Topic
-----------------

Command line: "--kafka-input-topic"

Default value: "sensebox-measurements"

Expected value: any string (as supported by Apache Kafka)



Output Kafka Topics
-------------------

Command line: "kafka-output-topic-unparsable", "--kafka-output-topic-invalid", "--kafka-output-topic-invalid"

Default value: "sensebox-measurements-error"

Expected value: any string (as supported by Apache Kafka)

An empty string('', just the quotes) disables output for the respective error type.



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

For db-user (as for db-pass) a null value does not trigger an exception, it just connects without user/password.
This works (at least) for PostgreSQL on localhost.


DB Pass
-------

Command line: "--db-pass"

Default value: null
