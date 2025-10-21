package com.project.smart_faq_rag_assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SmartFaqRagAssistantApplication {
	public static void main(String[] args) {
		SpringApplication.run(SmartFaqRagAssistantApplication.class, args);
	}
}