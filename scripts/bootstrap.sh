#!/bin/bash

# Install OpenJDK 7
if ! type java > /dev/null; then
    # remove package manager lock in case of premature system shutdown
    if [ -f /var/lib/pacman/db.lck ]; then
        sudo rm /var/lib/pacman/db.lck
    fi
    # install java
    pacman -Sy
    pacman --noconfirm -S jdk7-openjdk
fi
