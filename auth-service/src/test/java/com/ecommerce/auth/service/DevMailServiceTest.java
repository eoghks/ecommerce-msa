package com.ecommerce.auth.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-03: 임시 비밀번호 평문 로그 차단 검증.
 * 로그 이벤트를 직접 수집해 자격증명 노출 여부를 단언한다.
 */
class DevMailServiceTest {

    private static final String TEMP_PASSWORD = "EzB*Atmw0%";

    private final DevMailService devMailService = new DevMailService();

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DevMailService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("임시 비밀번호는 로그에 남기지 않는다")
    void tempPassword_is_not_logged() {
        devMailService.sendTempPassword("alice@example.com", TEMP_PASSWORD);

        assertThat(loggedMessages()).noneMatch(message -> message.contains(TEMP_PASSWORD));
    }

    @Test
    @DisplayName("이메일은 로컬파트 앞 2자리만 남기고 마스킹한다")
    void email_is_masked() {
        devMailService.sendTempPassword("alice@example.com", TEMP_PASSWORD);

        assertThat(loggedMessages()).anyMatch(message -> message.contains("al***@example.com"));
        assertThat(loggedMessages()).noneMatch(message -> message.contains("alice@example.com"));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 전체를 마스킹한다")
    void invalid_email_is_fully_masked() {
        devMailService.sendTempPassword("not-an-email", TEMP_PASSWORD);

        assertThat(loggedMessages()).noneMatch(message -> message.contains("not-an-email"));
    }

    private List<String> loggedMessages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
