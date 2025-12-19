package com.example.restservice.PgipSipStream;

import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.PartEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SipCreatePartEventHandler {
  private Flux<PartEvent> allPartsEvents;

  public SipCreatePartEventHandler(Flux<PartEvent> allPartsEvents) {
    this.allPartsEvents = allPartsEvents;
  }

  public Mono<Void> handlePartEvents() {
     allPartsEvents.windowUntil(PartEvent::isLast)
        .concatMap( p -> p.switchOnFirst((signal, partEvents) -> {
              if (signal.hasValue()) {
                PartEvent event = signal.get();
                System.out.println("Handling field '" + event.name() + "', isLast? " + event.isLast());
                partEvents.map(partEvent -> {System.out.println("hej"); return partEvent;}).then(Mono.empty());
              } else if(signal.hasError()) {
                Throwable throwable = signal.getThrowable();
                System.out.println("Error occured: " + throwable.getMessage());
              } else {
                System.out.println("Completed");
                return partEvents;
              }

              return partEvents;
            })
        );
        return Mono.empty();
  }
}
