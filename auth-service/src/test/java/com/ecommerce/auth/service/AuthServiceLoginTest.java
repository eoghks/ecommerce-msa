package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResponse;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.jwt.JwtProvider;
import com.ecommerce.auth.repository.RefreshTokenRepository;
import com.ecommerce.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @InjectMocks private AuthService authService;

    private LoginRequest makeRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        ReflectionTestUtils.setField(req, "email", email);
        ReflectionTestUtils.setField(req, "password", password);
        return req;
    }

    private User makeUser(Long id, String email, String encodedPw) {
        User user = User.builder().email(email).password(encodedPw).name("테스터").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("로그인 성공 - AccessToken + RefreshToken 반환")
    void login_success_returnsTokens() {
        LoginRequest req = makeRequest("test@example.com", "password123");
        User user = makeUser(1L, "test@example.com", "hashed");

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123", "hashed")).willReturn(true);
        given(jwtProvider.issueAccessToken(1L, "USER")).willReturn("access.token");
        given(jwtProvider.issueRefreshToken()).willReturn("refresh-uuid");
        given(jwtProvider.getRefreshTokenTtl()).willReturn(Duration.ofDays(7));
        given(jwtProvider.getAccessTokenExpiryMs()).willReturn(3600000L);

        LoginResponse response = authService.login(req);

        assertThat(response.getAccessToken()).isEqualTo("access.token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-uuid");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(3600000L);
    }

    @Test
    @DisplayName("존재하지 않는 이메일 - 예외 발생")
    void login_emailNotFound_throwsException() {
        LoginRequest req = makeRequest("none@example.com", "password123");
        given(userRepository.findByEmail("none@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("비밀번호 불일치 - 예외 발생")
    void login_wrongPassword_throwsException() {
        LoginRequest req = makeRequest("test@example.com", "wrongpw");
        User user = makeUser(1L, "test@example.com", "hashed");

        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongpw", "hashed")).willReturn(false);

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}