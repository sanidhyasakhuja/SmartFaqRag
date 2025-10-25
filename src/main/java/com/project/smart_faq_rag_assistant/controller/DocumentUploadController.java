package com.project.smart_faq_rag_assistant.controller;

import com.project.smart_faq_rag_assistant.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "*")
public class DocumentUploadController {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadController.class);
    private final DocumentService documentService;

    public DocumentUploadController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Upload a single text/PDF file
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File is empty"));
            }

            log.info("Uploading file: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());

            int count = documentService.processAndStoreFile(file);

            return ResponseEntity.ok(Map.of(
                    "message", "File processed successfully",
                    "filename", file.getOriginalFilename(),
                    "chunksStored", count
            ));

        } catch (OutOfMemoryError e) {
            log.error("Out of memory error processing file: {}", file.getOriginalFilename());
            System.gc(); // Suggest garbage collection
            return ResponseEntity.status(507) // Insufficient Storage
                    .body(Map.of("error", "File too large. Server ran out of memory. Try a smaller file."));

        } catch (IllegalArgumentException e) {
            log.error("Invalid argument: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error processing file: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process file: " + e.getMessage()));
        }
    }

    /**
     * Upload multiple files at once
     */
    @PostMapping("/upload/batch")
    public ResponseEntity<Map<String, Object>> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files.length == 0) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No files provided"));
            }

            log.info("Uploading {} files", files.length);

            int totalChunks = 0;
            int successCount = 0;
            StringBuilder errors = new StringBuilder();

            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }

                try {
                    int chunks = documentService.processAndStoreFile(file);
                    totalChunks += chunks;
                    successCount++;
                    log.info("Successfully processed: {}", file.getOriginalFilename());

                } catch (OutOfMemoryError e) {
                    log.error("Out of memory on file: {}", file.getOriginalFilename());
                    System.gc();
                    errors.append(file.getOriginalFilename()).append(": Out of memory. ");

                } catch (Exception e) {
                    log.error("Error processing file: {}", file.getOriginalFilename(), e);
                    errors.append(file.getOriginalFilename()).append(": ").append(e.getMessage()).append(". ");
                }
            }

            if (successCount == 0) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "Failed to process any files: " + errors.toString()));
            }

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("message", "Processed " + successCount + " of " + files.length + " files");
            response.put("filesCount", successCount);
            response.put("totalChunksStored", totalChunks);

            if (errors.length() > 0) {
                response.put("warnings", errors.toString());
            }

            return ResponseEntity.ok(response);

        } catch (OutOfMemoryError e) {
            log.error("Out of memory during batch upload");
            System.gc();
            return ResponseEntity.status(507)
                    .body(Map.of("error", "Server ran out of memory. Try uploading fewer or smaller files."));

        } catch (Exception e) {
            log.error("Error in batch upload", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process files: " + e.getMessage()));
        }
    }

    /**
     * Upload raw text content
     */
    @PostMapping("/upload/text")
    public ResponseEntity<Map<String, Object>> uploadText(@RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            String title = request.getOrDefault("title", "User Text");

            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Text cannot be empty"));
            }

            log.info("Uploading text: {} (length: {} chars)", title, text.length());

            int count = documentService.processAndStoreText(text, title);

            return ResponseEntity.ok(Map.of(
                    "message", "Text processed successfully",
                    "chunksStored", count
            ));

        } catch (OutOfMemoryError e) {
            log.error("Out of memory processing text");
            System.gc();
            return ResponseEntity.status(507)
                    .body(Map.of("error", "Text too large. Server ran out of memory."));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Error processing text", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process text: " + e.getMessage()));
        }
    }

    /**
     * Upload FAQs in JSON format
     */
    @PostMapping("/upload/faqs")
    public ResponseEntity<Map<String, Object>> uploadFaqs(@RequestBody Map<String, List<String>> request) {
        try {
            List<String> faqs = request.get("faqs");
            if (faqs == null || faqs.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "FAQs list cannot be empty"));
            }

            log.info("Uploading {} FAQs", faqs.size());

            int count = documentService.processFaqs(faqs);

            return ResponseEntity.ok(Map.of(
                    "message", "FAQs processed successfully",
                    "faqsStored", count
            ));

        } catch (OutOfMemoryError e) {
            log.error("Out of memory processing FAQs");
            System.gc();
            return ResponseEntity.status(507)
                    .body(Map.of("error", "Too many FAQs. Server ran out of memory."));

        } catch (Exception e) {
            log.error("Error processing FAQs", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to process FAQs: " + e.getMessage()));
        }
    }

    /**
     * Get statistics about stored documents
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = documentService.getStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting stats", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to get statistics: " + e.getMessage()));
        }
    }

    /**
     * Clear all documents from vector store
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearDocuments() {
        try {
            documentService.clearAllDocuments();
            System.gc(); // Suggest garbage collection after clearing
            return ResponseEntity.ok(Map.of("message", "All documents cleared successfully"));
        } catch (Exception e) {
            log.error("Error clearing documents", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to clear documents: " + e.getMessage()));
        }
    }
}