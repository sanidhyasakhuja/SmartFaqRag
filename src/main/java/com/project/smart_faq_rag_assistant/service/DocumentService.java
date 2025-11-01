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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    // Configuration constants
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 100;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024; // 5MB
    private static final int BATCH_SIZE = 10;
    private static final int MAX_PDF_PAGES = 200;
    private static final long BATCH_DELAY_MS = 100;

    // Thread-safe collections
    private final VectorStore vectorStore;
    private final AtomicInteger documentCounter = new AtomicInteger(0);
    private final Map<String, Integer> documentStats = new ConcurrentHashMap<>();
    private final Map<String, List<String>> documentIdsBySource = new ConcurrentHashMap<>();
    private final ReentrantLock processingLock = new ReentrantLock();

    public DocumentService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Process and store a file with validation and error handling
     */
    public int processAndStoreFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        long fileSize = file.getSize();

        log.info("Processing file: {} (size: {} bytes)", filename, fileSize);

        // Validation
        validateFile(file, filename, fileSize);

        String content;
        try {
            content = extractContent(file, filename);
        } catch (OutOfMemoryError e) {
            log.error("Out of memory while reading file: {}", filename);
            throw new IOException("File too large to process. Please try a smaller file.");
        }

        validateContentSize(content);

        return processAndStoreText(content, filename);
    }

    /**
     * Process and store text with batch processing and memory management
     */
    public int processAndStoreText(String text, String source) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Empty text provided for source: {}", source);
            return 0;
        }

        validateContentSize(text);

        log.info("Processing text from '{}' (length: {} chars)", source, text.length());

        processingLock.lock();
        try {
            List<String> chunks = chunkText(text);
            log.info("Split '{}' into {} chunks", source, chunks.size());

            if (chunks.isEmpty()) {
                log.warn("No chunks created from '{}'", source);
                return 0;
            }

            int totalStored = processBatches(chunks, source);

            documentCounter.addAndGet(totalStored);
            documentStats.put(source, totalStored);

            log.info("Successfully stored {} chunks from '{}'", totalStored, source);
            System.gc();

            return totalStored;

        } finally {
            processingLock.unlock();
        }
    }

    /**
     * Process FAQs with batch processing
     */
    public int processFaqs(List<String> faqs) {
        if (faqs == null || faqs.isEmpty()) {
            log.warn("Empty FAQ list provided");
            return 0;
        }

        log.info("Processing {} FAQs", faqs.size());

        processingLock.lock();
        try {
            int totalStored = 0;
            List<String> docIds = new ArrayList<>();

            for (int i = 0; i < faqs.size(); i += BATCH_SIZE) {
                int endIdx = Math.min(i + BATCH_SIZE, faqs.size());

                List<Document> documents = new ArrayList<>();
                for (int j = i; j < endIdx; j++) {
                    String faq = faqs.get(j).trim();
                    if (faq.isEmpty()) continue;

                    String docId = generateDocumentId("faq", j);
                    Map<String, Object> metadata = createMetadata("faq", j, faqs.size());

                    Document doc = new Document(docId, faq, metadata);
                    documents.add(doc);
                    docIds.add(docId);
                }

                if (!documents.isEmpty()) {
                    vectorStore.add(documents);
                    totalStored += documents.size();
                    log.debug("Stored FAQ batch {}-{}", i, endIdx - 1);

                    documents.clear();
                    delayForGC();
                }
            }

            documentCounter.addAndGet(totalStored);
            documentStats.put("faqs", totalStored);
            documentIdsBySource.put("faqs", docIds);

            log.info("Stored {} FAQs", totalStored);
            System.gc();

            return totalStored;

        } finally {
            processingLock.unlock();
        }
    }

    /**
     * Delete documents by source name
     */
    public boolean deleteDocumentsBySource(String source) {
        processingLock.lock();
        try {
            List<String> docIds = documentIdsBySource.get(source);
            if (docIds == null || docIds.isEmpty()) {
                log.warn("No documents found for source: {}", source);
                return false;
            }

            log.info("Deleting {} documents from source: {}", docIds.size(), source);

            // Delete from vector store
            vectorStore.delete(docIds);

            // Update statistics
            Integer count = documentStats.remove(source);
            if (count != null) {
                documentCounter.addAndGet(-count);
            }
            documentIdsBySource.remove(source);

            log.info("Deleted {} documents from source: {}", docIds.size(), source);
            System.gc();

            return true;

        } catch (Exception e) {
            log.error("Error deleting documents from source: {}", source, e);
            return false;
        } finally {
            processingLock.unlock();
        }
    }

    /**
     * Clear ALL documents from vector store
     */
    public void clearAllDocuments() {
        processingLock.lock();
        try {
            log.info("Clearing all documents from vector store");

            // Delete all documents from vector store
            List<String> allDocIds = new ArrayList<>();
            documentIdsBySource.values().forEach(allDocIds::addAll);

            if (!allDocIds.isEmpty()) {
                log.info("Deleting {} documents from vector store", allDocIds.size());
                vectorStore.delete(allDocIds);
            }

            // Clear all statistics
            documentCounter.set(0);
            documentStats.clear();
            documentIdsBySource.clear();

            log.info("All documents cleared successfully");
            System.gc();

        } catch (Exception e) {
            log.error("Error clearing documents", e);
            throw new RuntimeException("Failed to clear documents: " + e.getMessage());
        } finally {
            processingLock.unlock();
        }
    }

    /**
     * Get comprehensive statistics
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
        stats.put("sources", new ArrayList<>(documentStats.keySet()));

        return stats;
    }

    /**
     * List all document sources
     */
    public List<String> listSources() {
        return new ArrayList<>(documentStats.keySet());
    }

    // ==================== Private Helper Methods ====================

    private void validateFile(MultipartFile file, String filename, long fileSize) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("File is empty");
        }

        if (fileSize > MAX_FILE_SIZE) {
            throw new IOException(String.format(
                    "File too large (%d bytes). Maximum size is %d MB",
                    fileSize, MAX_FILE_SIZE / (1024 * 1024)
            ));
        }

        if (filename == null || filename.trim().isEmpty()) {
            throw new IOException("Invalid filename");
        }
    }

    private void validateContentSize(String content) {
        if (content.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(String.format(
                    "Content too large (%d chars). Maximum length is %d MB",
                    content.length(), MAX_TEXT_LENGTH / (1024 * 1024)
            ));
        }
    }

    private String extractContent(MultipartFile file, String filename) throws IOException {
        if (filename.toLowerCase().endsWith(".pdf")) {
            return extractTextFromPdf(file.getInputStream());
        } else {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }

    private int processBatches(List<String> chunks, String source) {
        int totalStored = 0;
        List<String> docIds = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
            int endIdx = Math.min(i + BATCH_SIZE, chunks.size());

            List<Document> documents = new ArrayList<>();
            for (int j = i; j < endIdx; j++) {
                String docId = generateDocumentId(source, j);
                Map<String, Object> metadata = createMetadata(source, j, chunks.size());

                Document doc = new Document(docId, chunks.get(j), metadata);
                documents.add(doc);
                docIds.add(docId);
            }

            try {
                vectorStore.add(documents);
                totalStored += documents.size();
                log.debug("Stored batch {}-{} of {} chunks from '{}'",
                        i, endIdx - 1, chunks.size(), source);

                documents.clear();
                delayForGC();

            } catch (OutOfMemoryError e) {
                log.error("Out of memory storing batch {}-{}", i, endIdx - 1);
                System.gc();
                throw new RuntimeException(
                        "Out of memory while storing documents. Try smaller files or restart the server."
                );
            } catch (Exception e) {
                log.error("Error storing batch {}-{}", i, endIdx - 1, e);
                throw new RuntimeException("Failed to store documents: " + e.getMessage());
            }
        }

        documentIdsBySource.put(source, docIds);
        return totalStored;
    }

    private String generateDocumentId(String source, int index) {
        return String.format("%s_%d_%d",
                source.replaceAll("[^a-zA-Z0-9]", "_"),
                index,
                System.currentTimeMillis()
        );
    }

    private Map<String, Object> createMetadata(String source, int index, int total) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", source);
        metadata.put("chunk", index);
        metadata.put("total_chunks", total);
        metadata.put("timestamp", System.currentTimeMillis());
        return metadata;
    }

    private void delayForGC() {
        if (BATCH_DELAY_MS > 0) {
            try {
                Thread.sleep(BATCH_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int textLength = text.length();
        int position = 0;
        int maxChunks = (textLength / CHUNK_SIZE) + 100;
        int chunkCount = 0;

        while (position < textLength && chunkCount < maxChunks) {
            int endPosition = Math.min(position + CHUNK_SIZE, textLength);

            if (endPosition < textLength) {
                int lastPeriod = findLastBoundary(text, position, endPosition, '.', '!', '?');
                int lastNewline = findLastBoundary(text, position, endPosition, '\n');
                int breakPoint = Math.max(lastPeriod, lastNewline);

                if (breakPoint > position + (CHUNK_SIZE / 2)) {
                    endPosition = breakPoint + 1;
                } else {
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

            int nextPosition = endPosition - CHUNK_OVERLAP;
            if (nextPosition <= position) {
                nextPosition = endPosition;
            }

            position = nextPosition;
        }

        return chunks;
    }

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

    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        PDDocument document = null;
        try {
            document = PDDocument.load(inputStream);

            int pageCount = document.getNumberOfPages();
            log.info("PDF has {} pages", pageCount);

            if (pageCount > MAX_PDF_PAGES) {
                throw new IOException(String.format(
                        "PDF too large (%d pages). Maximum %d pages allowed.",
                        pageCount, MAX_PDF_PAGES
                ));
            }

            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder fullText = new StringBuilder();

            for (int i = 1; i <= pageCount; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String pageText = stripper.getText(document);
                fullText.append(pageText);

                if (fullText.length() > MAX_TEXT_LENGTH) {
                    throw new IOException(String.format(
                            "PDF content too large. Maximum %d MB of text allowed.",
                            MAX_TEXT_LENGTH / (1024 * 1024)
                    ));
                }
            }

            return fullText.toString();

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