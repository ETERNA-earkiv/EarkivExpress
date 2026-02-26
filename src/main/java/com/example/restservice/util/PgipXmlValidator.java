package com.example.restservice.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;

public class PgipXmlValidator {

    private static Schema schema;

    static {
        try {
            URL schemaURL = Thread.currentThread().getContextClassLoader()
                .getResource("schemas/pgip_1.3.xsd");
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            schema = schemaFactory.newSchema(schemaURL);
        } catch (SAXException e) {
            throw new RuntimeException("Failed to load PGIP schema", e);
        }
    }

    public static ValidationResult validate(byte[] pgipXmlBytes) {
        try {
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new ByteArrayInputStream(pgipXmlBytes)));
            return ValidationResult.valid();
        } catch (SAXException e) {
            return ValidationResult.invalid(e.getMessage());
        } catch (IOException e) {
            return ValidationResult.invalid("Failed to read XML: " + e.getMessage());
        }
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
