package com.example.restservice.PgipSipStream;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamPipeline {

  private static final int BUFFER_SIZE = 8192;

  private final byte[] buffer = new byte[BUFFER_SIZE];

  public void process(InputStream in, OutputStream out, StreamProcessor processor)
      throws IOException, JAXBException {
    try (OutputStream outputStream = processor.wrap(out)) {
      int bytesRead;
      while ((bytesRead = in.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
      }
      outputStream.flush();
    }
  }
}
