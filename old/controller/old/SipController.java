package com.example.restservice.controller.old;

//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
    import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletDiskFileUpload;
import org.springframework.core.io.buffer.DataBuffer;
    import org.springframework.http.codec.multipart.FormPartEvent;
    import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("sip")
public class SipController {

  private static final Path UPLOAD_DIR = Paths.get("/Volumes/ramdisk/upload");

  private static JakartaServletDiskFileUpload newServletDiskFileUpload() {
    JakartaServletDiskFileUpload upload = new JakartaServletDiskFileUpload();
    upload.setFileCountMax(-1);
    upload.setFileSizeMax(-1);
    return upload;
  }

  private static ZipEntry createZipEntry(String name) {
    ZipEntry zipEntry = new ZipEntry(name);

    // Use DEFLATED instead of STORED as it sets the ZipEntry's general purpose bit flags
    // 4th bit which means that the uncompressed size, compressed size and crc-32 are stored
    // in a data descriptor after the file data instead of storing it in the local file
    // header that precedes the data. This could be fixed with a new ZipOutputStream
    // implementation. Currently, we set the entire zip-files compression level to 0 which
    // means that all files are store decompressed no matter what the specific files ZipEntry
    // method is set to.
    zipEntry.setMethod(ZipEntry.DEFLATED);
    return zipEntry;
  }



  @PostMapping(path = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<String> uploadSip(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    JakartaServletDiskFileUpload upload = newServletDiskFileUpload();

    ApiClient apiClient = ApiClient.builder().baseUrl("http://localhost:8080/")
        .setBasicAuth("admin", "roda").build();

    Job job = null;
    SipUploadField lastField = SipUploadField.NONE;

    FileItemInputIterator iter = upload.getItemIterator(request);
    while (iter.hasNext()) {
      FileItemInput item = iter.next();

      SipUploadField currentField = SipUploadField.fromString(item.getFieldName());
      if (!lastField.nextValidFields().contains(currentField)) {
        String expectedFields = lastField.nextValidFields().stream()
            .map(f -> String.format("\"%s\"", f.getFieldName()))
            .collect(Collectors.joining(" || "));

        return ResponseEntity.badRequest().body(
            String.format("Encountered unexpected field \"%s\", expected: %s", item.getFieldName(),
                expectedFields));
      }

      ObjectMapper objectMapper = new ObjectMapper();
      JavaType javaType = objectMapper.getTypeFactory()
          .constructParametricType(ApiResult.class, Job.class);

      try (InputStream inputStream = item.getInputStream()) {
        switch (currentField) {
          case JOB:
            job = objectMapper.readValue(inputStream, javaType);
            break;

          case SIP:
            apiClient.uploadTransferredResource(item.getName(), inputStream);
            break;

          default:
            return ResponseEntity.badRequest()
                .body(String.format("Encountered unexpected field \"%s\"", item.getFieldName()));
        }

      }

      lastField = currentField;
    }

    return ResponseEntity.ok("Upload successful!\nTest: " + job.getTest() + "\n");
  }

  @PostMapping(path = "/create", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<String> createSip(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Job job = null;

    JakartaServletDiskFileUpload upload = newServletDiskFileUpload();

    SipCreateField lastField = SipCreateField.NONE;
    SipCreateField currentField = null;

    ObjectMapper objectMapper = new ObjectMapper();

    UUID uuid = UUID.randomUUID();
    Path targetPath = UPLOAD_DIR.resolve(uuid + ".zip");

    // Create new PGIP SIP Builder
    try (
        OutputStream outputStream = Files.newOutputStream(targetPath);
        PgipSipBuilder sipBuilder = new PgipSipBuilder(outputStream)
    ) {
      FileItemInputIterator iter = upload.getItemIterator(request);

      while (iter.hasNext()) {
        FileItemInput item = iter.next();

        currentField = SipCreateField.fromString(item.getFieldName());
        if (!lastField.nextValidFields().contains(currentField)) {
          String expectedFields = lastField.nextValidFields().stream()
              .map(f -> String.format("\"%s\"", f.getFieldName()))
              .collect(Collectors.joining(" || "));

          return ResponseEntity.badRequest().body(
              String.format("Encountered unexpected field \"%s\", expected: %s",
                  item.getFieldName(), expectedFields));
        }

        try (InputStream inputStream = item.getInputStream()) {
          switch (currentField) {
            case JOB:
              if (lastField != SipCreateField.NONE) {
                sipBuilder.addStaticFiles();
              }

              job = objectMapper.readValue(inputStream, Job.class);
              break;

            case PGIP:
              sipBuilder.processPgip(inputStream);
              break;

            case FILE:
              sipBuilder.processDataFile(inputStream, item.getName());
              break;

            default:
              return ResponseEntity.badRequest()
                  .body(String.format("Encountered unexpected field \"%s\"", item.getFieldName()));
          }
        }

        lastField = currentField;
      }

      sipBuilder.addStaticFiles();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return ResponseEntity.ok("Upload successful");
  }

 */

  private Mono<Void> handleFormField(FormPartEvent form) {
    System.out.println("handleFormField: " + form.name());
    return Mono.<Void>empty();
    /*
    switch (form.name()) {
      case "json":
        return startNewGroup(form.value());
      case "xml":
        if (current == null)
          return Mono.error(new IllegalStateException("xml before json"));
        current.addXml(form.value());
        return Mono.empty();
      default:
        return Mono.empty(); // ignore other form fields
    }

     */
  }

  private Mono<Void> handleFileField(String filename, Flux<DataBuffer> fileChunks) {
    System.out.println("handleFileField: " + filename);
    return Mono.<Void>empty();
    /*
    if (current == null)
      return Mono.error(new IllegalStateException("file before json"));

    return current.addFile(filename, fileChunks);
    */
  }

  /*
  @PostMapping(path = "/create2", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public Mono<Void> createSip2(ServerWebExchange exchange) {
    SipCreateConsumer consumer = new SipCreateConsumer();

    Flux<SipCreateGroup> groups = Flux.create(sink -> {
      exchange.getMultipartData()
          .flatMapMany(map -> Flux.fromIterable(map.values()))
          .concatMap(Flux::fromIterable)
          .subscribe(
              part -> consumer.accept(part, sink),
              sink::error,
              () -> consumer.complete(sink)
          );
    });

    ApiClient apiClient = ApiClient.builder().baseUrl("http://localhost:8080/")
        .setBasicAuth("admin", "roda").build();

    return groups.concatMap(group -> {
      Flux<DataBuffer> zipFlux = PgipSipBuilder2.build(group);
      String name = UUID.randomUUID().toString() + ".zip";
      return apiClient.uploadTransferredResource(name, zipFlux);
    }).then();
  }
  */

  /*
  // WIP Test Route
  @PostMapping(path = "/zip", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<String> createZip(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    JakartaServletDiskFileUpload upload = newServletDiskFileUpload();

    SipZipField lastField = SipZipField.NONE;

    Path targetPath = UPLOAD_DIR.resolve("zip").resolve(UUID.randomUUID() + ".zip");

    try (
        FileOutputStream fileOutputStream = new FileOutputStream(targetPath.toFile());
        ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)
    ) {
      zipOutputStream.setLevel(0);

      FileItemInputIterator iter = upload.getItemIterator(request);
      while (iter.hasNext()) {
        FileItemInput item = iter.next();

        SipZipField currentField = SipZipField.fromString(item.getFieldName());
        if (!lastField.nextValidFields().contains(currentField)) {
          String expectedFields = lastField.nextValidFields().stream()
              .map(f -> String.format("\"%s\"", f.getFieldName()))
              .collect(Collectors.joining(" || "));

          return ResponseEntity.badRequest().body(
              String.format("Encountered unexpected field \"%s\", expected: %s",
                  item.getFieldName(), expectedFields));
        }

        try (InputStream inputStream = item.getInputStream()) {
          switch (currentField) {
            case JOB:
              break;

            case FILE:
              ZipEntry zipEntry = createZipEntry(item.getName());
              zipOutputStream.putNextEntry(zipEntry);
              inputStream.transferTo(zipOutputStream);
              zipOutputStream.closeEntry();
              zipOutputStream.flush();
              break;

            default:
              return ResponseEntity.badRequest()
                  .body(String.format("Encountered unexpected field \"%s\"", item.getFieldName()));
          }
        }

        lastField = currentField;
      }

    }

    return ResponseEntity.ok("Upload successful");
  }

   */
}
