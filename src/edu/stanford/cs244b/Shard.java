package edu.stanford.cs244b;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.sun.jersey.api.Responses;
import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataParam;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import edu.stanford.cs244b.ChordConfiguration.Chord;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** A Shard represents a node in the Chord ring 
 * Shards receive incoming requests from the router,
 *  process the request and return it to the client. 
 */
@Path("/shard")
//TODO: @Path("/{shardId}")
@Api("/shard")
// TODO: return JSON? or some binary format like protobuf?
public class Shard {
    final static Logger logger = LoggerFactory.getLogger(Shard.class);
    
    private final int shardId;
    private final AtomicLong counter;
    
    private final String TEMP_DIR = "temp";
    private final String DATA_DIR = "data";

    public Shard(Chord chordConfig) throws UnknownHostException {
        // create temporary directories for this shard
        (new File(TEMP_DIR)).mkdir();
        (new File(DATA_DIR)).mkdir();
        
        // get my IP address
        InetAddress serverIP = InetAddress.getLocalHost();
        // use first 32 bits for server id...
        shardId = Util.ipByteArrayToInt(serverIP.getAddress());
        logger.info("Registering host "+serverIP+" in Chord ring with shardId="+Integer.toHexString(shardId));
        
        // get IP address of node in chord ring to join
        InetAddress hostToJoin = chordConfig.getEntryHost();
        int portToJoin = chordConfig.getEntryPort();
        logger.info("Attempting to join Chord ring host="+hostToJoin+" port="+portToJoin);
        
        this.counter = new AtomicLong();
    }
    
    /** perform basic operations which apply for each request,
     *  eg: populate hashmap containing shardId, record a hit to the shard
     *  (maybe for load-balancing purposes) 
     */
    private HashMap<String,Object> recordRequest() {
        return new HashMap<String,Object>() {{
            put("shard", Integer.toHexString(shardId));
            put("hits", counter.incrementAndGet());
        }};
    }

    /** Insert a new item into the distributed hash table 
     * @throws IOException 
     * @throws NoSuchAlgorithmException */
    @POST
    @Timed
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Insert a new item into the distributed hash table")
    public Map<String,Object> insertItem(@FormDataParam("file") final InputStream uploadInputStream //,
            //@FormDataParam("file") final FormDataContentDisposition contentDispositionHeader
            //@FormDataParam("fileBodyPart") FormDataBodyPart body
            ) throws NoSuchAlgorithmException, IOException {
        final String sha256hash = saveFile(uploadInputStream);
        return new HashMap<String,Object>() {{
            put("shard", Integer.toHexString(shardId));
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
        java.nio.file.Path tempPath = Paths.get(TEMP_DIR, UUID.randomUUID().toString());
        Files.copy(uploadInputStream, tempPath);

        // compute SHA-256 hash
        byte[] digest = sha256.digest();
        String sha256hash = Hex.encodeHexString(digest); 
        java.nio.file.Path outputPath = Paths.get(DATA_DIR, sha256hash);
        
        // TODO: verify that hash is correct, distribute to other replicas...
        
        // perform atomic rename on success
        Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE);
        return sha256hash;
    }
    
    /** Update an existing item in the distributed hash table */
    //@PUT 

    /** Retrieve the item with the specified SHA-256 hash 
     * @throws DecoderException 
     * @throws IOException */
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/{itemId}")
    @ApiOperation("Retrieve an item from this shard, or return 404 Not Found if it does not exist")
    public Response getItem(@PathParam("itemId") String sha256hash) throws DecoderException, IOException {
        byte[] id = Hex.decodeHex(sha256hash.toCharArray());
        //Map<String, Object> results = recordRequest();
        // TODO: check that file is owned by this node, otherwise forward to next node per fingertable
        java.nio.file.Path filePath = Paths.get(DATA_DIR, sha256hash);
        if (Files.exists(filePath)) {
            return Response.ok().entity(Files.newInputStream(filePath)).build();
        } else {
            return Responses.notFound().build();
        }
    }
}