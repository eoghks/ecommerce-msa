package com.ecommerce.auth.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() throws Exception {
        jwtProvider = new JwtProvider();
        ReflectionTestUtils.setField(jwtProvider, "accessTokenExpiryMs", 3600000L);
        jwtProvider.init();
    }

    @Test
    @DisplayName("JWT 발급 - subject와 role 클레임 포함")
    void issue_containsSubjectAndRole() throws Exception {
        String token = jwtProvider.issue(1L, "USER");

        SignedJWT jwt = SignedJWT.parse(token);
        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.getStringClaim("role")).isEqualTo("USER");
        assertThat(claims.getExpirationTime()).isNotNull();
    }

    @Test
    @DisplayName("JWT 발급 - 만료 시간 1시간")
    void issue_expiryIsOneHour() throws Exception {
        long before = System.currentTimeMillis();
        String token = jwtProvider.issue(1L, "USER");
        long after  = System.currentTimeMillis();

        SignedJWT jwt = SignedJWT.parse(token);
        long expiry = jwt.getJWTClaimsSet().getExpirationTime().getTime();

        // JWT exp 클레임은 초 단위 → 1초 오차 허용
        assertThat(expiry).isBetween(before + 3600000L - 1000, after + 3600000L + 1000);
    }
}