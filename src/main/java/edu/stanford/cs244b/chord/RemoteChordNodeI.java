package edu.stanford.cs244b.chord;

import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteChordNodeI extends Remote {

    /** Return the location (ip address, port) of this ChordNode */
    Finger getLocation() throws RemoteException;
    
    /** Return the identifier of this ChordNode */
    int getShardId() throws RemoteException;
    
    /** Return ip address associated with this ChordNode */
    InetAddress getHost() throws RemoteException;
    
    /** Successor is first entry in the fingerTable */
    Finger getSuccessor() throws RemoteException;
    
    /** Obtain reference to predecessor */
    Finger getPredecessor() throws RemoteException;

    /** Set reference to predecessor */
    void setPredecessor(Finger newPredecessor) throws RemoteException;
    
    /** Notify node of request to become predecessor */
    void notifyPredecessor(Finger newPredecessor) throws RemoteException;
    
    /** Ask node to find the successor of the specified identifier */
    public abstract RemoteChordNodeI findSuccessor(int identifier) throws RemoteException;

    /** Contact a series of nodes moving forward around the Chord circle
     *  towards the identifier
     */
    public abstract RemoteChordNodeI findPredecessor(int identifier) throws RemoteException;

    /** Return closest preceding id */
    public abstract RemoteChordNodeI closestPrecedingFinger(int identifier) throws RemoteException;
    
    /** Remove node from finger table */
    public abstract void removeNode(ChordNode node, int index, Finger replacement) throws RemoteException;
    
    /** Receive forwarded save request from another node */
    public void saveFile(String inputString) throws RemoteException;
    
    /** Look up file located on this server */
    public String getFile(String hash) throws RemoteException;
    
    /** Return finger table */
    public Finger[] getFingerTable() throws RemoteException;
    
    /** Save file stored on previous server */
    public void saveReplicatedFile(String inputString, int nodesLeft) throws RemoteException;
}