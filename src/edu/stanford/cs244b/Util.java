package edu.stanford.cs244b;

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
}
