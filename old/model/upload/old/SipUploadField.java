package com.example.restservice.model.upload.old;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public enum SipUploadField {
  NONE(null) {
    @Override
    public Set<SipUploadField> nextValidFields() {
      return Collections.singleton(SipUploadField.JOB);
    }
  }, JOB("job") {
    @Override
    public Set<SipUploadField> nextValidFields() {
      return Collections.singleton(SipUploadField.SIP);
    }
  }, SIP("sip") {
    @Override
    public Set<SipUploadField> nextValidFields() {
      return Collections.singleton(SipUploadField.SIP);
    }
  };

  private static final Map<String, SipUploadField> BY_FIELDNAME = new HashMap<>();

  static {
    for (SipUploadField f : values()) {
      BY_FIELDNAME.put(f.fieldName, f);
    }
  }

  private final String fieldName;

  SipUploadField(String fieldName) {
    this.fieldName = fieldName;
  }

  public static SipUploadField fromString(String fieldName) {
    return BY_FIELDNAME.get(fieldName);
  }

  public final String getFieldName() {
    return fieldName;
  }

  public abstract Set<SipUploadField> nextValidFields();
}
