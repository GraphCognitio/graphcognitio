package io.github.eduardopaim.graphcognitio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class GraphcognitioApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphcognitioApplication.class, args);
    }
}
