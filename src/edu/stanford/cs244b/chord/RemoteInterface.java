package edu.stanford.cs244b.chord;

import java.io.InputStream;
import java.rmi.Remote;
import java.rmi.RemoteException;

import javax.ws.rs.core.Response;

public interface RemoteInterface extends Remote {
	public String forwardRequest(final InputStream uploadInputStream) throws RemoteException;
	public Response getRemoteItem(String sha256hash) throws RemoteException;
}
