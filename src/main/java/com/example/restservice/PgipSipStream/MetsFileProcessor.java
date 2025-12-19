package com.example.restservice.PgipSipStream;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.io.OutputStream;

public class MetsFileProcessor implements StreamProcessor {

  private PgipMets pgipMets;

  public MetsFileProcessor(PgipMets pgipMets) {
    this.pgipMets = pgipMets;
  }

  @Override
  public OutputStream wrap(OutputStream out) throws IOException, JAXBException {
    pgipMets.marshallMETS(out);
    return out;
  }
}
