package com.ecommerce.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;

/**
 * Gateway 전역 예외 핸들러.
 * 다운스트림 서비스 연결 실패 시 500 대신 적절한 상태 코드로 변환.
 */
@Slf4j
@Order(-1)
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;

        if (ex instanceof ConnectException || isCausedBy(ex, ConnectException.class)) {
            // 다운스트림 서비스 연결 거부 (서비스 미기동)
            status  = HttpStatus.SERVICE_UNAVAILABLE;
            message = "서비스를 일시적으로 사용할 수 없습니다.";
            log.warn("다운스트림 서비스 연결 실패: path={}, error={}", exchange.getRequest().getURI().getPath(), ex.getMessage());

        } else if (ex instanceof PrematureCloseException || isCausedBy(ex, PrematureCloseException.class)) {
            // 다운스트림 연결 조기 종료
            status  = HttpStatus.BAD_GATEWAY;
            message = "게이트웨이 오류가 발생했습니다.";
            log.warn("다운스트림 연결 조기 종료: path={}", exchange.getRequest().getURI().getPath());

        } else if (ex instanceof ResponseStatusException rse) {
            status  = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : ex.getMessage();

        } else {
            status  = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "내부 서버 오류가 발생했습니다.";
            log.error("Gateway 처리 중 예외 발생: path={}", exchange.getRequest().getURI().getPath(), ex);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                status.value(), status.getReasonPhrase(), message,
                exchange.getRequest().getURI().getPath()
        );
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean isCausedBy(Throwable ex, Class<? extends Throwable> type) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            if (type.isInstance(cause)) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
