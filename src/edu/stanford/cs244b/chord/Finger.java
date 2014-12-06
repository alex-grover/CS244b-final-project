package edu.stanford.cs244b.chord;

import java.net.InetAddress;

import edu.stanford.cs244b.Shard;

public class Finger {
    public InetAddress host;
    public int port;
    public int shardid;

    public Finger(InetAddress host, int port) {
        this.host = host;
        this.port = port;
        this.shardid = Shard.inetAddressToShardId(host);
    }
    
    @Override
    public String toString() {
        return "shardid="+Integer.toHexString(shardid)+" @"+host+":"+port;
    }
}