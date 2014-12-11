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
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.annotation.Timed;
import com.sun.jersey.api.Responses;
import com.sun.jersey.core.header.ContentDisposition;
import com.sun.jersey.multipart.FormDataBodyPart;
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
import java.rmi.RemoteException;
import java.security.DigestInputStream;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.SignatureException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
        // indicates that this node is only replicating an existing file
        // (which may be verified by another algorithm on the uploader user's node)
        SHA256_REPLICATE, 
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
    private final String REPLICA_DIR;
    
    public IdentifierAlgorithm identifierAlgo;
    SecretKeySpec secretKey;
    
    // https://stackoverflow.com/questions/5318132/is-it-possible-to-control-the-filename-for-a-response-from-a-jersey-rest-service
    public class MetadataEntry {
        public String fileName;
        protected MediaType fileType;
        public String userChecksum;
        public String sha256;
        
        public MetadataEntry(String userChecksum, String sha256) {
            this.userChecksum = userChecksum;
            this.sha256 = sha256;
        }
        
        public void setFileDetail(String fileName, MediaType fileType) {
            this.fileName = fileName;
            this.fileType = fileType;
        }
        
        public String getFileType() {
            return fileType.toString();
        }
    }
    
    /** map from user hash to file information */
    private ConcurrentHashMap<String, MetadataEntry> fileMetadata = new ConcurrentHashMap<String, MetadataEntry>();

    public Shard(Chord chordConfig, HttpConnectorFactory serverConfig) throws UnknownHostException, NoSuchAlgorithmException {
        // get my IP address and port
        InetAddress myIP = chordConfig.getMyIP();
        int myPort = serverConfig.getPort();
        
        // use first 32 bits for server id...
        shardId = inetAddressToShardId(myIP);
        String hexShardId = shardIdAsHex();
        logger.info("Registering host "+myIP+" with shardId="+hexShardId);
        
        // create temporary directories and key for this shard
        TEMP_DIR = "temp-"+hexShardId+"-"+myPort;
        DATA_DIR = "data-"+hexShardId+"-"+myPort;
        REPLICA_DIR = "replica-"+hexShardId+"-"+myPort;
        KEY_FILE = "key-"+hexShardId+"-"+myPort+".txt";
        
        (new File(TEMP_DIR)).mkdir();
        (new File(DATA_DIR)).mkdir();
        (new File(REPLICA_DIR)).mkdir();
        
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
            // wait 20 seconds so we can attach debugger
            logger.info("Attach debugger now");
            Thread.sleep(20000l);
            
            // initialize Chord node and join ring
            // note that RMI port is 1 higher than webserver port.
            node = new ChordNode(myIP, myPort+1, this);
            
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
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/meta")
    @ApiOperation("Retrieve metadata for all items uploaded to this shard")
    public HashMap<String, MetadataEntry> getMetadata() {
        return new HashMap<String, MetadataEntry>(fileMetadata);
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
    public Map<String,Object> insertItem(@FormDataParam("file") final InputStream uploadInputStream,
            @FormDataParam("file") final FormDataBodyPart fileBody) throws NoSuchAlgorithmException, IOException, InvalidKeyException, NoSuchProviderException {
        final MetadataEntry meta = saveFile(uploadInputStream, identifierAlgo);
        meta.setFileDetail(fileBody.getFormDataContentDisposition().getFileName(), fileBody.getMediaType());
        fileMetadata.put(meta.userChecksum, meta);
        return new HashMap<String,Object>() {{
            put("shard", shardIdAsHex());
            put("id", meta.userChecksum);
            put("sha256", meta.sha256);
            put("filename", meta.fileName);
            put("filetype", meta.fileType.toString());
        }};
    }
    
    /** Save uploaded inputStream to disk, return the sha256 identifier of the file 
     * @throws NoSuchAlgorithmException 
     * @throws IOException 
     * @throws InvalidKeyException 
     * @throws NoSuchProviderException */ 
    public MetadataEntry saveFile(InputStream uploadInputStream, IdentifierAlgorithm algo)
            throws NoSuchAlgorithmException, IOException, InvalidKeyException, NoSuchProviderException {
        // assume SHA256, SHA256_NOVERIFY, or SHA256_REPLICATE
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        InputStream wrappedInputStream = new DigestInputStream(uploadInputStream, sha256);
        
        byte[] serializedFile;
        HMACInputStream hmacInputStream = null;
        // convert to byte[] to consume inputStream and perform hash computation
        if (algo.equals(IdentifierAlgorithm.HMAC_SHA256)) {
            hmacInputStream = new HMACInputStream(wrappedInputStream, secretKey);
            serializedFile = IOUtils.toByteArray(hmacInputStream);
            hmacInputStream.close();
        } else {
            serializedFile = IOUtils.toByteArray(wrappedInputStream);
        }
        wrappedInputStream.close();
        
        // always compute SHA-256 hash
        byte[] sha256Digest = sha256.digest();
        String sha256Hash = Hex.encodeHexString(sha256Digest);
        
        // checksum computed via user-specified algorithm
        String userChecksum = sha256Hash;
        if (algo.equals(IdentifierAlgorithm.HMAC_SHA256)){
            byte[] hmacDigest = hmacInputStream.getDigest();
            userChecksum = Hex.encodeHexString(hmacDigest);
        }
        // TODO: return hmacDigest to uploader user? or register somewhere in the shard...
        
        // write to file
        java.nio.file.Path tempPath = Paths.get(TEMP_DIR, UUID.randomUUID().toString());
        Files.write(tempPath, serializedFile);
        
        // TODO: verify that hash is correct on read to account for disk failure?

        if (algo.equals(IdentifierAlgorithm.SHA256_REPLICATE)) {
            // remote node is asking us to replicate this file for them in REPLICA_DIR
            logger.info("Saving replica to disk with sha256Hash="+sha256Hash);
            java.nio.file.Path outputPath = Paths.get(REPLICA_DIR, sha256Hash);
            Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE);

        } else {
            // this is uploader user's node, save file to disk in DATA directory
            logger.info("Saving new file to disk with userChecksum "+algo+"="+userChecksum);
            java.nio.file.Path outputPath = Paths.get(DATA_DIR, userChecksum);
            Files.move(tempPath, outputPath, StandardCopyOption.ATOMIC_MOVE);
            
            // Start replication process
            int identifier = Util.hexStringToIdentifier(sha256Hash);
            node.beginReplicatingFile(identifier, serializedFile);
        }
        
        return new MetadataEntry(userChecksum, sha256Hash);
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
    @Path("/{itemId}")
    @ApiOperation("Retrieve an item from this shard, or return 404 Not Found if it does not exist")
    public Response getItem(@PathParam("itemId") String idString) throws DecoderException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        Map<String, Object> results = recordRequest();
        
        MetadataEntry meta = fileMetadata.get(idString);
        ContentDisposition contentDisposition = ContentDisposition.type("attachment").
                fileName((meta != null) ? meta.fileName : idString).build();
        
        // first attempt to get the original from uploader node's DATA_DIR
        java.nio.file.Path filePath = Paths.get(DATA_DIR, idString);
        if (Files.exists(filePath)) {
        	logger.info("File exists, fetching from local server");
            InputStream downloadInputStream = Files.newInputStream(filePath);
            
            try {
                byte[] bytes = verifyFile(downloadInputStream, idString);
                ResponseBuilder rb = Response.ok().entity(bytes).header("Content-Disposition", contentDisposition);
                if (meta != null) {
                    rb.type(meta.fileType);
                }
                return rb.build();
            } catch (SignatureException e) {
                logger.info("request for "+idString+" does not match checksum");
            }
            
        }
        // ask for replicas to retrieve from REPLICA_DIR
        int identifier = Util.hexStringToIdentifier(idString);
        logger.info("File doesn't exist or is corrupted, forwarding request");
        try {
            byte[] verifiedOutput = node.forwardLookup(identifier, idString);
            ResponseBuilder rb = Response.ok().entity(verifiedOutput).header("Content-Disposition", contentDisposition);
            if (meta != null) {
                rb.type(meta.fileType);
            }
            return rb.build();
        } catch (RemoteException e) {
            return Responses.notFound().build();
        } catch (SignatureException e) {
            results.put("error", e.toString());
            return Response.status(Response.Status.GONE).
                type(MediaType.APPLICATION_JSON_TYPE).
                entity(results).build();
        }
    }
    
    public byte[] getItemAsByteArray(String idString) throws DecoderException, IOException, NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        java.nio.file.Path filePath = Paths.get(REPLICA_DIR, idString);
        if (Files.exists(filePath)) {
        	logger.info("Retrieving file for remote server as byte[] "+idString);
        	return Files.readAllBytes(filePath);
        } else {
            // null indicates file not available
            logger.info("Replica "+shardId+" does not have copy of requested file "+idString);
            return null;
        }
    }
    
    /** Ensure that the retrieved file has not been tampered with by verifying checksum 
     * @throws SignatureException */ 
    public byte[] verifyFile(InputStream downloadInputStream, String idString) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, IOException, SignatureException {
    	byte[] digest = null;
    	MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    	// by default, just copy directly from file for IdentifierAlgorithm.SHA256_NOVERIFY
        InputStream wrappedInputStream = downloadInputStream;
        if (identifierAlgo.equals(IdentifierAlgorithm.HMAC_SHA256)) {
            wrappedInputStream = new HMACInputStream(downloadInputStream, secretKey);
        } else {
            wrappedInputStream = new DigestInputStream(downloadInputStream, sha256);
        }
        
        // consume inputStream so that checksum computation completes
        byte[] bytes = IOUtils.toByteArray(wrappedInputStream); 
        
        if (identifierAlgo.equals(IdentifierAlgorithm.HMAC_SHA256)){
            digest = ((HMACInputStream) wrappedInputStream).getDigest();     
        } else {
            digest = sha256.digest();
        }
        
        if (digest != null && !idString.equalsIgnoreCase(Hex.encodeHexString(digest))) {
            throw new SignatureException("File "+idString+" has invalid "+identifierAlgo.toString()+" checksum "+Hex.encodeHexString(digest));
        }
        return bytes;
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
