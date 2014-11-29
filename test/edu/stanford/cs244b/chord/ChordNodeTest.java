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
        // stabilize()
        
        Assert.assertEquals(node2, node1.predecessor);
        // the following fails because we haven't implemented stabilization yet...
        //Assert.assertEquals(node2, node1.fingerTable[0]);
        Assert.assertEquals(node1, node2.predecessor);
        Assert.assertEquals(node1, node2.fingerTable[0]);
    }

    /*@Test
    public void testClosestPrecedingFinger() {
        fail("Not yet implemented");
    }*/

}
