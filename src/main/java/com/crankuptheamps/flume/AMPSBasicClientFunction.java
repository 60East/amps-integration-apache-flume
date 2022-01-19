////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2017-2022 60East Technologies Inc., All Rights Reserved.
//
// This computer software is owned by 60East Technologies Inc. and is
// protected by U.S. copyright laws and other laws and by international
// treaties.  This computer software is furnished by 60East Technologies
// Inc. pursuant to a written license agreement and may be used, copied,
// transmitted, and stored only in accordance with the terms of such
// license agreement and with the inclusion of the above copyright notice.
// This computer software or any other copies thereof may not be provided
// or otherwise made available to any other person.
//
// U.S. Government Restricted Rights.  This computer software: (a) was
// developed at private expense and is in all respects the proprietary
// information of 60East Technologies Inc; (b) was not developed with
// government funds; (c) is a trade secret of 60East Technologies Inc.
// for all purposes of the Freedom of Information Act; and (d) is a
// commercial item and thus, pursuant to Section 12.212 of the Federal
// Acquisition Regulations (FAR) and DFAR Supplement Section 227.7202,
// Government's use, duplication or disclosure of the computer software
// is subject to the restrictions set forth by 60East Technologies Inc.
//
////////////////////////////////////////////////////////////////////////////

package com.crankuptheamps.flume;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.DefaultServerChooser;
import com.crankuptheamps.client.HAClient;
import com.crankuptheamps.client.LoggedBookmarkStore;
import com.crankuptheamps.client.MemoryPublishStore;
import com.crankuptheamps.client.PublishStore;
import com.crankuptheamps.client.util.SerializableFunction;

import static com.crankuptheamps.flume.Constants.*;

import java.beans.ExceptionListener;
import java.util.Properties;

/**
 * Simple implementation of the {@link SerializableFunction<Properties, Client>}
 * client factory interface from the AMPS Client JAR. This is implemented
 * to return either an AMPS Client or AMPS HAClient based upon parameters
 * in the Flume config Properties. More advanced implementations could setup
 * an HAClient with custom server chooser and authenticator implementations. 
 * 
 * @author Keith Caceres
 */
public class AMPSBasicClientFunction
        implements SerializableFunction<Properties, Client> {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(AMPSBasicClientFunction.class);

    /**
     * Executes this function using the passed in configuration
     * {@link Properties}, to construct and return an AMPS client for use by
     * the Flume source.
     * 
     * @param config The Properties object that holds the client's
     *        configuration as a set of key/value pairs.
     * 
     * @return The newly created AMPS client.
     */
    @Override
    public Client apply(Properties config) {
        boolean useHA = true; // Always use HA client for now.
        DefaultServerChooser svrChooser = null;
        Client client = null;
        
        try {
            // Calculate client name.
            String clientName = config.getProperty(CLIENT_NAME);
            
            // Get server URI(s). Determine if we need an HA client.
            int idx = 1;
            String serverURI = config.getProperty(URI + idx++);
            if (serverURI != null && !serverURI.isEmpty()) {
                useHA = true;
                svrChooser = new DefaultServerChooser();
                svrChooser.add(serverURI);
                do {
                    serverURI = config.getProperty(URI + idx++);
                    if (serverURI != null && !serverURI.isEmpty()) {
                        svrChooser.add(serverURI);
                    }
                } while (serverURI != null);
                idx -= 2; // Subtract to get the server URI count.
            } else {
                idx = 1;
                serverURI = config.getProperty(URI);
                svrChooser = new DefaultServerChooser();
                svrChooser.add(serverURI);
            }

            // Create client.
            if (useHA) {
                client = new HAClient(clientName);
                ((HAClient) client).setServerChooser(svrChooser);
            } else {
                client = new Client(clientName);
            }

            // Add logging exception listener to client.
            client.setExceptionListener(new ExceptionListener() {
                @Override
                public void exceptionThrown(Exception e) {
                    LOGGER.error("AMPS client received error: " + e, e);
                }
            });

            // Setup bookmark store if needed.
            String storePath = config.getProperty(BOOKMARK_LOG);
            String subIdStr = config.getProperty(SUB_ID);
            if (storePath != null && subIdStr == null)
                throw new IllegalArgumentException("Subscription ID must be set"
                        + "when using a bookmark subscription.");
            if (storePath != null) {
                client.setBookmarkStore(new LoggedBookmarkStore(storePath));
            }

            // Setup publish store if needed.
            String pubStoreType = config.getProperty(PUB_STORE_TYPE, "none");
            if (!"none".equals(pubStoreType)) {
                int initialCapacity = Integer.valueOf(
                        config.getProperty(PUB_STORE_INIT_CAP, "1000"));
                if ("memory".equals(pubStoreType)) {
                    client.setPublishStore(
                            new MemoryPublishStore(initialCapacity));
                } else if ("file".equals(pubStoreType)) {
                    String path = config.getProperty(PUB_STORE_PATH);
                    if (path == null || path.isEmpty()) {
                        throw new IllegalArgumentException(PUB_STORE_PATH +
                                " must be specified for a file-backed publish"
                                + " store.");
                    }
                    client.setPublishStore(
                            new PublishStore(path, initialCapacity));
                }
            }

            // Connect and logon to AMPS server.
            if (useHA) {
                ((HAClient) client).connectAndLogon();
                LOGGER.info("HAClient {} connected to {} server(s).",
                        clientName, idx);
            } else {
                client.connect(serverURI);
                client.logon();
                LOGGER.info("{} connected to {}.", clientName, serverURI);
            }

            return client;
        } catch (IllegalArgumentException e) {
            if (client != null) client.close();
            throw e;
        } catch (Exception e) {
            if (client != null) client.close();
            throw new RuntimeException("Failed to create AMPS client: " + e, e);
        }
    }
}
