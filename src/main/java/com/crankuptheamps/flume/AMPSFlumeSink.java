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
// information of 60East Technologies Inc.; (b) was not developed with
// government funds; (c) is a trade secret of 60East Technologies Inc.
// for all purposes of the Freedom of Information Act; and (d) is a
// commercial item and thus, pursuant to Section 12.212 of the Federal
// Acquisition Regulations (FAR) and DFAR Supplement Section 227.7202,
// Government's use, duplication or disclosure of the computer software
// is subject to the restrictions set forth by 60East Technologies Inc..
//
////////////////////////////////////////////////////////////////////////////

package com.crankuptheamps.flume;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.FlumeException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.lifecycle.LifecycleState;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.ConnectionStateListener;
import com.crankuptheamps.client.FailedWriteHandler;
import com.crankuptheamps.client.Message;
import com.crankuptheamps.client.exception.DisconnectedException;
import com.crankuptheamps.client.fields.ReasonField;
import com.crankuptheamps.client.util.SerializableFunction;

import static com.crankuptheamps.flume.Constants.*;

/**
 * A custom Flume sink used to stream messages from a Flume channel, into
 * an AMPS server via publishing them using an AMPS Java client.
 * 
 * @author Keith Caceres
 */
public class AMPSFlumeSink extends AbstractSink implements Configurable {
    /**
     * The logger for this class.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(
            AMPSFlumeSource.class);

    /**
     * The AMPS client instance.
     */
    protected Client client;

    /**
     * The Flume configuration context set by configure().
     */
    protected Context context;

    /**
     * The default configured AMPS topic that events should be published to.
     */
    protected String topic;

    /**
     * A binary array of bytes representing the default configured topic.
     */
    protected byte[] topicBytes;

    /**
     * The maximum number of events to read from the channel for a transaction.
     */
    protected int batchSize;

    /**
     * Indicates whether we should publish events to the topic specified in
     * their topic header, instead of the sink's default configured topic.
     */
    protected boolean useTopicHeader;

    /**
     * Timeout we should use for publish flush at the end of the batch.
     * A -1 indicates not to publish flush at end of batch.
     */
    protected long publishFlushTimeout;

    /**
     * Should we log the message to the Flume log when notified that it's a
     * failed write?
     */
    protected boolean logMsgOnWriteError;

    /**
     * Should we rollback the transaction batch if there are any failed writes
     * detected or exceptions caught?
     */
    protected boolean rollbackOnError;

    /**
     * Cache of string topic names mapped to their binary byte array
     * representation used for publishing to AMPS. This is only used when
     * useTopicHeader is true. It caches all the topics we've seen so far.
     */
    protected Map<String, byte[]> topicCache;

    /**
     * Reset to false at the beginning of a batch and set to true by the failed
     * write handler if a failed write is detected.
     */
    protected volatile boolean batchError;

    /**
     * Indicates whether our AMPS client is current connected or disconnected,
     * as reported by our connection state listener.
     */
    protected volatile boolean connected;

    /**
     * Indicates whether our AMPS client is shutdown,
     * as reported by our connection state listener.
     */
    protected volatile boolean shutdown;

    /**
     * Failed write handler we register on the client to detect publishing
     * errors during the batch.
     */
    public class SinkFailedWriteHandler implements FailedWriteHandler {
        @Override
        public void failedWrite(Message message, int reason) {
            batchError = true;
            String msg;
            if (logMsgOnWriteError) {
                msg = message.toString();
            } else {
                msg = "{msg seq " + message.getSequence() + "}";
            }
            LOGGER.error(String.format("FAILED WRITE: reason = %s, msg = %s",
                    ReasonField.encodeReason(reason), msg));
        }
    }

    /**
     * Connection state listener we register on the client to detect disconnects
     * and reconnects.
     */
    public class SinkConnectionListener implements ConnectionStateListener {
        @Override
        public void connectionStateChanged(int newState) {
            String state = "UNKNOWN: " + newState;
            switch (newState)
            {
                case ConnectionStateListener.Connected:
                    state = "Connected";
                    shutdown = false;
                    break;
                case ConnectionStateListener.LoggedOn:
                    state = "LoggedOn";
                    break;
                case ConnectionStateListener.PublishReplayed:
                    state = "PublishReplayed";
                    break;
                case ConnectionStateListener.HeartbeatInitiated:
                    state = "HeartbeatInitiated";
                    break;
                case ConnectionStateListener.Resubscribed:
                    state = "Resubscribed";
                    connected = true;
                    break;
                case ConnectionStateListener.Disconnected:
                    state = "Disconnected";
                    connected = false;
                    break;
                case ConnectionStateListener.Shutdown:
                    state = "Shutdown";
                    connected = false;
                    shutdown = true;
                    break;
            }
            LOGGER.debug("SinkConnectionListener: connection state changed: "
                    + state);
        }
    }

    @Override
    public synchronized void configure(Context ctx) {
        LOGGER.debug("Entering doConfigure() --------------------------------");
        context = ctx;
        LOGGER.debug("Leaving doConfigure() ---------------------------------");
    }

    @Override
    public synchronized void start() {
    LOGGER.debug("Entering start() ------------------------------------");
        try {
            // Create the client factory class, which must implement
            // SerializableFunction<Properties, Client>.
            String clientFactoryClass = context.getString(CLIENT_FACTORY,
                    "com.crankuptheamps.flume.AMPSBasicClientFunction");
            Class factoryClass = Class.forName(clientFactoryClass);
            @SuppressWarnings("unchecked")
            SerializableFunction<Properties, Client> clientFactory =
                    (SerializableFunction<Properties, Client>)
                    factoryClass.newInstance();

            // Put all the config parameters in a Properties object so that
            // a customer's client factory implementation doesn't need to depend
            // on Flume. This way it can be packaged with their custom AMPS
            // client build without pulling in Flume dependencies.
            Properties cfg = new Properties();
            cfg.putAll(context.getParameters());

            // Create the AMPS client.
            client = clientFactory.apply(cfg);

            // Set failed write handler.
            client.setFailedWriteHandler(new SinkFailedWriteHandler());

            // Set connection state listener.
            client.addConnectionStateListener(new SinkConnectionListener());

            // Get the default topic we publish to.
            topic = context.getString(TOPIC);
            topicBytes = topic.getBytes();

            // Get the batch size we'll write before publish flushing and
            // committing the transaction.
            batchSize = context.getInteger(MAX_BATCH, 1000);

            // Should we publish events to the topic specified in their topic
            // header, or always use the sink's default configured topic?
            useTopicHeader = context.getBoolean(USE_TOPIC_HEADER, true);
            if (useTopicHeader) {
                topicCache = new HashMap<>();
                topicCache.put(topic, topicBytes);
            }

            // Timeout we should use for publish flush at the end of the batch.
            // A -1 indicates not to publish flush at end of batch.
            publishFlushTimeout = context.getLong(PUBLISH_FLUSH_TIMEOUT, 0L);

            // Should we log the message to the Flume log when notified that
            // it's a failed write?
            logMsgOnWriteError = context.getBoolean(LOG_MSG_ON_WRITE_ERROR, true);

            // Should we rollback the transaction if there are any errors?
            rollbackOnError = context.getBoolean(ROLLBACK_ON_ERROR, false);

            super.start();
        } catch (Exception e) {
            throw new FlumeException(
                    "AMPS Flume Sink failed to start due to: " + e, e);
        } finally {
            LOGGER.debug("Leaving start() ---------------------------------");
        }
    }

    @Override
    public synchronized void stop() {
        LOGGER.debug("Entering stop() -------------------------------------");
        try {
            super.stop();
            topicCache = null;
            if (client != null) {
                client.close();
                client = null;
            }
        } catch (Exception e) {
            throw new FlumeException(
                    "AMPS Flume Sink failed to stop due to: " + e, e);
        } finally {
            LOGGER.debug("Leaving stop() ----------------------------------");
        }
    }

    @Override
    public Status process() throws EventDeliveryException {
        LOGGER.debug("Entering process() ----------------------------------");
        batchError = false;
        Channel ch = this.getChannel();
        Transaction tx = ch.getTransaction();
        Status status = Status.READY;

        try {
            tx.begin();

            for (int i = 0; i < batchSize; i++) {
                // Grab an event from the channel.
                Event evt = ch.take();
                if (evt == null) {
                    if (i == 0) status = Status.BACKOFF;
                    break;
                }

                // Get the topic.
                String tpc = topic;
                byte[] tpcBytes = topicBytes;
                if (useTopicHeader) {
                    Map<String,String> headers = evt.getHeaders();
                    if (headers != null && headers.containsKey(TOPIC_HEADER)) {
                        tpc = headers.get(TOPIC_HEADER);
                        tpcBytes = topicCache.get(tpc);
                        if (tpcBytes == null) {
                            tpcBytes = tpc.getBytes();
                            topicCache.put(tpc, tpcBytes);
                        }
                    }
                }

                // Publish the event as an AMPS message.
                try {
                    client.publish(tpcBytes, 0, tpcBytes.length,
                            evt.getBody(), 0, evt.getBody().length);
                } catch (DisconnectedException e) {
                    // Wait until we are reconnected to continue publishing,
                    // unless client is shutdown.
                    while (!connected && !shutdown) {
                        Thread.sleep(1000L);
                    }
                    if (shutdown) {
                        // Rethrow
                        throw e;
                    }
                }
            }

            // Wait for the batch to flush to AMPS if specified by config.
            if (publishFlushTimeout != -1L) {
                client.publishFlush(publishFlushTimeout);
            }
            if (rollbackOnError && batchError) {
                tx.rollback();
            } else {
                tx.commit();
            }
        } catch (Exception e) {
            LOGGER.error("An error occurred while processing batch: " + e, e);
            if (rollbackOnError) {
                tx.rollback();
            } else {
                tx.commit();
            }
        } finally {
            tx.close();
        }

        LOGGER.debug("Leaving process() -------------------------------");

        return status;
    }

}
