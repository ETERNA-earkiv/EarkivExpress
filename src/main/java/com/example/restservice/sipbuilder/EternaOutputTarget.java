package com.example.restservice.sipbuilder;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import reactor.core.publisher.Mono;

public class EternaOutputTarget implements SipBuilderOutputTarget {

  @Override
  public ZipArchiveOutputStream createZipArchiveOutputStream() {
    return null;
  }

  @Override
  public Mono<Void> write() {
    return null;
  }
}
