package com.example.restservice.writers;

import com.example.restservice.PgipSipStream.DataBufferOutputStream;
import com.example.restservice.eterna.ApiClient;
import com.example.restservice.eterna.EternaConfig;
import com.example.restservice.exception.UploadFailureException;
import com.example.restservice.sipbuilder.SipBuilder;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class EternaTransferredResourceWriter implements SipOutputWriter {

  private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

  private final EternaConfig eternaConfig;
  private ZipArchiveOutputStream zipArchiveOutputStream;
  private Flux<DataBuffer> dataBufferFlux;

  public EternaTransferredResourceWriter(EternaConfig eternaConfig) {
    this.eternaConfig = eternaConfig;
  }

  public Flux<DataBuffer> test() {
    DataBufferOutputStream outputStream = new DataBufferOutputStream(BUFFER_FACTORY);
    zipArchiveOutputStream = new ZipArchiveOutputStream(outputStream);
    zipArchiveOutputStream.setLevel(0);

    dataBufferFlux = Flux.create(outputStream::setSink);
    return dataBufferFlux;
  }

  @Override
  public Mono<Mono<Void>> startWriter(Function<ZipArchiveOutputStream, SipBuilder> builderFactory) {
    if (zipArchiveOutputStream == null) {
      throw new RuntimeException("ZipArchiveOutputStream is null");
    }

    builderFactory.apply(zipArchiveOutputStream);

    Executor executor =
        command -> Schedulers.boundedElastic().schedule(command);

    ApiClient apiClient = eternaConfig.createApiClient();
    WebClient webClient = apiClient.getWebClient();

    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("upl", BodyInserters.fromDataBuffers(dataBufferFlux))
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
        .filename("test.zip");

    MultiValueMap<String, HttpEntity<?>> multipartBody = builder.build();

    Mono<Void> uploadMono =
        webClient.post()
            .uri("/controller/v1/transfers/")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(BodyInserters.fromMultipartData(multipartBody))
            .retrieve()
            .bodyToMono(Void.class)
            .onErrorMap(UploadFailureException::new);

    return Mono.just(uploadMono);
  }
}
