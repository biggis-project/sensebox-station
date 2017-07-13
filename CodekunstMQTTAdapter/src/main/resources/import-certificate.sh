#!/bin/bash

### Java's keytool imports only the first certificate in a multi-certificate PEM file.
### This scripts splits gets TheThingsNetwork's certificate chain,
### splits the pem into separate files, one per certificate and imports all
### of them into the truststore given as the single parameter.

truststore=$1

set -e

curl -OL https://console.thethingsnetwork.org/mqtt-ca.pem

csplit --prefix=mqtt-certpart- --suffix-format='%02d.pem' --elide-empty-files mqtt-ca.pem '/-----BEGIN CERTIFICATE-----/' '{*}'

rm mqtt-ca.pem

for certfile in mqtt-certpart-*.pem; do
    echo "Importing $certfile"
    keytool -importcert -file $certfile -keystore $truststore -storepass changeit -alias $certfile -noprompt
done

rm mqtt-certpart-*.pem
