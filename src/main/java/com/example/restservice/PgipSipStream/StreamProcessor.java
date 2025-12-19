package com.example.restservice.PgipSipStream;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.OutputStream;

public interface StreamProcessor {

  OutputStream wrap(OutputStream out) throws IOException, JAXBException;

  default StreamProcessor then(StreamProcessor next) {
    return out -> next.wrap(this.wrap(out));
  }
}
