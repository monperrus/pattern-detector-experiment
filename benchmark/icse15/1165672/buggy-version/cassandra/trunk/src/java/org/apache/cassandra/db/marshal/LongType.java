package org.apache.cassandra.db.marshal;
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


import java.nio.ByteBuffer;

import org.apache.cassandra.cql.jdbc.LongTerm;
import org.apache.cassandra.utils.ByteBufferUtil;

public class LongType extends AbstractType<Long>
{
    public static final LongType instance = new LongType();

    LongType() {} // singleton

    public Long compose(ByteBuffer bytes)
    {
        return LongTerm.instance.compose(bytes);
    }

    public ByteBuffer decompose(Long value)
    {
        return ByteBufferUtil.bytes(value);
    }

    public int compare(ByteBuffer o1, ByteBuffer o2)
    {
        if (o1.remaining() == 0)
        {
            return o2.remaining() == 0 ? 0 : -1;
        }
        if (o2.remaining() == 0)
        {
            return 1;
        }

        int diff = o1.get(o1.position()) - o2.get(o2.position());
        if (diff != 0)
            return diff;
        
       
        return ByteBufferUtil.compareUnsigned(o1, o2);
    }

    public String getString(ByteBuffer bytes)
    {
        try
        {
            return LongTerm.instance.getString(bytes);
        }
        catch (org.apache.cassandra.cql.jdbc.MarshalException e)
        {
            throw new MarshalException(e.getMessage());
        }
    }

    public ByteBuffer fromString(String source) throws MarshalException
    {
        // Return an empty ByteBuffer for an empty string.
        if (source.isEmpty())
            return ByteBufferUtil.EMPTY_BYTE_BUFFER;

        long longType;

        try
        {
            longType = Long.parseLong(source);
        }
        catch (Exception e)
        {
            throw new MarshalException(String.format("unable to make long from '%s'", source), e);
        }

        return decompose(longType);
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        if (bytes.remaining() != 8 && bytes.remaining() != 0)
            throw new MarshalException(String.format("Expected 8 or 0 byte long (%d)", bytes.remaining()));
    }
}
