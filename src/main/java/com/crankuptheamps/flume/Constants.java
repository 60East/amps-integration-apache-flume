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
    public static final String CLIENT_FACTORY = "clientFactoryClass";
    
    /**
     * Configuration key used to get the AMPS client name from the
     * Flume context.
     */
    public static final String CLIENT_NAME = "clientName";
    
    /**
     * Configuration key used to get the bookmark log file path/name from the
     * Flume context.
     */
    public static final String BOOKMARK_LOG = "bookmarkLog";
    
    /**
     * Configuration key used to get server URI(s) from the Flume context.
     * When an HAClient with multiple AMPS servers is desired, this key should
     * be used as a prefix for multiple numbered uri's (e.g. uri1=...,
     * uri2=..., uri3=...).
     */
    public static final String URI = "uri";
    
    /**
     * Configuration key used to get the AMPS command type string from the
     * Flume context. This key is optional. If its value isn't specified
     * it defaults to "subscribe".
     */
    public static final String COMMAND = "command";
    
    /**
     * Configuration key used to get the AMPS topic expression from the
     * Flume context.
     */
    public static final String TOPIC = "topic";
    
    /**
     * Configuration key used to get the AMPS filter expression from the
     * Flume context. This key is optional.
     */
    public static final String FILTER = "filter";
    
    /**
     * Configuration key used to get the AMPS command options from the
     * Flume context. This key is optional.
     */
    public static final String OPTIONS = "options";
    
    /**
     * Configuration key used to get the AMPS subscription ID from the
     * Flume context.
     */
    public static final String SUB_ID = "subscriptionId";
    
    /**
     * Configuration key used to get the maximum number of message buffers
     * (for the message multi-buffer) from the Flume context.
     */
    public static final String MAX_BUFFS = "maxBuffers";
    
    /**
     * Configuration key used to get the maximum batch size (within each 
     * message buffer) from the Flume context. This shouldn't be larger than
     * your smallest configured transaction capacity for all Flume
     * channels that your AMPS source writes to.
     */
    public static final String MAX_BATCH = "maxBatch";
    
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
    public static final String PRUNE_TIME_THRESHOLD = "pruneTimeThreshold";
}
