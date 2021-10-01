CS244b-final-project
====================

Secure, distributed, scalable peer-to-peer file locker for Stanford CS 244B Distributed Systems final project. Users can run a server node (a java jar executable) which mirror files of their choice in a decentralized "cloud" of individually untrusted commodity consumer computers, while still ensuring the integrity of replicated files by utilizing keyed-hash message authentication codes (HMAC_SHA256). Our system is cross-platform (it can be ported to any device which is capable of running the Java virtual machine) and utilizes standard TCP/IP networking to promote adoption and usage across the wider internet. Participants in the system are registered in a distributed hash table backed by the Chord algorithm, therefore saves and lookups in the system scale logarithmically in proportion to the number of participating clients.

## Eclipse Setup

Menu > Help > Install New Software > Add...

* Name: M2Eclipse
* Location: http://download.eclipse.org/technology/m2e/releases

Select All > Next > Finish

File > Import > Maven > Existing Maven Projects

## Run in Eclipse
Menu > Run > Run Configurations

* Name: Server
* Project: cs244b-final-project
* Main Class: edu.stanford.cs244b.Server
* Arguments Tab > Program Arguments: server configuration.yml.local

Once the server is running, navigate to [localhost:8078](http://localhost:8078) in your browser. Upload a file; you should receive a response such as

    {"id":"7c8359ffb8a8a65040246234bb32ff676e7b214501ed7379851e4b272ed2c345","shard":"6d3779b1","sha256":"4bee078a72d4e5b6252f5510123e786d134fd321fd220d28351962641315e6eb","filename":"icon.png","filetype":"image/png"}

 Note that uploaded files are saved to the filesystem in the data/ directory. Now go to [http://localhost:8078/api/shard/7c8359ffb8a8a65040246234bb32ff676e7b214501ed7379851e4b272ed2c345](http://localhost:8078/api/shard/7c8359ffb8a8a65040246234bb32ff676e7b214501ed7379851e4b272ed2c345) and you can download the file you just uploaded.

Browse to [http://localhost:8078/admin/metrics?pretty=true](http://localhost:8078/admin/metrics?pretty=true). Scroll down to view latency metrics for the shard.

## Chord Ring ##
If you start the server without any arguments, then it will assume that it is the first node in the Chord ring. To join an existing Chord ring, specify the ip address of any server in the ring. The server will automatically lookup its correct position in the ring and join it. For example, in eclipse Run > Run Configurations > Arguments > VM arguments:

    -Ddw.chord.entryHost=192.168.1.4

You may modify the algorithm (SHA-256 hash or HMAC-SHA256 keyed message authentication code - default) used to generate identifiers for objects added to chord ring by passing one of the following parameters:

    -Ddw.chord.identifier=hmac_sha256
    -Ddw.chord.identifier=sha256

These commandline arguments will override any parameters which were set in the configuration*.yml files.

## Running On Multiple Virtual Machines ##
1. Download and install [Vagrant](https://www.vagrantup.com/downloads.html) and [VirtualBox](https://www.virtualbox.org/wiki/Downloads)
2. Pull latest code from one of our two git repositories:

        git clone https://github.com/akovacs/cs244b.git # OR
        git clone https://github.com/alex-grover/CS244b-final-project.git

3. In your terminal, cd to the project directory, execute the following command:

        vagrant up

4. Once the Ubuntu 14.04 Server image has been downloaded, and the 12 virtual machines have booted (note: you can edit the Vagrantfile to reduce number of virtual machines if your computer has limited RAM), install the software on them:

        ./deploy.sh

5. Optionally, log into the virtual machine and tail the logs.

        vagrant ssh cs244b-1
        tail -f log1.txt

6. Navigate to the [web UI](http://localhost:8080) and watch the Chord distributed hash table in action.
