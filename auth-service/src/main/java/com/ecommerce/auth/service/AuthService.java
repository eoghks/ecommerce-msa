package com.ecommerce.auth.service;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.SellerApplyRequest;
import com.ecommerce.auth.dto.SellerApplyResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResponse;
import com.ecommerce.auth.dto.RefreshResponse;
import com.ecommerce.auth.dto.ChangePasswordRequest;
import com.ecommerce.auth.dto.ForgotPasswordRequest;
import com.ecommerce.auth.dto.ForgotPasswordResponse;
import com.ecommerce.auth.dto.MeResponse;
import com.ecommerce.auth.dto.SignupRequest;
import com.ecommerce.auth.dto.SignupResponse;
import com.ecommerce.auth.dto.UserSummaryResponse;
import com.ecommerce.auth.exception.DuplicateEmailException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.InvalidInternalTokenException;
import com.ecommerce.auth.exception.InvalidTokenException;
import com.ecommerce.auth.jwt.JwtProvider;
import com.ecommerce.auth.repository.RefreshTokenRepository;
import com.ecommerce.auth.repository.UserRepository;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String JWT_KEY_ID = "auth-key";

    // 임시 비밀번호 생성용 문자셋
    private static final String PW_UPPER  = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String PW_LOWER  = "abcdefghijklmnopqrstuvwxyz";
    private static final String PW_DIGITS = "0123456789";
    private static final String PW_SPEC   = "!@#$%^&*";
    private static final String PW_ALL    = PW_UPPER + PW_LOWER + PW_DIGITS + PW_SPEC;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MailService mailService;

    // dev: true (응답 바디에 임시 비밀번호 포함), prod: false
    @Value("${app.mail.expose-temp-password:true}")
    private boolean exposeTempPassword;

    // H-2: 판매자 mock 자동 승인 — dev만 true. prod 프로파일에서 false로 막아 누구나 SELLER 되는 것 방지
    @Value("${app.seller.auto-approve:true}")
    private boolean sellerAutoApprove;

    // M-N1: 서비스 간 내부 호출 공유 시크릿. dev 기본값 제공, prod는 env로 필수 주입.
    @Value("${app.internal.token:dev-internal-secret}")
    private String internalToken;

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

        // 임시 비밀번호 로그인 시 pwdChangeRequired 클레임을 true로 발급
        String accessToken  = jwtProvider.issueAccessToken(
                user.getId(), user.getRole().name(), user.isPasswordChangeRequired());
        String refreshToken = jwtProvider.issueRefreshToken();
        refreshTokenRepository.save(refreshToken, user.getId(), jwtProvider.getRefreshTokenTtl());

        return new LoginResponse(accessToken, refreshToken, jwtProvider.getAccessTokenExpiryMs());
    }

    // LW-01: Redis 쓰기 포함 — readOnly 제거
    @Transactional
    public RefreshResponse refresh(String refreshToken) {
        // H-B: 조회+삭제를 원자적으로(GETDEL) — 동시 요청 TOCTOU 방지, 이미 소비된 토큰은 거부
        Long userId = refreshTokenRepository.findAndDeleteUserId(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않거나 만료된 Refresh Token입니다."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("토큰에 해당하는 사용자를 찾을 수 없습니다."));

        // Refresh Token Rotation — 기존 토큰은 이미 위에서 원자적으로 삭제됨. 새 토큰 발급.
        String newRefreshToken = jwtProvider.issueRefreshToken();
        refreshTokenRepository.save(newRefreshToken, userId, jwtProvider.getRefreshTokenTtl());

        String newAccessToken = jwtProvider.issueAccessToken(
                userId, user.getRole().name(), user.isPasswordChangeRequired());
        return new RefreshResponse(newAccessToken, newRefreshToken, jwtProvider.getAccessTokenExpiryMs());
    }

    public void logout(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        // 인증 주체(X-User-Id) 기준으로 사용자 로드 — 임의 이메일 대상 변경 차단
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        user.changePassword(passwordEncoder.encode(request.getNewPassword()));
        // 비밀번호 변경 후 모든 Refresh Token 무효화
        refreshTokenRepository.deleteAllByUserId(user.getId());
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("등록되지 않은 이메일입니다."));

        String tempPassword = generateTempPassword();
        user.applyTempPassword(passwordEncoder.encode(tempPassword));

        // 기존 Refresh Token 전체 무효화 (보안)
        refreshTokenRepository.deleteAllByUserId(user.getId());

        mailService.sendTempPassword(user.getEmail(), tempPassword);

        // prod: null 반환 (이메일로만 전달), dev: 응답 바디에 포함
        String exposed = exposeTempPassword ? tempPassword : null;
        return new ForgotPasswordResponse("임시 비밀번호가 발급되었습니다.", exposed);
    }

    @Transactional(readOnly = true)
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("사용자를 찾을 수 없습니다."));
        return new MeResponse(user);
    }

    /**
     * 판매자 신청 + mock 인증.
     * 테스트 서버이므로 전화번호 저장 후 즉시 SELLER 승격 + 새 JWT 발급.
     */
    @Transactional
    public SellerApplyResponse applyForSeller(Long userId, String phone) {
        // H-2: prod 등에서 자동 승인이 꺼져 있으면 차단 (실제 심사 절차 필요)
        if (!sellerAutoApprove) {
            throw new IllegalStateException("판매자 자동 승인이 비활성화되어 있습니다. 관리자 심사가 필요합니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidTokenException("사용자를 찾을 수 없습니다."));
        user.promoteToSeller(phone);
        String accessToken = jwtProvider.issueAccessToken(
                user.getId(), user.getRole().name(), user.isPasswordChangeRequired());
        return new SellerApplyResponse("테스트 서버입니다. 인증되었습니다.", accessToken,
                jwtProvider.getAccessTokenExpiryMs());
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /** M-N1: 내부 호출 공유 시크릿 검증 — 불일치 시 403 */
    public void verifyInternalToken(String token) {
        if (token == null || !internalToken.equals(token)) {
            throw new InvalidInternalTokenException();
        }
    }

    /** 서비스 간 사용자 요약 배치 조회 (product-service 판매자 정보 표시용) */
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> getUsersByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userRepository.findByIdIn(ids).stream()
                .map(UserSummaryResponse::from)
                .toList();
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

    /**
     * 복잡도 조건을 만족하는 10자리 임시 비밀번호 생성.
     * 대문자·소문자·숫자·특수문자 각 1개 이상 보장.
     */
    private String generateTempPassword() {
        SecureRandom random = new SecureRandom();
        List<Character> chars = new ArrayList<>();

        // 카테고리별 최소 1개 보장
        chars.add(PW_UPPER.charAt(random.nextInt(PW_UPPER.length())));
        chars.add(PW_LOWER.charAt(random.nextInt(PW_LOWER.length())));
        chars.add(PW_DIGITS.charAt(random.nextInt(PW_DIGITS.length())));
        chars.add(PW_SPEC.charAt(random.nextInt(PW_SPEC.length())));

        // 나머지 6자리 무작위
        for (int i = 4; i < 10; i++) {
            chars.add(PW_ALL.charAt(random.nextInt(PW_ALL.length())));
        }

        Collections.shuffle(chars, random);
        StringBuilder sb = new StringBuilder(10);
        for (char c : chars) sb.append(c);
        return sb.toString();
    }
}
