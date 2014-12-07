#!/bin/bash

echo 'Starting server on machine' $1
java -agentlib:jdwp=transport=dt_socket,address=9999,server=y,suspend=n -jar cs244b-final-project.jar server configuration$1.yml &
