package edu.stanford.cs244b.chord;

import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Remote;
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
public class ChordNode implements ChordInterface {
    final static int NUM_FINGERS = 1;
    final static Logger logger = LoggerFactory.getLogger(ChordNode.class);
    
    protected InetAddress host;
    protected int port;
    protected int shardid;
    
    /** Pointer to immediate predecessor, can be used to walk
     *  counterclockwise around the identifier circle */
    protected ChordInterface predecessor;
    
    /** In initial Chord implementation, only maintain a pointer to
     *  the direct successor for simplicity.
     *  TODO: keep pointer to all log(n) nodes as required by Chord. */
    protected ChordInterface[] fingerTable;
        
    public ChordNode(InetAddress host, int port) throws RemoteException {
        this.host = host;
        this.port = port;
        this.shardid = Shard.inetAddressToShardId(host);
        fingerTable = new ChordInterface[NUM_FINGERS];
        
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
    
    public ChordInterface getSuccessor() throws RemoteException {
        return fingerTable[0];
    }
    
    public ChordInterface findSuccessor(int identifier) throws RemoteException {
        ChordInterface next = findPredecessor(identifier);
        return next.getSuccessor();
    }
    
    public ChordInterface findPredecessor(int identifier) throws RemoteException {
        ChordInterface next = this;
        logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+shardIdAsHex());
        while (!Util.withinInterval(identifier, next.getShardId(), next.getSuccessor().getShardId())) {
            next = next.closestPrecedingFinger(identifier);
            logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+next.getShardId());
        }
        return next;
        
    }
    
    public ChordInterface closestPrecedingFinger(int identifier) throws RemoteException {
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
    public boolean join(ChordInterface existingNode, boolean isFirstNode) throws RemoteException {
    	// TODO: look up all other objects in ring in registry
        if (isFirstNode) {
            logger.info("Joining new ring, I am first node: "+existingNode.getHost()+" shardid="+existingNode.getShardId());
            // TODO: initialize full finger table; for now we only keep track of successor
            fingerTable = new ChordInterface[] { existingNode };
            predecessor = existingNode;
        } else {
            logger.info("Joining existing ring, querying node: "+existingNode.getHost()+" shardid="+existingNode.getShardId());
            // TODO: invoke remote procedure call here
            initFingerTable(existingNode);
            updateOthers();
        }
        return true;
    }
    
    public boolean initFingerTable(ChordInterface existingNode) throws RemoteException {
        // TODO: this seems wrong, since finger table is not yet initialized for new node which is just joining...
        // fingerTable[0] = existingNode.findSuccessor(fingerTable[0].shardid);
        // temporary workaround:
        fingerTable[0] = existingNode.findSuccessor(shardid);
        predecessor = getSuccessor().getPredecessor();
        getSuccessor().setPredecessor(this);
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
        	ChordInterface p = findPredecessor(shardid - (1 << index));
            p.updateFingerTable(this, index);
        }
    }
    
    public boolean updateFingerTable(ChordInterface s, int index) throws RemoteException {
    	int remoteShardId = s.getShardId();
        if (remoteShardId == this.shardid) {
            return true;
        }
        if (Util.withinInterval(remoteShardId, shardid, fingerTable[index].getShardId())) {
            fingerTable[index] = s;
            ChordInterface p = predecessor;
            p.updateFingerTable(s, index);
        }
        return true;
    }
    
    public void leave() throws RemoteException {
    	// TODO: implement this
    }
    
    public void removeNode(ChordInterface node, int index, ChordInterface replacement) throws RemoteException {
    	// TODO: implement this
    }
    
    public InetAddress getHost() throws RemoteException {
    	return host;
    }
    
    public int getShardId() throws RemoteException {
    	return shardid;
    }
    
    public ChordInterface getPredecessor() throws RemoteException {
    	return predecessor;
    }
    
    public void setPredecessor(ChordInterface predecessor) throws RemoteException {
    	this.predecessor = predecessor;
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
