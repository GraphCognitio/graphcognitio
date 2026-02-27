package io.github.eduardopaim.graphcognitio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.features")
public class FeatureFlagsProperties {

    private boolean searchEnabled = true;
    private boolean graphExpandEnabled = true;

    public boolean isSearchEnabled() {
        return searchEnabled;
    }

    public void setSearchEnabled(boolean searchEnabled) {
        this.searchEnabled = searchEnabled;
    }

    public boolean isGraphExpandEnabled() {
        return graphExpandEnabled;
    }

    public void setGraphExpandEnabled(boolean graphExpandEnabled) {
        this.graphExpandEnabled = graphExpandEnabled;
    }
}
