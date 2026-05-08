package com.ecommerce.auth.config;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 최초 기동 시 ADMIN 시드 계정 생성.
 *
 * - 이미 존재하면 아무 동작 안 함 (멱등성 보장)
 * - 운영 환경에서는 환경변수로 이메일/비밀번호 주입 권장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:admin@ecommerce.com}")
    private String adminEmail;

    @Value("${admin.password:Admin1234!}")
    private String adminPassword;

    @PostConstruct
    public void init() {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("ADMIN 계정 이미 존재 — 시드 생략: {}", adminEmail);
            return;
        }

        userRepository.save(User.builder()
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .name("관리자")
                .role(Role.ADMIN)
                .build());

        log.info("ADMIN 시드 계정 생성 완료: {}", adminEmail);
    }
}
