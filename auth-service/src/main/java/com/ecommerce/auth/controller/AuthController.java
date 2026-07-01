package com.ecommerce.auth.controller;

import com.ecommerce.auth.dto.ChangePasswordRequest;
import com.ecommerce.auth.dto.ForgotPasswordRequest;
import com.ecommerce.auth.dto.ForgotPasswordResponse;
import com.ecommerce.auth.dto.MeResponse;
import com.ecommerce.auth.dto.LoginRequest;
import com.ecommerce.auth.dto.LoginResponse;
import com.ecommerce.auth.dto.RefreshResponse;
import com.ecommerce.auth.dto.SellerApplyRequest;
import com.ecommerce.auth.dto.SellerApplyResponse;
import com.ecommerce.auth.dto.SignupRequest;
import com.ecommerce.auth.dto.SignupResponse;
import com.ecommerce.auth.dto.UserSummaryResponse;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.auth.support.RefreshTokenCookie;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshTokenCookie;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity
                .created(URI.create("/api/v1/auth/" + response.getId()))
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        // Refresh Token은 HttpOnly 쿠키로 전달 (바디에는 @JsonIgnore로 제외됨)
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(response.getRefreshToken()))
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(value = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        RefreshResponse response = authService.refresh(refreshToken);
        // Rotation된 새 Refresh Token을 쿠키로 재발급
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(response.getRefreshToken()))
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-User-Id") Long userId) {
        authService.logout(userId);
        // 쿠키 만료 처리
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.expire())
                .build();
    }

    // 내 정보 조회 — 게이트웨이가 설정한 X-User-Id 헤더 사용
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(authService.getMe(userId));
    }

    // 비밀번호 변경 — 인증 필요 (JWT)
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    // 비밀번호 찾기 — 임시 비밀번호 발급 (비인증 공개 엔드포인트)
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    // 판매자 신청 + mock 인증 — JWT 필요 (X-User-Id 헤더)
    @PostMapping("/seller/apply")
    public ResponseEntity<SellerApplyResponse> applyForSeller(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody SellerApplyRequest request) {
        return ResponseEntity.ok(authService.applyForSeller(userId, request.phone()));
    }

    // 서비스 간 사용자 요약 배치 조회 (product-service 판매자 정보 표시용)
    // 게이트웨이 화이트리스트 제외 — 서비스 간 직접 호출 전용
    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryResponse>> getUsers(@RequestParam List<Long> ids) {
        return ResponseEntity.ok(authService.getUsersByIds(ids));
    }

    // 이메일 중복 체크 — 비인증 공개 엔드포인트
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestParam String email) {
        boolean available = !authService.existsByEmail(email);
        return ResponseEntity.ok(Map.of("available", available));
    }

    // MD-01: JWK 조립 로직은 AuthService에 위임
    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(authService.getJwks());
    }
}
