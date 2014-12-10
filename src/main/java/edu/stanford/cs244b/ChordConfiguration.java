package edu.stanford.cs244b;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.bazaarvoice.dropwizard.assets.AssetsBundleConfiguration;
import com.bazaarvoice.dropwizard.assets.AssetsConfiguration;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.Configuration;

public class ChordConfiguration extends Configuration implements AssetsBundleConfiguration {
    @Valid
    @NotNull
    @JsonProperty
    private final AssetsConfiguration assets = new AssetsConfiguration();

    @Override
    public AssetsConfiguration getAssetsConfiguration() {
      return assets;
    }
    
    @Valid
    @NotNull
    private Chord chord = new Chord();
    
    public Chord getChord() {
        return chord;
    }

    public class Chord {
        /** IP address to assign to this node */
        @NotNull
        @JsonProperty
        private InetAddress myIP;
        
        /** Host which should be queried when joining the chord ring */
        @NotNull
        @JsonProperty
        private InetAddress entryHost;
        
        /** Port which should be queried when joining the chord ring */
        @Min(1)
        @Max(65535)
        @JsonProperty
        private int entryPort;
        
        /** Algorithm to use for generating identifiers for objects added to chord ring */
        @JsonProperty
        private String identifier;

        public InetAddress getMyIP() {
            return myIP;
        }
        
        public void setMyIP(String ipAddress) throws UnknownHostException {
            this.myIP = InetAddress.getByName(ipAddress);
        }
        
        public InetAddress getEntryHost() {
            return entryHost;
        }
        
        public void setEntryPoint(String entryHost) throws UnknownHostException {
            this.entryHost = InetAddress.getByName(entryHost);
        }
        
        public int getEntryPort() {
            return entryPort;
        }

        public void setEntryPort(int entryPort) {
            this.entryPort = entryPort;
        }

        public String getIdentifier() {
            return identifier;
        }

        public void setIdentifier(String identifier) {
            this.identifier = identifier;
        }    
    }
}
