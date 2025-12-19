package com.example.restservice.model.upload;

import java.util.Set;
import java.util.stream.Collectors;

public class SipCreateUnexpectedFieldException extends IllegalStateException {

  public SipCreateUnexpectedFieldException(String fieldName, Set<SipCreateField> expectedFields) {
    super(String.format("Encountered unexpected field \"%s\", expected: %s", fieldName,
        expectedFields.stream().map(f -> String.format("\"%s\"", f.getFieldName()))
            .collect(Collectors.joining(" || "))));
  }
}
