package com.project.smart_faq_rag_assistant.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    @Value("${app.preload.faqs:false}")
    private boolean shouldPreloadFaqs;

    private final EmbeddingService embeddingService;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private volatile boolean isReady = false;

    public RagService(EmbeddingService embeddingService, ChatClient chatClient, VectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void loadDocuments() {
        if (!shouldPreloadFaqs) {
            log.info("FAQ preloading is disabled. System is ready for user uploads.");
            isReady = true;
            return;
        }

        try {
            log.info("Loading FAQs from static resources...");
            ObjectMapper mapper = new ObjectMapper();
            InputStream input = getClass().getResourceAsStream("/data/faqs.json");

            if (input == null) {
                log.warn("Could not find /data/faqs.json — continuing without preloaded FAQs.");
                isReady = true;
                return;
            }

            List<String> faqs = mapper.readValue(input, new TypeReference<>() {});
            log.info("Found {} FAQs to process", faqs.size());

            for (int i = 0; i < faqs.size(); i++) {
                String faq = faqs.get(i);
                log.info("Processing FAQ {}/{}", i + 1, faqs.size());

                Document doc = new Document(faq, Map.of(
                        "source", "faq-preloaded",
                        "index", String.valueOf(i)
                ));
                vectorStore.add(List.of(doc));
            }

            isReady = true;
            log.info("✅ All {} FAQs loaded successfully!", faqs.size());

        } catch (Exception e) {
            log.error("Failed to load FAQs", e);
            isReady = true;
        }
    }

    public String generateAnswer(String userQuestion) {
        if (!isReady) {
            return "⏳ System is still initializing. Please try again shortly.";
        }

        try {
            log.info("Question: {}", userQuestion);

            // similarity search via vector store (top 3)
            SearchRequest req = SearchRequest.query(userQuestion).withTopK(3);
            List<Document> hits = vectorStore.similaritySearch(req);

            if (hits.isEmpty()) {
                return "❓ No documents found in the knowledge base. Please upload documents first.";
            }

            String context = hits.stream()
                    .map(d -> d.getContent().toString())
                    .reduce("", (a, b) -> a + "\n\n" + b).trim();

            String prompt = String.format(
                    "You are an assistant. Answer the question using ONLY the context below. " +
                            "If the answer is not contained in the context, say 'Not in the uploaded documents.'\n\n" +
                            "CONTEXT:\n%s\n\nQuestion: %s\n\nAnswer:",
                    context, userQuestion
            );

            String answer = chatClient.prompt().user(prompt).call().content();
            log.info("Generated answer (len:{}).", answer.length());
            return answer;

        } catch (org.springframework.web.client.RestClientException e) {
            log.error("API connection error: {}", e.getMessage());
            if (e.getMessage().contains("authentication")) {
                return "❌ API authentication failed. Please check your API key.";
            }
            return "❌ Error connecting to AI service: " + e.getMessage();
        } catch (Exception e) {
            log.error("Error generating answer", e);
            return "Sorry, I encountered an error: " + e.getMessage();
        }
    }

    public boolean isReady() {
        return isReady;
    }
}