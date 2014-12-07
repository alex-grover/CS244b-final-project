#!/bin/bash

echo 'Starting server on machine' $1
java -jar cs244b-final-project.jar server configuration$1.yml
