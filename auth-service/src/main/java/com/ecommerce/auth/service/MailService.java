package com.ecommerce.auth.service;

public interface MailService {

    /**
     * 임시 비밀번호 이메일 발송.
     * prod: Gmail SMTP 실제 발송
     * dev : no-op (응답 바디로 직접 반환)
     */
    void sendTempPassword(String to, String tempPassword);
}
