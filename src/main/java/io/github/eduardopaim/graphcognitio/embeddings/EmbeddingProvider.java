package io.github.eduardopaim.graphcognitio.embeddings;

public interface EmbeddingProvider {

    EmbeddingVector embed(String text);

    String model();

    int dimensions();
}
