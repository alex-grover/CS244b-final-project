package edu.stanford.cs244b.chord;

import java.io.Serializable;
import java.net.InetAddress;

import edu.stanford.cs244b.Shard;

public class Finger implements Serializable {
    public InetAddress host;
    public int port;
    public int shardid;

    public Finger(InetAddress host, int port) {
        this.host = host;
        this.port = port;
        this.shardid = Shard.inetAddressToShardId(host);
    }
    
    public String getRMIUrl() {
        return "rmi://"+host.getHostAddress()+":"+port;
    }
    
    @Override
    public String toString() {
        return "shardid="+Integer.toHexString(shardid)+" @"+host+":"+port;
    }
}