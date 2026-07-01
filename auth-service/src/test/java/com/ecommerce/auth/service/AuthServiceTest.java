package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.ChangePasswordRequest;
import com.ecommerce.auth.dto.SignupRequest;
import com.ecommerce.auth.dto.SignupResponse;
import com.ecommerce.auth.exception.DuplicateEmailException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.InvalidInternalTokenException;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private ChangePasswordRequest makeChangeRequest(String currentPw, String newPw) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        ReflectionTestUtils.setField(req, "currentPassword", currentPw);
        ReflectionTestUtils.setField(req, "newPassword", newPw);
        return req;
    }

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

    @Test
    @DisplayName("비밀번호 변경 성공 — 인증 주체(userId) 기준 로드 + 리프레시 토큰 무효화")
    void changePassword_success() {
        User user = User.builder()
                .email("test@example.com").password("oldHash").name("테스터").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", 7L);

        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Current1!", "oldHash")).willReturn(true);
        given(passwordEncoder.encode("NewPass1!")).willReturn("newHash");

        ChangePasswordRequest request = makeChangeRequest("Current1!", "NewPass1!");

        authService.changePassword(7L, request);

        assertThat(user.getPassword()).isEqualTo("newHash");
        verify(refreshTokenRepository).deleteAllByUserId(7L);
    }

    @Test
    @DisplayName("비밀번호 변경 — 현재 비밀번호 불일치 시 예외")
    void changePassword_wrongCurrentPassword() {
        User user = User.builder()
                .email("test@example.com").password("oldHash").name("테스터").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", 7L);

        given(userRepository.findById(7L)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Wrong1!", "oldHash")).willReturn(false);

        ChangePasswordRequest request = makeChangeRequest("Wrong1!", "NewPass1!");

        assertThatThrownBy(() -> authService.changePassword(7L, request))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("내부 토큰 검증 — 일치 시 통과")
    void verifyInternalToken_valid() {
        ReflectionTestUtils.setField(authService, "internalToken", "secret");

        assertThatCode(() -> authService.verifyInternalToken("secret")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("내부 토큰 검증 — 불일치/누락 시 예외")
    void verifyInternalToken_invalid() {
        ReflectionTestUtils.setField(authService, "internalToken", "secret");

        assertThatThrownBy(() -> authService.verifyInternalToken("wrong"))
                .isInstanceOf(InvalidInternalTokenException.class);
        assertThatThrownBy(() -> authService.verifyInternalToken(null))
                .isInstanceOf(InvalidInternalTokenException.class);
    }
}