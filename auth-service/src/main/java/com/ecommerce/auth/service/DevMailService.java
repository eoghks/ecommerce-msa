package com.ecommerce.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!prod")
public class DevMailService implements MailService {

    /** 마스킹 시 노출할 이메일 로컬파트 앞자리 수 */
    private static final int VISIBLE_LOCAL_LENGTH = 2;

    /** 마스킹 치환 문자열 */
    private static final String MASK = "***";

    @Override
    public void sendTempPassword(String to, String tempPassword) {
        // dev 환경에서는 이메일 미발송 — 임시 비밀번호는 API 응답으로 직접 반환
        // F-03: 임시 비밀번호는 자격증명이므로 로그에 남기지 않고, 이메일도 마스킹한다.
        log.info("[DEV] 임시 비밀번호 발급 (이메일 미발송) — email={}", maskEmail(to));
    }

    /** 이메일 마스킹 — 로컬파트 앞 2자리만 노출 (예: ab***@example.com) */
    private String maskEmail(String email) {
        int atIndex = (email == null) ? -1 : email.indexOf('@');
        if (atIndex <= 0) {
            return MASK;
        }
        String local = email.substring(0, atIndex);
        String visible = local.substring(0, Math.min(VISIBLE_LOCAL_LENGTH, local.length()));
        return visible + MASK + email.substring(atIndex);
    }
}
