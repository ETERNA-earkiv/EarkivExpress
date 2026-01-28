package com.example.restservice.controller;

import com.example.restservice.eterna.EternaConfig;
import com.example.restservice.manager.CreateSipManager;
import com.example.restservice.sipbuilder.SipBuilder;
import com.example.restservice.model.upload.SipCreateField;
import com.example.restservice.writers.EternaTransferredResourceWriter;
import org.springframework.web.bind.annotation.GetMapping;
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
import reactor.core.scheduler.Schedulers;

@RestController
public class CreateSipController {
  private final CreateSipManager createSipManager;
  private final EternaConfig eternaConfig;

  public CreateSipController(CreateSipManager createSipManager, EternaConfig eternaConfig) {
    this.createSipManager = createSipManager;
    this.eternaConfig = eternaConfig;
  }

  @GetMapping("/api/config/eterna")
  public EternaConfigInfo getEternaConfig() {
    return new EternaConfigInfo(eternaConfig.getBaseUrl(), eternaConfig.getUsername());
  }

  public static class EternaConfigInfo {
    private final String baseUrl;
    private final String username;

    public EternaConfigInfo(String baseUrl, String username) {
      this.baseUrl = baseUrl;
      this.username = username;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public String getUsername() {
      return username;
    }
  }

  @PostMapping(path = "api/sip/create")
  public Mono<Void> createSip(@RequestBody Flux<PartEvent> events) {
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

          final PartEvent event = signal.get();
          assert event != null;

          SipCreateField currentField = SipCreateField.fromString(event.name());
          if (!lastFieldRef.get().nextValidFields().contains(currentField)) {
            return Mono.error(new InvalidFieldException(currentField, lastFieldRef.get()));
          }

          Mono<Mono<Void>> mono = switch (currentField) {
            case JOB -> createSipManager.newSipBuilder().doOnNext((a) ->
                createSipManager.processJob(event, partEvents)
            ).then(Mono.just(Mono.<Void>empty()));
            case PGIP -> createSipManager.processPgip(event, partEvents).then(Mono.just(Mono.<Void>empty()));
            case FILE -> createSipManager.processFile(event, partEvents).then(Mono.just(Mono.<Void>empty()));

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
