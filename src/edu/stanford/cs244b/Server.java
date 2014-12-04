package edu.stanford.cs244b;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.federecio.dropwizard.swagger.SwaggerDropwizard;

/** Main server class - the entry-point into our system
 *  Define configuration and any initialization steps here. */
public class Server extends Application<ChordConfiguration> {
    
    
    private final SwaggerDropwizard swaggerDropwizard = new SwaggerDropwizard();
    
    public static void main(String[] args) throws Exception {
        new Server().run(args);
    }

    @Override
    public void initialize(Bootstrap<ChordConfiguration> bootstrap) {
        swaggerDropwizard.onInitialize(bootstrap);
    }

    @Override
    public void run(ChordConfiguration configuration,
                    Environment environment) throws UnknownHostException, NoSuchAlgorithmException {
        // TODO: each shard should be uniquely identified/numbered...
        final Shard shard = new Shard(configuration.getChord());
        // register the shard endpoint
        environment.jersey().register(shard);
        
        // TODO: automatically discover shards from other servers?
        
        // add api documentation ui
        swaggerDropwizard.onRun(configuration, environment, "localhost");
    }

}
