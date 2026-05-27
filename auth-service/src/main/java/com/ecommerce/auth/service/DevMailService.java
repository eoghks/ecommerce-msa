package com.ecommerce.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!prod")
public class DevMailService implements MailService {

    @Override
    public void sendTempPassword(String to, String tempPassword) {
        // dev 환경에서는 이메일 미발송 — 임시 비밀번호는 API 응답으로 직접 반환
        log.info("[DEV] 임시 비밀번호 발급 (이메일 미발송) — email={}, tempPw={}", to, tempPassword);
    }
}
