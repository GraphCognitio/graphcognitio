package io.github.eduardopaim.graphcognitio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;

@ConfigurationPropertiesScan
@SpringBootApplication
@EnableCaching
public class GraphcognitioApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphcognitioApplication.class, args);
    }
}
