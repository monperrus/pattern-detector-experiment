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

import org.apache.cassandra.db.RangeSliceCommand;
import org.apache.cassandra.db.RangeSliceReply;
import org.apache.cassandra.db.Table;
import org.apache.cassandra.net.IVerbHandler;
import org.apache.cassandra.net.Message;
import org.apache.cassandra.net.MessagingService;
import org.apache.log4j.Logger;

public class RangeSliceVerbHandler implements IVerbHandler
{

    private static final Logger logger = Logger.getLogger(RangeSliceVerbHandler.class);

    public void doVerb(Message message)
    {
        try
        {
            RangeSliceCommand command = RangeSliceCommand.read(message);
            RangeSliceReply reply = Table.open(command.keyspace).getColumnFamilyStore(command.column_family).getRangeSlice(
                    command.super_column,
                    command.startKey,
                    command.finishKey,
                    command.max_keys,
                    command.predicate.slice_range,
                    command.predicate.column_names);
            Message response = reply.getReply(message);
            if (logger.isDebugEnabled())
                logger.debug("Sending " + reply+ " to " + message.getMessageId() + "@" + message.getFrom());
            MessagingService.instance().sendOneWay(response, message.getFrom());
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
