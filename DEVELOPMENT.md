# EarkivExpress - SIP Transfer Service

## Overview

Spring Boot WebFlux service for transferring files and SIPs (Submission Information Packages) to Eterna digital preservation system via REST API.

## What Was Built

### 1. Eterna Integration

- **Connection Configuration**: Configurable Eterna API endpoint with Basic Auth
- **Reactive Upload**: Non-blocking file transfer using Spring WebFlux
- **Transfer API**: Direct integration with Eterna's `/api/v1/transfers/` endpoint

### 2. API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/config/eterna` | GET | Returns current Eterna connection config |
| `/api/sip/upload` | POST | Upload a single file to Eterna |
| `/api/sip/upload-simple` | POST | Simple file upload (no name parameter) |
| `/api/sip/upload-zip` | POST | Upload ZIP archive as SIP |
| `/actuator/health` | GET | Health check endpoint |

### 3. Bug Fixes Applied

Fixed Eterna API URLs from `/controller/v1/transfers/` to `/api/v1/transfers/` in:
- `ApiClient.java`
- `EternaTransferredResourceWriter.java`

### 4. ZIP Validation

- **ZIP validation utility**: `ZipValidator.java` validates ZIP contains pgip.xml
- **XML Schema validation**: `PgipXmlValidator.java` validates pgip.xml against `pgip_1.3.xsd`
- Upload is rejected (400) if:
  - ZIP doesn't contain pgip.xml
  - pgip.xml doesn't conform to the XSD schema

---

## Prerequisites

- **Java 21**
- **Maven** (or use included Maven wrapper)
- **Eterna** instance running and accessible

---

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8085

# Multipart Upload Settings (unlimited size)
spring.servlet.multipart.max-file-size=-1
spring.servlet.multipart.max-request-size=-1
spring.servlet.multipart.resolve-lazily=true

# ETERNA Configuration
eterna.api.base-url=http://localhost:8080
eterna.api.username=admin
eterna.api.password=eterna
```

### Eterna Requirements

Ensure your Eterna instance has:

1. **API Basic Auth enabled** (`roda-core.properties`):
   ```properties
   core.api.basicAuth.disable = false
   ```

2. **User with transfer.create role** - The API user must have the `transfer.create` permission

3. **Network accessibility** - Eterna must be reachable from this service

---

## Running the Service

### Start with default port (8085):

```bash
./mvnw spring-boot:run
```

### Start with custom port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8086"
```

### Start with debug mode:

```bash
./mvnw spring-boot:run -Pdebug
```

---

## Testing

### 1. Health Check

```bash
curl http://localhost:8085/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

### 2. Check Eterna Configuration

```bash
curl http://localhost:8085/api/config/eterna
```

Expected response:
```json
{
  "baseUrl": "http://localhost:8080",
  "username": "admin"
}
```

### 3. Upload a Single File

```bash
curl -F "file=@myfile.txt" \
     -F "name=custom-name.txt" \
     http://localhost:8085/api/sip/upload
```

Expected response:
```
File uploaded successfully: custom-name.txt (Eterna ID: custom-name.txt)
```

### 4. Upload a ZIP as SIP (with validation)

First, create a test ZIP with pgip.xml:
```bash
mkdir -p /tmp/test-sip
cat > /tmp/test-sip/pgip.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<pgip>
  <objectId>TEST-2026-001</objectId>
  <title>Test Submission</title>
  <securityClassification>ÖPPEN</securityClassification>
  <caseType>Test Case</caseType>
  <creator>Test Creator</creator>
  <conformsTo>PGIP 1.3</conformsTo>
  <informationOwner>Test Owner</informationOwner>
  <legalRestriction>No restrictions</legalRestriction>
  <submissionAgreementId>SA-001</submissionAgreementId>
  <type>ARKIV</type>
</pgip>
EOF
echo "Document" > /tmp/test-sip/doc.txt
cd /tmp/test-sip && zip -r /tmp/archive.zip .
```

Then upload:
```bash
curl -F "zip=@/tmp/archive.zip" \
     -F "sipName=my-sip" \
     http://localhost:8085/api/sip/upload-zip
```

**Validation errors:**
- No pgip.xml: `ZIP must contain pgip.xml metadata file`
- Invalid pgip.xml: `Invalid pgip.xml: [validation error message]`

Expected response:
```
ZIP uploaded as SIP: my-sip.zip (Eterna ID: my-sip.zip). Process it in Eterna UI to convert to AIP.
```

### 5. Verify Upload in Eterna

```bash
curl -u admin:eterna http://localhost:8080/api/v1/transfers/
```

Expected response:
```json
{
  "transferred_resources": [
    {
      "uuid": "...",
      "id": "my-sip.zip",
      "fullPath": "/path/to/transferred-resources/my-sip.zip",
      "size": 1234,
      "name": "my-sip.zip",
      "file": true
    }
  ]
}
```

---

## Architecture

```
┌─────────────────┐          ┌──────────────────┐          ┌─────────────┐
│   Client        │  HTTP    │  EarkivExpress   │  HTTP    │   Eterna    │
│  (curl/app)     │ ───────> │  (Spring Boot)   │ ───────> │   (RODA)    │
│                 │  POST    │  - WebFlux       │  POST    │             │
│                 │          │  - WebClient     │  /api/   │  Transfer   │
│                 │          │  - Reactive      │  v1/     │  Resources  │
│                 │          │                  │  transfers│             │
└─────────────────┘          └──────────────────┘          └─────────────┘
```

### Technology Stack

- **Spring Boot 3.2.3** - Application framework
- **Spring WebFlux** - Reactive web framework
- **WebClient** - Non-blocking HTTP client
- **Netty** - Async event-driven network framework
- **commons-ip2** - SIP handling library
- **Apache Commons Compress** - ZIP handling

---

## Files Modified/Created

### New Files
- `src/main/java/com/example/restservice/controller/UploadSipController.java` - Upload endpoints
- `src/main/java/com/example/restservice/util/ZipValidator.java` - ZIP validation utility
- `src/main/java/com/example/restservice/util/PgipXmlValidator.java` - XML schema validation

### Modified Files
- `src/main/java/com/example/restservice/eterna/ApiClient.java` - Fixed API URLs
- `src/main/java/com/example/restservice/writers/EternaTransferredResourceWriter.java` - Fixed API URLs
- `src/main/resources/application.properties` - Added Eterna configuration

---

## Workflow

1. **Upload** - Send file/ZIP to this service via REST API
2. **Transfer** - Service streams content to Eterna's transfer area
3. **Ingest** - In Eterna UI, create an ingest job to process transferred resources
4. **AIP Creation** - Eterna converts transferred resources to AIPs (Archival Information Packages)

---

## Troubleshooting

### Connection Refused

**Error**: `Connection refused: localhost/127.0.0.1:8080`

**Solution**: Ensure Eterna is running on the configured port

### 404 Not Found

**Error**: `404 Not Found from POST http://localhost:8080/controller/v1/transfers/`

**Solution**: The API URL was fixed to `/api/v1/transfers/`. Ensure you're using the latest code.

### Block() Not Supported Error

**Error**: `block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-*`

**Solution**: This was fixed by using proper reactive patterns. Ensure you're using the latest code.

### Authentication Failed

**Error**: `401 Unauthorized`

**Solution**: Verify credentials in `application.properties` match Eterna's user database

---

## Development Notes

### WebFlux Reactive Patterns

This service uses Spring WebFlux, which requires non-blocking reactive code:

- **No `.block()` calls** in reactive threads
- **Use `Mono` and `Flux`** for async operations
- **Use `subscribeOn(Schedulers.boundedElastic())`** for blocking operations
- **Multipart uploads** use `FilePart` instead of `MultipartFile`

### Key Classes

- `UploadSipController` - REST endpoints for uploads
- `ApiClient` - WebClient wrapper for Eterna API
- `EternaConfig` - Configuration and ApiClient factory
- `EternaTransferredResourceWriter` - Streaming SIP writer

---

## Next Steps

1. **Add validation** - Validate file types and sizes
2. **Add metadata** - Support PGIP metadata in uploads
3. **Add progress tracking** - Track upload progress for large files
4. **Add error recovery** - Retry failed uploads
5. **Add logging** - Structured logging for audit trails
