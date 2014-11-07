package edu.stanford.cs244b;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Shards receive incoming requests from the router,
 *  process the request and return it to the client. 
 */
@Path("/shard")
@Api("/shard")
// TODO: return JSON? or some binary format like protobuf?
@Produces(MediaType.APPLICATION_JSON)
public class Shard {
    private final int shardId;
    private final AtomicLong counter;

    public Shard(int shardId) {
        this.shardId = shardId;
        this.counter = new AtomicLong();
    }

    @GET
    @Timed
    @Path("/0")
    // TODO: @Path("/{shardId}")
    @ApiOperation("Record a request to this shard")
    public HashMap<String,Object> getHits() {
    // TODO: public HashMap<String,Object> getHits(@PathParam("shardId")) {
        return new HashMap<String,Object>() {{
            put("shard", shardId);
            put("hits", counter.incrementAndGet());
        }};
    }
}