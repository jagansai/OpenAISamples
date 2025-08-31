package com.example.ragloader;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
public class AIConfig {

    private final Logger logger = Logger.getLogger(AIConfig.class.getName());

    @Value("classpath:/docs/contact.md")
    private Resource contactDoc;

    @Bean
    VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    ApplicationRunner applicationRunner(VectorStore vectorStore) {
        return args -> {
            loadDocument(vectorStore, contactDoc);
        };
    }

    private void loadDocument(VectorStore vectorStore, Resource doc) {
       logger.info("Loading document: " + doc.getFilename());
        MarkdownDocumentReader reader = new MarkdownDocumentReader(doc, MarkdownDocumentReaderConfig.defaultConfig());
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();
        List<Document> splitDocuments = tokenTextSplitter.apply(reader.get());
        try {
            vectorStore.accept(splitDocuments);
            logger.info("Document loaded: " + doc.getFilename());
        } catch (Exception e) {
            // Log the error but don't fail application startup. Common cause: Redis not available.
            logger.severe("Failed to load document into VectorStore: " + e.getMessage());
        }
    }
}
