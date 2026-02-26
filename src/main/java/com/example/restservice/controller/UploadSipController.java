package com.example.restservice.controller;

import com.example.restservice.eterna.ApiClient;
import com.example.restservice.eterna.EternaConfig;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import com.example.restservice.util.ZipValidator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/sip")
public class UploadSipController {

  private final EternaConfig eternaConfig;

  public UploadSipController(EternaConfig eternaConfig) {
    this.eternaConfig = eternaConfig;
  }

  @PostMapping("/upload")
  public Mono<ResponseEntity<String>> uploadSip(
      @RequestPart("file") FilePart filePart,
      @RequestParam(value = "name", required = false) String name) {
    
    String fileName = name != null ? name : 
                     (filePart.filename() != null ? filePart.filename() : "uploaded-file");

    // Collect bytes and upload reactively
    return filePart.content()
        .collectList()
        .flatMap(buffers -> {
          int totalSize = buffers.stream().mapToInt(b -> b.readableByteCount()).sum();
          byte[] allBytes = new byte[totalSize];
          int pos = 0;
          for (var buffer : buffers) {
            int size = buffer.readableByteCount();
            buffer.read(allBytes, pos, size);
            pos += size;
          }
          
          // Create the API client
          ApiClient apiClient = eternaConfig.createApiClient();
          
          // Create input stream from bytes
          ByteArrayInputStream inputStream = new ByteArrayInputStream(allBytes);
          
          // Use WebFlux's MultipartBodyBuilder to create multipart request
          org.springframework.http.client.MultipartBodyBuilder builder = new org.springframework.http.client.MultipartBodyBuilder();
          builder.part("upl", new InputStreamResource(inputStream)).filename(fileName);
          
          // Upload using WebClient reactively (no block())
          return apiClient.getWebClient()
              .post()
              .uri("/api/v1/transfers/")
              .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
              .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(builder.build()))
              .retrieve()
              .bodyToMono(org.roda.core.data.v2.ip.TransferredResource.class)
              .map(response -> ResponseEntity.ok("File uploaded successfully: " + fileName + 
                  " (Eterna ID: " + response.getId() + ")"))
              .onErrorResume(e -> Mono.just(
                  ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                      .body("Failed to upload to Eterna: " + e.getMessage())));
        })
        .onErrorResume(e -> {
            e.printStackTrace();
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to process file: " + e.getMessage()));
        });
  }

  @PostMapping("/upload-simple")
  public Mono<ResponseEntity<String>> uploadSipSimple(
      @RequestPart("file") FilePart filePart) {
    return uploadSip(filePart, null);
  }

  @PostMapping("/upload-zip")
  public Mono<ResponseEntity<String>> uploadZipAsSip(
      @RequestPart("zip") FilePart zipFile,
      @RequestParam(value = "sipName", required = false) String sipName) {
    
    String finalSipName = sipName != null ? sipName : 
                         (zipFile.filename() != null ? zipFile.filename().replace(".zip", "") : "sip-archive");

    return zipFile.content()
        .collectList()
        .flatMap(buffers -> {
          int totalSize = buffers.stream().mapToInt(b -> b.readableByteCount()).sum();
          byte[] zipBytes = new byte[totalSize];
          int pos = 0;
          for (var buffer : buffers) {
            int size = buffer.readableByteCount();
            buffer.read(zipBytes, pos, size);
            pos += size;
          }
          
          try {
            // Validate ZIP contains pgip.xml
            if (!ZipValidator.hasPgipMetadata(zipBytes)) {
              return Mono.just(ResponseEntity.badRequest()
                  .body("ZIP must contain pgip.xml metadata file"));
            }
            
            // Upload the ZIP to Eterna
            String uploadName = finalSipName + ".zip";
            ApiClient apiClient = eternaConfig.createApiClient();
            ByteArrayInputStream sipInputStream = new ByteArrayInputStream(zipBytes);
            
            org.springframework.http.client.MultipartBodyBuilder builder = new org.springframework.http.client.MultipartBodyBuilder();
            builder.part("upl", new InputStreamResource(sipInputStream)).filename(uploadName);
            
            return apiClient.getWebClient()
                .post()
                .uri("/api/v1/transfers/")
                .contentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA)
                .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(org.roda.core.data.v2.ip.TransferredResource.class)
                .map(response -> ResponseEntity.ok("ZIP uploaded as SIP: " + uploadName + 
                    " (Eterna ID: " + response.getId() + "). " +
                    "Process it in Eterna UI to convert to AIP."))
                .onErrorResume(e -> Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to upload to Eterna: " + e.getMessage())));
                        
          } catch (Exception e) {
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to process ZIP: " + e.getMessage()));
          }
        })
        .onErrorResume(e -> {
            e.printStackTrace();
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Failed to process ZIP file: " + e.getMessage()));
        });
  }
}
