package com.example.restservice.model.upload.old;

import reactor.core.publisher.Mono;

public class SipCreateProcessor {
  public static Mono<Void> process(SipCreateGroup group) {

    return Mono.empty();
  }
}
