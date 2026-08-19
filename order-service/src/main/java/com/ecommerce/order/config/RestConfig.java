package com.ecommerce.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 토스페이먼츠 전용 RestTemplate — 타임아웃을 명시한다.
     * 결제 승인은 카드사 왕복이 있어 일반 내부 호출보다 느리므로 read 타임아웃을 넉넉히 잡되,
     * 무한 대기로 요청 스레드가 묶이지 않게 상한을 둔다.
     */
    @Bean
    public RestTemplate tossRestTemplate(
            @Value("${toss.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${toss.read-timeout-ms:10000}") long readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return new RestTemplate(factory);
    }
}
