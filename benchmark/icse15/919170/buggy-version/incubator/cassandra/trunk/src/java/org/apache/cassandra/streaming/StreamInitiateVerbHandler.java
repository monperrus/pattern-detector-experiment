package org.apache.cassandra.streaming;
/*
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 */


import java.io.*;
import java.net.InetAddress;
import java.util.*;

import org.apache.log4j.Logger;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.ColumnFamilyStore;
import org.apache.cassandra.db.Table;
import org.apache.cassandra.io.SSTable;
import org.apache.cassandra.net.IVerbHandler;
import org.apache.cassandra.net.Message;
import org.apache.cassandra.net.MessagingService;
import org.apache.cassandra.streaming.StreamInitiateMessage;
import org.apache.cassandra.streaming.StreamInManager;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.FBUtilities;

public class StreamInitiateVerbHandler implements IVerbHandler
{
    private static Logger logger = Logger.getLogger(StreamInitiateVerbHandler.class);

    /*
     * Here we handle the StreamInitiateMessage. Here we get the
     * array of StreamContexts. We get file names for the column
     * families associated with the files and replace them with the
     * file names as obtained from the column family store on the
     * receiving end.
    */
    public void doVerb(Message message)
    {
        byte[] body = message.getMessageBody();
        ByteArrayInputStream bufIn = new ByteArrayInputStream(body);
        if (logger.isDebugEnabled())
            logger.debug(String.format("StreamInitiateVerbeHandler.doVerb %s %s %s", message.getVerb(), message.getMessageId(), message.getMessageType()));

        try
        {
            StreamInitiateMessage biMsg = StreamInitiateMessage.serializer().deserialize(new DataInputStream(bufIn));
            PendingFile[] pendingFiles = biMsg.getStreamContext();

            if (pendingFiles.length == 0)
            {
                if (logger.isDebugEnabled())
                    logger.debug("no data needed from " + message.getFrom());
                if (StorageService.instance.isBootstrapMode())
                    StorageService.instance.removeBootstrapSource(message.getFrom(), new String(message.getHeader(StreamOut.TABLE_NAME)));
                return;
            }

            /*
             * For each of the remote files in the incoming message
             * generate a local pendingFile and store it in the StreamInManager.
             */
            for (Map.Entry<PendingFile, PendingFile> pendingFile : getContextMapping(pendingFiles).entrySet())
            {
                PendingFile remoteFile = pendingFile.getKey();
                PendingFile localFile = pendingFile.getValue();

                CompletedFileStatus streamStatus = new CompletedFileStatus(remoteFile.getFilename(),
                                                                           remoteFile.getExpectedBytes());

                if (logger.isDebugEnabled())
                  logger.debug("Preparing to receive stream from " + message.getFrom() + ": " + remoteFile + " -> " + localFile);
                addStreamContext(message.getFrom(), localFile, streamStatus);
            }

            StreamInManager.registerStreamCompletionHandler(message.getFrom(), new StreamCompletionHandler());
            if (logger.isDebugEnabled())
              logger.debug("Sending a stream initiate done message ...");
            Message doneMessage = new Message(FBUtilities.getLocalAddress(), "", StorageService.Verb.STREAM_INITIATE_DONE, new byte[0] );
            MessagingService.instance.sendOneWay(doneMessage, message.getFrom());
        }
        catch (IOException ex)
        {
            throw new IOError(ex);
        }
    }

    /**
     * Translates remote files to local files by creating a local sstable
     * per remote sstable.
     */
    public LinkedHashMap<PendingFile, PendingFile> getContextMapping(PendingFile[] remoteFiles) throws IOException
    {
        /* Create a local sstable for each remote sstable */
        LinkedHashMap<PendingFile, PendingFile> mapping = new LinkedHashMap<PendingFile, PendingFile>();
        Map<SSTable.Descriptor, SSTable.Descriptor> sstables = new HashMap<SSTable.Descriptor, SSTable.Descriptor>();
        for (PendingFile remote : remoteFiles)
        {
            SSTable.Descriptor remotedesc = remote.getDescriptor();
            SSTable.Descriptor localdesc = sstables.get(remotedesc);
            if (localdesc == null)
            {
                // new local sstable
                Table table = Table.open(remotedesc.ksname);
                ColumnFamilyStore cfStore = table.getColumnFamilyStore(remotedesc.cfname);

                localdesc = SSTable.Descriptor.fromFilename(cfStore.getFlushPath());
                sstables.put(remotedesc, localdesc);
            }

            // add a local file for this component
            mapping.put(remote, new PendingFile(localdesc, remote.getComponent(), remote.getExpectedBytes()));
        }

        return mapping;
    }

    private void addStreamContext(InetAddress host, PendingFile pendingFile, CompletedFileStatus streamStatus)
    {
        if (logger.isDebugEnabled())
          logger.debug("Adding stream context " + pendingFile + " for " + host + " ...");
        StreamInManager.addStreamContext(host, pendingFile, streamStatus);
    }
}
