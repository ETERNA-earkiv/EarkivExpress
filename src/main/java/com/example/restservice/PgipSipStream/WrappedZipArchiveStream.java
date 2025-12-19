package com.example.restservice.PgipSipStream;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

public class WrappedZipArchiveStream extends OutputStream {

  private final ZipArchiveOutputStream zipOutputStream;
  private final MessageDigest digest;

  private long size = 0;

  public WrappedZipArchiveStream(ZipArchiveOutputStream zipOutputStream) {
    this.zipOutputStream = zipOutputStream;

    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public long getSize() {
    return size;
  }

  public byte[] getDigest() {
    return digest.digest();
  }

  @Override
  public void write(int b) throws IOException {
    size++;
    digest.update((byte) b);
    zipOutputStream.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    size += len;
    digest.update(b, off, len);
    zipOutputStream.write(b, off, len);
  }

  @Override
  public void flush() throws IOException {
    zipOutputStream.flush();
  }

  @Override
  public void close() throws IOException {
    zipOutputStream.closeArchiveEntry();
  }
}
