package edu.stanford.cs244b.chord;

import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.cs244b.Shard;
import edu.stanford.cs244b.Util;

/** Core components of the Chord distributed hash table implementation.
 *  Keeps track of other shards in the ring to ensure O(log n) lookup */
public class ChordNode extends UnicastRemoteObject implements ChordInterface {
    final static int NUM_FINGERS = 1;
    final static Logger logger = LoggerFactory.getLogger(ChordNode.class);
    
    protected InetAddress host;
    protected int port;
    protected int shardid;
    
    /** Pointer to immediate predecessor, can be used to walk
     *  counterclockwise around the identifier circle */
    protected ChordNode predecessor;
    
    /** In initial Chord implementation, only maintain a pointer to
     *  the direct successor for simplicity.
     *  TODO: keep pointer to all log(n) nodes as required by Chord. */
    protected ChordNode[] fingerTable;
        
    public ChordNode(InetAddress host, int port) throws RemoteException {
        this.host = host;
        this.port = port;
        this.shardid = Shard.inetAddressToShardId(host);
        fingerTable = new ChordNode[NUM_FINGERS];
        
        try {
        	// insert ChordNode into RMI registry
        	ChordInterface stub = (ChordInterface) UnicastRemoteObject.exportObject(this, 0);
        	Registry registry = LocateRegistry.getRegistry();
        	registry.bind(Integer.toString(shardid), stub);
        } catch (Exception e) {
        	logger.warn("Registering host "+host+" in Chord ring with shardId="+shardIdAsHex()+" FAILED");
        }
    }
    
    public ChordNode getSuccessor() throws RemoteException {
        return fingerTable[0];
    }
    
    public ChordNode findSuccessor(int identifier) throws RemoteException {
        ChordNode next = findPredecessor(identifier);
        return next.getSuccessor();
    }
    
    public ChordNode findPredecessor(int identifier) throws RemoteException {
        ChordNode next = this;
        logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+next.shardIdAsHex());
        while (!Util.withinInterval(identifier, next.shardid, next.getSuccessor().shardid)) {
            next = next.closestPrecedingFinger(identifier);
            logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+next.shardIdAsHex());
        }
        return next;
        
    }
    
    public ChordNode closestPrecedingFinger(int identifier) throws RemoteException {
        // TODO: lookup in finger tree
        // for now, just return successor node
        return this.getSuccessor();
    }
    
    /** When node <i>n</i> joins the network:
     *  <ol>
     *  <li>Initialize predecessor TODO: and fingers of node <i>n</i></li>
     *  <li>TODO: Update the fingers and predecessors of existing nodes to reflect the addition of node <i>n</i></li>
     *  <li>TODO: Notify higher-level software so it can transfer values associated with keys that node <i>n</i> is now responsible for</li>
     *  </ol>
     *  Returns true if join succeeded, false otherwise
     */
    public boolean join(ChordNode existingNode, boolean isFirstNode) throws RemoteException {
    	// TODO: look up all other objects in ring in registry
        if (isFirstNode) {
            logger.info("Joining new ring, I am first node: "+existingNode.host+" shardid="+existingNode.shardIdAsHex());
            // TODO: initialize full finger table; for now we only keep track of successor
            fingerTable = new ChordNode[] { existingNode };
            predecessor = existingNode;
        } else {
            logger.info("Joining existing ring, querying node: "+existingNode.host+" shardid="+existingNode.shardIdAsHex());
            // TODO: invoke remote procedure call here
            initFingerTable(existingNode);
            updateOthers();
        }
        return true;
    }
    
    public boolean initFingerTable(ChordNode existingNode) throws RemoteException {
        // TODO: this seems wrong, since finger table is not yet initialized for new node which is just joining...
        // fingerTable[0] = existingNode.findSuccessor(fingerTable[0].shardid);
        // temporary workaround:
        fingerTable[0] = existingNode.findSuccessor(shardid);
        predecessor = getSuccessor().predecessor;
        getSuccessor().predecessor = this;
        logger.info("InitFingerTable, predecessor= "+predecessor+
                "\ncurrent="+this+
                "\nsuccessor="+getSuccessor());
        // TODO: for i=1 to m-1: update fingers
        //for (int index=0; index < NUM_FINGERS; index++) {
        //}
        return true;
    }
    
    public void updateOthers() throws RemoteException {
        for (int index=0; index < NUM_FINGERS; index++) {
            // find last node p whose i'th finger might be n
            ChordNode p = findPredecessor(shardid - (1 << index));
            p.updateFingerTable(this, index);
        }
    }
    
    public boolean updateFingerTable(ChordNode s, int index) throws RemoteException {
        if (s.shardid == this.shardid) {
            return true;
        }
        if (Util.withinInterval(s.shardid, shardid, fingerTable[index].shardid)) {
            fingerTable[index] = s;
            ChordNode p = predecessor;
            p.updateFingerTable(s, index);
        }
        return true;
    }
    
    public boolean leave() throws RemoteException {
    	return false;
    }
    
    public boolean removeNode(ChordNode node, int index, ChordNode replacement) throws RemoteException {
    	return false;
    }
    
    /** Convenience method for displaying shardid as a hex string */
    public String shardIdAsHex() {
        return Integer.toHexString(shardid);
    }
    
    @Override
    public String toString() {
        return "shardid="+shardIdAsHex()+" @"+host;
    }

}
