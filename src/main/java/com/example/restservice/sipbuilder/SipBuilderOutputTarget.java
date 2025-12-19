package com.example.restservice.sipbuilder;

import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import reactor.core.publisher.Mono;

public interface SipBuilderOutputTarget {
  ZipArchiveOutputStream createZipArchiveOutputStream();
  Mono<Void> write();
}
