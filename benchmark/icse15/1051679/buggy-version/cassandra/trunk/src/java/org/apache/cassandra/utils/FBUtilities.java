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

package org.apache.cassandra.utils;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.base.Charsets;
import org.apache.commons.collections.iterators.CollatingIterator;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import org.apache.cassandra.config.ConfigurationException;
import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.DecoratedKey;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.dht.IPartitioner;
import org.apache.cassandra.dht.Range;
import org.apache.cassandra.dht.Token;
import org.apache.cassandra.locator.AbstractReplicationStrategy;
import org.apache.cassandra.locator.PropertyFileSnitch;
import org.apache.thrift.TBase;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;

public class FBUtilities
{
    private static Logger logger_ = LoggerFactory.getLogger(FBUtilities.class);

    public static final BigInteger TWO = new BigInteger("2");

    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.wrap(ArrayUtils.EMPTY_BYTE_ARRAY);
    
    private static volatile InetAddress localInetAddress_;

    public static final int MAX_UNSIGNED_SHORT = 0xFFFF;

    /**
     * Parses a string representing either a fraction, absolute value or percentage.
     */
    public static double parseDoubleOrPercent(String value)
    {
        if (value.endsWith("%"))
        {
            return Double.parseDouble(value.substring(0, value.length() - 1)) / 100;
        }
        else
        {
            return Double.parseDouble(value);
        }
    }

    public static InetAddress getLocalAddress()
    {
        if (localInetAddress_ == null)
            try
            {
                localInetAddress_ = DatabaseDescriptor.getListenAddress() == null
                                    ? InetAddress.getLocalHost()
                                    : DatabaseDescriptor.getListenAddress();
            }
            catch (UnknownHostException e)
            {
                throw new RuntimeException(e);
            }
        return localInetAddress_;
    }

    /**
     * @param fractOrAbs A double that may represent a fraction or absolute value.
     * @param total If fractionOrAbs is a fraction, the total to take the fraction from
     * @return An absolute value which may be larger than the total.
     */
    public static long absoluteFromFraction(double fractOrAbs, long total)
    {
        if (fractOrAbs < 0)
            throw new UnsupportedOperationException("unexpected negative value " + fractOrAbs);

        if (0 < fractOrAbs && fractOrAbs <= 1)
        {
            // fraction
            return Math.max(1, (long)(fractOrAbs * total));
        }

        // absolute
        assert fractOrAbs >= 1 || fractOrAbs == 0;
        return (long)fractOrAbs;
    }

    /**
     * Given two bit arrays represented as BigIntegers, containing the given
     * number of significant bits, calculate a midpoint.
     *
     * @param left The left point.
     * @param right The right point.
     * @param sigbits The number of bits in the points that are significant.
     * @return A midpoint that will compare bitwise halfway between the params, and
     * a boolean representing whether a non-zero lsbit remainder was generated.
     */
    public static Pair<BigInteger,Boolean> midpoint(BigInteger left, BigInteger right, int sigbits)
    {
        BigInteger midpoint;
        boolean remainder;
        if (left.compareTo(right) < 0)
        {
            BigInteger sum = left.add(right);
            remainder = sum.testBit(0);
            midpoint = sum.shiftRight(1);
        }
        else
        {
            BigInteger max = TWO.pow(sigbits);
            // wrapping case
            BigInteger distance = max.add(right).subtract(left);
            remainder = distance.testBit(0);
            midpoint = distance.shiftRight(1).add(left).mod(max);
        }
        return new Pair(midpoint, remainder);
    }

    public static ByteBuffer toByteBuffer(int i)
    {
        byte[] bytes = new byte[4];
        bytes[0] = (byte)( ( i >>> 24 ) & 0xFF);
        bytes[1] = (byte)( ( i >>> 16 ) & 0xFF);
        bytes[2] = (byte)( ( i >>> 8 ) & 0xFF);
        bytes[3] = (byte)( i & 0xFF );
        return ByteBuffer.wrap(bytes);
    }

    public static int byteBufferToInt(ByteBuffer bytes)
    {
        if (bytes.remaining() < 4 )
        {
            throw new IllegalArgumentException("An integer must be 4 bytes in size.");
        }
        int n = 0;
        for ( int i = 0; i < 4; ++i )
        {
            n <<= 8;
            n |= bytes.array()[bytes.position() + bytes.arrayOffset() + i] & 0xFF;
        }
        return n;
    }

    public static int compareUnsigned(byte[] bytes1, byte[] bytes2, int offset1, int offset2, int len1, int len2)
    {
        if (bytes1 == null)
        {
            return bytes2 == null ? 0 : -1;
        }
        if (bytes2 == null) return 1;

        int minLength = Math.min(len1 - offset1, len2 - offset2);
        for (int x = 0, i = offset1, j = offset2; x < minLength; x++, i++, j++)
        {
            if (bytes1[i] == bytes2[j])
                continue;
            // compare non-equal bytes as unsigned
            return (bytes1[i] & 0xFF) < (bytes2[j] & 0xFF) ? -1 : 1;
        }
        if ((len1 - offset1) == (len2 - offset2)) return 0;
        else return ((len1 - offset1) < (len2 - offset2)) ? -1 : 1;
    }

    /**
     * @return The bitwise XOR of the inputs. The output will be the same length as the
     * longer input, but if either input is null, the output will be null.
     */
    public static byte[] xor(byte[] left, byte[] right)
    {
        if (left == null || right == null)
            return null;
        if (left.length > right.length)
        {
            byte[] swap = left;
            left = right;
            right = swap;
        }

        // left.length is now <= right.length
        byte[] out = Arrays.copyOf(right, right.length);
        for (int i = 0; i < left.length; i++)
        {
            out[i] = (byte)((left[i] & 0xFF) ^ (right[i] & 0xFF));
        }
        return out;
    }

    public static BigInteger md5hash(ByteBuffer data)
    {
        byte[] result = hash("MD5", data);
        BigInteger hash = new BigInteger(result);
        return hash.abs();        
    }

    public static byte[] hash(String type, ByteBuffer... data)
    {
    	byte[] result;
    	try
        {
            MessageDigest messageDigest = MessageDigest.getInstance(type);
            for(ByteBuffer block : data)
                messageDigest.update(block.array(),block.position()+block.arrayOffset(),block.remaining());
            result = messageDigest.digest();
    	}
    	catch (Exception e)
        {
            throw new RuntimeException(e);
    	}
    	return result;
	}

    public static void writeByteArray(ByteBuffer bytes, DataOutput out) throws IOException
    {
        out.writeInt(bytes.remaining());
        out.write(bytes.array(),bytes.position()+bytes.arrayOffset(),bytes.remaining());
    }

    public static ByteBuffer readByteArray(DataInput in) throws IOException
    {
        int length = in.readInt();
        if (length < 0)
        {
            throw new IOException("Corrupt (negative) value length encountered");
        }
       
        ByteBuffer bb = ByteBuffer.allocate(length);
        if (length > 0)
        {
            in.readFully(bb.array(),bb.position(),bb.remaining());
        }
        return bb;
    }

    public static void writeShortByteArray(ByteBuffer name, DataOutput out)
    {
        int length = name.remaining();
        assert 0 <= length && length <= MAX_UNSIGNED_SHORT;
        try
        {
            out.writeByte((length >> 8) & 0xFF);
            out.writeByte(length & 0xFF);
            out.write(name.array(), name.position()+name.arrayOffset(), name.remaining());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    

    /** @return An unsigned short in an integer. */
    private static int readShortLength(DataInput in) throws IOException
    {
        int length = (in.readByte() & 0xFF) << 8;
        return length | (in.readByte() & 0xFF);
    }

    public static ByteBuffer readShortByteArray(DataInput in) throws IOException
    {
        int length = readShortLength(in);
        ByteBuffer bb = ByteBuffer.allocate(length);
        in.readFully(bb.array(),bb.position(),bb.remaining());
        return bb;
    }

    /** @return null */
    public static byte[] skipShortByteArray(DataInput in) throws IOException
    {
        int skip = readShortLength(in);
        while (skip > 0)
        {
            int skipped = in.skipBytes(skip);
            if (skipped == 0) throw new EOFException();
            skip -= skipped;
        }
        return null;
    }

    public static byte[] hexToBytes(String str)
    {
        if (str.length() % 2 == 1)
            str = "0" + str;
        byte[] bytes = new byte[str.length()/2];
        for (int i = 0; i < bytes.length; i++)
        {
            bytes[i] = (byte)Integer.parseInt(str.substring(i*2, i*2+2), 16);
        }
        return bytes;
    }

    public static String bytesToHex(byte... bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
        {
            int bint = b & 0xff;
            if (bint <= 0xF)
                // toHexString does not 0 pad its results.
                sb.append("0");
            sb.append(Integer.toHexString(bint));
        }
        return sb.toString();
    }

    public static String bytesToHex(ByteBuffer bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (int i=bytes.position()+bytes.arrayOffset(); i<bytes.limit()+bytes.arrayOffset(); i++)
        {
            int bint = bytes.array()[i] & 0xff;
            if (bint <= 0xF)
                // toHexString does not 0 pad its results.
                sb.append("0");
            sb.append(Integer.toHexString(bint));
        }
        return sb.toString();
    }

    public static void renameWithConfirm(String tmpFilename, String filename) throws IOException
    {
        if (!new File(tmpFilename).renameTo(new File(filename)))
        {
            throw new IOException("rename failed of " + filename);
        }
    }

    /*
    TODO how to make this work w/ ReducingKeyIterator?
    public static <T extends Comparable<T>> CollatingIterator getCollatingIterator()
    {
        // CollatingIterator will happily NPE if you do not specify a comparator explicitly
        return new CollatingIterator(new Comparator<T>()
        {
            public int compare(T o1, T o2)
            {
                return o1.compareTo(o2);
            }
        });
    }
     */
    public static CollatingIterator getCollatingIterator()
    {
        // CollatingIterator will happily NPE if you do not specify a comparator explicitly
        return new CollatingIterator(new Comparator()
        {
            public int compare(Object o1, Object o2)
            {
                return ((Comparable) o1).compareTo(o2);
            }
        });
    }

    public static void atomicSetMax(AtomicInteger atomic, int i)
    {
        while (true)
        {
            int j = atomic.get();
            if (j >= i || atomic.compareAndSet(j, i))
                break;
        }
    }

    public static void atomicSetMax(AtomicLong atomic, long i)
    {
        while (true)
        {
            long j = atomic.get();
            if (j >= i || atomic.compareAndSet(j, i))
                break;
        }
    }

    public static void serialize(TSerializer serializer, TBase struct, DataOutput out)
    throws IOException
    {
        assert serializer != null;
        assert struct != null;
        assert out != null;
        byte[] bytes;
        try
        {
            bytes = serializer.serialize(struct);
        }
        catch (TException e)
        {
            throw new RuntimeException(e);
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static void deserialize(TDeserializer deserializer, TBase struct, DataInput in)
    throws IOException
    {
        assert deserializer != null;
        assert struct != null;
        assert in != null;
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        try
        {
            deserializer.deserialize(struct, bytes);
        }
        catch (TException ex)
        {
            throw new IOException(ex);
        }
    }

    public static void sortSampledKeys(List<DecoratedKey> keys, Range range)
    {
        if (range.left.compareTo(range.right) >= 0)
        {
            // range wraps.  have to be careful that we sort in the same order as the range to find the right midpoint.
            final Token right = range.right;
            Comparator<DecoratedKey> comparator = new Comparator<DecoratedKey>()
            {
                public int compare(DecoratedKey o1, DecoratedKey o2)
                {
                    if ((right.compareTo(o1.token) < 0 && right.compareTo(o2.token) < 0)
                        || (right.compareTo(o1.token) > 0 && right.compareTo(o2.token) > 0))
                    {
                        // both tokens are on the same side of the wrap point
                        return o1.compareTo(o2);
                    }
                    return -o1.compareTo(o2);
                }
            };
            Collections.sort(keys, comparator);
        }
        else
        {
            // unwrapped range (left < right).  standard sort is all we need.
            Collections.sort(keys);
        }
    }

    public static int encodedUTF8Length(String st)
    {
        int strlen = st.length();
        int utflen = 0;
        for (int i = 0; i < strlen; i++)
        {
            int c = st.charAt(i);
            if ((c >= 0x0001) && (c <= 0x007F))
                utflen++;
            else if (c > 0x07FF)
                utflen += 3;
            else
                utflen += 2;
        }
        return utflen;
    }

    public static String decodeToUTF8(ByteBuffer bytes) throws CharacterCodingException
    {
        bytes = bytes.duplicate();
        String decoded  =  Charsets.UTF_8.newDecoder().decode(bytes).toString();
        return decoded;
    }

    public static ByteBuffer toByteBuffer(long n)
    {
        byte[] bytes = new byte[8];
        ByteBuffer bb = ByteBuffer.wrap(bytes).putLong(n);
        bb.rewind();
        return bb;
    }

    public static String resourceToFile(String filename) throws ConfigurationException
    {
        ClassLoader loader = PropertyFileSnitch.class.getClassLoader();
        URL scpurl = loader.getResource(filename);
        if (scpurl == null)
            throw new ConfigurationException("unable to locate " + filename);

        return scpurl.getFile();
    }

    public static String getReleaseVersionString()
    {
        try
        {
            InputStream in = FBUtilities.class.getClassLoader().getResourceAsStream("org/apache/cassandra/config/version.properties");
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("CassandraVersion");
        }
        catch (Exception e)
        {
            logger_.warn("Unable to load version.properties", e);
            return "debug version";
        }
    }

    public static long timestampMicros()
    {
        // we use microsecond resolution for compatibility with other client libraries, even though
        // we can't actually get microsecond precision.
        return System.currentTimeMillis() * 1000;
    }

    public static void waitOnFutures(Iterable<Future<?>> futures)
    {
        for (Future f : futures)
        {
            try
            {
                f.get();
            }
            catch (ExecutionException ee)
            {
                throw new RuntimeException(ee);
            }
            catch (InterruptedException ie)
            {
                throw new AssertionError(ie);
            }
        }
    }

    public static IPartitioner newPartitioner(String partitionerClassName) throws ConfigurationException
    {
        if (!partitionerClassName.contains("."))
            partitionerClassName = "org.apache.cassandra.dht." + partitionerClassName;
        return FBUtilities.<IPartitioner>construct(partitionerClassName, "partitioner");
    }

    public static AbstractType getComparator(String compareWith) throws ConfigurationException
    {
        String className = compareWith.contains(".") ? compareWith : "org.apache.cassandra.db.marshal." + compareWith;
        Class<? extends AbstractType> typeClass = FBUtilities.<AbstractType>classForName(className, "abstract-type");
        try
        {
            Field field = typeClass.getDeclaredField("instance");
            return (AbstractType) field.get(null);
        }
        catch (NoSuchFieldException e)
        {
            ConfigurationException ex = new ConfigurationException("Invalid comparator " + compareWith + " : must define a public static instance field.");
            ex.initCause(e);
            throw ex;
        }
        catch (IllegalAccessException e)
        {
            ConfigurationException ex = new ConfigurationException("Invalid comparator " + compareWith + " : must define a public static instance field.");
            ex.initCause(e);
            throw ex;
        }
    }

    /**
     * @return The Class for the given name.
     * @param classname Fully qualified classname.
     * @param readable Descriptive noun for the role the class plays.
     * @throws ConfigurationException If the class cannot be found.
     */
    public static <T> Class<T> classForName(String classname, String readable) throws ConfigurationException
    {
        try
        {
            return (Class<T>)Class.forName(classname);
        }
        catch (ClassNotFoundException e)
        {
            throw new ConfigurationException(String.format("Unable to find %s class '%s'", readable, classname));
        }
    }

    /**
     * Constructs an instance of the given class, which must have a no-arg constructor.
     * TODO: Similar method for our 'instance member' singleton pattern would be nice.
     * @param classname Fully qualified classname.
     * @param readable Descriptive noun for the role the class plays.
     * @throws ConfigurationException If the class cannot be found.
     */
    public static <T> T construct(String classname, String readable) throws ConfigurationException
    {
        Class<T> cls = FBUtilities.<T>classForName(classname, readable);
        try
        {
            Constructor ctor = cls.getConstructor();
            return (T)ctor.newInstance();
        }
        catch (NoSuchMethodException e)
        {
            throw new ConfigurationException(String.format("No default constructor for %s class '%s'.", readable, classname));
        }
        catch (IllegalAccessException e)
        {
            throw new ConfigurationException(String.format("Default constructor for %s class '%s' is inaccessible.", readable, classname));
        }
        catch (InstantiationException e)
        {
            throw new ConfigurationException(String.format("Cannot use abstract class '%s' as %s.", classname, readable));
        }
        catch (InvocationTargetException e)
        {
            if (e.getCause() instanceof ConfigurationException)
                throw (ConfigurationException)e.getCause();
            throw new ConfigurationException(String.format("Error instantiating %s class '%s'.", readable, classname), e);
        }
    }

    public static <T extends Comparable> SortedSet<T> singleton(T column)
    {
        return new TreeSet<T>(Arrays.asList(column));
    }

    public static String toString(Map<?,?> map)
    {
        // wtf, why isn't something like this in guava or commons collections?
        StringBuilder sb = new StringBuilder("{");
        for (Map.Entry<?,?> entry : map.entrySet())
        {
            sb.append(toString(entry.getKey())).append(": ").append(toString(entry.getValue())).append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    /** slow! */
    private static Object toString(Object o)
    {
        return o.getClass().isArray() ? Arrays.toString((Object[]) o) : o.toString();
    }
}
