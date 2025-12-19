package com.example.restservice.PgipSipStream;

import jakarta.xml.bind.DatatypeConverter;
import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.roda_project.commons_ip.utils.IPException;
import org.roda_project.commons_ip2.model.IPConstants;
import org.roda_project.commons_ip2.model.IPDescriptiveMetadata;
import org.roda_project.commons_ip2.model.IPRepresentation;
import org.roda_project.commons_ip2.model.MetadataType;
import org.roda_project.commons_ip2.validator.constants.Constants;

public class PgipSipBuilder implements AutoCloseable {

  private static final ClassLoader COMMONS_IP_CLASSLOADER = org.roda_project.commons_ip2.mets_v1_12.beans.Mets.class.getClassLoader();
  private static final String PGIP_METADATA_TYPE = "pgip";
  private static final String PGIP_VERSION = "1.3";
  private static final String PGIP_FILENAME = PGIP_METADATA_TYPE + "_" + PGIP_VERSION + ".xml";
  private static final String PGIP_SCHEMA = PGIP_METADATA_TYPE + "_" + PGIP_VERSION + ".xsd";
  private static final String REPRESENTATION_NAME = "rep1";
  private static final String REPRESENTATION_FOLDER = IPConstants.REPRESENTATIONS_FOLDER + REPRESENTATION_NAME + IPConstants.METS_PATH_SEPARATOR;

  private static final List<PgipPreprocessedFile> PREPROCESSED_SCHEMAS;

  static {
    try {
      PREPROCESSED_SCHEMAS = List.of(preprocessCommonsIpSchemaFile(IPConstants.SCHEMA_XLINK_FILENAME),
          preprocessCommonsIpSchemaFile(IPConstants.SCHEMA_METS_FILENAME_WITH_VERSION),
          preprocessCommonsIpSchemaFile(IPConstants.SCHEMA_EARK_CSIP_FILENAME),
          preprocessCommonsIpSchemaFile(IPConstants.SCHEMA_EARK_SIP_FILENAME),
          preprocessPgipSchemaFile(PGIP_SCHEMA));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private final StreamPipeline streamPipeline;
  private final ZipOutputStream zipOutputStream;
  private final List<IPDescriptiveMetadata> descriptiveMetadatas = new ArrayList<>();
  private final List<PgipIPFile> schemas = new ArrayList<>();
  private final List<PgipIPFile> representationFiles = new ArrayList<>();


  public PgipSipBuilder(OutputStream outputStream) {
    streamPipeline = new StreamPipeline();

    zipOutputStream = new ZipOutputStream(outputStream);
    zipOutputStream.setLevel(0);
  }

  private static PgipPreprocessedFile preprocessCommonsIpSchemaFile(final String schemaName)
      throws IOException {
    return preprocessResourceFile(COMMONS_IP_CLASSLOADER,
        IPConstants.SCHEMAS + "2" + IPConstants.METS_PATH_SEPARATOR + schemaName,
        IPConstants.SCHEMAS + IPConstants.METS_PATH_SEPARATOR + schemaName);
  }

  private static PgipPreprocessedFile preprocessPgipSchemaFile(final String schemaName)
      throws IOException {
    return preprocessResourceFile(PgipSipBuilder.class.getClassLoader(),
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

  public void processPgip(final InputStream inputStream) throws IOException, JAXBException {
    final String entryName = IPConstants.DESCRIPTIVE_FOLDER + PGIP_FILENAME;

    SipFileProcessor sipFileProcessor = new SipFileProcessor(zipOutputStream, entryName);
    streamPipeline.process(inputStream, zipOutputStream, sipFileProcessor);

    final String checksum = DatatypeConverter.printHexBinary(sipFileProcessor.getDigest());

    PgipIPFile ipFile = new PgipIPFile(Path.of(entryName), sipFileProcessor.getSize(), checksum);
    IPDescriptiveMetadata metadata = new IPDescriptiveMetadata(ipFile,
        new MetadataType(PGIP_METADATA_TYPE), PGIP_VERSION);

    descriptiveMetadatas.add(metadata);
  }

  public void processDataFile(final InputStream inputStream, final String fileName)
      throws IOException, JAXBException {

    final String filePath = IPConstants.DATA_FOLDER + fileName;
    final String entryName = REPRESENTATION_FOLDER + filePath;
    SipFileProcessor sipFileProcessor = new SipFileProcessor(zipOutputStream, entryName);
    streamPipeline.process(inputStream, zipOutputStream, sipFileProcessor);

    final String checksum = DatatypeConverter.printHexBinary(sipFileProcessor.getDigest());
    final PgipIPFile pgipIPFile =  new PgipIPFile(Path.of(filePath), sipFileProcessor.getSize(), checksum);

    representationFiles.add(pgipIPFile);
  }

  public void addStaticFiles() throws IOException, IPException, JAXBException {
    for (PgipPreprocessedFile file : PREPROCESSED_SCHEMAS) {
      ZipEntry zipEntry = newZipEntry(file.getPath().toString());
      zipOutputStream.putNextEntry(zipEntry);
      try (InputStream resourceStream = file.getClassLoader()
          .getResourceAsStream(file.getResourceName())) {
        Objects.requireNonNull(resourceStream).transferTo(zipOutputStream);
      }

      schemas.add(file);
    }

    processSipMetsFile();
  }

  private void processSipMetsFile() throws IPException, JAXBException, IOException {
    // TODO: Handle ancestors
    PgipMets sipMets = PgipMets.newIpMets("test", "description", Optional.empty());
    for (IPDescriptiveMetadata metadata : descriptiveMetadatas) {
      sipMets.addDescriptiveMetadata(metadata);
    }

    for (PgipIPFile ipFile : schemas) {
      sipMets.addSchema(ipFile);
    }

    IPRepresentation representation = new IPRepresentation(REPRESENTATION_NAME);
    PgipMets representationMets = PgipMets.newRepresentationMets(representation);

    for (PgipIPFile ipFile : representationFiles) {
      representation.addFile(ipFile);
      representationMets.addRepresentationDataFile(ipFile);
    }

    final String representationMetsPath = IPConstants.REPRESENTATIONS_FOLDER + REPRESENTATION_NAME + IPConstants.METS_PATH_SEPARATOR + Constants.METS_FILE;

    SipFileProcessor sipFileProcessor = new SipFileProcessor(zipOutputStream, representationMetsPath);
    OutputStream wrappedOutputStream = sipFileProcessor.wrap(zipOutputStream);
    representationMets.marshallMETS(wrappedOutputStream);

    final String checksum = DatatypeConverter.printHexBinary(sipFileProcessor.getDigest());
    PgipIPFile representationMetsFile = new PgipIPFile(Path.of(representationMetsPath), sipFileProcessor.getSize(), checksum);

    sipMets.addRepresentation(representation, representationMetsFile);

    ZipEntry sipMetsZipEntry = newZipEntry(Constants.METS_FILE);
    zipOutputStream.putNextEntry(sipMetsZipEntry);
    sipMets.marshallMETS(zipOutputStream);
  }

  @Override
  public void close() throws Exception {
    zipOutputStream.close();
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
