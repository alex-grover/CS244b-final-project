package edu.stanford.cs244b.chord;

import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.ws.rs.core.Response;

public interface ChordInterface extends Remote {
	public ChordInterface getSuccessor() throws RemoteException;
	public ChordInterface findSuccessor(int identifier) throws RemoteException;
	public ChordInterface findPredecessor(int identifier) throws RemoteException;
	public ChordInterface closestPrecedingFinger(int identifier) throws RemoteException;
	public boolean join(ChordInterface existingNode, boolean isFirstNode) throws RemoteException;
	public boolean initFingerTable(ChordInterface existingNode) throws RemoteException;
	public void updateOthers() throws RemoteException;
	public boolean updateFingerTable(ChordInterface s, int index) throws RemoteException;
	public void leave() throws RemoteException;
	public void removeNode(ChordInterface node, int index, ChordInterface replacement) throws RemoteException;
	
	public InetAddress getHost() throws RemoteException;
	public int getShardId() throws RemoteException;
	public ChordInterface getPredecessor() throws RemoteException;
	public void setPredecessor(ChordInterface predecessor) throws RemoteException;
	
//	public String forward(final InputStream uploadInputStream) throws RemoteException;
//	public Response lookup(String sha256hash) throws RemoteException;
}
