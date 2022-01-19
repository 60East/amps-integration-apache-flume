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

import com.crankuptheamps.client.Message;

/**
 * Configuration key constants used to pull configuration values from the
 * Flume context during start-up.
 *
 * @author Keith Caceres
 */
public interface Constants {
    /**
     * The name of the class to be used to construct the AMPS client from
     * the configuration. If not specified, then
     * com.crankuptheamps.flume.AMPSBasicClientFunction will be used.
     */
    String CLIENT_FACTORY = "clientFactoryClass";
    
    /**
     * Configuration key used to get the AMPS client name from the
     * Flume context.
     */
    String CLIENT_NAME = "clientName";
    
    /**
     * Configuration key used to get the bookmark log file path/name from the
     * Flume context.
     */
    String BOOKMARK_LOG = "bookmarkLog";

    /**
     * Configuration key used to get the publish store type from the
     * Flume context. Possible values are "none" for no publish store, "memory"
     * for a memory-backed publish store, or "file" for a file-backed publish
     * store. Default is "none".
     */
    String PUB_STORE_TYPE = "pubStoreType";

    /**
     * Configuration key used to get the publish store's initial capacity
     * (in 2KB blocks) from the Flume context. Default is 1000 blocks.
     */
    String PUB_STORE_INIT_CAP = "pubStoreInitialCap";

    /**
     * Configuration key used to get the publish store's backing file path from
     * the Flume context. Required if using a file-backed publish store.
     */
    String PUB_STORE_PATH = "pubStorePath";
    
    /**
     * Configuration key used to get server URI(s) from the Flume context.
     * When an HAClient with multiple AMPS servers is desired, this key should
     * be used as a prefix for multiple numbered uri's (e.g. uri1=...,
     * uri2=..., uri3=...).
     */
    String URI = "uri";
    
    /**
     * Configuration key used to get the AMPS command type string from the
     * Flume context. This key is optional. If its value isn't specified
     * it defaults to "subscribe".
     */
    String COMMAND = "command";
    
    /**
     * Configuration key used to get the AMPS topic expression from the
     * Flume context. For a source this may be a topic or regular expression
     * topic. For a sink it must be an actual topic since it will be published
     * to by default (unless an event specifies the topic it should be published
     * to in its topic header).
     */
    String TOPIC = "topic";
    
    /**
     * Configuration key used to get the AMPS filter expression from the
     * Flume context. This key is optional.
     */
    String FILTER = "filter";
    
    /**
     * Configuration key used to get the AMPS command options from the
     * Flume context. This key is optional.
     */
    String OPTIONS = "options";
    
    /**
     * Configuration key used to get the AMPS subscription ID from the
     * Flume context.
     */
    String SUB_ID = "subscriptionId";
    
    /**
     * Configuration key used to specify a bit mask of AMPS command type
     * integers that will be stored into configured Flume channels when received
     * by the source's AMPS client. Any message received whose command type is
     * not in this bit mask will be ignored. By default this mask contains
     * {@link Message.Command.Publish}, {@link Message.Command.SOW},
     * {@link Message.Command.DeltaPublish}, and {@link Message.Command.OOF}
     */
    String CMD_TYPE_FILTER = "cmdTypeFilter";
    
    /**
     * Configuration key used to get the maximum number of message buffers
     * (for the message multi-buffer) from the Flume context.
     */
    String MAX_BUFFS = "maxBuffers";
    
    /**
     * Configuration key used to get the maximum batch size from the Flume
     * context. This is the max number of events that will be written to or
     * read from a channel transaction. This shouldn't be larger than
     * your smallest configured transaction capacity for all Flume
     * channels used by your AMPS sources / sinks.
     */
    String MAX_BATCH = "maxBatch";
    
    /**
     * Configuration key used to get the pruning time threshold from the
     * Flume context. When the configured AMPS client is using a
     * LoggedBookmarkStore, the AMPS Flume source will prune it of old entries
     * whenever it is polled and it finds that at least this many milliseconds
     * have elapsed since the last prune operation or startup. The default is
     * 5 minutes (300,000 milliseconds). Pruning will also be done upon source
     * shutdown.
     * 
     * If this parameter is set to zero, both periodic and shutdown
     * pruning will be turned off. This may be useful for testing and debugging,
     * but is NOT recommended for a production deployment.
     */
    String PRUNE_TIME_THRESHOLD = "pruneTimeThreshold";

    /**
     * Configuration key used to get the use-topic-header flag from the
     * Flume context. If set to true, for any event read from the sink's channel
     * that has a topic header, it will be published to the topic specified in
     * the event's header (rather than the sink's default configured topic).
     * Defaults to true.
     */
    String USE_TOPIC_HEADER = "useTopicHeader";

    /**
     * Configuration key used to get the publish flush timeout from the
     * Flume context. This is the publish flush executed at the end of the
     * batch. Defaults to zero (i.e. wait indefinitely for whole batch to be
     * persisted). If set to -1, no publish flush is executed at the end of the
     * batch. Warning, not using a publish flush prevents detection of any
     * failed writes before committing and will also lead to committing before
     * the whole batch is persisted by AMPS (this might be appropriate if
     * relying on a file-backed publish store to make sure messages are
     * eventually persisted in AMPS).
     */
    String PUBLISH_FLUSH_TIMEOUT = "publishFlushTimeout";

    /**
     * Configuration key used to get the state of the log-message-on-write-error
     * flag from the Flume context. If this is true, the failed write handler
     * will write the actual message that failed to the Flume log when a failed
     * write is detected. NOTE: the whole message will only be available to the
     * failed write handler if the AMPS client is using a publish store. 
     */
    String LOG_MSG_ON_WRITE_ERROR = "logMsgOnWriteError";

    /**
     * Configuration key used to get the state of the rollback-on-error
     * flag from the Flume context. If this is true, then any failed writes
     * detected or exceptions caught during a batch will cause the channel
     * transaction to be rollback (leading to those same messages being read
     * during the next batch). Setting this to false may be appropriate if
     * relying on a file-backed publish store to eventually make sure any
     * messages published reach AMPS.
     */
    String ROLLBACK_ON_ERROR = "rollbackOnError";
    
    /**
     * <p>
     * Configuration key used to get a comma-separated list of headers that
     * should be added to each Flume event created from an AMPS message.
     * Possible values include:
     * </p>
     * 
     * <ul>
     * 
     * <li>command - The command type of the AMPS message.</li>
     * 
     * <li>topic - The topic name of the AMPS message.</li>
     * 
     * <li>sowKey - The SOW key of the AMPS message. Only set on the message
     * for SOW and SOW and Subscribe queries.</li>
     * 
     * <li>timestamp - The ISO-8601 timestamp of when AMPS processed the
     * message. Only set on the message when the query or subscription specifies
     * the 'timestamp' option.</li>
     * 
     * <li>bookmark - The unique bookmark string of the AMPS message. Only
     * set on the message for bookmark subscriptions.</li>
     * 
     * <li>correlationId - The correlation Id of the AMPS message.</li>
     * 
     * <li>subId - The subscription Id of the AMPS message.</li>
     * 
     * <li>length - The length of the AMPS message body in bytes.</li>
     * 
     * <li>currTimestamp - The current timestamp of when the AMPS Flume source
     * received the AMPS message. Its value is the number of milliseconds
     * since the system clock's epoch, represented as a string.</li>
     * 
     * </ul>
     */
    String EVENT_HEADERS = "eventHeaders";
    
    /**
     * Event header key for the message command type.
     */
    String COMMAND_HEADER = "command";
    
    /**
     * Event header key for the message topic.
     */
    String TOPIC_HEADER = "topic";
    
    /**
     * Event header key for the message .
     */
    String SOW_KEY_HEADER = "sowKey";
    
    /**
     * Event header key for the message's ISO-8601 timestamp of when it was
     * processed by the AMPS server.
     */
    String AMPS_TIMESTAMP_HEADER = "ampsTimestamp";
    
    /**
     * Event header key for the message bookmark.
     */
    String BOOKMARK_HEADER = "bookmark";
    
    /**
     * Event header key for the message correlationId.
     */
    String CORRELATION_ID_HEADER = "correlationId";
    
    /**
     * Event header key for the message's subscription Id.
     */
    String SUB_ID_HEADER = "subId";
    
    /**
     * Event header key for the message body length in bytes.
     */
    String LENGTH_HEADER = "length";
    
    /**
     * Event header key for the message's current timestamp when it was received
     * by the AMPS Flume Source.
     */
    String TIMESTAMP_HEADER = "timestamp";
}
