package com.example.restservice.model.upload;

import com.google.common.collect.ImmutableSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public enum SipCreateField {
  NONE(null) {
    @Override
    public Set<SipCreateField> nextValidFields() {
      return Collections.singleton(SipCreateField.JOB);
    }
  }, JOB("job") {
    @Override
    public Set<SipCreateField> nextValidFields() {
      return Collections.singleton(SipCreateField.PGIP);
    }
  }, PGIP("pgip") {
    @Override
    public Set<SipCreateField> nextValidFields() {
      return Collections.singleton(SipCreateField.FILE);
    }
  }, FILE("file") {
    @Override
    public Set<SipCreateField> nextValidFields() {
      return ImmutableSet.of(SipCreateField.FILE, SipCreateField.PGIP);
    }
  };

  private static final Map<String, SipCreateField> BY_FIELDNAME = new HashMap<>();

  static {
    for (SipCreateField f : values()) {
      BY_FIELDNAME.put(f.fieldName, f);
    }
  }

  private final String fieldName;

  SipCreateField(String fieldName) {
    this.fieldName = fieldName;
  }

  public static SipCreateField fromString(String fieldName) {
    return BY_FIELDNAME.get(fieldName);
  }

  public final String getFieldName() {
    return fieldName;
  }

  public abstract Set<SipCreateField> nextValidFields();
}