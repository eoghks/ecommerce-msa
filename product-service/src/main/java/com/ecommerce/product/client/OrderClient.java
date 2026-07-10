package com.ecommerce.product.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Order Service에서 구매 사실을 조회 (V1.1-1).
 * 리뷰 작성 자격(구매 인증) 판정용 — UserClient 미러(RestTemplate + X-Internal-Token).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderClient {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    private final RestTemplate restTemplate;

    @Value("${service.order.url:http://localhost:8083}")
    private String orderServiceUrl;

    // M-N1: order-service와 공유하는 내부 호출 시크릿
    @Value("${app.internal.token:dev-internal-secret}")
    private String internalToken;

    /**
     * 사용자가 상품을 구매했는지 조회.
     * 호출 실패/타임아웃 시 false 반환 (안전한 실패 — 리뷰 작성 거부).
     */
    public boolean hasPurchased(Long userId, Long productId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(orderServiceUrl + "/api/v1/orders/internal/purchased")
                    .queryParam("userId", userId)
                    .queryParam("productId", productId)
                    .build().toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_INTERNAL_TOKEN, internalToken);

            PurchasedResponse body = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), PurchasedResponse.class
            ).getBody();

            return body != null && body.purchased();
        } catch (Exception ex) {
            log.warn("Order Service 구매 인증 조회 실패 — 리뷰 작성 거부: {}", ex.getMessage());
            return false;
        }
    }

    public record PurchasedResponse(boolean purchased) {}
}
