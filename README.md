CS244b-final-project
====================

Scalable web cache using consistent hashing for Stanford CS 244b.

## Eclipse Setup

Menu > Help > Install New Software > Add...

Name: M2Eclipse
Location: http://download.eclipse.org/technology/m2e/releases

Select All > Next > Finish

File > Import > Maven > Existing Maven Projects

## Run in Eclipse
Menu > Run > Run Configurations
Name: Server
Project: cs244b-final-project
Main Class: edu.stanford.cs244b.Server
Arguments Tab > Program Arguments: server
Run

Browse to http://localhost:8080/shard/0
You should see Shard=0 Hits=1
The hits counter increases on each page refresh (eg: each time you send a GET to the /shard/0 endpoint).

Browse to http://localhost:8080/swagger#!/shard
There are 2 endpoints defined, the first is the shard from before
The second is the router which will dispatch requests to the correct shard.
Note that the router is currently hardcoded to redirect requests to shard 0.

Browse to http://localhost:8081/metrics?pretty=true
Scroll down to view latency metrics