package edu.stanford.cs244b.chord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.Responses;

import edu.stanford.cs244b.Shard;
import edu.stanford.cs244b.Shard.IdentifierAlgorithm;
import edu.stanford.cs244b.Util;

/** Core components of the Chord distributed hash table implementation.
 *  Keeps track of other shards in the ring to ensure O(log n) lookup */

public class ChordNode implements RemoteChordNodeI {
    Registry registry;
    
    final Shard shard;
    
    final static Logger logger = LoggerFactory.getLogger(ChordNode.class);
    
    /** Location of this ChordNode, includes host ip, port, and shardid */
    protected Finger location;
    
    /** Pointer to immediate predecessor, can be used to walk
     *  counterclockwise around the identifier circle */
    protected Finger predecessor;
    
    final static int NUM_FINGERS = 32;
    
    /** In initial Chord implementation, only maintain a pointer to
     *  the direct successor for simplicity.
     *  TODO: keep pointer to all log(n) nodes as required by Chord. */
    protected Finger[] fingerTable;
    
    /** Each file is sent to REPLICATION_FACTOR nodes in
     *  addition to the origin node. */
    final static int REPLICATION_FACTOR = 2;
    
    /** List of successors to check in case of failure */
    protected Finger[] successorList = new Finger[REPLICATION_FACTOR];
    
    protected Stabilizer stabilizer;
        
    public ChordNode(InetAddress host, int port, Shard shard) throws RemoteException {
        super();
        
        this.shard = shard;
        
        this.location = new Finger(host, port);
        fingerTable = new Finger[NUM_FINGERS];
        for (int index=0; index < NUM_FINGERS; index++) {
            fingerTable[index] = location;
        }
        
        for (int i = 0; i < REPLICATION_FACTOR; i++) {
        	successorList[i] = null;
        }
        
        // insane hack to get RMI working in virtualbox
        System.getProperties().put("java.rmi.server.hostname", host.getHostAddress());
        RemoteChordNodeI stub = (RemoteChordNodeI) UnicastRemoteObject.exportObject(this, 0);

        try {
        	registry = LocateRegistry.createRegistry(port);
        } catch (Exception e) {
        	registry = LocateRegistry.getRegistry();
        }
        try {
        	// insert ChordNode into RMI registry
            String rmiURL = location.getRMIUrl();
        	logger.info("Binding to registry at "+rmiURL);
        	Naming.bind(rmiURL, stub);

        } catch (Exception e) {
        	logger.error("Registering host "+host+" in Chord ring with shardId="+shardIdAsHex()+" FAILED");
        	e.printStackTrace();
        }
    }
    
    /** Given a location, lookup the corresponding RemoteChordNodeI */
    public RemoteChordNodeI getChordNode(Finger remoteLocation) throws RemoteException {
        try {
            // OMG, figuring this out was painful...
            // http://euclid.nmu.edu/~rappleto/Classes/RMI/rmi-coding.html
            
            // insane hack to get RMI working in virtualbox
            System.getProperties().put("java.rmi.server.hostname", remoteLocation.host.getHostAddress());
            //Registry registry = LocateRegistry.getRegistry(remoteLocation.host.getHostAddress(), remoteLocation.port);
            String rmiURL = remoteLocation.getRMIUrl();
            
            RemoteChordNodeI chordNode = (RemoteChordNodeI) Naming.lookup(rmiURL);
            Finger nodeLocation = chordNode.getLocation(); // verify that we can contact ChordNode at specified location
            return chordNode;
        } catch (Exception e) {
            logger.error("Failed to get remote ChordNode at location "+remoteLocation, e);
            throw new RemoteException("Failed to get remote ChordNode at location "+remoteLocation);
        }
    }
    
    @Override
    public Finger getLocation() {
        return this.location;
    }
    
    @Override
    public int getShardId() {
        return this.location.shardid;
    }
    
    @Override
    public InetAddress getHost() {
        return this.location.host;
    }
    
    @Override
    public Finger getSuccessor() {
        return fingerTable[0];
    }
    
    @Override
    public Finger getPredecessor() {
        return predecessor;
    }
    
    @Override
    public void setPredecessor(Finger newPredecessor) {
        this.predecessor = newPredecessor;
    }
    
    /** When node <i>n</i> joins the network:
     *  <ol>
     *  <li>Initialize predecessor TODO: and fingers of node <i>n</i></li>
     *  <li>TODO: Update the fingers and predecessors of existing nodes to reflect the addition of node <i>n</i></li>
     *  <li>TODO: Notify higher-level software so it can transfer values associated with keys that node <i>n</i> is now responsible for</li>
     *  </ol>
     *  Returns true if join succeeded, false otherwise
     */
    public boolean join(Finger existingLocation, boolean isFirstNode) {
        logger.info("Joining node "+existingLocation+"; isFirstNode="+isFirstNode);
    	try {
    		predecessor = null;
    		fingerTable[0] = getChordNode(existingLocation).findSuccessor(getShardId()).getLocation();
    		
    		if (!isFirstNode) {
    			// check for malicious nodes
    			Finger[] remoteFingerTable = getChordNode(getSuccessor()).getFingerTable();
    			
    			// Nodes we must find
    			Set<Integer> nodesToFind = new HashSet<Integer>();
    			for (Finger f : remoteFingerTable) {
    				nodesToFind.add(Integer.valueOf(f.shardid));
    			}
    			// Include trusted node if it isn't in finger table
    			nodesToFind.add(Integer.valueOf(existingLocation.shardid));
    			// Remove node we started from
    			nodesToFind.remove(Integer.valueOf(getSuccessor().shardid));
    			
    			// Walk successor pointers in ring. Stop when you get reach yourself or your successor.
    			Finger successor = remoteFingerTable[0]; // actually the 2nd successor 
    			do {
    				// This also validates that node is reachable
    				Finger next = getChordNode(successor).getSuccessor();
    				
    				// Check if you found a finger you are looking for
    				Integer currId = Integer.valueOf(successor.shardid);
    				if (nodesToFind.contains(currId)) {
    					nodesToFind.remove(currId);
    				}
    				
    				successor = next;
    				
    			} while (successor.shardid != getShardId() && successor.shardid != getSuccessor().shardid);
    			
    			// If not all fingers were found, error occurred
    			if (!nodesToFind.isEmpty()) {
    				logger.error("WARNING: MALICIOUS NODE, FAILED TO JOIN RING");
    				return false;
    			}
    		}
    		
    		refreshSuccessors(0);
    		
    		stabilizer = new Stabilizer();
    		stabilizer.start();
    		return true;
    	} catch (RemoteException e) {
    		logger.error("Failed to get successor", e);
    	}
    	return false;
    }
    
    /** Periodically run to verify successor relationship */
    public void stabilize() {
    	try {
    		Finger x = getChordNode(getSuccessor()).getPredecessor();
    		if (x != null &&
                    Util.withinInterval(x.shardid, location.shardid+1, getSuccessor().shardid-1)) {
    			logger.info("Updating successor from "+Integer.toHexString(getSuccessor().shardid)+" to "+Integer.toHexString(x.shardid));
    		    fingerTable[0] = x;
    		    getChordNode(predecessor).refreshSuccessors(REPLICATION_FACTOR - 1);
    		}
    		RemoteChordNodeI successor = getChordNode(getSuccessor());
    		successor.notifyPredecessor(location);
    	} catch (RemoteException e) {
    		logger.error("Failed to update and notify successor", e);
    	}
    }
    
    /** Notify node of request to become predecessor */
    @Override
    public void notifyPredecessor(Finger newPredecessor) {
    	if (predecessor == null ||
    	        Util.withinInterval(newPredecessor.shardid, predecessor.shardid+1, location.shardid-1)) {
    	    String oldPredecessor = (predecessor == null ? "null" : Integer.toHexString(predecessor.shardid));
    	    logger.info("Updating predecessor from "+oldPredecessor+" to "+Integer.toHexString(newPredecessor.shardid));
    		predecessor = newPredecessor;
    	}
    }
    
    /** Choose a random node and update finger table */
    public void fixFingers() {
    	try {
	    	Random rgen = new Random();
	    	int i = rgen.nextInt(NUM_FINGERS - 1) + 1;
	    	Finger f = findSuccessor(fingerTable[i].shardid).getLocation();
//	    	logger.info("Updating fingerTable[" + i + "] from "+ fingerTable[i] + " to " + f);
	    	fingerTable[i] = f;
    	} catch (RemoteException e) {
    		logger.error("Failed to update finger table", e);
    	}
    }
    
    @Override
    public RemoteChordNodeI findSuccessor(int identifier) throws RemoteException {
        RemoteChordNodeI next = findPredecessor(identifier);
        return getChordNode(next.getSuccessor());
    }
    
    @Override
    public RemoteChordNodeI findPredecessor(int identifier) throws RemoteException {
        RemoteChordNodeI next = this;
        //logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+Integer.toHexString(next.getShardId()));
        while (!Util.withinInterval(identifier, next.getShardId()+1, next.getSuccessor().shardid)) {
            next = next.closestPrecedingFinger(identifier);
//            logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+Integer.toHexString(next.getShardId()));
        }
        return next;
        
    }
    
    @Override
    public RemoteChordNodeI closestPrecedingFinger(int identifier) {
        
        // lookup in finger tree 
        for (int index = NUM_FINGERS-1; index >= 0; index--) {
            try {
                if (Util.withinInterval(fingerTable[index].shardid, location.shardid+1, identifier-1)) {
                    return getChordNode(fingerTable[index]);
                }
            } catch (RemoteException e) {
                logger.error("closestPrecedingFinger failed to lookup finger", e);
                // TODO: howto deal with lookup failure until fixFingers runs?
            }
        }
        return this;
    }
    
    /** Leave Chord ring and update other nodes */
    public void leave() {
    	if (this.location.host == predecessor.host) {
    		return;
    	}
    	
    	stabilizer.cancel();
    	
    	try {
    		getChordNode(getSuccessor()).setPredecessor(predecessor);
    	} catch (RemoteException e) {
    		logger.error("Failed to set successor's predecessor", e);
    	}
    	
    	for (int i=0; i < NUM_FINGERS; i++) {
    		int fingerValue = (location.shardid - (1 << i))+1;
    		
    		try {
    			RemoteChordNodeI p = findPredecessor(fingerValue);
    			p.removeNode(this, i, getSuccessor());
    		} catch (RemoteException e) {
    			logger.error("Failed to find predecessor", e);
    		}
    		
    	}
    }
    
    @Override
    public void removeNode(ChordNode node, int i, Finger replacement) {
    	if (fingerTable[i].host == node.getLocation().host) {
    		fingerTable[i] = replacement;
    		try {
    			getChordNode(predecessor).removeNode(node, i, replacement);
    		} catch (RemoteException e) {
    			logger.error("Failed to remove node from predecessor", e);
    		}
    	}
    }
    
    /** Convenience method for displaying shardid as a hex string */
    public String shardIdAsHex() {
        return Integer.toHexString(location.shardid);
    }
    
    @Override
    public String toString() {
        return location.toString();
    }
    
    public boolean ownsIdentifier(int identifier) {
    	return Util.withinInterval(identifier, this.getShardId(), this.getSuccessor().shardid-1);
    }
		
	/** Look up file on remote replica node, keep looking at successors for replica 
	 * @throws RemoteException 
	 * @throws SignatureException */
	public byte[] forwardLookup(int identifier, String hash) throws RemoteException, SignatureException, IOException {
	    int numTries = REPLICATION_FACTOR;
	    RemoteChordNodeI replica = null;
	    while (numTries > 0) {
	        try {
	            numTries--;
	            if (replica == null) {
	                // replica from previous iteration was dead, try looking it up again
                    replica = getChordNode(findPredecessor(identifier).getLocation());
	            } else {
	                // replica from previous iteration returned corrupt data, try its successor
                    replica = getChordNode(replica.getSuccessor());
	            }
	            int shardId = replica.getShardId(); // will throw remoteException if this fails
	            byte[] retrievedData = replica.getFile(hash);
	            if (retrievedData == null) {
	                logger.error("Replica "+Integer.toHexString(shardId)+" does not have copy of file "+hash);
	                continue;
	            }

	            InputStream downloadInputStream = new ByteArrayInputStream(retrievedData);
	            byte[] verifiedBytes;
                verifiedBytes = shard.verifyFile(downloadInputStream, hash);
	            return verifiedBytes;
	        } catch (RemoteException e) {
	            logger.error("Error looking up remote node", e);
	            if (numTries <= 0) {
	                throw e;
	            } else {
	                // reset replica, try again after waiting for stabilize to complete
	                replica = null;
	                try {
                        Thread.sleep(Stabilizer.SLEEP_MILLIS + Stabilizer.SLEEP_MILLIS);
                    } catch (InterruptedException e1) {
                        logger.error("Thread sleep failed", e);
                    }
	            }
	        } catch (SignatureException | IOException e) {
	            logger.error("Signature mismatch", e.getLocalizedMessage());
	            if (numTries <= 0) {
	                throw e;
	            } // try successor replica in next iteration 
	        } catch (InvalidKeyException | NoSuchAlgorithmException
                    | NoSuchProviderException e) {
                logger.error("Failure on uploader user's local node", e);
            }
	    }
	    throw new RemoteException("Cannot retrieve file from replicas");
	}

	/** Remote method to return item if contained on this server */
	@Override
	public byte[] getFile(String hash) {
		logger.info("Looking up object for remote server");
		try {
			return this.shard.getItemAsByteArray(hash);
		} catch (Exception e) {
			logger.error("Error getting file", e);
			return null;
		}
	}
	
	/** Get finger table for new node to verify */
	@Override
	public Finger[] getFingerTable() {
		return fingerTable;
	}
	
	/** Find node where we should start replication from, and send saved file down the ring to be replicated */
    public void beginReplicatingFile(int identifier, byte[] data) {
        try {
            if (REPLICATION_FACTOR > 0) {
                getChordNode(findPredecessor(identifier).getLocation()).replicateFile(data, REPLICATION_FACTOR);
            }
        } catch (RemoteException e) {
            logger.error("Failed to replicate file", e);
        }
    }
	
	/** Receive replication request from predecessor */
	@Override
	public void replicateFile(byte[] data, int nodesLeft) {
		InputStream uploadInputStream = new ByteArrayInputStream(data);
		
		try {
		    // always use SHA256_REPLICATE for replicated files, since
            // only the user's node has secret key for HMAC
			shard.saveFile(uploadInputStream, IdentifierAlgorithm.SHA256_REPLICATE);
		} catch (Exception e) {
			logger.error("Failed to save replicated file", e);
		}
		
		if (nodesLeft > 0) {
			try {
				getChordNode(getSuccessor()).replicateFile(data, nodesLeft - 1);
			} catch (RemoteException e) {
				logger.error("Failed to replicate file further", e);
			}
		}
	}

	
	/** Used to update successor list */
	@Override
	public void refreshSuccessors(int nodesLeft) throws RemoteException {
		Set<Integer> seenSuccessors = new HashSet<Integer>();
		Finger successor = getSuccessor();
		for (int i = 0; i < REPLICATION_FACTOR; i++) {
			if (seenSuccessors.contains(Integer.valueOf(successor.shardid))) {
				// End of ring, set rest of list to null
				while (i < REPLICATION_FACTOR) {
					successorList[i] = null;
					i++;
				}
				break;
			}
			successorList[i] = successor;
			seenSuccessors.add(Integer.valueOf(successor.shardid));
			successor = getChordNode(successor).getSuccessor();
		}
		
		if (nodesLeft > 0) {
			getChordNode(predecessor).refreshSuccessors(nodesLeft - 1);
		}
	}
	
	/** Indicates whether successor pointers are correct */
	public boolean stable() {
		return getSuccessor().shardid != location.shardid;
	}
    
    public class Stabilizer extends Thread {
        final static int SLEEP_MILLIS = 1000;
        
        /** Run stabilization and fix fingers for ChordNode */
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    stabilize();
                    fixFingers();
//                    logger.info("Node "+Integer.toHexString(location.shardid)+" predecessor="+Integer.toHexString(predecessor.shardid)+" successor="+Integer.toHexString(fingerTable[0].shardid));
                    Thread.sleep(SLEEP_MILLIS);
                }
            } catch (InterruptedException e) {
                logger.info("Exiting...", e);
            }
        }
        
        /** Kill stabilization thread */
        public void cancel() {
            interrupt();
        }
    }
}
