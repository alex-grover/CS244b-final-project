package edu.stanford.cs244b;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;

import java.util.concurrent.atomic.AtomicLong;

/** Shard class. This will receive incoming requests, apply consistent hashing
 *  algorithm to determine which replica to pass the request to, and if it is
 *  the right replica then process the request and return it to the client. 
 */

@Path("/")
// TODO: return JSON? or some binary format like protobuf?
//@Produces(MediaType.APPLICATION_JSON)
public class Shard {
    private final int shardId;
    private final AtomicLong counter;

    public Shard(int shardId) {
        this.shardId = shardId;
        this.counter = new AtomicLong();
    }

    @GET
    @Timed
    public String getHits() {
        return "Shard="+shardId+" Hits="+counter.incrementAndGet();
    }
}