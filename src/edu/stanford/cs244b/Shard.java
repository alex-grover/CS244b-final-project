package edu.stanford.cs244b;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;

import org.apache.commons.codec.binary.Base64;

import com.codahale.metrics.annotation.Timed;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataParam;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
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
    
    private final String TEMP_DIR = "temp";
    private final String DATA_DIR = "data";

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

    /** Insert a new item into the distributed hash table 
     * @throws IOException 
     * @throws NoSuchAlgorithmException */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Timed
    @ApiOperation("Insert a new item into the distributed hash table")
    public Map<String,Object> insertItem(@FormDataParam("file") final InputStream uploadInputStream //,
            //@FormDataParam("file") final FormDataContentDisposition contentDispositionHeader
            //@FormDataParam("fileBodyPart") FormDataBodyPart body
            ) throws NoSuchAlgorithmException, IOException {
        final String sha256hash = saveFile(uploadInputStream);
        return new HashMap<String,Object>() {{
            put("shard", shardId);
            put("sha256", sha256hash);
        }};
    }
    
    /** Save uploaded inputStream to disk, return the sha256 identifier of the file 
     * @throws NoSuchAlgorithmException 
     * @throws IOException */ 
    private String saveFile(InputStream uploadInputStream)
            throws NoSuchAlgorithmException, IOException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        DigestInputStream dis = new DigestInputStream(uploadInputStream, sha256);

        // write file to temporary location
        // TODO: this could result in race condition if files are created at same instance... 
        java.nio.file.Path tempPath = Paths.get(TEMP_DIR, "temp-" + (new Date()));
        Files.copy(uploadInputStream, tempPath);

        // compute SHA-256 hash
        byte[] digest = sha256.digest();
        String sha256hash = Base64.encodeBase64String(digest);
        java.nio.file.Path outputPath = Paths.get(DATA_DIR, sha256hash);
        
        // perform atomic rename; TODO: verify that hash is correct...
        Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE);
        return sha256hash;
    }
    
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