package com.ecommerce.order.client;

import com.ecommerce.order.exception.PaymentApprovalFailedException;
import com.ecommerce.order.support.ServiceTimeZone;
import com.ecommerce.order.exception.PaymentCancelFailedException;
import com.ecommerce.order.exception.PaymentGatewayUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import java.util.Set;
import java.util.function.Function;

/**
 * 토스페이먼츠 결제 연동 (payment-foundation §11-1).
 * 승인: POST /v1/payments/confirm, 취소·부분취소: POST /v1/payments/{paymentKey}/cancel,
 * 승인 결과 재조회: GET /v1/payments/orders/{pgOrderId} (C-01).
 * 인증은 시크릿키 Basic 인증(시크릿키 + 콜론을 base64)이며 키·카드정보는 어떤 경우에도 로그에 남기지 않는다.
 *
 * 실패는 두 갈래로 나눈다 (C-01):
 *   - 응답 수신(4xx/5xx): PG 가 판정한 결과 → 호출부가 정한 도메인 예외(승인 거절·취소 실패)
 *   - 응답 미수신(타임아웃·연결 실패): 결과 미확정 → PaymentGatewayUnavailableException
 */
@Slf4j
@Component
public class TossPaymentClient {

    private static final String CONFIRM_PATH      = "/v1/payments/confirm";
    private static final String CANCEL_PATH       = "/v1/payments/%s/cancel";
    /** C-01: 승인 결과 재조회 — 통신 실패로 응답을 못 받았을 때 실제 승인 여부를 확인한다 */
    private static final String ORDER_LOOKUP_PATH = "/v1/payments/orders/%s";

    /**
     * M-02: 로그에 남기는 엔드포인트 종류.
     * 취소·조회 URL 에는 거래키·주문번호가 들어 있어 URL 자체를 로그에 찍지 않는다(마스킹 우회 방지).
     */
    private static final String ENDPOINT_CONFIRM = "payments/confirm";
    private static final String ENDPOINT_CANCEL  = "payments/cancel";
    private static final String ENDPOINT_LOOKUP  = "payments/lookup";

    /** 응답에서 사유를 못 읽었을 때 사용자에게 보여줄 일반 메시지(내부 정보 미노출) */
    private static final String DEFAULT_FAIL_MESSAGE = "결제 처리에 실패했습니다. 잠시 후 다시 시도해주세요.";

    /** C-01: 결과 미확정 안내 — 실패로 단정하지 않고 상태 확인을 안내한다 */
    private static final String UNAVAILABLE_MESSAGE =
            "결제 결과를 확인하지 못했습니다. 주문 내역에서 결제 상태를 확인해주세요.";

    /** H-01: 취소 멱등키 헤더 — 커밋 실패 후 재요청이 이중 환불되지 않게 한다 */
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

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
        return post(baseUrl + CONFIRM_PATH, body, headers(null),
                ENDPOINT_CONFIRM, PaymentApprovalFailedException::new);
    }

    /**
     * 결제 취소·환불 — cancelAmount 만큼 부분취소한다(§4.2).
     * 이미 취소된 결제의 재취소는 호출 전에 걸러야 한다(멱등 판정은 호출부 책임).
     * H-01: 멱등키를 함께 보내 "PG 취소 성공 + 우리 커밋 실패" 후의 재시도가 이중 환불되지 않게 한다.
     */
    public TossPayment cancel(String paymentKey, String cancelReason, long cancelAmount,
                              String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", cancelReason);
        body.put("cancelAmount", cancelAmount);

        log.info("PG 결제 취소 요청. paymentKey={}, cancelAmount={}, idempotencyKey={}",
                mask(paymentKey), cancelAmount, idempotencyKey);
        return post(String.format(baseUrl + CANCEL_PATH, paymentKey), body, headers(idempotencyKey),
                ENDPOINT_CANCEL, PaymentCancelFailedException::new);
    }

    /**
     * C-01: 승인 결과 재조회 — PG 주문번호로 실제 승인 여부를 확인한다.
     * @return 접수된 결제가 없으면(404) empty. 그 외 실패는 판정 불가이므로 예외로 올린다.
     */
    public Optional<TossPayment> findByPgOrderId(String pgOrderId) {
        log.info("PG 승인 결과 재조회. pgOrderId={}", pgOrderId);
        try {
            return Optional.ofNullable(tossRestTemplate.exchange(
                            String.format(baseUrl + ORDER_LOOKUP_PATH, pgOrderId),
                            HttpMethod.GET, new HttpEntity<>(headers(null)), TossPayment.class)
                    .getBody());
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("PG 재조회 — 접수된 결제 없음(미승인 확정). pgOrderId={}", pgOrderId);
                return Optional.empty();
            }
            log.error("PG 재조회 실패 — 판정 불가. endpoint={}, status={}",
                    ENDPOINT_LOOKUP, ex.getStatusCode());
            throw new PaymentGatewayUnavailableException(UNAVAILABLE_MESSAGE);
        } catch (ResourceAccessException ex) {
            log.error("PG 재조회 통신 실패 — 판정 불가. endpoint={}, cause={}",
                    ENDPOINT_LOOKUP, causeName(ex));
            throw new PaymentGatewayUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    /**
     * 공통 POST 호출 — 실패를 응답 수신 여부로 갈라 도메인 예외로 번역한다 (C-01).
     * try-catch 는 흐름 제어가 아니라 외부 오류 번역·미확정 판정 용도다(ProductClient 와 동일 패턴).
     */
    private TossPayment post(String url, Map<String, Object> body, HttpHeaders headers,
                             String endpoint, Function<String, RuntimeException> rejectionFactory) {
        try {
            return requireBody(tossRestTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, headers), TossPayment.class).getBody(), endpoint);
        } catch (HttpStatusCodeException ex) {
            String message = extractMessage(ex.getResponseBodyAsString());
            log.warn("PG 응답 오류. endpoint={}, status={}, message={}",
                    endpoint, ex.getStatusCode(), message);
            throw rejectionFactory.apply(message);
        } catch (ResourceAccessException ex) {
            // M-02: 거래키가 담긴 URL·원인 메시지는 남기지 않고 엔드포인트 종류와 원인 타입만 기록한다
            log.error("PG 통신 실패 — 결과 미확정. endpoint={}, cause={}", endpoint, causeName(ex));
            throw new PaymentGatewayUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    /** H-03: 응답 본문 방어 — 본문이 없으면 결과를 판정할 수 없으므로 확정하지 않는다 */
    private TossPayment requireBody(TossPayment body, String endpoint) {
        if (body == null) {
            log.error("PG 응답 본문 없음 — 결과 미확정. endpoint={}", endpoint);
            throw new PaymentGatewayUnavailableException(UNAVAILABLE_MESSAGE);
        }
        return body;
    }

    /** 공통 헤더 — 멱등키는 취소 호출에만 붙인다 */
    private HttpHeaders headers(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, authorizationHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set(IDEMPOTENCY_KEY_HEADER, idempotencyKey);
        }
        return headers;
    }

    /** 통신 실패 원인 타입만 추출 — 원인 메시지에는 내부 주소·거래키가 담기므로 쓰지 않는다 (M-02) */
    private String causeName(ResourceAccessException ex) {
        return ex.getMostSpecificCause().getClass().getSimpleName();
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
     * 승인·취소·조회 공통 응답(필요 필드만).
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
        /** H-03: 승인 완료 상태 — 가상계좌 입금대기 등도 성공 응답으로 오므로 DONE 만 승인으로 본다 */
        private static final String STATUS_DONE = "DONE";

        /** 승인이 성립하지 않은 종료 상태 — 실패로 확정해도 남은 돈이 없다 */
        private static final Set<String> TERMINATED_STATUSES =
                Set.of("ABORTED", "EXPIRED", "CANCELED", "PARTIAL_CANCELED");

        /** 승인 완료 여부 — 이 값이 true 일 때만 결제를 확정한다 (H-03) */
        public boolean isDone() {
            return STATUS_DONE.equals(status);
        }

        /** 승인 실패로 확정할 수 있는 상태인지 (C-01 재조회 판정) */
        public boolean isTerminated() {
            return status != null && TERMINATED_STATUSES.contains(status);
        }

        /**
         * 승인 시각 — 응답에 없으면 호출 시점을 쓰도록 Optional 로 돌려준다.
         *
         * <p>{@code toLocalDateTime()} 은 오프셋을 <b>변환 없이 버린다</b> — 토스가 UTC 오프셋으로
         * 응답하면 서비스 타임존(KST)으로 기록되는 다른 컬럼과 9시간 어긋난다(실측 확인).
         * 반드시 같은 순간을 서비스 타임존으로 옮긴 뒤 LocalDateTime 으로 변환한다.
         */
        public Optional<LocalDateTime> approvedAtAsLocal() {
            return Optional.ofNullable(approvedAt)
                    .map(at -> at.atZoneSameInstant(ServiceTimeZone.zone()).toLocalDateTime());
        }
    }

    /** 토스 오류 응답 — code/message 만 사용 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TossError(String code, String message) {}
}
