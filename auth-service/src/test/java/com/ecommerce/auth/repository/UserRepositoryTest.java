package com.ecommerce.auth.repository;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("이메일로 유저 조회")
    void findByEmail_returnsUser() {
        User user = User.builder()
                .email("test@example.com")
                .password("hashed_password")
                .name("테스터")
                .role(Role.USER)
                .build();
        userRepository.save(user);

        Optional<User> result = userRepository.findByEmail("test@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("테스터");
        assertThat(result.get().getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("존재하지 않는 이메일 조회 시 빈 Optional 반환")
    void findByEmail_notFound_returnsEmpty() {
        Optional<User> result = userRepository.findByEmail("none@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("이메일 중복 여부 확인")
    void existsByEmail_duplicateEmail_returnsTrue() {
        userRepository.save(User.builder()
                .email("dup@example.com")
                .password("pw")
                .name("중복")
                .role(Role.USER)
                .build());

        assertThat(userRepository.existsByEmail("dup@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("new@example.com")).isFalse();
    }

    @Test
    @DisplayName("저장 시 createdAt, updatedAt 자동 설정")
    void save_auditing_fieldsAutoSet() {
        User user = User.builder()
                .email("audit@example.com")
                .password("pw")
                .name("감사")
                .role(Role.USER)
                .build();

        User saved = userRepository.save(user);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
