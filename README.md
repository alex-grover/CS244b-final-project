CS244b-final-project
====================

Scalable web cache using consistent hashing for Stanford CS 244b.

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
* Arguments Tab > Program Arguments: server configuration.yml

Once the server is running, open upload.html in your browser. Upload a file; you should receive a response such as {"shard":"c0a80105","sha256":"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}. Note that uploaded files are saved to the filesystem in the data/ directory. Now go to [http://localhost:8080/shard/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855](http://localhost:8080/shard/e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855) and you can download the file you just uploaded. You may need to rename the file so that it is interpreted in the correct format (eg: append .png if the original file you uploaded was a PNG image).

Browse to [http://localhost:8080/swagger](http://localhost:8080/swagger). There are 2 endpoints defined, the first is the [shard](http://localhost:8080/swagger#!/shard) from before. The second is the [router](http://localhost:8080/swagger#!/router) which will dispatch requests to the correct shard. Note that the router is currently hardcoded to redirect requests to shard 0.

Browse to [http://localhost:8081/metrics?pretty=true](http://localhost:8081/metrics?pretty=true). Scroll down to view latency metrics for the shard and router.

## Chord Ring ##
If you start the server without any arguments, then it will assume that it is the first node in the Chord ring. To join an existing Chord ring, specify the ip address of any server in the ring. The server will automatically lookup its correct position in the ring and join it. For example, in eclipse Run > Run Configurations > Arguments > VM arguments:

    -Ddw.chord.entryHost=192.168.1.4

You may modify the algorithm (SHA-256 hash or HMAC-SHA256 keyed message authentication code - default) used to generate identifiers for objects added to chord ring by passing the following parameter:

    -Ddw.chord.identifier=hmac_sha256
    -Ddw.chord.identifier=sha256

## Running On Multiple Virtual Machines ##
1. Download and install [Vagrant](https://www.vagrantup.com/downloads.html) and [VirtualBox](https://www.virtualbox.org/wiki/Downloads)
2. Pull latest code from chord-feature branch
3. In terminal, cd to the project directory, execute the following command:

    vagrant up

4. Wait for the 2 virtual machines to download and load, then install the software on them:

    ./deploy.sh

5. Optionally, log into the virtual machine:

    vagrant ssh cs244b-1
