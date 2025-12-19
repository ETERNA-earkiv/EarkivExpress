package com.example.restservice.sipbuilder;

import com.example.restservice.model.upload.Job;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.roda_project.commons_ip2.model.IPConstants;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBuffer;
import org.springframework.core.io.buffer.NettyDataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.PartEvent;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.Queues;

public class SipBuilder {

  private static final String PGIP_METADATA_TYPE = "pgip";
  private static final String PGIP_VERSION = "1.3";
  private static final String PGIP_FILENAME = PGIP_METADATA_TYPE + "_" + PGIP_VERSION + ".xml";
  private static final String PGIP_SCHEMA = PGIP_METADATA_TYPE + "_" + PGIP_VERSION + ".xsd";
  private static final String REPRESENTATION_NAME = "rep1";
  private static final String REPRESENTATION_FOLDER =
      IPConstants.REPRESENTATIONS_FOLDER + REPRESENTATION_NAME + IPConstants.METS_PATH_SEPARATOR;

  private Job job;
  private ZipArchiveOutputStream zipArchiveOutputStream;

  public void setZipArchiveOutputStream(final ZipArchiveOutputStream zipArchiveOutputStream) {
    this.zipArchiveOutputStream = zipArchiveOutputStream;
  }

  public void startZipEntry(final String entryName) throws IOException {
    final ZipArchiveEntry zipEntry = new ZipArchiveEntry(entryName);
    zipArchiveOutputStream.putArchiveEntry(zipEntry);
    zipArchiveOutputStream.flush();
  }

  public Mono<Void> processJob(PartEvent firstPart, Flux<PartEvent> partEvents) {
    System.out.println("Processing job");

    Flux<DataBuffer> contents = partEvents.map(PartEvent::content);
    ObjectMapper objectMapper = new ObjectMapper();

    return DataBufferUtils.join(contents)
        .map(content -> {
          final HttpHeaders headers = firstPart.headers();
          final MediaType contentType = headers.getContentType();
          final Charset charset =
              contentType != null && contentType.getCharset() != null ? contentType.getCharset()
                  : StandardCharsets.UTF_8;

          final String value = content.toString(charset);
          DataBufferUtils.release(content);

          try {
            job = objectMapper.readValue(value, Job.class);
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }

          System.out.println("Job: " + value);
          return Mono.empty();
        })
        .then();
  }

  public Mono<Void> processPgip(PartEvent firstPart, Flux<PartEvent> partEvents) {
    System.out.println("Processing PGIP");

    try {
      startZipEntry(IPConstants.DESCRIPTIVE_FOLDER + PGIP_FILENAME);
    } catch (IOException e) {
      return Mono.error(e);
    }

    return partEvents
        .map(PartEvent::content)
        .filter(Objects::nonNull)
        .concatMap(dataBuffer -> Mono.fromRunnable(() -> {
              try {
                writeDataBufferToZip(dataBuffer);
              } catch (IOException e) {
                throw new RuntimeException(e);
              } finally {
                DataBufferUtils.release(dataBuffer);
              }
            })
            .subscribeOn(Schedulers.boundedElastic()))
        .doOnComplete(() -> {
          try {
            zipArchiveOutputStream.closeArchiveEntry();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        })
        .then();
  }

  public Mono<Void> processFile(PartEvent firstPart, Flux<PartEvent> partEvents) {
    if (!(firstPart instanceof FilePartEvent filePartEvent)) {
      return Mono.error(new IllegalStateException("Multipart field 'file' not sent as a file"));
    }

    System.out.println("Processing file \"" + filePartEvent.filename() + "\"");
    try {
      final String fileName = filePartEvent.filename();
      final String filePath = IPConstants.DATA_FOLDER + fileName;
      final String entryName = REPRESENTATION_FOLDER + filePath;
      startZipEntry(entryName);
    } catch (IOException e) {
      return Mono.error(e);
    }

    return partEvents
        .map(PartEvent::content)
        .filter(Objects::nonNull)
        .concatMap(dataBuffer -> Mono.fromRunnable(() -> {
              try {
                writeDataBufferToZip(dataBuffer);
              } catch (IOException e) {
                throw new RuntimeException(e);
              } finally {
                DataBufferUtils.release(dataBuffer);
              }
            })
            .subscribeOn(Schedulers.boundedElastic())
        )
        .doOnComplete(() -> {
          System.out.println("File read completed");
          try {
            zipArchiveOutputStream.closeArchiveEntry();
            zipArchiveOutputStream.finish();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        })
        .doOnError(err -> {
          // propagate error into group so it can clean up
          System.out.println("Error reading file");
          throw new RuntimeException("Error reading file");
        })
        .doFinally((signalType) -> {
          try {
            zipArchiveOutputStream.close();
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        })
        .then();
  }

  private void writeDataBufferToZip(DataBuffer dataBuffer) throws IOException {
    if (dataBuffer instanceof NettyDataBuffer nettyDataBuffer) {
      io.netty.buffer.ByteBuf nettyByteBuffer = nettyDataBuffer.getNativeBuffer();
      int readable = nettyByteBuffer.readableBytes();
      if (readable == 0) {
        return;
      }

      if (nettyByteBuffer.hasArray()) {
        zipArchiveOutputStream.write(nettyByteBuffer.array(),
            nettyByteBuffer.arrayOffset() + nettyByteBuffer.readerIndex(), readable);
      } else {
        byte[] tmp = new byte[readable];
        nettyByteBuffer.getBytes(nettyByteBuffer.readerIndex(), tmp);
        zipArchiveOutputStream.write(tmp);
      }

      nettyByteBuffer.skipBytes(readable);
    } else if (dataBuffer instanceof DefaultDataBuffer defaultDataBuffer) {
      ByteBuffer byteBuffer = defaultDataBuffer.getNativeBuffer();
      writeByteBufferToZip(byteBuffer);
    } else {
      try (ByteBufferIterator it = dataBuffer.readableByteBuffers()) {
        while (it.hasNext()) {
          ByteBuffer byteBuffer = it.next();
          writeByteBufferToZip(byteBuffer);
        }
      }
    }
  }

  private void writeByteBufferToZip(ByteBuffer byteBuffer) throws IOException {
    if (!byteBuffer.hasRemaining()) {
      return;
    }

    if (byteBuffer.hasArray()) {
      zipArchiveOutputStream.write(byteBuffer.array(),
          byteBuffer.arrayOffset() + byteBuffer.position(), byteBuffer.remaining());
      byteBuffer.position(byteBuffer.limit());
    } else {
      byte[] tmp = new byte[byteBuffer.remaining()];
      byteBuffer.get(tmp);
      zipArchiveOutputStream.write(tmp);
    }
  }
}
