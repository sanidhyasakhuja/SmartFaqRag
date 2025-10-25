package com.project.smart_faq_rag_assistant.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final int CHUNK_SIZE = 500; // Reduced from 1000 to 500
    private static final int CHUNK_OVERLAP = 100; // Reduced from 200 to 100
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024; // 5MB of text
    private static final int BATCH_SIZE = 10; // Reduced from 50 to 10 for embeddings

    private final VectorStore vectorStore;
    private final AtomicInteger documentCounter = new AtomicInteger(0);
    private final Map<String, Integer> documentStats = Collections.synchronizedMap(new HashMap<>());

    public DocumentService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Process and store a file (supports .txt, .pdf, .md)
     */
    public int processAndStoreFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.info("Processing file: {} (size: {} bytes)", filename, fileSize);

        if (fileSize > MAX_FILE_SIZE) {
            throw new IOException("File too large. Maximum size is " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }

        if (fileSize == 0) {
            throw new IOException("File is empty");
        }

        String content;
        try {
            if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
                content = extractTextFromPdf(file.getInputStream());
            } else {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (OutOfMemoryError e) {
            log.error("Out of memory while reading file: {}", filename);
            throw new IOException("File too large to process. Try a smaller file.");
        }

        if (content.length() > MAX_TEXT_LENGTH) {
            throw new IOException("Text content too large. Maximum length is " + (MAX_TEXT_LENGTH / 1024 / 1024) + "MB");
        }

        return processAndStoreText(content, filename);
    }

    /**
     * Process and store raw text content with aggressive memory management
     */
    public int processAndStoreText(String text, String source) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Empty text provided for source: {}", source);
            return 0;
        }

        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Text too large. Maximum length is " + (MAX_TEXT_LENGTH / 1024 / 1024) + "MB");
        }

        log.info("Processing text from '{}' (length: {} chars)", source, text.length());

        List<String> chunks = chunkText(text);
        log.info("Split '{}' into {} chunks", source, chunks.size());

        if (chunks.isEmpty()) {
            log.warn("No chunks created from '{}'", source);
            return 0;
        }

        int totalStored = 0;

        // Process chunks one at a time to minimize memory usage
        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            int endIdx = Math.min(i + BATCH_SIZE, chunks.size());

            List<Document> documents = new ArrayList<>();
            for (int j = i; j < endIdx; j++) {
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", source);
                metadata.put("chunk", j);
                metadata.put("total_chunks", chunks.size());
                metadata.put("timestamp", System.currentTimeMillis());

                documents.add(new Document(chunks.get(j), metadata));
            }

            try {
                vectorStore.add(documents);
                totalStored += documents.size();
                log.info("Stored batch {}-{} of {} chunks from '{}'", i, endIdx - 1, chunks.size(), source);

                // Clear references to help GC
                documents.clear();
                documents = null;

                // Small delay to allow garbage collection
                if (i + BATCH_SIZE < chunks.size()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

            } catch (OutOfMemoryError e) {
                log.error("Out of memory storing batch {}-{}", i, endIdx - 1);
                System.gc();
                throw new RuntimeException("Out of memory while storing documents. Try smaller files or restart the server.");
            } catch (Exception e) {
                log.error("Error storing batch {}-{}", i, endIdx - 1, e);
                throw new RuntimeException("Failed to store documents: " + e.getMessage());
            }
        }

        // Clear chunks list
        chunks.clear();

        documentCounter.addAndGet(totalStored);
        documentStats.put(source, totalStored);

        log.info("Successfully stored {} chunks from '{}'", totalStored, source);

        // Force garbage collection after processing
        System.gc();

        return totalStored;
    }

    /**
     * Process FAQs list
     */
    public int processFaqs(List<String> faqs) {
        log.info("Processing {} FAQs", faqs.size());

        if (faqs.isEmpty()) {
            return 0;
        }

        int totalStored = 0;

        // Process FAQs in small batches
        for (int i = 0; i < faqs.size(); i += BATCH_SIZE) {
            int endIdx = Math.min(i + BATCH_SIZE, faqs.size());

            List<Document> documents = new ArrayList<>();
            for (int j = i; j < endIdx; j++) {
                String faq = faqs.get(j).trim();
                if (faq.isEmpty()) {
                    continue;
                }

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", "faq");
                metadata.put("index", j);
                metadata.put("timestamp", System.currentTimeMillis());

                documents.add(new Document(faq, metadata));
            }

            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                totalStored += documents.size();
            }

            documents.clear();
        }

        documentCounter.addAndGet(totalStored);
        documentStats.put("faqs", totalStored);

        log.info("Stored {} FAQs", totalStored);
        System.gc();

        return totalStored;
    }

    /**
     * Get statistics about stored documents
     */
    public Map<String, Object> getStatistics() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemory = runtime.maxMemory() / (1024 * 1024);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalChunks", documentCounter.get());
        stats.put("documentBreakdown", new HashMap<>(documentStats));
        stats.put("timestamp", System.currentTimeMillis());
        stats.put("memoryUsedMB", usedMemory);
        stats.put("memoryMaxMB", maxMemory);
        stats.put("memoryUsagePercent", (usedMemory * 100) / maxMemory);

        return stats;
    }

    /**
     * Clear all documents
     */
    public void clearAllDocuments() {
        documentCounter.set(0);
        documentStats.clear();
        log.info("Cleared all document statistics");
        System.gc();
    }

    /**
     * OPTIMIZED: Split text into chunks with overlap
     */
    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int textLength = text.length();
        int position = 0;
        int maxChunks = (textLength / CHUNK_SIZE) + 100; // Safety limit
        int chunkCount = 0;

        while (position < textLength && chunkCount < maxChunks) {
            int endPosition = Math.min(position + CHUNK_SIZE, textLength);

            if (endPosition < textLength) {
                // Find sentence boundary
                int lastPeriod = findLastBoundary(text, position, endPosition, '.', '!', '?');
                int lastNewline = findLastBoundary(text, position, endPosition, '\n');

                int breakPoint = Math.max(lastPeriod, lastNewline);

                if (breakPoint > position + (CHUNK_SIZE / 2)) {
                    endPosition = breakPoint + 1;
                } else {
                    // Try word boundary
                    int lastSpace = findLastBoundary(text, position, endPosition, ' ');
                    if (lastSpace > position + (CHUNK_SIZE / 2)) {
                        endPosition = lastSpace;
                    }
                }
            }

            String chunk = text.substring(position, endPosition).trim();

            if (!chunk.isEmpty()) {
                chunks.add(chunk);
                chunkCount++;
            }

            // Move forward with overlap
            int nextPosition = endPosition - CHUNK_OVERLAP;

            // Ensure progress
            if (nextPosition <= position) {
                nextPosition = endPosition;
            }

            position = nextPosition;
        }

        return chunks;
    }

    /**
     * Find the last occurrence of boundary characters
     */
    private int findLastBoundary(String text, int start, int end, char... chars) {
        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            for (char boundary : chars) {
                if (c == boundary) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Extract text from PDF with memory optimization
     */
    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        PDDocument document = null;
        try {
            document = PDDocument.load(inputStream);

            int pageCount = document.getNumberOfPages();
            log.info("PDF has {} pages", pageCount);

            if (pageCount > 200) {
                throw new IOException("PDF too large. Maximum 200 pages allowed.");
            }

            PDFTextStripper stripper = new PDFTextStripper();

            // Extract text page by page to reduce memory usage
            StringBuilder fullText = new StringBuilder();
            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                fullText.append(pageText);

                // Check size periodically
                if (fullText.length() > MAX_TEXT_LENGTH) {
                    throw new IOException("PDF content too large. Maximum " + (MAX_TEXT_LENGTH / 1024 / 1024) + "MB of text allowed.");
                }
            }

            String text = fullText.toString();
            fullText = null; // Help GC

            return text;

        } catch (OutOfMemoryError e) {
            log.error("Out of memory while processing PDF");
            throw new IOException("PDF too large to process in memory");
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (IOException e) {
                    log.warn("Error closing PDF document", e);
                }
            }
        }
    }
}