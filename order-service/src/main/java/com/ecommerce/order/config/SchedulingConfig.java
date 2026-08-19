package com.ecommerce.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화 (payment-foundation §3.2).
 * 현재 대상은 자동 구매확정 스케줄러 하나이며, 이후 미완료 주문 만료(§11.1)도 같은 스케줄러에 태운다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
