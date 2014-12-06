package edu.stanford.cs244b;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
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
import org.bouncycastle.jce.provider.BouncyCastleProvider;
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
import edu.stanford.cs244b.crypto.HMACInputStream;
import edu.stanford.cs244b.chord.ChordInterface;
import edu.stanford.cs244b.chord.ChordNode;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.DigestInputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
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
 *  Shards receive incoming requests from the router,
 *  process the request and return it to the client. 
 */
@Path("/shard")
//TODO: @Path("/{shardId}")
@Api("/shard")
// TODO: return JSON? or some binary format like protobuf?
public class Shard {
    public enum IdentifierAlgorithm {
        SHA256,
        HMAC_SHA256 // default
    }
    
	private ChordNode node;
	
    final static Logger logger = LoggerFactory.getLogger(Shard.class);
    
    private final int shardId;
    private final AtomicLong counter;
    
    private final String KEY_FILE = "key.txt";
    private final String TEMP_DIR = "temp";
    private final String DATA_DIR = "data";
    
    public IdentifierAlgorithm identifierAlgo;
    SecretKeySpec secretKey;

    public Shard(Chord chordConfig) throws UnknownHostException, NoSuchAlgorithmException {
        // create temporary directories for this shard
        (new File(TEMP_DIR)).mkdir();
        (new File(DATA_DIR)).mkdir();
        
        // determine algorithm used to generate identifiers for objects added to chord ring
        if (chordConfig.getIdentifier().toLowerCase().equals(IdentifierAlgorithm.SHA256.toString().toLowerCase())) {
            identifierAlgo = IdentifierAlgorithm.SHA256;
        } else {
            identifierAlgo = IdentifierAlgorithm.HMAC_SHA256; // default
            Security.addProvider(new BouncyCastleProvider());
        }
        logger.info("Using "+identifierAlgo+" to generate identifiers for objects added to chord ring");
        
        // load key from filesystem if it exists
        secretKey = readOrCreateSecretKey();
        
        // get my IP address
        InetAddress serverIP = InetAddress.getLocalHost();
        // use first 32 bits for server id...
        shardId = inetAddressToShardId(serverIP);
        logger.info("Registering host "+serverIP+" in Chord ring with shardId="+shardIdAsHex());
        
        // get IP address of node in chord ring where we begin the join process
        InetAddress hostToJoin = chordConfig.getEntryHost();
        int portToJoin = chordConfig.getEntryPort();
        if (hostToJoin.isLoopbackAddress()) {
            logger.info("Chord entryHost is loopback address, creating new Chord ring");
        } else {
            logger.info("Attempting to join Chord ring host="+hostToJoin+" port="+portToJoin);
        }
        
        // initialize Chord node and join ring
        try {
        	// TODO: allow user to specify ports
        	node = new ChordNode(serverIP, portToJoin);
        	
        	// TODO: join existing ring
        	node.join(node, true);
        } catch (Exception e) {
        	e.printStackTrace();
        	System.exit(1);
        }
        
        this.counter = new AtomicLong();
    }
    
    /** perform basic operations which apply for each request,
     *  eg: populate hashmap containing shardId, record a hit to the shard
     *  (maybe for load-balancing purposes) 
     */
    private HashMap<String,Object> recordRequest() {
        return new HashMap<String,Object>() {{
            put("shard", shardIdAsHex());
            put("hits", counter.incrementAndGet());
        }};
    }

    /** Insert a new item into the distributed hash table 
     * @throws IOException 
     * @throws NoSuchAlgorithmException 
     * @throws InvalidKeyException 
     * @throws NoSuchProviderException */
    @POST
    @Timed
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation("Insert a new item into the distributed hash table")
    public Map<String,Object> insertItem(@FormDataParam("file") final InputStream uploadInputStream //,
            //@FormDataParam("file") final FormDataContentDisposition contentDispositionHeader
            //@FormDataParam("fileBodyPart") FormDataBodyPart body
            ) throws NoSuchAlgorithmException, IOException, InvalidKeyException, NoSuchProviderException {
        final String objectId = saveFile(uploadInputStream);
        return new HashMap<String,Object>() {{
            put("shard", shardIdAsHex());
            put("id", objectId);
        }};
    }
    
    /** Save uploaded inputStream to disk, return the sha256 identifier of the file 
     * @throws NoSuchAlgorithmException 
     * @throws IOException 
     * @throws InvalidKeyException 
     * @throws NoSuchProviderException */ 
    private String saveFile(InputStream uploadInputStream)
            throws NoSuchAlgorithmException, IOException, InvalidKeyException, NoSuchProviderException {
        byte[] digest;
        InputStream wrappedInputStream;
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        if (identifierAlgo.equals(IdentifierAlgorithm.SHA256)) {
            wrappedInputStream = new DigestInputStream(uploadInputStream, sha256);
        } else { // assume HMAC_SHA256
            wrappedInputStream = new HMACInputStream(uploadInputStream, secretKey);
        }
        // write file to temporary location
        java.nio.file.Path tempPath = Paths.get(TEMP_DIR, UUID.randomUUID().toString());
        Files.copy(wrappedInputStream, tempPath);
        
        // compute SHA-256 hash
        if (identifierAlgo.equals(IdentifierAlgorithm.SHA256)) {
            digest = sha256.digest();
        } else {
            digest = ((HMACInputStream) wrappedInputStream).getDigest();
        }
        
        String hexadecimalHash = Hex.encodeHexString(digest);
        java.nio.file.Path outputPath = Paths.get(DATA_DIR, hexadecimalHash);
        
        // TODO: verify that hash is correct, distribute to other replicas...
        
        // perform atomic rename on success
        Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE);
        return hexadecimalHash;
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
    
    public SecretKeySpec readOrCreateSecretKey() throws NoSuchAlgorithmException {
        java.nio.file.Path file = Paths.get(KEY_FILE);
        if (Files.exists(file)) {
            logger.info("Loading existing secret key from file "+KEY_FILE);
            try {
                String keyString = new String(Files.readAllBytes(file));
                return new SecretKeySpec(Hex.decodeHex(keyString.toCharArray()), "RAW");
            } catch (Exception e) {
                logger.error("Failed to load secret key from file "+KEY_FILE, e);
            }
        }
        logger.info("Generating new secret key");
        KeyGenerator keygen = KeyGenerator.getInstance("AES");
        // TODO: is default securerandom number generator safe? does it need to be seeded?
        //SecureRandom secureRandom = new SecureRandom();
        //keygen.init(256, secureRandom);
        byte[] secretKeyValue = keygen.generateKey().getEncoded();
        SecretKeySpec secretKey = new SecretKeySpec(secretKeyValue, "AES");
        String keyString = new String(Hex.encodeHex(secretKeyValue));
        try {
            Files.write(file, keyString.getBytes());
            logger.info("Successfully wrote new secret key to file "+KEY_FILE);
        } catch (Exception e) {
            logger.error("Failed to write secret key to file "+KEY_FILE, e);
        }
        return secretKey;
    }
    
    /** Convenience method for displaying shardid as a hex string */
    public String shardIdAsHex() {
        return Integer.toHexString(shardId);
    }
    
    /** Compute shardid from an IP address */
    public static int inetAddressToShardId(InetAddress address) {
        // Use Knuth's multiplicative method "hash" of IP address to evenly
        // distribute shard identifiers
        return Util.intHash(Util.ipByteArrayToInt(address.getAddress()));
    }
}
