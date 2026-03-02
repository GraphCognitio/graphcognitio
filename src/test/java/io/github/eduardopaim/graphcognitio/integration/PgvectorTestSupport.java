package io.github.eduardopaim.graphcognitio.integration;

import org.testcontainers.utility.DockerImageName;

final class PgvectorTestSupport {

    static final DockerImageName PGVECTOR_POSTGRES = DockerImageName.parse("pgvector/pgvector:pg18")
            .asCompatibleSubstituteFor("postgres");

    private PgvectorTestSupport() {
    }
}
