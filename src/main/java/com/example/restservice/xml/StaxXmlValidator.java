package com.example.restservice.xml;

import com.example.restservice.exception.XmlValidationException;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stax.StAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class StaxXmlValidator {
    private final DataBufferInputStream input = new DataBufferInputStream();
    private final Mono<Void> validation;
    private volatile boolean finished;

    public StaxXmlValidator(Schema schema) {
        this.validation =
                Mono.fromRunnable(() -> runValidation(schema))
                        .subscribeOn(Schedulers.boundedElastic())
                        .cache()
                        .then();
    }

    private void runValidation(Schema schema) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Validator validator = schema.newValidator();
            XMLStreamReader reader =
                    factory.createXMLStreamReader(input);

            validator.validate(new StAXSource(reader));
        } catch (Exception e) {
            input.fail(new IOException("XML validation failed", e));
            throw new XmlValidationException(e);
        }
    }

    public void accept(ByteBuffer buffer) {
        if (finished) {
            throw new IllegalStateException("Validator already finished");
        }
        input.feed(buffer);
    }

    public void finish() {
        finished = true;
        input.closeInput();
        validation.block(); // propagates validation error
    }
}
