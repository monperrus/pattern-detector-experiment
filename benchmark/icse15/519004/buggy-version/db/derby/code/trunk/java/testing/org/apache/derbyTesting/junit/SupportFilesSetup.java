/*
 *
 * Derby - Class org.apache.derbyTesting.junit.SupportFilesSetup
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an 
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, 
 * either express or implied. See the License for the specific 
 * language governing permissions and limitations under the License.
 */
package org.apache.derbyTesting.junit;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;

import junit.extensions.TestSetup;
import junit.framework.Test;

/**
 * A decorator that copies test resources from the classpath
 * into the file system at setUp time. Resources are named
 * relative to org/apache/derbyTesting/, e.g. the name
 * passed into the constructor should be something like
 * funtionTests/test/lang/mytest.sql
 * <BR>
 * Read only resources are placed into ${user.dir}/extin/name
 * Read-write resources are placed into ${user.dir}/extinout/name
 * write only output files can be created in ${user.dir}/extout
 * 
 * These locations map to entries in the test policy file that
 * have restricted permissions granted.
 * 
 * All the three folders are created even if no files are
 * copied into them.
 * 
 * In each case the name of a file is the base name of the resource,
 * no package structure is retained.
 * 
 * A test may access such a resource using either files or URLs.
 * The static utility methods must be used to obtain the location
 * of any resource. In the future this decorator may create sub-folders
 * to ensure that tests run in paralled do not interfere with each other.
 * 
 * tearDown removes the three folders and their contents.
 * 
 */
public class SupportFilesSetup extends TestSetup {
    
    private String[] readOnly;
    private String[] readWrite;

    /**
     * Create all the folders but don't copy any resources.
     */
    public SupportFilesSetup(Test test)
    {
        this(test, (String[]) null, (String[]) null);
    }

    /**
     * Create all the folders and copy a set of resources into
     * the read only folder.
     */
    public SupportFilesSetup(Test test, String[] readOnly)
    {
        this(test, readOnly, (String[]) null);
    }
    
    /**
     * Create all the folders, copy a set of resources into
     * the read only folder and copy a set of resources into
     * the read write folder.
   */
    public SupportFilesSetup(Test test, String[] readOnly, String[] readWrite)
    {
        super(test);
        this.readOnly = readOnly;
        this.readWrite = readWrite;
    }
    
    protected void setUp() throws PrivilegedActionException, IOException
    {
        privCopyFiles("extin", readOnly);
        privCopyFiles("extinout", readWrite);
        privCopyFiles("extout", (String[]) null);
    }
    
    protected void tearDown()
    {
        DropDatabaseSetup.removeDirectory("extin");
        DropDatabaseSetup.removeDirectory("extinout");
        DropDatabaseSetup.removeDirectory("extout");
    }
    
    private void privCopyFiles(final String dirName, final String[] resources)
    throws PrivilegedActionException
    {
        AccessController.doPrivileged
        (new java.security.PrivilegedExceptionAction(){
            
            public Object run() throws IOException, PrivilegedActionException { 
              copyFiles(dirName, resources);
              return null;
            }
        });

    }
    
    private void copyFiles(String dirName, String[] resources)
        throws PrivilegedActionException, IOException
    {
        File dir = new File(dirName);
        dir.mkdir();
        
        if (resources == null)
            return;

        for (int i = 0; i < resources.length; i++)
        {
            String name =
                "org/apache/derbyTesting/".concat(resources[i]);
            
            String baseName = name.substring(name.lastIndexOf('/') + 1);
            
            
            URL url = BaseTestCase.getTestResource(name);
            assertNotNull(name, url);
            
            InputStream in = BaseTestCase.openTestResource(url);
            
            File copy = new File(dir, baseName);
            copy.delete();
            
            OutputStream out = new FileOutputStream(copy);
            
            byte[] buf = new byte[32*1024];
            
            for (;;) {
                int read = in.read(buf);
                if (read == -1)
                    break;
                out.write(buf, 0, read);
            }
            in.close();
            out.flush();
            out.close();
        }
    }
    
    /**
     * Obtain the URL to the local copy of a read-only resource.
     * @param name Base name for the resouce.
     */
    public static URL getReadOnlyURL(String name) throws MalformedURLException
    {
        return getURL(getReadOnly(name));
    }
    /**
     * Obtain the URL to the local copy of a read-write resource.
     * @param name Base name for the resouce.
     */
    public static URL getReadWriteURL(String name) throws MalformedURLException
    {
        return getURL(getReadWrite(name));
    }
    /**
     * Obtain the URL to the local copy of a write-only resource.
     * @param name Base name for the resouce.
     */
    public static URL getWriteOnlyURL(String name) throws MalformedURLException
    {
        return getURL(getWriteOnly(name));
    }
    
    
    /**
     * Obtain a File for the local copy of a read-only resource.
     * @param name Base name for the resouce.
     */
    public static File getReadOnly(String name)
    {
        return getFile("extin", name);
    }
    /**
     * Obtain a File for the local copy of a read-write resource.
     * @param name Base name for the resouce.
     */
    public static File getReadWrite(String name)
    {
        return getFile("extinout", name);
    }
    /**
     * Obtain a File for the local copy of a write-only resource.
     * @param name Base name for the resouce.
     */
    public static File getWriteOnly(String name)
    {
        return getFile("extout", name);
    }
    
    private static File getFile(String dirName, String name)
    {
        File dir = new File(dirName);
        return new File(dir, name);
    }
    
    private static URL getURL(final File file) throws MalformedURLException
    {
        try {
            return (URL) AccessController.doPrivileged
            (new java.security.PrivilegedExceptionAction(){

                public Object run() throws MalformedURLException{
                return file.toURL();

                }
            }
             );
        } catch (PrivilegedActionException e) {
            throw (MalformedURLException) e.getException();
        } 
    }
}
