package edu.stanford.cs244b.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

/**
 * {@link InputStream} that builds a MAC by observing bytes as they pass through another stream.
 * <p/>
 * This implementation does not support mark/reset or seek, as all bytes of the request must be processed in order.
 * 
 * Derived from implementation by Drew Noakes <http://drewnoakes.com> at
 * https://stackoverflow.com/questions/14837886/calculate-a-hmac-from-an-http-post-without-copying-entire-request-body-into-memo
 * 
 * Modified by Arpad Kovacs <akovacs@stanford.edu>
 */
public class HMACInputStream extends InputStream {

    private final InputStream _inputStream;
    private final Mac _mac;

    public HMACInputStream(InputStream inputStream, SecretKeySpec secretKey) throws NoSuchAlgorithmException, InvalidKeyException, NoSuchProviderException {
        _inputStream = inputStream;
        _mac = Mac.getInstance("Hmac-SHA256", BouncyCastleProvider.PROVIDER_NAME);
        _mac.init(secretKey);
    }

    /**
     * Calculates whether the built-up HMAC matches the provided one (commonly from an HTTP request header.)
     * Should only be called once when the stream has been consumed.
     */
    public byte[] getDigest() {
        return _mac.doFinal();
    }

    // Override default InputStream methods below
    
    @Override
    public int read() throws IOException {
        int i = _inputStream.read();
        if (i != -1)
            _mac.update((byte)i);
        return i;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int i = _inputStream.read(b);
        if (i != -1)
            _mac.update(b, 0, i);
        return i;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int i = _inputStream.read(b, off, len);
        if (i != -1)
            _mac.update(b, off, i);
        return i;
    }

    @Override
    public long skip(long n) throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public int available() throws IOException {
        return _inputStream.available();
    }

    @Override
    public void close() throws IOException {
        _inputStream.close();
    }

    @Override
    public void mark(int readlimit) {}

    @Override
    public void reset() throws IOException {
        throw new IOException("Not supported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }
}