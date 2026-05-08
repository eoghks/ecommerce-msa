package com.ecommerce.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    @Value("${jwt.access-token-expiry-ms:3600000}")
    private long accessTokenExpiryMs;

    @Value("${jwt.refresh-token-expiry-ms:604800000}")
    private long refreshTokenExpiryMs;

    private RSAPrivateKey privateKey;
    private RSAPublicKey  publicKey;

    @PostConstruct
    public void init() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        this.privateKey = (RSAPrivateKey) pair.getPrivate();
        this.publicKey  = (RSAPublicKey)  pair.getPublic();
        log.info("RSA 키 페어 생성 완료");
    }

    public String issueAccessToken(Long userId, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issueTime(now)
                .expirationTime(expiry)
                .build();

        return sign(claims);
    }

    public String issueRefreshToken() {
        return UUID.randomUUID().toString();
    }

    public JWTClaimsSet validateAccessToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            RSASSAVerifier verifier = new RSASSAVerifier(publicKey);
            if (!jwt.verify(verifier)) {
                throw new IllegalArgumentException("JWT 서명이 유효하지 않습니다.");
            }
            // HR-02: exp 클레임 null 방어
            Date expiry = jwt.getJWTClaimsSet().getExpirationTime();
            if (expiry == null || expiry.before(new Date())) {
                throw new IllegalArgumentException("만료된 토큰입니다.");
            }
            return jwt.getJWTClaimsSet();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
        }
    }

    private String sign(JWTClaimsSet claims) {
        try {
            JWSSigner signer = new RSASSASigner(privateKey);
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("JWT 발급 실패", e);
        }
    }

    // MD-05: 내부 코드베이스 미사용 — 향후 제거 예정
    @Deprecated(since = "Week2", forRemoval = true)
    public String issue(Long userId, String role) {
        return issueAccessToken(userId, role);
    }

    public long getAccessTokenExpiryMs()  { return accessTokenExpiryMs; }
    public long getRefreshTokenExpiryMs() { return refreshTokenExpiryMs; }
    public Duration getRefreshTokenTtl()  { return Duration.ofMillis(refreshTokenExpiryMs); }
    public RSAPublicKey getPublicKey()     { return publicKey; }
}