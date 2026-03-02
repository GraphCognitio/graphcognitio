package io.github.eduardopaim.graphcognitio.posts.service;

import io.github.eduardopaim.graphcognitio.config.EmbeddingsProperties;
import io.github.eduardopaim.graphcognitio.embeddings.EmbeddingProvider;
import io.github.eduardopaim.graphcognitio.embeddings.EmbeddingVector;
import io.github.eduardopaim.graphcognitio.entity.PostEntity;
import io.github.eduardopaim.graphcognitio.repository.PostEmbeddingJdbcRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostEmbeddingService {

    private final EmbeddingsProperties embeddingsProperties;
    private final EmbeddingProvider embeddingProvider;
    private final PostEmbeddingJdbcRepository postEmbeddingJdbcRepository;

    public PostEmbeddingService(EmbeddingsProperties embeddingsProperties,
                                EmbeddingProvider embeddingProvider,
                                PostEmbeddingJdbcRepository postEmbeddingJdbcRepository) {
        this.embeddingsProperties = embeddingsProperties;
        this.embeddingProvider = embeddingProvider;
        this.postEmbeddingJdbcRepository = postEmbeddingJdbcRepository;
    }

    public boolean isEnabled() {
        return embeddingsProperties.isEnabled();
    }

    public EmbeddingVector embedContent(String content) {
        return embeddingProvider.embed(content == null ? "" : content);
    }

    @Transactional
    public void indexPost(PostEntity post) {
        if (!isEnabled() || post == null) {
            return;
        }

        EmbeddingVector embedding = embedContent(post.getContent());
        postEmbeddingJdbcRepository.updateEmbedding(post.getId(), embedding, Instant.now());
    }

    @Transactional
    public int reindexMissing(int requestedLimit) {
        return reindex(requestedLimit, true);
    }

    @Transactional
    public int reindexAll(int requestedLimit) {
        return reindex(requestedLimit, false);
    }

    public String currentModel() {
        return embeddingProvider.model();
    }

    public int currentDimensions() {
        return embeddingProvider.dimensions();
    }

    public int reindexBatchSize() {
        return embeddingsProperties.getReindexBatchSize();
    }

    private int reindex(int requestedLimit, boolean missingOnly) {
        if (!isEnabled()) {
            return 0;
        }

        int safeLimit = resolveBatchLimit(requestedLimit);
        List<PostEntity> posts = postEmbeddingJdbcRepository.findPostsForReindex(currentModel(), safeLimit, missingOnly);
        for (PostEntity post : posts) {
            indexPost(post);
        }
        return posts.size();
    }

    private int resolveBatchLimit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return embeddingsProperties.getReindexBatchSize();
        }
        return Math.min(requestedLimit, embeddingsProperties.getReindexBatchSize());
    }
}
