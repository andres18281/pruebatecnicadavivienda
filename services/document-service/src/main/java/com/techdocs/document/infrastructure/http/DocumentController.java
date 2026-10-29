package com.techdocs.document.infrastructure.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techdocs.document.application.commands.BatchUploadCommand;
import com.techdocs.document.application.commands.UploadCommand;
import com.techdocs.document.application.ports.in.GetDocumentDetailUseCase;
import com.techdocs.document.application.ports.in.GetDocumentFileUseCase;
import com.techdocs.document.application.ports.in.GetDocumentStatusUseCase;
import com.techdocs.document.application.ports.in.UploadBatchUseCase;
import com.techdocs.document.application.ports.in.UploadDocumentUseCase;
import com.techdocs.document.application.views.BatchReceipt;
import com.techdocs.document.application.views.DocumentDetail;
import com.techdocs.document.application.views.DocumentFile;
import com.techdocs.document.application.views.StatusView;
import com.techdocs.document.application.views.UploadReceipt;
import com.techdocs.document.domain.errors.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final UploadDocumentUseCase uploadDocument;
    private final UploadBatchUseCase uploadBatch;
    private final GetDocumentDetailUseCase detail;
    private final GetDocumentFileUseCase file;
    private final GetDocumentStatusUseCase status;
    private final ObjectMapper objectMapper;

    public DocumentController(
            UploadDocumentUseCase uploadDocument,
            UploadBatchUseCase uploadBatch,
            GetDocumentDetailUseCase detail,
            GetDocumentFileUseCase file,
            GetDocumentStatusUseCase status,
            ObjectMapper objectMapper
    ) {
        this.uploadDocument = uploadDocument;
        this.uploadBatch = uploadBatch;
        this.detail = detail;
        this.file = file;
        this.status = status;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadReceipt> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("titulo") String titulo,
            @RequestParam("autor") String autor,
            @RequestParam("categoria") String categoria,
            @RequestParam("version") String version,
            @RequestParam(value = "etiquetas", required = false) String etiquetas,
            @RequestHeader(value = "traceparent", required = false) String traceparent
    ) throws IOException {
        UploadReceipt receipt = uploadDocument.execute(new UploadCommand(
                file.getOriginalFilename(),
                file.getBytes(),
                titulo,
                autor,
                categoria,
                etiquetas,
                version,
                traceparent
        ));
        log.info("evento=carga id={} estado={} archivo={}", receipt.id(), receipt.estado(), file.getOriginalFilename());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(receipt);
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<BatchReceipt>> batch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam("manifest") String manifest,
            @RequestHeader(value = "traceparent", required = false) String traceparent
    ) throws IOException {
        List<BatchReceipt> receipts = uploadBatch.execute(new BatchUploadCommand(match(files, manifest, traceparent)));
        log.info("evento=carga-masiva archivos={} aceptados={}", files.size(), receipts.size());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(receipts);
    }

    @GetMapping("/{id}")
    public DocumentDetail get(@PathVariable UUID id) {
        return detail.execute(id);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable UUID id) {
        DocumentFile document = file.execute(id);
        MediaType mediaType = MediaType.parseMediaType(document.mimeType());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(document.nombreArchivo(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(document.content());
    }

    @GetMapping("/{id}/status")
    public StatusView status(@PathVariable UUID id) {
        return status.execute(id);
    }

    private List<UploadCommand> match(List<MultipartFile> files, String manifest, String traceparent) throws IOException {
        JsonNode root;
        try {
            root = objectMapper.readTree(manifest);
        } catch (IOException exception) {
            throw new DomainException.InvalidMetadata("El manifiesto no es JSON valido.");
        }
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw new DomainException.InvalidMetadata("El manifiesto debe ser una lista de metadatos.");
        }
        Map<String, MultipartFile> byName = new HashMap<>();
        for (MultipartFile file : files) {
            String name = file.getOriginalFilename();
            if (name == null || name.isBlank()) {
                throw new DomainException.InvalidMetadata("Cada archivo de la carga masiva necesita nombre.");
            }
            if (byName.put(name, file) != null) {
                throw new DomainException.InvalidMetadata("Hay archivos con el mismo nombre: " + name);
            }
        }
        if (byName.size() != root.size()) {
            throw new DomainException.InvalidMetadata("El manifiesto y los archivos no coinciden.");
        }
        List<UploadCommand> commands = new ArrayList<>();
        for (JsonNode entry : root) {
            String fileName = text(entry, "nombreArchivo");
            MultipartFile file = byName.remove(fileName);
            if (file == null) {
                throw new DomainException.InvalidMetadata("No llego el archivo " + fileName);
            }
            commands.add(new UploadCommand(
                    file.getOriginalFilename(),
                    file.getBytes(),
                    text(entry, "titulo"),
                    text(entry, "autor"),
                    text(entry, "categoria"),
                    tags(entry),
                    text(entry, "version"),
                    traceparent
            ));
        }
        return commands;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String tags(JsonNode node) {
        JsonNode value = node.get("etiquetas");
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isArray()) {
            List<String> tags = new ArrayList<>();
            value.forEach(item -> tags.add(item.asText()));
            return String.join(",", tags);
        }
        return value.asText();
    }
}
