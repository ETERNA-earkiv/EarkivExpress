package com.example.restservice.manager;

import com.example.restservice.sipbuilder.SipBuilder;
import com.example.restservice.writers.EternaTransferredResourceWriter;
import com.example.restservice.writers.SipOutputWriter;
import com.example.restservice.xml.StaxXmlValidator;
import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.springframework.http.codec.multipart.PartEvent;
import org.xml.sax.SAXException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CreateSipManager {

  private final SipOutputWriter outputWriter;
  private final Schema xmlSchema;

  private SipBuilder currentSipBuilder;
  private StaxXmlValidator xmlValidator;
  private Mono<Void> currentWriteMono;

  public CreateSipManager() {
    this.outputWriter = new EternaTransferredResourceWriter();

    URL schemaURL = Thread.currentThread().getContextClassLoader()
        .getResource("schemas/pgip_1.3.xsd");
    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    try {
      this.xmlSchema = schemaFactory.newSchema(schemaURL);
    } catch (SAXException e) {
      throw new RuntimeException(e);
    }
  }

  public Mono<Void> newSipBuilder() {
    currentSipBuilder = new SipBuilder();


    return ((EternaTransferredResourceWriter) outputWriter).test().then(
        outputWriter.startWriter(zipArchiveOutputStream -> {
              currentSipBuilder.setZipArchiveOutputStream(zipArchiveOutputStream);
              xmlValidator = xmlSchema != null ? new StaxXmlValidator(xmlSchema) : null;
              return currentSipBuilder;
            })
            .doOnNext(writeMono -> currentWriteMono = writeMono)
            .then()
    );
  }

  public Mono<Void> processJob(PartEvent firstPart, Flux<PartEvent> partEvents) {
    return currentSipBuilder.processJob(firstPart, partEvents);
  }

  public Mono<Void> processPgip(PartEvent firstPart, Flux<PartEvent> partEvents) {
    return currentSipBuilder.processPgip(firstPart, partEvents);
  }

  public Mono<Void> processFile(PartEvent firstPart, Flux<PartEvent> partEvents) {
    return currentSipBuilder.processFile(firstPart, partEvents);
  }
}
