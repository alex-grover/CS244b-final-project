package edu.stanford.cs244b;

import java.net.URI;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

/** Receive the request, and apply the consistent hashing algorithm
 *  to determine which replica to pass the request to.
 *  TODO: using HTTP as RPC is kinda lame... 
 */
@Path("/router")
@Api("/router")
public class Router {
    
    public Router(Shard[] shards) {
        // TODO: build distributed hash table containing shards...
    }
    
    @GET
    @Timed
    @ApiOperation("Dispatch a request to the shard which is responsible for the corresponding data")
    /** Determine the shard to redirect to */
    public void dispatch() {
        // TODO: consistent hashing algorithms go here. 
        int shardId = 0;
        URI uri = UriBuilder.fromPath("/shard/{shardId}").build(shardId);
        // for now, send 303 redirect to shard 0.
        Response response = Response.seeOther(uri).build();
        // yeah, this is kinda hacky.
        throw new WebApplicationException(response);
    }

}
