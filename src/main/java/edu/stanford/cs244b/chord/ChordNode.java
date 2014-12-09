package edu.stanford.cs244b.chord;

import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Random;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.cs244b.Shard;
import edu.stanford.cs244b.Util;

/** Core components of the Chord distributed hash table implementation.
 *  Keeps track of other shards in the ring to ensure O(log n) lookup */
public class ChordNode extends UnicastRemoteObject implements RemoteChordNodeI {
    Registry registry;
    
    final Shard shard;
    
    //final static int NUM_FINGERS = 32;
    final static int NUM_FINGERS = 1;
    final static Logger logger = LoggerFactory.getLogger(ChordNode.class);
    
    /** Location of this ChordNode, includes host ip, port, and shardid */
    protected Finger location;
    
    /** Pointer to immediate predecessor, can be used to walk
     *  counterclockwise around the identifier circle */
    protected Finger predecessor;
    
    /** In initial Chord implementation, only maintain a pointer to
     *  the direct successor for simplicity.
     *  TODO: keep pointer to all log(n) nodes as required by Chord. */
    protected Finger[] fingerTable;
    
    protected Stabilizer stabilizer;
        
    public ChordNode(InetAddress host, int port, Shard shard) throws RemoteException {
        super();
        
        this.shard = shard;
        
        this.location = new Finger(host, port);
        fingerTable = new Finger[NUM_FINGERS];
        for (int index=0; index < NUM_FINGERS; index++) {
            fingerTable[index] = location;
        }
        String ipAddress=host.getHostAddress();
        System.getProperties().put("java.rmi.server.hostname", ipAddress);
        try {
        	registry = LocateRegistry.createRegistry(port);
        } catch (Exception e) {
        	registry = LocateRegistry.getRegistry();
        }
        try {
        	// insert ChordNode into RMI registry
            String rmiURL = location.getRMIUrl();
        	logger.info("Binding to registry at "+rmiURL);
        	Naming.rebind(rmiURL, this);
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
            String rmiURL = location.getRMIUrl();
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
    	try {
    		predecessor = null;
    		fingerTable[0] = getChordNode(existingLocation).findSuccessor(getShardId()).getLocation();
    		stabilizer = new Stabilizer(this);
    		stabilizer.start();
    	} catch (RemoteException e) {
    		logger.error("Failed to get successor", e);
    	}
    	return false;
    }
    
    /** Periodically run to verify successor relationship */
    public void stabilize() {
    	try {
    		Finger x = getChordNode(getSuccessor()).getPredecessor();
    		if (x != null && (x.host == location.host || x.host == getSuccessor().host)) {
    			fingerTable[0] = x;
    		}
    		getChordNode(getSuccessor()).notifyPredecessor(location);
    	} catch (RemoteException e) {
    		logger.error("Failed to update and notify successor", e);
    	}
    }
    
    /** Notify node of request to become predecessor */
    @Override
    public void notifyPredecessor(Finger newPredecessor) {
    	if (predecessor == null ||
    		predecessor.host == newPredecessor.host ||
    		predecessor.host == location.host) {
    			predecessor = newPredecessor;
    	}
    }
    
    /** Choose a random node and update finger table */
    public void fixFingers() {
    	try {
	    	Random rgen = new Random();
	    	int i = rgen.nextInt(NUM_FINGERS);
	    	fingerTable[i] = findSuccessor(fingerTable[i].shardid).getLocation();
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
        logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+Integer.toHexString(next.getShardId()));
        while (!Util.withinInterval(identifier, next.getShardId()+1, next.getSuccessor().shardid+1)) {
            next = next.closestPrecedingFinger(identifier);
            logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+Integer.toHexString(next.getShardId()));
        }
        return next;
        
    }
    
    @Override
    public RemoteChordNodeI closestPrecedingFinger(int identifier) {
        
        // lookup in finger tree 
        for (int index = NUM_FINGERS-1; index >= 0; index--) {
            try {
                if (Util.withinInterval(fingerTable[index].shardid, location.shardid+1, identifier)) {
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
    	return Util.withinInterval(identifier, this.getShardId(), this.getSuccessor().shardid);
    }

    /** Forward POST request to appropriate node */
	public void forwardSave(int identifier, final InputStream uploadInputStream) {
		try {
			RemoteChordNodeI node = getChordNode(findPredecessor(identifier).getLocation());
			node.saveFile(uploadInputStream);
		} catch (RemoteException e) {
			logger.error("Error forwarding save request", e);
		}
	}
	
	/** Receive forwarded request */
	@Override
	public void saveFile(final InputStream uploadInputStream) {
		try {
			this.shard.saveFile(uploadInputStream);
		} catch (Exception e) {
			logger.error("Error saving file", e);
		}
	}
	
	public Response forwardLookup(int identifier, String hash) {
		try {
			RemoteChordNodeI node = getChordNode(findPredecessor(identifier).getLocation());
			return node.getFile(hash);
		} catch (RemoteException e) {
			logger.error("Error looking up file on remote node", e);
			return null;
		}
	}

	/** Remote method to return item if contained on this server */
	@Override
	public Response getFile(String hash) {
		try {
			return this.shard.getItem(hash);
		} catch (Exception e) {
			logger.error("Error getting file", e);
			return null;
		}
	}
}
