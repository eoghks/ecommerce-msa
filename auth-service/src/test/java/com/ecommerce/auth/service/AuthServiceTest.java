package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.SignupRequest;
import com.ecommerce.auth.dto.SignupResponse;
import com.ecommerce.auth.exception.DuplicateEmailException;
import com.ecommerce.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private SignupRequest makeRequest(String email, String password, String name) {
        SignupRequest req = new SignupRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        ReflectionTestUtils.setField(req, "name", name);
        return req;
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        SignupRequest request = makeRequest("test@example.com", "password123", "테스터");

        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("hashed");

        User savedUser = User.builder()
                .email("test@example.com")
                .password("hashed")
                .name("테스터")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(savedUser, "id", 1L);
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        SignupResponse response = authService.signup(request);

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        assertThat(response.getName()).isEqualTo("테스터");
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이메일 중복 시 예외 발생")
    void signup_duplicateEmail_throwsException() {
        SignupRequest request = makeRequest("dup@example.com", "password123", "중복");

        given(userRepository.existsByEmail("dup@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("이미 사용 중인 이메일입니다: dup@example.com");
    }
}