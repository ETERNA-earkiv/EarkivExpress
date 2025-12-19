
package com.example.restservice.controller.old;

import com.example.restservice.manager.CreateSipManager;
import com.example.restservice.model.upload.SipCreateField;
import com.example.restservice.sipbuilder.SipBuilder;
import com.example.restservice.writers.EternaTransferredResourceWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
public class CreateSipController {
  private final CreateSipManager createSipManager = new CreateSipManager(new EternaTransferredResourceWriter());

  @PostMapping(path = "controller/sip/create")
  public Mono<Void> createSip(@RequestBody Flux<PartEvent> events) {
    AtomicReference<SipBuilder> pgipSipBuilderRef = new AtomicReference<>(new SipBuilder());
    AtomicReference<SipCreateField> lastFieldRef = new AtomicReference<>(SipCreateField.NONE);

    return events
        .windowUntil(PartEvent::isLast)
        .concatMap(fieldFlux -> fieldFlux.switchOnFirst((signal, partEvents) -> {
          if (!signal.hasValue()) {
            // Upload has completed or an error occurred
            return partEvents.map(PartEvent::content)
                .map(DataBufferUtils::release)
                .then(Mono.just(Mono.<Void>empty()));
          }

          final SipBuilder pgipSipBuilder = pgipSipBuilderRef.get();

          final PartEvent event = signal.get();
          assert event != null;

          SipCreateField currentField = SipCreateField.fromString(event.name());
          if (!lastFieldRef.get().nextValidFields().contains(currentField)) {
            return Mono.error(new InvalidFieldException(currentField, lastFieldRef.get()));
          }

          Mono<Mono<Void>> mono = switch (currentField) {
            case JOB -> pgipSipBuilder.processJob(event, partEvents).then(Mono.just(Mono.<Void>empty()));
            case PGIP -> pgipSipBuilder.processPgip(event, partEvents).thenReturn(Mono.defer(() -> {
              try {
                return pgipSipBuilder.saveZip();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            }));
            case FILE -> pgipSipBuilder.processFile(event, partEvents).then(Mono.just(Mono.<Void>empty()));

            default -> Mono.error(new InvalidFieldException(currentField, lastFieldRef.get()));
          };

          lastFieldRef.set(currentField);

          return mono;
        }))
        .doOnComplete(() -> {
          System.out.println("Complete");
        })
        .doOnError(err -> {
          System.out.println("Error: " + err);
        })
        .concatMap(Function.identity())
        .then();
  }

  public static class InvalidFieldException extends IllegalStateException {
    public InvalidFieldException(SipCreateField currentField, SipCreateField lastField) {
      super(String.format("Encountered unexpected field \"%s\", expected one of: %s",
          currentField.getFieldName(), lastField.nextValidFields().stream()
              .map(f -> String.format("\"%s\"", f.getFieldName()))
              .collect(Collectors.joining(", "))));
    }
  }
}
*(