package edu.stanford.cs244b;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.codahale.metrics.annotation.Timed;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Shards receive incoming requests from the router,
 *  process the request and return it to the client. 
 */
@Path("/shard/0")
//TODO: @Path("/{shardId}")
@Api("/shard/0")
// TODO: return JSON? or some binary format like protobuf?
@Produces(MediaType.APPLICATION_JSON)
public class Shard {
    private final int shardId;
    private final AtomicLong counter;
    
    private Map<String,String> storage = new HashMap<String,String>();

    public Shard(int shardId) {
        this.shardId = shardId;
        this.counter = new AtomicLong();
    }
    
    /** perform basic operations which apply for each request,
     *  eg: populate hashmap containing shardId, record a hit to the shard
     *  (maybe for load-balancing purposes) 
     */
    private HashMap<String,Object> processRequest() {
        return new HashMap<String,Object>() {{
            put("shard", shardId);
            put("hits", counter.incrementAndGet());
        }};
    }

    /** Insert a new item into the distributed hash table */
    /*@POST
    @Timed
    @ApiOperation("Insert a new item into the distributed hash table")
    public Map<String,Object> insertItem(Object obj) {
        
    }*/
    
    /** Update an existing item in the distributed hash table */
    //@PUT 

    @GET
    @Timed
    @Path("/shard/0/{itemId}")
    @ApiOperation("Retrieve an item from this shard, or return 404 Not Found if it does not exist")
    public Map<String,Object> getItem(@PathParam("itemId") String itemId) {
        Map<String, Object> results = processRequest();
        Object item = storage.get(itemId);
        if (item == null) {
            //throw new Exception
        } else {
            results.put(itemId, item);
        }
        return results;
    }
}