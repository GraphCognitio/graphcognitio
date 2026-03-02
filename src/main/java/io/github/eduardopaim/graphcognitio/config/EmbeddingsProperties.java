package io.github.eduardopaim.graphcognitio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.embeddings")
public class EmbeddingsProperties {

    private boolean enabled = true;
    private String provider = "hashing";
    private String model = "semantic-hash-v3";
    private int dimensions = 192;
    private int reindexBatchSize = 200;
    private final Remote remote = new Remote();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getReindexBatchSize() {
        return reindexBatchSize;
    }

    public void setReindexBatchSize(int reindexBatchSize) {
        this.reindexBatchSize = reindexBatchSize;
    }

    public Remote getRemote() {
        return remote;
    }

    public static class Remote {
        private String baseUrl = "https://api.openai.com";
        private String path = "/v1/embeddings";
        private String apiKey;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
