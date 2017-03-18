CREATE TABLE measurements (
    boxid       text,
    sensorid    text,
    ts          timestamp with time zone,
    value       double precision,
    PRIMARY KEY (boxid, sensorid, ts)
);