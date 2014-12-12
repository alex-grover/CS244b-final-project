#!/bin/bash

mvn clean package
#for host in {1..2}; do
#  scp -P 2222 target/cs244b-final-project-0.0.1-SNAPSHOT-shaded.jar vagrant@192.168.50.$host:cs244b-final-project.jar
#done
vagrant provision
