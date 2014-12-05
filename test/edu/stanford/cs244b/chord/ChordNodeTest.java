package edu.stanford.cs244b.chord;

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Assert;
import org.junit.Test;

public class ChordNodeTest {

    @Test
    public void testJoinNew() throws UnknownHostException { 
        ChordNode newNode = new ChordNode(InetAddress.getByName("192.168.1.1"), 8080);
        newNode.join(newNode, true);
        Assert.assertEquals(newNode, newNode.predecessor);
        for (ChordNode finger : newNode.fingerTable) {
            Assert.assertEquals(newNode, finger);
        }
    }
    
    @Test
    public void testJoinExisting() throws UnknownHostException {
        // setup
        ChordNode node1 = new ChordNode(InetAddress.getByName("192.168.1.1"), 8080);
        node1.join(node1, true);
        ChordNode node2 = new ChordNode(InetAddress.getByName("192.168.1.2"), 8080);
        node2.join(node1, false);
        
        Assert.assertEquals(node2, node1.predecessor);
        Assert.assertEquals(node1, node2.predecessor);
        for (int index=0; index < ChordNode.NUM_FINGERS; index++) {
            Assert.assertEquals(node2, node1.fingerTable[index]);
            Assert.assertEquals(node1, node2.fingerTable[index]);
        }
    }
    
    @Test
    public void testJoinExisting2() throws UnknownHostException {
        // setup
        ChordNode node1 = new ChordNode(InetAddress.getByName("192.168.1.1"), 8080);
        node1.join(node1, true);  // node1 shardid=71d92ab1
        ChordNode node2 = new ChordNode(InetAddress.getByName("192.168.1.2"), 8080);
        node2.join(node1, false); // node2 shardid=1010a462
        ChordNode node3 = new ChordNode(InetAddress.getByName("192.168.1.3"), 8080);
        node3.join(node1, false); // node3 shardid=ae481e13
        // node2(1010a462) => node1(71d92ab1) => node3(ae481e13) => node2
        
        Assert.assertEquals(node2, node1.predecessor);
        Assert.assertEquals(node3, node2.predecessor);
        Assert.assertEquals(node1, node3.predecessor);
        
        Assert.assertEquals(node1, node2.fingerTable[0]);
        Assert.assertEquals(node3, node1.fingerTable[0]);
        Assert.assertEquals(node2, node3.fingerTable[0]);
        
        if (ChordNode.NUM_FINGERS > 31) {
            Assert.assertEquals(node3, node2.fingerTable[31]);
            Assert.assertEquals(node2, node1.fingerTable[31]);
            Assert.assertEquals(node1, node3.fingerTable[31]);
        }
    }
    
    @Test
    public void testJoinExisting3() throws UnknownHostException {
        // setup
        ChordNode node1 = new ChordNode(InetAddress.getByName("192.168.1.1"), 8080);
        node1.join(node1, true);  // node1 shardid=71d92ab1
        ChordNode node2 = new ChordNode(InetAddress.getByName("192.168.1.2"), 8080);
        node2.join(node1, false); // node2 shardid=1010a462
        ChordNode node3 = new ChordNode(InetAddress.getByName("192.168.1.3"), 8080);
        node3.join(node1, false); // node3 shardid=ae481e13
        ChordNode node4 = new ChordNode(InetAddress.getByName("192.168.1.4"), 8080);
        node4.join(node1, false); // node4 shardid=4c7f97c4
        ChordNode node5 = new ChordNode(InetAddress.getByName("192.168.1.5"), 8080);
        node5.join(node1, false); // node5 shardid=eab71175
        
        // node2(1010a462) => node1(71d92ab1) => node3(ae481e13) => node2
        
        Assert.assertEquals(node2, node1.predecessor);
        Assert.assertEquals(node3, node2.predecessor);
        Assert.assertEquals(node1, node3.predecessor);
        
        Assert.assertEquals(node1, node2.fingerTable[0]);
        Assert.assertEquals(node3, node1.fingerTable[0]);
        Assert.assertEquals(node2, node3.fingerTable[0]);
        
        if (ChordNode.NUM_FINGERS > 31) {
            Assert.assertEquals(node3, node2.fingerTable[31]);
            Assert.assertEquals(node2, node1.fingerTable[31]);
            Assert.assertEquals(node1, node3.fingerTable[31]);
        }
    }

    /*@Test
    public void testClosestPrecedingFinger() {
        fail("Not yet implemented");
    }*/

}
