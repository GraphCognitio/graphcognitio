package io.github.eduardopaim.graphcognitio.repository;

import io.github.eduardopaim.graphcognitio.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

        Optional<UserEntity> findByEmail(String email);

        boolean existsByEmail(String email);

        Optional<UserEntity> findByUsername(String username);

        boolean existsByUsername(String username);

        @Query("""
                        SELECT u
                        FROM UserEntity u
                        WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%'))
                           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))
                        ORDER BY u.createdAt DESC, u.id DESC
                        """)
        List<UserEntity> searchAdminUsers(@Param("query") String query, Pageable pageable);

        @Query("""
                        SELECT u
                        FROM UserEntity u
                        ORDER BY u.createdAt DESC, u.id DESC
                        """)
        List<UserEntity> findRecentUsers(Pageable pageable);
}
