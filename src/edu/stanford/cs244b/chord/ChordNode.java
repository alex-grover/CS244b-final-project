package edu.stanford.cs244b.chord;

import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.stanford.cs244b.Shard;
import edu.stanford.cs244b.Util;

/** Core components of the Chord distributed hash table implementation.
 *  Keeps track of other shards in the ring to ensure O(log n) lookup */
public class ChordNode {
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
        
    public ChordNode(InetAddress host, int port) {
        this.host = host;
        this.port = port;
        this.shardid = Shard.inetAddressToShardId(host);
        fingerTable = new ChordNode[NUM_FINGERS];
    }
    
    /** Successor is first entry in the fingerTable */
    public ChordNode getSuccessor() {
        return fingerTable[0];
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
            fingerTable = new ChordNode[] { existingNode };
            predecessor = existingNode;
        } else {
            logger.info("Joining existing ring, querying node: "+existingNode.host+" shardid="+existingNode.shardIdAsHex());
            // TODO: invoke remote procedure call here
            initFingerTable(existingNode);
            // TODO: updateOthers()
        }
        return true;
    }
    
    /** Initialize finger table of local node.
     *  existingNode is an arbitrary node already on the network
     */
    public boolean initFingerTable(ChordNode existingNode) {
        // TODO: this seems wrong, since finger table is not yet initialized for new node which is just joining...
        // fingerTable[0] = existingNode.findSuccessor(fingerTable[0].shardid);
        // temporary workaround:
        fingerTable[0] = existingNode.findSuccessor(shardid);
        predecessor = getSuccessor().predecessor;
        getSuccessor().predecessor = this;
        logger.info("InitFingerTable, predecessor= "+predecessor.host+" shardid="+predecessor.shardIdAsHex()+
                "\ncurrent="+host+" shardid="+shardIdAsHex()+
                "\nsuccessor="+getSuccessor().host+" shardid="+getSuccessor().shardIdAsHex());
        // TODO: for i=1 to m-1: update fingers
        //for (int index=0; index < NUM_FINGERS; index++) {
        //}
        return true;
    }
    
    public ChordNode findSuccessor(int identifier) {
        ChordNode next = findPredecessor(identifier);
        return next.getSuccessor();
    }
    
    /** Contact a series of nodes moving forward around the Chord circle
     *  towards the identifier
     */
    public ChordNode findPredecessor(int identifier) {
        ChordNode next = this;
        logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+next.shardIdAsHex());
        while (!Util.withinInterval(identifier, next.shardid, next.getSuccessor().shardid)) {
            next = next.closestPrecedingFinger(identifier);
            logger.info("FindPredecessor for id="+Integer.toHexString(identifier)+" next_shardid="+next.shardIdAsHex());
        }
        return next;
        
    }
    
    /** Return closest preceding id */
    public ChordNode closestPrecedingFinger(int identifier) {
        // TODO: lookup in finger tree
        // for now, just return successor node
        return this.getSuccessor();
    }
    
    /** Convenience method for displaying shardid as a hex string */
    public String shardIdAsHex() {
        return Integer.toHexString(shardid);
    }
}
