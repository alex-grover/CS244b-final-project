package edu.stanford.cs244b.chord;

import java.io.Serializable;
import java.net.InetAddress;

import com.fasterxml.jackson.annotation.JsonIgnore;

import edu.stanford.cs244b.Shard;

@SuppressWarnings("serial")
public class Finger implements Serializable {
    public InetAddress host;
    public int port;
    @JsonIgnore
    public int shardid;

    public Finger(InetAddress host, int port) {
        this.host = host;
        this.port = port;
        this.shardid = Shard.inetAddressToShardId(host);
    }
    
    @JsonIgnore
    public String getRMIUrl() {
        return "rmi://"+host.getHostAddress()+":"+port+"/"+ChordNode.class.getCanonicalName();
    }
    
    @Override
    public String toString() {
        return "shardid="+Integer.toHexString(shardid)+" @"+host+":"+port;
    }
    
    public String getShardId() {
        return Integer.toHexString(shardid);
    }
}