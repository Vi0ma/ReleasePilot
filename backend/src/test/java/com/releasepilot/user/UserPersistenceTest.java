package com.releasepilot.user;

import com.releasepilot.user.entity.User;
import com.releasepilot.user.entity.UserRole;
import com.releasepilot.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses the configured PostgreSQL database and rolls back test data. */
@SpringBootTest
@Transactional
class UserPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private Flyway flyway;

    @Test
    void migrationIsAppliedAndUserCanBeRetrievedByEmail() {
        assertThat(flyway.info().applied())
                .anySatisfy(migration -> {
                    assertThat(migration.getVersion().getVersion()).isEqualTo("1");
                    assertThat(migration.getScript()).isEqualTo("V1__create_users.sql");
                });

        String email = "persistence-" + UUID.randomUUID() + "@example.test";
        assertThat(users.existsByEmail(email)).isFalse();
        // Opaque test placeholder, not a real password or an implementation of hashing.
        User saved = users.saveAndFlush(new User("Test User", email, "test-hash-placeholder", UserRole.USER));
        Long id = saved.getId();
        assertThat(id).isNotNull();
        entityManager.clear();

        User loaded = users.findByEmail(email).orElseThrow();
        assertThat(loaded.getId()).isEqualTo(id);
        assertThat(loaded.getName()).isEqualTo("Test User");
        assertThat(loaded.getEmail()).isEqualTo(email);
        assertThat(loaded.getPasswordHash()).isEqualTo("test-hash-placeholder");
        assertThat(loaded.getRole()).isEqualTo(UserRole.USER);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isEqualTo(loaded.getCreatedAt());
        assertThat(users.existsByEmail(email)).isTrue();
    }
}
