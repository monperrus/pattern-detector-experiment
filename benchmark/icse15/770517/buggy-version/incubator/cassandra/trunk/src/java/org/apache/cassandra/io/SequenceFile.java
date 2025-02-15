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

package org.apache.cassandra.io;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.utils.BloomFilter;
import org.apache.cassandra.utils.LogUtil;

import org.apache.log4j.Logger;

/**
 * This class writes key/value pairs seqeuntially to disk. It is
 * also used to read sequentially from disk. However one could
 * jump to random positions to read data from the file. This class
 * also has many implementations of the IFileWriter and IFileReader
 * interfaces which are exposed through factory methods.
 * <p/>
 * Author : Avinash Lakshman ( alakshman@facebook.com) & Prashant Malik ( pmalik@facebook.com ) & Karthik Ranganathan ( kranganathan@facebook.com )
 */

public class SequenceFile
{
    public static abstract class AbstractWriter implements IFileWriter
    {
        protected String filename_;

        AbstractWriter(String filename)
        {
            filename_ = filename;
        }

        public String getFileName()
        {
            return filename_;
        }

        public long lastModified()
        {
            File file = new File(filename_);
            return file.lastModified();
        }
    }

    public static class Writer extends AbstractWriter
    {
        protected RandomAccessFile file_;

        Writer(String filename) throws IOException
        {
            super(filename);
            init(filename);
        }

        Writer(String filename, int size) throws IOException
        {
            super(filename);
            init(filename, size);
        }

        protected void init(String filename) throws IOException
        {
            File file = new File(filename);
            if (!file.exists())
            {
                file.createNewFile();
            }
            file_ = new RandomAccessFile(file, "rw");
        }

        protected void init(String filename, int size) throws IOException
        {
            init(filename);
        }

        public long getCurrentPosition() throws IOException
        {
            return file_.getFilePointer();
        }

        public void seek(long position) throws IOException
        {
            file_.seek(position);
        }

        public void append(DataOutputBuffer buffer) throws IOException
        {
            file_.write(buffer.getData(), 0, buffer.getLength());
        }

        public void append(DataOutputBuffer keyBuffer, DataOutputBuffer buffer) throws IOException
        {
            int keyBufLength = keyBuffer.getLength();
            if (keyBuffer == null || keyBufLength == 0)
                throw new IllegalArgumentException("Key cannot be NULL or of zero length.");

            file_.writeInt(keyBufLength);
            file_.write(keyBuffer.getData(), 0, keyBufLength);

            int length = buffer.getLength();
            file_.writeInt(length);
            file_.write(buffer.getData(), 0, length);
        }

        public void append(String key, DataOutputBuffer buffer) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            file_.writeUTF(key);
            int length = buffer.getLength();
            file_.writeInt(length);
            file_.write(buffer.getData(), 0, length);
        }

        public void append(String key, byte[] value) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            file_.writeUTF(key);
            file_.writeInt(value.length);
            file_.write(value);
        }

        public void append(String key, long value) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            file_.writeUTF(key);
            file_.writeLong(value);
        }

        /**
         * Be extremely careful while using this API. This currently
         * used to write the commit log header in the commit logs.
         * If not used carefully it could completely screw up reads
         * of other key/value pairs that are written.
         *
         * @param bytes the bytes to write
         */
        public long writeDirect(byte[] bytes) throws IOException
        {
            file_.write(bytes);
            return file_.getFilePointer();
        }

        public void writeLong(long value) throws IOException
        {
            file_.writeLong(value);
        }

        public void close() throws IOException
        {
            file_.close();
        }

        public void close(byte[] footer, int size) throws IOException
        {
            file_.writeUTF(SequenceFile.marker_);
            file_.writeInt(size);
            file_.write(footer, 0, size);
        }

        public String getFileName()
        {
            return filename_;
        }

        public long getFileSize() throws IOException
        {
            return file_.length();
        }
    }

    public static class BufferWriter extends Writer
    {

        BufferWriter(String filename, int size) throws IOException
        {
            super(filename, size);
        }

        @Override
        protected void init(String filename) throws IOException
        {
            init(filename, 0);
        }

        @Override
        protected void init(String filename, int size) throws IOException
        {
            File file = new File(filename);
            file_ = new BufferedRandomAccessFile(file, "rw", size);
            if (!file.exists())
            {
                file.createNewFile();
            }
        }
    }

    public static class ChecksumWriter extends Writer
    {

        ChecksumWriter(String filename, int size) throws IOException
        {
            super(filename, size);
        }

        @Override
        protected void init(String filename) throws IOException
        {
            init(filename, 0);
        }

        @Override
        protected void init(String filename, int size) throws IOException
        {
            File file = new File(filename);
            file_ = new ChecksumRandomAccessFile(file, "rw", size);
        }

        @Override
        public void close() throws IOException
        {
            super.close();
            ChecksumManager.close(filename_);
        }
    }

    public static class ConcurrentWriter extends AbstractWriter
    {
        private FileChannel fc_;

        public ConcurrentWriter(String filename) throws IOException
        {
            super(filename);
            RandomAccessFile raf = new RandomAccessFile(filename, "rw");
            fc_ = raf.getChannel();
        }

        public long getCurrentPosition() throws IOException
        {
            return fc_.position();
        }

        public void seek(long position) throws IOException
        {
            fc_.position(position);
        }

        public void append(DataOutputBuffer buffer) throws IOException
        {
            int length = buffer.getLength();
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(length);
            byteBuffer.put(buffer.getData(), 0, length);
            byteBuffer.flip();
            fc_.write(byteBuffer);
        }

        public void append(DataOutputBuffer keyBuffer, DataOutputBuffer buffer) throws IOException
        {
            int keyBufLength = keyBuffer.getLength();
            if (keyBuffer == null || keyBufLength == 0)
                throw new IllegalArgumentException("Key cannot be NULL or of zero length.");

            /* Size allocated "int" for key length + key + "int" for data length + data */
            int length = buffer.getLength();
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 + keyBufLength + 4 + length);
            byteBuffer.putInt(keyBufLength);
            byteBuffer.put(keyBuffer.getData(), 0, keyBufLength);
            byteBuffer.putInt(length);
            byteBuffer.put(buffer.getData(), 0, length);
            byteBuffer.flip();
            fc_.write(byteBuffer);
        }

        public void append(String key, DataOutputBuffer buffer) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            int length = buffer.getLength();
            /* Size allocated : utfPrefix_ + key length + "int" for data size + data */
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(SequenceFile.utfPrefix_ + key.length() + 4 + length);
            SequenceFile.writeUTF(byteBuffer, key);
            byteBuffer.putInt(length);
            byteBuffer.put(buffer.getData(), 0, length);
            byteBuffer.flip();
            fc_.write(byteBuffer);
        }

        public void append(String key, byte[] value) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            /* Size allocated key length + "int" for data size + data */
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(utfPrefix_ + key.length() + 4 + value.length);
            SequenceFile.writeUTF(byteBuffer, key);
            byteBuffer.putInt(value.length);
            byteBuffer.put(value);
            byteBuffer.flip();
            fc_.write(byteBuffer);
        }

        public void append(String key, long value) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            /* Size allocated key length + a long */
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(SequenceFile.utfPrefix_ + key.length() + 8);
            SequenceFile.writeUTF(byteBuffer, key);
            byteBuffer.putLong(value);
            byteBuffer.flip();
            fc_.write(byteBuffer);
        }

        /*
         * Be extremely careful while using this API. This currently
         * used to write the commit log header in the commit logs.
         * If not used carefully it could completely screw up reads
         * of other key/value pairs that are written.
        */
        public long writeDirect(byte[] bytes) throws IOException
        {
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bytes.length);
            byteBuffer.put(bytes);
            byteBuffer.flip();
            fc_.write(byteBuffer);
            return fc_.position();
        }

        public void writeLong(long value) throws IOException
        {
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(8);
            byteBuffer.putLong(value);
            byteBuffer.flip();
            fc_.write(byteBuffer);
        }

        public void close() throws IOException
        {
            fc_.close();
        }

        public void close(byte[] footer, int size) throws IOException
        {
            /* Size is marker length + "int" for size + footer data */
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(utfPrefix_ + SequenceFile.marker_.length() + 4 + footer.length);
            SequenceFile.writeUTF(byteBuffer, SequenceFile.marker_);
            byteBuffer.putInt(size);
            byteBuffer.put(footer);
            byteBuffer.flip();
            fc_.write(byteBuffer);
        }

        public String getFileName()
        {
            return filename_;
        }

        public long getFileSize() throws IOException
        {
            return fc_.size();
        }
    }

    public static class FastConcurrentWriter extends AbstractWriter
    {
        private FileChannel fc_;
        private MappedByteBuffer buffer_;

        public FastConcurrentWriter(String filename, int size) throws IOException
        {
            super(filename);
            fc_ = new RandomAccessFile(filename, "rw").getChannel();
            buffer_ = fc_.map(FileChannel.MapMode.READ_WRITE, 0, size);
            buffer_.load();
        }

        void unmap(final Object buffer)
        {
            AccessController.doPrivileged(new PrivilegedAction<MappedByteBuffer>()
            {
                public MappedByteBuffer run()
                {
                    try
                    {
                        Method getCleanerMethod = buffer.getClass().getMethod("cleaner", new Class[0]);
                        getCleanerMethod.setAccessible(true);
                        sun.misc.Cleaner cleaner = (sun.misc.Cleaner) getCleanerMethod.invoke(buffer);
                        cleaner.clean();
                    }
                    catch (Throwable e)
                    {
                        logger_.warn(LogUtil.throwableToString(e));
                    }
                    return null;
                }
            });
        }


        public long getCurrentPosition() throws IOException
        {
            return buffer_.position();
        }

        public void seek(long position) throws IOException
        {
            buffer_.position((int) position);
        }

        public void append(DataOutputBuffer buffer) throws IOException
        {
            buffer_.put(buffer.getData(), 0, buffer.getLength());
        }

        public void append(DataOutputBuffer keyBuffer, DataOutputBuffer buffer) throws IOException
        {
            int keyBufLength = keyBuffer.getLength();
            if (keyBuffer == null || keyBufLength == 0)
                throw new IllegalArgumentException("Key cannot be NULL or of zero length.");

            int length = buffer.getLength();
            buffer_.putInt(keyBufLength);
            buffer_.put(keyBuffer.getData(), 0, keyBufLength);
            buffer_.putInt(length);
            buffer_.put(buffer.getData(), 0, length);
        }

        public void append(String key, DataOutputBuffer buffer) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            int length = buffer.getLength();
            SequenceFile.writeUTF(buffer_, key);
            buffer_.putInt(length);
            buffer_.put(buffer.getData(), 0, length);
        }

        public void append(String key, byte[] value) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            SequenceFile.writeUTF(buffer_, key);
            buffer_.putInt(value.length);
            buffer_.put(value);
        }

        public void append(String key, long value) throws IOException
        {
            if (key == null)
                throw new IllegalArgumentException("Key cannot be NULL.");

            SequenceFile.writeUTF(buffer_, key);
            buffer_.putLong(value);
        }

        /*
         * Be extremely careful while using this API. This currently
         * used to write the commit log header in the commit logs.
         * If not used carefully it could completely screw up reads
         * of other key/value pairs that are written.
        */
        public long writeDirect(byte[] bytes) throws IOException
        {
            buffer_.put(bytes);
            return buffer_.position();
        }

        public void writeLong(long value) throws IOException
        {
            buffer_.putLong(value);
        }

        public void close() throws IOException
        {
            buffer_.flip();
            buffer_.force();
            unmap(buffer_);
            fc_.truncate(buffer_.limit());
        }

        public void close(byte[] footer, int size) throws IOException
        {
            SequenceFile.writeUTF(buffer_, SequenceFile.marker_);
            buffer_.putInt(size);
            buffer_.put(footer);
            close();
        }

        public String getFileName()
        {
            return filename_;
        }

        public long getFileSize() throws IOException
        {
            return buffer_.position();
        }
    }

    public static abstract class AbstractReader implements IFileReader
    {
        private static final short utfPrefix_ = 2;
        protected RandomAccessFile file_;
        protected String filename_;

        AbstractReader(String filename)
        {
            filename_ = filename;
        }

        public String getFileName()
        {
            return filename_;
        }

        /**
         * Return the position of the given key from the block index.
         *
         * @param key the key whose offset is to be extracted from the current block index
         */
        public long getPositionFromBlockIndex(String key) throws IOException
        {
            long position = -1L;
            /* note the beginning of the block index */
            long blockIndexPosition = file_.getFilePointer();
            /* read the block key. */
            String blockIndexKey = file_.readUTF();
            if (!blockIndexKey.equals(SSTable.blockIndexKey_))
                throw new IOException("Unexpected position to be reading the block index from.");
            /* read the size of the block index */
            int size = file_.readInt();

            /* Read the entire block index. */
            byte[] bytes = new byte[size];
            file_.readFully(bytes);

            DataInputBuffer bufIn = new DataInputBuffer();
            bufIn.reset(bytes, bytes.length);
            /* Number of keys in the block. */
            int keys = bufIn.readInt();
            for (int i = 0; i < keys; ++i)
            {
                String keyInBlock = bufIn.readUTF();
                if (keyInBlock.equals(key))
                {
                    position = bufIn.readLong();
                    break;
                }
                else
                {
                    /*
                     * This is not the key we are looking for. So read its position
                     * and the size of the data associated with it. This was strored
                     * as the BlockMetadata.
                    */
                    bufIn.readLong();
                    bufIn.readLong();
                }
            }

            /* we do this because relative position of the key within a block is stored. */
            if (position != -1L)
                position = blockIndexPosition - position;
            return position;
        }

        /**
         * Return the block index metadata for a given key.
         */
        public SSTable.BlockMetadata getBlockMetadata(String key) throws IOException
        {
            SSTable.BlockMetadata blockMetadata = SSTable.BlockMetadata.NULL;
            /* read the block key. */
            String blockIndexKey = file_.readUTF();
            if (!blockIndexKey.equals(SSTable.blockIndexKey_))
                throw new IOException("Unexpected position to be reading the block index from.");
            /* read the size of the block index */
            int size = file_.readInt();

            /* Read the entire block index. */
            byte[] bytes = new byte[size];
            file_.readFully(bytes);

            DataInputBuffer bufIn = new DataInputBuffer();
            bufIn.reset(bytes, bytes.length);

            /* Number of keys in the block. */
            int keys = bufIn.readInt();
            for (int i = 0; i < keys; ++i)
            {
                if (bufIn.readUTF().equals(key))
                {
                    long position = bufIn.readLong();
                    long dataSize = bufIn.readLong();
                    blockMetadata = new SSTable.BlockMetadata(position, dataSize);
                    break;
                }
                else
                {
                    /*
                     * This is not the key we are looking for. So read its position
                     * and the size of the data associated with it. This was strored
                     * as the BlockMetadata.
                    */
                    bufIn.readLong();
                    bufIn.readLong();
                }
            }

            return blockMetadata;
        }

        /**
         * This function seeks to the position where the key data is present in the file
         * in order to get the buffer cache populated with the key-data. This is done as
         * a hint before the user actually queries the data.
         *
         * @param key   the key whose data is being touched
         * @param fData
         */
        public long touch(String key, boolean fData) throws IOException
        {
            long bytesRead = -1L;
            if (isEOF())
                return bytesRead;

            long startPosition = file_.getFilePointer();
            String keyInDisk = file_.readUTF();
            if (keyInDisk != null)
            {
                /*
                 * If key on disk is greater than requested key
                 * we can bail out since we exploit the property
                 * of the SSTable format.
                */
                if (keyInDisk.compareTo(key) > 0)
                    return bytesRead;

                /*
                 * If we found the key then we populate the buffer that
                 * is passed in. If not then we skip over this key and
                 * position ourselves to read the next one.
                */
                int dataSize = file_.readInt();
                if (keyInDisk.equals(key))
                {
                    /* return 0L to signal the key has been touched. */
                    bytesRead = 0L;
                    return bytesRead;
                }
                else
                {
                    /* skip over data portion */
                    file_.seek(dataSize + file_.getFilePointer());
                }

                long endPosition = file_.getFilePointer();
                bytesRead = endPosition - startPosition;
            }

            return bytesRead;
        }

        /**
         * This method seek the disk head to the block index, finds
         * the offset of the key within the block and seeks to that
         * offset.
         *
         * @param key     we are interested in.
         * @param section indicates the location of the block index.
         * @throws IOException
         */
        protected void seekTo(String key, Coordinate section) throws IOException
        {
            /* Goto the Block Index */
            seek(section.end_);
            long position = getPositionFromBlockIndex(key);
            if (position == -1)
                throw new IOException("This key " + key + " does not exist in this file.");
            seek(position);
        }

        /**
         * Defreeze the bloom filter.
         *
         * @return bloom filter summarizing the column information
         * @throws IOException
         */
        private BloomFilter defreezeBloomFilter() throws IOException
        {
            int size = file_.readInt();
            byte[] bytes = new byte[size];
            file_.readFully(bytes);
            DataInputBuffer bufIn = new DataInputBuffer();
            bufIn.reset(bytes, bytes.length);
            BloomFilter bf = BloomFilter.serializer().deserialize(bufIn);
            return bf;
        }

        /**
         * Reads the column name indexes if present. If the
         * indexes are based on time then skip over them.
         *
         * @param cfName
         * @return
         */
        private int handleColumnNameIndexes(String cfName, List<IndexHelper.ColumnIndexInfo> columnIndexList) throws IOException
        {
            /* check if we have an index */
            boolean hasColumnIndexes = file_.readBoolean();
            int totalBytesRead = 1;
            /* if we do then deserialize the index */
            if (hasColumnIndexes)
            {
                if (DatabaseDescriptor.isNameSortingEnabled(cfName) || DatabaseDescriptor.getColumnFamilyType(cfName).equals("Super"))
                {
                    /* read the index */
                    totalBytesRead += IndexHelper.deserializeIndex(cfName, file_, columnIndexList);
                }
                else
                {
                    totalBytesRead += IndexHelper.skipIndex(file_);
                }
            }
            return totalBytesRead;
        }

        /**
         * Reads the column name indexes if present. If the
         * indexes are based on time then skip over them.
         *
         * @param cfName
         * @return
         */
        private int handleColumnTimeIndexes(String cfName, List<IndexHelper.ColumnIndexInfo> columnIndexList) throws IOException
        {
            /* check if we have an index */
            boolean hasColumnIndexes = file_.readBoolean();
            int totalBytesRead = 1;
            /* if we do then deserialize the index */
            if (hasColumnIndexes)
            {
                if (DatabaseDescriptor.isTimeSortingEnabled(cfName))
                {
                    /* read the index */
                    totalBytesRead += IndexHelper.deserializeIndex(cfName, file_, columnIndexList);
                }
                else
                {
                    totalBytesRead += IndexHelper.skipIndex(file_);
                }
            }
            return totalBytesRead;
        }

        /**
         * This method dumps the next key/value into the DataOuputStream
         * passed in. Always use this method to query for application
         * specific data as it will have indexes.
         *
         * @param key       key we are interested in.
         * @param bufOut    DataOutputStream that needs to be filled.
         * @param columnFamilyName name of the columnFamily
         * @param columnNames columnNames we are interested in
         * OR
         * @param timeRange time range we are interested in
         * @param section   region of the file that needs to be read
         * @return number of bytes that were read.
         * @throws IOException
         */
        public long next(String key, DataOutputBuffer bufOut, String columnFamilyName, List<String> columnNames, IndexHelper.TimeRange timeRange, Coordinate section) throws IOException
        {
            assert !columnFamilyName.contains(":");
            assert timeRange == null || columnNames == null; // at most one may be non-null

            long bytesRead = -1L;
            if (isEOF())
                return bytesRead;
            seekTo(key, section);
            /* note the position where the key starts */
            long startPosition = file_.getFilePointer();
            String keyInDisk = file_.readUTF();
            if (keyInDisk != null)
            {
                /*
                 * If key on disk is greater than requested key
                 * we can bail out since we exploit the property
                 * of the SSTable format.
                */
                if (keyInDisk.compareTo(key) > 0)
                    return bytesRead;

                /*
                 * If we found the key then we populate the buffer that
                 * is passed in. If not then we skip over this key and
                 * position ourselves to read the next one.
                */
                if (keyInDisk.equals(key))
                {
                    if (timeRange == null) {
                        readColumns(key, bufOut, columnFamilyName, columnNames);
                    } else {
                        readTimeRange(key, bufOut, columnFamilyName, timeRange);
                    }
                }
                else
                {
                    /* skip over data portion */
                    int dataSize = file_.readInt();
                    file_.seek(dataSize + file_.getFilePointer());
                }

                long endPosition = file_.getFilePointer();
                bytesRead = endPosition - startPosition;
            }

            return bytesRead;
        }

        private void readTimeRange(String key, DataOutputBuffer bufOut, String columnFamilyName, IndexHelper.TimeRange timeRange)
                throws IOException
        {
            int dataSize = file_.readInt();

            /* write the key into buffer */
            bufOut.writeUTF(key);

            int bytesSkipped = IndexHelper.skipBloomFilter(file_);
            /*
             * read the correct number of bytes for the column family and
             * write data into buffer. Substract from dataSize the bloom
             * filter size.
            */
            dataSize -= bytesSkipped;
            List<IndexHelper.ColumnIndexInfo> columnIndexList = new ArrayList<IndexHelper.ColumnIndexInfo>();
            /* Read the times indexes if present */
            int totalBytesRead = handleColumnTimeIndexes(columnFamilyName, columnIndexList);
            dataSize -= totalBytesRead;

            /* read the column family name */
            String cfName = file_.readUTF();
            dataSize -= (utfPrefix_ + cfName.length());
            
            /* read local deletion time */
            int localDeletionTime = file_.readInt();
            dataSize -=4;

            /* read if this cf is marked for delete */
            long markedForDeleteAt = file_.readLong();
            dataSize -= 8;

            /* read the total number of columns */
            int totalNumCols = file_.readInt();
            dataSize -= 4;

            /* get the column range we have to read */
            IndexHelper.ColumnRange columnRange = IndexHelper.getColumnRangeFromTimeIndex(timeRange, columnIndexList, dataSize, totalNumCols);

            Coordinate coordinate = columnRange.coordinate();
            /* seek to the correct offset to the data, and calculate the data size */
            file_.skipBytes((int) coordinate.start_);
            dataSize = (int) (coordinate.end_ - coordinate.start_);

            /*
             * write the number of columns in the column family we are returning:
             *  dataSize that we are reading +
             *  length of column family name +
             *  one booleanfor deleted or not +
             *  one int for number of columns
            */
            bufOut.writeInt(dataSize + utfPrefix_ + cfName.length() + 4 + 8 + 4);
            /* write the column family name */
            bufOut.writeUTF(cfName);
            /* write local deletion time */
            bufOut.writeInt(localDeletionTime);
            /* write if this cf is marked for delete */
            bufOut.writeLong(markedForDeleteAt);
            /* write number of columns */
            bufOut.writeInt(columnRange.count());
            /* now write the columns */
            bufOut.write(file_, dataSize);
        }

        private void readColumns(String key, DataOutputBuffer bufOut, String columnFamilyName, List<String> cNames)
                throws IOException
        {
            int dataSize = file_.readInt();

            /* write the key into buffer */
            bufOut.writeUTF(key);

            /* if we need to read the all the columns do not read the column indexes */
            if (cNames == null || cNames.size() == 0)
            {
                int bytesSkipped = IndexHelper.skipBloomFilterAndIndex(file_);
                /*
                       * read the correct number of bytes for the column family and
                       * write data into buffer
                      */
                dataSize -= bytesSkipped;
                /* write the data size */
                bufOut.writeInt(dataSize);
                /* write the data into buffer, except the boolean we have read */
                bufOut.write(file_, dataSize);
            }
            else
            {
                /* Read the bloom filter summarizing the columns */
                long preBfPos = file_.getFilePointer();
                BloomFilter bf = defreezeBloomFilter();
                long postBfPos = file_.getFilePointer();
                dataSize -= (postBfPos - preBfPos);

                List<IndexHelper.ColumnIndexInfo> columnIndexList = new ArrayList<IndexHelper.ColumnIndexInfo>();
                /* read the column name indexes if present */
                int totalBytesRead = handleColumnNameIndexes(columnFamilyName, columnIndexList);
                dataSize -= totalBytesRead;

                /* read the column family name */
                String cfName = file_.readUTF();
                dataSize -= (utfPrefix_ + cfName.length());

                /* read local deletion time */
                int localDeletionTime = file_.readInt();
                dataSize -=4;

                /* read if this cf is marked for delete */
                long markedForDeleteAt = file_.readLong();
                dataSize -= 8;

                /* read the total number of columns */
                int totalNumCols = file_.readInt();
                dataSize -= 4;

                // TODO: this is name sorted - but eventually this should be sorted by the same criteria as the col index
                /* sort the required list of columns */
                cNames = new ArrayList<String>(cNames);
                Collections.sort(cNames);
                /* get the various column ranges we have to read */
                List<IndexHelper.ColumnRange> columnRanges = IndexHelper.getMultiColumnRangesFromNameIndex(cNames, columnIndexList, dataSize, totalNumCols);

                /* calculate the data size */
                int numColsReturned = 0;
                int dataSizeReturned = 0;
                for (IndexHelper.ColumnRange columnRange : columnRanges)
                {
                    numColsReturned += columnRange.count();
                    Coordinate coordinate = columnRange.coordinate();
                    dataSizeReturned += coordinate.end_ - coordinate.start_;
                }

                /*
                 * write the number of columns in the column family we are returning:
                 * 	dataSize that we are reading +
                 * 	length of column family name +
                 * 	one booleanfor deleted or not +
                 * 	one int for number of columns
                */
                bufOut.writeInt(dataSizeReturned + utfPrefix_ + cfName.length() + 4 + 8 + 4);
                /* write the column family name */
                bufOut.writeUTF(cfName);
                /* write local deletion time */
                bufOut.writeInt(localDeletionTime);
                /* write if this cf is marked for delete */
                bufOut.writeLong(markedForDeleteAt);
                /* write number of columns */
                bufOut.writeInt(numColsReturned);
                int prevPosition = 0;
                /* now write all the columns we are required to write */
                for (IndexHelper.ColumnRange columnRange : columnRanges)
                {
                    /* seek to the correct offset to the data */
                    Coordinate coordinate = columnRange.coordinate();
                    file_.skipBytes((int) (coordinate.start_ - prevPosition));
                    bufOut.write(file_, (int) (coordinate.end_ - coordinate.start_));
                    prevPosition = (int) coordinate.end_;
                }
            }
        }

        /**
         * This method dumps the next key/value into the DataOuputStream
         * passed in.
         *
         * @param bufOut DataOutputStream that needs to be filled.
         * @return total number of bytes read/considered
         */
        public long next(DataOutputBuffer bufOut) throws IOException
        {
            long bytesRead = -1L;
            if (isEOF())
                return bytesRead;

            long startPosition = file_.getFilePointer();
            String key = file_.readUTF();
            if (key != null)
            {
                /* write the key into buffer */
                bufOut.writeUTF(key);
                int dataSize = file_.readInt();
                /* write data size into buffer */
                bufOut.writeInt(dataSize);
                /* write the data into buffer */
                bufOut.write(file_, dataSize);
                long endPosition = file_.getFilePointer();
                bytesRead = endPosition - startPosition;
            }

            /*
             * If we have read the bloom filter in the data
             * file we know we are at the end of the file 
             * and no further key processing is required. So
             * we return -1 indicating we are at the end of
             * the file. 
            */
            if (key.equals(SequenceFile.marker_))
                bytesRead = -1L;
            return bytesRead;
        }

        /**
         * This method dumps the next key/value into the DataOuputStream
         * passed in.
         *
         * @param key     - key we are interested in.
         * @param bufOut  DataOutputStream that needs to be filled.
         * @param section region of the file that needs to be read
         * @return total number of bytes read/considered
         */
        public long next(String key, DataOutputBuffer bufOut, Coordinate section) throws IOException
        {
            long bytesRead = -1L;
            if (isEOF())
                return bytesRead;

            seekTo(key, section);
            /* note the position where the key starts */
            long startPosition = file_.getFilePointer();
            String keyInDisk = file_.readUTF();
            if (keyInDisk != null)
            {
                /*
                 * If key on disk is greater than requested key
                 * we can bail out since we exploit the property
                 * of the SSTable format.
                */
                if (keyInDisk.compareTo(key) > 0)
                    return bytesRead;

                /*
                 * If we found the key then we populate the buffer that
                 * is passed in. If not then we skip over this key and
                 * position ourselves to read the next one.
                */
                int dataSize = file_.readInt();
                if (keyInDisk.equals(key))
                {
                    /* write the key into buffer */
                    bufOut.writeUTF(keyInDisk);
                    /* write data size into buffer */
                    bufOut.writeInt(dataSize);
                    /* write the data into buffer */
                    bufOut.write(file_, dataSize);
                }
                else
                {
                    /* skip over data portion */
                    file_.seek(dataSize + file_.getFilePointer());
                }

                long endPosition = file_.getFilePointer();
                bytesRead = endPosition - startPosition;
            }

            return bytesRead;
        }
    }

    public static class Reader extends AbstractReader
    {
        Reader(String filename) throws IOException
        {
            super(filename);
            init(filename);
        }

        protected void init(String filename) throws IOException
        {
            file_ = new RandomAccessFile(filename, "r");
        }

        public long getEOF() throws IOException
        {
            return file_.length();
        }

        public long getCurrentPosition() throws IOException
        {
            return file_.getFilePointer();
        }

        public boolean isHealthyFileDescriptor() throws IOException
        {
            return file_.getFD().valid();
        }

        public void seek(long position) throws IOException
        {
            file_.seek(position);
        }

        public boolean isEOF() throws IOException
        {
            return (getCurrentPosition() == getEOF());
        }

        /**
         * Be extremely careful while using this API. This currently
         * used to read the commit log header from the commit logs.
         * Treat this as an internal API.
         *
         * @param bytes read from the buffer into the this array
         */
        public void readDirect(byte[] bytes) throws IOException
        {
            file_.readFully(bytes);
        }

        public long readLong() throws IOException
        {
            return file_.readLong();
        }

        public void close() throws IOException
        {
            file_.close();
        }
    }

    public static class BufferReader extends Reader
    {
        private int size_;

        BufferReader(String filename, int size) throws IOException
        {
            super(filename);
            size_ = size;
        }

        protected void init(String filename) throws IOException
        {
            file_ = new BufferedRandomAccessFile(filename, "r", size_);
        }
    }

    public static class ChecksumReader extends Reader
    {
        private int size_;

        ChecksumReader(String filename, int size) throws IOException
        {
            super(filename);
            size_ = size;
        }

        protected void init(String filename) throws IOException
        {
            file_ = new ChecksumRandomAccessFile(filename, "r", size_);
        }
    }

    private static Logger logger_ = Logger.getLogger(SequenceFile.class);
    public static final short utfPrefix_ = 2;
    public static final String marker_ = "Bloom-Filter";

    public static IFileWriter writer(String filename) throws IOException
    {
        return new Writer(filename);
    }

    public static IFileWriter bufferedWriter(String filename, int size) throws IOException
    {
        return new BufferWriter(filename, size);
    }

    public static IFileWriter fastWriter(String filename, int size) throws IOException
    {
        return new FastConcurrentWriter(filename, size);
    }

    public static IFileReader reader(String filename) throws IOException
    {
        return new Reader(filename);
    }

    public static IFileReader bufferedReader(String filename, int size) throws IOException
    {
        return new BufferReader(filename, size);
    }

    /**
     * Efficiently writes a UTF8 string to the buffer.
     * Assuming all Strings that are passed in have length
     * that can be represented as a short i.e length of the
     * string is <= 65535
     *
     * @param buffer buffer to write the serialize version into
     * @param str    string to serialize
     */
    protected static void writeUTF(ByteBuffer buffer, String str)
    {
        int strlen = str.length();
        int utflen = 0;
        int c, count = 0;

        /* use charAt instead of copying String to char array */
        for (int i = 0; i < strlen; i++)
        {
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F))
            {
                utflen++;
            }
            else if (c > 0x07FF)
            {
                utflen += 3;
            }
            else
            {
                utflen += 2;
            }
        }

        byte[] bytearr = new byte[utflen + 2];
        bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
        bytearr[count++] = (byte) ((utflen >>> 0) & 0xFF);

        int i = 0;
        for (i = 0; i < strlen; i++)
        {
            c = str.charAt(i);
            if (!((c >= 0x0001) && (c <= 0x007F)))
                break;
            bytearr[count++] = (byte) c;
        }

        for (; i < strlen; i++)
        {
            c = str.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F))
            {
                bytearr[count++] = (byte) c;

            }
            else if (c > 0x07FF)
            {
                bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
                bytearr[count++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            }
            else
            {
                bytearr[count++] = (byte) (0xC0 | ((c >> 6) & 0x1F));
                bytearr[count++] = (byte) (0x80 | ((c >> 0) & 0x3F));
            }
        }
        buffer.put(bytearr, 0, utflen + 2);
    }

}
