package com.example.restservice.PgipSipStream;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.roda_project.commons_ip2.model.IPConstants;

public class SipFileProcessor2 {

  private final MessageDigest digest;
  private long size = 0;

  public SipFileProcessor2() {
    try {
      this.digest = MessageDigest.getInstance(IPConstants.CHECKSUM_SHA_256_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException("Unsupported digest algorithm", e);
    }
  }

  public void update(final byte[] bytes) {
    digest.update(bytes, 0, bytes.length);
    size += bytes.length;
  }

  public byte[] getDigest() {
    return digest.digest();
  }

  public long getSize() {
    return size;
  }
}
