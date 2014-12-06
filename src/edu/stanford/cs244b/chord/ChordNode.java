package edu.stanford.cs244b.chord;

import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.cs244b.Shard;
import edu.stanford.cs244b.Util;

/** Core components of the Chord distributed hash table implementation.
 *  Keeps track of other shards in the ring to ensure O(log n) lookup */
public class ChordNode extends UnicastRemoteObject implements RemoteChordNodeI {
    //final static int NUM_FINGERS = 32;
    final static int NUM_FINGERS = 1;
    final static Logger logger = LoggerFactory.getLogger(ChordNode.class);
    
    protected InetAddress host;
    protected int port;
    protected int shardid;
    
    /** Pointer to immediate predecessor, can be used to walk
     *  counterclockwise around the identifier circle */
    protected RemoteChordNodeI predecessor;
    
    /** In initial Chord implementation, only maintain a pointer to
     *  the direct successor for simplicity.
     *  TODO: keep pointer to all log(n) nodes as required by Chord. */
    protected RemoteChordNodeI[] fingerTable;
        
    public ChordNode(InetAddress host, int port) throws RemoteException {
        this.host = host;
        this.port = port;
        this.shardid = Shard.inetAddressToShardId(host);
        fingerTable = new ChordNode[NUM_FINGERS];
        for (int index=0; index < NUM_FINGERS; index++) {
            fingerTable[index] = this;
        }
        
        Registry registry;
        try {
        	registry = LocateRegistry.createRegistry(1099);
        } catch (Exception e) {
        	registry = LocateRegistry.getRegistry();
        }
        
        try {
        	// insert ChordNode into RMI registry
        	ChordInterface stub = (ChordInterface) UnicastRemoteObject.exportObject(this, 0);
//        	registry.bind(Integer.toString(shardid), stub);
        	System.out.println("\n\n\nBINDING TO REGISTRY AS "+Integer.toString(port)+"\n\n\n");
        	registry.bind(Integer.toString(port), this); // using port to hardcode 2-node ring
        } catch (Exception e) {
        	logger.warn("Registering host "+host+" in Chord ring with shardId="+shardIdAsHex()+" FAILED");
        	e.printStackTrace();
        }
    }
    
    @Override
    public int getShardId() {
        return this.shardid;
    }
    
    @Override
    public RemoteChordNodeI getSuccessor() {
        return fingerTable[0];
    }
    
    @Override
    public RemoteChordNodeI getPredecessor() {
        return predecessor;
    }
    
    @Override
    public void setPredecessor(RemoteChordNodeI newPredecessor) {
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
    public boolean join(ChordNode existingNode, boolean isFirstNode) {
        if (isFirstNode) {
            logger.info("Joining new ring, I am first node: "+existingNode.host+" shardid="+existingNode.shardIdAsHex());
            // TODO: initialize full finger table; for now we only keep track of successor
            fingerTable = new ChordNode[NUM_FINGERS];
            for (int index=0; index < NUM_FINGERS; index++) {
                fingerTable[index] = existingNode;
            }
            predecessor = existingNode;
        } else {
            logger.info("Joining existing ring, querying node: "+existingNode.host+" shardid="+existingNode.shardIdAsHex());
            // TODO: invoke remote procedure call here
            initFingerTable(existingNode);
            updateOthers();
        }
        return true;
    }
    
    /** Initialize finger table of local node.
     *  existingNode is an arbitrary node already on the network
     */
    public boolean initFingerTable(ChordNode existingNode) {
        // TODO: this seems wrong, since finger table is not yet initialized for new node which is just joining...
        //fingerTable[0] = existingNode.findSuccessor(fingerTable[0].shardid);
        // temporary workaround:
        try {
            fingerTable[0] = existingNode.findSuccessor(shardid);
            predecessor = getSuccessor().getPredecessor();
            getSuccessor().setPredecessor(this);
            logger.info("InitFingerTable, predecessor= "+predecessor+
                    "\ncurrent="+this+
                    "\nsuccessor="+getSuccessor());
            // update fingers
            for (int index=0; index < NUM_FINGERS-1; index++) {
                if (Util.withinInterval(fingerTable[index+1].getShardId(), shardid, fingerTable[index].getShardId())) {
                    fingerTable[index+1] = fingerTable[index];
                } else {
                    fingerTable[index+1] = existingNode.findSuccessor(fingerTable[index+1].getShardId());
                    //if (!Util.withinInterval(fingerTable[index+1].shardid, shardid, fingerTable[index].shardid)) {
                }
            }
            return true;
        } catch (RemoteException e) {
            logger.error("Failed to initFingerTable", e);
            return false;
        }
    }
    
    @Override
    public RemoteChordNodeI findSuccessor(int identifier) throws RemoteException {
        RemoteChordNodeI next = findPredecessor(identifier);
        return next.getSuccessor();
    }
    
    @Override
    public RemoteChordNodeI findPredecessor(int identifier) throws RemoteException {
        RemoteChordNodeI next = this;
        logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+Integer.toHexString(next.getShardId()));
        while (!Util.withinInterval(identifier, next.getShardId()+1, next.getSuccessor().getShardId()+1)) {
            next = next.closestPrecedingFinger(identifier);
            logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+Integer.toHexString(next.getShardId()));
        }
        return next;
        
    }
    
    @Override
    public RemoteChordNodeI closestPrecedingFinger(int identifier) {
        
        RemoteChordNodeI successor = this.getSuccessor();
        // lookup in finger tree 
        for (int index = NUM_FINGERS-1; index >= 0; index--) {
            try {
                if (Util.withinInterval(fingerTable[index].getShardId(), shardid+1, identifier)) {
                    return fingerTable[index];
                }
            } catch (RemoteException e) {
                logger.error("closestPrecedingFinger failed to lookup finger", e);
                // TODO: howto deal with lookup failure until fixFingers runs?
            }
        }
        return this;
    }
    
    /** Update all nodes whose finger tables should refer to n */
    public void updateOthers() {
        for (int index=0; index < NUM_FINGERS; index++) {
            // find last node p whose i'th finger might be n
            // shardid+1 to fix shadow bug (updateOthers fails to update immediate predecessor
            // of a newly joined node if that predecessor occupied the slot right behind it.
            int fingerValue = (shardid - (1 << index))+1;
            try {
                RemoteChordNodeI p = findPredecessor(fingerValue);
                p.updateFingerTable(this, index);
            } catch (RemoteException e) {
                logger.error("Failed to updateOthers", e);
                // wait for fixFingers to update in future.
            }
        }
    }
    
    @Override
    public boolean updateFingerTable(ChordNode s, int index) {
        if (s.shardid == this.shardid) {
            return true;
        }
        try {
            if (Util.withinInterval(s.shardid, shardid, fingerTable[index].getShardId())) {
                fingerTable[index] = s;
                RemoteChordNodeI p = predecessor;
                p.updateFingerTable(s, index);
            }
        } catch (RemoteException e) {
            logger.error("Failed to updateFingerTable", e);
        }
        return true;
    }
    
    /** Convenience method for displaying shardid as a hex string */
    public String shardIdAsHex() {
        return Integer.toHexString(shardid);
    }
    
    @Override
    public String toString() {
        return "shardid="+shardIdAsHex()+" @"+host;
    }

    /** Remote method to accept POST request from another server in Chord ring
	@Override
	public String forwardRequest(final InputStream uploadInputStream) throws RemoteException {
		// TODO: Call shard.insertItem with the data given
		return "";
	}*/

	/** Remote method to return item if contained on this server 
	@Override
	public Response getRemoteItem(String identifier) throws RemoteException {
		// TODO: Return object from shard if exists or forward GET
		// to next node in the finger table
		return null;
	}*/
}
