/*
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

package org.apache.paimon.disk;

import org.apache.paimon.data.BinaryRow;
import org.apache.paimon.data.InternalRow;
import org.apache.paimon.data.RandomAccessInputView;
import org.apache.paimon.data.SimpleCollectingOutputView;
import org.apache.paimon.data.serializer.AbstractRowDataSerializer;
import org.apache.paimon.memory.MemorySegment;
import org.apache.paimon.memory.MemorySegmentPool;
import org.apache.paimon.utils.MutableObjectIterator;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;

/** Only cache {@link InternalRow}s in memory. */
public class InMemoryBuffer implements RowBuffer {

    private final AbstractRowDataSerializer<InternalRow> serializer;
    private final ArrayList<MemorySegment> recordBufferSegments;
    private final SimpleCollectingOutputView recordCollector;
    private final MemorySegmentPool pool;
    private final int segmentSize;

    private long currentDataBufferOffset;
    private int numBytesInLastBuffer;
    private int numRecords = 0;

    InMemoryBuffer(MemorySegmentPool pool, AbstractRowDataSerializer<InternalRow> serializer) {
        // serializer has states, so we must duplicate
        this.serializer = (AbstractRowDataSerializer<InternalRow>) serializer.duplicate();
        this.pool = pool;
        this.segmentSize = pool.pageSize();
        this.recordBufferSegments = new ArrayList<>();
        this.recordCollector =
                new SimpleCollectingOutputView(this.recordBufferSegments, pool, segmentSize);
    }

    @Override
    public void reset() {
        this.currentDataBufferOffset = 0;
        this.numRecords = 0;
        returnToSegmentPool();
        this.recordCollector.reset();
    }

    private void returnToSegmentPool() {
        pool.returnAll(this.recordBufferSegments);
        this.recordBufferSegments.clear();
    }

    @Override
    public boolean put(InternalRow row) throws IOException {
        try {
            this.serializer.serializeToPages(row, this.recordCollector);
            currentDataBufferOffset = this.recordCollector.getCurrentOffset();
            numBytesInLastBuffer = this.recordCollector.getCurrentPositionInSegment();
            numRecords++;
            return true;
        } catch (EOFException e) {
            return false;
        }
    }

    @Override
    public int size() {
        return numRecords;
    }

    @Override
    public void complete() {}

    @Override
    public long memoryOccupancy() {
        return currentDataBufferOffset;
    }

    @Override
    public InMemoryBufferIterator newIterator() {
        RandomAccessInputView recordBuffer =
                new RandomAccessInputView(
                        this.recordBufferSegments, segmentSize, numBytesInLastBuffer);
        return new InMemoryBufferIterator(recordBuffer, serializer);
    }

    ArrayList<MemorySegment> getRecordBufferSegments() {
        return recordBufferSegments;
    }

    long getCurrentDataBufferOffset() {
        return currentDataBufferOffset;
    }

    int getNumRecordBuffers() {
        int result = (int) (currentDataBufferOffset / segmentSize);
        long mod = currentDataBufferOffset % segmentSize;
        if (mod != 0) {
            result += 1;
        }
        return result;
    }

    int getNumBytesInLastBuffer() {
        return numBytesInLastBuffer;
    }

    AbstractRowDataSerializer<InternalRow> getSerializer() {
        return serializer;
    }

    private static class InMemoryBufferIterator
            implements RowBufferIterator, MutableObjectIterator<BinaryRow> {

        private final RandomAccessInputView recordBuffer;
        private final AbstractRowDataSerializer<InternalRow> serializer;
        private final BinaryRow reuse;
        private BinaryRow row;

        private InMemoryBufferIterator(
                RandomAccessInputView recordBuffer,
                AbstractRowDataSerializer<InternalRow> serializer) {
            this.recordBuffer = recordBuffer;
            this.serializer = serializer;
            this.reuse = new BinaryRow(serializer.getArity());
        }

        @Override
        public boolean advanceNext() {
            try {
                row = next(reuse);
                return row != null;
            } catch (IOException ioException) {
                throw new RuntimeException(ioException);
            }
        }

        @Override
        public BinaryRow getRow() {
            return row;
        }

        @Override
        public BinaryRow next(BinaryRow reuse) throws IOException {
            try {
                return (BinaryRow) serializer.mapFromPages(reuse, recordBuffer);
            } catch (EOFException e) {
                return null;
            }
        }

        @Override
        public BinaryRow next() throws IOException {
            return next(reuse);
        }

        @Override
        public void close() {}
    }
}
