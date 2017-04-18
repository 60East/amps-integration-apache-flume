////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2017 60East Technologies Inc., All Rights Reserved.
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

import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.flume.ChannelException;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.FlumeException;
import org.apache.flume.source.AbstractPollableSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.crankuptheamps.client.BookmarkStore;
import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.Command;
import com.crankuptheamps.client.CommandId;
import com.crankuptheamps.client.LoggedBookmarkStore;
import com.crankuptheamps.client.Message;
import com.crankuptheamps.client.MessageHandler;
import com.crankuptheamps.client.RingBookmarkStore;
import com.crankuptheamps.client.fields.Field;
import com.crankuptheamps.client.util.SerializableFunction;

import static com.crankuptheamps.flume.Constants.*;

/**
 * A custom Flume source used to stream messages from an AMPS subscription
 * into one or more Flume channels.
 * 
 * @author Keith Caceres
 */
public class AMPSFlumeSource extends AbstractPollableSource {
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
     * The AMPS bookmark store instance.
     */
    protected BookmarkStore bStore = null;
    
    /**
     * Flag that indicates whether we are performing bookmark store discards
     * for a bookmark subscription.
     */
    protected boolean doDiscards;
    
    /**
     * Helper class of message buffers that the AMPS asynchronous
     * {@link MessageHandler} writes messages to for batching into Flume
     * channel transactions.
     */
    protected MessageMultiBuffer msgMultiBuf;
    
    /**
     * Helper class that implements {@link MessageHandler} for feeding
     * our asynchronous message stream into the {@link MessageMultiBuffer}.
     */
    protected FlumeSrcMsgHandler handler;
    
    /**
     * The commandId of the AMPS subscription.
     */
    protected CommandId cmdId;
    
    /**
     * The Flume configuration context set by doConfigure().
     */
    protected Context context;
    
    /**
     * The subscription ID used for discarding messages in the bookmark store.
     */
    protected Field subId;
    
    /**
     * The current batch of messages retrieved from the message multi-buffer.
     */
    protected List<Msg> messages = null;
    
    /**
     * A reusable List used to store the current batch of Flume events, created
     * from the current batch of AMPS messages.
     */
    protected List<Event> events = new ArrayList<Event>();
    
    /**
     * When the configured AMPS client is using a LoggedBookmarkStore,
     * the AMPS Flume source will prune it of old entries whenever
     * it is polled and it finds that at least this many milliseconds
     * have elapsed since the last prune operation or startup. The default is
     * 5 minutes (300,000 milliseconds). Pruning will also be done upon source
     * shutdown.
     * 
     * If this parameter is set to zero, both periodic and shutdown
     * pruning will be turned off. This may be useful for testing and debugging,
     * but is NOT recommended for a production deployment.
     */
    protected long pruneTimeMillis;
    
    /**
     * Timestamp of when this source last performed a prune operation, or
     * when it finished start-up if no prune operations have occurred.
     */
    protected long lastPruneMillis;

    /**
     * Reads the current batch of AMPS messages from the message multi-buffer,
     * converts them into Flume events, and commits them to the source's
     * channel processor. If an error occurs, the current batch is rolled-back
     * for a retry. If the commit succeeds, then the messages of the current
     * batch are discarded.
     * 
     * This method is where all the real work is done.
     * 
     * @return Returns a status indicating whether the Flume engine should
     *         poll this source more often (READY), or whether it should
     *         back-off -- calling it less often (BACKOFF).
     *         
     * @throws If any unhandled error occurs.
     */
    @Override
    protected Status doProcess() throws EventDeliveryException {
        LOGGER.debug("Entering doProcess() ----------------------------------");
        boolean error = true;
        messages = null;
        try {
            // Get a batch of messages.
            messages = msgMultiBuf.rotateBuffer();
            int size = messages.size();
            LOGGER.debug("Batch size: {}", size);
            
            // If its empty, indicate no error and tell Flume to backoff
            // the polling interval.
            if (size == 0) {
                error = false;
                LOGGER.debug("Returning with status BACKOFF.");
                return Status.BACKOFF;
            }
            
            // Give the batch to Flume.
            events.clear();
            events.addAll(messages);
            getChannelProcessor().processEventBatch(events);
            error = false;
            LOGGER.debug("Batch successfully processed and committed to "
                    + "channel. Returning with status READY.");
            
            // Indicate a READY to poll me status.
            return Status.READY;
        } catch (ChannelException e) {
            LOGGER.warn("Error adding event batch to channel. Channel may be "
                    + "full. Increase channel size or optimize sink "
                    + "performance.", e);
        } finally {
            // Determine whether to rollback or commit.
            if (error) {
                // "Rollback" by pushing message batch back on multi-buffer.
                if (messages != null)
                    msgMultiBuf.pushBack(messages);
                LOGGER.debug("Rolled-back batch for redelivery.");
            } else {
                // "Commit" by performing message discards.
                try {
                    if (doDiscards) {
                        for (Msg msg: messages) {
                            bStore.discard(subId, msg.bookmarkSeqNo);
                        }
                        LOGGER.debug("Committed batch by discarding messages.");
                    }

                    // Prune logged bookmark store if enough time has elapsed.
                    long currTime = System.currentTimeMillis();
                    if (bStore instanceof LoggedBookmarkStore
                            && pruneTimeMillis != 0L
                            && (currTime - lastPruneMillis) > pruneTimeMillis) {
                        ((LoggedBookmarkStore) bStore).prune();
                        lastPruneMillis = currTime;
                    }

                } catch (Exception e) {
                    throw new FlumeException("Message discards failed: " + e, e);
                }
            }
            
            // Return the buffer to the multi-buffer since we're done with it.
            msgMultiBuf.returnBuffer(messages);
            
            LOGGER.debug("Leaving doProcess() -------------------------------");
        }
        return Status.BACKOFF;
    }

    /**
     * Configures the AMPS source from the passed in Flume context. 
     */
    @Override
    protected void doConfigure(Context ctx) throws FlumeException {
        LOGGER.debug("Entering doConfigure() --------------------------------");
        context = ctx;
        LOGGER.debug("Leaving doConfigure() ---------------------------------");
    }

    /**
     * Based upon configuration from the Flume context, starts up an AMPS
     * Client and associated subscription.
     * 
     * @throws FlumeException If anything goes wrong during start-up.
     */
    @Override
    protected void doStart() throws FlumeException {
        LOGGER.debug("Entering doStart() ------------------------------------");
        try {
            // Set the pruning threshold.
            pruneTimeMillis = context.getLong(
                    PRUNE_TIME_THRESHOLD, 5 * 60 * 1000L);
            
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
            
            // Get the client's bookmark store.
            bStore = client.getBookmarkStore();
            
            String cmdType = context.getString(COMMAND, "subscribe");
            String topic = context.getString(TOPIC);
            String filter = context.getString(FILTER);
            String options = context.getString(OPTIONS);
            String bookmarkLog = context.getString(BOOKMARK_LOG);
            String subId = context.getString(SUB_ID);
            int maxBuffs = context.getInteger(MAX_BUFFS, 10);
            int maxBatch = context.getInteger(MAX_BATCH, 1000);
            if (bookmarkLog != null && !bookmarkLog.isEmpty()
                    && (subId == null || subId.isEmpty()))
                throw new IllegalArgumentException("Subscription ID must be set"
                        + "when using a bookmark subscription.");
            doDiscards = bookmarkLog != null && !bookmarkLog.isEmpty();
            
            if (subId != null && !subId.isEmpty()) {
                this.subId = new Field(subId);
            }
            LOGGER.debug("maxBuffs: {}", maxBuffs);
            LOGGER.debug("maxBatch: {}", maxBatch);
            msgMultiBuf = new MessageMultiBuffer(maxBuffs, maxBatch);
            handler = new FlumeSrcMsgHandler(msgMultiBuf);
            LOGGER.debug("command: {}", cmdType);
            LOGGER.debug("topic: {}", topic);
            Command command = new Command(cmdType).setTopic(topic);
            
            // If there is a filter, set it.
            if (filter != null && !filter.isEmpty()) {
                command.setFilter(filter);
                LOGGER.debug("filter: {}", filter);
            }
            
            // If there are command options, set them.
            if (options != null && !options.isEmpty()) {
                command.setOptions(options);
                LOGGER.debug("options: {}", options);
            }
            
            // If this is a bookmark subscription, set it.
            if (bookmarkLog != null && !bookmarkLog.isEmpty()) {
                LOGGER.debug("bookmarkLog: {}", bookmarkLog);
                LOGGER.debug("subId: {}", subId);
                command.setSubId(subId)
                    .setBookmark(Client.Bookmarks.MOST_RECENT);
            }
            
            // Execute the command.
            cmdId = client.executeAsync(command, handler);
        } catch (Exception e) {
            throw new FlumeException(
                    "AMPS Flume Source failed to start due to: " + e, e);
        } finally {
            lastPruneMillis = System.currentTimeMillis();
            LOGGER.debug("Leaving doStart() ---------------------------------");
        }
    }

    /**
     * Shuts-down the AMPS subscription and client.
     * 
     * @throws FlumeException If anything goes wrong during start-up.
     */
    @Override
    protected void doStop() throws FlumeException {
        LOGGER.debug("Entering doStop() -------------------------------------");
        try {
            if (client != null) {
                client.unsubscribe();
                client.close();
            }
            if (bStore instanceof LoggedBookmarkStore) {
                if (pruneTimeMillis > 0L)
                    ((LoggedBookmarkStore) bStore).prune();
                ((LoggedBookmarkStore) bStore).close();
            }
            if (bStore instanceof RingBookmarkStore) {
                ((RingBookmarkStore) bStore).close();
            }
        } catch (Exception e) {
            throw new FlumeException(
                    "AMPS Flume Source failed to stop due to: " + e, e);
        } finally {
            LOGGER.debug("Leaving doStop() ----------------------------------");
        }
    }

    /**
     * Helper class that implements {@link MessageHandler} for feeding
     * our asynchronous message stream into the {@link MessageMultiBuffer}.
     */
    protected class FlumeSrcMsgHandler implements MessageHandler {
        private MessageMultiBuffer msgMultiBuf;
        
        /**
         * Constructs a new Flume source message handler with the specified
         * message multi-buffer.
         * 
         * @param mmb The message multi-buffer to copy the asynchronous message
         *        stream into for Flume transaction batching.
         */
        public FlumeSrcMsgHandler(MessageMultiBuffer mmb) {
            msgMultiBuf = mmb;
        }

        /**
         * Invoked by AMPS Client background reader thread for asynchronous
         * message delivery.
         */
        @Override
        public void invoke(Message msg) {
            if (msg.getCommand() == Message.Command.Publish) {
                Msg m = new Msg();
                Field data = msg.getDataRaw();
                byte[] ba = new byte[data.length];
                System.arraycopy(data.buffer, data.position, ba, 0, data.length);
                m.data = ba;
                m.bookmarkSeqNo = msg.getBookmarkSeqNo();
                msgMultiBuf.append(m);
            }
        }
        
    }
    
    /**
     * A simple message class to copy the important parts of the AMPS message
     * to. This allows us to copy the minimal amount of data into the
     * MessageMultiBuffer so that this Flume source takes less memory and
     * doesn't waste time copying message fields it doesn't need.
     * 
     * This class also directly implements a very sparse version of the
     * Flume {@link Event} interface so that it can be directly committed to
     * Flume channels.
     */
    protected static class Msg implements Event {
        /**
         * The bookmark sequence number assigned to this message when it was
         * logged into the bookmark store. We'll need this later for discarding
         * the message.
         */
        public long bookmarkSeqNo;
        
        /**
         * The message body data as a byte array.
         */
        public byte[] data;

        /**
         * Gets the message body data as a byte array.
         */
        @Override
        public byte[] getBody() {
            return data;
        }

        /**
         * Overridden to return null. Headers aren't supported by this class.
         */
        @Override
        public Map<String, String> getHeaders() {
            return null;
        }

        /**
         * Sets the message body data as a byte array.
         */
        @Override
        public void setBody(byte[] ba) {
            data = ba;
            
        }
        
        /**
         * @throws UnsupportedOperationException Overridden to always throw
         *         this exception. Headers aren't supported by this class.
         */
        @Override
        public void setHeaders(Map<String, String> hdrs) {
            throw new UnsupportedOperationException("Headers not supported.");
        }
    }

    /**
     * Helper class of message buffers that the AMPS asynchronous
     * {@link MessageHandler} writes messages to for batching into Flume
     * channel transactions.
     */
    protected static class MessageMultiBuffer {
        private int maxBuffs;
        private int maxBatch;
        private List<List<Msg>> msgBuffers;
        private List<Msg> currBuffer;
        private List<Msg> rollbackBuffer;
        private Deque<List<Msg>> stack = new ArrayDeque<List<Msg>>();
        private boolean rolledback;
        private int readIndex;
        private int writeIndex;
        
        /**
         * Constructs an instance with a maximum number of message buffers
         * and maximum batch size.
         * 
         * @param bufs Max number of buffers.
         * @param batch Max batch size. This shouldn't be larger than your
         *        smallest configured transaction capacity for all Flume
         *        channels that this source writes to.
         */
        public MessageMultiBuffer(int bufs, int batch) {
            maxBuffs = bufs;
            maxBatch = batch;
            msgBuffers = new ArrayList<List<Msg>>();
            for (int i = 0; i < maxBuffs; i++) {
                msgBuffers.add(new LinkedList<Msg>());
            }
            rollbackBuffer = new LinkedList<Msg>();
            rolledback = false;
            readIndex = writeIndex = 0;
            currBuffer = msgBuffers.get(writeIndex);
        }
        
        /**
         * Called by the AMPS asynchronous message handler to write a message
         * into the multi-buffer.
         */
        public synchronized void append(Msg msg) {
            if (currBuffer.size() == maxBatch) {
                int newWriteIdx = writeIndex + 1;
                if (newWriteIdx == maxBuffs) newWriteIdx = 0;
                while (currBuffer.size() == maxBatch
                        && newWriteIdx == readIndex) {
                    LOGGER.warn("Pausing AMPS message processing to let Flume "
                            + "source polling catch-up. Consider increasing "
                            + "maxBuffers; or maxBatch and the transaction"
                            + "capacity of your Flume channel(s).");
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        LOGGER.info("Wait interrupted.", e);
                    }
                }
                writeIndex++;
                if (writeIndex == maxBuffs) writeIndex = 0;
                currBuffer = msgBuffers.get(writeIndex);
                currBuffer.clear();
            }
            currBuffer.add(msg);
        }
        
        /**
         * Gets the next available message batch as a message buffer and
         * advances the current read position to the next available buffer.
         * Buffers returned by this method become "owned" by the caller. When
         * the caller is done with them, {@link #returnBuffer(List)} should
         * be called to return them to the multi-buffer for reuse.
         */
        public synchronized List<Msg> rotateBuffer() {
            LOGGER.debug("Entering rotateBuffer().");
            LOGGER.debug("readIndex: {}", readIndex);
            LOGGER.debug("writeIndex: {}", writeIndex);
            LOGGER.debug("rolledback: {}", rolledback);
            try {
                if (rolledback && rollbackBuffer.size() > 0) {
                    return rollbackBuffer;
                }
                
                if (readIndex == writeIndex) {
                    currBuffer = getBuffer();
                    return msgBuffers.set(readIndex, currBuffer);
                }

                int oldReadIndex = readIndex++;
                if (readIndex == maxBuffs) readIndex = 0;

                return msgBuffers.set(oldReadIndex, getBuffer());
            } finally {
                rolledback = false;
                this.notifyAll();
                LOGGER.debug("Leaving rotateBuffer().");
            }
        }
        
        /**
         * When a Flume ChannelProcessor transaction error occurs, this allows
         * the source to push the failed batch back into the multi-buffer so
         * that it will be re-delivered upon the next call
         * {@link #rotateBuffer()}, thus allowing it to be retried.
         */
        public synchronized void pushBack(List<Msg> list) {
            if (list != rollbackBuffer) {
                rollbackBuffer.clear();
                rollbackBuffer.addAll(list);
            }
            rolledback = true;
        }
        
        /**
         * Allows a message buffer returned by {@link #rotateBuffer()} to be
         * returned to the multi-buffer for reuse. After calling this method
         * the caller should no longer perform any operations on the passed in
         * list.
         */
        public synchronized void returnBuffer(List<Msg> list) {
            list.clear();
            stack.push(list);
        }
        
        /**
         * Internal helper method that provides an empty message buffer for
         * reuse, or if one is not available, it constructs a new one.
         * 
         * NOTE: This method requires external synchronization on the
         * multi-buffer's monitor.
         */
        protected List<Msg> getBuffer() {
            if (!stack.isEmpty())
                return stack.pop();
            
            return new LinkedList<Msg>();
        }
    }
}
