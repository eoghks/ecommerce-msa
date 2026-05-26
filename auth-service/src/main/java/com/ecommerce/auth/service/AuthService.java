package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResponse;
import com.ecommerce.auth.dto.RefreshRequest;
import com.ecommerce.auth.dto.RefreshResponse;
import com.ecommerce.auth.dto.ChangePasswordRequest;
import com.ecommerce.auth.dto.MeResponse;
import com.ecommerce.auth.dto.SignupRequest;
import com.ecommerce.auth.dto.SignupResponse;
import com.ecommerce.auth.exception.DuplicateEmailException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.InvalidTokenException;
import com.ecommerce.auth.jwt.JwtProvider;
import com.ecommerce.auth.repository.RefreshTokenRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String JWT_KEY_ID = "auth-key";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .role(Role.USER)
                .build();
        return new SignupResponse(userRepository.save(user));
    }

    // HR-01: Redis 쓰기 포함 — readOnly 제거
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String accessToken  = jwtProvider.issueAccessToken(user.getId(), user.getRole().name());
        String refreshToken = jwtProvider.issueRefreshToken();
        refreshTokenRepository.save(refreshToken, user.getId(), jwtProvider.getRefreshTokenTtl());

        return new LoginResponse(accessToken, refreshToken, jwtProvider.getAccessTokenExpiryMs());
    }

    // LW-01: Redis 쓰기 포함 — readOnly 제거
    @Transactional
    public RefreshResponse refresh(RefreshRequest request) {
        Long userId = refreshTokenRepository.findUserIdByToken(request.getRefreshToken())
                .orElseThrow(() -> new InvalidTokenException("유효하지 않거나 만료된 Refresh Token입니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("토큰에 해당하는 사용자를 찾을 수 없습니다."));

        // Refresh Token Rotation
        refreshTokenRepository.delete(request.getRefreshToken());
        String newRefreshToken = jwtProvider.issueRefreshToken();
        refreshTokenRepository.save(newRefreshToken, userId, jwtProvider.getRefreshTokenTtl());

        String newAccessToken = jwtProvider.issueAccessToken(userId, user.getRole().name());
        return new RefreshResponse(newAccessToken, newRefreshToken, jwtProvider.getAccessTokenExpiryMs());
    }

    public void logout(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
        // 비밀번호 변경 후 모든 Refresh Token 무효화
        refreshTokenRepository.deleteAllByUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("사용자를 찾을 수 없습니다."));
        return new MeResponse(user);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    // MD-01: Controller 대신 Service에서 JWK 조립
    public Map<String, Object> getJwks() {
        RSAKey rsaKey = new RSAKey.Builder(jwtProvider.getPublicKey())
                .keyID(JWT_KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .keyUse(KeyUse.SIGNATURE)
                .build();
        return new JWKSet(rsaKey).toJSONObject();
    }
}
