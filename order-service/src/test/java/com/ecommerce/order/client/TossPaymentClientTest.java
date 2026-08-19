package com.ecommerce.order.client;

import com.ecommerce.order.exception.PaymentApprovalFailedException;
import com.ecommerce.order.exception.PaymentCancelFailedException;
import com.ecommerce.order.exception.PaymentGatewayUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 토스페이먼츠 연동 단위 테스트 — 실제 PG 를 호출하지 않고 MockRestServiceServer 로 검증한다.
 */
@DisplayName("TossPaymentClient 단위 테스트 (V1.1-6)")
class TossPaymentClientTest {

    private static final String BASE_URL    = "https://api.tosspayments.com";
    private static final String SECRET_KEY  = "test_sk_dummy_secret";
    private static final String PAYMENT_KEY = "test_payment_key_123456";
    private static final String PG_ORDER_ID = "ORD-00000001-a1b2c3d4";
    private static final String IDEMPOTENCY_KEY = "PAYCNL-99-0";

    private RestTemplate          restTemplate;
    private MockRestServiceServer server;
    private TossPaymentClient     tossPaymentClient;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        tossPaymentClient = new TossPaymentClient(
                restTemplate, new ObjectMapper(), BASE_URL, SECRET_KEY);
    }

    @Test
    @DisplayName("승인 — 시크릿키 Basic 인증 헤더와 (paymentKey, orderId, amount) 를 전송한다")
    void confirm_sendsBasicAuthAndBody() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasicHeader()))
                .andExpect(jsonPath("$.paymentKey").value(PAYMENT_KEY))
                .andExpect(jsonPath("$.orderId").value(PG_ORDER_ID))
                .andExpect(jsonPath("$.amount").value(20000))
                .andRespond(withSuccess(confirmResponseBody(), MediaType.APPLICATION_JSON));

        TossPaymentClient.TossPayment result =
                tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, 20_000L);

        assertThat(result.paymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(result.totalAmount()).isEqualTo(20_000L);
        assertThat(result.isDone()).isTrue();
        assertThat(result.approvedAtAsLocal()).isPresent();
        server.verify();
    }

    @Test
    @DisplayName("승인 — PG 오류 응답의 message 만 도메인 예외로 전달한다")
    void confirm_pgError_throwsApprovalFailed() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"REJECT_CARD_COMPANY\",\"message\":\"카드사 승인 거절\"}"));

        assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, 20_000L))
                .isInstanceOf(PaymentApprovalFailedException.class)
                .hasMessage("카드사 승인 거절");
        server.verify();
    }

    @Test
    @DisplayName("C-01 승인 — 응답을 받지 못한 통신 실패는 '거절'이 아니라 '결과 미확정'으로 구분한다")
    void confirm_communicationFailure_throwsGatewayUnavailable() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, 20_000L))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasMessageContaining("결제 결과를 확인하지 못했습니다");
        server.verify();
    }

    @Test
    @DisplayName("H-03 승인 — 응답 본문이 없으면 확정하지 않고 결과 미확정으로 처리한다")
    void confirm_emptyBody_throwsGatewayUnavailable() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.OK));

        assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, 20_000L))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("H-03 승인 — 가상계좌 입금대기 응답은 DONE 이 아니므로 승인으로 보지 않는다")
    void confirm_waitingForDeposit_isNotDone() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withSuccess(waitingResponseBody(), MediaType.APPLICATION_JSON));

        TossPaymentClient.TossPayment result =
                tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, 20_000L);

        assertThat(result.isDone()).isFalse();
        assertThat(result.isTerminated()).isFalse();
    }

    @Test
    @DisplayName("H-01 취소 — paymentKey 경로에 사유·부분취소 금액·멱등키를 전송한다")
    void cancel_sendsReasonAmountAndIdempotencyKey() {
        server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasicHeader()))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(jsonPath("$.cancelReason").value("반품 승인"))
                .andExpect(jsonPath("$.cancelAmount").value(5000))
                .andRespond(withSuccess(cancelResponseBody(), MediaType.APPLICATION_JSON));

        TossPaymentClient.TossPayment result =
                tossPaymentClient.cancel(PAYMENT_KEY, "반품 승인", 5_000L, IDEMPOTENCY_KEY);

        assertThat(result.balanceAmount()).isEqualTo(15_000L);
        server.verify();
    }

    @Test
    @DisplayName("취소 — PG 오류 시 취소 실패 예외로 변환한다")
    void cancel_pgError_throwsCancelFailed() {
        server.expect(requestTo(BASE_URL + "/v1/payments/" + PAYMENT_KEY + "/cancel"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"ALREADY_CANCELED_PAYMENT\",\"message\":\"이미 취소된 결제\"}"));

        assertThatThrownBy(() ->
                tossPaymentClient.cancel(PAYMENT_KEY, "반품 승인", 5_000L, IDEMPOTENCY_KEY))
                .isInstanceOf(PaymentCancelFailedException.class)
                .hasMessage("이미 취소된 결제");
    }

    @Test
    @DisplayName("C-01 재조회 — PG 주문번호로 승인 결과를 조회한다")
    void findByPgOrderId_returnsPayment() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + PG_ORDER_ID))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedBasicHeader()))
                .andRespond(withSuccess(confirmResponseBody(), MediaType.APPLICATION_JSON));

        Optional<TossPaymentClient.TossPayment> found =
                tossPaymentClient.findByPgOrderId(PG_ORDER_ID);

        assertThat(found).isPresent();
        assertThat(found.get().isDone()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("C-01 재조회 — 접수된 결제가 없으면(404) empty 로 돌려 미승인으로 확정할 수 있게 한다")
    void findByPgOrderId_notFound_returnsEmpty() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + PG_ORDER_ID))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"NOT_FOUND_PAYMENT_SESSION\",\"message\":\"결제 없음\"}"));

        assertThat(tossPaymentClient.findByPgOrderId(PG_ORDER_ID)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("C-01 재조회 — 통신 실패는 판정 불가이므로 미확정 예외로 올린다")
    void findByPgOrderId_communicationFailure_throwsGatewayUnavailable() {
        server.expect(requestTo(BASE_URL + "/v1/payments/orders/" + PG_ORDER_ID))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        assertThatThrownBy(() -> tossPaymentClient.findByPgOrderId(PG_ORDER_ID))
                .isInstanceOf(PaymentGatewayUnavailableException.class);
        server.verify();
    }

    @Test
    @DisplayName("오류 본문을 해석할 수 없으면 내부 정보 없는 일반 메시지를 쓴다")
    void confirm_unparsableError_usesDefaultMessage() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("<html>error</html>"));

        assertThatThrownBy(() -> tossPaymentClient.confirm(PAYMENT_KEY, PG_ORDER_ID, 20_000L))
                .isInstanceOf(PaymentApprovalFailedException.class)
                .hasMessageContaining("결제 처리에 실패");
    }

    private String expectedBasicHeader() {
        return "Basic " + Base64.getEncoder()
                .encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
    }

    private String confirmResponseBody() {
        return """
                {
                  "paymentKey": "%s",
                  "orderId": "%s",
                  "status": "DONE",
                  "totalAmount": 20000,
                  "balanceAmount": 20000,
                  "approvedAt": "2026-08-19T12:00:00+09:00",
                  "method": "카드"
                }
                """.formatted(PAYMENT_KEY, PG_ORDER_ID);
    }

    private String waitingResponseBody() {
        return """
                {
                  "paymentKey": "%s",
                  "orderId": "%s",
                  "status": "WAITING_FOR_DEPOSIT",
                  "totalAmount": 20000,
                  "balanceAmount": 20000
                }
                """.formatted(PAYMENT_KEY, PG_ORDER_ID);
    }

    private String cancelResponseBody() {
        return """
                {
                  "paymentKey": "%s",
                  "orderId": "%s",
                  "status": "PARTIAL_CANCELED",
                  "totalAmount": 20000,
                  "balanceAmount": 15000
                }
                """.formatted(PAYMENT_KEY, PG_ORDER_ID);
    }
}
