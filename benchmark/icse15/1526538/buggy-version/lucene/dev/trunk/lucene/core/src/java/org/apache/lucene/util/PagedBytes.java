package org.apache.lucene.util;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.IndexInput;

/** Represents a logical byte[] as a series of pages.  You
 *  can write-once into the logical byte[] (append only),
 *  using copy, and then retrieve slices (BytesRef) into it
 *  using fill.
 *
 * @lucene.internal
 **/
// TODO: refactor this, byteblockpool, fst.bytestore, and any
// other "shift/mask big arrays". there are too many of these classes!
public final class PagedBytes {
  private final List<byte[]> blocks = new ArrayList<byte[]>();
  // TODO: these are unused?
  private final List<Integer> blockEnd = new ArrayList<Integer>();
  private final int blockSize;
  private final int blockBits;
  private final int blockMask;
  private boolean didSkipBytes;
  private boolean frozen;
  private int upto;
  private byte[] currentBlock;
  private final long bytesUsedPerBlock;

  private static final byte[] EMPTY_BYTES = new byte[0];

  /** Provides methods to read BytesRefs from a frozen
   *  PagedBytes.
   *
   * @see #freeze */
  public final static class Reader {
    private final byte[][] blocks;
    private final int[] blockEnds;
    private final int blockBits;
    private final int blockMask;
    private final int blockSize;

    private Reader(PagedBytes pagedBytes) {
      blocks = new byte[pagedBytes.blocks.size()][];
      for(int i=0;i<blocks.length;i++) {
        blocks[i] = pagedBytes.blocks.get(i);
      }
      blockEnds = new int[blocks.length];
      for(int i=0;i< blockEnds.length;i++) {
        blockEnds[i] = pagedBytes.blockEnd.get(i);
      }
      blockBits = pagedBytes.blockBits;
      blockMask = pagedBytes.blockMask;
      blockSize = pagedBytes.blockSize;
    }

    /**
     * Gets a slice out of {@link PagedBytes} starting at <i>start</i> with a
     * given length. Iff the slice spans across a block border this method will
     * allocate sufficient resources and copy the paged data.
     * <p>
     * Slices spanning more than two blocks are not supported.
     * </p>
     * @lucene.internal 
     **/
    public void fillSlice(BytesRef b, long start, int length) {
      assert length >= 0: "length=" + length;
      assert length <= blockSize+1: "length=" + length;
      final int index = (int) (start >> blockBits);
      final int offset = (int) (start & blockMask);
      b.length = length;
      if (blockSize - offset >= length) {
        // Within block
        b.bytes = blocks[index];
        b.offset = offset;
      } else {
        // Split
        b.bytes = new byte[length];
        b.offset = 0;
        System.arraycopy(blocks[index], offset, b.bytes, 0, blockSize-offset);
        System.arraycopy(blocks[1+index], 0, b.bytes, blockSize-offset, length-(blockSize-offset));
      }
    }
    
    /**
     * Reads length as 1 or 2 byte vInt prefix, starting at <i>start</i>.
     * <p>
     * <b>Note:</b> this method does not support slices spanning across block
     * borders.
     * </p>
     * 
     * @lucene.internal
     **/
    // TODO: this really needs to be refactored into fieldcacheimpl
    public void fill(BytesRef b, long start) {
      final int index = (int) (start >> blockBits);
      final int offset = (int) (start & blockMask);
      final byte[] block = b.bytes = blocks[index];

      if ((block[offset] & 128) == 0) {
        b.length = block[offset];
        b.offset = offset+1;
      } else {
        b.length = ((block[offset] & 0x7f) << 8) | (block[1+offset] & 0xff);
        b.offset = offset+2;
        assert b.length > 0;
      }
    }

    /** Returns approximate RAM bytes used */
    public long ramBytesUsed() {
      return ((blocks!=null) ? (blockSize * blocks.length) : 0);
    }
  }

  /** 1&lt;&lt;blockBits must be bigger than biggest single
   *  BytesRef slice that will be pulled */
  public PagedBytes(int blockBits) {
    assert blockBits > 0 && blockBits <= 31 : blockBits;
    this.blockSize = 1 << blockBits;
    this.blockBits = blockBits;
    blockMask = blockSize-1;
    upto = blockSize;
    bytesUsedPerBlock = blockSize + RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + RamUsageEstimator.NUM_BYTES_OBJECT_REF;
  }

  /** Read this many bytes from in */
  public void copy(IndexInput in, long byteCount) throws IOException {
    while (byteCount > 0) {
      int left = blockSize - upto;
      if (left == 0) {
        if (currentBlock != null) {
          blocks.add(currentBlock);
          blockEnd.add(upto);
        }
        currentBlock = new byte[blockSize];
        upto = 0;
        left = blockSize;
      }
      if (left < byteCount) {
        in.readBytes(currentBlock, upto, left, false);
        upto = blockSize;
        byteCount -= left;
      } else {
        in.readBytes(currentBlock, upto, (int) byteCount, false);
        upto += byteCount;
        break;
      }
    }
  }

  /** Copy BytesRef in, setting BytesRef out to the result.
   * Do not use this if you will use freeze(true).
   * This only supports bytes.length <= blockSize */
  public void copy(BytesRef bytes, BytesRef out) {
    int left = blockSize - upto;
    if (bytes.length > left || currentBlock==null) {
      if (currentBlock != null) {
        blocks.add(currentBlock);
        blockEnd.add(upto);
        didSkipBytes = true;
      }
      currentBlock = new byte[blockSize];
      upto = 0;
      left = blockSize;
      assert bytes.length <= blockSize;
      // TODO: we could also support variable block sizes
    }

    out.bytes = currentBlock;
    out.offset = upto;
    out.length = bytes.length;

    System.arraycopy(bytes.bytes, bytes.offset, currentBlock, upto, bytes.length);
    upto += bytes.length;
  }

  /** Commits final byte[], trimming it if necessary and if trim=true */
  public Reader freeze(boolean trim) {
    if (frozen) {
      throw new IllegalStateException("already frozen");
    }
    if (didSkipBytes) {
      throw new IllegalStateException("cannot freeze when copy(BytesRef, BytesRef) was used");
    }
    if (trim && upto < blockSize) {
      final byte[] newBlock = new byte[upto];
      System.arraycopy(currentBlock, 0, newBlock, 0, upto);
      currentBlock = newBlock;
    }
    if (currentBlock == null) {
      currentBlock = EMPTY_BYTES;
    }
    blocks.add(currentBlock);
    blockEnd.add(upto); 
    frozen = true;
    currentBlock = null;
    return new PagedBytes.Reader(this);
  }

  public long getPointer() {
    if (currentBlock == null) {
      return 0;
    } else {
      return (blocks.size() * ((long) blockSize)) + upto;
    }
  }

  /** Return approx RAM usage in bytes. */
  public long ramBytesUsed() {
    return (blocks.size() + (currentBlock != null ? 1 : 0)) * bytesUsedPerBlock;
  }

  /** Copy bytes in, writing the length as a 1 or 2 byte
   *  vInt prefix. */
  // TODO: this really needs to be refactored into fieldcacheimpl!
  public long copyUsingLengthPrefix(BytesRef bytes) {
    if (bytes.length >= 32768) {
      throw new IllegalArgumentException("max length is 32767 (got " + bytes.length + ")");
    }

    if (upto + bytes.length + 2 > blockSize) {
      if (bytes.length + 2 > blockSize) {
        throw new IllegalArgumentException("block size " + blockSize + " is too small to store length " + bytes.length + " bytes");
      }
      if (currentBlock != null) {
        blocks.add(currentBlock);
        blockEnd.add(upto);        
      }
      currentBlock = new byte[blockSize];
      upto = 0;
    }

    final long pointer = getPointer();

    if (bytes.length < 128) {
      currentBlock[upto++] = (byte) bytes.length;
    } else {
      currentBlock[upto++] = (byte) (0x80 | (bytes.length >> 8));
      currentBlock[upto++] = (byte) (bytes.length & 0xff);
    }
    System.arraycopy(bytes.bytes, bytes.offset, currentBlock, upto, bytes.length);
    upto += bytes.length;

    return pointer;
  }

  public final class PagedBytesDataInput extends DataInput {
    private int currentBlockIndex;
    private int currentBlockUpto;
    private byte[] currentBlock;

    PagedBytesDataInput() {
      currentBlock = blocks.get(0);
    }

    @Override
    public PagedBytesDataInput clone() {
      PagedBytesDataInput clone = getDataInput();
      clone.setPosition(getPosition());
      return clone;
    }

    /** Returns the current byte position. */
    public long getPosition() {
      return (long) currentBlockIndex * blockSize + currentBlockUpto;
    }
  
    /** Seek to a position previously obtained from
     *  {@link #getPosition}. */
    public void setPosition(long pos) {
      currentBlockIndex = (int) (pos >> blockBits);
      currentBlock = blocks.get(currentBlockIndex);
      currentBlockUpto = (int) (pos & blockMask);
    }

    @Override
    public byte readByte() {
      if (currentBlockUpto == blockSize) {
        nextBlock();
      }
      return currentBlock[currentBlockUpto++];
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) {
      assert b.length >= offset + len;
      final int offsetEnd = offset + len;
      while (true) {
        final int blockLeft = blockSize - currentBlockUpto;
        final int left = offsetEnd - offset;
        if (blockLeft < left) {
          System.arraycopy(currentBlock, currentBlockUpto,
                           b, offset,
                           blockLeft);
          nextBlock();
          offset += blockLeft;
        } else {
          // Last block
          System.arraycopy(currentBlock, currentBlockUpto,
                           b, offset,
                           left);
          currentBlockUpto += left;
          break;
        }
      }
    }

    private void nextBlock() {
      currentBlockIndex++;
      currentBlockUpto = 0;
      currentBlock = blocks.get(currentBlockIndex);
    }
  }

  public final class PagedBytesDataOutput extends DataOutput {
    @Override
    public void writeByte(byte b) {
      if (upto == blockSize) {
        if (currentBlock != null) {
          blocks.add(currentBlock);
          blockEnd.add(upto);
        }
        currentBlock = new byte[blockSize];
        upto = 0;
      }
      currentBlock[upto++] = b;
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) {
      assert b.length >= offset + length;
      if (length == 0) {
        return;
      }

      if (upto == blockSize) {
        if (currentBlock != null) {
          blocks.add(currentBlock);
          blockEnd.add(upto);
        }
        currentBlock = new byte[blockSize];
        upto = 0;
      }
          
      final int offsetEnd = offset + length;
      while(true) {
        final int left = offsetEnd - offset;
        final int blockLeft = blockSize - upto;
        if (blockLeft < left) {
          System.arraycopy(b, offset, currentBlock, upto, blockLeft);
          blocks.add(currentBlock);
          blockEnd.add(blockSize);
          currentBlock = new byte[blockSize];
          upto = 0;
          offset += blockLeft;
        } else {
          // Last block
          System.arraycopy(b, offset, currentBlock, upto, left);
          upto += left;
          break;
        }
      }
    }

    /** Return the current byte position. */
    public long getPosition() {
      return getPointer();
    }
  }

  /** Returns a DataInput to read values from this
   *  PagedBytes instance. */
  public PagedBytesDataInput getDataInput() {
    if (!frozen) {
      throw new IllegalStateException("must call freeze() before getDataInput");
    }
    return new PagedBytesDataInput();
  }

  /** Returns a DataOutput that you may use to write into
   *  this PagedBytes instance.  If you do this, you should
   *  not call the other writing methods (eg, copy);
   *  results are undefined. */
  public PagedBytesDataOutput getDataOutput() {
    if (frozen) {
      throw new IllegalStateException("cannot get DataOutput after freeze()");
    }
    return new PagedBytesDataOutput();
  }
}
