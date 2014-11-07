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

Browse to http://localhost:8080/

You should see Shard=0 Hits=1
The hits counter increases on each page refresh.
