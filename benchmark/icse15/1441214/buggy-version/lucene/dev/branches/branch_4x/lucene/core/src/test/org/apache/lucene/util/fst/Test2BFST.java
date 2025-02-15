  Merged /lucene/dev/trunk/lucene/CHANGES.txt:r1441213
package org.apache.lucene.util.fst;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.Arrays;
import java.util.Random;

import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TimeUnits;
import org.apache.lucene.util.packed.PackedInts;
import org.junit.Ignore;
import com.carrotsearch.randomizedtesting.annotations.TimeoutSuite;

@Ignore("Requires tons of heap to run (10G works)")
@TimeoutSuite(millis = 100 * TimeUnits.HOUR)
public class Test2BFST extends LuceneTestCase {

  private static long LIMIT = 3L*1024*1024*1024;

  public void test() throws Exception {
    int[] ints = new int[7];
    IntsRef input = new IntsRef(ints, 0, ints.length);
    long seed = random().nextLong();

    for(int doPackIter=0;doPackIter<2;doPackIter++) {
      boolean doPack = doPackIter == 1;

      // Build FST w/ NoOutputs and stop when nodeCount > 3B
      if (!doPack) {
        System.out.println("\nTEST: 3B nodes; doPack=false output=NO_OUTPUTS");
        Outputs<Object> outputs = NoOutputs.getSingleton();
        Object NO_OUTPUT = outputs.getNoOutput();
        final Builder<Object> b = new Builder<Object>(FST.INPUT_TYPE.BYTE1, 0, 0, false, false, Integer.MAX_VALUE, outputs,
                                                      null, doPack, PackedInts.COMPACT, true, 15);

        int count = 0;
        Random r = new Random(seed);
        int[] ints2 = new int[200];
        IntsRef input2 = new IntsRef(ints2, 0, ints2.length);
        while(true) {
          //System.out.println("add: " + input + " -> " + output);
          for(int i=10;i<ints2.length;i++) {
            ints2[i] = r.nextInt(256);
          }
          b.add(input2, NO_OUTPUT);
          count++;
          if (count % 100000 == 0) {
            System.out.println(count + ": " + b.fstSizeInBytes() + " bytes; " + b.getTotStateCount() + " nodes");
          }
          if (b.getTotStateCount() > LIMIT) {
            break;
          }
          nextInput(r, ints2);
        }

        FST<Object> fst = b.finish();

        System.out.println("\nTEST: now verify [fst size=" + fst.sizeInBytes() + "; nodeCount=" + fst.getNodeCount() + "; arcCount=" + fst.getArcCount() + "]");

        Arrays.fill(ints2, 0);
        r = new Random(seed);

        for(int i=0;i<count;i++) {
          if (i % 1000000 == 0) {
            System.out.println(i + "...: ");
          }
          for(int j=10;j<ints2.length;j++) {
            ints2[j] = r.nextInt(256);
          }
          assertEquals(NO_OUTPUT, Util.get(fst, input2));
          nextInput(r, ints2);
        }

        System.out.println("\nTEST: enum all input/outputs");
        IntsRefFSTEnum<Object> fstEnum = new IntsRefFSTEnum<Object>(fst);

        Arrays.fill(ints2, 0);
        r = new Random(seed);
        int upto = 0;
        while(true) {
          IntsRefFSTEnum.InputOutput<Object> pair = fstEnum.next();
          if (pair == null) {
            break;
          }
          for(int j=10;j<ints2.length;j++) {
            ints2[j] = r.nextInt(256);
          }
          assertEquals(input2, pair.input);
          assertEquals(NO_OUTPUT, pair.output);
          upto++;
          nextInput(r, ints2);
        }
        assertEquals(count, upto);
      }

      // Build FST w/ ByteSequenceOutputs and stop when FST
      // size = 3GB
      {
        System.out.println("\nTEST: 3 GB size; doPack=" + doPack + " outputs=bytes");
        Outputs<BytesRef> outputs = ByteSequenceOutputs.getSingleton();
        final Builder<BytesRef> b = new Builder<BytesRef>(FST.INPUT_TYPE.BYTE1, 0, 0, true, true, Integer.MAX_VALUE, outputs,
                                                          null, doPack, PackedInts.COMPACT, true, 15);

        byte[] outputBytes = new byte[20];
        BytesRef output = new BytesRef(outputBytes);
        Arrays.fill(ints, 0);
        int count = 0;
        Random r = new Random(seed);
        while(true) {
          r.nextBytes(outputBytes);
          //System.out.println("add: " + input + " -> " + output);
          b.add(input, BytesRef.deepCopyOf(output));
          count++;
          if (count % 1000000 == 0) {
            System.out.println(count + "...: " + b.fstSizeInBytes() + " bytes");
          }
          if (b.fstSizeInBytes() > LIMIT) {
            break;
          }
          nextInput(r, ints);
        }

        FST<BytesRef> fst = b.finish();

        System.out.println("\nTEST: now verify [fst size=" + fst.sizeInBytes() + "; nodeCount=" + fst.getNodeCount() + "; arcCount=" + fst.getArcCount() + "]");

        r = new Random(seed);
        Arrays.fill(ints, 0);

        for(int i=0;i<count;i++) {
          if (i % 1000000 == 0) {
            System.out.println(i + "...: ");
          }
          r.nextBytes(outputBytes);
          assertEquals(output, Util.get(fst, input));
          nextInput(r, ints);
        }

        System.out.println("\nTEST: enum all input/outputs");
        IntsRefFSTEnum<BytesRef> fstEnum = new IntsRefFSTEnum<BytesRef>(fst);

        Arrays.fill(ints, 0);
        r = new Random(seed);
        int upto = 0;
        while(true) {
          IntsRefFSTEnum.InputOutput<BytesRef> pair = fstEnum.next();
          if (pair == null) {
            break;
          }
          assertEquals(input, pair.input);
          r.nextBytes(outputBytes);
          assertEquals(output, pair.output);
          upto++;
          nextInput(r, ints);
        }
        assertEquals(count, upto);
      }

      // Build FST w/ PositiveIntOutputs and stop when FST
      // size = 3GB
      {
        System.out.println("\nTEST: 3 GB size; doPack=" + doPack + " outputs=long");
        Outputs<Long> outputs = PositiveIntOutputs.getSingleton();
        final Builder<Long> b = new Builder<Long>(FST.INPUT_TYPE.BYTE1, 0, 0, true, true, Integer.MAX_VALUE, outputs,
                                                  null, doPack, PackedInts.COMPACT, true, 15);

        long output = 1;

        Arrays.fill(ints, 0);
        int count = 0;
        Random r = new Random(seed);
        while(true) {
          //System.out.println("add: " + input + " -> " + output);
          b.add(input, output);
          output += 1+r.nextInt(10);
          count++;
          if (count % 1000000 == 0) {
            System.out.println(count + "...: " + b.fstSizeInBytes() + " bytes");
          }
          if (b.fstSizeInBytes() > LIMIT) {
            break;
          }
          nextInput(r, ints);
        }

        FST<Long> fst = b.finish();

        System.out.println("\nTEST: now verify [fst size=" + fst.sizeInBytes() + "; nodeCount=" + fst.getNodeCount() + "; arcCount=" + fst.getArcCount() + "]");

        Arrays.fill(ints, 0);

        output = 1;
        r = new Random(seed);
        for(int i=0;i<count;i++) {
          if (i % 1000000 == 0) {
            System.out.println(i + "...: ");
          }

          // forward lookup:
          assertEquals(output, Util.get(fst, input).longValue());
          // reverse lookup:
          assertEquals(input, Util.getByOutput(fst, output));
          output += 1 + r.nextInt(10);
          nextInput(r, ints);
        }

        System.out.println("\nTEST: enum all input/outputs");
        IntsRefFSTEnum<Long> fstEnum = new IntsRefFSTEnum<Long>(fst);

        Arrays.fill(ints, 0);
        r = new Random(seed);
        int upto = 0;
        output = 1;
        while(true) {
          IntsRefFSTEnum.InputOutput<Long> pair = fstEnum.next();
          if (pair == null) {
            break;
          }
          assertEquals(input, pair.input);
          assertEquals(output, pair.output.longValue());
          output += 1 + r.nextInt(10);
          upto++;
          nextInput(r, ints);
        }
        assertEquals(count, upto);
      }
    }
  }

  private void nextInput(Random r, int[] ints) {
    int downTo = 6;
    while(downTo >= 0) {
      // Must add random amounts (and not just 1) because
      // otherwise FST outsmarts us and remains tiny:
      ints[downTo] += 1+r.nextInt(10);
      if (ints[downTo] < 256) {
        break;
      } else {
        ints[downTo] = 0;
        downTo--;
      }
    }
  }
}
