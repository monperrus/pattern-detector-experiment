package org.apache.lucene.document;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * Provides support for converting longs to Strings, and back again. The strings
 * are structured so that lexicographic sorting order is preserved.
 * 
 * <p>
 * That is, if l1 is less than l2 for any two longs l1 and l2, then
 * LongField.longToString(l1) is lexicographically less than
 * LongField.longToString(l2). (Similarly for "greater than" and "equals".)
 * 
 * <p>
 * This class handles <b>all</b> long values (unlike
 * {@link org.apache.lucene.document.DateField}).
 * 
 * @author Matt Quail (spud at madbean dot com)
 */
public class NumberTools {

    private static final int RADIX = 36;

    private static final char NEGATIVE_PREFIX = '-';

    // NB: NEGATIVE_PREFIX must be < POSITIVE_PREFIX
    private static final char POSITIVE_PREFIX = '0';

    //NB: this must be less than
    /**
     * Equivalent to longToString(Long.MIN_VALUE)
     */
    public static final String MIN_STRING_VALUE = NEGATIVE_PREFIX
            + "0000000000000";

    /**
     * Equivalent to longToString(Long.MAX_VALUE)
     */
    public static final String MAX_STRING_VALUE = POSITIVE_PREFIX
            + "1y2p0ij32e8e7";

    /**
     * the length of (all) strings returned by {@link #longToString}
     */
    public static final int STR_SIZE = MIN_STRING_VALUE.length();

    /**
     * Converts a long to a String suitable for indexing.
     */
    public static String longToString(long l) {

        if (l == Long.MIN_VALUE) {
            // special case, because long is not symetric around zero
            return MIN_STRING_VALUE;
        }

        StringBuffer buf = new StringBuffer(STR_SIZE);

        if (l < 0) {
            buf.append(NEGATIVE_PREFIX);
            l = Long.MAX_VALUE + l + 1;
        } else {
            buf.append(POSITIVE_PREFIX);
        }
        String num = Long.toString(l, RADIX);

        int padLen = STR_SIZE - num.length() - buf.length();
        while (padLen-- > 0) {
            buf.append('0');
        }
        buf.append(num);

        return buf.toString();
    }

    /**
     * Converts a String that was returned by {@link #longToString}back to a
     * long.
     * 
     * @throws IllegalArgumentException
     *             if the input is null
     * @throws NumberFormatException
     *             if the input does not parse (it was not a String returned by
     *             longToString()).
     */
    public static long stringToLong(String str) {
        if (str == null) {
            throw new IllegalArgumentException("string cannot be null");
        }
        if (str.length() != STR_SIZE) {
            throw new NumberFormatException("string is the wrong size");
        }

        if (str.equals(MIN_STRING_VALUE)) {
            return Long.MIN_VALUE;
        }

        char prefix = str.charAt(0);
        long l = Long.parseLong(str.substring(1), RADIX);

        if (prefix == POSITIVE_PREFIX) {
            // nop
        } else if (prefix == NEGATIVE_PREFIX) {
            l = l - Long.MAX_VALUE - 1;
        } else {
            throw new NumberFormatException(
                    "string does not begin with the correct prefix");
        }

        return l;
    }
}
