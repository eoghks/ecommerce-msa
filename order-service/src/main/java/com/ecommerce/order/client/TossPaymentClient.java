package com.ecommerce.order.client;

import com.ecommerce.order.exception.PaymentApprovalFailedException;
import com.ecommerce.order.exception.PaymentCancelFailedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 토스페이먼츠 결제 연동 (payment-foundation §11-1).
 * 승인: POST /v1/payments/confirm, 취소·부분취소: POST /v1/payments/{paymentKey}/cancel.
 * 인증은 시크릿키 Basic 인증(시크릿키 + ':' 를 base64)이며 키·카드정보는 어떤 경우에도 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class TossPaymentClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String CANCEL_PATH  = "/v1/payments/%s/cancel";

    /** 응답에서 사유를 못 읽었을 때 사용자에게 보여줄 일반 메시지(내부 정보 미노출) */
    private static final String DEFAULT_FAIL_MESSAGE = "결제 처리에 실패했습니다. 잠시 후 다시 시도해주세요.";

    /** 로그에 남기는 거래키 접두 길이 — 전체 노출 없이 추적만 가능하게 마스킹한다 */
    private static final int KEY_LOG_PREFIX = 6;

    private final RestTemplate  tossRestTemplate;
    private final ObjectMapper  objectMapper;
    private final String        baseUrl;
    private final String        authorizationHeader;

    public TossPaymentClient(@Qualifier("tossRestTemplate") RestTemplate tossRestTemplate,
                             ObjectMapper objectMapper,
                             @Value("${toss.api-base-url}") String baseUrl,
                             @Value("${toss.secret-key}") String secretKey) {
        this.tossRestTemplate    = tossRestTemplate;
        this.objectMapper        = objectMapper;
        this.baseUrl             = baseUrl;
        // 시크릿키는 헤더 문자열로만 보관하고 별도 필드로 노출하지 않는다(로그·응답 유출 방지)
        this.authorizationHeader = "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 결제 승인 — 프론트 결제위젯 성공 후 서버가 최종 승인한다.
     * 금액 검증은 호출부(PaymentService)가 서버 계산값으로 마친 뒤 이 메서드에 전달한다.
     */
    public TossPayment confirm(String paymentKey, String pgOrderId, long amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", pgOrderId);
        body.put("amount", amount);

        log.info("PG 결제 승인 요청. pgOrderId={}, amount={}, paymentKey={}",
                pgOrderId, amount, mask(paymentKey));
        return call(baseUrl + CONFIRM_PATH, body, PaymentApprovalFailedException::new);
    }

    /**
     * 결제 취소·환불 — cancelAmount 를 주면 부분취소(§4.2), 없으면 전액취소.
     * 이미 취소된 결제의 재취소는 호출 전에 걸러야 한다(멱등 판정은 호출부 책임).
     */
    public TossPayment cancel(String paymentKey, String cancelReason, long cancelAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", cancelReason);
        body.put("cancelAmount", cancelAmount);

        log.info("PG 결제 취소 요청. paymentKey={}, cancelAmount={}", mask(paymentKey), cancelAmount);
        return call(String.format(baseUrl + CANCEL_PATH, paymentKey), body,
                PaymentCancelFailedException::new);
    }

    /**
     * 공통 호출 — 실패 응답은 PG 사유 메시지만 뽑아 도메인 예외로 변환한다.
     * try-catch 는 흐름 제어가 아니라 외부 오류를 도메인 예외로 번역하는 용도다(ProductClient 와 동일 패턴).
     */
    private TossPayment call(String url, Map<String, Object> body,
                             Function<String, RuntimeException> errorFactory) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            return tossRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), TossPayment.class).getBody();
        } catch (HttpStatusCodeException ex) {
            String message = extractMessage(ex.getResponseBodyAsString());
            log.warn("PG 응답 오류. status={}, message={}", ex.getStatusCode(), message);
            throw errorFactory.apply(message);
        } catch (ResourceAccessException ex) {
            // 타임아웃·연결 실패 — 원인 메시지에 내부 주소가 담기므로 사용자에게는 일반 메시지만 전달
            log.error("PG 통신 실패. url={}", url, ex);
            throw errorFactory.apply(DEFAULT_FAIL_MESSAGE);
        }
    }

    /** PG 오류 응답에서 message 만 추출 — 파싱 실패 시 일반 메시지로 대체한다 */
    private String extractMessage(String responseBody) {
        return readErrorMessage(responseBody).orElse(DEFAULT_FAIL_MESSAGE);
    }

    private Optional<String> readErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }
        try {
            TossError error = objectMapper.readValue(responseBody, TossError.class);
            return Optional.ofNullable(error.message()).filter(m -> !m.isBlank());
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    /** 거래키 마스킹 — 앞 일부만 남긴다 */
    private String mask(String paymentKey) {
        if (paymentKey == null || paymentKey.length() <= KEY_LOG_PREFIX) {
            return "***";
        }
        return paymentKey.substring(0, KEY_LOG_PREFIX) + "***";
    }

    /**
     * 승인·취소 공통 응답(필요 필드만).
     * 토스 응답 필드는 계속 늘어나므로 미지의 필드는 무시한다(서비스 전역 fail-on-unknown 설정과 분리).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossPayment(
            String paymentKey,
            String orderId,
            String status,
            Long totalAmount,
            Long balanceAmount,
            OffsetDateTime approvedAt
    ) {
        /** 승인 시각 — 응답에 없으면 호출 시점을 쓰도록 Optional 로 돌려준다 */
        public Optional<LocalDateTime> approvedAtAsLocal() {
            return Optional.ofNullable(approvedAt).map(OffsetDateTime::toLocalDateTime);
        }
    }

    /** 토스 오류 응답 — code/message 만 사용 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossError(String code, String message) {}
}
