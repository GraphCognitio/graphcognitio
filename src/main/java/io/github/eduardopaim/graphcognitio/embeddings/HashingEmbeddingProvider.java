package io.github.eduardopaim.graphcognitio.embeddings;

import io.github.eduardopaim.graphcognitio.config.EmbeddingsProperties;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.embeddings", name = "provider", havingValue = "hashing", matchIfMissing = true)
public class HashingEmbeddingProvider implements EmbeddingProvider {

    private static final int FEATURE_PROJECTIONS = 6;
    private static final double BIGRAM_FEATURE_WEIGHT = 0.72;
    private static final double CHARACTER_TRIGRAM_WEIGHT = 0.18;
    private static final long HASH_SEED = 0x9E3779B97F4A7C15L;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private final EmbeddingsProperties embeddingsProperties;

    public HashingEmbeddingProvider(EmbeddingsProperties embeddingsProperties) {
        this.embeddingsProperties = embeddingsProperties;
    }

    @Override
    public EmbeddingVector embed(String text) {
        int dimensions = dimensions();
        float[] vector = new float[dimensions];
        for (String term : extractTerms(text)) {
            addProjectedFeature(vector, "term:" + term, 1.0);
            for (String trigram : extractCharacterTrigrams(term)) {
                addProjectedFeature(vector, "tri:" + trigram, CHARACTER_TRIGRAM_WEIGHT);
            }
        }

        for (String bigram : extractBigrams(text)) {
            addProjectedFeature(vector, "bigram:" + bigram, BIGRAM_FEATURE_WEIGHT);
        }

        normalizeVector(vector);
        return new EmbeddingVector(model(), dimensions, vector);
    }

    @Override
    public String model() {
        return embeddingsProperties.getModel();
    }

    @Override
    public int dimensions() {
        return embeddingsProperties.getDimensions();
    }

    private Set<String> extractTerms(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }

        String normalized = normalize(content);
        if (normalized.isBlank()) {
            return Set.of();
        }

        Set<String> terms = new LinkedHashSet<>();
        for (String token : NON_ALPHANUMERIC.split(normalized)) {
            if (token.length() < 3) {
                continue;
            }
            terms.add(token);
        }
        return terms;
    }

    private Set<String> extractBigrams(String content) {
        String[] tokens = extractTerms(content).toArray(String[]::new);
        if (tokens.length < 2) {
            return Set.of();
        }

        Set<String> bigrams = new LinkedHashSet<>();
        for (int index = 0; index < tokens.length - 1; index++) {
            bigrams.add(tokens[index] + ":" + tokens[index + 1]);
        }
        return bigrams;
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        return normalized.toLowerCase(Locale.ROOT).trim();
    }

    private void addProjectedFeature(float[] vector, String feature, double weight) {
        long state = mix64((((long) feature.hashCode()) << 32) ^ HASH_SEED);
        for (int projection = 0; projection < FEATURE_PROJECTIONS; projection++) {
            state = mix64(state + (projection * HASH_SEED));
            int index = (int) Math.floorMod(state, vector.length);
            state = mix64(state + 0xBF58476D1CE4E5B9L);
            vector[index] += ((state & 1L) == 0L ? 1.0f : -1.0f) * (float) weight;
        }
    }

    private Set<String> extractCharacterTrigrams(String token) {
        String padded = "^" + token + "$";
        if (padded.length() < 3) {
            return Set.of(token);
        }

        Set<String> trigrams = new LinkedHashSet<>();
        for (int index = 0; index <= padded.length() - 3; index++) {
            trigrams.add(padded.substring(index, index + 3));
        }
        return trigrams;
    }

    private void normalizeVector(float[] vector) {
        double magnitude = 0.0;
        for (float value : vector) {
            magnitude += value * value;
        }
        if (magnitude <= 0.0) {
            return;
        }

        float norm = (float) Math.sqrt(magnitude);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / norm;
        }
    }

    private long mix64(long value) {
        long mixed = value;
        mixed ^= (mixed >>> 30);
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= (mixed >>> 27);
        mixed *= 0x94D049BB133111EBL;
        mixed ^= (mixed >>> 31);
        return mixed;
    }
}
