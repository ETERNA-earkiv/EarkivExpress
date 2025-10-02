package com.example.restservice.eterna;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import org.roda.core.data.v2.ip.TransferredResource;

import java.io.InputStream;

public class ApiClient {
  private WebClient webClient;

  private ApiClient(WebClient webClient){
    this.webClient = webClient;
  }

  public static ApiClientBuilder builder() {
    return new ApiClientBuilder();
  }

  public void createTransferredResourceDirectory(String name) {
    webClient
            .post()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/transfers/")
                    .queryParam("name", name)
                    .build()
            )
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("upl", ""))
            .retrieve();
  }

  public void createTransferredResourceDirectory(String name, String parentUUID) {
    webClient
            .post()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/transfers/")
                    .queryParam("name", name)
                    .queryParam("parentUUID", parentUUID)
                    .build()
            )
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("upl", ""))
            .retrieve();
  }

  public void uploadTransferredResource(String name, InputStream inputStream) {
    MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
    multipartBodyBuilder.part("upl", new InputStreamResource(inputStream)).filename(name);

    MultiValueMap<String, HttpEntity<?>> multipartBody = multipartBodyBuilder.build();

    TransferredResource response = webClient
            .post()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/transfers/")
                    .build()
            )
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(multipartBody))
            .retrieve().bodyToMono(TransferredResource.class).block();

    System.out.println("test");
  }

  public void uploadTransferredResource(String name, String parentUUID, InputStream inputStream) {
    webClient
            .post()
            .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/transfers/")
                    .queryParam("parentUUID", parentUUID)
                    .build()
            )
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData("upl", new NamedInputStreamResource(name, inputStream)))
            .retrieve();
  }

  public static class ApiClientBuilder {
    private final WebClient.Builder webClientBuilder;

    public ApiClientBuilder() {
      webClientBuilder = WebClient.builder();
    }

    public ApiClientBuilder baseUrl(String baseUrl) {
      webClientBuilder.baseUrl(baseUrl);
      return this;
    }

    public ApiClientBuilder setBasicAuth(String username, String password) {
      webClientBuilder.defaultHeaders(headers -> headers.setBasicAuth(username, password));
      return this;
    }

    public ApiClientBuilder setBearerAuth(String bearerToken) {
      webClientBuilder.defaultHeaders(headers -> headers.setBearerAuth(bearerToken));
      return this;
    }

    public ApiClient build() {
      return new ApiClient(webClientBuilder.build());
    }
  }
}
