package com.example.restservice.PgipSipStream;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.StreamSupport;
import org.roda_project.commons_ip2.model.IPFileInterface;

public class PgipIPFile implements IPFileInterface {

  private final String checksumAlgorithm = "SHA256";
  private final List<String> relativeFolders;
  private final long size;
  private final String checksum;
  private Path path;

  public PgipIPFile(final Path path, final long size, final String checksum) {
    this.size = size;
    this.checksum = checksum;
    this.path = path;
    this.relativeFolders = StreamSupport.stream(path.spliterator(), false).map(Path::toString)
        .toList();
  }

  @Override
  public List<String> getRelativeFolders() {
    return relativeFolders;
  }

  @Override
  public String getFileName() {
    return path.getFileName().toString();
  }

  @Override
  public Path getPath() {
    return path;
  }

  public void setPath(final Path path) {
    this.path = path;
    this.relativeFolders.clear();
    this.relativeFolders.addAll(
        StreamSupport.stream(path.spliterator(), false).map(Path::toString).toList());
  }

  public long getSize() {
    return size;
  }

  public String getChecksum() {
    return checksum;
  }
}
