package com.example.restservice.controller;

import com.example.restservice.eterna.EternaConfig;
import com.example.restservice.manager.CreateSipManager;
import com.example.restservice.sipbuilder.SipBuilder;
import com.example.restservice.model.upload.SipCreateField;
import com.example.restservice.writers.EternaTransferredResourceWriter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import reactor.core.publisher.Mono;
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

  @GetMapping("/api/config/eterna/test")
  @ResponseBody
  public Mono<ResponseEntity<EternaConnectionTest>> testEternaConnection() {
    return Mono.fromCallable(() -> {
      try {
        String testName = "test-connection-" + System.currentTimeMillis();
        System.out.println("Creating test transfer directory: " + testName);

        // Try to create a test directory to verify connection
        eternaConfig.createApiClient().createTransferredResourceDirectory(testName);

        System.out.println("Successfully created test transfer directory: " + testName);
        return ResponseEntity
            .ok(new EternaConnectionTest(true, "Connection successful - directory '" + testName + "' created", null));
      } catch (Exception e) {
        System.err.println("Failed to create test transfer directory: " + e.getMessage());
        e.printStackTrace();
        return ResponseEntity
            .ok(new EternaConnectionTest(false, "Connection failed: " + e.getMessage(), e.getClass().getSimpleName()));
      }
    });
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

  public static class EternaConnectionTest {
    private final boolean connected;
    private final String message;
    private final String errorType;

    public EternaConnectionTest(boolean connected, String message, String errorType) {
      this.connected = connected;
      this.message = message;
      this.errorType = errorType;
    }

    public boolean isConnected() {
      return connected;
    }

    public String getMessage() {
      return message;
    }

    public String getErrorType() {
      return errorType;
    }
  }

  @PostMapping(path = "api/sip/test")
  public Mono<String> testSipEndpoint() {
    System.out.println("Test SIP endpoint called");
    return Mono.just("Test endpoint working");
  }

  @PostMapping(path = "api/sip/create")
  public Mono<Void> createSip(@RequestBody Flux<PartEvent> events) {
    System.out.println("SIP creation request received");
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
            case JOB -> createSipManager.newSipBuilder().doOnNext((a) -> createSipManager.processJob(event, partEvents))
                .then(Mono.just(Mono.<Void>empty()));
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
