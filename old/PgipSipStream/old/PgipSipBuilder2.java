package com.example.restservice.PgipSipStream;

import com.example.restservice.model.upload.Job;
import com.example.restservice.model.upload.old.SipCreateGroup;
import jakarta.xml.bind.DatatypeConverter;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.roda_project.commons_ip.utils.IPException;
import org.roda_project.commons_ip2.model.IPConstants;
import org.roda_project.commons_ip2.model.IPDescriptiveMetadata;
import org.roda_project.commons_ip2.model.IPRepresentation;
import org.roda_project.commons_ip2.model.MetadataType;
import org.roda_project.commons_ip2.validator.constants.Constants;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.codec.multipart.Part;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

public class PgipSipBuilder2 {

  private static final DataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();
  private static final ClassLoader COMMONS_IP_CLASSLOADER = org.roda_project.commons_ip2.mets_v1_12.beans.Mets.class.getClassLoader();
  private static final String PGIP_METADATA_TYPE = "pgip";
  private static final String PGIP_VERSION = "1.3";
  private static final String PGIP_FILENAME = PGIP_METADATA_TYPE + "_" + PGIP_VERSION + ".xml";
  private static final String PGIP_SCHEMA = PGIP_METADATA_TYPE + "_" + PGIP_VERSION + ".xsd";
  private static final String REPRESENTATION_NAME = "rep1";
  private static final String REPRESENTATION_FOLDER =
      IPConstants.REPRESENTATIONS_FOLDER + REPRESENTATION_NAME + IPConstants.METS_PATH_SEPARATOR;

  private static final List<PgipPreprocessedFile> PREPROCESSED_SCHEMAS;

  static {
    try {
      PREPROCESSED_SCHEMAS = List.of(
          preprocessCommonsIpSchemaFile(IPConstants.SCHEMA_XLINK_FILENAME),
          preprocessCommonsIpSchemaFile(IPConstants.SCHEMA_METS_FILENAME_WITH_VERSION),
          preprocessCommonsIpSchemaFile(IPConstants.SCHEMA_EARK_CSIP_FILENAME),
          preprocessCommonsIpSchemaFile(IPConstants.SCHEMA_EARK_SIP_FILENAME),
          preprocessPgipSchemaFile(PGIP_SCHEMA));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static PgipPreprocessedFile preprocessCommonsIpSchemaFile(final String schemaName)
      throws IOException {
    return preprocessResourceFile(COMMONS_IP_CLASSLOADER,
        IPConstants.SCHEMAS + "2" + IPConstants.METS_PATH_SEPARATOR + schemaName,
        IPConstants.SCHEMAS + IPConstants.METS_PATH_SEPARATOR + schemaName);
  }

  private static PgipPreprocessedFile preprocessPgipSchemaFile(final String schemaName)
      throws IOException {
    return preprocessResourceFile(PgipSipBuilder2.class.getClassLoader(),
        IPConstants.SCHEMAS + IPConstants.METS_PATH_SEPARATOR + schemaName,
        IPConstants.SCHEMAS + IPConstants.METS_PATH_SEPARATOR + schemaName);
  }

  private static PgipPreprocessedFile preprocessResourceFile(final ClassLoader classLoader,
      final String resourceName, final String entryName) throws IOException {

    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance(IPConstants.CHECKSUM_SHA_256_ALGORITHM);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    OutputStream nullOut = OutputStream.nullOutputStream();

    try (InputStream resourceStream = classLoader.getResourceAsStream(
        resourceName); DigestOutputStream digestStream = new DigestOutputStream(nullOut, digest)) {
      final long size = Objects.requireNonNull(resourceStream).transferTo(digestStream);
      final String checksum = DatatypeConverter.printHexBinary(digest.digest());

      return new PgipPreprocessedFile(classLoader, resourceName, Path.of(entryName), size,
          checksum);
    }
  }

  public static Flux<DataBuffer> build(SipCreateGroup group) {
    return Flux.create(sink -> {
      try (final DataBufferOutputStream outputStream = new DataBufferOutputStream(BUFFER_FACTORY,
          sink); final ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(
          outputStream)) {
        zipOutputStream.setLevel(0);

        final Job job = group.getJob();
        final List<PgipIPFile> schemas = Collections.unmodifiableList(PREPROCESSED_SCHEMAS);

        Mono<IPDescriptiveMetadata> pgipMetadata = processPgip(zipOutputStream, group.getPgip(),
            sink);

        List<Mono<PgipIPFile>> dataFileMonos = group.getFiles().stream().map(part -> processDataFile(zipOutputStream, part, sink)).toList();

        Mono<List<PgipIPFile>> dataFiles = Flux.concat(dataFileMonos).collectList();

        Mono.zip(pgipMetadata, dataFiles).flatMap(tuple -> {
          try {
            processStaticSchemas(zipOutputStream);
            processSipMetsFile(zipOutputStream, List.of(tuple.getT1()), schemas, tuple.getT2());

            zipOutputStream.finish();
            zipOutputStream.flush();
            outputStream.flush();
            sink.complete();
          } catch (IPException | JAXBException | IOException e) {
            sink.error(e);
          }

          return Mono.empty();
        }).subscribe();
      } catch (IOException | JAXBException e) {
        sink.error(e);
      }
    });
  }

  private static Mono<PgipIPFile> processPgipIPFile(final ZipArchiveOutputStream zipOutputStream,
      Part pgipPart, String entryName, FluxSink<DataBuffer> sink)
      throws IOException, JAXBException {

    final SipFileProcessor2 fileProcessor = new SipFileProcessor2();
    final ZipArchiveEntry zipEntry = new ZipArchiveEntry(entryName);

    try {
      zipOutputStream.putArchiveEntry(zipEntry);
    } catch (IOException e) {
      return Mono.error(e);
    }

    return pgipPart.content().doOnNext(buffer -> {
      try {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read();
        fileProcessor.update(bytes);
        zipOutputStream.write(bytes);
      } catch (IOException e) {
        sink.error(e);
      } finally {
        DataBufferUtils.release(buffer);
      }
    }).then(Mono.fromCallable(() -> {
      zipOutputStream.closeArchiveEntry();

      final String checksum = DatatypeConverter.printHexBinary(fileProcessor.getDigest());
      final Path path = Path.of(entryName);
      return new PgipIPFile(path, fileProcessor.getSize(), checksum);
    }));
  }

  private static Mono<IPDescriptiveMetadata> processPgip(
      final ZipArchiveOutputStream zipOutputStream, Part pgipPart, FluxSink<DataBuffer> sink)
      throws IOException, JAXBException {

    final String entryName = IPConstants.DESCRIPTIVE_FOLDER + PGIP_FILENAME;

    return processPgipIPFile(zipOutputStream, pgipPart, entryName, sink).flatMap(ipFile -> {
      final MetadataType metadataType = new MetadataType(PGIP_METADATA_TYPE);
      return Mono.just(new IPDescriptiveMetadata(ipFile, metadataType, PGIP_VERSION));
    });
  }

  private static Mono<PgipIPFile> processDataFile(final ZipArchiveOutputStream zipOutputStream,
      Part pgipPart, FluxSink<DataBuffer> sink) {
    final String fileName = pgipPart.name();
    final String filePath = IPConstants.DATA_FOLDER + fileName;
    final String entryName = REPRESENTATION_FOLDER + filePath;

    try {
      return processPgipIPFile(zipOutputStream, pgipPart, entryName, sink).flatMap(ipFile -> {
        ipFile.setPath(Path.of(filePath));
        return Mono.just(ipFile);
      });
    } catch (IOException | JAXBException e) {
      return Mono.error(e);
    }
  }

  private static void processStaticSchemas(final ZipArchiveOutputStream zipOutputStream)
      throws IOException, IPException, JAXBException {
    for (PgipPreprocessedFile file : PREPROCESSED_SCHEMAS) {
      ZipArchiveEntry zipEntry = newZipEntry(file.getPath().toString());
      zipOutputStream.putArchiveEntry(zipEntry);
      try (InputStream resourceStream = file.getClassLoader()
          .getResourceAsStream(file.getResourceName())) {
        Objects.requireNonNull(resourceStream).transferTo(zipOutputStream);
        zipOutputStream.closeArchiveEntry();
      }
    }
  }

  private static void processSipMetsFile(final ZipArchiveOutputStream zipOutputStream,
      List<IPDescriptiveMetadata> descriptiveMetadata, List<PgipIPFile> schema,
      List<PgipIPFile> representationFiles) throws IPException, JAXBException, IOException {
    // TODO: Handle ancestors
    PgipMets sipMets = PgipMets.newIpMets("test", "description", Optional.empty());
    for (IPDescriptiveMetadata metadata : descriptiveMetadata) {
      sipMets.addDescriptiveMetadata(metadata);
    }

    for (PgipIPFile ipFile : schema) {
      sipMets.addSchema(ipFile);
    }

    IPRepresentation representation = new IPRepresentation(REPRESENTATION_NAME);
    PgipMets representationMets = PgipMets.newRepresentationMets(representation);

    for (PgipIPFile ipFile : representationFiles) {
      representation.addFile(ipFile);
      representationMets.addRepresentationDataFile(ipFile);
    }

    final String representationMetsPath =
        IPConstants.REPRESENTATIONS_FOLDER + REPRESENTATION_NAME + IPConstants.METS_PATH_SEPARATOR
            + Constants.METS_FILE;

    ZipArchiveEntry sipFileEntry = newZipEntry(representationMetsPath);
    zipOutputStream.putArchiveEntry(sipFileEntry);
    WrappedZipArchiveStream sipFileOutputStream = new WrappedZipArchiveStream(zipOutputStream);
    representationMets.marshallMETS(sipFileOutputStream);
    zipOutputStream.closeArchiveEntry();

    final String checksum = DatatypeConverter.printHexBinary(sipFileOutputStream.getDigest());
    PgipIPFile representationMetsFile = new PgipIPFile(Path.of(representationMetsPath),
        sipFileOutputStream.getSize(), checksum);

    sipMets.addRepresentation(representation, representationMetsFile);

    ZipArchiveEntry sipMetsZipEntry = newZipEntry(Constants.METS_FILE);
    zipOutputStream.putArchiveEntry(sipMetsZipEntry);
    sipMets.marshallMETS(zipOutputStream);
    zipOutputStream.closeArchiveEntry();
  }

  private static ZipArchiveEntry newZipEntry(final String entryName) {
    ZipArchiveEntry zipEntry = new ZipArchiveEntry(entryName);

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
