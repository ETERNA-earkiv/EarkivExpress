package com.example.restservice.sipbuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class FileOutputTarget implements SipBuilderOutputTarget {

  private final File outputFile;
  private PipedInputStream pipedInputStream;

  public FileOutputTarget(final File outputFile) {
    this.outputFile = outputFile;
  }

  @Override
  public ZipArchiveOutputStream createZipArchiveOutputStream() {
    try {
      PipedOutputStream out = new PipedOutputStream();
      pipedInputStream = new PipedInputStream(out, 64 * 1024);
      return new ZipArchiveOutputStream(out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Mono<Void> write() {
    return Mono.fromRunnable(() -> {
      try (OutputStream os = new FileOutputStream(outputFile)) {
        //PipeForwarder.pipe(in, os);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }).subscribeOn(Schedulers.boundedElastic()).then();
  }
}
