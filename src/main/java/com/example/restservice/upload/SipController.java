package com.example.restservice.upload;

import com.example.restservice.eterna.ApiClient;
import com.example.restservice.eterna.ApiResult;
import com.example.restservice.model.upload.Job;
import com.example.restservice.model.upload.SipCreateField;
import com.example.restservice.model.upload.SipUploadField;
import com.example.restservice.model.upload.SipZipField;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload2.core.FileItemInput;
import org.apache.commons.fileupload2.core.FileItemInputIterator;
import org.apache.commons.fileupload2.jakarta.servlet6.JakartaServletDiskFileUpload;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

  @PostMapping(path = "/upload", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<String> uploadSip(HttpServletRequest request, HttpServletResponse response) throws IOException {
    JakartaServletDiskFileUpload upload = newServletDiskFileUpload();

    ApiClient apiClient = ApiClient.builder()
            .baseUrl("http://localhost:8080/")
            .setBasicAuth("admin", "roda")
            .build();

    Job job = null;
    SipUploadField lastField = SipUploadField.NONE;

    FileItemInputIterator iter = upload.getItemIterator(request);
    while (iter.hasNext()) {
      FileItemInput item = iter.next();

      SipUploadField currentField = SipUploadField.fromString(item.getFieldName());
      if (!lastField.nextValidFields().contains(currentField)) {
        String expectedFields = lastField.nextValidFields().stream().map(f -> String.format("\"%s\"", f.getFieldName())).collect(Collectors.joining(" || "));
        return ResponseEntity.badRequest().body(String.format("Encountered unexpected field \"%s\", expected: %s", item.getFieldName(), expectedFields));
      }

      ObjectMapper objectMapper = new ObjectMapper();
      JavaType javaType = objectMapper.getTypeFactory().constructParametricType(ApiResult.class, Job.class);

      try (InputStream inputStream = item.getInputStream()) {
        switch (currentField) {
          case JOB:
            job = objectMapper.readValue(inputStream, javaType);
            break;

          case SIP:
            apiClient.uploadTransferredResource(item.getName(), inputStream);
            break;

          default:
            return ResponseEntity.badRequest().body(String.format("Encountered unexpected field \"%s\"", item.getFieldName()));
        }

      }

      lastField = currentField;
    }

    return ResponseEntity.ok("Upload successful!\nTest: " + job.getTest() + "\n");
  }

  @PostMapping(path = "/create", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<String> createSip(HttpServletRequest request, HttpServletResponse response) throws IOException {
    JakartaServletDiskFileUpload upload = newServletDiskFileUpload();

    SipCreateField lastField = SipCreateField.NONE;
    SipCreateField currentField = null;

    FileItemInputIterator iter = upload.getItemIterator(request);
    while (iter.hasNext()) {
      FileItemInput item = iter.next();

      currentField = SipCreateField.fromString(item.getFieldName());
      if (!lastField.nextValidFields().contains(currentField)) {
        String expectedFields = lastField.nextValidFields().stream().map(f -> String.format("\"%s\"", f.getFieldName())).collect(Collectors.joining(" || "));
        return ResponseEntity.badRequest().body(String.format("Encountered unexpected field \"%s\", expected: %s", item.getFieldName(), expectedFields));
      }

      Path targetPath = UPLOAD_DIR.resolve(item.getName());

      switch (currentField) {
        case JOB:
          break;
        case PGIP:
          Files.createDirectories(targetPath.getParent());
          try (InputStream inputStream = item.getInputStream(); OutputStream outputStream = Files.newOutputStream(targetPath)) {
            inputStream.transferTo(outputStream);
          }
          break;
        case FILE:
          Files.createDirectories(targetPath.getParent());
          try (InputStream inputStream = item.getInputStream(); OutputStream outputStream = Files.newOutputStream(targetPath)) {
            inputStream.transferTo(outputStream);
          }
          break;
        default:
          return ResponseEntity.badRequest().body(String.format("Encountered unexpected field \"%s\"", item.getFieldName()));
      }

      lastField = currentField;
    }

    return ResponseEntity.ok("Upload successful");
  }

  // WIP Test Route
  @PostMapping(path = "/zip", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<String> createZip(HttpServletRequest request, HttpServletResponse response) throws IOException {
    JakartaServletDiskFileUpload upload = newServletDiskFileUpload();

    SipZipField lastField = SipZipField.NONE;

    Path targetPath = UPLOAD_DIR.resolve("zip").resolve(UUID.randomUUID().toString() + ".zip");

    try (FileOutputStream fileOutputStream = new FileOutputStream(targetPath.toFile()); ZipOutputStream zipOutputStream = new ZipOutputStream(fileOutputStream)) {
      zipOutputStream.setLevel(0);

      FileItemInputIterator iter = upload.getItemIterator(request);
      while (iter.hasNext()) {
        FileItemInput item = iter.next();

        SipZipField currentField = SipZipField.fromString(item.getFieldName());
        if (!lastField.nextValidFields().contains(currentField)) {
          String expectedFields = lastField.nextValidFields().stream().map(f -> String.format("\"%s\"", f.getFieldName())).collect(Collectors.joining(" || "));
          return ResponseEntity.badRequest().body(String.format("Encountered unexpected field \"%s\", expected: %s", item.getFieldName(), expectedFields));
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
              return ResponseEntity.badRequest().body(String.format("Encountered unexpected field \"%s\"", item.getFieldName()));
          }
        }

        lastField = currentField;
      }

    }

    return ResponseEntity.ok("Upload successful");
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
}
