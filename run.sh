#!/usr/bin/env bash
set -e
echo 'Starting ParkFlow. Make sure MySQL is running and application.properties has your DB password.'
mvn spring-boot:run
