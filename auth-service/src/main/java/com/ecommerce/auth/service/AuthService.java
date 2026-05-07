package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResponse;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RefreshResponse;
import com.ecommerce.auth.dto.SignupRequest;
import com.ecommerce.auth.dto.SignupResponse;
import com.ecommerce.auth.jwt.JwtProvider;
import com.ecommerce.auth.repository.RefreshTokenRepository;
import com.ecommerce.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(Role.USER)
                .build();

        return new SignupResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken  = jwtProvider.issueAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtProvider.issueRefreshToken();
        refreshTokenRepository.save(refreshToken, user.getId(), jwtProvider.getRefreshTokenTtl());

        return new LoginResponse(accessToken, refreshToken, jwtProvider.getAccessTokenExpiryMs());
    }

    @Transactional(readOnly = true)
    public RefreshResponse refresh(RefreshRequest request) {
        Long userId = refreshTokenRepository.findUserIdByToken(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 Refresh Token입니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // Refresh Token Rotation - 기존 토큰 삭제 후 새로 발급
        refreshTokenRepository.delete(request.getRefreshToken());
        String newRefreshToken = jwtProvider.issueRefreshToken();
        refreshTokenRepository.save(newRefreshToken, userId, jwtProvider.getRefreshTokenTtl());

        String newAccessToken = jwtProvider.issueAccessToken(userId, user.getRole().name());
        return new RefreshResponse(newAccessToken, jwtProvider.getAccessTokenExpiryMs());
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.delete(refreshToken);
    }
}