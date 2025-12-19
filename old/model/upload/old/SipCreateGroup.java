package com.example.restservice.model.upload.old;

import com.example.restservice.model.upload.Job;
import java.util.List;
import org.springframework.http.codec.multipart.Part;

public class SipCreateGroup {
  private final Job job;
  private final Part pgip;
  private final List<Part> files;

  public SipCreateGroup(Job job, Part pgip, List<Part> files) {
    this.job = job;
    this.pgip = pgip;
    this.files = files;
  }

  public Job getJob() {
    return job;
  }

  public Part getPgip() {
    return pgip;
  }

  public List<Part> getFiles() {
    return files;
  }

}
