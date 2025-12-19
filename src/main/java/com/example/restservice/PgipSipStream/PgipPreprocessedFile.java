package com.example.restservice.PgipSipStream;

import java.nio.file.Path;

public class PgipPreprocessedFile extends PgipIPFile {
  private final ClassLoader classLoader;
  private final String resourceName;

  public PgipPreprocessedFile(final ClassLoader classLoader, final String resourceName, final Path path, final long size, final String checksum) {
    super(path, size, checksum);
    this.classLoader = classLoader;
    this.resourceName = resourceName;
  }

  public final ClassLoader getClassLoader() {
    return classLoader;
  }

  public final String getResourceName() {
    return resourceName;
  }
}
