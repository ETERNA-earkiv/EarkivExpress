package com.example.restservice.model.upload.old;

import com.google.common.collect.ImmutableSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public enum SipZipField {
  NONE(null) {
    @Override
    public Set<SipZipField> nextValidFields() {
      return Collections.singleton(SipZipField.JOB);
    }
  }, JOB("job") {
    @Override
    public Set<SipZipField> nextValidFields() {
      return Collections.singleton(SipZipField.FILE);
    }
  }, FILE("file") {
    @Override
    public Set<SipZipField> nextValidFields() {
      return ImmutableSet.of(SipZipField.FILE);
    }
  };

  private static final Map<String, SipZipField> BY_FIELDNAME = new HashMap<>();

  static {
    for (SipZipField f : values()) {
      BY_FIELDNAME.put(f.fieldName, f);
    }
  }

  private final String fieldName;

  SipZipField(String fieldName) {
    this.fieldName = fieldName;
  }

  public static SipZipField fromString(String fieldName) {
    return BY_FIELDNAME.get(fieldName);
  }

  public final String getFieldName() {
    return fieldName;
  }

  public abstract Set<SipZipField> nextValidFields();
}