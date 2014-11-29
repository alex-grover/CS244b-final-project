package edu.stanford.cs244b.chord;

import java.net.InetAddress;

import edu.stanford.cs244b.Shard;

/** Core components of the Chord distributed hash table implementation.
 *  Keeps track of other shards in the ring to ensure O(log n) lookup */
public class FingerTable {
    /** Pointer to immediate predecessor, can be used to walk
     *  counterclockwise around the identifier circle */
    public FingerTableEntry predecessor;
    
    /** In initial Chord implementation, only maintain a pointer to
     *  the direct successor for simplicity.
     *  TODO: keep pointer to all log(n) nodes as required by Chord. */
    public FingerTableEntry[] fingerTable;
    
    public FingerTableEntry getSuccessor() {
        return fingerTable[0];
    }
    
    // TODO: maybe this should be FingerTable constructor...
    /** When node <i>n</i> joins the network:
     *  <ol>
     *  <li>Initialize predecessor TODO: and fingers of node <i>n</i></li>
     *  <li>TODO: Update the fingers and predecessors of existing nodes to reflect the addition of node <i>n</i></li>
     *  <li>TODO: Notify higher-level software so it can transfer values associated with keys that node <i>n</i> is now responsible for</li>
     *  </ol>
     *  Returns true if join succeeded, false otherwise
     */
    public boolean join(InetAddress existingNode, int port, boolean isFirstNode) {
        if (isFirstNode) {
            // TODO: initialize full finger table; for now we only keep track of successor
            fingerTable = new FingerTableEntry[] { new FingerTableEntry(existingNode, port)};
            predecessor = new FingerTableEntry(existingNode, port);
        } else {
            initFingerTable(existingNode, port);
            // TODO: updateOthers()
        }
        return true;
    }
    
    /** Initialize finger table of local node.
     *  existingNode is an arbitrary node already on the network
     */
    public boolean initFingerTable(InetAddress existingNode, int port) {
        fingerTable[0] = existingNode.findSuccessor(fingerTable[0].shardid);
        predecessor = getSuccessor().predecessor;
        getSuccessor().predecessor = this;
        // TODO: for i=1 to m-1: update fingers
        return true;
    }
    
    public FingerTableEntry findSuccessor(int identifier) {
        FingerTableEntry np = findPredecessor(identifier);
        return np.getSuccessor();
    }
    

    public class FingerTableEntry {
        protected InetAddress host;
        protected int port;
        protected int shardid;
        
        public FingerTableEntry(InetAddress host, int port) {
            this.host = host;
            this.port = port;
            this.shardid = Shard.inetAddressToShardId(host);
        }
    }
}
