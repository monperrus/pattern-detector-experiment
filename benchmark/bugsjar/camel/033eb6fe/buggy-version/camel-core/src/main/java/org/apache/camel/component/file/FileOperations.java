/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File operations for {@link java.io.File}.
 */
public class FileOperations implements GenericFileOperations<File> {
    private static final transient Logger LOG = LoggerFactory.getLogger(FileOperations.class);
    private FileEndpoint endpoint;

    public FileOperations() {
    }

    public FileOperations(FileEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void setEndpoint(GenericFileEndpoint<File> endpoint) {
        this.endpoint = (FileEndpoint) endpoint;
    }

    public boolean deleteFile(String name) throws GenericFileOperationFailedException {
        File file = new File(name);
        return FileUtil.deleteFile(file);
    }

    public boolean renameFile(String from, String to) throws GenericFileOperationFailedException {
        File file = new File(from);
        File target = new File(to);
        try {
            return FileUtil.renameFile(file, target, endpoint.isCopyAndDeleteOnRenameFail());
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Error renaming file from " + from + " to " + to, e);
        }
    }

    public boolean existsFile(String name) throws GenericFileOperationFailedException {
        File file = new File(name);
        return file.exists();
    }

    public boolean buildDirectory(String directory, boolean absolute) throws GenericFileOperationFailedException {
        ObjectHelper.notNull(endpoint, "endpoint");

        // always create endpoint defined directory
        if (endpoint.isAutoCreate() && !endpoint.getFile().exists()) {
            LOG.trace("Building starting directory: {}", endpoint.getFile());
            endpoint.getFile().mkdirs();
        }

        if (ObjectHelper.isEmpty(directory)) {
            // no directory to build so return true to indicate ok
            return true;
        }

        File endpointPath = endpoint.getFile();
        File target = new File(directory);

        File path;
        if (absolute) {
            // absolute path
            path = target;
        } else if (endpointPath.equals(target)) {
            // its just the root of the endpoint path
            path = endpointPath;
        } else {
            // relative after the endpoint path
            String afterRoot = ObjectHelper.after(directory, endpointPath.getPath() + File.separator);
            if (ObjectHelper.isNotEmpty(afterRoot)) {
                // dir is under the root path
                path = new File(endpoint.getFile(), afterRoot);
            } else {
                // dir is relative to the root path
                path = new File(endpoint.getFile(), directory);
            }
        }

        // We need to make sure that this is thread-safe and only one thread tries to create the path directory at the same time.
        synchronized (this) {
            if (path.isDirectory() && path.exists()) {
                // the directory already exists
                return true;
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Building directory: {}", path);
                }
                return path.mkdirs();
            }
        }
    }

    public List<File> listFiles() throws GenericFileOperationFailedException {
        // noop
        return null;
    }

    public List<File> listFiles(String path) throws GenericFileOperationFailedException {
        // noop
        return null;
    }

    public void changeCurrentDirectory(String path) throws GenericFileOperationFailedException {
        // noop
    }

    public void changeToParentDirectory() throws GenericFileOperationFailedException {
        // noop
    }

    public String getCurrentDirectory() throws GenericFileOperationFailedException {
        // noop
        return null;
    }

    public boolean retrieveFile(String name, Exchange exchange) throws GenericFileOperationFailedException {
        // noop as we use type converters to read the body content for java.io.File
        return true;
    }

    public boolean storeFile(String fileName, Exchange exchange) throws GenericFileOperationFailedException {
        ObjectHelper.notNull(endpoint, "endpoint");

        File file = new File(fileName);

        // if an existing file already exists what should we do?
        if (file.exists()) {
            if (endpoint.getFileExist() == GenericFileExist.Ignore) {
                // ignore but indicate that the file was written
                LOG.trace("An existing file already exists: {}. Ignore and do not override it.", file);
                return true;
            } else if (endpoint.getFileExist() == GenericFileExist.Fail) {
                throw new GenericFileOperationFailedException("File already exist: " + file + ". Cannot write new file.");
            }
        }

        // we can write the file by 3 different techniques
        // 1. write file to file
        // 2. rename a file from a local work path
        // 3. write stream to file
        try {

            // is the body file based
            File source = null;
            // get the File Object from in message
            source = exchange.getIn().getBody(File.class);

            if (source != null) {
                // okay we know the body is a file type

                // so try to see if we can optimize by renaming the local work path file instead of doing
                // a full file to file copy, as the local work copy is to be deleted afterwards anyway
                // local work path
                File local = exchange.getIn().getHeader(Exchange.FILE_LOCAL_WORK_PATH, File.class);
                if (local != null && local.exists()) {
                    boolean renamed = writeFileByLocalWorkPath(local, file);
                    if (renamed) {
                        // try to keep last modified timestamp if configured to do so
                        keepLastModified(exchange, file);
                        // clear header as we have renamed the file
                        exchange.getIn().setHeader(Exchange.FILE_LOCAL_WORK_PATH, null);
                        // return as the operation is complete, we just renamed the local work file
                        // to the target.
                        return true;
                    }
                } else if (source.exists()) {
                    // no there is no local work file so use file to file copy if the source exists
                    writeFileByFile(source, file);
                    // try to keep last modified timestamp if configured to do so
                    keepLastModified(exchange, file);
                    return true;
                }
            }

            // fallback and use stream based
            InputStream in = exchange.getIn().getMandatoryBody(InputStream.class);
            writeFileByStream(in, file);
            // try to keep last modified timestamp if configured to do so
            keepLastModified(exchange, file);
            return true;
        } catch (IOException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + file, e);
        } catch (InvalidPayloadException e) {
            throw new GenericFileOperationFailedException("Cannot store file: " + file, e);
        }
    }

    private void keepLastModified(Exchange exchange, File file) {
        if (endpoint.isKeepLastModified()) {
            Long last;
            Date date = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Date.class);
            if (date != null) {
                last = date.getTime();
            } else {
                // fallback and try a long
                last = exchange.getIn().getHeader(Exchange.FILE_LAST_MODIFIED, Long.class);
            }
            if (last != null) {
                boolean result = file.setLastModified(last);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Keeping last modified timestamp: {} on file: {} with result: {}", new Object[]{last, file, result});
                }
            }
        }
    }

    private boolean writeFileByLocalWorkPath(File source, File file) throws IOException {
        LOG.trace("Using local work file being renamed from: {} to: {}", source, file);

        return FileUtil.renameFile(source, file, endpoint.isCopyAndDeleteOnRenameFail());
    }

    private void writeFileByFile(File source, File target) throws IOException {
        FileChannel in = new FileInputStream(source).getChannel();
        FileChannel out = null;
        try {
            out = prepareOutputFileChannel(target, out);
            LOG.trace("Using FileChannel to transfer from: {} to: {}", in, out);
            long size = in.size();
            long position = 0;
            while (position < size) {
                position += in.transferTo(position, endpoint.getBufferSize(), out);
            }
        } finally {
            IOHelper.close(in, source.getName(), LOG);
            // force updates to be written, and then close afterwards
            IOHelper.force(out, target.getName(), LOG);
            IOHelper.close(out, target.getName(), LOG);
        }
    }

    private void writeFileByStream(InputStream in, File target) throws IOException {
        FileChannel out = null;
        try {
            out = prepareOutputFileChannel(target, out);
            LOG.trace("Using InputStream to transfer from: {} to: {}", in, out);
            int size = endpoint.getBufferSize();
            byte[] buffer = new byte[size];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (bytesRead < size) {
                    byteBuffer.limit(bytesRead);
                }
                out.write(byteBuffer);
                byteBuffer.clear();
            }
        } finally {
            IOHelper.close(in, target.getName(), LOG);
            // force updates to be written, and then close afterwards
            IOHelper.force(out, target.getName(), LOG);
            IOHelper.close(out, target.getName(), LOG);
        }
    }

    /**
     * Creates and prepares the output file channel. Will position itself in correct position if the file is writable
     * eg. it should append or override any existing content.
     */
    private FileChannel prepareOutputFileChannel(File target, FileChannel out) throws IOException {
        if (endpoint.getFileExist() == GenericFileExist.Append) {
            out = new RandomAccessFile(target, "rw").getChannel();
            out = out.position(out.size());
        } else {
            // will override
            out = new FileOutputStream(target).getChannel();
        }
        return out;
    }
}
