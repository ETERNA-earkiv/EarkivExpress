package com.example.restservice.PgipSipStream;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class SipFileProcessor implements StreamProcessor {
  private final ZipOutputStream zipOutputStream;
  private final String entryName;
  private final MessageDigest digest;

  private long size = 0;

  public long getSize() {
    return size;
  }

  public SipFileProcessor(ZipOutputStream zipOutputStream, String entryName) {
    this.zipOutputStream = zipOutputStream;
    this.entryName = entryName;

    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public byte[] getDigest() {
    return digest.digest();
  }

  @Override
  public OutputStream wrap(OutputStream out) throws IOException {
    ZipEntry zipEntry = newZipEntry(this.entryName);
    zipOutputStream.putNextEntry(zipEntry);

    return new OutputStream() {
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
        zipOutputStream.closeEntry();
      }
    };
  }

  private ZipEntry newZipEntry(final String entryName) {
    ZipEntry zipEntry = new ZipEntry(entryName);

    // Use DEFLATED instead of STORED as it sets the ZipEntry's general purpose bit flags
    // 4th bit which means that the uncompressed size, compressed size and crc-32 are stored
    // in a data descriptor after the file data instead of storing it in the local file
    // header that precedes the data. This could be fixed with a new ZipOutputStream
    // implementation. Currently, we set the entire zip-files compression level to 0 which
    // means that all files are store decompressed no matter what the specific files ZipEntry
    // method is set to.
    zipEntry.setMethod(ZipEntry.DEFLATED);
    return zipEntry;
  }
}
