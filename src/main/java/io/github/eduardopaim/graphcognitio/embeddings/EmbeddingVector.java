package io.github.eduardopaim.graphcognitio.embeddings;

import java.util.Arrays;

public record EmbeddingVector(
        String model,
        int dimensions,
        float[] values
) {

    public EmbeddingVector {
        values = values == null ? new float[0] : Arrays.copyOf(values, values.length);
    }

    public String toPgVectorLiteral() {
        StringBuilder builder = new StringBuilder(values.length * 8 + 2);
        builder.append('[');
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(values[index]));
        }
        builder.append(']');
        return builder.toString();
    }
}
