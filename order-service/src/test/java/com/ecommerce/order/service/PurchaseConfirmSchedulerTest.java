package com.ecommerce.order.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseConfirmScheduler 단위 테스트 (payment-foundation 3.2)")
class PurchaseConfirmSchedulerTest {

    @InjectMocks private PurchaseConfirmScheduler purchaseConfirmScheduler;

    @Mock private PurchaseConfirmService purchaseConfirmService;

    @Test
    @DisplayName("스케줄 실행 — 자동확정 서비스에 1회 위임한다")
    void autoConfirm_delegatesToService() {
        given(purchaseConfirmService.autoConfirmExpired()).willReturn(3);

        purchaseConfirmScheduler.autoConfirm();

        then(purchaseConfirmService).should(times(1)).autoConfirmExpired();
    }

    @Test
    @DisplayName("스케줄 실행 — 확정 대상이 없어도(0건) 예외 없이 종료한다")
    void autoConfirm_noTargets() {
        given(purchaseConfirmService.autoConfirmExpired()).willReturn(0);

        purchaseConfirmScheduler.autoConfirm();

        then(purchaseConfirmService).should(times(1)).autoConfirmExpired();
    }

    @Test
    @DisplayName("스케줄 설정 — 매시 정각(cron) + 서비스 기준 타임존(KST)")
    void schedule_hourlyCronInServiceZone() throws NoSuchMethodException {
        Method method = PurchaseConfirmScheduler.class.getMethod("autoConfirm");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(CronExpression.isValidExpression(scheduled.cron())).isTrue();
        assertThat(scheduled.cron()).isEqualTo("0 0 * * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
