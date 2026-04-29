package com.ecommerce.common.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * MDC 로깅 필터 — 요청마다 X-Request-ID 를 MDC 에 등록
 *
 * - 게이트웨이가 X-Request-ID 헤더를 전달하면 재사용
 * - 없으면 UUID 신규 생성
 * - 응답 헤더에도 X-Request-ID 를 포함시켜 클라이언트가 추적 가능
 * - 요청 종료 후 반드시 MDC.clear() — 스레드풀 오염 방지
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter implements Filter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String MDC_REQUEST_ID_KEY = "requestId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq = (HttpServletRequest)  request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String requestId = Optional.ofNullable(httpReq.getHeader(REQUEST_ID_HEADER))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put(MDC_REQUEST_ID_KEY, requestId);
        httpRes.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear(); // 스레드 반환 전 반드시 초기화
        }
    }
}
