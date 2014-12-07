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

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.cs244b.Shard;
import edu.stanford.cs244b.Util;

/** Core components of the Chord distributed hash table implementation.
 *  Keeps track of other shards in the ring to ensure O(log n) lookup */
public class ChordNode extends UnicastRemoteObject implements RemoteChordNodeI {
    Registry registry;
    
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
        
    public ChordNode(InetAddress host, int port) throws RemoteException {
        super();
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
    public boolean join(Finger existinglocation, boolean isFirstNode) {
        try {
            RemoteChordNodeI existingNode = (RemoteChordNodeI) getChordNode(existinglocation);
            if (isFirstNode) {
                logger.info("Joining new ring, I am first node: "+existingNode.getHost()+" shardid="+Integer.toHexString(existingNode.getShardId()));
                // TODO: initialize full finger table; for now we only keep track of successor
                fingerTable = new Finger[NUM_FINGERS];
                for (int index=0; index < NUM_FINGERS; index++) {
                    fingerTable[index] = existingNode.getLocation();
                }
                predecessor = existingNode.getLocation();
            } else {
                logger.info("Joining existing ring, querying node: "+existingNode.getHost()+" shardid="+Integer.toHexString(existingNode.getShardId()));
                // TODO: reenable remote procedure call here
                initFingerTable(existingNode);
                updateOthers();
            }
            return true;
        } catch (RemoteException e) {
            logger.error("Failed to complete join", e);
        }
        return false;
    }
    
    /** Initialize finger table of local node.
     *  existingNode is an arbitrary node already on the network
     */
    public boolean initFingerTable(RemoteChordNodeI existingNode) {
        // TODO: this seems wrong, since finger table is not yet initialized for new node which is just joining...
        //fingerTable[0] = existingNode.findSuccessor(fingerTable[0].shardid);
        // temporary workaround:
        try {
            //RemoteChordNodeI successor = existingNode.findSuccessor(location.shardid);
            RemoteChordNodeI successor = findSuccessor(location.shardid);
            // TODO: this fails with java.lang.ArrayStoreException
            // since RemoteChordNodeI returns a proxy, not the actual thing
            fingerTable[0] = successor.getLocation();
            predecessor = getChordNode(getSuccessor()).getPredecessor();
            getChordNode(getSuccessor()).setPredecessor(this.location);
            logger.info("InitFingerTable, predecessor= "+predecessor+
                    "\ncurrent="+this+
                    "\nsuccessor="+getSuccessor());
            // update fingers
            for (int index=0; index < NUM_FINGERS-1; index++) {
                if (Util.withinInterval(fingerTable[index+1].shardid, location.shardid, fingerTable[index].shardid)) {
                    fingerTable[index+1] = fingerTable[index];
                } else {
                    fingerTable[index+1] = existingNode.findSuccessor(fingerTable[index+1].shardid).getLocation();
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
    
    /** Update all nodes whose finger tables should refer to n */
    public void updateOthers() {
        for (int index=0; index < NUM_FINGERS; index++) {
            // find last node p whose i'th finger might be n
            // shardid+1 to fix shadow bug (updateOthers fails to update immediate predecessor
            // of a newly joined node if that predecessor occupied the slot right behind it.
            int fingerValue = (location.shardid - (1 << index))+1;
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
        if (s.location.shardid == this.location.shardid) {
            return true;
        }
        try {
            if (Util.withinInterval(s.location.shardid, location.shardid, fingerTable[index].shardid)) {
                fingerTable[index] = s.getLocation();
                RemoteChordNodeI p = getChordNode(predecessor);
                p.updateFingerTable(s, index);
            }
        } catch (RemoteException e) {
            logger.error("Failed to updateFingerTable", e);
        }
        return true;
    }
    
    /** Convenience method for displaying shardid as a hex string */
    public String shardIdAsHex() {
        return Integer.toHexString(location.shardid);
    }
    
    @Override
    public String toString() {
        return location.toString();
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
