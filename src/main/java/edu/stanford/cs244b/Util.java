package edu.stanford.cs244b;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Collection of various useful utility methods */
public class Util {    
    /** Take 32 most-significant bits of IP address
     *  (represented as bytes), and convert to integer id */
    public static int ipByteArrayToInt(byte[] bytes) {
        int val = 0;
        for (int i = 0; i < 4; i++) {
            val <<= 8;
            val |= bytes[i] & 0xff;
        }
        return val;
    }
    
    /** Turn integer into 4 byte array representation */
    public static byte[] intToByteArray(int bytes) {
        return new byte[] {
            (byte)((bytes >>> 24) & 0xff),
            (byte)((bytes >>> 16) & 0xff),
            (byte)((bytes >>>  8) & 0xff),
            (byte)((bytes       ) & 0xff)
        };
    }
    
    /** Determine whether the identifier falls within the specified interval (both start and
     *  end included) while taking wrapping into account, since identifiers are located on a ring *
     */
    public static boolean withinInterval(int identifier, int intervalStart, int intervalEnd) {
        if (intervalStart < intervalEnd) {
            // standard monotonically increasing case
            return ((identifier >= intervalStart) && (identifier <= intervalEnd));
        } else {
            // wrap around case, also handles special case for single node which encompasses full ring
            return ((identifier >= intervalStart) || (identifier <= intervalEnd));
        }
    }
    
    /** Redistribute integer identifier keyspace using Knuth's multiplicative method.
     *  Modified to use long internally since java does not have unsigned int.
     *  This performs one-to-one remapping from integer to integer to avoid collisions
     *  https://stackoverflow.com/questions/664014/what-integer-hash-function-are-good-that-accepts-an-integer-hash-key
     */
    public static int intHash(int input) {
        // convert to unsigned long
        long unsignedValue = ((long) input)-Integer.MIN_VALUE;
        long unsignedIntMax = (1l << 32);
        // Knuth's multiplicative method
        long unsignedHashValue = ((unsignedValue*2654435761l) % unsignedIntMax);
        // convert back to signed integer
        return (int) (unsignedHashValue+Integer.MIN_VALUE);
    }
    
    public static int hexStringToIdentifier(String hash) {
    	return Integer.parseInt(hash.substring(0, 4), 16);
    }
    
    public static String streamToString(InputStream stream) throws IOException {
    	StringBuilder inputStringBuilder = new StringBuilder();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        String line = bufferedReader.readLine();
        while (line != null) {
            inputStringBuilder.append(line);inputStringBuilder.append('\n');
            line = bufferedReader.readLine();
        }
        return inputStringBuilder.toString();
    }
    
    public static InputStream stringToStream(String inputString) {
    	return new ByteArrayInputStream(inputString.getBytes());
    }
}
