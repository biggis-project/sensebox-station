CREATE TABLE measurements (
    boxid       varchar(24),
    sensorid    varchar(24),
    ts          timestamp,
    value       double precision,
    PRIMARY KEY (boxid, sensorid, ts)
);