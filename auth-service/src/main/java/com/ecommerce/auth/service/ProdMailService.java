package com.ecommerce.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class ProdMailService implements MailService {

    private final JavaMailSender mailSender;

    @Override
    public void sendTempPassword(String to, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[ShopMSA] 임시 비밀번호 발급 안내");
        message.setText(
            "안녕하세요, ShopMSA입니다.\n\n" +
            "임시 비밀번호가 발급되었습니다.\n\n" +
            "임시 비밀번호: " + tempPassword + "\n\n" +
            "보안을 위해 로그인 후 반드시 비밀번호를 변경해주세요.\n" +
            "임시 비밀번호는 1회 로그인 후 변경이 강제됩니다.\n\n" +
            "본인이 요청하지 않은 경우 이 메일을 무시해주세요."
        );
        mailSender.send(message);
        log.info("임시 비밀번호 이메일 발송 완료: {}", to);
    }
}
