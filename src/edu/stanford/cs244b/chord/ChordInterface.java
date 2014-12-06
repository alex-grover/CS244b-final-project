package edu.stanford.cs244b.chord;

import java.io.InputStream;
import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.ws.rs.core.Response;

public interface ChordInterface extends Remote {
	public ChordNode getSuccessor() throws RemoteException;
	public ChordNode findSuccessor(int identifier) throws RemoteException;
	public ChordNode findPredecessor(int identifier) throws RemoteException;
	public ChordNode closestPrecedingFinger(int identifier) throws RemoteException;
	public boolean join(ChordNode existingNode, boolean isFirstNode) throws RemoteException;
	public boolean initFingerTable(ChordNode existingNode) throws RemoteException;
	public void updateOthers() throws RemoteException;
	public boolean updateFingerTable(ChordNode s, int index) throws RemoteException;
	public boolean leave() throws RemoteException;
	public boolean removeNode(ChordNode node, int index, ChordNode replacement) throws RemoteException;
//	public String forward(final InputStream uploadInputStream) throws RemoteException;
//	public Response lookup(String sha256hash) throws RemoteException;
}
