package com.example.restservice.PgipSipStream;

import java.io.IOException;
import java.io.OutputStream;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.FluxSink;

public class DataBufferOutputStream extends OutputStream {

  private static final int DEFAULT_CHUNK_SIZE = 64 * 1024;

  private final DataBufferFactory bufferFactory;
  private FluxSink<DataBuffer> sink;
  private final int chunkSize;

  private DataBuffer currentBuffer;
  private int position = 0;
  private boolean closed = false;

  public DataBufferOutputStream(DataBufferFactory bufferFactory, FluxSink<DataBuffer> sink) {
    this.bufferFactory = bufferFactory;
    this.sink = sink;
    this.chunkSize = DEFAULT_CHUNK_SIZE;
    this.currentBuffer = bufferFactory.allocateBuffer(chunkSize);
  }

  public DataBufferOutputStream(DataBufferFactory bufferFactory) {
    this.bufferFactory = bufferFactory;
    this.chunkSize = DEFAULT_CHUNK_SIZE;
    this.currentBuffer = bufferFactory.allocateBuffer(chunkSize);
  }

  public void setSink(FluxSink<DataBuffer> sink) {
    this.sink = sink;
  }

  @Override
  public void write(int b) throws IOException {
    ensureCapacity(1);
    currentBuffer.write((byte) b);
    position++;
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    int remaining = len;
    int offset = off;
    while (remaining > 0) {
      int writable = Math.min(chunkSize - position, remaining);
      ensureCapacity(writable);
      currentBuffer.write(b, offset, writable);
      position += writable;
      remaining -= writable;
      offset += writable;
    }
  }

  private void ensureCapacity(int needed) {
    if (position + needed > chunkSize) {
      flushCurrent();
    }
  }

  private void flushCurrent() {
    if (sink == null) {
      throw new RuntimeException("DataBufferOutputStream sink is null");
    }

    if (position > 0) {
      sink.next(currentBuffer);
      currentBuffer = bufferFactory.allocateBuffer(chunkSize);
      position = 0;
    }
  }

  @Override
  public void flush() {
    flushCurrent();
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    flushCurrent();
    DataBufferUtils.release(currentBuffer);
    closed = true;
  }
}
