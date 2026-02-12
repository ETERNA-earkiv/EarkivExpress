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
import org.springframework.core.io.buffer.DataBufferUtils;
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

    System.out.println("Starting ETERNA upload - collecting data");
    // Collect all DataBuffers into a single Flux and convert to byte array
    Mono<byte[]> dataMono = dataBufferFlux
        .doOnNext(buffer -> System.out.println("Received data buffer: " + buffer.readableByteCount() + " bytes"))
        .collectList()
        .doOnNext(buffers -> System.out.println("Collected " + buffers.size() + " data buffers"))
        .map(dataBuffers -> {
            int totalSize = dataBuffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
            byte[] result = new byte[totalSize];
            int offset = 0;
            for (DataBuffer buffer : dataBuffers) {
                int length = buffer.readableByteCount();
                buffer.read(result, offset, length);
                offset += length;
                DataBufferUtils.release(buffer);
            }
            System.out.println("Collected " + totalSize + " bytes for upload");
            return result;
        });

    Mono<Void> uploadMono = dataMono.flatMap(data -> {
        System.out.println("Sending SIP data to ETERNA (" + data.length + " bytes)");
        return webClient.post()
            .uri("/controller/v1/transfers/")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("upl", data))
            .retrieve()
            .bodyToMono(Void.class)
            .doOnSuccess(v -> System.out.println("ETERNA upload completed successfully"))
            .doOnError(e -> System.err.println("ETERNA upload failed: " + e.getMessage()));
    }).onErrorMap(UploadFailureException::new);

    return Mono.just(uploadMono);
  }
}
