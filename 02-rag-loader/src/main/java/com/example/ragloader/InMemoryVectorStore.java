package com.example.ragloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Minimal in-memory VectorStore implementation for local development and tests.
 * This stores Document objects in a list; no real vector indexing is performed.
 */
@Component
public class InMemoryVectorStore implements VectorStore {

    private final Logger logger = Logger.getLogger(InMemoryVectorStore.class.getName());
    private final List<Document> storage = Collections.synchronizedList(new ArrayList<>());
    private final List<float[]> embeddings = Collections.synchronizedList(new ArrayList<>());
    private final EmbeddingModel embeddingModel;

    public InMemoryVectorStore(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void add(@NonNull List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        int added = 0;
        for (Document d : documents) {
            try {
                float[] vec = null;
                try {
                    vec = embeddingModel.embed(d);
                } catch (Exception ex) {
                    // fallback: try embedding by text
                    if (d.getText() != null) {
                        vec = embeddingModel.embed(d.getText());
                    }
                }
                if (vec == null) {
                    logger.warning("InMemoryVectorStore: embedding returned null for document, skipping");
                    continue;
                }
                storage.add(d);
                embeddings.add(vec);
                added++;
            } catch (Exception e) {
                logger.severe("Failed to compute embedding for document: " + e.getMessage());
            }
        }
        logger.info("InMemoryVectorStore: stored " + added + " documents (total=" + storage.size() + ")");
    }

    // inherit default accept(List<Document>) from interface
    @Override
    public @NonNull List<Document> similaritySearch(@NonNull SearchRequest request) {
        String query = request.getQuery();
        if (query == null || query.isBlank()) {
            synchronized (storage) {
                return new ArrayList<>(storage);
            }
        }

        float[] qVec;
        try {
            qVec = embeddingModel.embed(query);
        } catch (Exception e) {
            logger.severe("Failed to embed query: " + e.getMessage());
            return List.of();
        }

        List<Map.Entry<Integer, Double>> scores = new ArrayList<>();
        synchronized (storage) {
            for (int i = 0; i < storage.size(); i++) {
                float[] vec = embeddings.get(i);
                if (vec == null || vec.length != qVec.length) continue;
                double sim = cosineSimilarity(qVec, vec);
                scores.add(Map.entry(i, sim));
            }
        }

        int topK = request.getTopK();
        if (topK <= 0) topK = Math.max(10, scores.size());

        double threshold = request.getSimilarityThreshold();

        List<Integer> sorted = scores.stream()
                .filter(e -> e.getValue() >= threshold)
                .sorted(Comparator.comparingDouble(Map.Entry<Integer, Double>::getValue).reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<Document> results = new ArrayList<>();
        synchronized (storage) {
            for (Integer idx : sorted) {
                results.add(storage.get(idx));
            }
        }
        return results;
    }

    @Override
    public void delete(@NonNull Filter.Expression expression) {
        // No-op for in-memory simple store; real implementations would apply the filter expression
        logger.info("InMemoryVectorStore.delete(Expression) called - no-op");
    }

    @Override
    public void delete(@NonNull List<String> ids) {
        // Remove documents with matching ids
        if (ids == null || ids.isEmpty()) return;
        synchronized (storage) {
            for (int i = storage.size() - 1; i >= 0; i--) {
                Document d = storage.get(i);
                if (d.getId() != null && ids.contains(d.getId())) {
                    storage.remove(i);
                    embeddings.remove(i);
                }
            }
        }
    }

    // Expose a read-only snapshot for tests or debugging
    public List<Document> snapshot() {
        synchronized (storage) {
            return new ArrayList<>(storage);
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
