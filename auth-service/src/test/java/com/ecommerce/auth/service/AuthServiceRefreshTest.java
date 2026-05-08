package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RefreshResponse;
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
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @InjectMocks private AuthService authService;

    private RefreshRequest makeRefreshRequest(String token) {
        RefreshRequest req = new RefreshRequest();
        ReflectionTestUtils.setField(req, "refreshToken", token);
        return req;
    }

    @Test
    @DisplayName("Refresh Token으로 Access Token 재발급 성공")
    void refresh_success_returnsNewAccessToken() {
        String oldRefresh = "old-refresh-token";
        User user = User.builder().email("test@example.com").password("pw").name("테스터").role(Role.USER).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        given(refreshTokenRepository.findUserIdByToken(oldRefresh)).willReturn(Optional.of(1L));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(jwtProvider.issueRefreshToken()).willReturn("new-refresh-token");
        given(jwtProvider.getRefreshTokenTtl()).willReturn(Duration.ofDays(7));
        given(jwtProvider.issueAccessToken(1L, "USER")).willReturn("new-access-token");
        given(jwtProvider.getAccessTokenExpiryMs()).willReturn(3600000L);

        RefreshResponse response = authService.refresh(makeRefreshRequest(oldRefresh));

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        then(refreshTokenRepository).should().delete(oldRefresh);
    }

    @Test
    @DisplayName("유효하지 않은 Refresh Token - 예외 발생")
    void refresh_invalidToken_throwsException() {
        given(refreshTokenRepository.findUserIdByToken("invalid-token")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(makeRefreshRequest("invalid-token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("유효하지 않거나 만료된 Refresh Token입니다.");
    }

    @Test
    @DisplayName("로그아웃 시 Refresh Token 삭제")
    void logout_deletesRefreshToken() {
        authService.logout("some-refresh-token");
        then(refreshTokenRepository).should().delete("some-refresh-token");
    }
}