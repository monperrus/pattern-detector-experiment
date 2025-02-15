/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cassandra.service;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.io.IOException;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.net.IAsyncCallback;
import org.apache.cassandra.net.Message;
import org.apache.cassandra.net.MessagingService;
import org.apache.cassandra.utils.SimpleCondition;

import org.apache.log4j.Logger;

public class WriteResponseHandler implements IAsyncCallback
{
    protected static final Logger logger = Logger.getLogger( WriteResponseHandler.class );
    protected final SimpleCondition condition = new SimpleCondition();
    private final int responseCount;
    protected final List<Message> responses;
    protected int localResponses;
    private final long startTime;

    public WriteResponseHandler(int responseCount)
    {
        // at most one node per range can bootstrap at a time, and these will be added to the write until
        // bootstrap finishes (at which point we no longer need to write to the old ones).
        assert 1 <= responseCount && responseCount <= 2 * DatabaseDescriptor.getReplicationFactor()
            : "invalid response count " + responseCount;

        this.responseCount = responseCount;
        responses = new ArrayList<Message>(responseCount);
        startTime = System.currentTimeMillis();
    }

    public void get() throws TimeoutException
    {
        try
        {
            long timeout = System.currentTimeMillis() - startTime + DatabaseDescriptor.getRpcTimeout();
            boolean success;
            try
            {
                success = condition.await(timeout, TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException ex)
            {
                throw new AssertionError(ex);
            }

            if (!success)
            {
                throw new TimeoutException("Operation timed out - received only " + responses.size() + localResponses + " responses");
            }
        }
        finally
        {
            for (Message response : responses)
            {
                MessagingService.removeRegisteredCallback(response.getMessageId());
            }
        }
    }

    public synchronized void response(Message message)
    {
        if (condition.isSignaled())
            return;
        responses.add(message);
        maybeSignal();
    }

    public synchronized void localResponse()
    {
        if (condition.isSignaled())
            return;
        localResponses++;
        maybeSignal();
    }

    private void maybeSignal()
    {
        if (responses.size() + localResponses >= responseCount)
        {
            condition.signal();
        }
    }
}
