package com.example.restservice.model.upload.old;

import com.example.restservice.model.upload.Job;
import com.example.restservice.model.upload.SipCreateField;
import com.example.restservice.model.upload.SipCreateUnexpectedFieldException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import reactor.core.publisher.FluxSink;

public class SipCreateConsumer implements BiConsumer<Part, FluxSink<SipCreateGroup>> {

  private SipCreateField lastField = SipCreateField.NONE;
  private Job job;
  private Part pgip;
  private List<Part> files = new ArrayList<>();

  @Override
  public void accept(Part part, FluxSink<SipCreateGroup> sink) {
    final String fieldName = part.name();
    final SipCreateField currentField = SipCreateField.fromString(fieldName);

    if (!lastField.nextValidFields().contains(currentField)) {
      sink.error(new SipCreateUnexpectedFieldException(fieldName, lastField.nextValidFields()));
    }

    switch (currentField) {
      case JOB:
        if (part instanceof FormFieldPart formFieldPart) {
          if (lastField != SipCreateField.NONE) {
            flush(sink);
          }

          final ObjectMapper objectMapper = new ObjectMapper();
          try {
            job = objectMapper.readValue(formFieldPart.value(), Job.class);
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        } else {
          throw new RuntimeException("Field \"Job\" is not a form field");
        }

        break;

      case PGIP:
        pgip = part;
        break;

      case FILE:
        files.add(part);
        break;

      default:
        sink.error(new SipCreateUnexpectedFieldException(fieldName, lastField.nextValidFields()));
    }
  }

  public void flush(FluxSink<SipCreateGroup> sink) {
    if (job != null && pgip != null) {
      SipCreateGroup sipCreateGroup = new SipCreateGroup(job, pgip, files);
      sink.next(sipCreateGroup);
    }

    job = null;
    pgip = null;
    files.clear();
  }

  public void complete(FluxSink<SipCreateGroup> sink) {
    flush(sink);
    sink.complete();
  }

  /*
  public static Flux<SipCreateGroup> consume(Flux<Part> parts) {
    SipCreateConsumer consumer = new SipCreateConsumer();
    return parts
        .handle(consumer)
        .concatWith(Mono.defer(() -> {
          SipCreateGroup last = consumer.finalizeGroup();
          return last != null ? Mono.just(last) : Mono.empty();
        }));
  }
   */
}
