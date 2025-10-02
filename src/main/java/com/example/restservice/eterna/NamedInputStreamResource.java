package com.example.restservice.eterna;

import org.springframework.core.io.InputStreamResource;

import java.io.InputStream;

public class NamedInputStreamResource extends InputStreamResource {
  private final String name;

  public NamedInputStreamResource(String name, InputStream inputStream) {
    super(inputStream);
    this.name = name;
  }

  @Override
  public String getFilename() {
    return name;
  }
}
