#!/bin/bash

# Install OpenJDK 7
if ! type java > /dev/null; then
    # install java
    pacman -Sy
    pacman --noconfirm -S jdk7-openjdk
fi
