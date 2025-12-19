package com.example.restservice.writers;

import com.example.restservice.sipbuilder.SipBuilder;
import java.util.function.Function;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import reactor.core.publisher.Mono;

public interface SipOutputWriter {
  Mono<Mono<Void>> startWriter(Function<ZipArchiveOutputStream, SipBuilder> builderFactory);
}
