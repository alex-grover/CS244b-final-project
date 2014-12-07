#!/bin/bash

# Install OpenJDK 7
if ! type java > /dev/null; then
    # install java
    sudo apt-get update
    sudo apt-get -y install openjdk-7-jre 
fi
