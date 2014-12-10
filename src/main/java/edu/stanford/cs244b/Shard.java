package edu.stanford.cs244b;

import io.dropwizard.jetty.HttpConnectorFactory;

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
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.sun.jersey.api.Responses;
import com.sun.jersey.multipart.FormDataParam;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;

import edu.stanford.cs244b.ChordConfiguration.Chord;
import edu.stanford.cs244b.crypto.HMACInputStream;
import edu.stanford.cs244b.chord.ChordNode;
import edu.stanford.cs244b.chord.Finger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
        SHA256_NOVERIFY, // don't verify hash on read
        SHA256, // verify hash on read
        HMAC_SHA256 // use keyed message authentication code
    }
    
	private ChordNode node;
	
    final static Logger logger = LoggerFactory.getLogger(Shard.class);
    
    private final int shardId;
    private final AtomicLong counter;
    
    private final String KEY_FILE;
    private final String TEMP_DIR;
    private final String DATA_DIR;
    
    public IdentifierAlgorithm identifierAlgo;
    SecretKeySpec secretKey;

    public Shard(Chord chordConfig, HttpConnectorFactory serverConfig) throws UnknownHostException, NoSuchAlgorithmException {
        // get my IP address and port
        InetAddress myIP = InetAddress.getLocalHost();
        int myPort = serverConfig.getPort();
        // temporary hack to enable running 2 instances on same machine
        if (myPort == 8080) {
            myIP = InetAddress.getLoopbackAddress();
        }
        
        // use first 32 bits for server id...
        shardId = inetAddressToShardId(myIP);
        String hexShardId = shardIdAsHex();
        logger.info("Registering host "+myIP+" with shardId="+hexShardId);
        
        // create temporary directories and key for this shard
        TEMP_DIR = "temp-"+hexShardId+"-"+myPort;
        DATA_DIR = "data-"+hexShardId+"-"+myPort;
        KEY_FILE = "key-"+hexShardId+"-"+myPort+".txt";
        
        (new File(TEMP_DIR)).mkdir();
        (new File(DATA_DIR)).mkdir();
        
        // determine algorithm used to generate identifiers for objects added to chord ring
        String identifierAlgoName = chordConfig.getIdentifier().toLowerCase();
        if (identifierAlgoName.equals(IdentifierAlgorithm.SHA256_NOVERIFY.toString().toLowerCase())) {
            identifierAlgo = IdentifierAlgorithm.SHA256_NOVERIFY;
        } else if (identifierAlgoName.equals(IdentifierAlgorithm.SHA256.toString().toLowerCase())) {
            identifierAlgo = IdentifierAlgorithm.SHA256;
        } else {
            identifierAlgo = IdentifierAlgorithm.HMAC_SHA256; // default
            Security.addProvider(new BouncyCastleProvider());
        }
        logger.info("Using "+identifierAlgo+" to generate identifiers for objects added to chord ring");
        
        // load key from filesystem if it exists
        secretKey = readOrCreateSecretKey();
        
        // get IP address of node in chord ring where we begin the join process
        InetAddress hostToJoin = chordConfig.getEntryHost();
        int portToJoin = chordConfig.getEntryPort();
        
        try {
            // initialize Chord node and join ring
            // note that RMI port is 1 higher than webserver port.
            node = new ChordNode(myIP, myPort+1, this);
            
            // wait 20 seconds so we can attach debugger
            logger.info("Attach debugger now");
            Thread.sleep(20000l);
            
            Finger locationToJoin = new Finger(hostToJoin, portToJoin+1);
            if ((hostToJoin.isLoopbackAddress() || hostToJoin.equals(myIP)) && portToJoin==myPort) {
                node.join(locationToJoin, true);
            } else {
                node.join(locationToJoin, false);
            }
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
    @SuppressWarnings("serial")
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
    @SuppressWarnings("serial")
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
    public String saveFile(InputStream uploadInputStream)
            throws NoSuchAlgorithmException, IOException, InvalidKeyException, NoSuchProviderException {
        byte[] digest;
        InputStream wrappedInputStream;
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        if (identifierAlgo.equals(IdentifierAlgorithm.HMAC_SHA256)) {
            wrappedInputStream = new HMACInputStream(uploadInputStream, secretKey);
        } else {
            // assume SHA256 or SHA256_NOVERIFY
            wrappedInputStream = new DigestInputStream(uploadInputStream, sha256);
        }
        
        // write file to temporary location
        java.nio.file.Path tempPath = Paths.get(TEMP_DIR, UUID.randomUUID().toString());
        Files.copy(wrappedInputStream, tempPath);
        
        if (identifierAlgo.equals(IdentifierAlgorithm.HMAC_SHA256)){
            digest = ((HMACInputStream) wrappedInputStream).getDigest();
        } else {
            // compute SHA-256 hash for both standard and noverify
            digest = sha256.digest();
        }
        
        String hexadecimalHash = Hex.encodeHexString(digest);
        
        // TODO: verify that hash is correct, distribute to other replicas...
        
        int identifier = Util.hexStringToIdentifier(hexadecimalHash);
        if (node.ownsIdentifier(identifier)) {
        	logger.info("Saving file to disk");
        	// save file to disk
        	java.nio.file.Path outputPath = Paths.get(DATA_DIR, hexadecimalHash);
        	Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE);
        } else {
        	logger.info("posting file to remote server");
        	// forward request to appropriate node
        	String serializedFile = Util.streamToString(uploadInputStream);
        	node.forwardSave(identifier, serializedFile);
        	Files.delete(tempPath);
        }
        
        return hexadecimalHash;
    }
    
    /** Update an existing item in the distributed hash table */
    //@PUT 

    /** Retrieve the item with the specified SHA-256 hash 
     * @throws DecoderException 
     * @throws IOException 
     * @throws NoSuchAlgorithmException 
     * @throws NoSuchProviderException 
     * @throws InvalidKeyException */
    @GET
    @Timed
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/{itemId}")
    @ApiOperation("Retrieve an item from this shard, or return 404 Not Found if it does not exist")
    public Response getItem(@PathParam("itemId") String idString) throws DecoderException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        java.nio.file.Path filePath = Paths.get(DATA_DIR, idString);
        if (Files.exists(filePath)) {
        	logger.info("File exists, fetching from local server");
            InputStream downloadInputStream = Files.newInputStream(filePath);
            return processFile(downloadInputStream, idString);
        } else {
    		int identifier = Util.hexStringToIdentifier(idString);
    		if (!node.ownsIdentifier(identifier)) {
    			logger.info("File doesn't exist, forwarding request");
    			String res = node.forwardLookup(identifier, idString);
    			
    			if (res == null) {
    				return Responses.notFound().build();
    			}
    			
    			InputStream downloadInputStream = Util.stringToStream(res);
    			return processFile(downloadInputStream, idString);
    		} else {
    			return Responses.notFound().build();
    		}
        }
    }
    
    public String getItemAsString(String idString) throws DecoderException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        java.nio.file.Path filePath = Paths.get(DATA_DIR, idString);
        if (Files.exists(filePath)) {
        	logger.info("Getting file for remote server as string");
            InputStream downloadInputStream = Files.newInputStream(filePath);
            return Util.streamToString(downloadInputStream);
        } 
        return null;
    }
    
    public Response processFile(InputStream downloadInputStream, String idString) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, IOException {
    	byte[] digest = null;
    	Map<String, Object> results = recordRequest();
    	MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    	// by default, just copy directly from file for IdentifierAlgorithm.SHA256_NOVERIFY
        InputStream wrappedInputStream = downloadInputStream;
        if (identifierAlgo.equals(IdentifierAlgorithm.SHA256_NOVERIFY)) {
            return Response.ok().entity(downloadInputStream).build();
        } else if (identifierAlgo.equals(IdentifierAlgorithm.HMAC_SHA256)) {
            wrappedInputStream = new HMACInputStream(downloadInputStream, secretKey);
        } else if (identifierAlgo.equals(IdentifierAlgorithm.SHA256)) {
            wrappedInputStream = new DigestInputStream(downloadInputStream, sha256);
        }
        
        // consume inputStream so that checksum computation completes
        byte[] bytes = IOUtils.toByteArray(wrappedInputStream); 
        
        if (identifierAlgo.equals(IdentifierAlgorithm.HMAC_SHA256)){
            digest = ((HMACInputStream) wrappedInputStream).getDigest();     
        } else if (identifierAlgo.equals(IdentifierAlgorithm.SHA256)) {
            digest = sha256.digest();
        }
        
        if (digest != null && !idString.equalsIgnoreCase(Hex.encodeHexString(digest))) {
            results.put("error", "request for "+idString+" does not match computed checksum "+Hex.encodeHexString(digest));
            return Response.status(Response.Status.GONE).
                type(MediaType.APPLICATION_JSON_TYPE).
                entity(results).build();
        } else {
            return Response.ok().entity(bytes).build();
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
